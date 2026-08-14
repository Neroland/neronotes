package za.co.neroland.neronotes.forge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.payload.PayloadFlow;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.command.NeroNotesCommands;
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
        // Stage 4: Harmonic Gate energy on Core's shared capability.
        ForgeCapabilities.register();
        // Stage 4: /neronotes soundforge return (game-bus command registration).
        RegisterCommandsEvent.BUS.addListener(event ->
                NeroNotesCommands.register(event.getDispatcher()));
        // Stage 3/5: client-only playback engine + screens, behind the dist
        // guard so client classes never load on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeroNotesForgeClient.install(context.getModBusGroup());
        }
    }

    private static Channel<CustomPacketPayload> buildChannel() {
        // Bidirectional since Stage 5: clientbound resonance/session payloads
        // plus the serverbound sequencer-edit and Disk Press requests.
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow = ChannelBuilder
                .named(Identifier.fromNamespaceAndPath(
                        NotesNetwork.CHANNEL_NAMESPACE, NotesNetwork.CHANNEL_PATH))
                .networkProtocolVersion(1)
                .payloadChannel()
                .play()
                .bidirectional();
        for (NotesNetwork.ClientboundPayloadSpec<?> spec : NotesNetwork.clientboundPayloads()) {
            flow = addClientbound(flow, spec);
        }
        for (NotesNetwork.ServerboundPayloadSpec<?> spec : NotesNetwork.serverboundPayloads()) {
            flow = addServerbound(flow, spec);
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

    private static <T extends CustomPacketPayload> PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> addServerbound(
            PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow,
            NotesNetwork.ServerboundPayloadSpec<T> spec) {
        return flow.add(spec.type(),
                spec.codec().<RegistryFriendlyByteBuf>mapStream(buf -> buf),
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        if (context.getSender() instanceof ServerPlayer serverPlayer) {
                            spec.handler().accept(payload, serverPlayer);
                        }
                    });
                    context.setPacketHandled(true);
                });
    }
}
