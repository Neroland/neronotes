package za.co.neroland.neronotes.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

/** NeroNotes' Core export section: same document, no envelope or UUID, scores by size, null when empty. */
class DataExportCoreSectionTest {

    private static JsonObject document(UUID subject, boolean recorded, boolean withEntry) {
        JsonObject root = new JsonObject();
        root.addProperty("mod", "neronotes");
        root.addProperty("format", 1);
        root.addProperty("exported_at", "2026-11-22T00:00:00Z");
        root.addProperty("subject", subject.toString());
        JsonObject activity = new JsonObject();
        activity.addProperty("recorded", recorded);
        root.add("activity", activity);
        root.add("channels_owned", new JsonArray());
        root.add("channel_trust_memberships", new JsonArray());
        JsonObject session = new JsonObject();
        session.addProperty("present", false);
        root.add("soundforge_session", session);
        JsonArray entries = new JsonArray();
        if (withEntry) {
            JsonObject row = new JsonObject();
            row.addProperty("title", "Tune");
            row.addProperty("score_size_bytes", 3);
            row.addProperty("score_nbt_base64", "AAAA");
            entries.add(row);
        }
        root.add("published_library_entries", entries);
        root.add("policy_notes", new JsonObject());
        return root;
    }

    @Test
    void nothingStoredIsNull() {
        assertNull(DataExport.coreSection(document(UUID.randomUUID(), false, false)));
    }

    @Test
    void dropsEnvelopeUuidAndScoreBytes() {
        UUID subject = UUID.randomUUID();
        JsonObject out = DataExport.coreSection(document(subject, true, true));

        assertFalse(out.has("subject"));
        assertFalse(out.has("mod"));
        assertFalse(out.toString().contains(subject.toString()));
        JsonObject row = out.getAsJsonArray("published_library_entries").get(0).getAsJsonObject();
        assertEquals("Tune", row.get("title").getAsString());
        assertEquals(3, row.get("score_size_bytes").getAsInt());
        assertFalse(row.has("score_nbt_base64"));
        assertTrue(out.has("note"));
    }

    @Test
    void doesNotChangeTheFullDocument() {
        JsonObject doc = document(UUID.randomUUID(), true, true);
        DataExport.coreSection(doc);
        assertTrue(doc.getAsJsonArray("published_library_entries").get(0).getAsJsonObject().has("score_nbt_base64"));
    }
}
