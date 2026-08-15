package za.co.neroland.neronotes.library;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.item.DiskNames;

/**
 * The published-disk library rows — the plain-JVM heart of
 * {@link LibraryStore}. All row logic, policy enforcement (size cap,
 * per-player quota, op-approval visibility, author-only unpublish) and NBT
 * live here so every rule is directly unit-testable; the store only adds
 * {@code SavedData} dirty-tracking and Core's recovery guard.
 *
 * <p><strong>Privacy is a design constraint of this table, not a
 * preference:</strong> an entry holds the disk id, title, author UUID +
 * display choice, voice family, score bytes, the pending-approval flag and an
 * <em>aggregate download count only</em>. There is no listening history, no
 * per-download identity or timestamp, and no play log — a download increments
 * one integer and records nothing else. {@link #anonymiseAuthor(UUID)}
 * implements the coming "sever the link, keep the work" erasure design
 * (wired to Core's erasure hook in a later stage): it strips the author UUID
 * and display name and marks the entries anonymous while the compositions —
 * and every disk other players copied from them — keep working.</p>
 *
 * <p>Not thread-safe on its own — mutate only on the server thread.</p>
 */
public final class LibraryTable {

    /** Hard ceiling on entries a single page may carry (config caps page size at 100). */
    public static final int MAX_PAGE_SIZE = 100;

    // NBT field names.
    private static final String KEY_NEXT_ID = "next_id";
    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_ID = "id";
    private static final String KEY_TITLE = "title";
    private static final String KEY_AUTHOR = "author";
    private static final String KEY_AUTHOR_NAME = "author_name";
    private static final String KEY_ANONYMOUS = "anon";
    private static final String KEY_FAMILY = "family";
    private static final String KEY_SCORE = "score";
    private static final String KEY_DOWNLOADS = "downloads";
    private static final String KEY_PENDING = "pending";

    /**
     * One published composition. The author UUID exists for quota,
     * author-only unpublish and data erasure — it is <strong>never</strong> a
     * display surface and never leaves the server: client-facing metadata
     * goes through {@link #authorDisplay()}, which is empty for anonymous
     * entries (and for entries whose author was erased).
     */
    public static final class Entry {
        private final int id;
        private final String title;
        @Nullable
        private UUID author;
        private String authorName;
        private boolean anonymous;
        private final String familyId;
        private final byte[] scoreBytes;
        private int downloads;
        private boolean pending;

        private Entry(int id, String title, @Nullable UUID author, String authorName,
                      boolean anonymous, String familyId, byte[] scoreBytes,
                      int downloads, boolean pending) {
            this.id = id;
            this.title = title;
            this.author = author;
            // The anonymity invariant, same as DiskContents: anonymous rows
            // (and authorless rows) store NO display name.
            this.authorName = anonymous || author == null || authorName == null ? "" : authorName;
            this.anonymous = anonymous;
            this.familyId = familyId;
            this.scoreBytes = scoreBytes;
            this.downloads = downloads;
            this.pending = pending;
        }

        public int id() {
            return id;
        }

        public String title() {
            return title;
        }

        /** The author UUID — server-side use only (quota, unpublish, erasure). Empty after erasure. */
        public Optional<UUID> author() {
            return Optional.ofNullable(author);
        }

        /**
         * The client-visible author name, if any. Empty for anonymous entries
         * and for entries whose author was erased — callers show the
         * translated "anonymous" line instead. The UUID is never displayed.
         */
        public Optional<String> authorDisplay() {
            return anonymous || author == null || authorName.isBlank()
                    ? Optional.empty() : Optional.of(authorName);
        }

        public boolean anonymous() {
            return anonymous;
        }

        public String familyId() {
            return familyId;
        }

        /** The serialised score (defensive copy — the stored bytes are immutable). */
        public byte[] scoreBytes() {
            return scoreBytes.clone();
        }

        /** The aggregate download count — the ONLY download data this mod keeps. */
        public int downloads() {
            return downloads;
        }

        /** Whether the entry awaits operator approval (hidden from every listing until approved). */
        public boolean pending() {
            return pending;
        }

        /** Whether the entry is visible in listings and downloadable. */
        public boolean visible() {
            return !pending;
        }
    }

    /** Why a publish was refused. */
    public enum PublishError {
        NONE,
        /** The server-wide {@code library.size_cap} is reached. */
        LIBRARY_FULL,
        /** The author already holds {@code library.per_player_quota} entries. */
        QUOTA_EXCEEDED
    }

    /** A publish outcome: the error, and the new entry id on success. */
    public record PublishResult(PublishError error, int id) {

