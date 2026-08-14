package za.co.neroland.neronotes.forge;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import za.co.neroland.neronotes.client.ClientPlaybackEngine;
import za.co.neroland.neronotes.client.ClientSequencerState;
import za.co.neroland.neronotes.client.DiskPressScreen;
import za.co.neroland.neronotes.client.SequencerScreen;
import za.co.neroland.neronotes.menu.NeroNotesMenus;

/**
 * Forge client-only bootstrap. Kept as a separate class so
 * {@code client/ClientPlaybackEngine} (which references
 * {@code net.minecraft.client.*}) is only class-loaded behind the
 * {@code FMLEnvironment.dist} guard in {@link NeroNotesForge} — never on a
 * dedicated server.
 */
public final class NeroNotesForgeClient {

    private NeroNotesForgeClient() {
    }

    /**
     * Install the Stage 3 synchronised playback engine and the Stage 5
     * sequencer sink as the payload sinks, and register the menu screens once
     * the registries exist (client setup — menu types are not registered yet
     * at mod construction).
     */
    public static void install(BusGroup modBusGroup) {
        ClientPlaybackEngine.install();
        ClientSequencerState.install();
        FMLClientSetupEvent.getBus(modBusGroup).addListener(event -> {
            MenuScreens.register(NeroNotesMenus.SEQUENCER.get(), SequencerScreen::new);
            MenuScreens.register(NeroNotesMenus.DISK_PRESS.get(), DiskPressScreen::new);
        });
    }
}
