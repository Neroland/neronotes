package za.co.neroland.neronotes.signal;

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
 * The persisted channel state — every resonance channel and its trust list —
 * as a {@link SavedData} stored on the overworld and keyed by the full
 * {@link ChannelKey} (so one store covers all dimensions).
 *
 * <p><strong>Every accessor routes through Core's
 * {@link SavedDataRecovery}</strong> (ecosystem convention: a direct
 * {@code getDataStorage().computeIfAbsent} is a review failure — a corrupt
 * {@code .dat} otherwise produces a repeating hard crash, MC-NEROSPACE-H).
 * The recovery name is {@value #RECOVERY_NAME}.</p>
 *
 * <p><strong>Per-player purge is designed in now</strong>: Stage 7 registers
 * {@link #purgePlayer(UUID)} with Core's {@code PlayerDataErasure}; callers
 * performing an erasure must use {@link #purgePlayerAndBackup} so the backup
 * kept by the recovery guard does not retain the rows that were just erased.</p>
 */
public final class ChannelStore extends SavedData {

    /** Stable, non-identifying recovery/backup label — {@code <modid>:<store>}. */
    public static final String RECOVERY_NAME = "neronotes:channels";

    private static final Codec<ChannelStore> CODEC =
            CompoundTag.CODEC.xmap(ChannelStore::fromTag, ChannelStore::toTag);

    private static final Supplier<ChannelStore> FACTORY = ChannelStore::new;

    // The four-argument constructor is the one present on every loader/MC cell
    // (the three-argument convenience overload is a NeoForge-only patch). A null
    // DataFixTypes means "no datafixing" — correct for mod data with its own
    // versioning discipline.
    public static final SavedDataType<ChannelStore> TYPE = new SavedDataType<ChannelStore>(
            Identifier.fromNamespaceAndPath(NeroNotesCommon.MOD_ID, "channels"),
            FACTORY,
            CODEC,
            null);

    private final ChannelTable table;

    public ChannelStore() {
        this(new ChannelTable());
    }

    private ChannelStore(ChannelTable table) {
        this.table = table;
    }

    private static ChannelStore fromTag(CompoundTag tag) {
        return new ChannelStore(ChannelTable.load(tag));
    }

    private CompoundTag toTag() {
        return table.save();
    }

    /**
     * The one accessor. Routed through Core's recovery guard; the store lives
     * on the overworld regardless of which dimension a channel is in.
     */
    public static ChannelStore get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, ChannelStore::new, RECOVERY_NAME);
    }

    // ------------------------------------------------------------------
    // Mutations (delegate to the table; mark dirty on change)
    // ------------------------------------------------------------------

    public boolean create(ChannelKey key) {
        return dirtyIf(table.create(key));
    }

    public boolean rename(ChannelKey key, String newName) {
        return dirtyIf(table.rename(key, newName));
    }

    public boolean delete(ChannelKey key) {
        return dirtyIf(table.delete(key));
    }

    public boolean trust(ChannelKey key, UUID player) {
        return dirtyIf(table.trust(key, player));
    }

    public boolean untrust(ChannelKey key, UUID player) {
        return dirtyIf(table.untrust(key, player));
    }

    /**
     * Per-player purge (owner rows + trust entries), for Core's erasure hook
     * (registered in a later stage). Prefer {@link #purgePlayerAndBackup}
     * from erasure call sites.
     */
    public boolean purgePlayer(UUID player) {
        return dirtyIf(table.purgePlayer(player));
    }

    /**
     * Purge {@code player} and immediately refresh the recovery backup —
     * without the backup call the recovery guard would keep a pre-erasure
     * snapshot containing the rows that were just removed.
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

    public Optional<ResonanceChannel> channel(ChannelKey key) {
        return table.get(key);
    }

    public List<ResonanceChannel> channelsIn(String dimension) {
        return table.channelsIn(dimension);
    }

    public List<ResonanceChannel> channelsOwnedBy(UUID player) {
        return table.channelsOwnedBy(player);
    }

    /** Erasure-conformance probe: does {@code player} still own any channel? */
    public boolean hasOwnerRow(UUID player) {
        return table.hasOwnerRow(player);
    }

    /** Does {@code player} appear anywhere — as owner or on any trust list? */
    public boolean hasAnyDataFor(UUID player) {
        return table.hasAnyDataFor(player);
    }

    public int channelCount() {
        return table.size();
    }

    private boolean dirtyIf(boolean changed) {
        if (changed) {
            setDirty();
        }
        return changed;
    }
}
