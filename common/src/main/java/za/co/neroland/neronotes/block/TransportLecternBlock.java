package za.co.neroland.neronotes.block;

import java.util.OptionalInt;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.neronotes.block.entity.NeroNotesBlockEntities;
import za.co.neroland.neronotes.block.entity.TransportLecternBlockEntity;
import za.co.neroland.neronotes.menu.MenuOpener;
import za.co.neroland.neronotes.menu.SequencerMenu;
import za.co.neroland.neronotes.network.SequencerServerHandlers;
import za.co.neroland.neronotes.soundforge.SoundforgeDimension;

/**
 * The transport lectern: the Soundforge's composing console. Using it opens
 * the sequencer screen (via {@link MenuOpener} — never a raw
 * {@code openMenu}) editing the player's <em>session score</em>; the block
 * entity drives preview playback.
 *
 * <p><strong>The place is preserved</strong> (locked decision 2): the lectern
 * only works inside the Soundforge — composition happens where you walked
 * through the Harmonic Gate to be. Outside it, the lectern is furniture.</p>
 */
public class TransportLecternBlock extends Block implements EntityBlock {

    public TransportLecternBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransportLecternBlockEntity(pos, state);
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
            serverPlayer.sendSystemMessage(Component.translatable("neronotes.sequencer.outside"));
            return InteractionResult.CONSUME;
        }
        OptionalInt containerId = MenuOpener.open(serverPlayer, new SimpleMenuProvider(
                (id, inventory, opener) -> new SequencerMenu(id, (ServerPlayer) opener, pos),
                Component.translatable("neronotes.sequencer.title")));
        if (containerId.isEmpty()) {
            return InteractionResult.CONSUME;
        }
        // Hand the freshly opened screen the authoritative session state.
        if (serverPlayer.containerMenu instanceof SequencerMenu menu) {
            SequencerServerHandlers.sendSession(serverPlayer, menu);
        }
        return InteractionResult.SUCCESS;
    }

    /** Server-side ticker driving preview playback; no client ticker exists. */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != NeroNotesBlockEntities.TRANSPORT_LECTERN.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> TransportLecternBlockEntity.serverTick(
                (net.minecraft.server.level.ServerLevel) tickLevel, pos, tickState,
                (TransportLecternBlockEntity) blockEntity);
    }
}
