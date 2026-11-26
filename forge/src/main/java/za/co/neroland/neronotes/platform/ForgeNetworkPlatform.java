package za.co.neroland.neronotes.platform;

//? if >=1.20.5 {
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
        Channel<CustomPacketPayload> ch = channel;
        if (ch == null) {
            NeroNotesCommon.LOGGER.warn("[NeroNotes] dropped client->server payload {} — {} channel not built yet",
                    payload.getClass().getSimpleName(), NotesNetwork.CHANNEL_ID);
            return;
        }
        ch.send(payload, PacketDistributor.SERVER.noArg());
    }
}
//?} else {
/*import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
//? if >=1.20.2 {
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;
//?} else {
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
//?}

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.network.NotesNetwork;
import za.co.neroland.nerolandcore.network.legacy.LegacyPayload;
import za.co.neroland.neronotes.platform.NetworkPlatform;

public final class ForgeNetworkPlatform implements NetworkPlatform {
    private static final int PROTOCOL_VERSION = 1;
    private static SimpleChannel channel;

    public static void register() {
        //? if >=1.20.2 {
        channel = ChannelBuilder.named(new ResourceLocation(NeroNotesCommon.MOD_ID, "main"))
                .networkProtocolVersion(PROTOCOL_VERSION).optional().simpleChannel();
        //?} else {
        channel = NetworkRegistry.newSimpleChannel(new ResourceLocation(NeroNotesCommon.MOD_ID, "main"),
                () -> Integer.toString(PROTOCOL_VERSION), ForgeNetworkPlatform::acceptVersion, ForgeNetworkPlatform::acceptVersion);
        //?}
        int id = 0;
        for (NotesNetwork.ClientboundPayloadSpec<?> packet : NotesNetwork.clientboundPayloads()) {
            registerClientbound(packet, id++);
        }
        for (NotesNetwork.ServerboundPayloadSpec<?> packet : NotesNetwork.serverboundPayloads()) {
            registerServerbound(packet, id++);
        }
    }

    //? if <1.20.2 {
    private static boolean acceptVersion(String version) {
        return Integer.toString(PROTOCOL_VERSION).equals(version) || NetworkRegistry.ABSENT.version().equals(version)
                || NetworkRegistry.ACCEPTVANILLA.equals(version);
    }
    //?}

    private static <T extends LegacyPayload> void registerClientbound(NotesNetwork.ClientboundPayloadSpec<T> packet, int id) {
        channel.messageBuilder(packet.type().payloadClass(), id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((payload, buffer) -> packet.codec().encode(buffer, payload))
                .decoder(packet.codec()::decode)
                .consumerMainThread((payload, context) -> packet.handler().accept(payload))
                .add();
    }

    private static <T extends LegacyPayload> void registerServerbound(NotesNetwork.ServerboundPayloadSpec<T> packet, int id) {
        channel.messageBuilder(packet.type().payloadClass(), id, NetworkDirection.PLAY_TO_SERVER)
                .encoder((payload, buffer) -> packet.codec().encode(buffer, payload))
                .decoder(packet.codec()::decode)
                .consumerMainThread((payload, context) -> {
                    //? if >=1.20.2 {
                    ServerPlayer player = context.getSender();
                    //?} else {
                    ServerPlayer player = context.get().getSender();
                    //?}
                    if (player != null) {
                        packet.handler().accept(payload, player);
                    }
                })
                .add();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, LegacyPayload payload) {
        //? if >=1.20.2 {
        var connection = player.connection.getConnection();
        //?} else {
        var connection = player.connection.connection;
        //?}
        if (channel != null && channel.isRemotePresent(connection)) {
            //? if >=1.20.2 {
            channel.send(payload, connection);
            //?} else {
            channel.send(PacketDistributor.PLAYER.with(() -> player), payload);
            //?}
            return;
        }
        return;
    }

    @Override
    public void sendToServer(LegacyPayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (channel != null && connection != null && channel.isRemotePresent(connection.getConnection())) {
            //? if >=1.20.2 {
            channel.send(payload, connection.getConnection());
            //?} else {
            channel.sendToServer(payload);
            //?}
        }
    }
}
*///?}
