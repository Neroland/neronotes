package za.co.neroland.neronotes.block.entity;

import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.block.NeroNotesBlocks;
import za.co.neroland.neronotes.voice.VoiceFamily;

/**
 * NeroNotes block-entity type registrations — init step 3 alongside
 * {@link NeroNotesBlocks}. The factory lambdas resolve the block entries at
 * registration time (block registration always precedes block-entity-type
 * registration on every loader).
 *
 * <p>{@link BlockEntityType}'s constructor is private on MC 26.1.2 (builder
 * era) and public on 26.2 (builder removed), so {@link #create} is the single
 * Stonecutter-guarded construction point.</p>
 */
public final class NeroNotesBlockEntities {

    private static final RegistrationProvider<BlockEntityType<?>> BLOCK_ENTITIES =
            RegistrationProvider.get(Registries.BLOCK_ENTITY_TYPE, NeroNotesCommon.MOD_ID);

    /** One shared type for all seven family-tuned Resonant Blocks. */
    public static final RegistrationProvider.RegistryEntry<BlockEntityType<ResonantBlockEntity>> RESONANT_BLOCK =
            BLOCK_ENTITIES.register("resonant_block",
                    key -> create(ResonantBlockEntity::new, resonantBlocks()));

    public static final RegistrationProvider.RegistryEntry<BlockEntityType<ResonatorBlockEntity>> RESONATOR =
            BLOCK_ENTITIES.register("resonator",
                    key -> create(ResonatorBlockEntity::new, new Block[] { NeroNotesBlocks.RESONATOR.get() }));

    /** The Harmonic Gate machine (Stage 4) — Core energy capability registered per loader. */
    public static final RegistrationProvider.RegistryEntry<BlockEntityType<HarmonicGateBlockEntity>> HARMONIC_GATE =
            BLOCK_ENTITIES.register("harmonic_gate",
                    key -> create(HarmonicGateBlockEntity::new, new Block[] { NeroNotesBlocks.HARMONIC_GATE.get() }));

    /** The transport lectern (Stage 5) — sequencer preview playback driver. */
    public static final RegistrationProvider.RegistryEntry<BlockEntityType<TransportLecternBlockEntity>> TRANSPORT_LECTERN =
            BLOCK_ENTITIES.register("transport_lectern",
                    key -> create(TransportLecternBlockEntity::new, new Block[] { NeroNotesBlocks.TRANSPORT_LECTERN.get() }));

    private NeroNotesBlockEntities() {
    }

    private static Block[] resonantBlocks() {
        VoiceFamily[] families = VoiceFamily.values();
        Block[] blocks = new Block[families.length];
        for (int i = 0; i < families.length; i++) {
            blocks[i] = NeroNotesBlocks.RESONANT_BLOCKS.get(families[i]).get();
        }
        return blocks;
    }

    /**
     * The single {@link BlockEntityType} construction point. The
     * {@code (BlockEntitySupplier, Set)} constructor is public in vanilla
     * 26.2; on 26.1.2 it is private in vanilla but patched public by both
     * NeoForge and Forge, and widened for Fabric via
     * {@code fabric/src/main/resources/neronotes.accesswidener} (26.1.2 has
     * no public construction path at all — the builder era ended before it).
     */
    private static <T extends BlockEntity> BlockEntityType<T> create(
            BlockEntityType.BlockEntitySupplier<T> factory, Block[] blocks) {
        return new BlockEntityType<>(factory, Set.of(blocks));
    }

    /**
     * Class-load hook — step 3 of {@code NeroNotesCommon.init()}, after
     * {@link NeroNotesBlocks#init()}.
     */
    public static void init() {
        NeroNotesCommon.LOGGER.debug("[NeroNotes] block entity types queued for registration");
    }
}
