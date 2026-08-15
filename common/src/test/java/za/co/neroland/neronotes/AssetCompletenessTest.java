package za.co.neroland.neronotes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Resource-completeness guard: walks the shipped asset graph — every
 * blockstate &rarr; every referenced model &rarr; every referenced texture,
 * every item definition &rarr; its model, plus {@code sounds.json} — and
 * asserts each referenced {@code neronotes:} file actually ships. Every PNG
 * under {@code textures/} is also verified <em>structurally</em> (signature,
 * per-chunk CRC-32, IEND, inflatable IDAT stream): the first gallery run
 * shipped three byte-corrupt PNGs (transport lectern side/top, pattern wall
 * layer-0 lit) that existed on disk but rendered as the pink/black missing
 * texture — existence checks alone would not have caught them, and this
 * test now does.
 *
 * <p>Plain JVM: reads the exploded {@code common/src/main/resources} off the
 * test classpath (same pattern as {@code SoundforgeResourcesTest}) and
 * touches no Minecraft classes.</p>
 */
class AssetCompletenessTest {

    private static final Gson GSON = new Gson();
    private static final String NAMESPACE = "neronotes:";

    private static Path assetsRoot() {
        URL url = AssetCompletenessTest.class.getResource("/assets/neronotes/blockstates");
        assertNotNull(url, "assets/neronotes/blockstates must be on the test classpath");
        try {
            return Paths.get(url.toURI()).getParent();
        } catch (URISyntaxException e) {
            throw new AssertionError("could not resolve the assets directory", e);
        }
    }

    private static JsonObject readJson(Path file) {
        try (Reader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            assertNotNull(json, file + " must parse as a JSON object");
            return json;
        } catch (IOException e) {
            throw new AssertionError("could not read " + file, e);
        }
    }

