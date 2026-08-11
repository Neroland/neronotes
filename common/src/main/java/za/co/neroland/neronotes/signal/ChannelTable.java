package za.co.neroland.neronotes.signal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import za.co.neroland.neronotes.NeroNotesCommon;

/**
 * The channel rows themselves: a mutable map of {@link ChannelKey} →
 * {@link ResonanceChannel}, with NBT (de)serialisation. This is the plain-JVM
 * heart of {@link ChannelStore} — all row logic (create, rename, trust,
 * per-player purge) lives here so it is directly unit-testable; the store
 * only adds {@code SavedData} dirty-tracking and Core's recovery guard.
 *
 * <p><strong>Designed for per-player purge from day one</strong> (POPIA /
 * GDPR): {@link #purgePlayer(UUID)} removes every channel the player owns and
 * strips the player from every other channel's trust list, leaving bystanders'
 * channels untouched. Core erasure registration follows in a later stage;
 * the capability exists now so no store ships without it.</p>
 *
 * <p>Not thread-safe on its own — mutate only on the server thread (as
 * {@link ResonanceService} does).</p>
 */
public final class ChannelTable {

    // NBT field names.
    private static final String KEY_CHANNELS = "channels";
    private static final String KEY_DIMENSION = "dim";
    private static final String KEY_OWNER = "owner";
    private static final String KEY_NAME = "name";
    private static final String KEY_TRUSTED = "trusted";

    /** Insertion-ordered for deterministic saves and listings. */
    private final Map<ChannelKey, ResonanceChannel> channels = new LinkedHashMap<>();

    /**
     * Create a channel with an empty trust list. Fails (returns false,
     * quietly) if the name is invalid or the exact identity already exists.
     */
    public boolean create(ChannelKey key) {
        if (key == null || !ChannelNames.isValid(key.name()) || channels.containsKey(key)) {
            return false;
        }
        channels.put(key, ResonanceChannel.create(key));
        return true;
    }

    public Optional<ResonanceChannel> get(ChannelKey key) {
        return Optional.ofNullable(channels.get(key));
    }

    public boolean contains(ChannelKey key) {
        return channels.containsKey(key);
    }

    /**
     * Rename a channel, preserving its trust list. Fails quietly if the
     * channel does not exist, the new name is invalid, or the owner already
     * has a channel of the new name in that dimension.
     */
    public boolean rename(ChannelKey key, String newName) {
        ResonanceChannel channel = channels.get(key);
        if (channel == null || !ChannelNames.isValid(newName)) {
            return false;
        }
        ChannelKey newKey = key.withName(newName);
        if (newKey.equals(key)) {
            return true; // no-op rename
        }
        if (channels.containsKey(newKey)) {
            return false;
        }
        channels.remove(key);
        channels.put(newKey, channel.renamed(newName));
        return true;
    }

    public boolean delete(ChannelKey key) {
        return channels.remove(key) != null;
    }

    /** Add a player to a channel's trust list. The owner cannot be "trusted" — ownership is implicit. */
    public boolean trust(ChannelKey key, UUID player) {
        ResonanceChannel channel = channels.get(key);
        if (channel == null || player == null || player.equals(channel.owner()) || channel.isTrusted(player)) {
            return false;
        }
        channels.put(key, channel.withTrusted(player));
        return true;
    }

    public boolean untrust(ChannelKey key, UUID player) {
        ResonanceChannel channel = channels.get(key);
        if (channel == null || player == null || !channel.isTrusted(player)) {
            return false;
        }
        channels.put(key, channel.withoutTrusted(player));
        return true;
    }

    /** All channels in a dimension (listing surface for later stages). */
    public List<ResonanceChannel> channelsIn(String dimension) {
        List<ResonanceChannel> result = new ArrayList<>();
        for (ResonanceChannel channel : channels.values()) {
            if (channel.dimension().equals(dimension)) {
                result.add(channel);
            }
        }
        return result;
    }

