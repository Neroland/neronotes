package za.co.neroland.neronotes.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Stage 8 gate: the pure rising-edge crossing detector fires once per
 * threshold on rising edges, never repeats while the value stays above, and
 * never fires on an unchanged value.
 */
class ThresholdCrossingsTest {

    private static final long[] THRESHOLDS = {1, 10, 50, 100};
    private static final long[] NONE = {};

    @Test
    void firstPublishCrossesTheFirstThreshold() {
        assertArrayEquals(new long[] {1}, ThresholdCrossings.crossedRising(0, 1, THRESHOLDS));
    }

    @Test
    void noRepeatFireWhileAboveTheThreshold() {
        // 1 -> 2: threshold 1 was already crossed; nothing new is passed.
        assertArrayEquals(NONE, ThresholdCrossings.crossedRising(1, 2, THRESHOLDS));
        assertArrayEquals(NONE, ThresholdCrossings.crossedRising(99, 99, THRESHOLDS));
    }

    @Test
    void sameValueNeverFires() {
        assertArrayEquals(NONE, ThresholdCrossings.crossedRising(0, 0, THRESHOLDS));
        assertArrayEquals(NONE, ThresholdCrossings.crossedRising(10, 10, THRESHOLDS));
        assertArrayEquals(NONE, ThresholdCrossings.crossedRising(50, 50, THRESHOLDS));
    }

    @Test
    void fallingNeverFires() {
        assertArrayEquals(NONE, ThresholdCrossings.crossedRising(51, 49, THRESHOLDS));
        assertArrayEquals(NONE, ThresholdCrossings.crossedRising(1, 0, THRESHOLDS));
    }

    @Test
    void oneJumpAcrossSeveralThresholdsFiresEachOnce() {
        assertArrayEquals(new long[] {1, 10, 50}, ThresholdCrossings.crossedRising(0, 60, THRESHOLDS));
    }

    @Test
    void landingExactlyOnAThresholdFiresIt() {
        assertArrayEquals(new long[] {10}, ThresholdCrossings.crossedRising(9, 10, THRESHOLDS));
    }

    @Test
    void reCrossingAfterAGenuineDropFiresAgain() {
        // Unpublish below 10, publish back over it: a real rising edge.
        assertArrayEquals(new long[] {10}, ThresholdCrossings.crossedRising(9, 11, THRESHOLDS));
    }

    @Test
    void emptyOrNullThresholdsFireNothing() {
        assertArrayEquals(NONE, ThresholdCrossings.crossedRising(0, 1000, NONE));
        assertArrayEquals(NONE, ThresholdCrossings.crossedRising(0, 1000, null));
    }
}
