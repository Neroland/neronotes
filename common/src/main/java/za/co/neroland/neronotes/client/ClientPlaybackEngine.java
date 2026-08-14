package za.co.neroland.neronotes.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.config.NeroNotesConfig;
import za.co.neroland.neronotes.network.ResonanceClientHandlers;
import za.co.neroland.neronotes.network.ResonanceNotePayload;
import za.co.neroland.neronotes.network.ResonanceTransportPayload;
import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.signal.TransportAction;
import za.co.neroland.neronotes.sync.ChannelPlayhead;
import za.co.neroland.neronotes.sync.PlaybackClock;
import za.co.neroland.neronotes.voice.VoiceDefinition;
import za.co.neroland.neronotes.voice.VoiceFamily;
import za.co.neroland.neronotes.voice.VoiceRegistry;

/**
 * The client playback engine (Stage 3): the real sinks behind
 * {@link ResonanceClientHandlers}, installed once per loader from the client
 * entry point. Everything here runs on the client main thread.
 *
 * <p><strong>Synchronised playback per locked design decision 4</strong> —
 * the server owns the timeline anchor; this engine only ever schedules
 * against it:</p>
 * <ul>
 *   <li>Each channel gets a {@link ChannelPlayhead}. Transport payloads
 *       re-anchor it with latency compensation of half the measured round
 *       trip, clamped to {@code sync.max_latency_compensation_ms} — which is
 *       also exactly the late-join / chunk-reload seek.</li>
 *   <li>The playhead is advanced lazily on payload arrival; measured drift
 *       beyond {@code sync.drift_threshold_ms} produces a hard seek inside
 *       {@link ChannelPlayhead#tick} — playback rate is never adjusted.</li>
 *   <li>A timeline note that is still ahead of the local playhead is handed
 *       to the sound engine's own delayed queue
 *       ({@code SoundManager.playDelayed}), so it fires on its due tick
 *       without the engine needing a tick loop of its own; live and past-due
 *       notes play immediately.</li>
 * </ul>
 *
 * <p><strong>Client comfort keys are honoured here, now</strong> (Stage 3,
 * not later): per-voice-family volume ({@code client.volume.*}), the neon
 * flare particle burst scaled by {@code client.glow_intensity}, and
 * {@code client.mute_other_bases} — notes and playback from channels owned
 * by other players are dropped entirely on this client when set (synced
 * base-wide audio is a griefing vector). Muting is a local audio preference;
 * it makes no authorisation decision.</p>
 */
public final class ClientPlaybackEngine {

    /** Bound on how far ahead a note may be scheduled into the sound engine, in game ticks. */
    private static final int MAX_SCHEDULE_AHEAD_TICKS = 200;

    /** Playhead map bound; stale entries are pruned past this. */
    private static final int MAX_TRACKED_CHANNELS = 64;

    /** Channels idle for this many game ticks are prunable. */
    private static final int STALE_AFTER_TICKS = 1200;

    /** A channel as the client tracks it — dimension is implicit (delivery is same-dimension only). */
    private record ChannelId(UUID owner, String name) {
    }

    private static final class ChannelState {
        final ChannelPlayhead playhead = new ChannelPlayhead();
        long lastTickedGameTick = Long.MIN_VALUE;
    }

    private final Map<ChannelId, ChannelState> channels = new HashMap<>();

    private ClientPlaybackEngine() {
    }

    /** Install the engine as the resonance payload sinks — client init, all loaders. */
    public static void install() {
        ClientPlaybackEngine engine = new ClientPlaybackEngine();
        ResonanceClientHandlers.setNoteSink(engine::handleNote);
        ResonanceClientHandlers.setTransportSink(engine::handleTransport);
        NeroNotesCommon.LOGGER.info("[NeroNotes] client playback engine installed");
    }

    // ------------------------------------------------------------------
    // Transport: anchor, seek, stop
    // ------------------------------------------------------------------

    private void handleTransport(ResonanceTransportPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        ChannelId id = new ChannelId(payload.owner(), payload.channelName());
        if (payload.action() == TransportAction.STOP) {
            channels.remove(id);
            return;
        }
        if (isMuted(minecraft, payload.owner())) {
            channels.remove(id); // muted channels are not tracked at all
            return;
        }
        long now = level.getGameTime();
        ChannelState state = channels.computeIfAbsent(id, ignored -> new ChannelState());
        // Re-anchoring IS the hard seek: play, seek, late join and chunk
        // reload all land here with the same compensated arithmetic.
        state.playhead.applyTransport(payload.action(), payload.positionTick(), payload.anchorGameTick(),
                payload.tempoBpm(), payload.ticksPerBeat(), now, compensationMs(minecraft));
        state.lastTickedGameTick = now;
        prune(now);
    }

    // ------------------------------------------------------------------
    // Notes: render one-shots through the voice registry
    // ------------------------------------------------------------------

