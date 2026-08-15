package za.co.neroland.neronotes.data;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.config.NeroNotesConfig;

/**
 * The Stage 7 retention sweep: purge NeroNotes' stored data for players
 * inactive longer than {@code data.retention_days} (0 disables the sweep).
 *
 * <p>"Last active" is the per-player last-seen timestamp in
 * {@link ActivityStore}, written on every join (and refreshed for online
 * players at each sweep, so a marathon session can never be purged from
 * under a connected player). The sweep runs shortly after each server start
 * and once per real day thereafter, driven by each loader's server-tick hook
 * calling {@link #onServerTick}.</p>
 *
 * <p>The purge path is the same {@link NeroNotesData#eraseFor} used by an
 * explicit erasure request — deliberately <em>not</em> Core's full
 * {@code PlayerDataErasure.erase} fan-out: NeroNotes' retention config must
 * only ever govern NeroNotes' own data. (Core runs its own ecosystem-wide
 * sweep under its own retention key.) Only anonymous counts are logged.</p>
 */
public final class RetentionSweep {

    /** First sweep of a server run: one minute in, clear of startup work. */
    static final int STARTUP_DELAY_TICKS = 20 * 60;

    /** Repeat interval: one real day at 20 TPS. */
    static final int SWEEP_INTERVAL_TICKS = 20 * 60 * 60 * 24;

    /** Tick counting is per server run; a new server instance restarts it. */
    private static WeakReference<MinecraftServer> currentServer = new WeakReference<>(null);
    private static int ticksThisServer;

    private RetentionSweep() {
    }

    /** Join hook (all three loaders): record that the player was just seen. */
    public static void onPlayerJoin(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            ActivityStore.get(server).touch(player.getUUID());
        }
    }

    /** Server-tick hook (all three loaders): sweep on schedule. Server thread only. */
    public static void onServerTick(MinecraftServer server) {
        if (currentServer.get() != server) {
            currentServer = new WeakReference<>(server);
            ticksThisServer = 0;
        }
        ticksThisServer++;
        if (shouldSweepAt(ticksThisServer)) {
            sweep(server);
        }
    }

    /**
     * Pure schedule decision: the {@link #STARTUP_DELAY_TICKS}th tick of a
     * server run, then every {@link #SWEEP_INTERVAL_TICKS} after that.
     */
    static boolean shouldSweepAt(int tickOfServerRun) {
        return tickOfServerRun == STARTUP_DELAY_TICKS
                || (tickOfServerRun > STARTUP_DELAY_TICKS
                        && (tickOfServerRun - STARTUP_DELAY_TICKS) % SWEEP_INTERVAL_TICKS == 0);
    }

    /**
     * Run one sweep now: refresh online players' timestamps, then purge
     * NeroNotes data for every player whose last-seen is older than the
     * configured window. Returns the number of players purged.
     */
    public static int sweep(MinecraftServer server) {
        int days = NeroNotesConfig.RETENTION_DAYS.get();
        if (days <= 0) {
            return 0;
        }
        ActivityStore activity = ActivityStore.get(server);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            activity.touch(online.getUUID());
        }
        List<UUID> stale = activity.stalerThan(days);
        int purged = 0;
        for (UUID player : stale) {
            if (server.getPlayerList().getPlayer(player) != null) {
                continue; // defensively never purge a connected player
            }
            NeroNotesData.eraseFor(server, player);
            purged++;
        }
        if (purged > 0) {
            NeroNotesCommon.LOGGER.info(
                    "[NeroNotes] retention sweep purged stored data for {} inactive player record(s)", purged);
        }
        return purged;
    }
}
