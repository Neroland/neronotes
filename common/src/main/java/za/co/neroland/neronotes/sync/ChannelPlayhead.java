package za.co.neroland.neronotes.sync;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import za.co.neroland.neronotes.signal.TransportAction;

/**
 * One channel's client-side playhead: the local clock a client runs against
 * the server's timeline anchor, plus the small queue of timeline notes not
 * yet due. This is where locked design decision 4 is <em>applied</em> (the
 * math itself lives in {@link PlaybackClock}):
 *
 * <ul>
 *   <li>{@code play}/{@code seek} hard-seek the local playhead to the
 *       compensated expected position — which is also exactly what a late
 *       joiner or a chunk reload gets, because re-anchoring IS the seek.</li>
 *   <li>each tick the playhead advances by exactly one game tick's worth of
 *       score ticks, then measured drift beyond the threshold produces a
 *       hard seek — never a rate change ({@link PlaybackClock.Correction}
 *       has no rate variant).</li>
 *   <li>timeline notes ({@code scoreTick >= 0}) play when the playhead
 *       reaches them; live notes ({@code scoreTick < 0}) always play
 *       immediately.</li>
 * </ul>
 *
 * <p>Plain JVM on purpose — no Minecraft types (origins travel as packed
 * longs), so the whole scheduling behaviour is unit-testable. Not
 * thread-safe; the client engine confines each instance to the client main
 * thread.</p>
 */
public final class ChannelPlayhead {

    /** Bound on queued not-yet-due notes; oldest are dropped beyond it. */
    public static final int MAX_PENDING_NOTES = 256;

    private final PriorityQueue<ScheduledNote> pending =
            new PriorityQueue<>(Comparator.comparingLong(ScheduledNote::scoreTick));

    private boolean playing;
    private long anchorPositionTick;
    private long anchorGameTick;
    private int tempoBpm;
    private int ticksPerBeat;
    private double localPositionTicks;
    private PlaybackClock.Correction lastCorrection = PlaybackClock.Correction.HOLD;
    private long lastActivityGameTick;

    /**
     * A note event as the playhead schedules it. {@code scoreTick < 0} means
     * "live, play immediately" (interaction notes with no timeline position);
     * {@code originPacked} is the emitting block's packed position
     * ({@code BlockPos.asLong()} on the Minecraft side).
     *
     * @param scoreTick    timeline position in score ticks, or {@code -1}
     * @param voiceId      voice registry id
     * @param pitch        MIDI-style note number 0–127
     * @param velocity     0–127
     * @param originPacked packed origin position
     */
    public record ScheduledNote(long scoreTick, String voiceId, int pitch, int velocity, long originPacked) {
    }

    /** What one tick produced: the notes now due, and the correction applied. */
    public record TickResult(List<ScheduledNote> dueNotes, PlaybackClock.SyncDecision decision) {
    }

    // ------------------------------------------------------------------
    // Transport
    // ------------------------------------------------------------------

    /**
     * Apply a transport event against the server anchor. {@code play} and
     * {@code seek} re-anchor and hard-seek the local playhead to the
     * compensated expected position; {@code stop} halts and clears the
     * queue.
     */
    public void applyTransport(TransportAction action, long positionTick, long anchorGameTick,
                               int tempoBpm, int ticksPerBeat, long nowGameTick, long compensationMs) {
        this.lastActivityGameTick = nowGameTick;
        switch (action) {
            case PLAY, SEEK -> {
                this.playing = true;
                this.anchorPositionTick = positionTick;
                this.anchorGameTick = anchorGameTick;
                if (tempoBpm > 0 && ticksPerBeat > 0) {
                    this.tempoBpm = tempoBpm;
                    this.ticksPerBeat = ticksPerBeat;
                }
                // Re-anchoring IS the hard seek — late joiners and chunk
                // reloads land here and start at the current position.
                this.localPositionTicks = PlaybackClock.expectedPositionTicks(
                        positionTick, anchorGameTick, nowGameTick, rate(), compensationMs);
                this.lastCorrection = PlaybackClock.Correction.SEEK;
                pending.removeIf(note -> note.scoreTick() >= 0 && note.scoreTick() < this.localPositionTicks
                        && action == TransportAction.SEEK);
            }
            case STOP -> {
                this.playing = false;
                pending.clear();
            }
        }
    }

    // ------------------------------------------------------------------
    // Per-tick advance + drift correction
    // ------------------------------------------------------------------

    /**
     * Advance one game tick: move the playhead at the nominal rate, measure
     * drift against the compensated anchor, hard-seek if it exceeds
     * {@code driftThresholdMs}, then return the queued notes that are now
     * due. While stopped this returns no notes and {@code HOLD}.
     */
    public TickResult tick(long nowGameTick, long compensationMs, int driftThresholdMs) {
        if (!playing) {
            return new TickResult(List.of(), new PlaybackClock.SyncDecision(
                    PlaybackClock.Correction.HOLD, localPositionTicks));
        }
        lastActivityGameTick = nowGameTick;
        double rate = rate();
        localPositionTicks += rate; // nominal advance — the rate itself is never altered
        double expected = PlaybackClock.expectedPositionTicks(
                anchorPositionTick, anchorGameTick, nowGameTick, rate, compensationMs);
        PlaybackClock.SyncDecision decision = PlaybackClock.evaluate(
                localPositionTicks, expected, rate, driftThresholdMs);
        if (decision.correction() == PlaybackClock.Correction.SEEK) {
            localPositionTicks = decision.positionTicks();
        }
        lastCorrection = decision.correction();
        return new TickResult(drainDue(), decision);
    }

    // ------------------------------------------------------------------
    // Note scheduling
    // ------------------------------------------------------------------

    /**
     * Offer an incoming note. Returns {@code true} if it should play
     * <em>right now</em> (live note, playhead already at/past its tick, or
     * this channel is not in timeline playback); {@code false} means it was
     * queued and will come back from {@link #tick} when due.
     */
    public boolean offer(ScheduledNote note) {
        if (note.scoreTick() < 0 || !playing) {
            return true;
        }
        if (note.scoreTick() <= localPositionTicks) {
            return true;
        }
        pending.add(note);
        while (pending.size() > MAX_PENDING_NOTES) {
            pending.poll();
        }
        return false;
    }

    private List<ScheduledNote> drainDue() {
        if (pending.isEmpty()) {
            return List.of();
        }
        List<ScheduledNote> due = new ArrayList<>();
        while (!pending.isEmpty() && pending.peek().scoreTick() <= localPositionTicks) {
            due.add(pending.poll());
        }
        return due;
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    public boolean isPlaying() {
        return playing;
    }

    public double positionTicks() {
        return localPositionTicks;
    }

    /** The correction applied by the most recent transport/tick — test + debug observability. */
    public PlaybackClock.Correction lastCorrection() {
        return lastCorrection;
    }

    public int pendingCount() {
        return pending.size();
    }

    /** Game tick of the last transport or tick activity (staleness cleanup). */
    public long lastActivityGameTick() {
        return lastActivityGameTick;
    }

    /** Current score-ticks-per-game-tick rate (0 while no tempo is known). */
    public double rate() {
        return PlaybackClock.scoreTicksPerGameTick(tempoBpm, ticksPerBeat);
    }
}
