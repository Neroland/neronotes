package za.co.neroland.neronotes.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.neronotes.library.LibraryService;
import za.co.neroland.neronotes.soundforge.SoundforgeDimension;

/**
 * The publish lectern — the Soundforge's release desk, placed on the arrival
 * platform by {@code SoundforgeDimension.ensurePlatform}. Tap it while
 * holding a pressed disk and the composition is published to the server's
 * shared library (the disk stays with you; the library keeps a copy of the
 * score). Publishing is a committal, in-world decision, which is why it lives
 * here — inside the Soundforge, like every other composing surface, and it
 * refuses politely anywhere else (locked decision 2).
 *
 * <p>The whole flow — publishing toggle, author-only rule, publish-time name
 * validation, budget re-check, size cap, quota and the op-approval mode — is
 * server-side in {@code library/LibraryService}. No block entity needed.</p>
 */
public class PublishLecternBlock extends Block {

    public PublishLecternBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (!SoundforgeDimension.isSoundforge(level)) {
            serverPlayer.sendSystemMessage(Component.translatable("neronotes.library.publish.outside"));
            return InteractionResult.CONSUME;
        }
        LibraryService.publishHeldDisk(serverPlayer);
        return InteractionResult.CONSUME;
    }
}
