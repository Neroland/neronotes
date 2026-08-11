package za.co.neroland.neronotes.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.PacketDistributor;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.network.NotesNetwork;

/**
 * Forge {@link NetworkPlatform}. The {@code neronotes:main} channel is built
 * by {@code NeroNotesForge} (ChannelBuilder, after {@code NeroNotesCommon.init()})
 * from the declarations in {@code NotesNetwork}, then attached here.
 */
public final class ForgeNetworkPlatform implements NetworkPlatform {

    private static volatile Channel<CustomPacketPayload> channel;

    /** Called once from {@code NeroNotesForge} after the channel is built. */
    public static void attachChannel(Channel<CustomPacketPayload> built) {
        channel = built;
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        Channel<CustomPacketPayload> ch = channel;
        if (ch == null) {
            NeroNotesCommon.LOGGER.warn("[NeroNotes] dropped server->client payload {} — {} channel not built yet",
                    payload.getClass().getSimpleName(), NotesNetwork.CHANNEL_ID);
            return;
        }
        ch.send(payload, PacketDistributor.PLAYER.with(player));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        // No serverbound payloads are declared yet (the first arrive with the
        // sequencer stage); dropping is deliberate.
        NeroNotesCommon.LOGGER.debug("[NeroNotes] dropped client->server payload {} on {} (no serverbound payloads declared)",
                payload.getClass().getSimpleName(), NotesNetwork.CHANNEL_ID);
    }
}
