package za.co.neroland.neronotes.soundforge;

import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.score.ScoreCodec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure sequencer editor: every bound is enforced server-side, refusals
 * change nothing, and the caps keep any possible session under the wire
 * ceiling.
 */
class SessionEditorTest {

    private static Score empty() {
        return new Score(Score.CURRENT_FORMAT_VERSION, 120, 4, 0, 0,
                List.of(new Score.Layer("neronotes:void_bass", List.of())));
    }

    @Test
    void toggleAddsThenRemovesANote() {
        SessionEditor.Result added = SessionEditor.apply(empty(), SequencerEdit.toggleNote(0, 4, 60));
        assertTrue(added.ok());
        assertEquals(1, added.score().noteCount());
        Score.Note note = added.score().layers().get(0).notes().get(0);
        assertEquals(4, note.tick());
        assertEquals(60, note.pitch());
        assertEquals(SessionEditor.DEFAULT_VELOCITY, note.velocity());

        SessionEditor.Result removed = SessionEditor.apply(added.score(), SequencerEdit.toggleNote(0, 4, 60));
        assertTrue(removed.ok());
        assertEquals(0, removed.score().noteCount());
    }

    @Test
    void refusesOutOfRangeNotesQuietly() {
        Score before = empty();
        assertEquals(SessionEditor.Error.OUT_OF_RANGE,
                SessionEditor.apply(before, SequencerEdit.toggleNote(0, SessionEditor.MAX_TICK, 60)).error());
        assertEquals(SessionEditor.Error.OUT_OF_RANGE,
                SessionEditor.apply(before, SequencerEdit.toggleNote(0, -1, 60)).error());
        assertEquals(SessionEditor.Error.OUT_OF_RANGE,
                SessionEditor.apply(before, SequencerEdit.toggleNote(5, 0, 60)).error());
        assertEquals(SessionEditor.Error.OUT_OF_RANGE,
                SessionEditor.apply(before, SequencerEdit.setTempo(0)).error());
        // A refusal returns the untouched score.
        assertEquals(before, SessionEditor.apply(before, SequencerEdit.setTempo(0)).score());
    }

    @Test
    void layerCapAndLastLayerAreEnforced() {
        Score score = empty();
        for (int i = 1; i < SessionEditor.MAX_LAYERS; i++) {
            SessionEditor.Result result = SessionEditor.apply(score, SequencerEdit.addLayer("neronotes:pulse_lead"));
            assertTrue(result.ok());
            score = result.score();
        }
        assertEquals(SessionEditor.MAX_LAYERS, score.layers().size());
        assertEquals(SessionEditor.Error.LAYER_LIMIT,
                SessionEditor.apply(score, SequencerEdit.addLayer("neronotes:pulse_lead")).error());

        assertEquals(SessionEditor.Error.LAST_LAYER,
                SessionEditor.apply(empty(), SequencerEdit.removeLayer(0)).error());
    }

    @Test
    void noteCapKeepsEveryPossibleSessionUnderTheWireCeiling() {
        // Fill one layer to the cap with worst-case-ish notes, then refuse.
        Score score = empty();
        List<Score.Note> notes = new java.util.ArrayList<>();
        for (int i = 0; i < SessionEditor.MAX_NOTES_PER_LAYER; i++) {
            notes.add(new Score.Note(i % SessionEditor.MAX_TICK, i % 128, 100, 2));
        }
        Score full = new Score(score.formatVersion(), score.tempoBpm(), score.ticksPerBeat(),
                0, 0, List.of(new Score.Layer("neronotes:void_bass", notes)));
        assertEquals(SessionEditor.Error.NOTE_LIMIT,
                SessionEditor.apply(full, SequencerEdit.toggleNote(0, 9999, 1)).error());

        // Even MAX_LAYERS full layers stay under the 64 KiB wire ceiling.
        List<Score.Layer> layers = new java.util.ArrayList<>();
        for (int i = 0; i < SessionEditor.MAX_LAYERS; i++) {
            layers.add(new Score.Layer("neronotes:void_bass", notes));
        }
        Score maximal = new Score(score.formatVersion(), score.tempoBpm(), score.ticksPerBeat(),
                0, 0, layers);
        assertTrue(ScoreCodec.serialisedSize(maximal) <= ScoreCodec.HARD_BUDGET_CEILING_BYTES,
                "the editor caps must bound every session below the wire ceiling");
    }

    @Test
    void loopEditsValidateAndClear() {
        SessionEditor.Result looped = SessionEditor.apply(empty(), SequencerEdit.setLoop(8, 24));
        assertTrue(looped.ok());
        assertEquals(8, looped.score().loopStartTick());
        assertEquals(24, looped.score().loopEndTick());

        assertEquals(SessionEditor.Error.OUT_OF_RANGE,
                SessionEditor.apply(empty(), SequencerEdit.setLoop(24, 8)).error());

        SessionEditor.Result cleared = SessionEditor.apply(looped.score(), SequencerEdit.setLoop(0, 0));
        assertTrue(cleared.ok());
        assertEquals(0, cleared.score().loopEndTick());
    }

    @Test
    void sessionAndPreviewOpsAreNotScoreEdits() {
        assertEquals(SessionEditor.Error.UNSUPPORTED_OP,
                SessionEditor.apply(empty(), SequencerEdit.setActiveLayer(1)).error());
        assertEquals(SessionEditor.Error.UNSUPPORTED_OP,
                SessionEditor.apply(empty(), SequencerEdit.previewStart()).error());
    }
}
