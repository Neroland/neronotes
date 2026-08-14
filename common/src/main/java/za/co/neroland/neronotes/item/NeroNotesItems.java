package za.co.neroland.neronotes.item;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

import za.co.neroland.nerolandcore.registry.CoreCreativeTab;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.block.NeroNotesBlocks;
import za.co.neroland.neronotes.voice.VoiceFamily;

/**
 * NeroNotes item registrations — init step 4, via Core's
 * {@link RegistrationProvider}. Stage 3 ships the {@link NotesBlockItem}s for
 * the seven Resonant Blocks and the Resonator; disk items arrive in Stage 5.
 *
 * <p>Creative-tab placement is separate ({@link #addToCreativeTab()}, init
 * step 6) so the numbered ordering stays honest.</p>
 */
public final class NeroNotesItems {

    private static final RegistrationProvider<Item> ITEMS =
            RegistrationProvider.get(Registries.ITEM, NeroNotesCommon.MOD_ID);

    /** BlockItems for the seven Resonant Blocks, keyed by family. */
    public static final Map<VoiceFamily, RegistrationProvider.RegistryEntry<Item>> RESONANT_BLOCK_ITEMS;

    static {
        Map<VoiceFamily, RegistrationProvider.RegistryEntry<Item>> items = new EnumMap<>(VoiceFamily.class);
        for (VoiceFamily family : VoiceFamily.values()) {
            items.put(family, ITEMS.register(NeroNotesBlocks.resonantBlockName(family),
                    key -> new NotesBlockItem(NeroNotesBlocks.RESONANT_BLOCKS.get(family).get(),
                            new Item.Properties().setId(key).useBlockDescriptionPrefix(),
                            "neronotes.tooltip.resonant_block.tap",
                            "neronotes.tooltip.resonant_block.resonance")));
        }
        RESONANT_BLOCK_ITEMS = Collections.unmodifiableMap(items);
    }

    public static final RegistrationProvider.RegistryEntry<Item> RESONATOR =
            ITEMS.register("resonator", key -> new NotesBlockItem(NeroNotesBlocks.RESONATOR.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix(),
                    "neronotes.tooltip.resonator.disk",
                    "neronotes.tooltip.resonator.bind"));

    /** The Harmonic Gate (Stage 4) — the powered anchor into the Soundforge. */
    public static final RegistrationProvider.RegistryEntry<Item> HARMONIC_GATE =
            ITEMS.register("harmonic_gate", key -> new NotesBlockItem(NeroNotesBlocks.HARMONIC_GATE.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix(),
                    "neronotes.tooltip.harmonic_gate.energy",
                    "neronotes.tooltip.harmonic_gate.travel"));

    // ------------------------------------------------------------------
    // Stage 5 — Soundforge furniture + disks
    // ------------------------------------------------------------------

    public static final RegistrationProvider.RegistryEntry<Item> TRANSPORT_LECTERN =
            ITEMS.register("transport_lectern", key -> new NotesBlockItem(NeroNotesBlocks.TRANSPORT_LECTERN.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix(),
                    "neronotes.tooltip.transport_lectern.sequencer",
                    "neronotes.tooltip.transport_lectern.place"));

    public static final RegistrationProvider.RegistryEntry<Item> PATTERN_WALL =
            ITEMS.register("pattern_wall", key -> new NotesBlockItem(NeroNotesBlocks.PATTERN_WALL.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix(),
                    "neronotes.tooltip.pattern_wall.select",
                    "neronotes.tooltip.pattern_wall.retune"));

    public static final RegistrationProvider.RegistryEntry<Item> VOICE_PEDESTAL =
            ITEMS.register("voice_pedestal", key -> new NotesBlockItem(NeroNotesBlocks.VOICE_PEDESTAL.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix(),
                    "neronotes.tooltip.voice_pedestal.select",
                    "neronotes.tooltip.voice_pedestal.retune"));

    public static final RegistrationProvider.RegistryEntry<Item> DISK_PRESS =
            ITEMS.register("disk_press", key -> new NotesBlockItem(NeroNotesBlocks.DISK_PRESS.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix(),
                    "neronotes.tooltip.disk_press.budget",
                    "neronotes.tooltip.disk_press.anonymous"));

    /** A blank resonant disk — the Disk Press's input; craftable in survival. */
    public static final RegistrationProvider.RegistryEntry<Item> BLANK_DISK =
            ITEMS.register("blank_disk", key -> new BlankDiskItem(
                    new Item.Properties().setId(key).stacksTo(16)));

    /** A pressed custom disk — created only by the Disk Press; carries the score component. */
    public static final RegistrationProvider.RegistryEntry<Item> CUSTOM_DISK =
            ITEMS.register("custom_disk", key -> new CustomDiskItem(
                    new Item.Properties().setId(key).stacksTo(1)));

    private NeroNotesItems() {
    }

    /**
     * Class-load hook — step 4 of {@code NeroNotesCommon.init()}, after the
     * blocks have queued at step 3.
     */
    public static void init() {
        NeroNotesCommon.LOGGER.debug("[NeroNotes] items queued for registration");
    }

    /**
     * Init step 6 — add every item to Core's shared Neroland creative tab
     * (before tabs are built).
     */
    public static void addToCreativeTab() {
        for (VoiceFamily family : VoiceFamily.values()) {
            CoreCreativeTab.add(RESONANT_BLOCK_ITEMS.get(family));
        }
        CoreCreativeTab.add(RESONATOR);
        CoreCreativeTab.add(HARMONIC_GATE);
        CoreCreativeTab.add(TRANSPORT_LECTERN);
        CoreCreativeTab.add(PATTERN_WALL);
        CoreCreativeTab.add(VOICE_PEDESTAL);
        CoreCreativeTab.add(DISK_PRESS);
        CoreCreativeTab.add(BLANK_DISK);
        CoreCreativeTab.add(CUSTOM_DISK);
    }
}
