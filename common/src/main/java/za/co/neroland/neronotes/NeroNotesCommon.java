package za.co.neroland.neronotes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.neronotes.block.NeroNotesBlocks;
import za.co.neroland.neronotes.block.entity.NeroNotesBlockEntities;
import za.co.neroland.neronotes.config.NeroNotesConfig;
import za.co.neroland.neronotes.item.NeroNotesDataComponents;
import za.co.neroland.neronotes.item.NeroNotesItems;
import za.co.neroland.neronotes.menu.NeroNotesMenus;
import za.co.neroland.neronotes.network.NotesNetwork;
import za.co.neroland.neronotes.platform.Services;
import za.co.neroland.neronotes.signal.ResonanceService;
import za.co.neroland.neronotes.sound.NeroNotesSounds;
import za.co.neroland.neronotes.telemetry.NeroNotesTelemetry;
import za.co.neroland.neronotes.voice.VoiceRegistry;

/**
 * Loader-agnostic entry point for NeroNotes — the music layer of the Neroland
 * family. Each loader entry point (Fabric / Forge / NeoForge) calls
 * {@link #init()} once during mod construction.
 *
 * <p>{@code init()} follows the ecosystem's numbered ordering. Fabric
 * registers eagerly, so the ordering is not cosmetic: later stages fill the
 * numbered placeholder slots below rather than inserting themselves wherever
 * is convenient.</p>
 */
public final class NeroNotesCommon {

    public static final String MOD_ID = "neronotes";
    public static final Logger LOGGER = LoggerFactory.getLogger("NeroNotes");

    private NeroNotesCommon() {
    }

    /** Called once per loader during mod construction. */
    public static void init() {
        LOGGER.info("[NeroNotes] common init");

        // 0. Platform seams — resolve EVERY ServiceLoader service eagerly, never lazily mid-tick.
        Services.init();

        // 1. Config — the full 0.1.0 key schema; later stages only read it.
        NeroNotesConfig.register();

        // 2. Telemetry — opt-out Sentry error reporting (see PRIVACY.md).
        NeroNotesTelemetry.init();

        // 3. Blocks + block entities — Resonant Blocks + Resonators (Stage 3),
        //    the Harmonic Gate machine (Stage 4; Core energy capability is
        //    registered per loader from each loader entry point), and the
        //    Stage 5 Soundforge furniture: transport lectern (+ preview BE),
        //    pattern walls, voice pedestals, Disk Press.
        //    (Stage 6 placeholder: publish lectern, Disk Exchanger.)
        NeroNotesBlocks.init();
        NeroNotesBlockEntities.init();

        // 4. Items + menus + data components — BlockItems (tooltips live on
        //    the BlockItem), the Stage 5 blank/custom disks with the
        //    disk_contents component, and the sequencer + Disk Press menu
        //    types. All openMenu call sites route through menu/MenuOpener.
        NeroNotesItems.init();
        NeroNotesDataComponents.init();
        NeroNotesMenus.init();

        // 5. Sound events + voice registry — SoundEvents via Core's RegistrationProvider
        //    (aliased to vanilla sounds in assets/neronotes/sounds.json; no .ogg ships in 0.1.0),
        //    then the data-driven voice registry from assets/neronotes/voices/default.json.
        NeroNotesSounds.init();
        VoiceRegistry.bootstrap();

        // 6. Creative tab — every NeroNotes item into Core's shared Neroland tab, before tabs build.
        NeroNotesItems.addToCreativeTab();

        // 7. Data / PlayerDataErasure registration — EARLY ON PURPOSE: registering late is how an
        //    erasure request silently misses a store.
        //    (Stage 7 registers erasers with Core here. The stores are purge-ready already:
        //     signal/ChannelStore and soundforge/SoundforgeSessionStore both route through
        //     Core's SavedDataRecovery and expose purgePlayer(UUID) + purgePlayerAndBackup(...)
        //     + hasRow probes for the erasure hook.)

        // 8. Network payloads — on NeroNotes' OWN channel (neronotes:main), never Core's CoreNetwork.
        NotesNetwork.registerPayloads();

        // 9. Channel + playback services — the resonance signal (Stage 2). The Stage 3 client
        //    playback engine installs its payload sinks from each loader's CLIENT entry point
        //    (client/ClientPlaybackEngine.install()), never from common init.
        ResonanceService.init();

        // 10. Sibling integrations — feature-detected via Services.platform().isModLoaded(...),
        //     compileOnly + runtime guard, no reflection.
        //     (Stage 8 placeholder: NeroQuests rewards/triggers, NeroEconomy pricing seam,
        //      Core ThresholdEvents crossings.)

        // 11. Link module — LAST, wrapped in try/catch so a link failure can never take the mod down.
        //     (Stage 9 placeholder:
        //      try { NotesLinkModule.register(); } catch (RuntimeException e) { LOGGER.warn(...); })
    }
}