        public boolean ok() {
            return error == PublishError.NONE;
        }
    }

    /** An unpublish outcome. */
    public enum UnpublishResult {
        REMOVED,
        NO_SUCH_ENTRY,
        /** The requester is not the entry's author (erased authors count as nobody). */
        NOT_AUTHOR
    }

    /** Insertion-ordered for deterministic saves and stable pagination. */
    private final Map<Integer, Entry> entries = new LinkedHashMap<>();
    private int nextId = 1;

    // ------------------------------------------------------------------
    // Publishing
    // ------------------------------------------------------------------

    /**
     * Publish a composition, enforcing the server-wide size cap and the
     * per-author quota (both counted over ALL entries including pending ones
     * — a pending entry still occupies its author's quota and library
     * space). The caller has already validated the title and the score
     * budget. Returns the new entry id on success.
     */
    public PublishResult publish(String title, UUID author, String authorName, boolean anonymous,
                                 String familyId, byte[] scoreBytes,
                                 int sizeCap, int perPlayerQuota, boolean pendingApproval) {
        if (title == null || author == null || familyId == null || scoreBytes == null) {
            throw new IllegalArgumentException("title, author, familyId and scoreBytes must not be null");
        }
        if (entries.size() >= sizeCap) {
            return new PublishResult(PublishError.LIBRARY_FULL, 0);
        }
        if (countBy(author) >= perPlayerQuota) {
            return new PublishResult(PublishError.QUOTA_EXCEEDED, 0);
        }
        int id = nextId++;
        entries.put(id, new Entry(id, title.substring(0, Math.min(title.length(), DiskNames.HARD_MAX_LENGTH)),
                author, authorName, anonymous, familyId, scoreBytes.clone(), 0, pendingApproval));
        return new PublishResult(PublishError.NONE, id);
    }

    // ------------------------------------------------------------------
    // Listing (paginated from day one — locked decision 5)
    // ------------------------------------------------------------------

    /** Every entry, including pending ones — operator/server use only. */
    public List<Entry> allEntries() {
        return List.copyOf(entries.values());
    }

