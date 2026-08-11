package za.co.neroland.neronotes.signal;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.neronotes.signal.ChannelConcurrencyGuard.Anchor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 2 gate: the audio-spam cap on concurrently playing channels per
 * chunk radius holds, and rejections free no state. Pure logic — anchors are
 * dimension id + chunk coordinates, no Minecraft types.
 */
class ChannelConcurrencyGuardTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final int RADIUS = 4; // chunks — matches a 64-block emit range

    private static ChannelKey channel(String name) {
        return new ChannelKey(OVERWORLD, UUID.fromString("00000000-0000-0000-0000-000000000001"), name);
    }

    private static Anchor at(String dimension, int chunkX, int chunkZ) {
        return new Anchor(dimension, chunkX, chunkZ);
    }

    @Test
    void capHoldsWithinTheChunkRadius() {
        ChannelConcurrencyGuard guard = new ChannelConcurrencyGuard(() -> 3);
        assertTrue(guard.tryStartPlaying(channel("a"), at(OVERWORLD, 0, 0), RADIUS));
        assertTrue(guard.tryStartPlaying(channel("b"), at(OVERWORLD, 1, 1), RADIUS));
        assertTrue(guard.tryStartPlaying(channel("c"), at(OVERWORLD, 2, 0), RADIUS));
        // Fourth channel inside the radius: quiet refusal.
        assertFalse(guard.tryStartPlaying(channel("d"), at(OVERWORLD, 0, 1), RADIUS));
        assertEquals(3, guard.playingCount());
        assertFalse(guard.isPlaying(channel("d")), "a refused channel is not left half-registered");
    }

    @Test
    void farAwayChannelsDoNotCountAgainstTheCap() {
        ChannelConcurrencyGuard guard = new ChannelConcurrencyGuard(() -> 1);
        assertTrue(guard.tryStartPlaying(channel("a"), at(OVERWORLD, 0, 0), RADIUS));
        assertFalse(guard.tryStartPlaying(channel("b"), at(OVERWORLD, RADIUS, 0), RADIUS), "within radius");
        assertTrue(guard.tryStartPlaying(channel("c"), at(OVERWORLD, RADIUS + 1, 0), RADIUS), "just outside");
    }

    @Test
    void otherDimensionsDoNotCountAgainstTheCap() {
        ChannelConcurrencyGuard guard = new ChannelConcurrencyGuard(() -> 1);
        assertTrue(guard.tryStartPlaying(channel("a"), at(OVERWORLD, 0, 0), RADIUS));
        assertTrue(guard.tryStartPlaying(channel("b"), at(NETHER, 0, 0), RADIUS));
    }

    @Test
    void stoppingFreesASlot() {
        ChannelConcurrencyGuard guard = new ChannelConcurrencyGuard(() -> 1);
        assertTrue(guard.tryStartPlaying(channel("a"), at(OVERWORLD, 0, 0), RADIUS));
        assertFalse(guard.tryStartPlaying(channel("b"), at(OVERWORLD, 0, 0), RADIUS));
        guard.stopPlaying(channel("a"));
        assertTrue(guard.tryStartPlaying(channel("b"), at(OVERWORLD, 0, 0), RADIUS));
    }

    @Test
    void aPlayingChannelMayAlwaysReanchor() {
        ChannelConcurrencyGuard guard = new ChannelConcurrencyGuard(() -> 1);
        assertTrue(guard.tryStartPlaying(channel("a"), at(OVERWORLD, 0, 0), RADIUS));
        // Restart/seek of the SAME channel is not a new slot.
        assertTrue(guard.tryStartPlaying(channel("a"), at(OVERWORLD, 2, 2), RADIUS));
        assertEquals(1, guard.playingCount());
    }

    @Test
    void capIsReadAtDecisionTime() {
        int[] cap = {1};
        ChannelConcurrencyGuard guard = new ChannelConcurrencyGuard(() -> cap[0]);
        assertTrue(guard.tryStartPlaying(channel("a"), at(OVERWORLD, 0, 0), RADIUS));
        assertFalse(guard.tryStartPlaying(channel("b"), at(OVERWORLD, 0, 0), RADIUS));
        cap[0] = 2; // config raised — applies without rebuilding the guard
        assertTrue(guard.tryStartPlaying(channel("b"), at(OVERWORLD, 0, 0), RADIUS));
    }
}
