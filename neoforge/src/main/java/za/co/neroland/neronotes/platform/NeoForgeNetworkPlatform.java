package za.co.neroland.neronotes.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge {@link NetworkPlatform}. Payload types are registered on the
 * {@code neronotes:main} channel by {@code NeroNotesNeoForge} via
 * {@code RegisterPayloadHandlersEvent}, from the declarations in
 * {@code NotesNetwork}.
 *
 * <p>{@link #sendToServer} uses the client-only
 * {@code ClientPacketDistributor}, resolved lazily at the first actual client
 * send — never during service resolution on a dedicated server.</p>
 */
public final class NeoForgeNetworkPlatform implements NetworkPlatform {

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
