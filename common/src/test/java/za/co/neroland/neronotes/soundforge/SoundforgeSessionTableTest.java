package za.co.neroland.neronotes.soundforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/**
 * Plain-JVM tests for the Soundforge session rows: the return-position
 * round-trip through NBT (the "never strand a player" guarantee), the
 * purge-readiness contract Stage 7's erasure registration relies on, and
 * corrupt-row tolerance.
 */
class SoundforgeSessionTableTest {

    private static final UUID COMPOSER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BYSTANDER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static ReturnAnchor anchor() {
        return new ReturnAnchor("minecraft:overworld", 1.5, 64.0, -7.25, 90.0f, 10.0f);
    }

    @Test
    void returnAnchorRoundTripsThroughNbt() {
        SoundforgeSessionTable table = new SoundforgeSessionTable();
        assertTrue(table.beginSession(COMPOSER, anchor(), 1234L));
        CompoundTag data = new CompoundTag();
        data.putString("stage5", "session score placeholder");
        assertTrue(table.putSessionData(COMPOSER, data));

        SoundforgeSessionTable reloaded = SoundforgeSessionTable.load(table.save());
        assertEquals(anchor(), reloaded.returnAnchor(COMPOSER).orElseThrow(),
                "the exact return position and look must survive a save/load");
        assertTrue(reloaded.isInside(COMPOSER));
        assertEquals(1234L, reloaded.enteredGameTime(COMPOSER).orElseThrow());
        assertEquals("session score placeholder",
                reloaded.sessionData(COMPOSER).orElseThrow().getString("stage5").orElseThrow());
    }

    @Test
    void markOutsideKeepsAnchorAndSessionData() {
        SoundforgeSessionTable table = new SoundforgeSessionTable();
        table.beginSession(COMPOSER, anchor(), 10L);
        table.putSessionData(COMPOSER, new CompoundTag());

        assertTrue(table.markOutside(COMPOSER));
        assertFalse(table.isInside(COMPOSER));
        assertTrue(table.returnAnchor(COMPOSER).isPresent(), "a completed return keeps the anchor");
        assertTrue(table.sessionData(COMPOSER).isPresent(), "the session score survives a trip home");
        assertFalse(table.markOutside(COMPOSER), "already outside is a no-op");
    }

    @Test
    void reEntryOverwritesAnchorButKeepsSessionData() {
        SoundforgeSessionTable table = new SoundforgeSessionTable();
        table.beginSession(COMPOSER, anchor(), 10L);
        CompoundTag data = new CompoundTag();
        data.putInt("layers", 3);
        table.putSessionData(COMPOSER, data);
        table.markOutside(COMPOSER);

        ReturnAnchor elsewhere = new ReturnAnchor("nerospace:greenxertz", -100.0, 70.0, 42.0, 0.0f, 0.0f);
        table.beginSession(COMPOSER, elsewhere, 20L);
        assertEquals(elsewhere, table.returnAnchor(COMPOSER).orElseThrow());
        assertEquals(3, table.sessionData(COMPOSER).orElseThrow().getInt("layers").orElseThrow(),
                "re-entering must not discard the composing session");
    }

    @Test
    void purgeRemovesOnlyThatPlayersRow() {
        SoundforgeSessionTable table = new SoundforgeSessionTable();
        table.beginSession(COMPOSER, anchor(), 1L);
        table.beginSession(BYSTANDER, anchor(), 2L);

        assertTrue(table.hasRow(COMPOSER));
        assertTrue(table.purgePlayer(COMPOSER));
        assertFalse(table.hasRow(COMPOSER), "the erasure probe must go negative after a purge");
        assertTrue(table.hasRow(BYSTANDER), "a bystander's row must survive someone else's erasure");
        assertFalse(table.purgePlayer(COMPOSER), "purging an absent row reports no change");
        assertEquals(1, table.size());
    }

    @Test
    void malformedRowsAreSkippedNotFatal() {
        SoundforgeSessionTable table = new SoundforgeSessionTable();
        table.beginSession(COMPOSER, anchor(), 5L);
        CompoundTag root = table.save();

        ListTag sessions = root.getList("sessions").orElseThrow();
        sessions.add(StringTag.valueOf("not a compound"));
        CompoundTag junk = new CompoundTag();
        junk.putString("player", "definitely-not-a-uuid");
        sessions.add(junk);

        SoundforgeSessionTable reloaded = SoundforgeSessionTable.load(root);
        assertEquals(1, reloaded.size(), "one bad row must never take every anchor down");
        assertTrue(reloaded.hasRow(COMPOSER));
    }

    @Test
    void sessionDataRequiresAnExistingRow() {
        SoundforgeSessionTable table = new SoundforgeSessionTable();
        assertFalse(table.putSessionData(COMPOSER, new CompoundTag()),
                "session data can only attach to a player who has actually entered");
        assertTrue(table.sessionData(COMPOSER).isEmpty());
    }
}
