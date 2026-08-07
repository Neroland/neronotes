package za.co.neroland.neronotes.fabric;

import net.fabricmc.api.ModInitializer;

import za.co.neroland.neronotes.NeroNotesCommon;

/** Fabric entry point for NeroNotes. */
public final class NeroNotesFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroNotesCommon.LOGGER.info("[NeroNotes] Fabric bootstrap");
        NeroNotesCommon.init();
    }
}
