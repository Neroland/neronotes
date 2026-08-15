package za.co.neroland.neronotes.block;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.voice.VoiceFamily;

/**
 * NeroNotes block registrations — init step 3, via Core's
 * {@link RegistrationProvider} (each loader entry point calls
 * {@code RegistrationProvider.attach(bus)}; Fabric registers eagerly when
 * {@link #init()} class-loads this).
 *
 * <p>Stage 3 ships one matte-black <strong>Resonant Block</strong> per
 * {@link VoiceFamily} (block id {@code resonant_block_<family>}) and the
 * <strong>Resonator</strong> disk player. Both glow faintly through their
 * blockstate light: the Resonant Block's {@code lit} flare and the
 * Resonator's {@code playing} ring.</p>
 */
public final class NeroNotesBlocks {

    private static final RegistrationProvider<Block> BLOCKS =
            RegistrationProvider.get(Registries.BLOCK, NeroNotesCommon.MOD_ID);

    /** One Resonant Block per voice family, keyed by family (iteration order = declaration order). */
    public static final Map<VoiceFamily, RegistrationProvider.RegistryEntry<Block>> RESONANT_BLOCKS;

    static {
        Map<VoiceFamily, RegistrationProvider.RegistryEntry<Block>> blocks = new EnumMap<>(VoiceFamily.class);
        for (VoiceFamily family : VoiceFamily.values()) {
            blocks.put(family, BLOCKS.register(resonantBlockName(family), key -> new ResonantBlock(family,
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(1.2f, 6.0f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            // The neon edge-light flare (client glow intensity applies to the
                            // particle burst; the block light itself is server-authoritative).
                            .lightLevel(state -> state.getValue(ResonantBlock.LIT) ? 7 : 0))));
        }
        RESONANT_BLOCKS = Collections.unmodifiableMap(blocks);
    }

    /** The Resonator — NeroNotes' disk player (see {@code entity/ResonatorBlockEntity}). */
    public static final RegistrationProvider.RegistryEntry<Block> RESONATOR =
            BLOCKS.register("resonator", key -> new ResonatorBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(1.8f, 6.0f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .lightLevel(state -> state.getValue(ResonatorBlock.PLAYING) ? 9 : 2)));

    /**
     * The Harmonic Gate — the Core-powered anchor into the Soundforge
     * (Stage 4; see {@code entity/HarmonicGateBlockEntity}). Blast-resistant
     * so a stray creeper cannot sever the way home; the {@code charged}
     * arch-light answers "can I cross?" at a glance.
     */
    public static final RegistrationProvider.RegistryEntry<Block> HARMONIC_GATE =
            BLOCKS.register("harmonic_gate", key -> new HarmonicGateBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(3.0f, 1200.0f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .lightLevel(state -> state.getValue(HarmonicGateBlock.CHARGED) ? 11 : 3)));

    /**
     * The transport lectern (Stage 5) — the Soundforge's composing console.
     * Placed on the platform by {@code SoundforgeDimension.ensurePlatform};
     * only functional inside the Soundforge.
     */
    public static final RegistrationProvider.RegistryEntry<Block> TRANSPORT_LECTERN =
            BLOCKS.register("transport_lectern", key -> new TransportLecternBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(2.0f, 6.0f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .lightLevel(state -> 5)));

    /** A pattern wall (Stage 5) — in-world layer display/selection. */
    public static final RegistrationProvider.RegistryEntry<Block> PATTERN_WALL =
            BLOCKS.register("pattern_wall", key -> new PatternWallBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(1.5f, 6.0f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .lightLevel(state -> state.getValue(PatternWallBlock.LIT) ? 9 : 3)));

    /** A voice pedestal (Stage 5) — in-world voice selection per family. */
    public static final RegistrationProvider.RegistryEntry<Block> VOICE_PEDESTAL =
            BLOCKS.register("voice_pedestal", key -> new VoicePedestalBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(1.5f, 6.0f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .lightLevel(state -> 4)));

    /**
     * The Disk Press (Stage 5) — writes a session score onto a blank disk,
     * enforcing the score budget with a named refusal (never truncation).
     */
    public static final RegistrationProvider.RegistryEntry<Block> DISK_PRESS =
            BLOCKS.register("disk_press", key -> new DiskPressBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(2.5f, 6.0f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .lightLevel(state -> 5)));

    /**
     * The publish lectern (Stage 6) — the Soundforge's release desk: tap it
     * with a pressed disk to publish the composition to the shared library.
     * Placed on the platform by {@code SoundforgeDimension.ensurePlatform};
     * only functional inside the Soundforge.
     */
    public static final RegistrationProvider.RegistryEntry<Block> PUBLISH_LECTERN =
            BLOCKS.register("publish_lectern", key -> new PublishLecternBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(2.0f, 6.0f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .lightLevel(state -> 5)));

    /**
     * The Disk Exchanger (Stage 6) — the overworld-side library machine:
     * browse the published catalogue (paginated), copy an entry onto a blank
     * disk, duplicate a disk you hold. Craftable in survival.
     */
    public static final RegistrationProvider.RegistryEntry<Block> DISK_EXCHANGER =
            BLOCKS.register("disk_exchanger", key -> new DiskExchangerBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(2.5f, 6.0f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .lightLevel(state -> 5)));

    private NeroNotesBlocks() {
    }

    /** Registry path for a family's Resonant Block: {@code resonant_block_<family>}. */
    public static String resonantBlockName(VoiceFamily family) {
        return "resonant_block_" + family.id();
    }

    /**
     * Class-load hook — step 3 of {@code NeroNotesCommon.init()}. The static
     * initialisers above queue every block with the provider.
     */
    public static void init() {
        NeroNotesCommon.LOGGER.debug("[NeroNotes] blocks queued for registration");
    }
}
