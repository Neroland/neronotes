package za.co.neroland.neronotes.soundforge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import za.co.neroland.neronotes.NeroNotesCommon;

/**
 * The per-player Soundforge session rows: one row per player who has ever
 * stepped through a Harmonic Gate, holding their {@link ReturnAnchor}, an
 * "inside right now" flag, the entry game-time, and an opaque session-data
 * compound reserved for the Stage 5 sequencer (the session score). This is
 * the plain-JVM heart of {@link SoundforgeSessionStore} — all row logic lives
 * here so it is directly unit-testable; the store only adds {@code SavedData}
 * dirty-tracking and Core's recovery guard.
 *
 * <p><strong>Designed for per-player purge from day one</strong> (POPIA /
 * GDPR): {@link #purgePlayer(UUID)} removes the player's whole row — anchor,
 * flags and session data — leaving every other player's row untouched. Core
 * erasure registration follows in Stage 7; the capability exists now so no
 * store ships without it.</p>
 *
 * <p>Not thread-safe on its own — mutate only on the server thread (as
 * {@link SoundforgeTravel} does).</p>
 */
public final class SoundforgeSessionTable {

    // NBT field names.
    private static final String KEY_SESSIONS = "sessions";
    private static final String KEY_PLAYER = "player";
    private static final String KEY_DIMENSION = "dim";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_Z = "z";
    private static final String KEY_YROT = "yRot";
    private static final String KEY_XROT = "xRot";
    private static final String KEY_INSIDE = "inside";
    private static final String KEY_ENTERED = "entered";
    private static final String KEY_DATA = "data";

    private static final class Row {
        ReturnAnchor anchor;
        boolean inside;
        long enteredGameTime;
        CompoundTag sessionData; // null until Stage 5 writes one

        Row(ReturnAnchor anchor, boolean inside, long enteredGameTime, CompoundTag sessionData) {
            this.anchor = anchor;
            this.inside = inside;
            this.enteredGameTime = enteredGameTime;
            this.sessionData = sessionData;
        }
    }

    /** Insertion-ordered for deterministic saves. */
    private final Map<UUID, Row> rows = new LinkedHashMap<>();

    /**
     * Record an entry into the Soundforge: overwrite the return anchor, mark
     * the player inside, keep any existing session data. Returns false only
     * for null arguments.
     */
    public boolean beginSession(UUID player, ReturnAnchor anchor, long gameTime) {
        if (player == null || anchor == null) {
            return false;
        }
        Row existing = rows.get(player);
        CompoundTag keptData = existing == null ? null : existing.sessionData;
        rows.put(player, new Row(anchor, true, gameTime, keptData));
        return true;
    }

    /**
     * Mark the player outside again (a completed return). The anchor and
     * session data are kept — re-entering overwrites the anchor anyway, and
     * the Stage 5 session score must survive a trip home.
     */
    public boolean markOutside(UUID player) {
        Row row = player == null ? null : rows.get(player);
        if (row == null || !row.inside) {
            return false;
        }
        row.inside = false;
        return true;
    }

    /** The player's stored return anchor, if any. */
    public Optional<ReturnAnchor> returnAnchor(UUID player) {
        Row row = player == null ? null : rows.get(player);
        return row == null ? Optional.empty() : Optional.ofNullable(row.anchor);
    }

    /** Whether the store believes the player is currently inside the Soundforge. */
    public boolean isInside(UUID player) {
        Row row = player == null ? null : rows.get(player);
        return row != null && row.inside;
    }

    /** Game time (server ticks) of the player's most recent entry, or empty. */
    public Optional<Long> enteredGameTime(UUID player) {
        Row row = player == null ? null : rows.get(player);
        return row == null ? Optional.empty() : Optional.of(row.enteredGameTime);
    }

