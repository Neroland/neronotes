package za.co.neroland.neronotes.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

import za.co.neroland.neronotes.score.Score.Layer;
import za.co.neroland.neronotes.score.Score.Note;

/**
 * Plain-JVM tests for the score model and codec: exact round-trips,
 * over-budget rejection, and named rejection of newer format versions.
 */
class ScoreCodecTest {

    private static Score sampleScore() {
        List<Note> bassline = List.of(
                new Note(0, 36, 100, 4),
                new Note(4, 38, 90, 4),
                new Note(8, 36, 100, 8));
        List<Note> lead = List.of(
                new Note(0, 72, 110, 2),
                new Note(2, 76, 105, 2),
                new Note(4, 79, 127, 4),
                new Note(12, 84, 60, 4));
        return new Score(Score.CURRENT_FORMAT_VERSION, 120, 4, 0, 16, List.of(
                new Layer("neronotes:void_bass", bassline),
                new Layer("neronotes:pulse_lead", lead)));
    }

    /** A score guaranteed to serialise over any small budget. */
    private static Score bigScore(int notes) {
        List<Note> many = new ArrayList<>(notes);
        for (int i = 0; i < notes; i++) {
            many.add(new Note(i, 60 + (i % 24), 100, 2));
        }
        return new Score(Score.CURRENT_FORMAT_VERSION, 150, 8, 0, 0,
                List.of(new Layer("neronotes:crystal_pluck", many)));
    }

    @Test
    void nbtRoundTripIsExact() throws ScoreFormatException {
        Score original = sampleScore();
        Score back = ScoreCodec.fromNbt(ScoreCodec.toNbt(original));
        assertEquals(original, back, "NBT round-trip must be exact");
    }

    @Test
    void byteRoundTripIsExact() throws ScoreFormatException {
        Score original = sampleScore();
        byte[] bytes = ScoreCodec.toBytes(original);
        Score back = ScoreCodec.fromBytes(bytes, ScoreCodec.HARD_BUDGET_CEILING_BYTES);
        assertEquals(original, back, "byte round-trip must be exact");
    }

    @Test
    void serialisedSizeMatchesActualBytes() {
        Score score = sampleScore();
        assertEquals(ScoreCodec.toBytes(score).length, ScoreCodec.serialisedSize(score));
    }

    @Test
    void overBudgetEncodeIsRefusedNamingTheLimit() {
        Score big = bigScore(2000); // ~32 KiB of packed notes
        int budget = 1024;
        ScoreSizeException refusal = assertThrows(ScoreSizeException.class,
                () -> ScoreCodec.toBytes(big, budget));
        assertEquals(budget, refusal.budgetBytes());
        assertTrue(refusal.actualBytes() > budget);
        assertTrue(refusal.getMessage().contains(String.valueOf(budget)),
                "refusal must name the limit: " + refusal.getMessage());
        assertThrows(ScoreSizeException.class, () -> ScoreCodec.checkBudget(big, budget));
    }

    @Test
    void withinBudgetPasses() throws ScoreFormatException {
        ScoreCodec.checkBudget(sampleScore(), 16384);
    }

    @Test
    void overBudgetDecodeIsRefusedBeforeParsing() {
        byte[] oversized = new byte[2048]; // garbage on purpose — must be rejected on size alone
        ScoreSizeException refusal = assertThrows(ScoreSizeException.class,
                () -> ScoreCodec.fromBytes(oversized, 1024));
        assertEquals(oversized.length, refusal.actualBytes(),
                "size must be rejected before any NBT parsing sees the bytes");
    }

    @Test
    void futureFormatVersionIsRejectedByName() {
        CompoundTag tag = ScoreCodec.toNbt(sampleScore());
        int future = Score.CURRENT_FORMAT_VERSION + 1;
        tag.putInt("v", future);
        ScoreVersionException refusal = assertThrows(ScoreVersionException.class,
                () -> ScoreCodec.fromNbt(tag));
        assertEquals(future, refusal.scoreVersion());
        assertEquals(Score.CURRENT_FORMAT_VERSION, refusal.supportedVersion());
        assertTrue(refusal.getMessage().contains(String.valueOf(future)),
                "message must name the offending version: " + refusal.getMessage());
    }

    @Test
    void malformedNbtIsANamedErrorNeverASilentPartialParse() {
        assertThrows(ScoreFormatException.class, () -> ScoreCodec.fromNbt(new CompoundTag()),
                "an empty tag must fail loud");
        CompoundTag truncatedLayer = ScoreCodec.toNbt(sampleScore());
        truncatedLayer.getList("layers").orElseThrow().getCompound(0).orElseThrow().remove("notes");
        assertThrows(ScoreFormatException.class, () -> ScoreCodec.fromNbt(truncatedLayer),
                "a layer without notes must fail loud");
        assertThrows(ScoreFormatException.class,
                () -> ScoreCodec.fromBytes(new byte[] {1, 2, 3}, 1024),
                "corrupt bytes must fail loud");
    }

    @Test
    void invalidRangesAreRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new Note(0, 128, 100, 1));
        assertThrows(IllegalArgumentException.class, () -> new Note(-1, 60, 100, 1));
        assertThrows(IllegalArgumentException.class, () -> new Note(0, 60, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> new Layer(" ", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Score(Score.CURRENT_FORMAT_VERSION, 0, 4, 0, 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Score(Score.CURRENT_FORMAT_VERSION, 120, 4, 8, 8, List.of()),
                "a non-zero loop end must lie after the loop start");
    }

    @Test
    void loopSemantics() {
        assertTrue(sampleScore().hasLoop());
        Score noLoop = new Score(Score.CURRENT_FORMAT_VERSION, 120, 4, 0, 0, List.of());
        assertEquals(false, noLoop.hasLoop());
    }
}
