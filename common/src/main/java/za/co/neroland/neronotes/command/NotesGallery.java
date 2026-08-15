package za.co.neroland.neronotes.command;

import java.util.Locale;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.block.NeroNotesBlocks;
import za.co.neroland.neronotes.block.PatternWallBlock;
import za.co.neroland.neronotes.block.VoicePedestalBlock;
import za.co.neroland.neronotes.block.entity.ResonatorBlockEntity;
import za.co.neroland.neronotes.signal.ResonanceService.SignalResult;
import za.co.neroland.neronotes.telemetry.NeroNotesTelemetry;
import za.co.neroland.neronotes.voice.VoiceFamily;

/**
 * {@code /neronotes gallery} — the operator showcase (the ecosystem gallery
 * pattern, mirroring {@code /nerospace gallery}): a small labelled plaza a few
 * blocks east of the command sender with <strong>every NeroNotes block</strong>
 * — the seven Resonant Blocks (one per voice family), a live Resonator, the
 * Harmonic Gate, transport lectern, the four pattern walls (layers 1–4), the
 * seven voice pedestals (one per family), Disk Press, Publish Lectern and Disk
 * Exchanger — each under a floating label stating what it does (fixed strings,
 * verified against the block code; never player-authored text).
 *
 * <p><strong>The music demo:</strong> the Resonator receives the in-code
 * {@link GalleryDemoScore} (four layers, loop points set — it repeats until
 * stopped), is owned by the command sender, bound to the sender's
 * {@code "gallery"} channel (created if absent, reused if present — an
 * ordinary owner channel, nothing special), and playback starts through the
 * normal server-side transport path — the sender is the channel owner, so
 * authorisation passes legitimately; there is no bypass anywhere. The seven
 * Resonant Blocks stand within {@code ResonantBlockIndex} response range of
 * the Resonator, so the families the demo plays (percussion, deep bass, high
 * lead, glassy pluck) visibly flare in time with the beat.</p>
 *
 * <p><strong>Re-running</strong> in the same spot rebuilds in place: blocks
 * are overwritten (replacing the old Resonator stops its playback and frees
 * its channel slot) and stale gallery label stands inside the footprint are
 * removed first, so labels never stack. {@code /neronotes gallery clear} wipes
 * the footprint back to air — run it standing where you ran {@code gallery}.</p>
 */
public final class NotesGallery {

    /** The sender-owned channel the demo Resonator binds to. */
    public static final String GALLERY_CHANNEL = "gallery";

    // Layout: the plaza starts OFFSET_EAST blocks east of the sender and is
    // centred on the sender's Z. Floor at the sender's feet Y, displays one
    // above, labels one above that (Nerospace convention: clear restores the
    // natural ground below the floor layer).
    private static final int SPACING = 3;
    private static final int OFFSET_EAST = 4;
    private static final int HALF_DEPTH = 6;      // rows span z0 .. z0 + 12
    private static final int FOOT_MIN_X = -1;     // footprint, relative to (x0, z0)
    private static final int FOOT_MAX_X = 19;
    private static final int FOOT_MIN_Z = -1;
    private static final int FOOT_MAX_Z = 13;
    private static final int FOOT_HEIGHT = 4;     // floor layer up to the labels

    private NotesGallery() {
    }

    /** {@code /neronotes gallery} — build the showcase. Operator-gated at registration. */
    public static int build(CommandSourceStack source) {
        try {
            return buildGallery(source);
        } catch (RuntimeException failure) {
            return fail(source, "gallery", failure);
        }
    }

    /** {@code /neronotes gallery clear} — wipe the footprint built at the sender's position. */
    public static int clear(CommandSourceStack source) {
        try {
            return clearGallery(source);
        } catch (RuntimeException failure) {
            return fail(source, "gallery clear", failure);
        }
    }

    private static int fail(CommandSourceStack source, String name, RuntimeException failure) {
        NeroNotesTelemetry.captureHandled("command", "gallery", failure);
        NeroNotesCommon.LOGGER.error("[NeroNotes] /neronotes {} failed", name, failure);
        source.sendFailure(Component.literal("NeroNotes " + name + " failed; see latest.log."));
        return 0;
    }

