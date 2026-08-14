package za.co.neroland.neronotes.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neronotes.signal.TransportAction;

/**
 * The client playhead applying locked design decision 4: re-anchoring IS the
 * hard seek (late join / chunk reload), and per-tick drift beyond the
 * threshold seeks — the playback rate is never altered.
 */
class ChannelPlayheadTest {

    /** 120 BPM x 10 ticks/beat = exactly 1 score tick per game tick. */
    private static final int TEMPO = 120;
    private static final int TPB = 10;

    private static ChannelPlayhead startedAt(long anchorGameTick) {
        ChannelPlayhead playhead = new ChannelPlayhead();
        playhead.applyTransport(TransportAction.PLAY, 0L, anchorGameTick, TEMPO, TPB, anchorGameTick, 0L);
        return playhead;
    }

    private static ChannelPlayhead.ScheduledNote note(long scoreTick) {
        return new ChannelPlayhead.ScheduledNote(scoreTick, "neronotes:pulse_lead", 72, 100, 0L);
    }

    // ------------------------------------------------------------------
    // Transport: anchor + compensated seek
    // ------------------------------------------------------------------

    @Test
    void lateJoinerSeeksToTheCompensatedCurrentPosition() {
        ChannelPlayhead playhead = new ChannelPlayhead();
        // Anchor: position 200 at game tick 1000. This client hears about it
        // at game tick 1500 with 100 ms compensation (= 2 game ticks).
        playhead.applyTransport(TransportAction.PLAY, 200L, 1000L, TEMPO, TPB, 1500L, 100L);
        assertTrue(playhead.isPlaying());
        assertEquals(702.0, playhead.positionTicks(), 1e-9); // 200 + (500 + 2) * 1.0
        assertEquals(PlaybackClock.Correction.SEEK, playhead.lastCorrection()); // re-anchoring IS the seek
    }

    @Test
    void anchorApplicationIsDeterministic() {
        ChannelPlayhead first = new ChannelPlayhead();
        ChannelPlayhead second = new ChannelPlayhead();
        first.applyTransport(TransportAction.PLAY, 200L, 1000L, TEMPO, TPB, 1500L, 100L);
        second.applyTransport(TransportAction.PLAY, 200L, 1000L, TEMPO, TPB, 1500L, 100L);
        assertEquals(first.positionTicks(), second.positionTicks(), 0.0);
    }

    @Test
    void stopHaltsAndClearsTheQueue() {
        ChannelPlayhead playhead = startedAt(1000L);
        assertFalse(playhead.offer(note(50L))); // queued: ahead of the playhead
        playhead.applyTransport(TransportAction.STOP, 0L, 1001L, 0, 0, 1001L, 0L);
        assertFalse(playhead.isPlaying());
        assertEquals(0, playhead.pendingCount());
    }

    // ------------------------------------------------------------------
    // Per-tick drift: HOLD within threshold, SEEK beyond — never a rate change
    // ------------------------------------------------------------------

    @Test
    void inStepTickHolds() {
        ChannelPlayhead playhead = startedAt(1000L);
        ChannelPlayhead.TickResult result = playhead.tick(1001L, 0L, 100);
        assertEquals(PlaybackClock.Correction.HOLD, result.decision().correction());
        assertEquals(1.0, playhead.positionTicks(), 1e-9);
    }

    @Test
    void driftBeyondThresholdHardSeeksAndNeverChangesTheRate() {
        ChannelPlayhead playhead = startedAt(1000L);
        double rateBefore = playhead.rate();
        // Ten game ticks pass without this playhead ticking (a stall): its
        // one-tick advance leaves it ~500 ms behind the anchor -> hard seek.
        ChannelPlayhead.TickResult result = playhead.tick(1011L, 0L, 100);
        assertEquals(PlaybackClock.Correction.SEEK, result.decision().correction());
        assertEquals(11.0, playhead.positionTicks(), 1e-9); // snapped exactly to the expected position
        assertEquals(rateBefore, playhead.rate(), 0.0);     // the rate is untouched — seek, not speed-up
    }

    @Test
    void driftWithinThresholdIsLeftAlone() {
        ChannelPlayhead playhead = startedAt(1000L);
        // One tick, one advance: drift 0 -> HOLD, position keeps its local value.
        ChannelPlayhead.TickResult result = playhead.tick(1001L, 0L, 100);
        assertEquals(PlaybackClock.Correction.HOLD, result.decision().correction());
        assertEquals(result.decision().positionTicks(), playhead.positionTicks(), 0.0);
    }

    // ------------------------------------------------------------------
    // Note scheduling
    // ------------------------------------------------------------------

    @Test
    void liveNotesAlwaysPlayImmediately() {
        ChannelPlayhead playhead = startedAt(1000L);
        assertTrue(playhead.offer(note(-1L)));
    }

    @Test
    void notesAtOrBehindThePlayheadPlayImmediately() {
        ChannelPlayhead playhead = startedAt(1000L);
        playhead.tick(1001L, 0L, 100); // position 1.0
        assertTrue(playhead.offer(note(0L)));
        assertTrue(playhead.offer(note(1L)));
    }

    @Test
    void futureNotesQueueAndDrainWhenDue() {
        ChannelPlayhead playhead = startedAt(1000L);
        assertFalse(playhead.offer(note(3L))); // ahead of position 0 -> queued
        assertTrue(playhead.tick(1001L, 0L, 100).dueNotes().isEmpty());
        assertTrue(playhead.tick(1002L, 0L, 100).dueNotes().isEmpty());
        ChannelPlayhead.TickResult due = playhead.tick(1003L, 0L, 100); // position 3.0
        assertEquals(1, due.dueNotes().size());
        assertEquals(3L, due.dueNotes().getFirst().scoreTick());
        assertEquals(0, playhead.pendingCount());
    }

    @Test
    void whileStoppedNotesPlayImmediatelyAndTicksDoNothing() {
        ChannelPlayhead playhead = new ChannelPlayhead();
        assertTrue(playhead.offer(note(500L))); // no timeline -> play now
        ChannelPlayhead.TickResult result = playhead.tick(2000L, 0L, 100);
        assertTrue(result.dueNotes().isEmpty());
        assertEquals(PlaybackClock.Correction.HOLD, result.decision().correction());
    }

    @Test
    void thePendingQueueIsBounded() {
        ChannelPlayhead playhead = startedAt(1000L);
        for (int i = 0; i < ChannelPlayhead.MAX_PENDING_NOTES + 50; i++) {
            playhead.offer(note(1000L + i));
        }
        assertEquals(ChannelPlayhead.MAX_PENDING_NOTES, playhead.pendingCount());
    }
}
