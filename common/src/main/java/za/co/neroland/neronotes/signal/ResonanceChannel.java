package za.co.neroland.neronotes.signal;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A resonance channel: its {@link ChannelKey identity} plus the per-channel
 * <strong>trust list</strong> — the players the owner has granted control
 * (emit, transport, rename). Immutable; mutations return new instances and
 * are applied through {@link ChannelTable}.
 *
 * <p>Anyone in range may <em>listen</em>; the trust list only ever gates
 * <em>control</em> — see {@link ChannelAccess}.</p>
 *
 * @param key     channel identity {@code (dimension, ownerUUID, name)}
 * @param trusted UUIDs trusted to control this channel (never contains the
 *                owner — the owner's rights are implicit)
 */
public record ResonanceChannel(ChannelKey key, Set<UUID> trusted) {

    public ResonanceChannel {
        if (key == null) {
            throw new IllegalArgumentException("channel key must not be null");
        }
        trusted = Set.copyOf(trusted);
        if (trusted.contains(key.owner())) {
            throw new IllegalArgumentException("the owner is implicitly trusted and must not appear on the trust list");
        }
    }

    /** A brand-new channel with an empty trust list. */
    public static ResonanceChannel create(ChannelKey key) {
        return new ResonanceChannel(key, Set.of());
    }

    public UUID owner() {
        return key.owner();
    }

    public String name() {
        return key.name();
    }

    public String dimension() {
        return key.dimension();
    }

    /** Whether {@code player} is on the trust list (the owner is not listed — check ownership separately). */
    public boolean isTrusted(UUID player) {
        return trusted.contains(player);
    }

    /** This channel with {@code player} added to the trust list. */
    public ResonanceChannel withTrusted(UUID player) {
        Set<UUID> next = new HashSet<>(trusted);
        next.add(player);
        return new ResonanceChannel(key, next);
    }

    /** This channel with {@code player} removed from the trust list. */
    public ResonanceChannel withoutTrusted(UUID player) {
        Set<UUID> next = new HashSet<>(trusted);
        next.remove(player);
        return new ResonanceChannel(key, next);
    }

    /** This channel renamed, trust list preserved. */
    public ResonanceChannel renamed(String newName) {
        return new ResonanceChannel(key.withName(newName), trusted);
    }
}
