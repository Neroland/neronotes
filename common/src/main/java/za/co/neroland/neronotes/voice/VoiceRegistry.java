package za.co.neroland.neronotes.voice;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

import za.co.neroland.neronotes.NeroNotesCommon;

/**
 * Voice id → {@link VoiceDefinition} mapping — <strong>data-driven</strong>.
 * Every voice, including the seven built-ins, is declared in
 * {@code assets/neronotes/voices/default.json}; a later voice pack adds
 * entries by merging another JSON document, no code required.
 *
 * <p><strong>Unknown-voice fallback is graceful and defined:</strong>
 * {@link #resolve(String)} never fails and never returns {@code null} — an
 * unknown id resolves to the {@link #FALLBACK_VOICE_ID} definition (full
 * pitch band, high-lead family), and the unknown id is logged once at WARN.
 * A score referencing a voice from an uninstalled pack therefore still
 * plays, just on the fallback timbre.</p>
 *
 * <p>Definitions hold sound-event <em>identifiers</em>, not
 * {@code SoundEvent}s, so this class is plain-JVM testable and server-safe;
 * resolution to a live {@code SoundEvent} happens client-side at play time.
 * The shared instance is created eagerly by {@link #bootstrap()} at init
 * step 5 — never lazily mid-tick.</p>
 */
public final class VoiceRegistry {

    /** The defined fallback voice; guaranteed present in the shared registry. */
    public static final String FALLBACK_VOICE_ID = "neronotes:fallback";

    /** Classpath location of the bundled default voice pack. */
    public static final String DEFAULT_PACK_RESOURCE = "/assets/neronotes/voices/default.json";

    /** Newest voice-pack format version this release reads. */
    public static final int PACK_FORMAT_VERSION = 1;

    private static volatile VoiceRegistry shared;

    private volatile Map<String, VoiceDefinition> voices;
    /** Unknown ids already warned about (bounded; see {@link #resolve}). */
    private final Set<String> warnedUnknown = ConcurrentHashMap.newKeySet();
    private static final int MAX_WARNED_UNKNOWN = 64;

    private VoiceRegistry(Map<String, VoiceDefinition> voices) {
        this.voices = Collections.unmodifiableMap(new LinkedHashMap<>(voices));
    }

    // ------------------------------------------------------------------
    // Shared instance
    // ------------------------------------------------------------------

    /**
     * Create the shared registry from the bundled default pack — init step 5.
     * Idempotent. Fails loud ({@link VoicePackFormatException}) if the
     * bundled pack is missing or malformed: that is a packaging bug, not a
     * runtime condition to paper over.
     */
    public static void bootstrap() {
        if (shared != null) {
            return;
        }
        try (InputStream in = VoiceRegistry.class.getResourceAsStream(DEFAULT_PACK_RESOURCE)) {
            if (in == null) {
                throw new VoicePackFormatException("bundled voice pack " + DEFAULT_PACK_RESOURCE + " is missing from the jar");
            }
            VoiceRegistry parsed = parse(new InputStreamReader(in, StandardCharsets.UTF_8), DEFAULT_PACK_RESOURCE);
            if (!parsed.voices.containsKey(FALLBACK_VOICE_ID)) {
                throw new VoicePackFormatException(
                        "bundled voice pack must define the fallback voice '" + FALLBACK_VOICE_ID + "'");
            }
            shared = parsed;
            NeroNotesCommon.LOGGER.info("[NeroNotes] voice registry ready: {} voices across {} families",
                    parsed.voices.size(), VoiceFamily.values().length);
        } catch (IOException ioFailure) {
            throw new VoicePackFormatException("could not read bundled voice pack " + DEFAULT_PACK_RESOURCE, ioFailure);
        }
    }

    /** The shared registry; throws if {@link #bootstrap()} has not run. */
    public static VoiceRegistry shared() {
        VoiceRegistry instance = shared;
        if (instance == null) {
            throw new IllegalStateException(
                    "VoiceRegistry requested before bootstrap() — it is resolved eagerly at init step 5, never lazily");
        }
        return instance;
    }

    // ------------------------------------------------------------------
    // Parsing (data-driven; also the voice-pack entry point)
    // ------------------------------------------------------------------

    /**
     * Parse a standalone voice document. Format:
     * <pre>{@code
     * { "format_version": 1,
     *   "voices": { "<voice id>": { "sound_event": "<sound event id>",
     *                               "family": "<voice family id>",
     *                               "min_pitch": 0, "max_pitch": 127 } } }
     * }</pre>
     *
     * @throws VoicePackFormatException on any malformed entry or a newer
     *                                  {@code format_version} — a pack is
     *                                  accepted whole or rejected whole
     */
    public static VoiceRegistry parse(Reader reader, String sourceName) {
        return new VoiceRegistry(parseVoices(reader, sourceName));
    }

    /**
     * Merge another voice document into this registry (voice packs). Entries
     * with an existing id replace it — a pack may deliberately re-alias a
     * voice. The whole document is validated before anything is applied.
     */
    public synchronized void merge(Reader reader, String sourceName) {
        Map<String, VoiceDefinition> additions = parseVoices(reader, sourceName);
        Map<String, VoiceDefinition> merged = new LinkedHashMap<>(voices);
        merged.putAll(additions);
        voices = Collections.unmodifiableMap(merged);
        NeroNotesCommon.LOGGER.info("[NeroNotes] merged {} voice(s) from {}", additions.size(), sourceName);
    }

