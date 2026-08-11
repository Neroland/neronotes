package za.co.neroland.neronotes.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.score.Score.Layer;
import za.co.neroland.neronotes.score.Score.Note;
import za.co.neroland.neronotes.score.ScoreCodec;
import za.co.neroland.neronotes.score.ScoreFormatException;
import za.co.neroland.neronotes.score.ScoreSizeException;

/**
 * The packet-budget contract: a score on the wire is bounded by the SAME cap
 * as the disk budget, enforced at decode time before any parsing. An
 * unbounded score payload is a server-crash vector.
 */
class NotesNetworkBudgetTest {

    @Test
    void wireBoundIsTheDiskBudgetCeiling() {
        assertEquals(ScoreCodec.HARD_BUDGET_CEILING_BYTES, NotesNetwork.MAX_SCORE_PAYLOAD_BYTES,
                "payload bound must equal the disk score-budget ceiling — one cap, enforced twice");
        assertEquals(65536, NotesNetwork.MAX_SCORE_PAYLOAD_BYTES,
                "must mirror the config maximum of disk.score_budget_bytes (see NeroNotesConfig)");
    }

    @Test
    void oversizedWireBlobIsRejectedBeforeParsing() {
        byte[] oversized = new byte[NotesNetwork.MAX_SCORE_PAYLOAD_BYTES + 1]; // garbage on purpose
        ScoreSizeException refusal = assertThrows(ScoreSizeException.class,
                () -> NotesNetwork.decodeScoreFromWire(oversized),
                "an over-budget blob must be rejected on size alone, before NBT parsing");
        assertEquals(oversized.length, refusal.actualBytes());
        assertEquals(NotesNetwork.MAX_SCORE_PAYLOAD_BYTES, refusal.budgetBytes());
    }

    @Test
    void inBudgetScoreRoundTripsThroughTheWirePath() throws ScoreFormatException {
        List<Note> notes = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            notes.add(new Note(i * 2, 60 + (i % 12), 100, 2));
        }
        Score score = new Score(Score.CURRENT_FORMAT_VERSION, 128, 4, 0, 128,
                List.of(new Layer("neronotes:nebula_texture", notes)));
        byte[] wire = ScoreCodec.toBytes(score, NotesNetwork.MAX_SCORE_PAYLOAD_BYTES);
        assertEquals(score, NotesNetwork.decodeScoreFromWire(wire));
    }

    @Test
    void corruptWireBytesAreANamedErrorNeverACrash() {
        assertThrows(ScoreFormatException.class,
                () -> NotesNetwork.decodeScoreFromWire(new byte[] {0x7f, 0x00, 0x33}));
    }
}
