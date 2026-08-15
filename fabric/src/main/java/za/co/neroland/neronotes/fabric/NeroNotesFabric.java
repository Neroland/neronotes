package za.co.neroland.neronotes.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerolandcore.platform.FabricEnergyLookup;
import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.block.entity.NeroNotesBlockEntities;
import za.co.neroland.neronotes.command.NeroNotesCommands;
import za.co.neroland.neronotes.data.RetentionSweep;
import za.co.neroland.neronotes.link.NotesLinkModule;
import za.co.neroland.neronotes.network.NotesNetwork;

/** Fabric entry point for NeroNotes. */
public final class NeroNotesFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroNotesCommon.LOGGER.info("[NeroNotes] Fabric bootstrap");
        NeroNotesCommon.init();
        // Wire the declared payload types onto NeroNotes' own channel (both logical
        // sides need the type registered; the client receiver is registered in
        // NeroNotesFabricClient). Runs AFTER init(), so the declarations exist.
        for (NotesNetwork.ClientboundPayloadSpec<?> spec : NotesNetwork.clientboundPayloads()) {
            registerClientboundType(spec);
        }
        // Stage 5: serverbound payloads (sequencer edits, Disk Press) — type
        // + receiver both register here (both logical sides need the type;
        // the receiver is a server-side API, safe in common init).
        for (NotesNetwork.ServerboundPayloadSpec<?> spec : NotesNetwork.serverboundPayloads()) {
            registerServerbound(spec);
        }
        // Stage 4: Harmonic Gate energy on Core's shared BlockApiLookup (Fabric
        // registers eagerly, so the block-entity type exists by now).
        FabricEnergyLookup.ENERGY.registerForBlockEntity(
                (gate, direction) -> gate.getEnergy(),
                NeroNotesBlockEntities.HARMONIC_GATE.get());
        // Stage 4: /neronotes soundforge return.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                NeroNotesCommands.register(dispatcher));
        // Stage 7: last-seen touch on join + the daily retention sweep.
        // Stage 9: the same tick hook hands the link module its server handle
        // (Core's link SPI delivers only a UUID; the module needs the server).
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                RetentionSweep.onPlayerJoin(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            NotesLinkModule.rememberServer(server);
            RetentionSweep.onServerTick(server);
        });
    }

    private static <T extends CustomPacketPayload> void registerClientboundType(
            NotesNetwork.ClientboundPayloadSpec<T> spec) {
        PayloadTypeRegistry.clientboundPlay().register(spec.type(), spec.codec());
    }

    private static <T extends CustomPacketPayload> void registerServerbound(
            NotesNetwork.ServerboundPayloadSpec<T> spec) {
        PayloadTypeRegistry.serverboundPlay().register(spec.type(), spec.codec());
        ServerPlayNetworking.registerGlobalReceiver(spec.type(), (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> spec.handler().accept(payload, player));
        });
    }
}
