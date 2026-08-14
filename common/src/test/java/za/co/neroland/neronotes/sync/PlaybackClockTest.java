package za.co.neroland.neronotes.sync;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The Stage 3 gate for the sync math (locked design decision 4): the
 * anchor→client-offset computation is deterministic, compensation is clamped
 * RTT/2, and drift beyond the threshold produces a SEEK — the type system
 * offers no rate correction at all.
 */
class PlaybackClockTest {

    // ------------------------------------------------------------------
    // Rate
    // ------------------------------------------------------------------

    @Test
    void scoreTicksPerGameTickIsTempoTimesResolution() {
        // 120 BPM * 10 ticks/beat = 1200 score ticks/min = 1 per game tick.
        assertEquals(1.0, PlaybackClock.scoreTicksPerGameTick(120, 10), 1e-9);
        assertEquals(0.4, PlaybackClock.scoreTicksPerGameTick(120, 4), 1e-9);
    }

    @Test
    void nonPositiveTempoOrResolutionMeansStoppedClock() {
        assertEquals(0.0, PlaybackClock.scoreTicksPerGameTick(0, 10), 0.0);
        assertEquals(0.0, PlaybackClock.scoreTicksPerGameTick(120, 0), 0.0);
        assertEquals(0.0, PlaybackClock.scoreTicksPerGameTick(-5, -5), 0.0);
    }

    // ------------------------------------------------------------------
    // Compensation: clamped RTT/2
    // ------------------------------------------------------------------

    @Test
    void compensationIsHalfTheRoundTrip() {
        assertEquals(150L, PlaybackClock.clampedCompensationMs(300L, 500L));
    }

    @Test
    void compensationClampsToTheConfiguredMaximum() {
        assertEquals(500L, PlaybackClock.clampedCompensationMs(2000L, 500L));
    }

    @Test
    void compensationIsZeroForUnknownRttOrZeroCap() {
        assertEquals(0L, PlaybackClock.clampedCompensationMs(0L, 500L));
        assertEquals(0L, PlaybackClock.clampedCompensationMs(-40L, 500L));
        assertEquals(0L, PlaybackClock.clampedCompensationMs(300L, 0L));
    }

    // ------------------------------------------------------------------
    // Anchor → expected position: deterministic
    // ------------------------------------------------------------------

    @Test
    void expectedPositionIsDeterministicInItsArguments() {
        // anchor at position 100 on game tick 1000; now 1100; rate 0.4; comp 250 ms = 5 game ticks.
        double first = PlaybackClock.expectedPositionTicks(100L, 1000L, 1100L, 0.4, 250L);
        double second = PlaybackClock.expectedPositionTicks(100L, 1000L, 1100L, 0.4, 250L);
        assertEquals(142.0, first, 1e-9);
        assertEquals(first, second, 0.0); // identical inputs -> identical output, always
    }

    @Test
    void expectedPositionNeverRewindsBeforeTheAnchor() {
        // A client clock lagging the anchor holds at the anchor position.
        assertEquals(100.0, PlaybackClock.expectedPositionTicks(100L, 1000L, 900L, 0.4, 0L), 1e-9);
    }

    @Test
    void stoppedRateHoldsAtTheAnchorPosition() {
        assertEquals(100.0, PlaybackClock.expectedPositionTicks(100L, 1000L, 5000L, 0.0, 250L), 1e-9);
    }

    // ------------------------------------------------------------------
    // Drift + the decision: SEEK or HOLD, nothing else
    // ------------------------------------------------------------------

    @Test
    void driftIsSignedMilliseconds() {
        // 8 score ticks ahead at rate 0.4 = 20 game ticks = 1000 ms.
        assertEquals(1000.0, PlaybackClock.driftMs(150.0, 142.0, 0.4), 1e-9);
        assertEquals(-1000.0, PlaybackClock.driftMs(134.0, 142.0, 0.4), 1e-9);
        assertEquals(0.0, PlaybackClock.driftMs(150.0, 142.0, 0.0), 0.0);
    }

    @Test
    void driftBeyondThresholdSeeksToTheExpectedPosition() {
        PlaybackClock.SyncDecision decision = PlaybackClock.evaluate(150.0, 142.0, 0.4, 100);
        assertEquals(PlaybackClock.Correction.SEEK, decision.correction());
        assertEquals(142.0, decision.positionTicks(), 1e-9);
    }

    @Test
    void driftWithinThresholdHolds() {
        // 0.5 score ticks at rate 0.4 = 62.5 ms < 100 ms threshold.
        PlaybackClock.SyncDecision decision = PlaybackClock.evaluate(142.5, 142.0, 0.4, 100);
        assertEquals(PlaybackClock.Correction.HOLD, decision.correction());
        assertEquals(142.5, decision.positionTicks(), 1e-9);
    }

    @Test
    void theOnlyCorrectionsAreHoldAndSeek_neverARateChange() {
        // Locked design decision 4: no rate variant exists, so a rate change
        // is unrepresentable — not merely avoided.
        assertArrayEquals(
                new PlaybackClock.Correction[] { PlaybackClock.Correction.HOLD, PlaybackClock.Correction.SEEK },
                PlaybackClock.Correction.values());
    }
}
