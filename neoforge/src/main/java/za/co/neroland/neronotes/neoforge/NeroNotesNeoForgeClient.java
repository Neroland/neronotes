package za.co.neroland.neronotes.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.client.ClientExchangerState;
import za.co.neroland.neronotes.client.ClientPlaybackEngine;
import za.co.neroland.neronotes.client.ClientSequencerState;
import za.co.neroland.neronotes.client.DiskExchangerScreen;
import za.co.neroland.neronotes.client.DiskPressScreen;
import za.co.neroland.neronotes.client.SequencerScreen;
import za.co.neroland.neronotes.menu.NeroNotesMenus;

/**
 * NeoForge client-only entry point: constructed on the physical client only
 * ({@code dist = Dist.CLIENT}), so client classes never load on a dedicated
 * server. Installs the Stage 3 synchronised playback engine and the Stage 5
 * sequencer sink as the payload sinks, and registers the menu screens.
 */
@Mod(value = NeroNotesCommon.MOD_ID, dist = Dist.CLIENT)
public final class NeroNotesNeoForgeClient {

    public NeroNotesNeoForgeClient(IEventBus modEventBus) {
        ClientPlaybackEngine.install();
        ClientSequencerState.install();
        ClientExchangerState.install();
        modEventBus.addListener(NeroNotesNeoForgeClient::onRegisterScreens);
        // Leaving a world: drop every client-side cache (playheads, sequencer
        // session, exchanger page) so nothing from one world leaks into the next.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            ClientPlaybackEngine.clearClientState();
            ClientSequencerState.clear();
            ClientExchangerState.clear();
        });
    }

    private static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(NeroNotesMenus.SEQUENCER.get(), SequencerScreen::new);
        event.register(NeroNotesMenus.DISK_PRESS.get(), DiskPressScreen::new);
        event.register(NeroNotesMenus.DISK_EXCHANGER.get(), DiskExchangerScreen::new);
    }
}
