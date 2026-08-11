package za.co.neroland.neronotes.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import za.co.neroland.neronotes.NeroNotesCommon;
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
    }

    private static <T extends CustomPacketPayload> void registerClientboundType(
            NotesNetwork.ClientboundPayloadSpec<T> spec) {
        PayloadTypeRegistry.clientboundPlay().register(spec.type(), spec.codec());
    }
}
