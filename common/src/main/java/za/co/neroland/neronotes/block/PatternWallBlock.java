package za.co.neroland.neronotes.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.neronotes.soundforge.SequencerSessions;
import za.co.neroland.neronotes.soundforge.SessionEditor;
import za.co.neroland.neronotes.soundforge.SoundforgeDimension;

/**
 * A pattern wall: the Soundforge's in-world <strong>layer display and
 * selection</strong> surface (locked decision 2 — the place matters, the note
 * grid lives at the lectern). Each wall shows a layer index as glyph pips
 * ({@code layer} blockstate, 0–{@code 3} matching
 * {@link SessionEditor#MAX_LAYERS}); tapping one selects that layer as your
 * session's active layer and flashes the wall ({@code lit}); sneak-tapping
 * retunes the wall itself to the next layer index.
 *
 * <p>Selection is server-side session state — the wall never edits the
 * score, and outside the Soundforge it is inert decoration.</p>
 */
public class PatternWallBlock extends Block {

    /** The layer index this wall displays/selects. */
    public static final IntegerProperty LAYER = IntegerProperty.create("layer", 0, SessionEditor.MAX_LAYERS - 1);

    /** Brief selection flash, cleared by a scheduled tick. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** Flash length in game ticks. */
    public static final int FLASH_TICKS = 12;

    public PatternWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYER, 0).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYER, LIT);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        if (!SoundforgeDimension.isSoundforge(level)) {
            serverPlayer.sendSystemMessage(Component.translatable("neronotes.sequencer.outside"));
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown()) {
            // Retune the wall itself to the next layer index.
            int next = (state.getValue(LAYER) + 1) % SessionEditor.MAX_LAYERS;
            serverLevel.setBlock(pos, state.setValue(LAYER, next), UPDATE_ALL);
            return InteractionResult.SUCCESS;
        }
        int layer = state.getValue(LAYER);
        if (SequencerSessions.setActiveLayer(serverLevel.getServer(), player.getUUID(), layer)) {
            serverLevel.setBlock(pos, state.setValue(LIT, true), UPDATE_ALL);
            serverLevel.scheduleTick(pos, this, FLASH_TICKS);
            serverPlayer.sendSystemMessage(
                    Component.translatable("neronotes.sequencer.layer_selected", layer + 1), true);
        } else {
            serverPlayer.sendSystemMessage(
                    Component.translatable("neronotes.sequencer.no_such_layer", layer + 1), true);
        }
        return InteractionResult.SUCCESS;
    }

    /** The scheduled tick that ends a selection flash. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, false), UPDATE_ALL);
        }
    }
}
