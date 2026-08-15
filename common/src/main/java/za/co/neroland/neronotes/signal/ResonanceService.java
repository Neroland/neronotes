package za.co.neroland.neronotes.signal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.block.ResonantBlockIndex;
import za.co.neroland.neronotes.config.NeroNotesConfig;
import za.co.neroland.neronotes.integration.NotesThresholds;
import za.co.neroland.neronotes.link.NotesLinkEvents;
import za.co.neroland.neronotes.network.ResonanceNotePayload;
import za.co.neroland.neronotes.network.ResonanceTransportPayload;
import za.co.neroland.neronotes.platform.Services;

/**
 * The server-side resonance signal (init step 9): channel management,
 * server-tracked subscriptions, and ranged broadcast of note and transport
 * events to subscribed clients. No block updates, no wiring, no scanning —
 * emitters call in, subscribed nearby clients hear about it.
 *
 * <p><strong>Everything here is server-authoritative.</strong> Every control
 * operation authorises through {@link ChannelAccess} (owner / trust list /
 * operator — never proximity); refusals are quiet ({@link SignalResult}), the
 * caller decides whether to surface a message. The audio-spam guard
 * ({@link ChannelConcurrencyGuard}) is enforced here on {@code play}.</p>
 *
 * <p>Stage 3 adds the <em>machine emitter</em> paths ({@code emitNoteAs} /
 * {@code transportAs}): a Resonator keeps playing while its owner is offline,
 * so block entities authorise by their stored owner UUID — recorded
 * server-side at placement, never client-asserted, and never an operator
 * bypass (machines are not ops). It also adds {@link #emitLocalNote}: a
 * channel-less one-shot for Resonant Block taps, no more privileged than a
 * vanilla note block.</p>
 *
 * <p>Subscriber sets and playing state are runtime-only; persisted channel
 * state (ownership + trust) lives in {@link ChannelStore} behind Core's
 * recovery guard. All methods must be called on the server thread.</p>
 */
public final class ResonanceService {

    /** Quiet outcome of a signal operation — no chat spam, callers decide messaging. */
    public enum SignalResult {
        OK,
        /** Requester is not owner, trusted, or operator. */
        DENIED,
        /** No such channel. */
        UNKNOWN_CHANNEL,
        /** Audio-spam guard refused a {@code play} (cap per chunk radius reached). */
        CHANNEL_CAP_REACHED,
        /** Invalid or duplicate name on create/rename. */
        REJECTED_NAME,
        /** {@code seek} on a channel that is not playing. */
        NOT_PLAYING
    }

    /** The channel name used on live local-note payloads (no real channel involved). */
    public static final String LOCAL_CHANNEL_NAME = "local";

    private static final Map<ChannelKey, Set<UUID>> SUBSCRIBERS = new HashMap<>();
    private static final ChannelConcurrencyGuard GUARD = new ChannelConcurrencyGuard(
            () -> NeroNotesConfig.MAX_PLAYING_CHANNELS_PER_CHUNK_RADIUS.get());

    private ResonanceService() {
    }

    /** Init step 9 — nothing to resolve, the class just announces itself. */
    public static void init() {
        NeroNotesCommon.LOGGER.debug("[NeroNotes] resonance channel service ready");
    }

    // ------------------------------------------------------------------
    // Identity helpers
    // ------------------------------------------------------------------

