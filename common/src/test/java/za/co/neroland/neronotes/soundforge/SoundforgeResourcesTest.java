package za.co.neroland.neronotes.soundforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import za.co.neroland.nerolandcore.worldgen.SpaceTags;

/**
 * Validates the hand-authored Stage 4 datapack JSON (no datagen in this repo)
 * and the SpaceTags safety contract: on a Core-only server the
 * {@code neroland:space/dimensions} tag is empty and that must never throw.
 */
class SoundforgeResourcesTest {

    private static final Gson GSON = new Gson();

    private static JsonObject readJson(String resourcePath) {
        InputStream in = SoundforgeResourcesTest.class.getResourceAsStream(resourcePath);
        assertNotNull(in, resourcePath + " must be on the classpath");
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, JsonObject.class);
        } catch (IOException e) {
            throw new AssertionError("could not read " + resourcePath, e);
        }
    }

    @Test
    void noProgressionGateShips() {
        // Hard gates were removed (standalone-first, 2026-08-15): entering the
        // Soundforge needs only a charged Harmonic Gate. If a neroland_gates
        // datapack file ever reappears, this test fails loudly instead of the
        // gate quietly sealing the dimension again.
        assertNull(SoundforgeResourcesTest.class
                        .getResourceAsStream("/data/neronotes/neroland_gates/soundforge.json"),
                "NeroNotes must not declare progression gates — the Soundforge is ungated");
    }

    @Test
    void dimensionJsonPairIsWellFormedAndConsistent() {
        JsonObject dimension = readJson("/data/neronotes/dimension/soundforge.json");
        assertEquals("neronotes:soundforge", dimension.get("type").getAsString(),
                "the dimension must reference our own dimension type");
        JsonObject generator = dimension.getAsJsonObject("generator");
        assertEquals("minecraft:flat", generator.get("type").getAsString());
        assertEquals(0, generator.getAsJsonObject("settings").getAsJsonArray("layers").size(),
                "a void dimension: the platform is code-built on first entry, not worldgen");

        JsonObject type = readJson("/data/neronotes/dimension_type/soundforge.json");
        assertTrue(type.has("min_y") && type.has("height") && type.has("logical_height"));
        int minY = type.get("min_y").getAsInt();
        int height = type.get("height").getAsInt();
        assertTrue(SoundforgeDimension.GATE_POS.getY() > minY
                        && SoundforgeDimension.GATE_POS.getY() < minY + height,
                "the code-built platform must sit inside the dimension's build range");
    }

    @Test
    void spaceDimensionsTagEntryIsOptionalAndAdditive() {
        JsonObject tag = readJson("/data/neroland/tags/dimension_type/space/dimensions.json");
        assertFalse(tag.get("replace").getAsBoolean(),
                "never replace the shared vocabulary — other mods contribute to it too");
        JsonObject entry = tag.getAsJsonArray("values").get(0).getAsJsonObject();
        assertEquals("neronotes:soundforge", entry.get("id").getAsString());
        assertFalse(entry.get("required").getAsBoolean(),
                "entries into the shared neroland:* tags stay optional by convention");
    }

    @Test
    void spaceTagsAndDimensionHelpersAreNullSafe() {
        // A Core-only server has every neroland:space/* tag EMPTY; consumers
        // must treat "no such place" as a normal answer, never orElseThrow.
        assertFalse(SpaceTags.isSpace(null));
        assertFalse(SpaceTags.biomeIn(null, SpaceTags.PLANET_BIOMES));
        assertFalse(SoundforgeDimension.isSoundforge(null),
                "a missing Soundforge level must read as 'not the Soundforge', not an error");
    }

    @Test
    void recipeUsesCoreMaterialTagsNotForeignItemIds() {
        JsonObject recipe = readJson("/data/neronotes/recipe/harmonic_gate.json");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        JsonObject key = recipe.getAsJsonObject("key");
        assertEquals("#c:plates/nero_alloy", key.get("P").getAsString());
        assertEquals("#nerolandcore:materials/plasma_glass", key.get("G").getAsString());
        String centre = key.get("A").getAsString();
        assertTrue(centre.startsWith("minecraft:") || centre.startsWith("#"),
                "only tags or vanilla ids — never a hardcoded item id from another mod");
        assertEquals("neronotes:harmonic_gate",
                recipe.getAsJsonObject("result").get("id").getAsString());
    }
}
