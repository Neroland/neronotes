package za.co.neroland.neronotes.library;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataRecovery;
import za.co.neroland.neronotes.NeroNotesCommon;

/**
 * The persisted shared library of published disks, as a {@link SavedData}
 * stored on the overworld — one library per server, whatever dimension the
 * publisher or downloader stands in.
 *
 * <p><strong>Every accessor routes through Core's
 * {@link SavedDataRecovery}</strong> (ecosystem convention: a direct
 * {@code getDataStorage().computeIfAbsent} is a review failure). The recovery
 * name is {@value #RECOVERY_NAME}.</p>
 *
 * <p><strong>Erasure is designed in now</strong> (POPIA / GDPR): a later
 * stage registers {@link #anonymiseAuthor(UUID)} with Core's
 * {@code PlayerDataErasure} — "sever the link, keep the work". Erasure call
 * sites must use {@link #anonymiseAuthorAndBackup} so the recovery backup
 * does not retain the authorship rows that were just severed. All row logic
 * and policy lives in the plain-JVM {@link LibraryTable}.</p>
 */
public final class LibraryStore extends SavedData {

    /** Stable, non-identifying recovery/backup label — {@code <modid>:<store>}. */
    public static final String RECOVERY_NAME = "neronotes:library";

    private static final Codec<LibraryStore> CODEC =
            CompoundTag.CODEC.xmap(LibraryStore::fromTag, LibraryStore::toTag);

    private static final Supplier<LibraryStore> FACTORY = LibraryStore::new;

    // The four-argument constructor is the one present on every loader/MC cell
    // (the three-argument convenience overload is a NeoForge-only patch). A null
    // DataFixTypes means "no datafixing" — correct for mod data with its own
    // versioning discipline.
    public static final SavedDataType<LibraryStore> TYPE = new SavedDataType<LibraryStore>(
            Identifier.fromNamespaceAndPath(NeroNotesCommon.MOD_ID, "library"),
            FACTORY,
            CODEC,
            null);

    private final LibraryTable table;

    public LibraryStore() {
        this(new LibraryTable());
    }

    private LibraryStore(LibraryTable table) {
        this.table = table;
    }

    private static LibraryStore fromTag(CompoundTag tag) {
        return new LibraryStore(LibraryTable.load(tag));
    }

    private CompoundTag toTag() {
        return table.save();
    }

    /** The one accessor. Routed through Core's recovery guard; overworld-anchored. */
    public static LibraryStore get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, LibraryStore::new, RECOVERY_NAME);
    }

    // ------------------------------------------------------------------
    // Mutations (delegate to the table; mark dirty on change)
    // ------------------------------------------------------------------

    public LibraryTable.PublishResult publish(String title, UUID author, String authorName,
                                              boolean anonymous, String familyId, byte[] scoreBytes,
                                              int sizeCap, int perPlayerQuota, boolean pendingApproval) {
        LibraryTable.PublishResult result = table.publish(title, author, authorName, anonymous,
                familyId, scoreBytes, sizeCap, perPlayerQuota, pendingApproval);
        if (result.ok()) {
            setDirty();
        }
        return result;
    }

    public boolean approve(int id) {
        return dirtyIf(table.approve(id));
    }

    public boolean remove(int id) {
        return dirtyIf(table.remove(id));
    }

    public LibraryTable.UnpublishResult unpublish(int id, UUID requester) {
        LibraryTable.UnpublishResult result = table.unpublish(id, requester);
        if (result == LibraryTable.UnpublishResult.REMOVED) {
            setDirty();
        }
        return result;
    }

    /** Record one download — the aggregate count only, nothing else. */
    public boolean incrementDownloads(int id) {
        return dirtyIf(table.incrementDownloads(id));
    }

    /**
     * "Sever the link, keep the work" (erasure seam, registered with Core in
     * a later stage). Prefer {@link #anonymiseAuthorAndBackup} from erasure
     * call sites.
     */
    public boolean anonymiseAuthor(UUID author) {
        return dirtyIf(table.anonymiseAuthor(author));
    }

    /**
     * Anonymise {@code author}'s entries and immediately refresh the recovery
     * backup — without the backup call the recovery guard would keep a
     * pre-erasure snapshot still naming the author.
     */
    public boolean anonymiseAuthorAndBackup(MinecraftServer server, UUID author) {
        boolean changed = anonymiseAuthor(author);
        if (changed) {
            SavedDataRecovery.backupNow(server.overworld(), TYPE, this, RECOVERY_NAME);
        }
        return changed;
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    public Optional<LibraryTable.Entry> entry(int id) {
        return table.entry(id);
    }

    public List<LibraryTable.Entry> visiblePage(int page, int pageSize) {
        return table.visiblePage(page, pageSize);
    }

    public List<LibraryTable.Entry> allEntries() {
        return table.allEntries();
    }

    public int pageCount(int pageSize) {
        return table.pageCount(pageSize);
    }

    public int visibleCount() {
        return table.visibleCount();
    }

    public int totalCount() {
        return table.totalCount();
    }

    public int countBy(UUID author) {
        return table.countBy(author);
    }

    /** Erasure-conformance probe: does {@code author} still hold any library entry? */
    public boolean hasRow(UUID author) {
        return table.hasRow(author);
    }

    private boolean dirtyIf(boolean changed) {
        if (changed) {
            setDirty();
        }
        return changed;
    }
}