    private static int buildGallery(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition();
        int x0 = origin.getX() + OFFSET_EAST;
        int z0 = origin.getZ() - HALF_DEPTH;
        int fy = origin.getY();

        // Rebuild-idempotence: drop stale gallery label stands inside the
        // footprint first, so re-running never stacks labels. Only armour
        // stands are removed — the gallery spawns nothing else.
        removeLabelStands(level, x0, z0, fy);

        // The plaza floor — the Soundforge platform palette (matte, brick rim).
        BlockState floor = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState rim = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        for (int dx = FOOT_MIN_X; dx <= FOOT_MAX_X; dx++) {
            for (int dz = FOOT_MIN_Z; dz <= FOOT_MAX_Z; dz++) {
                boolean edge = dx == FOOT_MIN_X || dx == FOOT_MAX_X || dz == FOOT_MIN_Z || dz == FOOT_MAX_Z;
                level.setBlockAndUpdate(new BlockPos(x0 + dx, fy, z0 + dz), edge ? rim : floor);
            }
        }

        // Row 1 (north): the seven Resonant Blocks, one per family, in
        // declaration order — all within ResonantBlockIndex response range
        // (16 blocks) of the Resonator below, so the demo's families flare.
        VoiceFamily[] families = VoiceFamily.values();
        for (int i = 0; i < families.length; i++) {
            BlockPos pos = new BlockPos(x0 + i * SPACING, fy + 1, z0);
            level.setBlockAndUpdate(pos, NeroNotesBlocks.RESONANT_BLOCKS.get(families[i]).get().defaultBlockState());
            spawnLabel(level, pos.above(), "Resonant Block — " + familyLabel(families[i]));
        }
        spawnLabel(level, new BlockPos(x0 + 9, fy + 3, z0),
                "Resonant Blocks — tap to play, sneak-tap to tune; they flare to nearby channel notes of their family");

        // Row 2: the Resonator, centred, carrying the looping demo score.
        BlockPos resonatorPos = new BlockPos(x0 + 9, fy + 1, z0 + SPACING);
        level.setBlockAndUpdate(resonatorPos, NeroNotesBlocks.RESONATOR.get().defaultBlockState());
        spawnLabel(level, resonatorPos.above(),
                "Resonator — right-click: play/stop; with a pressed disk: load it; sneak-click: clear (owner/trusted only)");
        SignalResult started = SignalResult.NOT_PLAYING;
        if (level.getBlockEntity(resonatorPos) instanceof ResonatorBlockEntity resonator) {
            // Exactly the placement flow: the sender becomes the server-recorded
            // owner, then the Resonator rebinds to the sender's "gallery"
            // channel (created if absent, reused if it already exists).
            resonator.initializeOwner(level, player.getUUID());
            resonator.bindChannel(GALLERY_CHANNEL);
            resonator.setScore(GalleryDemoScore.build());
            // The normal authorised transport path — the sender owns the
            // channel, so this passes legitimately; no bypass.
            started = resonator.startPlayback(player);
        }

        // Row 3: the machines and stations, labelled with what they truly do.
        placeLabelled(level, new BlockPos(x0, fy + 1, z0 + 2 * SPACING),
                NeroNotesBlocks.HARMONIC_GATE.get().defaultBlockState(),
                "Harmonic Gate — feed it energy (Core/FE); right-click when the arch lights to enter the Soundforge");
        placeLabelled(level, new BlockPos(x0 + SPACING, fy + 1, z0 + 2 * SPACING),
                NeroNotesBlocks.TRANSPORT_LECTERN.get().defaultBlockState(),
                "Transport Lectern — opens the sequencer (functional only inside the Soundforge)");
        placeLabelled(level, new BlockPos(x0 + 2 * SPACING, fy + 1, z0 + 2 * SPACING),
                NeroNotesBlocks.DISK_PRESS.get().defaultBlockState(),
                "Disk Press — presses your session onto a blank disk (Soundforge only)");
        placeLabelled(level, new BlockPos(x0 + 3 * SPACING, fy + 1, z0 + 2 * SPACING),
                NeroNotesBlocks.PUBLISH_LECTERN.get().defaultBlockState(),
                "Publish Lectern — tap with a pressed disk to publish it to the shared library (Soundforge only)");
        placeLabelled(level, new BlockPos(x0 + 4 * SPACING, fy + 1, z0 + 2 * SPACING),
                NeroNotesBlocks.DISK_EXCHANGER.get().defaultBlockState(),
                "Disk Exchanger — browse the shared library, copy entries onto blank disks (works right here)");

        // Row 4: the four pattern walls, one per layer state.
        for (int layer = 0; layer < 4; layer++) {
            level.setBlockAndUpdate(new BlockPos(x0 + layer * SPACING, fy + 1, z0 + 3 * SPACING),
                    NeroNotesBlocks.PATTERN_WALL.get().defaultBlockState()
                            .setValue(PatternWallBlock.LAYER, layer));
        }
        spawnLabel(level, new BlockPos(x0 + 4, fy + 2, z0 + 3 * SPACING),
                "Pattern Walls — tap to select the layer you edit, sneak-tap to retune the wall (Soundforge only); shown: layers 1-4");

        // Row 5 (south): the seven voice pedestals, one per family state.
        for (int i = 0; i < families.length; i++) {
            level.setBlockAndUpdate(new BlockPos(x0 + i * SPACING, fy + 1, z0 + 4 * SPACING),
                    NeroNotesBlocks.VOICE_PEDESTAL.get().defaultBlockState()
                            .setValue(VoicePedestalBlock.FAMILY, i));
        }
        spawnLabel(level, new BlockPos(x0 + 9, fy + 2, z0 + 4 * SPACING),
                "Voice Pedestals — tap to cycle your layer's voice within that family (Soundforge only)");

        String demo = switch (started) {
            case OK -> "The Resonator is playing the looping demo beat on your 'gallery' channel — "
                    + "the matching Resonant Blocks flare in time.";
            case CHANNEL_CAP_REACHED -> "The demo did not start: too many channels are already playing "
                    + "nearby. Stop one, then right-click the Resonator.";
            default -> "The demo did not start (" + started + "); right-click the Resonator to try again.";
        };
        source.sendSuccess(() -> Component.literal("Built the NeroNotes gallery: 7 Resonant Blocks "
                + "(one per voice family), a Resonator with the looping demo score, the Harmonic Gate, "
                + "transport lectern, Disk Press, Publish Lectern, Disk Exchanger, 4 pattern walls and "
                + "7 voice pedestals — all labelled. " + demo
                + " Re-run to rebuild in place; '/neronotes gallery clear' removes it."), false);
        return 1;
    }