    private static Map<String, VoiceDefinition> parseVoices(Reader reader, String sourceName) {
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new VoicePackFormatException(sourceName + ": root must be a JSON object");
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException malformed) {
            throw new VoicePackFormatException(sourceName + ": not valid JSON", malformed);
        }
        int formatVersion = requireInt(root, "format_version", sourceName);
        if (formatVersion > PACK_FORMAT_VERSION) {
            throw new VoicePackFormatException(sourceName + ": voice pack format version " + formatVersion
                    + " is newer than the newest supported version " + PACK_FORMAT_VERSION);
        }
        JsonElement voicesElement = root.get("voices");
        if (voicesElement == null || !voicesElement.isJsonObject()) {
            throw new VoicePackFormatException(sourceName + ": missing 'voices' object");
        }
        Map<String, VoiceDefinition> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : voicesElement.getAsJsonObject().entrySet()) {
            result.put(entry.getKey(), parseVoice(entry.getKey(), entry.getValue(), sourceName));
        }
        if (result.isEmpty()) {
            throw new VoicePackFormatException(sourceName + ": 'voices' must not be empty");
        }
        return result;
    }

    private static VoiceDefinition parseVoice(String voiceId, JsonElement element, String sourceName) {
        if (!element.isJsonObject()) {
            throw new VoicePackFormatException(sourceName + ": voice '" + voiceId + "' must be a JSON object");
        }
        JsonObject voice = element.getAsJsonObject();
        String soundEventRaw = requireString(voice, "sound_event", voiceId, sourceName);
        Identifier soundEventId = Identifier.tryParse(soundEventRaw);
        if (soundEventId == null) {
            throw new VoicePackFormatException(
                    sourceName + ": voice '" + voiceId + "' has an invalid sound_event id '" + soundEventRaw + "'");
        }
        String familyId = requireString(voice, "family", voiceId, sourceName);
        VoiceFamily family = VoiceFamily.byId(familyId).orElseThrow(() -> new VoicePackFormatException(
                sourceName + ": voice '" + voiceId + "' names unknown family '" + familyId + "'"));
        int minPitch = requireInt(voice, "min_pitch", voiceId, sourceName);
        int maxPitch = requireInt(voice, "max_pitch", voiceId, sourceName);
        try {
            return new VoiceDefinition(voiceId, soundEventId, family, minPitch, maxPitch);
        } catch (IllegalArgumentException invalid) {
            throw new VoicePackFormatException(
                    sourceName + ": voice '" + voiceId + "' is invalid: " + invalid.getMessage(), invalid);
        }
    }

    private static String requireString(JsonObject object, String key, String voiceId, String sourceName) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new VoicePackFormatException(
                    sourceName + ": voice '" + voiceId + "' is missing string field '" + key + "'");
        }
        return value.getAsString();
    }

    private static int requireInt(JsonObject object, String key, String sourceName) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new VoicePackFormatException(sourceName + ": missing integer field '" + key + "'");
        }
        return value.getAsInt();
    }

    private static int requireInt(JsonObject object, String key, String voiceId, String sourceName) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new VoicePackFormatException(
                    sourceName + ": voice '" + voiceId + "' is missing integer field '" + key + "'");
        }
        return value.getAsInt();
    }

    // ------------------------------------------------------------------
    // Lookup
    // ------------------------------------------------------------------

    /** Exact lookup; empty for unknown ids (no fallback applied). */
    public Optional<VoiceDefinition> lookup(String voiceId) {
        return Optional.ofNullable(voices.get(voiceId));
    }

    /**
     * Resolve a voice id, falling back to {@link #FALLBACK_VOICE_ID} for
     * unknown ids — never {@code null}, never a throw. Each unknown id is
     * logged once at WARN (bounded, so a hostile score cannot spam the log).
     */
    public VoiceDefinition resolve(String voiceId) {
        VoiceDefinition definition = voices.get(voiceId);
        if (definition != null) {
            return definition;
        }
        if (warnedUnknown.size() < MAX_WARNED_UNKNOWN && warnedUnknown.add(voiceId)) {
            NeroNotesCommon.LOGGER.warn(
                    "[NeroNotes] unknown voice id '{}' — playing through the fallback voice (is a voice pack missing?)",
                    voiceId);
        }
        VoiceDefinition fallback = voices.get(FALLBACK_VOICE_ID);
        if (fallback == null) {
            // Only reachable on a hand-built registry without the fallback entry.
            throw new IllegalStateException("voice registry has no '" + FALLBACK_VOICE_ID + "' entry");
        }
        return fallback;
    }

    /** The fallback definition — the one {@link #resolve(String)} degrades to. */
    public VoiceDefinition fallback() {
        return resolve(FALLBACK_VOICE_ID);
    }

    /** All known voice ids, in declaration order. */
    public Set<String> voiceIds() {
        return voices.keySet();
    }

    /** All definitions in a family, in declaration order. */
    public List<VoiceDefinition> byFamily(VoiceFamily family) {
        List<VoiceDefinition> result = new ArrayList<>();
        for (VoiceDefinition definition : voices.values()) {
            if (definition.family() == family) {
                result.add(definition);
            }
        }
        return result;
    }
}