    /** All channels owned by {@code player}, across dimensions. */
    public List<ResonanceChannel> channelsOwnedBy(UUID player) {
        List<ResonanceChannel> result = new ArrayList<>();
        for (ResonanceChannel channel : channels.values()) {
            if (channel.owner().equals(player)) {
                result.add(channel);
            }
        }
        return result;
    }

    /** Whether {@code player} owns any channel — the erasure-conformance probe for this store. */
    public boolean hasOwnerRow(UUID player) {
        for (ResonanceChannel channel : channels.values()) {
            if (channel.owner().equals(player)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code player} appears anywhere — as an owner or on any trust list. */
    public boolean hasAnyDataFor(UUID player) {
        if (hasOwnerRow(player)) {
            return true;
        }
        for (ResonanceChannel channel : channels.values()) {
            if (channel.isTrusted(player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Per-player purge: remove every channel {@code player} owns and strip
     * {@code player} from every remaining trust list. Bystanders' channels
     * are untouched. Returns whether anything changed.
     */
    public boolean purgePlayer(UUID player) {
        if (player == null) {
            return false;
        }
        boolean changed = channels.entrySet().removeIf(e -> e.getValue().owner().equals(player));
        for (Map.Entry<ChannelKey, ResonanceChannel> entry : channels.entrySet()) {
            if (entry.getValue().isTrusted(player)) {
                entry.setValue(entry.getValue().withoutTrusted(player));
                changed = true;
            }
        }
        return changed;
    }

    public int size() {
        return channels.size();
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    /** Serialise every row. */
    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (ResonanceChannel channel : channels.values()) {
            CompoundTag row = new CompoundTag();
            row.putString(KEY_DIMENSION, channel.dimension());
            row.putString(KEY_OWNER, channel.owner().toString());
            row.putString(KEY_NAME, channel.name());
            ListTag trusted = new ListTag();
            for (UUID player : channel.trusted()) {
                trusted.add(StringTag.valueOf(player.toString()));
            }
            row.put(KEY_TRUSTED, trusted);
            list.add(row);
        }
        root.put(KEY_CHANNELS, list);
        return root;
    }

    /**
     * Deserialise. Malformed rows are skipped with a warning rather than
     * failing the whole store — a single bad row must never take the map (and
     * with it every base's channels) down. Skipped-row details never include
     * the player-chosen name.
     */
    public static ChannelTable load(CompoundTag root) {
        ChannelTable table = new ChannelTable();
        Optional<ListTag> list = root.getList(KEY_CHANNELS);
        if (list.isEmpty()) {
            return table;
        }
        for (int i = 0; i < list.get().size(); i++) {
            Optional<CompoundTag> row = list.get().getCompound(i);
            if (row.isEmpty()) {
                NeroNotesCommon.LOGGER.warn("[NeroNotes] skipping malformed channel row {} (not a compound)", i);
                continue;
            }
            try {
                table.loadRow(row.get());
            } catch (RuntimeException malformed) {
                NeroNotesCommon.LOGGER.warn("[NeroNotes] skipping malformed channel row {}: {}", i,
                        malformed.getClass().getSimpleName());
            }
        }
        return table;
    }

    private void loadRow(CompoundTag row) {
        String dimension = row.getString(KEY_DIMENSION).orElseThrow();
        UUID owner = UUID.fromString(row.getString(KEY_OWNER).orElseThrow());
        String name = row.getString(KEY_NAME).orElseThrow();
        ChannelKey key = new ChannelKey(dimension, owner, name);
        ResonanceChannel channel = ResonanceChannel.create(key);
        Optional<ListTag> trusted = row.getList(KEY_TRUSTED);
        if (trusted.isPresent()) {
            for (int i = 0; i < trusted.get().size(); i++) {
                Optional<String> id = trusted.get().getString(i);
                if (id.isPresent()) {
                    UUID player = UUID.fromString(id.get());
                    if (!player.equals(owner)) {
                        channel = channel.withTrusted(player);
                    }
                }
            }
        }
        channels.put(key, channel);
    }
}