    private static int clearGallery(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition();
        int x0 = origin.getX() + OFFSET_EAST;
        int z0 = origin.getZ() - HALF_DEPTH;
        int fy = origin.getY();

        // Clear the footprint to air from the floor layer up (the natural
        // ground below it is untouched). Replacing the Resonator removes its
        // block entity, which stops playback and frees the channel play slot.
        BlockState air = Blocks.AIR.defaultBlockState();
        int cleared = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = FOOT_MIN_X; dx <= FOOT_MAX_X; dx++) {
            for (int dz = FOOT_MIN_Z; dz <= FOOT_MAX_Z; dz++) {
                for (int dy = 0; dy <= FOOT_HEIGHT; dy++) {
                    cursor.set(x0 + dx, fy + dy, z0 + dz);
                    if (!level.getBlockState(cursor).isAir()) {
                        level.setBlock(cursor, air, 2); // notify clients, skip neighbour cascade
                        cleared++;
                    }
                }
            }
        }
        int removed = removeLabelStands(level, x0, z0, fy);

        int clearedBlocks = cleared;
        int removedStands = removed;
        source.sendSuccess(() -> Component.literal("Cleared the NeroNotes gallery: " + clearedBlocks
                + " blocks removed, " + removedStands + " labels removed."), false);
        return 1;
    }

    /** Remove the gallery's floating label stands inside the footprint. Armour stands only. */
    private static int removeLabelStands(ServerLevel level, int x0, int z0, int fy) {
        AABB box = new AABB(x0 + FOOT_MIN_X, fy, z0 + FOOT_MIN_Z,
                x0 + FOOT_MAX_X + 1, fy + FOOT_HEIGHT + 2, z0 + FOOT_MAX_Z + 1);
        int removed = 0;
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, box)) {
            stand.discard();
            removed++;
        }
        return removed;
    }

    private static void placeLabelled(ServerLevel level, BlockPos pos, BlockState state, String label) {
        level.setBlockAndUpdate(pos, state);
        spawnLabel(level, pos.above(), label);
    }

    /** A floating label: an invisible, named, invulnerable armour stand (the Nerospace pattern). */
    private static void spawnLabel(ServerLevel level, BlockPos pos, String text) {
        ArmorStand stand = new ArmorStand(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        stand.setCustomName(Component.literal(text));
        stand.setCustomNameVisible(true);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        level.addFreshEntity(stand);
    }

    /** Fixed display name for a family, derived from its id: {@code deep_bass} → {@code Deep Bass}. */
    private static String familyLabel(VoiceFamily family) {
        StringBuilder label = new StringBuilder();
        for (String part : family.id().split("_")) {
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return label.toString();
    }
}
