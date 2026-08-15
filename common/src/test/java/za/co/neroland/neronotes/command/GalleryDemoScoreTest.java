package za.co.neroland.neronotes.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.score.ScoreCodec;
import za.co.neroland.neronotes.score.ScoreFormatException;
import za.co.neroland.neronotes.soundforge.SessionEditor;
import za.co.neroland.neronotes.voice.VoiceDefinition;
import za.co.neroland.neronotes.voice.VoiceRegistry;

/**
 * Plain-JVM tests for the {@code /neronotes gallery} demo score: it builds,
 * round-trips exactly, sits far under the disk budget, loops, stays within
 * the in-game sequencer's layer bound, and references only voices that the
 * bundled default voice pack actually registers — with every note inside its
 * voice's pitch band (nothing plays through the fallback).
 */
class GalleryDemoScoreTest {

    /** The shipped default of {@code disk.score_budget_bytes} (see the config schema). */
    private static final int DEFAULT_DISK_BUDGET_BYTES = 16384;

    private static VoiceRegistry defaultRegistry() {
        InputStream in = VoiceRegistry.class.getResourceAsStream(VoiceRegistry.DEFAULT_PACK_RESOURCE);
        assertNotNull(in, "bundled default voice pack must be on the classpath");
        return VoiceRegistry.parse(new InputStreamReader(in, StandardCharsets.UTF_8), "default.json");
    }

    @Test
    void buildsAndLoopsIndefinitely() {
        Score demo = GalleryDemoScore.build();
        assertTrue(demo.hasLoop(), "the gallery demo must loop — it is the repeating beat");
        assertEquals(GalleryDemoScore.LOOP_START_TICK, demo.loopStartTick());
        assertEquals(GalleryDemoScore.LOOP_END_TICK, demo.loopEndTick());
        assertTrue(demo.loopStartTick() < demo.loopEndTick());
        // Every note starts inside the loop region, so each pass plays the whole beat.
        for (Score.Layer layer : demo.layers()) {
            for (Score.Note note : layer.notes()) {
                assertTrue(note.tick() < demo.loopEndTick(),
                        "note at tick " + note.tick() + " would never re-play inside the loop");
            }
        }
    }

    @Test
    void everyNoteLandsOnTheGameTickGrid() {
        Score demo = GalleryDemoScore.build();
        // One score tick in ms must be a whole multiple of one game tick
        // (50 ms) — see the timing note on Score. 120 BPM x 4 (125 ms = 2.5
        // game ticks) was the original off-beat bug; this pins the fix.
        int product = demo.tempoBpm() * demo.ticksPerBeat();
        assertEquals(0, 60000 % product,
                "tick duration must be a whole number of milliseconds");
        int tickDurationMs = 60000 / product;
        assertEquals(0, tickDurationMs % 50,
                "tick duration (" + tickDurationMs + " ms) must sit on the 50 ms game-tick grid");
        // The loop must span a whole number of 4/4 bars, or the wrap lands mid-bar.
        int barTicks = 4 * demo.ticksPerBeat();
        assertEquals(0, (demo.loopEndTick() - demo.loopStartTick()) % barTicks,
                "loop length must be a whole number of bars");
    }

    @Test
    void staysWithinSequencerRepresentableBounds() {
        Score demo = GalleryDemoScore.build();
        assertTrue(demo.layers().size() <= SessionEditor.MAX_LAYERS,
                "the demo must stay representative of what the in-game sequencer can produce");
        assertTrue(demo.noteCount() > 0, "an empty demo demonstrates nothing");
    }

    @Test
    void roundTripsExactlyAndFitsTheDiskBudget() throws ScoreFormatException {
        Score demo = GalleryDemoScore.build();
        byte[] bytes = ScoreCodec.toBytes(demo, DEFAULT_DISK_BUDGET_BYTES);
        assertTrue(bytes.length < DEFAULT_DISK_BUDGET_BYTES,
                "demo is " + bytes.length + " bytes — it must sit well under the default disk budget");
        Score back = ScoreCodec.fromBytes(bytes, ScoreCodec.HARD_BUDGET_CEILING_BYTES);
        assertEquals(demo, back, "byte round-trip must be exact");
    }

    @Test
    void usesOnlyRegisteredVoicesWithinTheirPitchBands() {
        VoiceRegistry registry = defaultRegistry();
        Score demo = GalleryDemoScore.build();
        for (Score.Layer layer : demo.layers()) {
            VoiceDefinition voice = registry.lookup(layer.voiceId()).orElseThrow(
                    () -> new AssertionError("demo voice '" + layer.voiceId()
                            + "' is not in the bundled voice pack — it would play through the fallback"));
            for (Score.Note note : layer.notes()) {
                assertTrue(voice.inBand(note.pitch()),
                        "pitch " + note.pitch() + " is outside " + layer.voiceId()
                                + "'s band [" + voice.minPitch() + ", " + voice.maxPitch() + "]");
            }
        }
    }
}
