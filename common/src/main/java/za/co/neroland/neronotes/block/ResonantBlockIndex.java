package za.co.neroland.neronotes.block;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.neronotes.block.entity.ResonantBlockEntity;
import za.co.neroland.neronotes.voice.VoiceDefinition;
import za.co.neroland.neronotes.voice.VoiceRegistry;

/**
 * A server-side index of the currently <em>loaded</em> Resonant Block
 * entities, per dimension. This is how "pitch set by an incoming resonance
 * event" works without world scanning: when a channel note is emitted,
 * {@code ResonanceService} asks the index for nearby Resonant Blocks of the
 * note's voice family, and those flare and adopt the pitch.
 *
 * <p>Entries register in {@code ResonantBlockEntity.setLevel} and remove in
 * {@code setRemoved}, so the index tracks chunk load state automatically.
 * A bounded radius ({@value #RESPONSE_RADIUS_BLOCKS} blocks) keeps the
 * per-note cost proportional to the local build, never the world. Purely a
 * visual/tuning reaction — nothing here is an authorisation or an emitter,
 * so proximity is fine.</p>
 *
 * <p>All mutation and lookup happens on the server thread; the concurrent
 * map is belt-and-braces for cross-dimension tick ordering, not a threading
 * contract.</p>
 */
public final class ResonantBlockIndex {

    /** How far (blocks, straight-line) a Resonant Block responds to a channel note. */
    public static final int RESPONSE_RADIUS_BLOCKS = 16;

    private static final Map<String, Map<BlockPos, ResonantBlockEntity>> BY_DIMENSION =
            new ConcurrentHashMap<>();

    private ResonantBlockIndex() {
    }

    /** Register a loaded Resonant Block entity. Idempotent per position. */
    public static void register(String dimensionId, BlockPos pos, ResonantBlockEntity entity) {
        BY_DIMENSION.computeIfAbsent(dimensionId, ignored -> new ConcurrentHashMap<>())
                .put(pos.immutable(), entity);
    }

    /** Remove an unloaded/removed Resonant Block entity. */
    public static void unregister(String dimensionId, BlockPos pos) {
        Map<BlockPos, ResonantBlockEntity> inDimension = BY_DIMENSION.get(dimensionId);
        if (inDimension != null) {
            inDimension.remove(pos);
            if (inDimension.isEmpty()) {
                BY_DIMENSION.remove(dimensionId, inDimension);
            }
        }
    }

    /**
     * A channel note was emitted at {@code origin}: nearby loaded Resonant
     * Blocks whose family matches the note's voice adopt the pitch and flare.
     * Called by {@code ResonanceService} on the server thread.
     */
    public static void onChannelNote(ServerLevel level, Vec3 origin, String voiceId, int pitch) {
        Map<BlockPos, ResonantBlockEntity> inDimension =
                BY_DIMENSION.get(level.dimension().identifier().toString());
        if (inDimension == null || inDimension.isEmpty()) {
            return;
        }
        VoiceDefinition voice = VoiceRegistry.shared().resolve(voiceId);
        double radiusSq = (double) RESPONSE_RADIUS_BLOCKS * RESPONSE_RADIUS_BLOCKS;
        for (Map.Entry<BlockPos, ResonantBlockEntity> entry : List.copyOf(inDimension.entrySet())) {
            ResonantBlockEntity block = entry.getValue();
            if (block.isRemoved()) {
                inDimension.remove(entry.getKey());
                continue;
            }
            if (block.family() != voice.family()) {
                continue;
            }
            if (entry.getKey().getCenter().distanceToSqr(origin) > radiusSq) {
                continue;
            }
            block.receiveResonance(pitch);
        }
    }

    /** Loaded entries in a dimension — test/debug observability. */
    public static int loadedCount(String dimensionId) {
        Map<BlockPos, ResonantBlockEntity> inDimension = BY_DIMENSION.get(dimensionId);
        return inDimension == null ? 0 : inDimension.size();
    }

    /** Forget everything (server stop / tests). */
    public static void clear() {
        BY_DIMENSION.clear();
    }
}
