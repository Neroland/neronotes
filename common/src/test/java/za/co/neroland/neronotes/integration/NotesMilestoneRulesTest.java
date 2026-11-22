package za.co.neroland.neronotes.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NotesMilestoneRulesTest {

    @Test
    void crossingIsUpwardOnly() {
        assertTrue(NotesMilestoneRules.crossed(9, 10, 10));
        assertFalse(NotesMilestoneRules.crossed(10, 11, 10));
        assertFalse(NotesMilestoneRules.crossed(10, 9, 10));
        assertFalse(NotesMilestoneRules.crossed(8, 9, 10));
    }

    @Test
    void reachedCountsEqualAndAbove() {
        assertFalse(NotesMilestoneRules.reached(9, 10));
        assertTrue(NotesMilestoneRules.reached(10, 10));
        assertTrue(NotesMilestoneRules.reached(30, 25));
    }

    @Test
    void topPublishedRungFitsTheDefaultQuota() {
        int[] r = NotesMilestoneRules.PUBLISHED_RUNGS;
        assertTrue(r[r.length - 1] <= 25, "default library.per_player_quota is 25");
    }
}
