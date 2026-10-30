package za.co.neroland.neronotes.forge;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
//? if >=1.21.6 {
import net.minecraftforge.eventbus.api.bus.BusGroup;
//?} else {
/*import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
*///?}
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import za.co.neroland.neronotes.client.ClientExchangerState;
import za.co.neroland.neronotes.client.ClientPlaybackEngine;
import za.co.neroland.neronotes.client.ClientSequencerState;
import za.co.neroland.neronotes.client.DiskExchangerScreen;
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
    //? if >=1.21.6 {
    public static void install(BusGroup modBusGroup) {
    //?} else {
    /*public static void install(IEventBus modBusGroup) {
    *///?}
        ClientPlaybackEngine.install();
        ClientSequencerState.install();
        ClientExchangerState.install();
        //? if >=1.21.6 {
        FMLClientSetupEvent.getBus(modBusGroup).addListener(NeroNotesForgeClient::onClientSetup);
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(NeroNotesForgeClient::onLoggingOut);
        //?} else {
        /*modBusGroup.addListener(NeroNotesForgeClient::onClientSetup);
        MinecraftForge.EVENT_BUS.addListener(NeroNotesForgeClient::onLoggingOut);
        *///?}
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        MenuScreens.register(NeroNotesMenus.SEQUENCER.get(), SequencerScreen::new);
        MenuScreens.register(NeroNotesMenus.DISK_PRESS.get(), DiskPressScreen::new);
        MenuScreens.register(NeroNotesMenus.DISK_EXCHANGER.get(), DiskExchangerScreen::new);
    }

    // Leaving a world: drop every client-side cache (playheads, sequencer
    // session, exchanger page) so nothing from one world leaks into the next.
    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPlaybackEngine.clearClientState();
        ClientSequencerState.clear();
        ClientExchangerState.clear();
    }
}
