package za.co.neroland.neronotes.library;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

import za.co.neroland.neronotes.library.LibraryTable.Entry;
import za.co.neroland.neronotes.library.LibraryTable.PublishError;
import za.co.neroland.neronotes.library.LibraryTable.PublishResult;
import za.co.neroland.neronotes.library.LibraryTable.UnpublishResult;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The published-disk library rules (Stage 6): quota and size-cap
 * enforcement, pagination from day one, op-approval gating (hidden until
 * approved), author-only unpublish vs operator takedown, aggregate-only
 * download counting, the anonymity invariant, NBT round-trips, and the
 * "sever the link, keep the work" author anonymisation the erasure hook will
 * call.
 */
class LibraryTableTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static final int BIG_CAP = 1000;
    private static final int BIG_QUOTA = 100;

    private static PublishResult publish(LibraryTable table, String title, UUID author,
                                         boolean anonymous, boolean pending) {
        return table.publish(title, author, anonymous ? "" : "Composer-" + author.toString().charAt(35),
                anonymous, "high_lead", new byte[] { 1, 2, 3 }, BIG_CAP, BIG_QUOTA, pending);
    }

    // ------------------------------------------------------------------
    // Quota + size cap
    // ------------------------------------------------------------------

    @Test
    void perPlayerQuotaIsEnforcedPerAuthor() {
        LibraryTable table = new LibraryTable();
        for (int i = 0; i < 3; i++) {
            assertTrue(table.publish("Song " + i, ALICE, "Alice", false, "high_lead",
                    new byte[] { 1 }, BIG_CAP, 3, false).ok());
        }
        PublishResult refused = table.publish("One Too Many", ALICE, "Alice", false, "high_lead",
                new byte[] { 1 }, BIG_CAP, 3, false);
        assertEquals(PublishError.QUOTA_EXCEEDED, refused.error());
        // Another author is unaffected by Alice's quota.
        assertTrue(table.publish("Bob's Song", BOB, "Bob", false, "high_lead",
                new byte[] { 1 }, BIG_CAP, 3, false).ok());
        assertEquals(3, table.countBy(ALICE));
        assertEquals(1, table.countBy(BOB));
    }

    @Test
    void pendingEntriesStillOccupyTheQuota() {
        LibraryTable table = new LibraryTable();
        assertTrue(table.publish("Hidden", ALICE, "Alice", false, "high_lead",
                new byte[] { 1 }, BIG_CAP, 1, true).ok());
        assertEquals(PublishError.QUOTA_EXCEEDED, table.publish("Second", ALICE, "Alice", false,
                "high_lead", new byte[] { 1 }, BIG_CAP, 1, false).error());
    }

    @Test
    void serverWideSizeCapIsEnforced() {
        LibraryTable table = new LibraryTable();
        assertTrue(publish(table, "First", ALICE, false, false).ok());
        assertTrue(publish(table, "Second", BOB, false, false).ok());
        PublishResult refused = table.publish("Third", ALICE, "Alice", false, "high_lead",
                new byte[] { 1 }, 2, BIG_QUOTA, false);
        assertEquals(PublishError.LIBRARY_FULL, refused.error());
        assertEquals(2, table.totalCount());
    }

    // ------------------------------------------------------------------
    // Pagination (locked decision 5: from day one)
    // ------------------------------------------------------------------

    @Test
    void paginationSplitsVisibleEntriesInPublishOrder() {
        LibraryTable table = new LibraryTable();
        for (int i = 0; i < 12; i++) {
            assertTrue(publish(table, "Song " + i, ALICE, false, false).ok());
        }
        assertEquals(3, table.pageCount(5));
        assertEquals(5, table.visiblePage(0, 5).size());
        assertEquals(5, table.visiblePage(1, 5).size());
        List<Entry> last = table.visiblePage(2, 5);
        assertEquals(2, last.size());
        assertEquals("Song 0", table.visiblePage(0, 5).get(0).title());
        assertEquals("Song 10", last.get(0).title());
        assertEquals("Song 11", last.get(1).title());
    }

    @Test
    void paginationBoundariesAndEmptyPagesAreSafe() {
        LibraryTable table = new LibraryTable();
        // Empty library: zero pages, every page empty.
        assertEquals(0, table.pageCount(50));
        assertTrue(table.visiblePage(0, 50).isEmpty());
        assertTrue(table.visiblePage(7, 50).isEmpty());
        assertTrue(table.visiblePage(-1, 50).isEmpty());
        // An exact multiple has no ragged last page.
        for (int i = 0; i < 10; i++) {
            assertTrue(publish(table, "Song " + i, ALICE, false, false).ok());
        }
        assertEquals(2, table.pageCount(5));
        assertTrue(table.visiblePage(2, 5).isEmpty()); // one past the end
    }

    // ------------------------------------------------------------------
    // Op-approval gating (locked decision 6, default off)
    // ------------------------------------------------------------------

    @Test
    void pendingEntriesAreHiddenUntilApproved() {
        LibraryTable table = new LibraryTable();
        int visibleId = publish(table, "Visible", ALICE, false, false).id();
        int pendingId = publish(table, "Awaiting", BOB, false, true).id();
        assertEquals(1, table.visibleCount());
        assertEquals(2, table.totalCount());
        List<Entry> page = table.visiblePage(0, 50);
        assertEquals(1, page.size());
        assertEquals(visibleId, page.get(0).id());
        // A pending entry cannot be downloaded either.
        assertFalse(table.incrementDownloads(pendingId));
        // Approval flips it visible exactly once.
        assertTrue(table.approve(pendingId));
        assertFalse(table.approve(pendingId));
        assertFalse(table.approve(9999));
        assertEquals(2, table.visibleCount());
        assertTrue(table.incrementDownloads(pendingId));
    }

    // ------------------------------------------------------------------
    // Takedown: author-only unpublish, operator remove
    // ------------------------------------------------------------------

    @Test
    void unpublishIsAuthorOnly() {
        LibraryTable table = new LibraryTable();
        int id = publish(table, "Alice's Song", ALICE, false, false).id();
        assertEquals(UnpublishResult.NOT_AUTHOR, table.unpublish(id, BOB));
        assertEquals(UnpublishResult.NOT_AUTHOR, table.unpublish(id, null));
        assertTrue(table.entry(id).isPresent());
        assertEquals(UnpublishResult.REMOVED, table.unpublish(id, ALICE));
        assertTrue(table.entry(id).isEmpty());
        assertEquals(UnpublishResult.NO_SUCH_ENTRY, table.unpublish(id, ALICE));
    }

    @Test
    void operatorRemoveDeletesOutright() {
        LibraryTable table = new LibraryTable();
        int id = publish(table, "Contested", ALICE, false, false).id();
        assertTrue(table.remove(id));
        assertTrue(table.entry(id).isEmpty());
        assertFalse(table.remove(id));
        assertEquals(0, table.totalCount());
    }

    // ------------------------------------------------------------------
    // Downloads: aggregate count ONLY
    // ------------------------------------------------------------------

    @Test
    void downloadCountAggregatesWithNoOtherRecord() {
        LibraryTable table = new LibraryTable();
        int id = publish(table, "Popular", ALICE, false, false).id();
        for (int i = 0; i < 5; i++) {
            assertTrue(table.incrementDownloads(id));
        }
        Entry entry = table.entry(id).orElseThrow();
        assertEquals(5, entry.downloads());
        assertFalse(table.incrementDownloads(9999));
        // The persisted row proves nothing but the number is stored: the NBT
        // for the entry carries no per-download identity and no timestamp.
        CompoundTag saved = table.save();
        String dump = saved.toString();
        assertFalse(dump.contains("timestamp"));
        assertFalse(dump.contains("downloader"));
    }

    // ------------------------------------------------------------------
    // Anonymity invariant
    // ------------------------------------------------------------------

    @Test
    void anonymousEntriesNeverExposeAnAuthor() {
        LibraryTable table = new LibraryTable();
        // Even a hostile caller passing a display name gets it scrubbed.
        int id = table.publish("Nameless", ALICE, "Alice The Composer", true, "deep_bass",
                new byte[] { 1 }, BIG_CAP, BIG_QUOTA, false).id();
        Entry entry = table.entry(id).orElseThrow();
        assertTrue(entry.anonymous());
        assertTrue(entry.authorDisplay().isEmpty());
        // The UUID is retained server-side (quota + unpublish + erasure)...
        assertEquals(ALICE, entry.author().orElseThrow());
        assertTrue(table.hasRow(ALICE));
        // ...and the author can still unpublish their anonymous work.
        assertEquals(UnpublishResult.REMOVED, table.unpublish(id, ALICE));
    }

    // ------------------------------------------------------------------
    // NBT round-trip
    // ------------------------------------------------------------------

    @Test
    void nbtRoundTripPreservesEveryFieldAndTheIdCounter() {
        LibraryTable table = new LibraryTable();
        int credited = table.publish("Credited", ALICE, "Alice", false, "glassy_pluck",
                new byte[] { 9, 8, 7 }, BIG_CAP, BIG_QUOTA, false).id();
        int anonymous = table.publish("Nameless", BOB, "", true, "percussion",
                new byte[] { 4, 5 }, BIG_CAP, BIG_QUOTA, false).id();
        int pending = table.publish("Awaiting", ALICE, "Alice", false, "sub_pad",
                new byte[] { 6 }, BIG_CAP, BIG_QUOTA, true).id();
        table.incrementDownloads(credited);
        table.incrementDownloads(credited);

        LibraryTable loaded = LibraryTable.load(table.save());
        assertEquals(3, loaded.totalCount());

        Entry creditedEntry = loaded.entry(credited).orElseThrow();
        assertEquals("Credited", creditedEntry.title());
        assertEquals(ALICE, creditedEntry.author().orElseThrow());
        assertEquals("Alice", creditedEntry.authorDisplay().orElseThrow());
        assertEquals("glassy_pluck", creditedEntry.familyId());
        assertArrayEquals(new byte[] { 9, 8, 7 }, creditedEntry.scoreBytes());
        assertEquals(2, creditedEntry.downloads());
        assertTrue(creditedEntry.visible());

        Entry anonymousEntry = loaded.entry(anonymous).orElseThrow();
        assertTrue(anonymousEntry.anonymous());
        assertTrue(anonymousEntry.authorDisplay().isEmpty());
        assertEquals(BOB, anonymousEntry.author().orElseThrow());

        Entry pendingEntry = loaded.entry(pending).orElseThrow();
        assertTrue(pendingEntry.pending());
        assertEquals(2, loaded.visibleCount()); // the pending row stays hidden across the round-trip

        // The id counter survives the round-trip: no id reuse after reload.
        int next = publish(loaded, "Fresh", BOB, false, false).id();
        assertTrue(next > pending);
        assertNotEquals(credited, next);
    }

    @Test
    void malformedRowsAreSkippedNotFatal() {
        LibraryTable table = new LibraryTable();
        publish(table, "Good", ALICE, false, false);
        CompoundTag saved = table.save();
        // Corrupt: append a rubbish row.
        CompoundTag bad = new CompoundTag();
        bad.putString("id", "not-an-int");
        saved.getList("entries").orElseThrow().add(bad);
        LibraryTable loaded = LibraryTable.load(saved);
        assertEquals(1, loaded.totalCount());
        assertEquals("Good", loaded.visibleEntries().get(0).title());
    }

    // ------------------------------------------------------------------
    // Erasure: sever the link, keep the work
    // ------------------------------------------------------------------

    @Test
    void anonymiseAuthorSeversTheLinkAndKeepsTheWork() {
        LibraryTable table = new LibraryTable();
        int aliceSong = publish(table, "Alice's Song", ALICE, false, false).id();
        int aliceSecret = publish(table, "Already Nameless", ALICE, true, false).id();
        int bobSong = publish(table, "Bob's Song", BOB, false, false).id();
        table.incrementDownloads(aliceSong);

        assertTrue(table.anonymiseAuthor(ALICE));
        assertFalse(table.anonymiseAuthor(ALICE)); // idempotent second pass

        // The person is gone...
        assertFalse(table.hasRow(ALICE));
        Entry severed = table.entry(aliceSong).orElseThrow();
        assertTrue(severed.author().isEmpty());
        assertTrue(severed.authorDisplay().isEmpty());
        assertTrue(severed.anonymous());
        // ...the work is not: still listed, still downloadable, count kept.
        assertEquals(3, table.visibleCount());
        assertEquals(1, severed.downloads());
        assertTrue(table.incrementDownloads(aliceSong));
        assertTrue(table.entry(aliceSecret).orElseThrow().author().isEmpty());
        // Nobody (not even the erased author) can unpublish a severed entry.
        assertEquals(UnpublishResult.NOT_AUTHOR, table.unpublish(aliceSong, ALICE));
        // The bystander is untouched.
        assertTrue(table.hasRow(BOB));
        assertTrue(table.entry(bobSong).orElseThrow().authorDisplay().isPresent());
        assertEquals(BOB, table.entry(bobSong).orElseThrow().author().orElseThrow());
        assertFalse(table.entry(bobSong).orElseThrow().anonymous());
    }
}
