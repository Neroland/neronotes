package za.co.neroland.neronotes.platform;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.network.NotesNetwork;

/**
 * Fabric {@link NetworkPlatform}. Payload types are registered on the
 * {@code neronotes:main} channel by {@code NeroNotesFabric} (types) and
 * {@code NeroNotesFabricClient} (client receivers) from the declarations in
 * {@code NotesNetwork}.
 */
public final class FabricNetworkPlatform implements NetworkPlatform {

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        // No serverbound payloads are declared yet (the first arrive with the
        // sequencer stage). Dropping here is deliberate: routing through a
        // client-only class from this common-loaded service would risk
        // classloading on a dedicated server.
        NeroNotesCommon.LOGGER.debug("[NeroNotes] dropped client->server payload {} on {} (no serverbound payloads declared)",
                payload.getClass().getSimpleName(), NotesNetwork.CHANNEL_ID);
    }
}
