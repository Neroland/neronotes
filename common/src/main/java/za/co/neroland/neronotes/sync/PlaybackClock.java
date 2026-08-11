package za.co.neroland.neronotes.sync;

/**
 * The synchronised-playback math — locked design decision 4, in pure
 * functions. <strong>Anchor and resync, never pitch-shift:</strong> the
 * server owns the timeline anchor {@code (positionTick, anchorGameTick)};
 * clients schedule locally against it, offset by half the measured
 * round-trip clamped to {@code sync.max_latency_compensation_ms}; measured
 * drift beyond {@code sync.drift_threshold_ms} produces a <em>hard seek</em>
 * to the correct position.
 *
 * <p>There is deliberately no rate correction anywhere in this class —
 * {@link SyncDecision} can only ever say {@link Correction#HOLD} or
 * {@link Correction#SEEK}. Adjusting playback rate to catch up is audible
 * and worse than a seek, so the type system simply does not offer it.</p>
 *
 * <p>Plain JVM, no Minecraft types: every function is deterministic in its
 * arguments, which is what makes the anchor→offset computation unit-testable
 * (the Stage 3 gate).</p>
 */
public final class PlaybackClock {

    /** One Minecraft game tick in milliseconds. */
    public static final double MS_PER_GAME_TICK = 50.0;

    private PlaybackClock() {
    }

    /**
     * Score ticks elapsed per game tick at a given tempo:
     * {@code tempoBpm * ticksPerBeat / 60 / 20}. Non-positive inputs yield
     * {@code 0.0} (a stopped clock), never a throw — transport {@code stop}
     * events legitimately carry no tempo.
     */
    public static double scoreTicksPerGameTick(int tempoBpm, int ticksPerBeat) {
        if (tempoBpm <= 0 || ticksPerBeat <= 0) {
            return 0.0;
        }
        return (tempoBpm * (double) ticksPerBeat) / 60.0 / 20.0;
    }

    /**
     * The latency compensation to apply, in milliseconds: half the measured
     * round trip, clamped into {@code [0, maxCompensationMs]} (config
     * {@code sync.max_latency_compensation_ms}, default 500).
     */
    public static long clampedCompensationMs(long roundTripMs, long maxCompensationMs) {
        if (roundTripMs <= 0 || maxCompensationMs <= 0) {
            return 0L;
        }
        return Math.min(roundTripMs / 2, maxCompensationMs);
    }

    /**
     * Where the server timeline is <em>now</em>, in score ticks, as this
     * client should estimate it: the anchored position plus the game ticks
     * elapsed since the anchor at {@code rate}, advanced by the clamped
     * latency compensation (the anchor was already {@code compensationMs}
     * old when it arrived). Never earlier than the anchor position — a
     * client whose clock lags the anchor holds at the anchor rather than
     * rewinding before it.
     */
    public static double expectedPositionTicks(long anchorPositionTick, long anchorGameTick,
                                               long nowGameTick, double rate, long compensationMs) {
        if (rate <= 0.0) {
            return anchorPositionTick;
        }
        double elapsedTicks = nowGameTick - anchorGameTick;
        double compensationTicks = compensationMs / MS_PER_GAME_TICK;
        double expected = anchorPositionTick + (elapsedTicks + compensationTicks) * rate;
        return Math.max(anchorPositionTick, expected);
    }

    /**
     * Signed drift of a local playhead from the expected position, in
     * milliseconds (positive = the local playhead runs ahead). {@code 0}
     * when the clock is stopped ({@code rate <= 0}).
     */
    public static double driftMs(double localPositionTicks, double expectedPositionTicks, double rate) {
        if (rate <= 0.0) {
            return 0.0;
        }
        return (localPositionTicks - expectedPositionTicks) / rate * MS_PER_GAME_TICK;
    }

    /**
     * Decide what a client does about measured drift. Within the threshold:
     * {@link Correction#HOLD} — play on, jitter is normal. Beyond it:
     * {@link Correction#SEEK} to exactly the expected position. Those are the
     * only two outcomes that exist; playback rate is never adjusted.
     */
    public static SyncDecision evaluate(double localPositionTicks, double expectedPositionTicks,
                                        double rate, int driftThresholdMs) {
        double drift = driftMs(localPositionTicks, expectedPositionTicks, rate);
        if (Math.abs(drift) > driftThresholdMs) {
            return new SyncDecision(Correction.SEEK, expectedPositionTicks);
        }
        return new SyncDecision(Correction.HOLD, localPositionTicks);
    }

    /**
     * The complete set of corrections a client may apply. There is no rate
     * variant and never will be in this format — see the class javadoc.
     */
    public enum Correction {
        /** Drift within threshold: keep playing, do nothing. */
        HOLD,
        /** Drift beyond threshold: hard-seek the playhead to the expected position. */
        SEEK
    }

    /**
     * The outcome of {@link #evaluate}: the correction plus the position the
     * playhead should be at after applying it (unchanged for
     * {@link Correction#HOLD}).
     */
    public record SyncDecision(Correction correction, double positionTicks) {
    }
}
