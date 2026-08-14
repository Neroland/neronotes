package za.co.neroland.neronotes.soundforge;

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
 * The persisted per-player Soundforge session state — return anchors, the
 * inside-flag, and the Stage 5 session-data slot — as a {@link SavedData}
 * stored on the overworld (the Soundforge itself may be absent or reset;
 * anchors must survive that).
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
public final class SoundforgeSessionStore extends SavedData {

    /** Stable, non-identifying recovery/backup label — {@code <modid>:<store>}. */
    public static final String RECOVERY_NAME = "neronotes:soundforge_sessions";

    private static final Codec<SoundforgeSessionStore> CODEC =
            CompoundTag.CODEC.xmap(SoundforgeSessionStore::fromTag, SoundforgeSessionStore::toTag);

    private static final Supplier<SoundforgeSessionStore> FACTORY = SoundforgeSessionStore::new;

    // The four-argument constructor is the one present on every loader/MC cell
    // (the three-argument convenience overload is a NeoForge-only patch). A null
    // DataFixTypes means "no datafixing" — correct for mod data with its own
    // versioning discipline.
    public static final SavedDataType<SoundforgeSessionStore> TYPE = new SavedDataType<SoundforgeSessionStore>(
            Identifier.fromNamespaceAndPath(NeroNotesCommon.MOD_ID, "soundforge_sessions"),
            FACTORY,
            CODEC,
            null);

    private final SoundforgeSessionTable table;

    public SoundforgeSessionStore() {
        this(new SoundforgeSessionTable());
    }

    private SoundforgeSessionStore(SoundforgeSessionTable table) {
        this.table = table;
    }

    private static SoundforgeSessionStore fromTag(CompoundTag tag) {
        return new SoundforgeSessionStore(SoundforgeSessionTable.load(tag));
    }

    private CompoundTag toTag() {
        return table.save();
    }

    /**
     * The one accessor. Routed through Core's recovery guard; the store lives
     * on the overworld regardless of which dimension the player entered from.
     */
    public static SoundforgeSessionStore get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, SoundforgeSessionStore::new, RECOVERY_NAME);
    }

    // ------------------------------------------------------------------
    // Mutations (delegate to the table; mark dirty on change)
    // ------------------------------------------------------------------

    public boolean beginSession(UUID player, ReturnAnchor anchor, long gameTime) {
        return dirtyIf(table.beginSession(player, anchor, gameTime));
    }

    public boolean markOutside(UUID player) {
        return dirtyIf(table.markOutside(player));
    }

    /** Stage 5 seam: store (or clear) the opaque session data for {@code player}. */
    public boolean putSessionData(UUID player, CompoundTag data) {
        return dirtyIf(table.putSessionData(player, data));
    }

    /**
     * Per-player purge (the whole row: anchor, flags, session data), for
     * Core's erasure hook (registered in Stage 7). Prefer
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

    public Optional<ReturnAnchor> returnAnchor(UUID player) {
        return table.returnAnchor(player);
    }

    public boolean isInside(UUID player) {
        return table.isInside(player);
    }

    public Optional<CompoundTag> sessionData(UUID player) {
        return table.sessionData(player);
    }

    /** Erasure-conformance probe: does {@code player} still have a session row? */
    public boolean hasRow(UUID player) {
        return table.hasRow(player);
    }

    public int sessionCount() {
        return table.size();
    }

    private boolean dirtyIf(boolean changed) {
        if (changed) {
            setDirty();
        }
        return changed;
    }
}
