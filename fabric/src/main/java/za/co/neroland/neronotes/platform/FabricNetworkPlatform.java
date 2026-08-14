package za.co.neroland.neronotes.platform;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric {@link NetworkPlatform}. Payload types are registered on the
 * {@code neronotes:main} channel by {@code NeroNotesFabric} (types +
 * serverbound receivers) and {@code NeroNotesFabricClient} (client
 * receivers) from the declarations in {@code NotesNetwork}.
 *
 * <p>{@link #sendToServer} delegates to {@link FabricClientNetworkSender} so
 * the client-only {@code ClientPlayNetworking} class is only resolved when a
 * client actually sends — this service itself loads on dedicated servers.</p>
 */
public final class FabricNetworkPlatform implements NetworkPlatform {

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        FabricClientNetworkSender.send(payload);
    }
}
