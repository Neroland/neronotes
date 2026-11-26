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
        //? if >=1.20.5 {
        ServerPlayNetworking.send(player, payload);
        //?} else {
        /*var id = payload.type().id();
        if (!ServerPlayNetworking.canSend(player, id)) {
            return;
        }
        for (var spec : za.co.neroland.neronotes.network.NotesNetwork.clientboundPayloads()) {
            if (spec.type().equals(payload.type())) {
                ServerPlayNetworking.send(player, id,
                        encode(spec, payload));
                return;
            }
        }
        throw new IllegalArgumentException("Unregistered clientbound NeroNotes payload " + id);
        *///?}
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        FabricClientNetworkSender.send(payload);
    }

    //? if <1.20.5 {
    /*private static <T extends CustomPacketPayload> net.minecraft.network.FriendlyByteBuf encode(
            za.co.neroland.neronotes.network.NotesNetwork.ClientboundPayloadSpec<T> spec,
            CustomPacketPayload payload) {
        var buffer = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        spec.codec().encode(buffer, spec.type().payloadClass().cast(payload));
        return buffer;
    }
    *///?}

}
