package za.co.neroland.neronotes.fabric;

import net.fabricmc.api.ClientModInitializer;

import za.co.neroland.neronotes.NeroNotesCommon;

/** Fabric client entry point for NeroNotes. */
public final class NeroNotesFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroNotesCommon.LOGGER.info("[NeroNotes] Fabric client bootstrap");
    }
}
