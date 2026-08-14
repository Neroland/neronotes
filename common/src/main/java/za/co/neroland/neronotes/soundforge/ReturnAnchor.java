package za.co.neroland.neronotes.soundforge;

/**
 * Where a player came from when they stepped through the Harmonic Gate: the
 * dimension id string (as {@code ServerLevel.dimension().identifier()}
 * renders it) plus the exact position and look angles to restore on return.
 *
 * <p>Held per player in {@link SoundforgeSessionStore} so a player who logs
 * out inside the Soundforge is never stranded — the anchor survives restarts
 * and is only removed by the per-player purge (POPIA/GDPR erasure).</p>
 */
public record ReturnAnchor(String dimension, double x, double y, double z, float yRot, float xRot) {
}