    /** The visible (approved) entries, in publish order. */
    public List<Entry> visibleEntries() {
        List<Entry> visible = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.visible()) {
                visible.add(entry);
            }
        }
        return visible;
    }

    /**
     * One page of visible entries. {@code page} is zero-based; a page beyond
     * the end (or an empty library) yields an empty list, never a throw.
     */
    public List<Entry> visiblePage(int page, int pageSize) {
        int size = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        if (page < 0) {
            return List.of();
        }
        List<Entry> visible = visibleEntries();
        int from = page * size;
        if (from >= visible.size()) {
            return List.of();
        }
        return List.copyOf(visible.subList(from, Math.min(from + size, visible.size())));
    }

    /** The number of visible pages at {@code pageSize} — 0 for an empty library. */
    public int pageCount(int pageSize) {
        int size = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        return (visibleCount() + size - 1) / size;
    }

    public int totalCount() {
        return entries.size();
    }

    public int visibleCount() {
        int count = 0;
        for (Entry entry : entries.values()) {
            if (entry.visible()) {
                count++;
            }
        }
        return count;
    }

    /** How many entries (visible or pending) {@code author} holds. */
    public int countBy(UUID author) {
        int count = 0;
        for (Entry entry : entries.values()) {
            if (entry.author != null && entry.author.equals(author)) {
                count++;
            }
        }
        return count;
    }

    /** The entry with {@code id}, visible or pending. */
    public Optional<Entry> entry(int id) {
        return Optional.ofNullable(entries.get(id));
    }

    // ------------------------------------------------------------------
    // Moderation + lifecycle (locked decision 6)
    // ------------------------------------------------------------------

    /** Operator approval: pending → visible. False if unknown or already visible. */
    public boolean approve(int id) {
        Entry entry = entries.get(id);
        if (entry == null || !entry.pending) {
            return false;
        }
        entry.pending = false;
        return true;
    }

    /** Operator takedown: delete the entry outright. */
    public boolean remove(int id) {
        return entries.remove(id) != null;
    }

    /**
     * Author-only unpublish: the entry is removed only when {@code requester}
     * is its author. An entry whose author was erased has no author, so
     * nobody can unpublish it except an operator ({@link #remove}).
     */
    public UnpublishResult unpublish(int id, UUID requester) {
        Entry entry = entries.get(id);
        if (entry == null) {
            return UnpublishResult.NO_SUCH_ENTRY;
        }
        if (entry.author == null || requester == null || !entry.author.equals(requester)) {
            return UnpublishResult.NOT_AUTHOR;
        }
        entries.remove(id);
        return UnpublishResult.REMOVED;
    }

    /**
     * Record one download — <strong>the aggregate count is the only thing
     * stored</strong>; who downloaded, and when, is deliberately never
     * recorded. Only visible entries count.
     */
    public boolean incrementDownloads(int id) {
        Entry entry = entries.get(id);
        if (entry == null || !entry.visible()) {
            return false;
        }
        if (entry.downloads < Integer.MAX_VALUE) {
            entry.downloads++;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Erasure seams (wired to Core's PlayerDataErasure in a later stage)
    // ------------------------------------------------------------------

    /** Whether {@code author} still holds any entry — the erasure-conformance probe. */
    public boolean hasRow(UUID author) {
        if (author == null) {
            return false;
        }
        for (Entry entry : entries.values()) {
            if (author.equals(entry.author)) {
                return true;
            }
        }
        return false;
    }

    /**
     * "Sever the link, keep the work": strip {@code author}'s UUID and
     * display name from every one of their entries and mark those entries
     * anonymous. The compositions stay published (and every already-copied
     * disk keeps playing) — erasure removes the person, not other players'
     * music. Returns whether anything changed.
     */
    public boolean anonymiseAuthor(UUID author) {
        if (author == null) {
            return false;
        }
        boolean changed = false;
        for (Entry entry : entries.values()) {
            if (author.equals(entry.author)) {
                entry.author = null;
                entry.authorName = "";
                entry.anonymous = true;
                changed = true;
            }
        }
        return changed;
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    /** Serialise every entry. */
    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        root.putInt(KEY_NEXT_ID, nextId);
        ListTag list = new ListTag();
        for (Entry entry : entries.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putInt(KEY_ID, entry.id);
            tag.putString(KEY_TITLE, entry.title);
            if (entry.author != null) {
                tag.putString(KEY_AUTHOR, entry.author.toString());
            }
            if (!entry.authorName.isEmpty()) {
                tag.putString(KEY_AUTHOR_NAME, entry.authorName);
            }
            tag.putBoolean(KEY_ANONYMOUS, entry.anonymous);
            tag.putString(KEY_FAMILY, entry.familyId);
            tag.putByteArray(KEY_SCORE, entry.scoreBytes);
            tag.putInt(KEY_DOWNLOADS, entry.downloads);
            tag.putBoolean(KEY_PENDING, entry.pending);
            list.add(tag);
        }
        root.put(KEY_ENTRIES, list);
        return root;
    }

    /**
     * Deserialise. Malformed rows are skipped with a warning rather than
     * failing the whole store — one bad row must never take the shared
     * library down with it. (No player-authored string is ever logged.)
     */
    public static LibraryTable load(CompoundTag root) {
        LibraryTable table = new LibraryTable();
        table.nextId = Math.max(1, root.getIntOr(KEY_NEXT_ID, 1));
        Optional<ListTag> list = root.getList(KEY_ENTRIES);
        if (list.isEmpty()) {
            return table;
        }
        int maxId = 0;
        for (int i = 0; i < list.get().size(); i++) {
            Optional<CompoundTag> row = list.get().getCompound(i);
            if (row.isEmpty()) {
                NeroNotesCommon.LOGGER.warn("[NeroNotes] skipping malformed library row {} (not a compound)", i);
                continue;
            }
            try {
                Entry entry = loadRow(row.get());
                table.entries.put(entry.id, entry);
                maxId = Math.max(maxId, entry.id);
            } catch (RuntimeException malformed) {
                NeroNotesCommon.LOGGER.warn("[NeroNotes] skipping malformed library row {}: {}", i,
                        malformed.getClass().getSimpleName());
            }
        }
        table.nextId = Math.max(table.nextId, maxId + 1);
        return table;
    }

    private static Entry loadRow(CompoundTag tag) {
        int id = tag.getInt(KEY_ID).orElseThrow();
        String title = tag.getString(KEY_TITLE).orElseThrow();
        UUID author = tag.getString(KEY_AUTHOR).map(UUID::fromString).orElse(null);
        String authorName = tag.getStringOr(KEY_AUTHOR_NAME, "");
        boolean anonymous = tag.getBooleanOr(KEY_ANONYMOUS, false);
        String familyId = tag.getStringOr(KEY_FAMILY, "");
        byte[] scoreBytes = tag.getByteArray(KEY_SCORE).orElseThrow();
        int downloads = tag.getIntOr(KEY_DOWNLOADS, 0);
        boolean pending = tag.getBooleanOr(KEY_PENDING, false);
        return new Entry(id, title, author, authorName, anonymous, familyId, scoreBytes, downloads, pending);
    }
}
