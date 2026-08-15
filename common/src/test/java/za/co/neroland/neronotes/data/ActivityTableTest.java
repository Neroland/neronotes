package za.co.neroland.neronotes.data;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The last-seen table behind the Stage 7 retention sweep. */
class ActivityTableTest {

    private static final long DAY_MS = 86_400_000L;

    @Test
    void touchRecordsAndRefreshesLastSeen() {
        ActivityTable table = new ActivityTable();
        UUID player = UUID.randomUUID();

        assertFalse(table.hasRow(player));
        assertTrue(table.touch(player, 1_000L));
        assertEquals(1_000L, table.lastSeen(player).orElseThrow());

        assertTrue(table.touch(player, 2_000L), "a later touch refreshes the timestamp");
        assertEquals(2_000L, table.lastSeen(player).orElseThrow());
        assertEquals(1, table.size());

        assertFalse(table.touch(null, 3_000L));
    }

    @Test
    void stalerThanFindsOnlyPlayersBeyondTheWindow() {
        ActivityTable table = new ActivityTable();
        UUID stale = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        long now = 400 * DAY_MS;

        table.touch(stale, now - 366 * DAY_MS);
        table.touch(fresh, now - 5 * DAY_MS);

        List<UUID> result = table.stalerThan(365, now);
        assertEquals(List.of(stale), result);

        // 0 (and any non-positive value) disables the sweep entirely.
        assertTrue(table.stalerThan(0, now).isEmpty());
        assertTrue(table.stalerThan(-1, now).isEmpty());
    }

    @Test
    void purgeRemovesOnlyTheOnePlayer() {
        ActivityTable table = new ActivityTable();
        UUID subject = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        table.touch(subject, 1_000L);
        table.touch(bystander, 1_000L);

        assertTrue(table.purgePlayer(subject));
        assertFalse(table.hasRow(subject));
        assertTrue(table.hasRow(bystander), "bystander rows survive a purge");

        assertFalse(table.purgePlayer(subject), "a second purge is a quiet no-op");
        assertFalse(table.purgePlayer(null));
    }

    @Test
    void nbtRoundTripPreservesRows() {
        ActivityTable table = new ActivityTable();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        table.touch(a, 111L);
        table.touch(b, 222L);

        ActivityTable reloaded = ActivityTable.load(table.save());
        assertEquals(2, reloaded.size());
        assertEquals(111L, reloaded.lastSeen(a).orElseThrow());
        assertEquals(222L, reloaded.lastSeen(b).orElseThrow());
    }

    @Test
    void malformedRowsAreSkippedNotFatal() {
        ActivityTable table = new ActivityTable();
        UUID good = UUID.randomUUID();
        table.touch(good, 42L);

        CompoundTag saved = table.save();
        CompoundTag bad = new CompoundTag();
        bad.putString("player", "not-a-uuid");
        bad.putLong("last_seen", 1L);
        saved.getList("players").orElseThrow().add(bad);

        ActivityTable reloaded = ActivityTable.load(saved);
        assertEquals(1, reloaded.size(), "the malformed row is dropped, the good one kept");
        assertTrue(reloaded.hasRow(good));
    }
}
