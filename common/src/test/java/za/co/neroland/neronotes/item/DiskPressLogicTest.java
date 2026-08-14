package za.co.neroland.neronotes.item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.score.ScoreCodec;
import za.co.neroland.neronotes.voice.VoiceFamily;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure Disk Press decision: budget refusal names both byte counts and
 * never truncates; name validation runs at press time; anonymous authorship
 * keeps the UUID (for erasure) but stores no display name.
 */
class DiskPressLogicTest {

    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private static final Function<String, Optional<VoiceFamily>> RESOLVER = voiceId -> switch (voiceId) {
        case "neronotes:void_bass" -> Optional.of(VoiceFamily.DEEP_BASS);
        case "neronotes:pulse_lead" -> Optional.of(VoiceFamily.HIGH_LEAD);
        default -> Optional.empty();
    };

    private static Score scoreWithNotes(int noteCount) {
        List<Score.Note> notes = new java.util.ArrayList<>();
        for (int i = 0; i < noteCount; i++) {
            notes.add(new Score.Note(i, 60, 100, 2));
        }
        return new Score(Score.CURRENT_FORMAT_VERSION, 120, 4, 0, 0,
                List.of(new Score.Layer("neronotes:void_bass", notes)));
    }

    @Test
    void pressesAValidScoreWithCredit() {
        DiskPressLogic.Result result = DiskPressLogic.press(scoreWithNotes(8), "First Light", false,
                AUTHOR, "Dario", 16384, 48, List.of(), RESOLVER);
        assertTrue(result.ok());
        assertNotNull(result.contents());
        assertEquals("First Light", result.contents().title());
        assertEquals(AUTHOR, result.contents().author());
        assertEquals(Optional.of("Dario"), result.contents().authorDisplay());
        assertEquals(VoiceFamily.DEEP_BASS.id(), result.contents().familyId());
        assertTrue(result.sizeBytes() > 0);
    }

    @Test
    void overBudgetRefusalNamesBothByteCounts() {
        Score big = scoreWithNotes(64);
        int actual = ScoreCodec.serialisedSize(big);
        int budget = actual - 1; // one byte too small
        DiskPressLogic.Result result = DiskPressLogic.press(big, "Too Big", false,
                AUTHOR, "Dario", budget, 48, List.of(), RESOLVER);
        assertEquals(DiskPressLogic.ErrorKind.OVER_BUDGET, result.error());
        assertEquals(actual, result.sizeBytes(), "the refusal names the actual size");
        assertEquals(budget, result.budgetBytes(), "the refusal names the limit");
        assertEquals(null, result.contents(), "nothing is pressed — and nothing is truncated");
    }

    @Test
    void anonymousKeepsTheUuidButNoDisplayName() {
        DiskPressLogic.Result result = DiskPressLogic.press(scoreWithNotes(4), "Nameless", true,
                AUTHOR, "Dario", 16384, 48, List.of(), RESOLVER);
        assertTrue(result.ok());
        DiskContents contents = result.contents();
        // The UUID is retained — data erasure needs it.
        assertEquals(AUTHOR, contents.author());
        assertTrue(contents.anonymous());
        // But no display name is stored, and no display surface names the author.
        assertEquals("", contents.authorName());
        assertEquals(Optional.empty(), contents.authorDisplay());
    }

    @Test
    void anonymousDropsTheNameEvenIfACallerPassesOne() {
        DiskContents contents = new DiskContents(scoreWithNotes(1), "T", AUTHOR, "LeakedName",
                true, VoiceFamily.DEEP_BASS.id());
        assertEquals("", contents.authorName(), "the DiskContents invariant scrubs the name");
        assertEquals(Optional.empty(), contents.authorDisplay());
    }

    @Test
    void refusesAnEmptyScoreAndBadNames() {
        Score empty = new Score(Score.CURRENT_FORMAT_VERSION, 120, 4, 0, 0,
                List.of(new Score.Layer("neronotes:void_bass", List.of())));
        assertEquals(DiskPressLogic.ErrorKind.EMPTY_SCORE,
                DiskPressLogic.press(empty, "T", false, AUTHOR, "D", 16384, 48, List.of(), RESOLVER).error());

        DiskPressLogic.Result blank = DiskPressLogic.press(scoreWithNotes(1), "  ", false,
                AUTHOR, "D", 16384, 48, List.of(), RESOLVER);
        assertEquals(DiskPressLogic.ErrorKind.BAD_NAME, blank.error());
        assertEquals(DiskNames.Status.EMPTY, blank.nameStatus());

        DiskPressLogic.Result blocked = DiskPressLogic.press(scoreWithNotes(1), "grief anthem", false,
                AUTHOR, "D", 16384, 48, List.of("grief"), RESOLVER);
        assertEquals(DiskPressLogic.ErrorKind.BAD_NAME, blocked.error());
        assertEquals(DiskNames.Status.BLOCKED_WORD, blocked.nameStatus());
    }

    @Test
    void dominantFamilyIsByNoteCountWithUnknownVoicesIgnored() {
        Score mixed = new Score(Score.CURRENT_FORMAT_VERSION, 120, 4, 0, 0, List.of(
                new Score.Layer("neronotes:void_bass", List.of(new Score.Note(0, 40, 100, 2))),
                new Score.Layer("neronotes:pulse_lead", List.of(
                        new Score.Note(0, 70, 100, 2), new Score.Note(4, 72, 100, 2))),
                new Score.Layer("unknown:voice", List.of(
                        new Score.Note(0, 60, 100, 2), new Score.Note(1, 60, 100, 2),
                        new Score.Note(2, 60, 100, 2), new Score.Note(3, 60, 100, 2)))));
        assertEquals(VoiceFamily.HIGH_LEAD, DiskPressLogic.dominantFamily(mixed, RESOLVER));
        assertFalse(DiskPressLogic.dominantFamily(mixed, RESOLVER) == VoiceFamily.DEEP_BASS);
    }
}
