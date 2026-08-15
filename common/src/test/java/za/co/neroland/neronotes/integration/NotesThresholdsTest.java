package za.co.neroland.neronotes.integration;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.event.ThresholdEvents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 8 gate: NeroNotes' crossings arrive on Core's {@code ThresholdEvents}
 * bus with the right channel, value and — the privacy-load-bearing part — a
 * scope that is a system/place key, never anything player-identifying.
 *
 * <p>Core's bus has no unregister, so ONE listener is registered for the
 * whole test class and the capture list is cleared per test.</p>
 */
class NotesThresholdsTest {

    private static final List<ThresholdEvents.ThresholdCrossing> CAPTURED = new ArrayList<>();
    private static boolean listening;

    @BeforeEach
    void listen() {
        if (!listening) {
            ThresholdEvents.onCrossing(crossing -> {
                if (crossing.channel().getNamespace().equals("neronotes")) {
                    CAPTURED.add(crossing);
                }
            });
            listening = true;
        }
        CAPTURED.clear();
    }

    @Test
    void firstPublishFiresTheFirstMilestone() {
        NotesThresholds.publishedCountChanged(0, 1);
        assertEquals(1, CAPTURED.size());
        ThresholdEvents.ThresholdCrossing crossing = CAPTURED.get(0);
        assertEquals(NotesThresholds.COMPOSITIONS_PUBLISHED, crossing.channel());
        assertEquals("library", crossing.scope());
        assertEquals(1, crossing.value());
        assertEquals(1, crossing.threshold());
        assertTrue(crossing.rising());
    }

    @Test
    void secondPublishDoesNotRepeatTheFirstMilestone() {
        NotesThresholds.publishedCountChanged(1, 2);
        assertTrue(CAPTURED.isEmpty());
    }

    @Test
    void refusedPublishLeavesTheCountUnchangedAndFiresNothing() {
        NotesThresholds.publishedCountChanged(10, 10);
        assertTrue(CAPTURED.isEmpty());
    }

    @Test
    void bulkImportCrossingSeveralMilestonesFiresEachOnce() {
        NotesThresholds.publishedCountChanged(0, 60);
        assertEquals(3, CAPTURED.size()); // 1, 10, 50 — once each
        assertEquals(1, CAPTURED.get(0).threshold());
        assertEquals(10, CAPTURED.get(1).threshold());
        assertEquals(50, CAPTURED.get(2).threshold());
        for (ThresholdEvents.ThresholdCrossing crossing : CAPTURED) {
            assertEquals(60, crossing.value());
            assertTrue(crossing.rising());
        }
    }

    @Test
    void listenerCrossingScopeIsTheDimensionIdOnly() {
        NotesThresholds.listenerCountChanged("neronotes:soundforge", 1, 2);
        assertEquals(1, CAPTURED.size());
        ThresholdEvents.ThresholdCrossing crossing = CAPTURED.get(0);
        assertEquals(NotesThresholds.CHANNEL_LISTENERS, crossing.channel());
        // The scope is a PLACE key — the dimension id, nothing else. No UUID
        // shape, no channel name: a crossing must never map a base or a person.
        assertEquals("neronotes:soundforge", crossing.scope());
        assertFalse(crossing.scope().matches(".*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}.*"),
                "scope must never carry a UUID");
        assertEquals(2, crossing.threshold());
    }

    @Test
    void losingAndRegainingAListenerReFiresTheMilestone() {
        NotesThresholds.listenerCountChanged("minecraft:overworld", 1, 2);
        NotesThresholds.listenerCountChanged("minecraft:overworld", 2, 3); // no milestone at 3
        NotesThresholds.listenerCountChanged("minecraft:overworld", 1, 2); // genuine re-crossing
        assertEquals(2, CAPTURED.size());
        assertEquals(2, CAPTURED.get(0).threshold());
        assertEquals(2, CAPTURED.get(1).threshold());
    }
}
