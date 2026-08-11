package za.co.neroland.neronotes.block.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.block.ResonatorBlock;
import za.co.neroland.neronotes.config.NeroNotesConfig;
import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.score.ScoreCodec;
import za.co.neroland.neronotes.score.ScoreFormatException;
import za.co.neroland.neronotes.signal.ChannelKey;
import za.co.neroland.neronotes.signal.ChannelNames;
import za.co.neroland.neronotes.signal.ResonanceService;
import za.co.neroland.neronotes.signal.ResonanceService.SignalResult;
import za.co.neroland.neronotes.signal.TransportAction;
import za.co.neroland.neronotes.sync.PlaybackClock;

/**
 * The Resonator: NeroNotes' disk player. It binds to a resonance channel
 * owned by its placer, holds a {@link Score} (written by Stage 5's disk
 * items; playable from NBT already), and drives <strong>server-side</strong>
 * playback: a tick schedule that reads the score timeline and emits note and
 * transport events through {@link ResonanceService}, as its owner — so it
 * keeps playing while the owner is offline, and a corrupt or deleted channel
 * quietly stops it.
 *
 * <p><strong>The server owns the timeline</strong> (locked design
 * decision 4): this entity is the source of the anchor. It re-anchors
 * every {@value #ANCHOR_INTERVAL_TICKS} ticks, re-arms itself through the
 * audio-spam guard after a server restart or chunk reload (resuming at the
 * persisted position — a reload is a seek, not a restart), and hands each
 * newly subscribed nearby player the current anchor immediately so late
 * joiners seek to the current position rather than waiting for the next
 * periodic anchor.</p>
 *
 * <p>Stage 5 API surface: {@link #setScore(Score)}, {@link #score()},
 * {@link #bindChannel(String)}, {@link #startPlayback(ServerPlayer)},
 * {@link #stopPlayback(ServerPlayer)}, {@link #togglePlayback(ServerPlayer)}.
 * 0.1.0 renders {@code note_on} one-shots only; note lengths are carried in
 * the score but not yet voiced as sustained {@code note_off} pairs.</p>
 */
public class ResonatorBlockEntity extends BlockEntity {

    /** Default channel a Resonator binds to at placement. */
    public static final String DEFAULT_CHANNEL_NAME = "base";

    /** Periodic re-anchor interval (game ticks) while playing. */
    public static final int ANCHOR_INTERVAL_TICKS = 100;

    /** Nearby-player subscription sweep interval (game ticks). */
    public static final int SUBSCRIBE_INTERVAL_TICKS = 20;

    /** Neon-ring pulse length after a note burst (game ticks). */
    public static final int PULSE_TICKS = 3;

    /** Bound on timeline catch-up after a lag spike, in score ticks. */
    private static final long MAX_CATCH_UP_TICKS = 1000;

    private static final String KEY_OWNER = "owner";
    private static final String KEY_CHANNEL = "channel";
    private static final String KEY_SCORE = "score";
    private static final String KEY_PLAYING = "playing";
    private static final String KEY_POSITION = "position";

    private UUID owner;
    private String channelName = DEFAULT_CHANNEL_NAME;
    private Score score;
    private boolean playing;
    private double positionTicks;
    private long lastEmittedTick = -1;

    // Runtime-only.
    private TreeMap<Long, List<TimelineNote>> timeline;
    private long scoreEndTick;
    private final Set<UUID> knownListeners = new HashSet<>();
    private int subscribeCooldown;
    private int anchorCooldown;
    private int pulseTicksLeft;

    private record TimelineNote(String voiceId, int pitch, int velocity) {
    }

