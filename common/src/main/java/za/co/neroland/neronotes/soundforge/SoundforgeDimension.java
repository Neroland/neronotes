package za.co.neroland.neronotes.soundforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.block.NeroNotesBlocks;
import za.co.neroland.neronotes.block.PatternWallBlock;
import za.co.neroland.neronotes.block.VoicePedestalBlock;
import za.co.neroland.neronotes.voice.VoiceFamily;

/**
 * The Soundforge — NeroNotes' small, quiet, starlit composing dimension. In
 * 26.x the dimension itself is pure datapack data
 * ({@code data/neronotes/dimension/soundforge.json} plus
 * {@code dimension_type/soundforge.json}; Core ships no dimension helper and
 * none is needed). This class holds the {@link ResourceKey}s code addresses
 * it by, the progression-gate id that seals it, and the code-built arrival
 * platform (built on first entry — no worldgen, no structure files).
 *
 * <p>The platform is deliberately simple: a matte floor in the void, four
 * inset lights, and a Harmonic Gate at its centre — the way back. Composing
 * furniture (the transport lectern, pattern walls, voice pedestals) arrives
 * in Stage 5.</p>
 *
 * <p>The Soundforge's dimension type is also tagged into the shared
 * {@code neroland:space/dimensions} vocabulary (it is off-Earth). That tag is
 * expected to be empty on a Core-only server and consumers must never
 * {@code orElseThrow} on it — see Core's {@code worldgen.SpaceTags}.</p>
 */
public final class SoundforgeDimension {

    /** The dimension key — {@code neronotes:soundforge}, declared by datapack JSON. */
    public static final ResourceKey<Level> LEVEL =
            ResourceKey.create(Registries.DIMENSION, id("soundforge"));

    /**
     * The progression gate sealing entry — {@code neronotes:soundforge},
     * declared in {@code data/neronotes/neroland_gates/soundforge.json} and
     * requiring Core's {@code nerolandcore:industrial_power}. Checked
     * server-side via Core's {@code progression.ProgressionGates} before any
     * teleport. Everything else in NeroNotes stays ungated.
     */
    public static final Identifier PROGRESSION_GATE = id("soundforge");

    /** The return Harmonic Gate at the platform centre. */
    public static final BlockPos GATE_POS = new BlockPos(0, 100, 0);

    /** The transport lectern (Stage 5) — the composing console, east of the gate. */
    public static final BlockPos LECTERN_POS = new BlockPos(3, 100, 0);

    /** The Disk Press (Stage 5) — west of the gate. */
    public static final BlockPos PRESS_POS = new BlockPos(-3, 100, 0);

    /** Where an arriving player stands (facing the return gate). */
    public static final BlockPos ARRIVAL_POS = new BlockPos(0, 100, 3);

    /** Arrival look angles: facing the gate (north), level gaze. */
    public static final float ARRIVAL_Y_ROT = 180.0f;
    public static final float ARRIVAL_X_ROT = 0.0f;

    private static final int PLATFORM_RADIUS = 7;
    private static final int FLOOR_Y = 99;

    private SoundforgeDimension() {
    }

    /** Whether {@code level} is the Soundforge. Null-safe; false everywhere else. */
    public static boolean isSoundforge(@Nullable Level level) {
        return level != null && level.dimension().equals(LEVEL);
    }

    /**
     * Build (or repair) the arrival platform. Cheap idempotence guard: when
     * the return gate still stands at {@link #GATE_POS} <em>and</em> the
     * transport lectern at {@link #LECTERN_POS}, the platform is assumed
     * intact. Otherwise the floor, lights, return gate and the Stage 5
     * composing furniture (transport lectern, Disk Press, four pattern walls,
     * seven voice pedestals) are (re)placed — so a griefed or partially
     * broken platform heals on the next entry, a first entry into the empty
     * void builds everything, and a pre-Stage-5 platform gains its furniture
     * the next time someone arrives.
     */
    public static void ensurePlatform(ServerLevel soundforge) {
        if (soundforge.getBlockState(GATE_POS).is(NeroNotesBlocks.HARMONIC_GATE.get())
                && soundforge.getBlockState(LECTERN_POS).is(NeroNotesBlocks.TRANSPORT_LECTERN.get())) {
            return;
        }
        NeroNotesCommon.LOGGER.info("[NeroNotes] building the Soundforge arrival platform");
        for (int x = -PLATFORM_RADIUS; x <= PLATFORM_RADIUS; x++) {
            for (int z = -PLATFORM_RADIUS; z <= PLATFORM_RADIUS; z++) {
                boolean rim = Math.abs(x) == PLATFORM_RADIUS || Math.abs(z) == PLATFORM_RADIUS;
                soundforge.setBlockAndUpdate(new BlockPos(x, FLOOR_Y, z),
                        rim ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                            : Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            }
        }
        // Four inset lights — quiet, starlit, not floodlit.
        for (int x : new int[] { -4, 4 }) {
            for (int z : new int[] { -4, 4 }) {
                soundforge.setBlockAndUpdate(new BlockPos(x, FLOOR_Y, z), Blocks.SEA_LANTERN.defaultBlockState());
            }
        }
        // The way home: a Harmonic Gate at the centre. Inside the Soundforge
        // it needs no charge and no open gate — returning is always free.
        soundforge.setBlockAndUpdate(GATE_POS, NeroNotesBlocks.HARMONIC_GATE.get().defaultBlockState());

        // Stage 5 — the composing furniture.
        // Console and press flank the gate.
        soundforge.setBlockAndUpdate(LECTERN_POS, NeroNotesBlocks.TRANSPORT_LECTERN.get().defaultBlockState());
        soundforge.setBlockAndUpdate(PRESS_POS, NeroNotesBlocks.DISK_PRESS.get().defaultBlockState());
        // Four pattern walls along the north rim, one per layer slot.
        for (int layer = 0; layer < 4; layer++) {
            soundforge.setBlockAndUpdate(new BlockPos(layer - 2, 100, -6),
                    NeroNotesBlocks.PATTERN_WALL.get().defaultBlockState()
                            .setValue(PatternWallBlock.LAYER, layer));
        }
        // Seven voice pedestals along the south rim, one per family.
        VoiceFamily[] families = VoiceFamily.values();
        for (int i = 0; i < families.length; i++) {
            soundforge.setBlockAndUpdate(new BlockPos(i - 3, 100, 6),
                    NeroNotesBlocks.VOICE_PEDESTAL.get().defaultBlockState()
                            .setValue(VoicePedestalBlock.FAMILY, i));
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroNotesCommon.MOD_ID, path);
    }
}
