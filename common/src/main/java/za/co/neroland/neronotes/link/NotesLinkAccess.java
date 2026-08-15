package za.co.neroland.neronotes.link;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neronotes.config.NeroNotesConfig;
import za.co.neroland.neronotes.signal.ChannelAccess;
import za.co.neroland.neronotes.signal.ChannelKey;
import za.co.neroland.neronotes.signal.ResonanceChannel;

/**
 * The one place NeroNotes' link-module visibility rule lives, plus the
 * plumbing every surface needs: the running server handle, the online lookup,
 * the opaque channel reference, and null-safe JSON parameter readers.
 *
 * <p>Core's SPI hands a provider nothing but a {@link UUID}, so the module
 * needs its own server handle. Each loader's server-tick hook calls
 * {@link #rememberServer(MinecraftServer)} beside the retention-sweep tick
 * driver (the same pattern the other Nero link modules use). Before the first
 * world load {@link #server()} answers {@code null}, and every caller must
 * then answer "nothing" rather than guess.</p>
 *
 * <p><b>POPIA/GDPR — the visibility rule.</b>
 * {@link #controllableChannel(UUID, String, List, List)} is the ownership rule
 * for the whole module and it never widens: a channel resolves only out of the
 * requester's OWN owned-or-trusted set, so "not yours" and "does not exist"
 * are the same answer, and no call path can enumerate the server's channels.
 * Operator status is deliberately not honoured — it is a property of a live
 * command source, not of a UUID arriving over a bridge. Channel rows are
 * identified by {@link #channelRef(ChannelKey)}, a one-way name-based UUID
 * over the full identity, so a trusted channel can be referenced without ever
 * emitting its owner's UUID.</p>
 */
final class NotesLinkAccess {

    /**
     * The running server, captured from each loader's server-tick hook.
     * Volatile — written from the server thread, read from whichever thread
     * Core's bridge dispatches on. Re-written every tick, so it self-corrects
     * when a new world is loaded.
     */
    @Nullable
    private static volatile MinecraftServer server;

    private NotesLinkAccess() {
    }

    /** Captures the running server. Called once per server tick from every loader entry point. */
    static void rememberServer(MinecraftServer runningServer) {
        server = runningServer;
    }

    /** Drops the handle if it is still {@code stoppedServer} (server-stopped hook; identity-guarded). */
    static void forgetServer(MinecraftServer stoppedServer) {
        if (server == stoppedServer) {
            server = null;
        }
    }

    /** The running server, or {@code null} before the first world load / after a server stop. */
    @Nullable
    static MinecraftServer server() {
        return server;
    }

    /** Whether the module is switched on right now (re-read on every call, so a config reload takes effect). */
    static boolean enabled() {
        try {
            return Boolean.TRUE.equals(NeroNotesConfig.LINK_MODULE_ENABLED.get());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The requester's live player handle, or {@code null} when they are offline. */
    @Nullable
    static ServerPlayer online(@Nullable MinecraftServer runningServer, @Nullable UUID playerId) {
        if (runningServer == null || playerId == null) {
            return null;
        }
        return runningServer.getPlayerList().getPlayer(playerId);
    }

    /** Whether the requester is online. Reported in every snapshot envelope so a client can say why a section is empty. */
    static boolean isOnline(@Nullable MinecraftServer runningServer, @Nullable UUID playerId) {
        return online(runningServer, playerId) != null;
    }

    /**
     * An opaque, stable reference for one channel: a name-based UUID over the
     * full identity {@code (dimension, ownerUUID, name)}. Deterministic (the
     * app can match rows across snapshots) and one-way (a listener holding a
     * trusted channel's ref learns nothing about its owner — the owner UUID
     * never appears in any link surface).
     */
    static String channelRef(ChannelKey key) {
        String identity = "neronotes-link:" + key.dimension() + "\n" + key.owner() + "\n" + key.name();
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * THE visibility/permission rule for actions: resolve {@code ref} strictly
     * inside the requester's own owned-or-trusted channels, then re-check
     * control through {@link ChannelAccess#canControl(UUID, boolean, ResonanceChannel)}
     * with <b>no operator bypass</b> (the bridge authenticated a token, not a
     * permission). Fails CLOSED: an unknown ref, someone else's channel and a
     * nonexistent channel are all the same empty answer.
     */
    static Optional<ResonanceChannel> controllableChannel(@Nullable UUID requester, @Nullable String ref,
            List<ResonanceChannel> owned, List<ResonanceChannel> trusted) {
        if (requester == null || ref == null || ref.isBlank()) {
            return Optional.empty();
        }
        for (List<ResonanceChannel> pool : List.of(owned, trusted)) {
            for (ResonanceChannel channel : pool) {
                if (ref.equals(channelRef(channel.key()))
                        && ChannelAccess.canControl(requester, false, channel)) {
                    return Optional.of(channel);
                }
            }
        }
        return Optional.empty();
    }

    /** A string parameter, or {@code null} when absent / not a primitive. Never throws. */
    @Nullable
    static String string(@Nullable JsonObject params, String key) {
        if (params == null || !params.has(key)) {
            return null;
        }
        JsonElement element = params.get(key);
        if (!element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        return value == null || value.isEmpty() ? null : value;
    }

    /** A non-negative integer query parameter, or {@code fallback}. Fails closed, never throws. */
    static int pageParam(@Nullable java.util.Map<String, String> params, String key, int fallback) {
        String raw = params == null ? null : params.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value < 0 ? fallback : value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
