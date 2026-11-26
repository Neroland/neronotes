package za.co.neroland.neronotes.platform;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The client-only half of {@link FabricNetworkPlatform#sendToServer}: kept in
 * its own class so {@code ClientPlayNetworking} (a client-only Fabric API) is
 * only class-loaded when a physical client actually sends a serverbound
 * payload — never during service resolution on a dedicated server.
 */
final class FabricClientNetworkSender {

    private FabricClientNetworkSender() {
    }

    static void send(CustomPacketPayload payload) {
        //? if >=1.20.5 {
        ClientPlayNetworking.send(payload);
        //?} else {
        /*var id = payload.type().id();
        if (!ClientPlayNetworking.canSend(id)) {
            return;
        }
        for (var spec : za.co.neroland.neronotes.network.NotesNetwork.serverboundPayloads()) {
            if (spec.type().equals(payload.type())) {
                ClientPlayNetworking.send(id, encode(spec, payload));
                return;
            }
        }
        throw new IllegalArgumentException("Unregistered serverbound NeroNotes payload " + id);
        *///?}
    }

    //? if <1.20.5 {
    /*private static <T extends CustomPacketPayload> net.minecraft.network.FriendlyByteBuf encode(
            za.co.neroland.neronotes.network.NotesNetwork.ServerboundPayloadSpec<T> spec,
            CustomPacketPayload payload) {
        var buffer = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        spec.codec().encode(buffer, spec.type().payloadClass().cast(payload));
        return buffer;
    }
    *///?}

}
