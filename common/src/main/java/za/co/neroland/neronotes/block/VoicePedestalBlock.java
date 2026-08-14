package za.co.neroland.neronotes.block;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.neronotes.soundforge.SequencerSessions;
import za.co.neroland.neronotes.soundforge.SoundforgeDimension;
import za.co.neroland.neronotes.voice.VoiceFamily;

/**
 * A voice pedestal: the Soundforge's in-world <strong>voice selection</strong>
 * surface, one per {@link VoiceFamily} on the platform (the {@code family}
 * blockstate is the display). Tapping a pedestal cycles your session's active
 * layer through that family's registered voices; sneak-tapping retunes the
 * pedestal itself to the next family. Selection is server-side session state;
 * outside the Soundforge the pedestal is inert decoration.
 */
public class VoicePedestalBlock extends Block {

    /** The {@link VoiceFamily} ordinal this pedestal displays/selects. */
    public static final IntegerProperty FAMILY =
            IntegerProperty.create("family", 0, VoiceFamily.values().length - 1);

    public VoicePedestalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FAMILY, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FAMILY);
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
            int next = (state.getValue(FAMILY) + 1) % VoiceFamily.values().length;
            serverLevel.setBlock(pos, state.setValue(FAMILY, next), UPDATE_ALL);
            return InteractionResult.SUCCESS;
        }
        VoiceFamily family = VoiceFamily.values()[state.getValue(FAMILY)];
        Optional<String> voice = SequencerSessions.cycleActiveLayerVoice(
                serverLevel.getServer(), player.getUUID(), family);
        if (voice.isPresent()) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("neronotes.sequencer.voice_selected",
                            Component.translatable(voiceNameKey(voice.get()))), true);
        } else {
            serverPlayer.sendSystemMessage(
                    Component.translatable("neronotes.sequencer.no_session"), true);
        }
        return InteractionResult.SUCCESS;
    }

    /** Translation key for a voice id's display name ({@code neronotes.voice.<path>}). */
    public static String voiceNameKey(String voiceId) {
        Identifier id = Identifier.tryParse(voiceId);
        String path = id == null ? voiceId : id.getPath();
        return "neronotes.voice." + path.replace('/', '.');
    }
}
