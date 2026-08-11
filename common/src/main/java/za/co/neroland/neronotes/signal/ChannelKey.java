package za.co.neroland.neronotes.signal;

import java.util.UUID;

/**
 * The identity of a resonance channel: {@code (dimension, ownerUUID, name)} —
 * locked design decision 3. Channels are <strong>owner-scoped, never
 * global</strong>: two players may each own a channel called "base" in the
 * same dimension without colliding, and no flat namespace exists for one
 * player to hijack another's audio.
 *
 * <p>Deliberately a plain record with no Minecraft types, so authorisation
 * and persistence logic stays plain-JVM testable. The dimension is the
 * dimension id string (e.g. {@code minecraft:overworld}), produced server-side
 * from {@code ServerLevel.dimension().identifier()} — see
 * {@code ResonanceService#dimensionId}.</p>
 *
 * @param dimension dimension id string, never blank
 * @param owner     owning player's UUID, never null
 * @param name      player-chosen display name (validated by
 *                  {@link ChannelNames} at create/rename time)
 */
public record ChannelKey(String dimension, UUID owner, String name) {

    public ChannelKey {
        if (dimension == null || dimension.isBlank()) {
            throw new IllegalArgumentException("channel dimension must not be blank");
        }
        if (owner == null) {
            throw new IllegalArgumentException("channel owner must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("channel name must not be blank");
        }
    }

    /** The same identity under a different display name (rename support). */
    public ChannelKey withName(String newName) {
        return new ChannelKey(dimension, owner, newName);
    }
}
