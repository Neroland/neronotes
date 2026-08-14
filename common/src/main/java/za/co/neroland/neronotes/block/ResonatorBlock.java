package za.co.neroland.neronotes.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.neronotes.block.entity.NeroNotesBlockEntities;
import za.co.neroland.neronotes.block.entity.ResonatorBlockEntity;
import za.co.neroland.neronotes.signal.ResonanceService.SignalResult;

/**
 * The Resonator block: NeroNotes' disk player, a matte-black unit with a neon
 * ring that lights while playing ({@code playing}) and pulses on each note
 * burst ({@code pulse}) — both are blockstates, so the visuals need no BER.
 *
 * <p><strong>Interaction</strong> (documented for the wiki):</p>
 * <ul>
 *   <li><strong>Placement</strong> — the placer becomes the Resonator's owner;
 *       it binds to the owner's {@code "base"} channel (created if absent).
 *       Ownership is recorded server-side at placement, never client-asserted.</li>
 *   <li><strong>Use</strong> (right-click) — toggle play/stop of the stored
 *       score. Authorised through the channel: owner, trust list or operator.
 *       Refusals answer quietly with a small status message.</li>
 * </ul>
 *
 * <p>Playback itself is server-side in {@link ResonatorBlockEntity} — the
 * server owns the timeline anchor (locked design decision 4). Disk items that
 * write the score arrive in Stage 5; until then {@code setScore} is the API
 * seam.</p>
 */
public class ResonatorBlock extends Block implements EntityBlock {

    /** Neon ring lit: the Resonator is playing. */
    public static final BooleanProperty PLAYING = BooleanProperty.create("playing");

    /** Neon ring pulse: a note burst within the last few ticks. */
    public static final BooleanProperty PULSE = BooleanProperty.create("pulse");

    public ResonatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(PLAYING, false)
                .setValue(PULSE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PLAYING, PULSE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResonatorBlockEntity(pos, state);
    }

    /** Record the placer as owner and bind the default channel — server-recorded, never client-asserted. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof ResonatorBlockEntity resonator) {
            resonator.initializeOwner(serverLevel, player.getUUID());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof ResonatorBlockEntity resonator)
                || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (resonator.score() == null) {
            serverPlayer.sendSystemMessage(Component.translatable("neronotes.resonator.no_disk"));
            return InteractionResult.SUCCESS;
        }
        SignalResult result = resonator.togglePlayback(serverPlayer);
        switch (result) {
            case DENIED -> serverPlayer.sendSystemMessage(Component.translatable("neronotes.resonator.denied"));
            case CHANNEL_CAP_REACHED ->
                    serverPlayer.sendSystemMessage(Component.translatable("neronotes.resonator.channel_busy"));
            default -> {
                // OK / quiet outcomes: the ring state change is the feedback.
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Server-side ticker driving the playback schedule; no client ticker exists. */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != NeroNotesBlockEntities.RESONATOR.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> ResonatorBlockEntity.serverTick(
                (ServerLevel) tickLevel, pos, tickState, (ResonatorBlockEntity) blockEntity);
    }
}
