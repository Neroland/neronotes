package za.co.neroland.neronotes.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import za.co.neroland.neronotes.NeroNotesCommon;

/** NeoForge entry point for NeroNotes. */
@Mod(NeroNotesCommon.MOD_ID)
public final class NeroNotesNeoForge {

    public NeroNotesNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroNotesCommon.LOGGER.info("[NeroNotes] NeoForge bootstrap");
        NeroNotesCommon.init();
    }
}
