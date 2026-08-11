package za.co.neroland.neronotes.signal;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * The audio-spam guard: a server-side cap on <em>concurrently playing</em>
 * channels per chunk radius (config
 * {@code signal.max_playing_channels_per_chunk_radius}). Enforced when a
 * {@code play} transport event is requested; rejections are quiet — the
 * caller simply reports refusal, nothing spams chat or the log.
 *
 * <p>Plain-JVM and deliberately free of Minecraft types (anchors are
 * dimension id + chunk coordinates) so the cap logic is directly
 * unit-testable. The cap is read through an {@link IntSupplier} at decision
 * time, so config changes apply without rebuilding the guard.</p>
 *
 * <p>Runtime state only — playing channels are not persisted; a server
 * restart silences everything, which is the safe default.</p>
 */
public final class ChannelConcurrencyGuard {

    /** Where a channel is currently playing from. */
    public record Anchor(String dimension, int chunkX, int chunkZ) {
    }

    private final IntSupplier cap;
    private final Map<ChannelKey, Anchor> playing = new HashMap<>();

    public ChannelConcurrencyGuard(IntSupplier cap) {
        this.cap = cap;
    }

    /**
     * Request that {@code channel} start playing at {@code anchor}. A channel
     * that is already playing may always re-anchor (a restart or seek is not
     * a new slot). Otherwise the request is refused when {@code cap} channels
     * are already playing within {@code chunkRadius} chunks (chessboard
     * distance) of the anchor in the same dimension.
     *
     * @return true if the channel is now playing; false = quiet refusal
     */
    public synchronized boolean tryStartPlaying(ChannelKey channel, Anchor anchor, int chunkRadius) {
        if (channel == null || anchor == null) {
            return false;
        }
        if (playing.containsKey(channel)) {
            playing.put(channel, anchor);
            return true;
        }
        int limit = Math.max(1, cap.getAsInt());
        int nearby = 0;
        for (Anchor other : playing.values()) {
            if (other.dimension().equals(anchor.dimension())
                    && chessboardDistance(other, anchor) <= chunkRadius) {
                nearby++;
                if (nearby >= limit) {
                    return false;
                }
            }
        }
        playing.put(channel, anchor);
        return true;
    }

    public synchronized void stopPlaying(ChannelKey channel) {
        playing.remove(channel);
    }

    public synchronized boolean isPlaying(ChannelKey channel) {
        return playing.containsKey(channel);
    }

    public synchronized int playingCount() {
        return playing.size();
    }

    /** Forget everything (server stop / tests). */
    public synchronized void clear() {
        playing.clear();
    }

    private static int chessboardDistance(Anchor a, Anchor b) {
        return Math.max(Math.abs(a.chunkX() - b.chunkX()), Math.abs(a.chunkZ() - b.chunkZ()));
    }
}
