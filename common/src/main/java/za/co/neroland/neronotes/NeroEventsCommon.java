package za.co.neroland.neronotes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-agnostic entry point for NeroNotes. Each loader entry point
 * (Fabric / Forge / NeoForge) calls {@link #init()} once during mod
 * construction. This is a barebones skeleton — no content is registered yet;
 * add shared blocks, items and systems here and reach loader-specific
 * behaviour through a platform seam.
 */
public final class NeroNotesCommon {

    public static final String MOD_ID = "neronotes";
    public static final Logger LOGGER = LoggerFactory.getLogger("NeroNotes");

    private NeroNotesCommon() {
    }

    /** Called once per loader during mod construction. */
    public static void init() {
        LOGGER.info("[NeroNotes] common init");
    }
}
