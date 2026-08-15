package za.co.neroland.neronotes.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;
import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.voice.VoiceFamily;

/**
 * The dedicated <strong>NeroNotes</strong> creative tab — init step 6. All
 * NeroNotes items moved here from Core's shared Neroland tab (owner request
 * after the first gallery run): the music kit is its own themed set and reads
 * better as its own tab than folded into the ecosystem catch-all.
 *
 * <p>Registered cross-loader via Core's {@link RegistrationProvider} over the
 * vanilla {@code CREATIVE_MODE_TAB} registry, exactly like Core's own
 * {@code CoreCreativeTab}: a plain-vanilla tab populated by
 * {@code displayItems}, which behaves identically on Fabric, Forge and
 * NeoForge. Note vanilla's {@code CreativeModeTab.builder} takes
 * {@code (Row, column)} — the no-arg overload is a NeoForge-only extension,
 * so common code must not use it.</p>
 *
 * <p>Contents, in progression order: the seven Resonant Blocks (family
 * order), the Resonator, the Harmonic Gate, the Soundforge furniture
 * (transport lectern, pattern wall, voice pedestal, Disk Press, publish
 * lectern), the overworld Disk Exchanger, then the disks (blank, custom).</p>
 */
public final class NeroNotesCreativeTab {

    public static final RegistrationProvider<CreativeModeTab> TABS =
            RegistrationProvider.get(Registries.CREATIVE_MODE_TAB, NeroNotesCommon.MOD_ID);

    public static final RegistryEntry<CreativeModeTab> NERONOTES = TABS.register("neronotes",
            key -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.neronotes"))
                    .icon(() -> new ItemStack(NeroNotesItems.RESONATOR.get()))
                    .displayItems((params, output) -> {
                        for (VoiceFamily family : VoiceFamily.values()) {
                            output.accept(NeroNotesItems.RESONANT_BLOCK_ITEMS.get(family).get());
                        }
                        output.accept(NeroNotesItems.RESONATOR.get());
                        output.accept(NeroNotesItems.HARMONIC_GATE.get());
                        output.accept(NeroNotesItems.TRANSPORT_LECTERN.get());
                        output.accept(NeroNotesItems.PATTERN_WALL.get());
                        output.accept(NeroNotesItems.VOICE_PEDESTAL.get());
                        output.accept(NeroNotesItems.DISK_PRESS.get());
                        output.accept(NeroNotesItems.PUBLISH_LECTERN.get());
                        output.accept(NeroNotesItems.DISK_EXCHANGER.get());
                        output.accept(NeroNotesItems.BLANK_DISK.get());
                        output.accept(NeroNotesItems.CUSTOM_DISK.get());
                    })
                    .build());

    private NeroNotesCreativeTab() {
    }

    /** Class-load hook — step 6 of {@code NeroNotesCommon.init()}, before tabs are built. */
    public static void init() {
        NeroNotesCommon.LOGGER.debug("[NeroNotes] creative tab queued for registration");
    }
}