    public ResonatorBlockEntity(BlockPos pos, BlockState state) {
        super(NeroNotesBlockEntities.RESONATOR.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // Stage 5 API surface
    // ------------------------------------------------------------------

    /** The stored score, or {@code null} when no disk has been written. */
    public Score score() {
        return score;
    }

    /**
     * Store (or clear, with {@code null}) the score this Resonator plays —
     * the seam Stage 5's disk handling writes through. Stops playback and
     * rewinds; the caller decides whether to start again.
     */
    public void setScore(Score newScore) {
        stopQuietly();
        this.score = newScore;
        this.positionTicks = 0;
        this.lastEmittedTick = -1;
        rebuildTimeline();
        setChanged();
    }

    /** The server-recorded owner (placer), or {@code null} if placed by no player. */
    public UUID owner() {
        return owner;
    }

    /** The bound channel name (owner-scoped; see {@link ChannelKey}). */
    public String channelName() {
        return channelName;
    }

    /**
     * Rebind to another of the owner's channels (validated name; the channel
     * is created if absent). Stops playback. Returns false on an invalid
     * name or a missing owner.
     */
    public boolean bindChannel(String name) {
        if (owner == null || !ChannelNames.isValid(name)
                || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        stopQuietly();
        this.channelName = name;
        setChanged();
        return ResonanceService.ensureChannel(serverLevel, owner, name) == SignalResult.OK;
    }

    /** Called from {@link ResonatorBlock#setPlacedBy}: record the placer and bind the default channel. */
    public void initializeOwner(ServerLevel serverLevel, UUID placer) {
        this.owner = placer;
        ResonanceService.ensureChannel(serverLevel, placer, channelName);
        setChanged();
    }

    /** The channel key this Resonator emits on, or {@code null} without an owner/level. */
    public ChannelKey channelKey() {
        if (owner == null || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return ResonanceService.keyFor(serverLevel, owner, channelName);
    }

    // ------------------------------------------------------------------
    // Transport (player-facing; authorisation via the player path, ops included)
    // ------------------------------------------------------------------

    /** Toggle play/stop for an interacting player. */
    public SignalResult togglePlayback(ServerPlayer requester) {
        return playing ? stopPlayback(requester) : startPlayback(requester);
    }

    /** Start playback from the current position, authorised as {@code requester}. */
    public SignalResult startPlayback(ServerPlayer requester) {
        ChannelKey key = channelKey();
        if (key == null || score == null || !(level instanceof ServerLevel serverLevel)) {
            return SignalResult.NOT_PLAYING;
        }
        SignalResult result = ResonanceService.transport(serverLevel, requester, key,
                TransportAction.PLAY, currentTick(), score.tempoBpm(), score.ticksPerBeat(), origin());
        if (result == SignalResult.OK) {
            playing = true;
            lastEmittedTick = currentTick() - 1;
            anchorCooldown = ANCHOR_INTERVAL_TICKS;
            setChanged();
            updateRingState();
        }
        return result;
    }

    /** Stop playback, authorised as {@code requester}. */
    public SignalResult stopPlayback(ServerPlayer requester) {
        ChannelKey key = channelKey();
        if (key == null || !(level instanceof ServerLevel serverLevel)) {
            return SignalResult.NOT_PLAYING;
        }
        SignalResult result = ResonanceService.transport(serverLevel, requester, key,
                TransportAction.STOP, currentTick(), 0, 0, origin());
        if (result == SignalResult.OK) {
            playing = false;
            setChanged();
            updateRingState();
        }
        return result;
    }

    public boolean isPlayingBack() {
        return playing;
    }

    /** Current playback position, floored to whole score ticks. */
    public long currentTick() {
        return (long) Math.floor(positionTicks);
    }

    // ------------------------------------------------------------------
    // The server tick schedule
    // ------------------------------------------------------------------

    /** Registered by {@link ResonatorBlock#getTicker}; server side only. */
    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, ResonatorBlockEntity be) {
        be.tickPulse();
        if (!be.playing || be.score == null || be.owner == null) {
            return;
        }
        ChannelKey key = ResonanceService.keyFor(level, be.owner, be.channelName);

        // Re-arm after a restart/reload: the guard is runtime-only, so a
        // persisted "playing" Resonator must pass it again. Refusal (cap,
        // deleted channel, revoked trust) quietly stops playback.
        if (!ResonanceService.isPlaying(key)) {
            SignalResult rearmed = ResonanceService.transportAs(level, be.owner, key,
                    TransportAction.PLAY, be.currentTick(),
                    be.score.tempoBpm(), be.score.ticksPerBeat(), be.origin());
            if (rearmed != SignalResult.OK) {
                be.playing = false;
                be.setChanged();
                be.updateRingState();
                return;
            }
            be.anchorCooldown = ANCHOR_INTERVAL_TICKS;
        }

        be.subscribeNearby(level, key);
        be.advanceTimeline(level, key);
    }

    private void subscribeNearby(ServerLevel level, ChannelKey key) {
        if (--subscribeCooldown > 0) {
            return;
        }
        subscribeCooldown = SUBSCRIBE_INTERVAL_TICKS;
        int range = Math.max(16, Math.min(NeroNotesConfig.EMIT_RANGE_BLOCKS.get(), 128));
        double rangeSq = (double) range * range;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(origin()) > rangeSq) {
                continue;
            }
            ResonanceService.subscribe(key, player);
            if (knownListeners.add(player.getUUID())) {
                // A listener this Resonator has not seen before: hand them the
                // current anchor immediately — the late-join seek.
                ResonanceService.sendAnchorTo(player, key, currentTick(), level.getGameTime(),
                        score.tempoBpm(), score.ticksPerBeat());
            }
        }
    }

    private void advanceTimeline(ServerLevel level, ChannelKey key) {
        double rate = PlaybackClock.scoreTicksPerGameTick(score.tempoBpm(), score.ticksPerBeat());
        positionTicks += rate;
        long target = currentTick();
        if (target - lastEmittedTick > MAX_CATCH_UP_TICKS) {
            lastEmittedTick = target - 1; // lag spike: skip, never burst-emit
        }

        int emitted = 0;
        while (lastEmittedTick < target) {
            lastEmittedTick++;
            List<TimelineNote> notes = timeline == null ? null : timeline.get(lastEmittedTick);
            if (notes == null) {
                continue;
            }
            for (TimelineNote note : notes) {
                SignalResult result = ResonanceService.emitNoteAs(level, owner, key, origin(),
                        true, note.voiceId(), note.pitch(), note.velocity(), lastEmittedTick);
                if (result != SignalResult.OK) {
                    // Channel gone or trust revoked mid-play: stop quietly.
                    playing = false;
                    setChanged();
                    updateRingState();
                    return;
                }
                emitted++;
            }
        }
        if (emitted > 0) {
            pulseTicksLeft = PULSE_TICKS;
            updateRingState();
        }

        // Loop or end.
        if (score.hasLoop() && positionTicks >= score.loopEndTick()) {
            positionTicks = score.loopStartTick();
            lastEmittedTick = score.loopStartTick() - 1;
            ResonanceService.transportAs(level, owner, key, TransportAction.SEEK,
                    score.loopStartTick(), score.tempoBpm(), score.ticksPerBeat(), origin());
            anchorCooldown = ANCHOR_INTERVAL_TICKS;
            setChanged();
        } else if (!score.hasLoop() && lastEmittedTick >= scoreEndTick) {
            ResonanceService.transportAs(level, owner, key, TransportAction.STOP,
                    currentTick(), 0, 0, origin());
            playing = false;
            positionTicks = 0;
            lastEmittedTick = -1;
            setChanged();
            updateRingState();
            return;
        }

        // Periodic re-anchor: drift correction and chunk-reload recovery for
        // everyone in range, at worst ANCHOR_INTERVAL_TICKS late.
        if (--anchorCooldown <= 0) {
            anchorCooldown = ANCHOR_INTERVAL_TICKS;
            ResonanceService.transportAs(level, owner, key, TransportAction.SEEK,
                    currentTick(), score.tempoBpm(), score.ticksPerBeat(), origin());
        }
    }

    private void tickPulse() {
        if (pulseTicksLeft > 0 && --pulseTicksLeft == 0) {
            updateRingState();
        }
    }

    /** Sync the {@code playing}/{@code pulse} blockstate ring visuals. */
    private void updateRingState() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState state = serverLevel.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof ResonatorBlock)) {
            return;
        }
        BlockState next = state
                .setValue(ResonatorBlock.PLAYING, playing)
                .setValue(ResonatorBlock.PULSE, playing && pulseTicksLeft > 0);
        if (next != state) {
            serverLevel.setBlock(worldPosition, next, 3);
        }
    }

    private void stopQuietly() {
        if (!playing) {
            return;
        }
        playing = false;
        ChannelKey key = channelKey();
        if (key != null && level instanceof ServerLevel serverLevel) {
            ResonanceService.transportAs(serverLevel, owner, key, TransportAction.STOP,
                    currentTick(), 0, 0, origin());
        }
        updateRingState();
    }

    private Vec3 origin() {
        return worldPosition.getCenter();
    }

    /** Rebuild the per-tick note lookup and the end-of-score tick. */
    private void rebuildTimeline() {
        if (score == null) {
            timeline = null;
            scoreEndTick = 0;
            return;
        }
        TreeMap<Long, List<TimelineNote>> built = new TreeMap<>();
        long end = 1;
        for (Score.Layer layer : score.layers()) {
            for (Score.Note note : layer.notes()) {
                built.computeIfAbsent((long) note.tick(), ignored -> new ArrayList<>())
                        .add(new TimelineNote(layer.voiceId(), note.pitch(), note.velocity()));
                end = Math.max(end, (long) note.tick() + note.lengthTicks());
            }
        }
        timeline = built;
        scoreEndTick = end;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (owner != null) {
            output.store(KEY_OWNER, UUIDUtil.CODEC, owner);
        }
        output.putString(KEY_CHANNEL, channelName);
        output.putBoolean(KEY_PLAYING, playing);
        output.putLong(KEY_POSITION, currentTick());
        if (score != null) {
            output.store(KEY_SCORE, CompoundTag.CODEC, ScoreCodec.toNbt(score));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = input.read(KEY_OWNER, UUIDUtil.CODEC).orElse(null);
        String storedChannel = input.getStringOr(KEY_CHANNEL, DEFAULT_CHANNEL_NAME);
        channelName = ChannelNames.isValid(storedChannel) ? storedChannel : DEFAULT_CHANNEL_NAME;
        playing = input.getBooleanOr(KEY_PLAYING, false);
        positionTicks = Math.max(0, input.getLongOr(KEY_POSITION, 0));
        lastEmittedTick = currentTick() - 1;
        score = null;
        input.read(KEY_SCORE, CompoundTag.CODEC).ifPresent(tag -> {
            try {
                score = ScoreCodec.fromNbt(tag);
            } catch (ScoreFormatException rejected) {
                // A newer-format or corrupt disk image: refuse loudly in the
                // log, keep the Resonator empty rather than guessing.
                NeroNotesCommon.LOGGER.warn("[NeroNotes] resonator at {} carries an unreadable score: {}",
                        worldPosition, rejected.getMessage());
            }
        });
        if (score == null) {
            playing = false;
            positionTicks = 0;
            lastEmittedTick = -1;
        }
        rebuildTimeline();
    }
}
