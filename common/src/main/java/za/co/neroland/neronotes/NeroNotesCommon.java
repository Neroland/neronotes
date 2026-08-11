package za.co.neroland.neronotes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.neronotes.config.NeroNotesConfig;
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

        // 3. Blocks + block entities.
        //    (Stage 3/4/5/6 placeholder: Resonant Blocks, Resonators, Harmonic Gate,
        //     transport/publish lecterns, Disk Press, Disk Exchanger.)

        // 4. Items + menus.
        //    (Stage 5 placeholder: blank disks, custom disks; sequencer + exchanger menu types.
        //     All openMenu call sites route through menu/MenuOpener.)

        // 5. Sound events + voice registry — SoundEvents via Core's RegistrationProvider
        //    (aliased to vanilla sounds in assets/neronotes/sounds.json; no .ogg ships in 0.1.0),
        //    then the data-driven voice registry from assets/neronotes/voices/default.json.
        NeroNotesSounds.init();
        VoiceRegistry.bootstrap();

        // 6. Creative tab.
        //    (Stage 3+ placeholder: Core's CoreCreativeTab.add(...) for each item, before tabs build.)

        // 7. Data / PlayerDataErasure registration — EARLY ON PURPOSE: registering late is how an
        //    erasure request silently misses a store.
        //    (Stage 7 registers erasers with Core here. The stores are purge-ready already:
        //     signal/ChannelStore routes through Core's SavedDataRecovery and exposes
        //     purgePlayer(UUID) + purgePlayerAndBackup(...) for the erasure hook.)

        // 8. Network payloads — on NeroNotes' OWN channel (neronotes:main), never Core's CoreNetwork.
        NotesNetwork.registerPayloads();

        // 9. Channel + playback services — the resonance signal (Stage 2); playback sync lands in Stage 3.
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
