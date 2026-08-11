package za.co.neroland.neronotes.forge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.payload.PayloadFlow;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.network.NotesNetwork;
import za.co.neroland.neronotes.platform.ForgeNetworkPlatform;

/** MinecraftForge entry point for NeroNotes. */
@Mod(NeroNotesCommon.MOD_ID)
public final class NeroNotesForge {

    public NeroNotesForge(FMLJavaModLoadingContext context) {
        NeroNotesCommon.LOGGER.info("[NeroNotes] Forge bootstrap");
        NeroNotesCommon.init();
        // Drain Core's registration provider queues (sound events, later blocks/items)
        // on this mod's bus group. Called AFTER init() so every registry class has queued.
        RegistrationProvider.attach(context.getModBusGroup());
        // Build the neronotes:main channel AFTER init() so the payload declarations exist.
        ForgeNetworkPlatform.attachChannel(buildChannel());
    }

    private static Channel<CustomPacketPayload> buildChannel() {
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow = ChannelBuilder
                .named(Identifier.fromNamespaceAndPath(
                        NotesNetwork.CHANNEL_NAMESPACE, NotesNetwork.CHANNEL_PATH))
                .networkProtocolVersion(1)
                .payloadChannel()
                .play()
                .clientbound();
        for (NotesNetwork.ClientboundPayloadSpec<?> spec : NotesNetwork.clientboundPayloads()) {
            flow = addClientbound(flow, spec);
        }
        return flow.build();
    }

    private static <T extends CustomPacketPayload> PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> addClientbound(
            PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow,
            NotesNetwork.ClientboundPayloadSpec<T> spec) {
        // Forge's add(...) is invariant in the buffer generic; narrow the ByteBuf codec.
        return flow.add(spec.type(),
                spec.codec().<RegistryFriendlyByteBuf>mapStream(buf -> buf),
                (payload, context) -> {
                    context.enqueueWork(() -> spec.handler().accept(payload));
                    context.setPacketHandled(true);
                });
    }
}
