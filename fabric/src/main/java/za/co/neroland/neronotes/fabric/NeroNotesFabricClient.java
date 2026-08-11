package za.co.neroland.neronotes.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import za.co.neroland.neronotes.NeroNotesCommon;
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
    }

    private static <T extends CustomPacketPayload> void registerReceiver(
            NotesNetwork.ClientboundPayloadSpec<T> spec) {
        ClientPlayNetworking.registerGlobalReceiver(spec.type(),
                (payload, context) -> spec.handler().accept(payload));
    }
}
