package za.co.neroland.neronotes.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.neronotes.menu.DiskPressMenu;
import za.co.neroland.neronotes.menu.MenuOpener;
import za.co.neroland.neronotes.soundforge.SoundforgeDimension;

/**
 * The Disk Press: writes the player's Soundforge session score onto a blank
 * disk. Opens its menu through {@link MenuOpener}; the press action itself is
 * server-side ({@code DiskPressMenu#tryPress}) with the score budget enforced
 * via {@code ScoreCodec.toBytes(score, budget)} — an over-budget score is
 * refused with a message naming the limit, never truncated (locked
 * decision 5). Only functional inside the Soundforge; no block entity needed
 * (the menu holds the two working slots).
 */
public class DiskPressBlock extends Block {

    public DiskPressBlock(Properties properties) {
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
            serverPlayer.sendSystemMessage(Component.translatable("neronotes.press.outside"));
            return InteractionResult.CONSUME;
        }
        return MenuOpener.openOrConsume(serverPlayer, new SimpleMenuProvider(
                (id, inventory, opener) -> new DiskPressMenu(id, inventory, (ServerPlayer) opener, pos),
                Component.translatable("neronotes.press.title")));
    }
}
