package za.co.neroland.neronotes.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.command.NeroNotesCommands;
import za.co.neroland.neronotes.data.RetentionSweep;
import za.co.neroland.neronotes.link.NotesLinkModule;
import za.co.neroland.neronotes.network.NotesNetwork;

/** NeoForge entry point for NeroNotes. */
@Mod(NeroNotesCommon.MOD_ID)
public final class NeroNotesNeoForge {

    public NeroNotesNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroNotesCommon.LOGGER.info("[NeroNotes] NeoForge bootstrap");
        NeroNotesCommon.init();
        // Drain Core's registration provider queues (sound events, later blocks/items)
        // on this mod's event bus. Called AFTER init() so every registry class has queued.
        RegistrationProvider.attach(modEventBus);
        // Wire the declared neronotes:main payloads (declared during init() step 8).
        modEventBus.addListener(NeroNotesNeoForge::onRegisterPayloadHandlers);
        // Stage 4: Harmonic Gate energy on Core's shared capability.
        NeoForgeCapabilities.register(modEventBus);
        // Stage 4: /neronotes soundforge return (game-bus command registration).
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                NeroNotesCommands.register(event.getDispatcher()));
        // Stage 7: last-seen touch on join + the daily retention sweep.
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                RetentionSweep.onPlayerJoin(serverPlayer);
            }
        });
        // Stage 9: the same tick hook hands the link module its server handle
        // (Core's link SPI delivers only a UUID; the module needs the server).
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            NotesLinkModule.rememberServer(event.getServer());
            RetentionSweep.onServerTick(event.getServer());
        });
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        for (NotesNetwork.ClientboundPayloadSpec<?> spec : NotesNetwork.clientboundPayloads()) {
            registerClientbound(registrar, spec);
        }
        for (NotesNetwork.ServerboundPayloadSpec<?> spec : NotesNetwork.serverboundPayloads()) {
            registerServerbound(registrar, spec);
        }
    }

    private static <T extends CustomPacketPayload> void registerClientbound(
            PayloadRegistrar registrar, NotesNetwork.ClientboundPayloadSpec<T> spec) {
        registrar.playToClient(spec.type(), spec.codec(),
                (payload, context) -> context.enqueueWork(() -> spec.handler().accept(payload)));
    }

    private static <T extends CustomPacketPayload> void registerServerbound(
            PayloadRegistrar registrar, NotesNetwork.ServerboundPayloadSpec<T> spec) {
        registrar.playToServer(spec.type(), spec.codec(),
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        spec.handler().accept(payload, serverPlayer);
                    }
                }));
    }
}
