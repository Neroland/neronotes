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

import za.co.neroland.neronotes.config.NeroNotesConfig;
import za.co.neroland.neronotes.menu.DiskExchangerMenu;
import za.co.neroland.neronotes.menu.MenuOpener;

/**
 * The Disk Exchanger — the overworld-side machine for the shared library:
 * browse the published catalogue (paginated from day one), copy a chosen
 * composition onto a blank disk, or duplicate a disk you already hold. It is
 * deliberately NOT Soundforge furniture: downloading music should be as easy
 * as crafting the machine, while <em>making</em> music stays a journey.
 *
 * <p>Opens its menu through {@link MenuOpener}; everything else — page
 * clamping, entry visibility, item movement, the aggregate-only download
 * count — is server-side in {@link DiskExchangerMenu}. Craftable in survival;
 * usable in any dimension. The {@code exchanger.enabled} config switches the
 * machine off entirely. No block entity needed (the menu holds the three
 * working slots).</p>
 */
public class DiskExchangerBlock extends Block {

    public DiskExchangerBlock(Properties properties) {
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
        if (!NeroNotesConfig.EXCHANGER_ENABLED.get()) {
            serverPlayer.sendSystemMessage(Component.translatable("neronotes.exchanger.disabled"));
            return InteractionResult.CONSUME;
        }
        return MenuOpener.openOrConsume(serverPlayer, new SimpleMenuProvider(
                (id, inventory, opener) -> new DiskExchangerMenu(id, inventory, (ServerPlayer) opener, pos),
                Component.translatable("neronotes.exchanger.title")));
    }
}
