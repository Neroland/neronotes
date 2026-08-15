package za.co.neroland.neronotes.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkActionResult;

import za.co.neroland.neronotes.item.DiskContents;
import za.co.neroland.neronotes.library.LibraryTable;
import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.signal.ChannelKey;
import za.co.neroland.neronotes.signal.ChannelTable;
import za.co.neroland.neronotes.signal.ResonanceChannel;

/**
 * Plain-JVM tests for the Stage 9 link module's decision points — the
 * visibility rule ({@code NotesLinkAccess}), the transport refusal ladder
 * ({@code NotesLinkActions.refusalFor}), and the row builders whose privacy
 * invariants are load-bearing: anonymous entries carry NO author key, channel
 * rows never carry an owner UUID, and a stranger's channel set is empty —
 * never a roster.
 */
class NotesLinkTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID TRUSTED = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static final String DIM = "minecraft:overworld";

    private static Score score() {
        return new Score(Score.CURRENT_FORMAT_VERSION, 120, 4, 0, 0,
                List.of(new Score.Layer("neronotes:void_bass",
                        List.of(new Score.Note(0, 60, 100, 4)))));
    }

    private static ResonanceChannel channel(UUID owner, String name, UUID... trusted) {
        ResonanceChannel channel = ResonanceChannel.create(new ChannelKey(DIM, owner, name));
        for (UUID player : trusted) {
            channel = channel.withTrusted(player);
        }
        return channel;
    }

    // ------------------------------------------------------------------
    // Channel references
    // ------------------------------------------------------------------

    @Test
    void channelRefIsDeterministicAndDistinct() {
        ChannelKey key = new ChannelKey(DIM, OWNER, "base");
        assertEquals(NotesLinkAccess.channelRef(key), NotesLinkAccess.channelRef(key));
        assertNotEquals(NotesLinkAccess.channelRef(key),
                NotesLinkAccess.channelRef(new ChannelKey(DIM, OWNER, "workshop")));
        assertNotEquals(NotesLinkAccess.channelRef(key),
                NotesLinkAccess.channelRef(new ChannelKey(DIM, STRANGER, "base")));
        assertNotEquals(NotesLinkAccess.channelRef(key),
                NotesLinkAccess.channelRef(new ChannelKey("neronotes:soundforge", OWNER, "base")));
        // One-way: the reference never embeds the owner UUID.
        assertFalse(NotesLinkAccess.channelRef(key).contains(OWNER.toString()));
    }

    // ------------------------------------------------------------------
    // Visibility / permission rule
    // ------------------------------------------------------------------

    @Test
    void ownerAndTrustedResolveTheirChannel() {
        ResonanceChannel channel = channel(OWNER, "base", TRUSTED);
        String ref = NotesLinkAccess.channelRef(channel.key());

        assertTrue(NotesLinkAccess.controllableChannel(OWNER, ref, List.of(channel), List.of()).isPresent());
        assertTrue(NotesLinkAccess.controllableChannel(TRUSTED, ref, List.of(), List.of(channel)).isPresent());
    }

    @Test
    void strangersChannelIsInvisible() {
        // The stranger's request arrives with THEIR owned/trusted sets, which do
        // not contain the channel — resolution fails closed, same as nonexistent.
        ResonanceChannel channel = channel(OWNER, "base", TRUSTED);
        String ref = NotesLinkAccess.channelRef(channel.key());

        assertTrue(NotesLinkAccess.controllableChannel(STRANGER, ref, List.of(), List.of()).isEmpty());
        // Even a poisoned candidate list does not widen access: canControl re-checks.
        assertTrue(NotesLinkAccess.controllableChannel(STRANGER, ref, List.of(channel), List.of(channel))
                .isEmpty());
    }

    @Test
    void nonOwnerTransportRequestIsNotOwner() {
        ResonanceChannel channel = channel(OWNER, "base");
        String ref = NotesLinkAccess.channelRef(channel.key());

        LinkActionResult refusal = NotesLinkActions.refusalFor(STRANGER, ref, List.of(), List.of());
        assertNotNull(refusal);
        assertFalse(refusal.ok());
        assertEquals(LinkActionResult.Error.NOT_OWNER, refusal.error());
    }

    @Test
    void ownerAndTrustedTransportRequestsPass() {
        ResonanceChannel channel = channel(OWNER, "base", TRUSTED);
        String ref = NotesLinkAccess.channelRef(channel.key());

        assertNull(NotesLinkActions.refusalFor(OWNER, ref, List.of(channel), List.of()));
        assertNull(NotesLinkActions.refusalFor(TRUSTED, ref, List.of(), List.of(channel)));
    }

    @Test
    void missingChannelParameterIsValidation() {
        LinkActionResult refusal = NotesLinkActions.refusalFor(OWNER, null, List.of(), List.of());
        assertNotNull(refusal);
        assertEquals(LinkActionResult.Error.VALIDATION, refusal.error());
        LinkActionResult blank = NotesLinkActions.refusalFor(OWNER, "  ", List.of(), List.of());
        assertNotNull(blank);
        assertEquals(LinkActionResult.Error.VALIDATION, blank.error());
    }

    // ------------------------------------------------------------------
    // Library rows — the anonymity invariant
    // ------------------------------------------------------------------

    @Test
    void creditedLibraryRowCarriesTheDisplayName() {
        LibraryTable table = new LibraryTable();
        int id = table.publish("Starlight", OWNER, "Composer", false, "high_lead",
                new byte[] {1}, 10, 10, false).id();

        JsonObject row = NotesLinkSnapshots.libraryRow(table.entry(id).orElseThrow());
        assertEquals("Composer", row.get("author").getAsString());
        assertFalse(row.get("anonymous").getAsBoolean());
        // The author UUID appears on no link surface, credited or not.
        assertFalse(row.toString().contains(OWNER.toString()));
    }

    @Test
    void anonymousLibraryRowCarriesNoAuthorKey() {
        LibraryTable table = new LibraryTable();
        int id = table.publish("Voidsong", OWNER, "ignored", true, "deep_bass",
                new byte[] {1}, 10, 10, false).id();

        JsonObject row = NotesLinkSnapshots.libraryRow(table.entry(id).orElseThrow());
        assertFalse(row.has("author"), "an anonymous entry must carry NO author key at all");
        assertTrue(row.get("anonymous").getAsBoolean());
        assertFalse(row.toString().contains(OWNER.toString()));
    }

    @Test
    void erasedAuthorRowCarriesNoAuthorKey() {
        LibraryTable table = new LibraryTable();
        int id = table.publish("Kept Work", OWNER, "Composer", false, "high_lead",
                new byte[] {1}, 10, 10, false).id();
        assertTrue(table.anonymiseAuthor(OWNER));

        JsonObject row = NotesLinkSnapshots.libraryRow(table.entry(id).orElseThrow());
        assertFalse(row.has("author"), "a severed entry must be indistinguishable from a chosen-anonymous one");
        assertTrue(row.get("anonymous").getAsBoolean());
    }

    // ------------------------------------------------------------------
    // Disk rows — same invariant for carried disks
    // ------------------------------------------------------------------

    @Test
    void anonymousDiskRowCarriesNoAuthorKey() {
        DiskContents contents = new DiskContents(score(), "Quiet One", OWNER, "ignored", true, "high_lead");
        JsonObject row = NotesLinkSnapshots.diskRow(contents, OWNER);
        assertFalse(row.has("author"));
        assertTrue(row.get("anonymous").getAsBoolean());
        assertTrue(row.get("authored_by_you").getAsBoolean());
        assertFalse(row.toString().contains(OWNER.toString()));
    }

    @Test
    void creditedDiskRowNamesTheComposerButNeverTheUuid() {
        DiskContents contents = new DiskContents(score(), "Loud One", OWNER, "Composer", false, "percussion");
        JsonObject row = NotesLinkSnapshots.diskRow(contents, TRUSTED);
        assertEquals("Composer", row.get("author").getAsString());
        assertFalse(row.get("authored_by_you").getAsBoolean());
        assertFalse(row.toString().contains(OWNER.toString()));
    }

    // ------------------------------------------------------------------
    // Channel rows — no roster, no owner UUID
    // ------------------------------------------------------------------

    @Test
    void strangerGetsNoChannelRows() {
        // The store reads for a stranger yield empty owned/trusted lists — the
        // section is empty, never a server-wide roster.
        ChannelTable table = new ChannelTable();
        table.create(new ChannelKey(DIM, OWNER, "base"));
        table.create(new ChannelKey(DIM, TRUSTED, "workshop"));

        JsonArray rows = NotesLinkSnapshots.channelRows(
                table.channelsOwnedBy(STRANGER), table.channelsTrusting(STRANGER),
                key -> true, key -> 5, false);
        assertEquals(0, rows.size());
    }

    @Test
    void channelRowsCoverOwnedAndTrustedAndNeverCarryAnOwnerUuid() {
        ChannelTable table = new ChannelTable();
        ChannelKey ownedKey = new ChannelKey(DIM, TRUSTED, "workshop");
        ChannelKey trustedKey = new ChannelKey(DIM, OWNER, "base");
        table.create(ownedKey);
        table.create(trustedKey);
        table.trust(trustedKey, TRUSTED);

        JsonArray rows = NotesLinkSnapshots.channelRows(
                table.channelsOwnedBy(TRUSTED), table.channelsTrusting(TRUSTED),
                key -> false, key -> 0, false);
        assertEquals(2, rows.size());
        assertEquals("owner", rows.get(0).getAsJsonObject().get("role").getAsString());
        assertTrue(rows.get(0).getAsJsonObject().has("trusted_count"));
        assertEquals("trusted", rows.get(1).getAsJsonObject().get("role").getAsString());
        // No owner UUID anywhere — not the requester's, not the trusted channel's owner's.
        String serialised = rows.toString();
        assertFalse(serialised.contains(OWNER.toString()));
        assertFalse(serialised.contains(TRUSTED.toString()));
    }

    @Test
    void nowPlayingRowsAreThePlayingSubsetOnly() {
        ChannelTable table = new ChannelTable();
        ChannelKey playing = new ChannelKey(DIM, OWNER, "base");
        ChannelKey silent = new ChannelKey(DIM, OWNER, "workshop");
        table.create(playing);
        table.create(silent);

        JsonArray rows = NotesLinkSnapshots.channelRows(
                table.channelsOwnedBy(OWNER), List.of(),
                playing::equals, key -> 2, true);
        assertEquals(1, rows.size());
        JsonObject row = rows.get(0).getAsJsonObject();
        assertEquals("base", row.get("name").getAsString());
        assertTrue(row.get("playing").getAsBoolean());
        assertEquals(2, row.get("subscribers").getAsInt());
    }

    // ------------------------------------------------------------------
    // Snapshot edges
    // ------------------------------------------------------------------

    @Test
    void snapshotWithoutAServerOrForUnknownSectionsIsEmpty() {
        // No server has been remembered in a plain-JVM test — every section
        // (known or not) answers an empty object rather than guessing.
        NotesLinkSnapshots snapshots = new NotesLinkSnapshots();
        assertEquals(0, snapshots.snapshot(OWNER, "library", Map.of()).size());
        assertEquals(0, snapshots.snapshot(OWNER, "no_such_section", Map.of()).size());
        assertEquals(0, snapshots.snapshot(null, "library", Map.of()).size());
        assertEquals(0, snapshots.snapshot(OWNER, null, Map.of()).size());
    }

    @Test
    void moduleSurfaceIsDeclaredConsistently() {
        NotesLinkSnapshots snapshots = new NotesLinkSnapshots();
        assertEquals("neronotes", snapshots.moduleId());
        assertEquals(NotesLinkModule.SCHEMA_VERSION, snapshots.schemaVersion());
        assertEquals(List.of("library", "disks", "channels", "now_playing"), snapshots.sections());

        NotesLinkActions actions = new NotesLinkActions();
        assertEquals("neronotes", actions.moduleId());
        // play/stop only — 0.1.0 has no playlists, so there is deliberately no "skip".
        assertEquals(List.of("play", "stop"), actions.actionIds());
        // Playback control is online-only: the SPI default (false) is the policy.
        assertFalse(actions.allowOffline("play"));
        assertFalse(actions.allowOffline("stop"));
    }
}
