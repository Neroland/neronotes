package za.co.neroland.neronotes.menu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.neronotes.NeroNotesCommon;

/**
 * NeroNotes menu-type registrations — init step 4 (items + menus), via
 * Core's {@link RegistrationProvider} over the vanilla MENU registry.
 * <strong>Every open goes through {@link MenuOpener}</strong>; the factories
 * here are only the client-side reconstruction path.
 */
public final class NeroNotesMenus {

    private static final RegistrationProvider<MenuType<?>> MENUS =
            RegistrationProvider.get(Registries.MENU, NeroNotesCommon.MOD_ID);

    /** The transport lectern's sequencer (Stage 5). */
    public static final RegistrationProvider.RegistryEntry<MenuType<SequencerMenu>> SEQUENCER =
            MENUS.register("sequencer",
                    key -> new MenuType<>(SequencerMenu::new, FeatureFlags.VANILLA_SET));

    /** The Disk Press (Stage 5). */
    public static final RegistrationProvider.RegistryEntry<MenuType<DiskPressMenu>> DISK_PRESS =
            MENUS.register("disk_press",
                    key -> new MenuType<>(DiskPressMenu::new, FeatureFlags.VANILLA_SET));

    private NeroNotesMenus() {
    }

    /** Class-load hook — step 4 of {@code NeroNotesCommon.init()}. */
    public static void init() {
        NeroNotesCommon.LOGGER.debug("[NeroNotes] menu types queued for registration");
    }
}
