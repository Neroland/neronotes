package za.co.neroland.neronotes.data;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.library.LibraryStore;
import za.co.neroland.neronotes.signal.ChannelStore;
import za.co.neroland.neronotes.signal.ResonanceService;
import za.co.neroland.neronotes.soundforge.SoundforgeSessionStore;

/**
 * NeroNotes' POPIA / GDPR erasure wiring — init step 7, <strong>early on
 * purpose</strong>: registering late is how an erasure request silently
 * misses a store.
 *
 * <p>One {@code PlayerDataEraser} registered with Core's shared
 * {@link PlayerDataErasure} covers everything NeroNotes stores about a
 * player:</p>
 *
 * <ol>
 *   <li><strong>Library</strong> — "sever the link, keep the work":
 *       {@link LibraryStore#anonymiseAuthorAndBackup} strips the author UUID
 *       and display name and marks the entries anonymous; the compositions —
 *       and every disk other players copied from them — keep working.</li>
 *   <li><strong>Soundforge sessions</strong> — the whole row (return anchor,
 *       inside-flag, session score) is removed.</li>
 *   <li><strong>Channels</strong> — every channel the player owns is deleted
 *       and the player is stripped from every other channel's trust list.</li>
 *   <li><strong>Activity</strong> — the last-seen record backing the
 *       retention sweep is removed.</li>
 *   <li><strong>Live runtime state</strong> — the player is dropped from
 *       every channel subscription.</li>
 * </ol>
 *
 * <p>Each store's {@code ...AndBackup} entry point pushes the erased state to
 * Core's {@link za.co.neroland.nerolandcore.data.SavedDataRecovery} backup in
 * the same request — the backup is a second copy of the same rows and erasure
 * has to reach it.</p>
 *
 * <p><strong>Not reached by erasure:</strong> pressed disks already in
 * circulation (item components in player inventories and containers) are
 * world data the server cannot enumerate; the library is the authoritative
 * anonymisation point. The decision and its reasoning live in
 * {@code PRIVACY.md}.</p>
 */
public final class NeroNotesData {

    private NeroNotesData() {
    }

    /** Init step 7 of {@code NeroNotesCommon.init()} — register the eraser with Core. */
    public static void init() {
        PlayerDataErasure.register(NeroNotesData::eraseFor);
        NeroNotesCommon.LOGGER.debug("[NeroNotes] player-data eraser registered with Core");
    }

    /**
     * Purge everything NeroNotes stores for {@code player}. Called by Core's
     * {@link PlayerDataErasure#erase} fan-out (an explicit request or Core's
     * own retention sweep) and by NeroNotes' {@link RetentionSweep}. Never
     * logs the player's identity — a UUID is personal data.
     */
    public static void eraseFor(MinecraftServer server, UUID player) {
        LibraryStore.get(server).anonymiseAuthorAndBackup(server, player);
        SoundforgeSessionStore.get(server).purgePlayerAndBackup(server, player);
        ChannelStore.get(server).purgePlayerAndBackup(server, player);
        ActivityStore.get(server).purgePlayerAndBackup(server, player);
        // Live runtime state (subscriptions are runtime-only; deleted channels
        // silence any Resonator still bound to them on its next emit).
        ResonanceService.unsubscribeAll(player);
    }
}
