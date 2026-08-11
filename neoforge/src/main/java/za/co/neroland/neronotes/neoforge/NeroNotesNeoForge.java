package za.co.neroland.neronotes.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.neronotes.NeroNotesCommon;
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
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        for (NotesNetwork.ClientboundPayloadSpec<?> spec : NotesNetwork.clientboundPayloads()) {
            registerClientbound(registrar, spec);
        }
    }

    private static <T extends CustomPacketPayload> void registerClientbound(
            PayloadRegistrar registrar, NotesNetwork.ClientboundPayloadSpec<T> spec) {
        registrar.playToClient(spec.type(), spec.codec(),
                (payload, context) -> context.enqueueWork(() -> spec.handler().accept(payload)));
    }
}
