package za.co.neroland.neronotes.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.neronotes.block.entity.ResonantBlockEntity;
import za.co.neroland.neronotes.voice.VoiceFamily;

/**
 * A Resonant Block: a matte-black block tuned to one {@link VoiceFamily},
 * with a neon edge-light flare on each note (the {@code lit} blockstate,
 * cleared by a scheduled tick a few ticks later).
 *
 * <p><strong>Interaction</strong> (documented for the wiki):</p>
 * <ul>
 *   <li><strong>Use</strong> (right-click) — play the block's current note:
 *       a live local resonance heard by everyone in range, plus the flare.
 *       No channel, no authorisation — exactly as privileged as tapping a
 *       vanilla note block.</li>
 *   <li><strong>Sneak-use</strong> — cycle the pitch one step up within the
 *       family voice's pitch band (wrapping), and play it as a preview.</li>
 *   <li><strong>Incoming resonance</strong> — when a channel note of this
 *       block's family plays nearby, the block adopts that pitch and flares
 *       (see {@link ResonantBlockIndex}).</li>
 * </ul>
 *
 * <p>Extends {@link Block} + {@link EntityBlock} rather than
 * {@code BaseEntityBlock} on purpose: no abstract block codec to satisfy,
 * and the default {@code MODEL} render shape is exactly what we want.
 * Tooltips live on the <em>BlockItem</em> ({@code item/NotesBlockItem}) —
 * {@code Block} has no hover text in 26.x.</p>
 */
public class ResonantBlock extends Block implements EntityBlock {

    /** Neon edge-light flare: on for a few ticks around each note. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** How long the flare stays lit, in game ticks. */
    public static final int FLARE_TICKS = 4;

    private final VoiceFamily family;

    public ResonantBlock(VoiceFamily family, Properties properties) {
        super(properties);
        this.family = family;
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    public VoiceFamily family() {
        return family;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResonantBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof ResonantBlockEntity resonant)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            resonant.cyclePitch();
        }
        resonant.playInteractionNote(player.getUUID());
        return InteractionResult.SUCCESS;
    }

    /** The scheduled tick that ends a flare. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, false), UPDATE_ALL);
        }
    }

    /** Light the neon edge flare and schedule its end. Server side only. */
    public static void flare(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ResonantBlock) {
            if (!state.getValue(LIT)) {
                level.setBlock(pos, state.setValue(LIT, true), UPDATE_ALL);
            }
            level.scheduleTick(pos, state.getBlock(), FLARE_TICKS);
        }
    }
}
