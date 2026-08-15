package za.co.neroland.neronotes.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.neronotes.block.entity.HarmonicGateBlockEntity;
import za.co.neroland.neronotes.block.entity.NeroNotesBlockEntities;
import za.co.neroland.neronotes.soundforge.SoundforgeTravel;
import za.co.neroland.neronotes.soundforge.SoundforgeTravel.TravelResult;

/**
 * The Harmonic Gate: the in-world portal-anchor into the Soundforge, a
 * matte-black frame whose violet neon arch lights when a full crossing
 * charge is banked ({@code charged} blockstate — no BER needed).
 *
 * <p><strong>Interaction</strong> (documented for the wiki):</p>
 * <ul>
 *   <li><strong>Power it</strong> — feed Neroland energy (or standard Forge
 *       Energy on NeoForge/Forge) into any face; the arch lights when a
 *       crossing is affordable.</li>
 *   <li><strong>Use</strong> (right-click) — when the gate is charged, you
 *       cross into the Soundforge. Server-side checks only; the crossing
 *       consumes the charge. There is no progression requirement
 *       (standalone-first).</li>
 *   <li><strong>Inside the Soundforge</strong> — using any Harmonic Gate
 *       returns you to where you entered from, free of charge and checks.</li>
 * </ul>
 */
public class HarmonicGateBlock extends Block implements EntityBlock {

    /** Neon arch lit: a full teleport charge is banked. */
    public static final BooleanProperty CHARGED = BooleanProperty.create("charged");

    public HarmonicGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HarmonicGateBlockEntity(pos, state);
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
        HarmonicGateBlockEntity gate =
                level.getBlockEntity(pos) instanceof HarmonicGateBlockEntity found ? found : null;
        TravelResult result = SoundforgeTravel.useGate(serverPlayer, gate);
        switch (result) {
            case ENTERED -> serverPlayer.sendSystemMessage(Component.translatable("neronotes.gate.entered"));
            case RETURNED -> serverPlayer.sendSystemMessage(Component.translatable("neronotes.gate.returned"));
            case RETURNED_FALLBACK ->
                    serverPlayer.sendSystemMessage(Component.translatable("neronotes.gate.returned_fallback"));
            case NOT_CHARGED -> serverPlayer.sendSystemMessage(Component.translatable("neronotes.gate.not_charged"));
            case UNAVAILABLE -> serverPlayer.sendSystemMessage(Component.translatable("neronotes.gate.unavailable"));
            case NOT_INSIDE -> {
                // Unreachable from the block path (useGate only returns home
                // when already inside); kept for exhaustive switch coverage.
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Server-side ticker: Core's machine tick (energy/blockstate upkeep). */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != NeroNotesBlockEntities.HARMONIC_GATE.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> AbstractMachineBlockEntity.tick(
                tickLevel, pos, tickState, (HarmonicGateBlockEntity) blockEntity);
    }
}
