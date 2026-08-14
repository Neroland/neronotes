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
        ClientPlayNetworking.send(payload);
    }
}