    /**
     * Opaque per-player session data — the Stage 5 seam for the sequencer's
     * session score. Only a player with an existing session row can carry
     * session data.
     */
    public Optional<CompoundTag> sessionData(UUID player) {
        Row row = player == null ? null : rows.get(player);
        return row == null ? Optional.empty() : Optional.ofNullable(row.sessionData);
    }

    /** Store (or clear, with {@code null}) session data. Fails without an existing row. */
    public boolean putSessionData(UUID player, CompoundTag data) {
        Row row = player == null ? null : rows.get(player);
        if (row == null) {
            return false;
        }
        row.sessionData = data;
        return true;
    }

    /** Whether any row exists for {@code player} — the erasure-conformance probe for this store. */
    public boolean hasRow(UUID player) {
        return player != null && rows.containsKey(player);
    }

    /**
     * Per-player purge: remove the player's whole row (anchor, flags and
     * session data). Bystanders' rows are untouched. Returns whether anything
     * changed.
     */
    public boolean purgePlayer(UUID player) {
        return player != null && rows.remove(player) != null;
    }

    public int size() {
        return rows.size();
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    /** Serialise every row. */
    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Row> entry : rows.entrySet()) {
            Row row = entry.getValue();
            CompoundTag tag = new CompoundTag();
            tag.putString(KEY_PLAYER, entry.getKey().toString());
            if (row.anchor != null) {
                tag.putString(KEY_DIMENSION, row.anchor.dimension());
                tag.putDouble(KEY_X, row.anchor.x());
                tag.putDouble(KEY_Y, row.anchor.y());
                tag.putDouble(KEY_Z, row.anchor.z());
                tag.putFloat(KEY_YROT, row.anchor.yRot());
                tag.putFloat(KEY_XROT, row.anchor.xRot());
            }
            tag.putBoolean(KEY_INSIDE, row.inside);
            tag.putLong(KEY_ENTERED, row.enteredGameTime);
            if (row.sessionData != null) {
                tag.put(KEY_DATA, row.sessionData);
            }
            list.add(tag);
        }
        root.put(KEY_SESSIONS, list);
        return root;
    }

    /**
     * Deserialise. Malformed rows are skipped with a warning rather than
     * failing the whole store — a single bad row must never take every
     * player's return anchor down with it.
     */
    public static SoundforgeSessionTable load(CompoundTag root) {
        SoundforgeSessionTable table = new SoundforgeSessionTable();
        Optional<ListTag> list = root.getList(KEY_SESSIONS);
        if (list.isEmpty()) {
            return table;
        }
        for (int i = 0; i < list.get().size(); i++) {
            Optional<CompoundTag> row = list.get().getCompound(i);
            if (row.isEmpty()) {
                NeroNotesCommon.LOGGER.warn("[NeroNotes] skipping malformed soundforge session row {} (not a compound)", i);
                continue;
            }
            try {
                table.loadRow(row.get());
            } catch (RuntimeException malformed) {
                NeroNotesCommon.LOGGER.warn("[NeroNotes] skipping malformed soundforge session row {}: {}", i,
                        malformed.getClass().getSimpleName());
            }
        }
        return table;
    }

    private void loadRow(CompoundTag tag) {
        UUID player = UUID.fromString(tag.getString(KEY_PLAYER).orElseThrow());
        ReturnAnchor anchor = null;
        Optional<String> dimension = tag.getString(KEY_DIMENSION);
        if (dimension.isPresent()) {
            anchor = new ReturnAnchor(dimension.get(),
                    tag.getDoubleOr(KEY_X, 0),
                    tag.getDoubleOr(KEY_Y, 0),
                    tag.getDoubleOr(KEY_Z, 0),
                    tag.getFloatOr(KEY_YROT, 0f),
                    tag.getFloatOr(KEY_XROT, 0f));
        }
        Row row = new Row(anchor,
                tag.getBooleanOr(KEY_INSIDE, false),
                tag.getLongOr(KEY_ENTERED, 0),
                tag.getCompound(KEY_DATA).orElse(null));
        rows.put(player, row);
    }
}
