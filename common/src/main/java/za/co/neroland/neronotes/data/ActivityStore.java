package za.co.neroland.neronotes.data;

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
 * The persisted per-player last-seen record backing the Stage 7 retention
 * sweep, as a {@link SavedData} stored on the overworld. Holds exactly a UUID
 * and a login timestamp per player — nothing else (see {@link ActivityTable}
 * for the privacy rationale).
 *
 * <p><strong>Every accessor routes through Core's
 * {@link SavedDataRecovery}</strong> (ecosystem convention: a direct
 * {@code getDataStorage().computeIfAbsent} is a review failure). The recovery
 * name is {@value #RECOVERY_NAME}.</p>
 *
 * <p>NeroNotes keeps its own record rather than reading Core's internal
 * {@code PlayerActivity} (that class is {@code @ApiStatus.Internal}); the
 * sweep in {@link RetentionSweep} honours NeroNotes' own
 * {@code data.retention_days} key and purges NeroNotes data only.</p>
 */
public final class ActivityStore extends SavedData {

    /** Stable, non-identifying recovery/backup label — {@code <modid>:<store>}. */
    public static final String RECOVERY_NAME = "neronotes:activity";

    private static final Codec<ActivityStore> CODEC =
            CompoundTag.CODEC.xmap(ActivityStore::fromTag, ActivityStore::toTag);

    private static final Supplier<ActivityStore> FACTORY = ActivityStore::new;

    // The four-argument constructor is the one present on every loader/MC cell
    // (the three-argument convenience overload is a NeoForge-only patch). A null
    // DataFixTypes means "no datafixing" — correct for mod data with its own
    // versioning discipline.
    public static final SavedDataType<ActivityStore> TYPE = new SavedDataType<ActivityStore>(
            Identifier.fromNamespaceAndPath(NeroNotesCommon.MOD_ID, "activity"),
            FACTORY,
            CODEC,
            null);

    private final ActivityTable table;

    public ActivityStore() {
        this(new ActivityTable());
    }

    private ActivityStore(ActivityTable table) {
        this.table = table;
    }

    private static ActivityStore fromTag(CompoundTag tag) {
        return new ActivityStore(ActivityTable.load(tag));
    }

    private CompoundTag toTag() {
        return table.save();
    }

    /** The one accessor. Routed through Core's recovery guard; overworld-anchored. */
    public static ActivityStore get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, ActivityStore::new, RECOVERY_NAME);
    }

    // ------------------------------------------------------------------
    // Mutations (delegate to the table; mark dirty on change)
    // ------------------------------------------------------------------

    /** Record that {@code player} was just seen (join / sweep refresh). */
    public boolean touch(UUID player) {
        return dirtyIf(table.touch(player, System.currentTimeMillis()));
    }

    /**
     * Per-player purge, for Core's erasure hook. Prefer
     * {@link #purgePlayerAndBackup} from erasure call sites.
     */
    public boolean purgePlayer(UUID player) {
        return dirtyIf(table.purgePlayer(player));
    }

    /**
     * Purge {@code player} and immediately refresh the recovery backup —
     * without the backup call the recovery guard would keep a pre-erasure
     * snapshot containing the row that was just removed.
     */
    public boolean purgePlayerAndBackup(MinecraftServer server, UUID player) {
        boolean changed = purgePlayer(player);
        if (changed) {
            SavedDataRecovery.backupNow(server.overworld(), TYPE, this, RECOVERY_NAME);
        }
        return changed;
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    /** When {@code player} was last seen (epoch millis), if recorded. */
    public Optional<Long> lastSeen(UUID player) {
        return table.lastSeen(player);
    }

    /** Erasure-conformance probe: does {@code player} still have an activity row? */
    public boolean hasRow(UUID player) {
        return table.hasRow(player);
    }

    /** UUIDs last seen more than {@code days} ago (empty when {@code days <= 0}). */
    public List<UUID> stalerThan(int days) {
        return table.stalerThan(days, System.currentTimeMillis());
    }

    public int recordCount() {
        return table.size();
    }

    private boolean dirtyIf(boolean changed) {
        if (changed) {
            setDirty();
        }
        return changed;
    }
}
