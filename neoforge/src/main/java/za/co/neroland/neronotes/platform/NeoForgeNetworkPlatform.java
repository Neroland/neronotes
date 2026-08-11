package za.co.neroland.neronotes.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.network.NotesNetwork;

/**
 * NeoForge {@link NetworkPlatform}. Payload types are registered on the
 * {@code neronotes:main} channel by {@code NeroNotesNeoForge} via
 * {@code RegisterPayloadHandlersEvent}, from the declarations in
 * {@code NotesNetwork}.
 */
public final class NeoForgeNetworkPlatform implements NetworkPlatform {

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        // No serverbound payloads are declared yet (the first arrive with the
        // sequencer stage); dropping is deliberate rather than half-wiring a path
        // that nothing exercises.
        NeroNotesCommon.LOGGER.debug("[NeroNotes] dropped client->server payload {} on {} (no serverbound payloads declared)",
                payload.getClass().getSimpleName(), NotesNetwork.CHANNEL_ID);
    }
}
