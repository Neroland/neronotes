package za.co.neroland.neronotes.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import za.co.neroland.neronotes.NeroNotesCommon;

/**
 * Last-seen timestamps per player — the plain-JVM heart of
 * {@link ActivityStore}, so the retention sweep's "who is inactive" decision
 * is directly unit-testable.
 *
 * <p>Privacy (POPIA / GDPR): a row is exactly a UUID and a login
 * epoch-millis — no names, no IPs, no chat, no coordinates. The table exists
 * precisely to <em>support</em> data minimisation (it drives the purge of
 * stale NeroNotes records), and is itself purged for a player on erasure.</p>
 *
 * <p>Not thread-safe on its own — mutate only on the server thread.</p>
 */
public final class ActivityTable {

    // NBT field names.
    private static final String KEY_PLAYERS = "players";
    private static final String KEY_PLAYER = "player";
    private static final String KEY_LAST_SEEN = "last_seen";

    private static final long MILLIS_PER_DAY = 86_400_000L;

    /** Insertion-ordered for deterministic saves. */
    private final Map<UUID, Long> lastSeen = new LinkedHashMap<>();

    /** Record that {@code player} was just seen at {@code nowMillis}. */
    public boolean touch(UUID player, long nowMillis) {
        if (player == null) {
            return false;
        }
        lastSeen.put(player, nowMillis);
        return true;
    }

    /** When {@code player} was last seen, if a row exists. */
    public Optional<Long> lastSeen(UUID player) {
        return Optional.ofNullable(lastSeen.get(player));
    }

    /** Erasure-conformance probe: does {@code player} still have an activity row? */
    public boolean hasRow(UUID player) {
        return lastSeen.containsKey(player);
    }

    /**
     * UUIDs whose last-seen is older than {@code days} before {@code nowMillis}.
     * Empty when {@code days <= 0} — 0 disables the sweep entirely.
     */
    public List<UUID> stalerThan(int days, long nowMillis) {
        if (days <= 0) {
            return List.of();
        }
        long threshold = nowMillis - days * MILLIS_PER_DAY;
        List<UUID> stale = new ArrayList<>();
        lastSeen.forEach((player, seen) -> {
            if (seen < threshold) {
                stale.add(player);
            }
        });
        return stale;
    }

    /** Per-player purge (the erasure path). Returns whether anything changed. */
    public boolean purgePlayer(UUID player) {
        return player != null && lastSeen.remove(player) != null;
    }

    public int size() {
        return lastSeen.size();
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    /** Serialise every row. */
    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        lastSeen.forEach((player, seen) -> {
            CompoundTag row = new CompoundTag();
            row.putString(KEY_PLAYER, player.toString());
            row.putLong(KEY_LAST_SEEN, seen);
            list.add(row);
        });
        root.put(KEY_PLAYERS, list);
        return root;
    }

    /**
     * Deserialise. Malformed rows are skipped with a warning rather than
     * failing the whole store — losing a timestamp only delays that player's
     * retention purge until their next join re-creates the row.
     */
    public static ActivityTable load(CompoundTag root) {
        ActivityTable table = new ActivityTable();
        Optional<ListTag> list = root.getList(KEY_PLAYERS);
        if (list.isEmpty()) {
            return table;
        }
        for (int i = 0; i < list.get().size(); i++) {
            Optional<CompoundTag> row = list.get().getCompound(i);
            if (row.isEmpty()) {
                NeroNotesCommon.LOGGER.warn("[NeroNotes] skipping malformed activity row {} (not a compound)", i);
                continue;
            }
            try {
                UUID player = UUID.fromString(row.get().getString(KEY_PLAYER).orElseThrow());
                long seen = row.get().getLong(KEY_LAST_SEEN).orElseThrow();
                table.lastSeen.put(player, seen);
            } catch (RuntimeException malformed) {
                NeroNotesCommon.LOGGER.warn("[NeroNotes] skipping malformed activity row {}: {}", i,
                        malformed.getClass().getSimpleName());
            }
        }
        return table;
    }
}
