package za.co.neroland.neronotes.block.entity;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.neronotes.block.ResonantBlock;
import za.co.neroland.neronotes.block.ResonantBlockIndex;
import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.signal.ResonanceService;
import za.co.neroland.neronotes.voice.VoiceDefinition;
import za.co.neroland.neronotes.voice.VoiceFamily;
import za.co.neroland.neronotes.voice.VoiceRegistry;

/**
 * The Resonant Block's entity: it holds the block's current pitch (a
 * MIDI-style note number inside its family voice's band) and mediates the
 * two ways that pitch changes — player interaction (use / sneak-use, see
 * {@link ResonantBlock}) and incoming resonance events (via
 * {@link ResonantBlockIndex}). Non-ticking; the flare is a scheduled block
 * tick, not an entity ticker.
 *
 * <p>The voice it plays is data-driven: the first registry voice of its
 * family (falling back to the registry fallback voice), so a voice pack can
 * re-tune every Resonant Block without code.</p>
 */
public class ResonantBlockEntity extends BlockEntity {

    private static final String KEY_PITCH = "pitch";

    /** Interaction notes play at a firm, fixed velocity. */
    public static final int INTERACTION_VELOCITY = 100;

    private int pitch = -1; // resolved lazily against the family voice band

    public ResonantBlockEntity(BlockPos pos, BlockState state) {
        super(NeroNotesBlockEntities.RESONANT_BLOCK.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------

    /** The family this block is tuned to (from its {@link ResonantBlock}). */
    public VoiceFamily family() {
        if (getBlockState().getBlock() instanceof ResonantBlock resonant) {
            return resonant.family();
        }
        return VoiceFamily.HIGH_LEAD;
    }

    /**
     * The voice this block renders through: the first declared voice of its
     * family, or the registry fallback if the family has none (a stripped
     * voice pack). Data-driven — never hardcoded.
     */
    public VoiceDefinition voice() {
        var inFamily = VoiceRegistry.shared().byFamily(family());
        return inFamily.isEmpty() ? VoiceRegistry.shared().fallback() : inFamily.getFirst();
    }

    /** Current pitch, clamped into the family voice's band. */
    public int pitch() {
        VoiceDefinition voice = voice();
        if (pitch < 0) {
            // Default: the middle of the band.
            pitch = voice.clampToBand((voice.minPitch() + voice.maxPitch()) / 2);
        }
        return voice.clampToBand(pitch);
    }

    // ------------------------------------------------------------------
    // Pitch changes
    // ------------------------------------------------------------------

    /** Sneak-use: one step up within the band, wrapping at the top. */
    public void cyclePitch() {
        VoiceDefinition voice = voice();
        int next = pitch() + 1;
        pitch = next > voice.maxPitch() ? voice.minPitch() : next;
        setChanged();
    }

    /**
     * An incoming channel resonance event of this block's family: adopt the
     * pitch (clamped into the band) and flare. Server thread, via
     * {@link ResonantBlockIndex}.
     */
    public void receiveResonance(int incomingPitch) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        int adopted = voice().clampToBand(incomingPitch);
        if (adopted != pitch) {
            pitch = adopted;
            setChanged();
        }
        ResonantBlock.flare(serverLevel, worldPosition);
    }

    /**
     * Use / sneak-use: play the current note as a live local resonance
     * (heard by everyone in range, mute/volume applied client-side) and
     * flare. {@code source} is the interacting player's server-side UUID.
     */
    public void playInteractionNote(UUID source) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ResonanceService.emitLocalNote(serverLevel, source, Vec3.atCenterOf(worldPosition),
                voice().voiceId(), pitch(), INTERACTION_VELOCITY);
        ResonantBlock.flare(serverLevel, worldPosition);
    }

    // ------------------------------------------------------------------
    // Index registration (chunk load tracking)
    // ------------------------------------------------------------------

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel serverLevel) {
            ResonantBlockIndex.register(
                    serverLevel.dimension().identifier().toString(), worldPosition, this);
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            ResonantBlockIndex.unregister(
                    serverLevel.dimension().identifier().toString(), worldPosition);
        }
        super.setRemoved();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt(KEY_PITCH, pitch());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        pitch = input.getIntOr(KEY_PITCH, -1);
        if (pitch > Score.MAX_PITCH) {
            pitch = -1; // corrupt / out-of-range: re-derive the band default
        }
    }
}
