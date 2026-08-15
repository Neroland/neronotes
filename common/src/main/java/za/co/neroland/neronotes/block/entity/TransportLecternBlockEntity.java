package za.co.neroland.neronotes.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.signal.ChannelKey;
import za.co.neroland.neronotes.signal.ResonanceService;
import za.co.neroland.neronotes.signal.ResonanceService.SignalResult;
import za.co.neroland.neronotes.signal.TransportAction;
import za.co.neroland.neronotes.soundforge.SoundforgeDimension;
import za.co.neroland.neronotes.sync.PlaybackClock;

/**
 * The transport lectern's block entity: the <strong>preview player</strong>
 * for the sequencer session being edited at it. Preview is runtime-only
 * (nothing persists), plays the previewing player's session once through on
 * their own {@value #PREVIEW_CHANNEL_NAME} resonance channel, and stops when
 * the score ends, the player leaves the Soundforge or goes offline, another
 * player starts a preview, or the lectern is broken.
 *
 * <p>Authorisation is the normal channel path: the preview channel is owned
 * by the previewing player and the lectern emits <em>as</em> that player
 * (UUID authorisation, no operator bypass — same discipline as the
 * Resonator's machine-emitter path). The audio-spam guard applies like it
 * does to any other channel.</p>
 */
public class TransportLecternBlockEntity extends BlockEntity {

    /** The per-player preview channel a lectern emits on. */
    public static final String PREVIEW_CHANNEL_NAME = "preview";

    /** Bound on timeline catch-up after a lag spike, in score ticks. */
    private static final long MAX_CATCH_UP_TICKS = 1000;

    /** Listener subscription refresh interval (game ticks). */
    private static final int SUBSCRIBE_INTERVAL_TICKS = 20;

    // Runtime-only preview state.
    private UUID previewPlayer;
    private Score score;
    private double positionTicks;
    private long lastEmittedTick;
    private TreeMap<Long, List<TimelineNote>> timeline;
    private long scoreEndTick;
    private int subscribeCooldown;

    private record TimelineNote(String voiceId, int pitch, int velocity) {
    }

    public TransportLecternBlockEntity(BlockPos pos, BlockState state) {
        super(NeroNotesBlockEntities.TRANSPORT_LECTERN.get(), pos, state);
    }

    /** Whether {@code player} currently has a preview running at this lectern. */
    public boolean isPreviewingFor(UUID player) {
        return previewPlayer != null && previewPlayer.equals(player);
    }

    /**
     * Start (or restart) a preview of {@code score} for {@code player}.
     * Replaces any running preview. Returns false when the score is empty or
     * the resonance channel refused the transport (cap reached, etc.).
     */
    public boolean startPreview(ServerPlayer player, Score score) {
        stopPreview();
        if (score == null || score.noteCount() == 0 || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        UUID owner = player.getUUID();
        if (ResonanceService.ensureChannel(serverLevel, owner, PREVIEW_CHANNEL_NAME) != SignalResult.OK) {
            return false;
        }
        ChannelKey key = ResonanceService.keyFor(serverLevel, owner, PREVIEW_CHANNEL_NAME);
        ResonanceService.subscribe(key, player);
        SignalResult started = ResonanceService.transportAs(serverLevel, owner, key,
                TransportAction.PLAY, 0, score.tempoBpm(), score.ticksPerBeat(), origin());
        if (started != SignalResult.OK) {
            return false;
        }
        this.previewPlayer = owner;
        this.score = score;
        this.positionTicks = 0;
        this.lastEmittedTick = -1;
        this.subscribeCooldown = SUBSCRIBE_INTERVAL_TICKS;
        rebuildTimeline();
        return true;
    }

    /** Stop the running preview, if any. Safe to call repeatedly. */
    public void stopPreview() {
        if (previewPlayer == null) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            ChannelKey key = ResonanceService.keyFor(serverLevel, previewPlayer, PREVIEW_CHANNEL_NAME);
            ResonanceService.transportAs(serverLevel, previewPlayer, key,
                    TransportAction.STOP, currentTick(), 0, 0, origin());
        }
        clearPreview();
    }

    /** Registered by {@code TransportLecternBlock#getTicker}; server side only. */
    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state,
                                  TransportLecternBlockEntity lectern) {
        if (lectern.previewPlayer == null || lectern.score == null) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(lectern.previewPlayer);
        if (player == null || !SoundforgeDimension.isSoundforge(player.level())) {
            lectern.stopPreview(); // the composer left — the preview has no audience
            return;
        }
        ChannelKey key = ResonanceService.keyFor(level, lectern.previewPlayer, PREVIEW_CHANNEL_NAME);
        if (!ResonanceService.isPlaying(key)) {
            lectern.clearPreview(); // stopped externally (cap, channel deleted)
            return;
        }
        if (--lectern.subscribeCooldown <= 0) {
            lectern.subscribeCooldown = SUBSCRIBE_INTERVAL_TICKS;
            ResonanceService.subscribe(key, player);
        }
        lectern.advanceTimeline(level, key);
    }

    private void advanceTimeline(ServerLevel level, ChannelKey key) {
        double rate = PlaybackClock.scoreTicksPerGameTick(score.tempoBpm(), score.ticksPerBeat());
        positionTicks += rate;
        long target = currentTick();
        if (target - lastEmittedTick > MAX_CATCH_UP_TICKS) {
            lastEmittedTick = target - 1; // lag spike: skip, never burst-emit
        }
        while (lastEmittedTick < target) {
            lastEmittedTick++;
            List<TimelineNote> notes = timeline == null ? null : timeline.get(lastEmittedTick);
            if (notes == null) {
                continue;
            }
            for (TimelineNote note : notes) {
                SignalResult result = ResonanceService.emitNoteAs(level, previewPlayer, key, origin(),
                        true, note.voiceId(), note.pitch(), note.velocity(), lastEmittedTick);
                if (result != SignalResult.OK) {
                    stopPreview();
                    return;
                }
            }
        }
        if (lastEmittedTick >= scoreEndTick) {
            stopPreview(); // a preview plays once through — no loop
        }
    }

    /**
     * Breaking the lectern ends the preview so the channel's play slot frees
     * up. 26.x calls this on REAL removal only — never on chunk unload.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        stopPreview();
    }

    /**
     * Fired on destruction AND on chunk unload (including every unload in the
     * shutdown drain loop), so it must stay world-inert — no transports, no
     * blockstate writes; see the Resonator's {@code setRemoved} javadoc for
     * the shutdown-freeze lesson. Preview state is runtime-only, so simply
     * dropping it is enough; the play-slot guard clears with the
     * server-stopped hook, and {@code serverTick} already ends a preview the
     * moment its composer leaves.
     */
    @Override
    public void setRemoved() {
        clearPreview();
        super.setRemoved();
    }

    private void clearPreview() {
        previewPlayer = null;
        score = null;
        positionTicks = 0;
        lastEmittedTick = -1;
        timeline = null;
        scoreEndTick = 0;
    }

    private long currentTick() {
        return (long) Math.floor(positionTicks);
    }

    private Vec3 origin() {
        return Vec3.atCenterOf(worldPosition);
    }

    private void rebuildTimeline() {
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
}
