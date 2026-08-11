package za.co.neroland.neronotes.signal;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Channel rows: create/rename/trust semantics, per-player purge (owner rows
 * AND trust entries, bystanders untouched — the shape Core's erasure hook
 * consumes in a later stage), and the NBT round-trip behind
 * {@code neronotes:channels}.
 */
class ChannelTableTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FRIEND = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID BYSTANDER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private static ChannelKey key(UUID owner, String name) {
        return new ChannelKey(OVERWORLD, owner, name);
    }

    @Test
    void createRejectsDuplicatesAndInvalidNames() {
        ChannelTable table = new ChannelTable();
        assertTrue(table.create(key(OWNER, "atrium")));
        assertFalse(table.create(key(OWNER, "atrium")), "exact duplicate identity");
        assertTrue(table.create(key(FRIEND, "atrium")), "same name under another owner is a different channel");
        assertFalse(table.create(key(OWNER, "a".repeat(ChannelNames.MAX_LENGTH + 1))), "over-long name");
        assertFalse(table.create(key(OWNER, "bad§name")), "formatting code");
        assertFalse(table.create(key(OWNER, "bad\tname")), "control character");
    }

    @Test
    void renameMovesIdentityAndKeepsTrust() {
        ChannelTable table = new ChannelTable();
        ChannelKey original = key(OWNER, "atrium");
        assertTrue(table.create(original));
        assertTrue(table.trust(original, FRIEND));

        assertTrue(table.rename(original, "reactor hall"));
        assertFalse(table.contains(original));
        ChannelKey renamed = key(OWNER, "reactor hall");
        assertTrue(table.contains(renamed));
        assertTrue(table.get(renamed).orElseThrow().isTrusted(FRIEND), "trust list survives a rename");

        assertFalse(table.rename(renamed, "bad§name"), "invalid new name");
        assertTrue(table.create(key(OWNER, "annex")));
        assertFalse(table.rename(key(OWNER, "annex"), "reactor hall"), "rename onto an existing identity");
    }

    @Test
    void trustNeverListsTheOwner() {
        ChannelTable table = new ChannelTable();
        ChannelKey key = key(OWNER, "atrium");
        assertTrue(table.create(key));
        assertFalse(table.trust(key, OWNER), "the owner is implicitly trusted");
        assertTrue(table.trust(key, FRIEND));
        assertFalse(table.trust(key, FRIEND), "already trusted");
        assertTrue(table.untrust(key, FRIEND));
        assertFalse(table.untrust(key, FRIEND), "no longer trusted");
    }

    @Test
    void purgePlayerRemovesOwnerRowsAndTrustEntriesButSparesBystanders() {
        ChannelTable table = new ChannelTable();
        ChannelKey owned = key(OWNER, "atrium");
        ChannelKey bystanders = key(BYSTANDER, "garden");
        assertTrue(table.create(owned));
        assertTrue(table.create(bystanders));
        assertTrue(table.trust(bystanders, OWNER)); // the erased player appears on a bystander's trust list

        assertTrue(table.hasOwnerRow(OWNER));
        assertTrue(table.hasAnyDataFor(OWNER));

        assertTrue(table.purgePlayer(OWNER));

        assertFalse(table.hasOwnerRow(OWNER), "owned channels gone");
        assertFalse(table.hasAnyDataFor(OWNER), "trust entries stripped");
        assertTrue(table.contains(bystanders), "bystander's channel survives");
        assertTrue(table.hasOwnerRow(BYSTANDER));
        assertFalse(table.purgePlayer(OWNER), "second purge is a no-op");
    }

    @Test
    void nbtRoundTripIsExact() {
        ChannelTable table = new ChannelTable();
        ChannelKey first = key(OWNER, "atrium");
        ChannelKey second = new ChannelKey("neronotes:soundforge", BYSTANDER, "stage");
        assertTrue(table.create(first));
        assertTrue(table.create(second));
        assertTrue(table.trust(first, FRIEND));

        CompoundTag saved = table.save();
        ChannelTable loaded = ChannelTable.load(saved);

        assertEquals(2, loaded.size());
        assertTrue(loaded.get(first).orElseThrow().isTrusted(FRIEND));
        assertFalse(loaded.get(first).orElseThrow().isTrusted(BYSTANDER));
        assertTrue(loaded.contains(second));
        assertEquals(saved, loaded.save(), "save -> load -> save is stable");
    }

    @Test
    void loadSkipsMalformedRowsInsteadOfFailing() {
        ChannelTable table = new ChannelTable();
        assertTrue(table.create(key(OWNER, "atrium")));
        CompoundTag saved = table.save();
        // Corrupt one row: a garbage owner UUID must not take the whole store down.
        CompoundTag broken = saved.copy();
        broken.getList("channels").orElseThrow().getCompound(0).orElseThrow().putString("owner", "not-a-uuid");
        ChannelTable loaded = ChannelTable.load(broken);
        assertEquals(0, loaded.size(), "malformed row skipped");
    }
}