    private static List<Path> jsonFilesIn(Path dir) {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new AssertionError("could not list " + dir, e);
        }
    }

    /** Every string value of a {@code "model"} member, anywhere in the tree (variants, arrays, multipart apply). */
    private static void collectModelRefs(JsonElement element, Set<String> into) {
        if (element == null) {
            return;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                if (("model".equals(entry.getKey()) || "apply".equals(entry.getKey()))
                        && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    into.add(value.getAsString());
                } else {
                    collectModelRefs(value, into);
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectModelRefs(child, into);
            }
        }
    }

    /**
     * The completeness walk: blockstates + item definitions &rarr; models
     * (parents chased) &rarr; textures. Every referenced {@code neronotes:}
     * file must exist, and every referenced texture must be a valid PNG.
     */
    @Test
    void everyReferencedModelAndTextureShips() {
        Path assets = assetsRoot();
        Set<String> modelRefs = new TreeSet<>();
        for (Path blockstate : jsonFilesIn(assets.resolve("blockstates"))) {
            collectModelRefs(readJson(blockstate), modelRefs);
        }
        for (Path itemDefinition : jsonFilesIn(assets.resolve("items"))) {
            collectModelRefs(readJson(itemDefinition), modelRefs);
        }
        assertTrue(modelRefs.size() >= 15, "suspiciously few model references — the walk is broken");

        Set<String> visitedModels = new HashSet<>();
        Set<String> textureRefs = new TreeSet<>();
        Deque<String> queue = new ArrayDeque<>(modelRefs);
        while (!queue.isEmpty()) {
            String ref = queue.poll();
            if (!ref.startsWith(NAMESPACE) || !visitedModels.add(ref)) {
                continue; // vanilla parents are the game's problem, not ours
            }
            Path model = assets.resolve("models").resolve(ref.substring(NAMESPACE.length()) + ".json");
            assertTrue(Files.isRegularFile(model), "referenced model " + ref + " does not ship (" + model + ")");
            JsonObject json = readJson(model);
            if (json.has("parent")) {
                queue.add(json.get("parent").getAsString());
            }
            if (json.has("textures")) {
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("textures").entrySet()) {
                    String texture = entry.getValue().getAsString();
                    if (!texture.startsWith("#")) {
                        textureRefs.add(texture);
                    }
                }
            }
        }

        for (String ref : textureRefs) {
            if (!ref.startsWith(NAMESPACE)) {
                continue;
            }
            Path texture = assets.resolve("textures").resolve(ref.substring(NAMESPACE.length()) + ".png");
            assertTrue(Files.isRegularFile(texture), "referenced texture " + ref + " does not ship (" + texture + ")");
            assertValidPng(texture);
        }
        assertTrue(textureRefs.size() >= 15, "suspiciously few texture references — the walk is broken");
    }

    /**
     * Every shipped PNG must be structurally sound, referenced or not — this
     * is the regression pin for the three corrupt gallery textures.
     */
    @Test
    void everyShippedPngIsStructurallyValid() {
        Path textures = assetsRoot().resolve("textures");
        List<Path> pngs;
        try (Stream<Path> stream = Files.walk(textures)) {
            pngs = stream.filter(p -> p.toString().endsWith(".png")).sorted().toList();
        } catch (IOException e) {
            throw new AssertionError("could not list " + textures, e);
        }
        assertTrue(pngs.size() >= 40, "suspiciously few PNGs — the walk is broken");
        for (Path png : pngs) {
            assertValidPng(png);
        }
    }

    /** {@code sounds.json} may alias other sound EVENTS freely; a direct file reference must ship. */
    @Test
    void soundsJsonReferencesOnlyEventsOrShippedFiles() {
        Path assets = assetsRoot();
        JsonObject sounds = readJson(assets.resolve("sounds.json"));
        assertTrue(sounds.size() > 0, "sounds.json must define the voice events");
        for (Map.Entry<String, JsonElement> event : sounds.entrySet()) {
            JsonArray entries = event.getValue().getAsJsonObject().getAsJsonArray("sounds");
            assertNotNull(entries, "sound event " + event.getKey() + " must list its sounds");
            for (JsonElement entry : entries) {
                String name;
                boolean isEvent;
                if (entry.isJsonObject()) {
                    JsonObject sound = entry.getAsJsonObject();
                    name = sound.get("name").getAsString();
                    isEvent = sound.has("type") && "event".equals(sound.get("type").getAsString());
                } else {
                    name = entry.getAsString();
                    isEvent = false;
                }
                if (isEvent || !name.startsWith(NAMESPACE)) {
                    continue; // vanilla/event aliases resolve at runtime
                }
                Path ogg = assets.resolve("sounds").resolve(name.substring(NAMESPACE.length()) + ".ogg");
                assertTrue(Files.isRegularFile(ogg),
                        "sound event " + event.getKey() + " references " + name + " but " + ogg + " does not ship");
            }
        }
    }

    // ------------------------------------------------------------------
    // PNG structural validation (signature, chunk CRCs, IEND, IDAT inflate)
    // ------------------------------------------------------------------

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private static void assertValidPng(Path file) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file, e);
        }
        assertTrue(bytes.length > PNG_SIGNATURE.length
                        && Arrays.equals(Arrays.copyOfRange(bytes, 0, PNG_SIGNATURE.length), PNG_SIGNATURE),
                file + ": not a PNG (bad signature)");

        CRC32 crc = new CRC32();
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        boolean sawIhdr = false;
        boolean sawIend = false;
        int pos = PNG_SIGNATURE.length;
        while (pos + 12 <= bytes.length && !sawIend) {
            int length = readInt(bytes, pos);
            assertTrue(length >= 0 && pos + 12 + length <= bytes.length,
                    file + ": truncated inside a chunk at offset " + pos);
            String type = new String(bytes, pos + 4, 4, StandardCharsets.US_ASCII);
            crc.reset();
            crc.update(bytes, pos + 4, 4 + length);
            assertEquals(readInt(bytes, pos + 8 + length), (int) crc.getValue(),
                    file + ": bad CRC on chunk " + type);
            switch (type) {
                case "IHDR" -> sawIhdr = true;
                case "IDAT" -> idat.write(bytes, pos + 8, length);
                case "IEND" -> sawIend = true;
                default -> { /* ancillary chunk — CRC already checked */ }
            }
            pos += 12 + length;
        }
        assertTrue(sawIhdr, file + ": missing IHDR chunk");
        assertTrue(sawIend, file + ": missing IEND chunk (truncated file)");
        assertTrue(idat.size() > 0, file + ": no IDAT pixel data");

        Inflater inflater = new Inflater();
        inflater.setInput(idat.toByteArray());
        byte[] buffer = new byte[8192];
        try {
            while (!inflater.finished()) {
                if (inflater.inflate(buffer) == 0 && inflater.needsInput()) {
                    break;
                }
            }
            assertTrue(inflater.finished(), file + ": IDAT stream ends prematurely");
        } catch (DataFormatException e) {
            fail(file + ": corrupt IDAT stream: " + e.getMessage());
        } finally {
            inflater.end();
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }
}
