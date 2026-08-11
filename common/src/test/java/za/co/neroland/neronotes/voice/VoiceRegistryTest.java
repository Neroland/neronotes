package za.co.neroland.neronotes.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Plain-JVM tests for the data-driven voice registry: the bundled default
 * pack, the defined unknown-voice fallback, and code-free extension by merge.
 */
class VoiceRegistryTest {

    private static VoiceRegistry defaultRegistry() {
        InputStream in = VoiceRegistry.class.getResourceAsStream(VoiceRegistry.DEFAULT_PACK_RESOURCE);
        assertNotNull(in, "bundled default voice pack must be on the classpath");
        return VoiceRegistry.parse(new InputStreamReader(in, StandardCharsets.UTF_8), "default.json");
    }

    @Test
    void bundledPackCoversEveryFamilyAndTheFallback() {
        VoiceRegistry registry = defaultRegistry();
        for (VoiceFamily family : VoiceFamily.values()) {
            assertTrue(!registry.byFamily(family).isEmpty(),
                    "bundled pack must ship at least one voice in family " + family.id());
        }
        assertTrue(registry.lookup(VoiceRegistry.FALLBACK_VOICE_ID).isPresent(),
                "bundled pack must define the fallback voice");
        // Spot-check one definition end to end.
        VoiceDefinition bass = registry.lookup("neronotes:void_bass").orElseThrow();
        assertEquals(VoiceFamily.DEEP_BASS, bass.family());
        assertEquals("neronotes", bass.soundEventId().getNamespace());
        assertEquals("voice.void_bass", bass.soundEventId().getPath());
        assertTrue(bass.minPitch() <= bass.maxPitch());
    }

    @Test
    void unknownVoiceFallsBackGracefully() {
        VoiceRegistry registry = defaultRegistry();
        assertTrue(registry.lookup("neronotes:not_a_voice").isEmpty(), "lookup stays exact");
        VoiceDefinition resolved = registry.resolve("neronotes:not_a_voice");
        assertEquals(VoiceRegistry.FALLBACK_VOICE_ID, resolved.voiceId(),
                "resolve must degrade to the defined fallback, never fail");
        assertEquals(registry.fallback(), resolved);
        // The fallback accepts the full pitch range so any note stays playable.
        assertTrue(resolved.inBand(0) && resolved.inBand(127));
    }

    @Test
    void voicePackAddsEntriesWithoutCode() {
        VoiceRegistry registry = defaultRegistry();
        String pack = """
                { "format_version": 1,
                  "voices": {
                    "example:solar_chime": {
                      "sound_event": "minecraft:block.amethyst_block.chime",
                      "family": "glassy_pluck",
                      "min_pitch": 60,
                      "max_pitch": 108
                    } } }""";
        registry.merge(new StringReader(pack), "example-pack.json");
        VoiceDefinition added = registry.lookup("example:solar_chime").orElseThrow();
        assertEquals(VoiceFamily.GLASSY_PLUCK, added.family());
        assertEquals("minecraft", added.soundEventId().getNamespace());
        // Existing entries survive a merge.
        assertTrue(registry.lookup("neronotes:void_bass").isPresent());
    }

    @Test
    void malformedPacksAreRejectedWhole() {
        VoiceRegistry registry = defaultRegistry();
        int before = registry.voiceIds().size();
        assertThrows(VoicePackFormatException.class,
                () -> registry.merge(reader("{ \"format_version\": 1, \"voices\": { \"x:y\": { \"family\": \"deep_bass\" } } }"),
                        "broken.json"),
                "a voice missing its sound_event must reject the pack");
        assertThrows(VoicePackFormatException.class,
                () -> registry.merge(reader("{ \"format_version\": 1, \"voices\": { \"x:y\": { \"sound_event\": \"a:b\", \"family\": \"kazoo\", \"min_pitch\": 0, \"max_pitch\": 1 } } }"),
                        "unknown-family.json"),
                "an unknown family must reject the pack — families are fixed in 0.1.0");
        assertThrows(VoicePackFormatException.class,
                () -> registry.merge(reader("not json"), "garbage.json"));
        assertEquals(before, registry.voiceIds().size(), "a rejected pack must not half-merge");
    }

    @Test
    void newerPackFormatVersionIsRejectedByName() {
        VoicePackFormatException refusal = assertThrows(VoicePackFormatException.class,
                () -> VoiceRegistry.parse(reader(
                        "{ \"format_version\": 99, \"voices\": { \"x:y\": { \"sound_event\": \"a:b\", \"family\": \"deep_bass\", \"min_pitch\": 0, \"max_pitch\": 1 } } }"),
                        "future.json"));
        assertTrue(refusal.getMessage().contains("99"),
                "message must name the offending version: " + refusal.getMessage());
    }

    private static Reader reader(String json) {
        return new StringReader(json);
    }
}
