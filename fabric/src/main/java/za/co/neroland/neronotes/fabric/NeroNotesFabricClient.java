package za.co.neroland.neronotes.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.client.ClientExchangerState;
import za.co.neroland.neronotes.client.ClientPlaybackEngine;
import za.co.neroland.neronotes.client.ClientSequencerState;
import za.co.neroland.neronotes.client.DiskExchangerScreen;
import za.co.neroland.neronotes.client.DiskPressScreen;
import za.co.neroland.neronotes.client.SequencerScreen;
import za.co.neroland.neronotes.menu.NeroNotesMenus;
import za.co.neroland.neronotes.network.NotesNetwork;

/** Fabric client entry point for NeroNotes. */
public final class NeroNotesFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroNotesCommon.LOGGER.info("[NeroNotes] Fabric client bootstrap");
        // Clientbound receivers for the neronotes:main payloads. Fabric play
        // payload handlers already run on the client main thread.
        for (NotesNetwork.ClientboundPayloadSpec<?> spec : NotesNetwork.clientboundPayloads()) {
            registerReceiver(spec);
        }
        // Stage 3: the synchronised playback engine replaces the debug sinks.
        ClientPlaybackEngine.install();
        // Stage 5: sequencer session sink + the menu screens (registries are
        // populated eagerly on Fabric, so the menu types resolve here).
        ClientSequencerState.install();
        // Stage 6: the Exchanger's library-page sink + screen.
        ClientExchangerState.install();
        MenuScreens.register(NeroNotesMenus.SEQUENCER.get(), SequencerScreen::new);
        MenuScreens.register(NeroNotesMenus.DISK_PRESS.get(), DiskPressScreen::new);
        MenuScreens.register(NeroNotesMenus.DISK_EXCHANGER.get(), DiskExchangerScreen::new);
    }

    private static <T extends CustomPacketPayload> void registerReceiver(
            NotesNetwork.ClientboundPayloadSpec<T> spec) {
        ClientPlayNetworking.registerGlobalReceiver(spec.type(),
                (payload, context) -> spec.handler().accept(payload));
    }
}
