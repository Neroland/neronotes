package za.co.neroland.neronotes.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure sweep schedule: once shortly after start, then daily. */
class RetentionSweepTest {

    @Test
    void sweepsOnceAtTheStartupDelay() {
        assertFalse(RetentionSweep.shouldSweepAt(1));
        assertFalse(RetentionSweep.shouldSweepAt(RetentionSweep.STARTUP_DELAY_TICKS - 1));
        assertTrue(RetentionSweep.shouldSweepAt(RetentionSweep.STARTUP_DELAY_TICKS));
        assertFalse(RetentionSweep.shouldSweepAt(RetentionSweep.STARTUP_DELAY_TICKS + 1));
    }

    @Test
    void sweepsDailyThereafter() {
        int firstDaily = RetentionSweep.STARTUP_DELAY_TICKS + RetentionSweep.SWEEP_INTERVAL_TICKS;
        assertTrue(RetentionSweep.shouldSweepAt(firstDaily));
        assertFalse(RetentionSweep.shouldSweepAt(firstDaily - 1));
        assertFalse(RetentionSweep.shouldSweepAt(firstDaily + 1));
        assertTrue(RetentionSweep.shouldSweepAt(firstDaily + RetentionSweep.SWEEP_INTERVAL_TICKS));
    }

    @Test
    void neverSweepsBeforeTheDelayEvenAtIntervalMultiples() {
        // Guard against a modulo-only implementation sweeping at tick 0 of a
        // fresh server run.
        assertFalse(RetentionSweep.shouldSweepAt(0));
        assertFalse(RetentionSweep.shouldSweepAt(RetentionSweep.SWEEP_INTERVAL_TICKS));
    }
}