    /** The dimension id string used in {@link ChannelKey}s, e.g. {@code minecraft:overworld}. */
    public static String dimensionId(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    /** Build a channel key for {@code owner} in {@code level}'s dimension. */
    public static ChannelKey keyFor(ServerLevel level, UUID owner, String name) {
        return new ChannelKey(dimensionId(level), owner, name);
    }

    // ------------------------------------------------------------------
    // Channel management (server-authoritative)
    // ------------------------------------------------------------------

    /** Create a channel owned by {@code creator} in {@code level}'s dimension. */
    public static SignalResult createChannel(ServerLevel level, ServerPlayer creator, String name) {
        if (!ChannelNames.isValid(name)) {
            return SignalResult.REJECTED_NAME;
        }
        ChannelKey key = keyFor(level, creator.getUUID(), name);
        return ChannelStore.get(level.getServer()).create(key)
                ? SignalResult.OK
                : SignalResult.REJECTED_NAME; // duplicate identity
    }

    /**
     * Ensure a channel exists for {@code owner} — used by a Resonator binding
     * itself to its placer's channel at placement. Creating a channel for
     * yourself needs no further authorisation; the owner identity comes from
     * the server-recorded placer, never the client.
     */
    public static SignalResult ensureChannel(ServerLevel level, UUID owner, String name) {
        if (!ChannelNames.isValid(name)) {
            return SignalResult.REJECTED_NAME;
        }
        ChannelKey key = keyFor(level, owner, name);
        ChannelStore store = ChannelStore.get(level.getServer());
        if (store.channel(key).isPresent()) {
            return SignalResult.OK;
        }
        return store.create(key) ? SignalResult.OK : SignalResult.REJECTED_NAME;
    }

    /** Rename — owner, trust list, or operator (a control operation). */
    public static SignalResult rename(ServerLevel level, ServerPlayer requester, ChannelKey key, String newName) {
        ChannelStore store = ChannelStore.get(level.getServer());
        Optional<ResonanceChannel> channel = store.channel(key);
        if (channel.isEmpty()) {
            return SignalResult.UNKNOWN_CHANNEL;
        }
        if (!ChannelAccess.canControl(requester, channel.get())) {
            return SignalResult.DENIED;
        }
        if (!store.rename(key, newName)) {
            return SignalResult.REJECTED_NAME;
        }
        // Runtime state follows the identity.
        ChannelKey newKey = key.withName(newName);
        Set<UUID> subs = SUBSCRIBERS.remove(key);
        if (subs != null) {
            SUBSCRIBERS.put(newKey, subs);
        }
        if (GUARD.isPlaying(key)) {
            GUARD.stopPlaying(key); // a rename mid-play simply re-arms on the next play
        }
        return SignalResult.OK;
    }

    /** Delete — owner or operator only. Clears runtime state for the channel. */
    public static SignalResult delete(ServerLevel level, ServerPlayer requester, ChannelKey key) {
        ChannelStore store = ChannelStore.get(level.getServer());
        Optional<ResonanceChannel> channel = store.channel(key);
        if (channel.isEmpty()) {
            return SignalResult.UNKNOWN_CHANNEL;
        }
        if (!ChannelAccess.canManage(requester, channel.get())) {
            return SignalResult.DENIED;
        }
        store.delete(key);
        SUBSCRIBERS.remove(key);
        GUARD.stopPlaying(key);
        return SignalResult.OK;
    }

    /** Trust-list addition — owner or operator only (trust grants control, not management). */
    public static SignalResult trust(ServerLevel level, ServerPlayer requester, ChannelKey key, UUID target) {
        ChannelStore store = ChannelStore.get(level.getServer());
        Optional<ResonanceChannel> channel = store.channel(key);
        if (channel.isEmpty()) {
            return SignalResult.UNKNOWN_CHANNEL;
        }
        if (!ChannelAccess.canManage(requester, channel.get())) {
            return SignalResult.DENIED;
        }
        store.trust(key, target);
        return SignalResult.OK;
    }

    /** Trust-list removal — owner or operator only. */
    public static SignalResult untrust(ServerLevel level, ServerPlayer requester, ChannelKey key, UUID target) {
        ChannelStore store = ChannelStore.get(level.getServer());
        Optional<ResonanceChannel> channel = store.channel(key);
        if (channel.isEmpty()) {
            return SignalResult.UNKNOWN_CHANNEL;
        }
        if (!ChannelAccess.canManage(requester, channel.get())) {
            return SignalResult.DENIED;
        }
        store.untrust(key, target);
        return SignalResult.OK;
    }

    // ------------------------------------------------------------------
    // Subscriptions (server-tracked; listening is not gated)
    // ------------------------------------------------------------------

    /** Subscribe a player to a channel's events. Anyone may listen; range still applies per event. */
    public static void subscribe(ChannelKey key, ServerPlayer player) {
        Set<UUID> subs = SUBSCRIBERS.computeIfAbsent(key, ignored -> new HashSet<>());
        int before = subs.size();
        if (subs.add(player.getUUID())) {
            // Stage 8: listener-milestone crossings on Core's ThresholdEvents.
            // Scope is the channel's DIMENSION id only — a place key; the
            // owner UUID and channel name never leave this method. Server
            // thread: subscribe is only ever called from server-side hooks.
            NotesThresholds.listenerCountChanged(key.dimension(), before, subs.size());
        }
    }

    public static void unsubscribe(ChannelKey key, ServerPlayer player) {
        Set<UUID> subs = SUBSCRIBERS.get(key);
        if (subs != null) {
            subs.remove(player.getUUID());
            if (subs.isEmpty()) {
                SUBSCRIBERS.remove(key);
            }
        }
    }

    /** Drop a player from every channel (disconnect housekeeping). */
    public static void unsubscribeAll(UUID player) {
        SUBSCRIBERS.values().forEach(subs -> subs.remove(player));
        SUBSCRIBERS.values().removeIf(Set::isEmpty);
    }

    public static int subscriberCount(ChannelKey key) {
        Set<UUID> subs = SUBSCRIBERS.get(key);
        return subs == null ? 0 : subs.size();
    }

    // ------------------------------------------------------------------
    // Emitting (auth + ranged broadcast)
    // ------------------------------------------------------------------

    /**
     * Broadcast a {@code note_on} / {@code note_off} on a channel from
     * {@code origin}, requested by a live player. Requires control of the
     * channel (owner / trust / operator). Live notes only — timeline notes
     * come from machine emitters via
     * {@link #emitNoteAs(ServerLevel, UUID, ChannelKey, Vec3, boolean, String, int, int, long)}.
     */
    public static SignalResult emitNote(ServerLevel level, ServerPlayer requester, ChannelKey key, Vec3 origin,
                                        boolean noteOn, String voiceId, int pitch, int velocity) {
        Optional<ResonanceChannel> channel = ChannelStore.get(level.getServer()).channel(key);
        if (channel.isEmpty()) {
            return SignalResult.UNKNOWN_CHANNEL;
        }
        if (!ChannelAccess.canControl(requester, channel.get())) {
            return SignalResult.DENIED;
        }
        deliverNote(level, origin, key, noteOn, voiceId, pitch, velocity, ResonanceNotePayload.LIVE_NOTE);
        return SignalResult.OK;
    }

    /**
     * Machine-emitter variant: a block entity (Resonator) emitting as its
     * server-recorded owner. Authorises by UUID against the channel's owner
     * and trust list — <strong>no operator bypass</strong>, machines are not
     * ops. {@code scoreTick} is the note's timeline position (or
     * {@link ResonanceNotePayload#LIVE_NOTE}).
     */
    public static SignalResult emitNoteAs(ServerLevel level, UUID emitter, ChannelKey key, Vec3 origin,
                                          boolean noteOn, String voiceId, int pitch, int velocity,
                                          long scoreTick) {
        Optional<ResonanceChannel> channel = ChannelStore.get(level.getServer()).channel(key);
        if (channel.isEmpty()) {
            return SignalResult.UNKNOWN_CHANNEL;
        }
        if (!ChannelAccess.canControl(emitter, false, channel.get())) {
            return SignalResult.DENIED;
        }
        deliverNote(level, origin, key, noteOn, voiceId, pitch, velocity, scoreTick);
        return SignalResult.OK;
    }

    /**
     * A live, channel-less one-shot from a Resonant Block tap: delivered to
     * every player in range (no subscription), never persisted, no more
     * privileged than a vanilla note block — which is why it needs no channel
     * authorisation. {@code source} is the interacting player (server-side
     * identity), carried so clients can apply their "mute other players'
     * bases" preference to it.
     */
    public static void emitLocalNote(ServerLevel level, UUID source, Vec3 origin,
                                     String voiceId, int pitch, int velocity) {
        ResonanceNotePayload payload = new ResonanceNotePayload(source, LOCAL_CHANNEL_NAME, true,
                voiceId, pitch, velocity, BlockPos.containing(origin), ResonanceNotePayload.LIVE_NOTE);
        int range = emitRangeBlocks();
        double rangeSq = (double) range * range;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(origin) <= rangeSq) {
                Services.network().sendToPlayer(player, payload);
            }
        }
    }

    /**
     * Broadcast a transport event ({@code play} / {@code stop} /
     * {@code seek}) on a channel from {@code origin}, requested by a live
     * player. Requires control of the channel; {@code play} additionally
     * passes the audio-spam guard (quiet refusal). {@code tempoBpm} /
     * {@code ticksPerBeat} describe the playing score (pass {@code 0, 0} for
     * {@code stop}); together with the server game tick they form the
     * timeline anchor clients schedule against.
     */
    public static SignalResult transport(ServerLevel level, ServerPlayer requester, ChannelKey key,
                                         TransportAction action, long positionTick,
                                         int tempoBpm, int ticksPerBeat, Vec3 origin) {
        Optional<ResonanceChannel> channel = ChannelStore.get(level.getServer()).channel(key);
        if (channel.isEmpty()) {
            return SignalResult.UNKNOWN_CHANNEL;
        }
        if (!ChannelAccess.canControl(requester, channel.get())) {
            return SignalResult.DENIED;
        }
        return applyTransport(level, key, action, positionTick, tempoBpm, ticksPerBeat, origin);
    }

    /**
     * Machine-emitter transport: the Resonator's tick schedule re-arming,
     * re-anchoring or stopping as its server-recorded owner. UUID
     * authorisation, no operator bypass — see {@link #emitNoteAs}.
     */
    public static SignalResult transportAs(ServerLevel level, UUID emitter, ChannelKey key,
                                           TransportAction action, long positionTick,
                                           int tempoBpm, int ticksPerBeat, Vec3 origin) {
        Optional<ResonanceChannel> channel = ChannelStore.get(level.getServer()).channel(key);
        if (channel.isEmpty()) {
            return SignalResult.UNKNOWN_CHANNEL;
        }
        if (!ChannelAccess.canControl(emitter, false, channel.get())) {
            return SignalResult.DENIED;
        }
        return applyTransport(level, key, action, positionTick, tempoBpm, ticksPerBeat, origin);
    }

    /**
     * Send the current anchor to a single player — how a Resonator catches a
     * late joiner up the moment it subscribes them, instead of leaving them
     * silent until the next periodic re-anchor. Delivery only; the channel
     * must already be playing (no auth decision is made here and none is
     * needed — an anchor is information every listener receives anyway).
     */
    public static void sendAnchorTo(ServerPlayer player, ChannelKey key, long positionTick,
                                    long anchorGameTick, int tempoBpm, int ticksPerBeat) {
        if (!GUARD.isPlaying(key)) {
            return;
        }
        Services.network().sendToPlayer(player, new ResonanceTransportPayload(
                key.owner(), key.name(), TransportAction.PLAY, positionTick, anchorGameTick,
                0, tempoBpm, ticksPerBeat));
    }

    /** Whether the audio-spam guard currently counts this channel as playing. */
    public static boolean isPlaying(ChannelKey key) {
        return GUARD.isPlaying(key);
    }

    /** Forget all runtime state (server stop / tests). Persisted channels are untouched. */
    public static void clearRuntime() {
        SUBSCRIBERS.clear();
        GUARD.clear();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static SignalResult applyTransport(ServerLevel level, ChannelKey key, TransportAction action,
                                               long positionTick, int tempoBpm, int ticksPerBeat, Vec3 origin) {
        boolean wasPlaying = GUARD.isPlaying(key);
        switch (action) {
            case PLAY -> {
                if (!GUARD.tryStartPlaying(key, anchorOf(level, origin), capChunkRadius())) {
                    return SignalResult.CHANNEL_CAP_REACHED; // quiet — no chat, no log spam
                }
            }
            case STOP -> GUARD.stopPlaying(key);
            case SEEK -> {
                if (!wasPlaying) {
                    return SignalResult.NOT_PLAYING;
                }
            }
        }
        long anchorGameTick = level.getGameTime();
        broadcast(level, origin, key, new ResonanceTransportPayload(
                key.owner(), key.name(), action, positionTick, anchorGameTick, 0, tempoBpm, ticksPerBeat));
        // Stage 9: owner-scoped now_playing link events, on GENUINE transitions
        // only — a re-anchor, seek or repeated play/stop never fires one. The
        // event goes to the channel owner; NotesLinkEvents no-ops when the link
        // module is disabled or absent.
        if (action == TransportAction.PLAY && !wasPlaying) {
            NotesLinkEvents.nowPlayingChanged(key, true, subscriberCount(key));
        } else if (action == TransportAction.STOP && wasPlaying) {
            NotesLinkEvents.nowPlayingChanged(key, false, subscriberCount(key));
        }
        return SignalResult.OK;
    }

    /** Shared note delivery: payload broadcast + the Resonant Block flare hook. */
    private static void deliverNote(ServerLevel level, Vec3 origin, ChannelKey key, boolean noteOn,
                                    String voiceId, int pitch, int velocity, long scoreTick) {
        broadcast(level, origin, key, new ResonanceNotePayload(
                key.owner(), key.name(), noteOn, voiceId, pitch, velocity,
                BlockPos.containing(origin), scoreTick));
        if (noteOn) {
            // Loaded Resonant Blocks near the origin flare and adopt the pitch
            // (bounded index lookup, never a world scan).
            ResonantBlockIndex.onChannelNote(level, origin, voiceId, pitch);
        }
    }

    /** Configured emit range in blocks, defensively clamped to the schema bounds. */
    private static int emitRangeBlocks() {
        int range = NeroNotesConfig.EMIT_RANGE_BLOCKS.get();
        return Math.max(16, Math.min(range, 128));
    }

    /** The spam-guard radius in chunks, derived from the emit range. */
    private static int capChunkRadius() {
        return (emitRangeBlocks() + 15) / 16;
    }

    private static ChannelConcurrencyGuard.Anchor anchorOf(ServerLevel level, Vec3 origin) {
        return new ChannelConcurrencyGuard.Anchor(dimensionId(level),
                (int) Math.floor(origin.x()) >> 4, (int) Math.floor(origin.z()) >> 4);
    }

    /**
     * Deliver a payload to every subscribed, online player in {@code level}'s
     * dimension within the configured emit range of {@code origin}. Range and
     * dimension are delivery filters only — never authorisation.
     */
    private static void broadcast(ServerLevel level, Vec3 origin, ChannelKey key, CustomPacketPayload payload) {
        Set<UUID> subs = SUBSCRIBERS.get(key);
        if (subs == null || subs.isEmpty()) {
            return;
        }
        int range = emitRangeBlocks();
        double rangeSq = (double) range * range;
        for (UUID id : List.copyOf(subs)) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player == null) {
                continue; // offline — subscription survives, delivery skips
            }
            if (!player.level().dimension().equals(level.dimension())) {
                continue;
            }
            if (player.distanceToSqr(origin) > rangeSq) {
                continue;
            }
            Services.network().sendToPlayer(player, payload);
        }
    }
}
