package za.co.neroland.neronotes.data;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.data.ErasureConformance;
import za.co.neroland.nerolandcore.data.PlayerDataEraser;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.neronotes.library.LibraryTable;
import za.co.neroland.neronotes.signal.ChannelKey;
import za.co.neroland.neronotes.signal.ChannelTable;
import za.co.neroland.neronotes.soundforge.ReturnAnchor;
import za.co.neroland.neronotes.soundforge.SoundforgeSessionTable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Core's POPIA/GDPR {@link ErasureConformance} harness run against
 * NeroNotes' stores (the plain-JVM tables the {@code SavedData} stores
 * delegate every row decision to — the store wrappers only add
 * dirty-tracking and Core's recovery guard, which need a live server).
 *
 * <p>The probes mirror the production eraser in
 * {@code data/NeroNotesData.eraseFor}: library authorship (anonymised, not
 * deleted — "sever the link, keep the work"; {@code hasRow} is author-keyed
 * so it reads false once severed), Soundforge session rows, channel
 * ownership + trust entries, and the retention activity record. There is no
 * separate disk-authorship store: disk authorship lives in item components
 * (world data, unreachable in offline inventories) and in the library, which
 * is the authoritative anonymisation point — see PRIVACY.md.</p>
 *
 * <p>Note: the harness deliberately logs one "Data eraser ... failed"
 * warning and one "erasure INCOMPLETE" line per run — that is its
 * failure-isolation canary working, not a defect.</p>
 */
class NeroNotesErasureConformanceTest {

    private PlayerDataEraser eraser;

    @AfterEach
    void unregisterEraser() {
        if (eraser != null) {
            PlayerDataErasure.unregister(eraser);
            eraser = null;
        }
    }

    @Test
    void erasurePurgesEverythingNeroNotesStores() {
        UUID author = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();

        // --- Seed every store for BOTH players, so no probe is vacuous and
        // --- bystander survival is actually provable.
        LibraryTable library = new LibraryTable();
        assertTrue(library.publish("Neon Skyline", author, "Composer", false,
                "deep_bass", new byte[] {1, 2, 3}, 100, 25, false).ok());
        assertTrue(library.publish("Quiet Orbit", author, "", true,
                "sub_pad", new byte[] {4, 5}, 100, 25, false).ok(), "anonymous entries still key on the author");
        assertTrue(library.publish("Bystander Tune", bystander, "Neighbour", false,
                "percussion", new byte[] {6}, 100, 25, false).ok());

        SoundforgeSessionTable sessions = new SoundforgeSessionTable();
        ReturnAnchor anchor = new ReturnAnchor("minecraft:overworld", 1.0, 64.0, 1.0, 0.0F, 0.0F);
        assertTrue(sessions.beginSession(author, anchor, 100L));
        assertTrue(sessions.beginSession(bystander, anchor, 100L));

        ChannelTable channels = new ChannelTable();
        ChannelKey authorChannel = new ChannelKey("minecraft:overworld", author, "base");
        ChannelKey bystanderChannel = new ChannelKey("minecraft:overworld", bystander, "base");
        assertTrue(channels.create(authorChannel));
        assertTrue(channels.create(bystanderChannel));
        assertTrue(channels.trust(bystanderChannel, author), "the subject appears on a bystander's trust list");

        ActivityTable activity = new ActivityTable();
        assertTrue(activity.touch(author, 1_000L));
        assertTrue(activity.touch(bystander, 1_000L));

        // --- The eraser under test: the same operations NeroNotesData.eraseFor
        // --- performs against the live stores.
        eraser = (server, uuid) -> {
            library.anonymiseAuthor(uuid);
            sessions.purgePlayer(uuid);
            channels.purgePlayer(uuid);
            activity.purgePlayer(uuid);
        };
        PlayerDataErasure.register(eraser);

        ErasureConformance.Report report = ErasureConformance.create()
                .probe("neronotes:library", library::hasRow)
                .probe("neronotes:soundforge_sessions", sessions::hasRow)
                .probe("neronotes:channels", channels::hasAnyDataFor)
                .probe("neronotes:activity", activity::hasRow)
                .run(null, author); // null server: these erasers never touch one

        report.assertPassed();

        // The report carries no player identity — a UUID is personal data.
        assertNotNull(report.describe());
        assertFalse(report.describe().contains(author.toString()),
                "the conformance report must never contain the subject's UUID");
        assertFalse(report.describe().contains(bystander.toString()));

        // Bystander rows survive untouched.
        assertTrue(library.hasRow(bystander));
        assertTrue(sessions.hasRow(bystander));
        assertTrue(channels.hasOwnerRow(bystander));
        assertTrue(activity.hasRow(bystander));

        // "Sever the link, keep the work": the author's entries still exist,
        // anonymous, authorless and playable — only the person is gone.
        assertEquals(3, library.totalCount(), "erasure keeps every published composition");
        library.allEntries().stream()
                .filter(entry -> !"Bystander Tune".equals(entry.title()))
                .forEach(entry -> {
                    assertTrue(entry.anonymous(), "severed entries are anonymous");
                    assertTrue(entry.author().isEmpty(), "severed entries carry no UUID");
                    assertTrue(entry.authorDisplay().isEmpty(), "severed entries display no name");
                });

        // Channel purge removed ownership AND the trust entry on the
        // bystander's channel, without touching the bystander's channel itself.
        assertFalse(channels.hasAnyDataFor(author));
        assertTrue(channels.contains(bystanderChannel));
    }

    @Test
    void erasingAPlayerWithNoDataIsHarmless() {
        // The retention sweep and the conformance canary both erase players no
        // store has heard of; the eraser must tolerate that quietly.
        LibraryTable library = new LibraryTable();
        SoundforgeSessionTable sessions = new SoundforgeSessionTable();
        ChannelTable channels = new ChannelTable();
        ActivityTable activity = new ActivityTable();

        eraser = (server, uuid) -> {
            library.anonymiseAuthor(uuid);
            sessions.purgePlayer(uuid);
            channels.purgePlayer(uuid);
            activity.purgePlayer(uuid);
        };
        PlayerDataErasure.register(eraser);

        PlayerDataErasure.erase(null, UUID.randomUUID());

        assertEquals(0, library.totalCount());
        assertEquals(0, channels.size());
        assertEquals(0, activity.size());
    }
}