    private void handleNote(ResonanceNotePayload payload) {
        if (!payload.noteOn() || payload.velocity() <= 0) {
            return; // 0.1.0 renders note_on one-shots only
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        if (isMuted(minecraft, payload.owner())) {
            return;
        }
        VoiceDefinition voice = VoiceRegistry.shared().resolve(payload.voiceId());
        float volume = (float) (familyVolume(voice.family()) * payload.velocity() / Score.MAX_VELOCITY);
        if (volume <= 0.0f) {
            return;
        }
        long now = level.getGameTime();
        int delayTicks = scheduleDelayTicks(payload, minecraft, now);

        BlockPos origin = payload.origin();
        SimpleSoundInstance instance = new SimpleSoundInstance(
                voice.soundEventId(), SoundSource.RECORDS, volume, pitchMultiplier(voice, payload.pitch()),
                RandomSource.create(), false, 0, SoundInstance.Attenuation.LINEAR,
                origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, false);
        if (delayTicks <= 0) {
            minecraft.getSoundManager().play(instance);
        } else {
            minecraft.getSoundManager().playDelayed(instance, delayTicks);
        }
        spawnFlare(level, origin);
    }

    /**
     * How many game ticks ahead of the local playhead a timeline note is —
     * {@code 0} (play now) for live notes, past-due notes, and channels with
     * no tracked anchor. Advancing the playhead first applies the lazy tick
     * with its drift-seek, so the answer is always measured against the
     * compensated server timeline.
     */
    private int scheduleDelayTicks(ResonanceNotePayload payload, Minecraft minecraft, long now) {
        if (payload.isLive()) {
            return 0;
        }
        ChannelState state = channels.get(new ChannelId(payload.owner(), payload.channelName()));
        if (state == null || !state.playhead.isPlaying()) {
            return 0; // no anchor (e.g. joined between anchors): the server's pacing is the schedule
        }
        advance(state, now, minecraft);
        double rate = state.playhead.rate();
        if (rate <= 0.0) {
            return 0;
        }
        double aheadScoreTicks = payload.scoreTick() - state.playhead.positionTicks();
        if (aheadScoreTicks <= 0.0) {
            return 0;
        }
        return (int) Math.min(MAX_SCHEDULE_AHEAD_TICKS, Math.ceil(aheadScoreTicks / rate));
    }

    /**
     * Lazy per-tick advance: at most one {@link ChannelPlayhead#tick} per game
     * tick. The tick's built-in drift evaluation hard-seeks to the expected
     * position whenever drift exceeds {@code sync.drift_threshold_ms}, so a
     * gap in arrivals corrects itself on the next event — by seeking, never by
     * a rate change.
     */
    private void advance(ChannelState state, long now, Minecraft minecraft) {
        if (state.lastTickedGameTick == now) {
            return;
        }
        state.playhead.tick(now, compensationMs(minecraft), NeroNotesConfig.SYNC_DRIFT_THRESHOLD_MS.get());
        state.lastTickedGameTick = now;
    }

    // ------------------------------------------------------------------
    // Client comfort keys (honoured NOW, Stage 3)
    // ------------------------------------------------------------------

    /** {@code client.mute_other_bases}: local audio preference, not an authorisation decision. */
    private static boolean isMuted(Minecraft minecraft, UUID owner) {
        return NeroNotesConfig.MUTE_OTHER_BASES.get()
                && minecraft.player != null
                && !owner.equals(minecraft.player.getUUID());
    }

    private static double familyVolume(VoiceFamily family) {
        return switch (family) {
            case DEEP_BASS -> NeroNotesConfig.VOLUME_DEEP_BASS.get();
            case SUB_PAD -> NeroNotesConfig.VOLUME_SUB_PAD.get();
            case LOW_DRONE -> NeroNotesConfig.VOLUME_LOW_DRONE.get();
            case HIGH_LEAD -> NeroNotesConfig.VOLUME_HIGH_LEAD.get();
            case GLASSY_PLUCK -> NeroNotesConfig.VOLUME_GLASSY_PLUCK.get();
            case PERCUSSION -> NeroNotesConfig.VOLUME_PERCUSSION.get();
            case SYNTH_TEXTURE -> NeroNotesConfig.VOLUME_SYNTH_TEXTURE.get();
        };
    }

    /** The neon flare, client-side: an END_ROD burst scaled by {@code client.glow_intensity}. */
    private static void spawnFlare(ClientLevel level, BlockPos origin) {
        int count = (int) Math.round(3.0 * NeroNotesConfig.GLOW_INTENSITY.get());
        RandomSource random = level.getRandom();
        for (int i = 0; i < count; i++) {
            level.addParticle(ParticleTypes.END_ROD,
                    origin.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                    origin.getY() + 1.05,
                    origin.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                    0.0, 0.02, 0.0);
        }
    }

    // ------------------------------------------------------------------
    // Sync math inputs
    // ------------------------------------------------------------------

    /** Clamped RTT/2 latency compensation in ms (cap = {@code sync.max_latency_compensation_ms}). */
    private static long compensationMs(Minecraft minecraft) {
        return PlaybackClock.clampedCompensationMs(roundTripMs(minecraft),
                NeroNotesConfig.SYNC_MAX_LATENCY_COMPENSATION_MS.get());
    }

    /** The measured round trip: the local player's tab-list latency, {@code 0} if unknown. */
    private static long roundTripMs(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return 0L;
        }
        PlayerInfo info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
        return info == null ? 0L : info.getLatency();
    }

    /**
     * MIDI-style pitch to playback-speed multiplier, centred on the voice's
     * band midpoint and clamped to the sound engine's usable range.
     */
    private static float pitchMultiplier(VoiceDefinition voice, int pitch) {
        double center = (voice.minPitch() + voice.maxPitch()) / 2.0;
        double multiplier = Math.pow(2.0, (voice.clampToBand(pitch) - center) / 12.0);
        return (float) Math.max(0.5, Math.min(2.0, multiplier));
    }

    private void prune(long now) {
        if (channels.size() <= MAX_TRACKED_CHANNELS) {
            return;
        }
        channels.values().removeIf(state -> now - state.lastTickedGameTick > STALE_AFTER_TICKS);
    }
}
