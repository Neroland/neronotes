package za.co.neroland.neronotes.block;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tripwire for the 2026-08-15 shutdown-freeze fix: {@code setRemoved()} fires
 * on <strong>chunk unload</strong> as well as on block destruction (26.x
 * {@code LevelChunk.clearAllBlockEntities}), including every unload inside
 * {@code MinecraftServer.stopServer}'s has-work drain loop — so it must stay
 * world-inert. A {@code getBlockState}/{@code setBlock} from there
 * synchronously re-loads the very chunk being unloaded and, with the
 * Resonator's persisted {@code playing} flag re-arming each reloaded copy, the
 * drain loop never empties: "save and quit" hangs forever at "Saving worlds".
 *
 * <p>Destruction-only side effects (the STOP transport that frees the play
 * slot) therefore live in {@code preRemoveSideEffects(BlockPos, BlockState)},
 * which vanilla calls from {@code LevelChunk.setBlockState} on REAL removal
 * only. This test pins that seam in place on the two playing block entities:
 * if the override disappears, playback would either leak its play slot on
 * every break (override deleted) or someone has moved the stop back into
 * {@code setRemoved} — both regressions.</p>
 *
 * <p>Classes are inspected without initialisation — reflection only, no
 * Minecraft bootstrap, plain-JVM like the rest of the suite.</p>
 */
class RemovalSideEffectsTest {

    private static final String BE_PACKAGE = "za.co.neroland.neronotes.block.entity.";
    private static final String BLOCK_POS = "net.minecraft.core.BlockPos";
    private static final String BLOCK_STATE = "net.minecraft.world.level.block.state.BlockState";

    @Test
    void resonatorDeclaresDestructionSeam() throws Exception {
        assertDeclaresPreRemoveSideEffects(BE_PACKAGE + "ResonatorBlockEntity");
    }

    @Test
    void transportLecternDeclaresDestructionSeam() throws Exception {
        assertDeclaresPreRemoveSideEffects(BE_PACKAGE + "TransportLecternBlockEntity");
    }

    private static void assertDeclaresPreRemoveSideEffects(String className) throws Exception {
        ClassLoader loader = RemovalSideEffectsTest.class.getClassLoader();
        Class<?> blockEntity = Class.forName(className, false, loader);
        Class<?> pos = Class.forName(BLOCK_POS, false, loader);
        Class<?> state = Class.forName(BLOCK_STATE, false, loader);
        Method seam = blockEntity.getDeclaredMethod("preRemoveSideEffects", pos, state);
        assertNotNull(seam, className + " must keep destruction side effects in preRemoveSideEffects "
                + "(setRemoved also fires on chunk unload and must stay world-inert)");
    }
}
