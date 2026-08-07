package za.co.neroland.neronotes.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import za.co.neroland.neronotes.NeroNotesCommon;

/** MinecraftForge entry point for NeroNotes. */
@Mod(NeroNotesCommon.MOD_ID)
public final class NeroNotesForge {

    public NeroNotesForge(FMLJavaModLoadingContext context) {
        NeroNotesCommon.LOGGER.info("[NeroNotes] Forge bootstrap");
        NeroNotesCommon.init();
    }
}
