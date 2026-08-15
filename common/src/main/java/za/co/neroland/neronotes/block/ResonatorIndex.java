package za.co.neroland.neronotes.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;

import za.co.neroland.neronotes.block.entity.ResonatorBlockEntity;
import za.co.neroland.neronotes.signal.ChannelKey;

/**
 * A server-side index of the currently <em>loaded</em> Resonator block
 * entities, per dimension — the same shape as {@link ResonantBlockIndex}.
 * This is how the Stage 9 link module's {@code play}/{@code stop} actions
 * find the Resonators bound to a channel without a world scan: entries
 * register in {@code ResonatorBlockEntity.setLevel} and remove in
 * {@code setRemoved}, so the index tracks chunk load state automatically.
 *
 * <p><strong>Never an authorisation surface.</strong> The index answers
 * "which loaded Resonators are bound to this exact {@link ChannelKey}" —
 * callers must already have authorised the requester against that channel
 * (owner / trust list) before acting on the result, and the Resonator's own
 * transport paths re-authorise again server-side.</p>
 *
 * <p>All mutation and lookup happens on the server thread; the concurrent
 * map is belt-and-braces for cross-dimension tick ordering, not a threading
 * contract.</p>
 */
public final class ResonatorIndex {

    private static final Map<String, Map<BlockPos, ResonatorBlockEntity>> BY_DIMENSION =
            new ConcurrentHashMap<>();

    private ResonatorIndex() {
    }

    /** Register a loaded Resonator block entity. Idempotent per position. */
    public static void register(String dimensionId, BlockPos pos, ResonatorBlockEntity entity) {
        BY_DIMENSION.computeIfAbsent(dimensionId, ignored -> new ConcurrentHashMap<>())
                .put(pos.immutable(), entity);
    }

    /** Remove an unloaded/removed Resonator block entity. */
    public static void unregister(String dimensionId, BlockPos pos) {
        Map<BlockPos, ResonatorBlockEntity> inDimension = BY_DIMENSION.get(dimensionId);
        if (inDimension != null) {
            inDimension.remove(pos);
            if (inDimension.isEmpty()) {
                BY_DIMENSION.remove(dimensionId, inDimension);
            }
        }
    }

    /**
     * The loaded Resonators bound to exactly {@code key} (owner + name +
     * dimension), pruning any entry that turns out to be removed. Bounded by
     * the loaded Resonators of one dimension — never a world scan, and never
     * a chunk load.
     */
    public static List<ResonatorBlockEntity> boundTo(String dimensionId, ChannelKey key) {
        Map<BlockPos, ResonatorBlockEntity> inDimension = BY_DIMENSION.get(dimensionId);
        if (inDimension == null || inDimension.isEmpty()) {
            return List.of();
        }
        List<ResonatorBlockEntity> matches = new ArrayList<>();
        for (Map.Entry<BlockPos, ResonatorBlockEntity> entry : List.copyOf(inDimension.entrySet())) {
            ResonatorBlockEntity resonator = entry.getValue();
            if (resonator.isRemoved()) {
                inDimension.remove(entry.getKey());
                continue;
            }
            if (key.equals(resonator.channelKey())) {
                matches.add(resonator);
            }
        }
        return matches;
    }

    /** Loaded entries in a dimension — test/debug observability. */
    public static int loadedCount(String dimensionId) {
        Map<BlockPos, ResonatorBlockEntity> inDimension = BY_DIMENSION.get(dimensionId);
        return inDimension == null ? 0 : inDimension.size();
    }

    /** Forget everything (server stop / tests). */
    public static void clear() {
        BY_DIMENSION.clear();
    }
}
