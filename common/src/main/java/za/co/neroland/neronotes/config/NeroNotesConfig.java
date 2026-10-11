package za.co.neroland.neronotes.config;

import za.co.neroland.nerolandcore.config.ConfigManager;
import za.co.neroland.nerolandcore.config.ConfigSchema;
import za.co.neroland.nerolandcore.config.ConfigValue;
import za.co.neroland.neronotes.NeroNotesCommon;

/**
 * NeroNotes configuration via Core's config framework — written to
 * {@code config/neronotes.properties}.
 *
 * <p>This is the FULL 0.1.0 key schema, landed in Stage 0 so later stages
 * only ever read it. Every gameplay key is server-authoritative; the only
 * client-local keys are {@code telemetryEnabled}, the per-voice-family volumes,
 * the glow intensity and the "mute other players' bases" toggle.</p>
 */
public final class NeroNotesConfig {

    private static final String HEADER =
            "NeroNotes configuration.\n"
            + "Gameplay keys are server-authoritative: in multiplayer the server's values win and\n"
            + "clients never decide what they may play, publish or own. The client.* keys and\n"
            + "telemetryEnabled are client-local (audio comfort + crash-report opt-out). Crash\n"
            + "reporting is on by default; see PRIVACY.md for what it does and does not collect.";

    public static final ConfigSchema SCHEMA = ConfigSchema.create(NeroNotesCommon.MOD_ID, HEADER);

    // ------------------------------------------------------------------
    // Server-authoritative gameplay keys
    // ------------------------------------------------------------------

    /** Resonance emit range in blocks (Stage 2). */
    public static final ConfigValue<Integer> EMIT_RANGE_BLOCKS = SCHEMA.intRange(
            "signal.emit_range_blocks", 64, 16, 128, true,
            "Range in blocks within which emitters broadcast resonance (note/transport) events.");

    /** Audio-spam guard: concurrently playing channels per chunk radius (Stage 2). */
    public static final ConfigValue<Integer> MAX_PLAYING_CHANNELS_PER_CHUNK_RADIUS = SCHEMA.intRange(
            "signal.max_playing_channels_per_chunk_radius", 3, 1, 16, true,
            "Maximum concurrently playing channels per chunk radius; further play requests are quietly refused.");

    /** Sync drift threshold in milliseconds before a hard seek (Stage 3). */
    public static final ConfigValue<Integer> SYNC_DRIFT_THRESHOLD_MS = SCHEMA.intRange(
            "sync.drift_threshold_ms", 100, 20, 1000, true,
            "Measured playback drift (ms) beyond which a client hard-seeks to the server anchor. Playback rate is never adjusted.");

    /** Clamp on latency compensation in milliseconds (Stage 3). */
    public static final ConfigValue<Integer> SYNC_MAX_LATENCY_COMPENSATION_MS = SCHEMA.intRange(
            "sync.max_latency_compensation_ms", 500, 0, 2000, true,
            "Maximum latency compensation (ms) applied when scheduling against the server timeline anchor.");

    /** Harmonic Gate internal energy buffer capacity in NE (Stage 4). */
    public static final ConfigValue<Integer> GATE_ENERGY_CAPACITY = SCHEMA.intRange(
            "gate.energy_capacity", 16000, 1000, 1000000, true,
            "Energy buffer capacity (NE) of the Harmonic Gate. Applies to gates placed after a change.");

    /** Energy consumed per crossing into the Soundforge (Stage 4). Returning is always free. */
    public static final ConfigValue<Integer> GATE_TELEPORT_ENERGY_COST = SCHEMA.intRange(
            "gate.teleport_energy_cost", 8000, 0, 1000000, true,
            "Energy (NE) one crossing into the Soundforge consumes (clamped to the gate's capacity). Returning from the Soundforge is always free.");

    /** Serialised score budget in bytes, enforced at press time and on the wire (Stage 5). */
    public static final ConfigValue<Integer> DISK_SCORE_BUDGET_BYTES = SCHEMA.intRange(
            "disk.score_budget_bytes", 16384, 1024, 65536, true,
            "Hard cap in bytes on a serialised score. The Disk Press refuses over-budget scores with a named message; it never truncates.");

    /** Maximum disk/composition name length (Stage 5, locked decision 6). */
    public static final ConfigValue<Integer> DISK_NAME_MAX_LENGTH = SCHEMA.intRange(
            "disk.name_max_length", 48, 8, 128, true,
            "Maximum length of a player-chosen disk/composition name, validated server-side at press and publish time.");

    /**
     * Moderation word list for player-chosen names (Stage 5, locked
     * decision 6). Comma-separated, case-insensitive substrings; empty
     * disables the list.
     */
    public static final ConfigValue<String> MODERATION_BLOCKED_WORDS = SCHEMA.string(
            "moderation.blocked_words", "", true,
            "Comma-separated, case-insensitive words refused in player-chosen disk/composition names. Empty = no word list.");

    /** Library listing page size (Stage 6; paginated from day one). */
    public static final ConfigValue<Integer> LIBRARY_PAGE_SIZE = SCHEMA.intRange(
            "library.page_size", 50, 10, 100, true,
            "Page size for shared-library listings (library browsing is paginated from the first release).");

    /** Master publish toggle (Stage 6). */
    public static final ConfigValue<Boolean> PUBLISHING_ENABLED = SCHEMA.bool(
            "library.publishing_enabled", true, true,
            "Whether players may publish compositions to the shared library at all.");

    /** Server-wide cap on published library entries (Stage 6). */
    public static final ConfigValue<Integer> LIBRARY_SIZE_CAP = SCHEMA.intRange(
            "library.size_cap", 1000, 10, 100000, true,
            "Maximum number of published entries the shared library holds server-wide.");

    /** Per-player published-entry quota (Stage 6). */
    public static final ConfigValue<Integer> LIBRARY_PER_PLAYER_QUOTA = SCHEMA.intRange(
            "library.per_player_quota", 25, 1, 1000, true,
            "Maximum number of published entries a single player may hold in the shared library.");

    /** Moderation: op approval before a published disk becomes visible (Stage 6, default off). */
    public static final ConfigValue<Boolean> OP_APPROVAL_REQUIRED = SCHEMA.bool(
            "library.op_approval_required", false, true,
            "If true, a published disk only becomes visible in the library after an operator approves it.");

    /** Disk Exchanger machine toggle (Stage 6). */
    public static final ConfigValue<Boolean> EXCHANGER_ENABLED = SCHEMA.bool(
            "exchanger.enabled", true, true,
            "Whether the Disk Exchanger (library browsing + disk copying machine) is enabled.");

    /**
     * Price in credits of one Disk Exchanger library copy (0.4.0). Only
     * charged when NeroEconomy is installed; 0 makes copies free. Capped at
     * NeroEconomy's single-payment ceiling so the screen's data-slot readout
     * never clamps.
     */
    public static final ConfigValue<Integer> LIBRARY_COPY_PRICE = SCHEMA.intRange(
            "exchanger.library_copy_price", 25, 0, 1000, true,
            "Credits one Disk Exchanger library copy costs when NeroEconomy is installed (0 = free). The composer receives the price less NeroEconomy's transfer fee; copies of anonymous, erased-author or your own entries pay the whole price to the Reserve. Ignored without NeroEconomy.");

    /** Retention window in days for the inactive-player purge (Stage 7). 0 disables the sweep. */
    public static final ConfigValue<Integer> RETENTION_DAYS = SCHEMA.intRange(
            "data.retention_days", 365, 0, 3650, true,
            "Days of player inactivity after which NeroNotes purges that player's stored data (authorship, sessions, channel ownership). 0 disables the automatic purge.");

    /** Non-essential action logging (Stage 7). Off by default; per-player opt-out applies on top. */
    public static final ConfigValue<Boolean> ACTION_LOGGING_ENABLED = SCHEMA.bool(
            "data.action_logging_enabled", false, true,
            "Whether non-essential action logging (press/publish/download events) is enabled. Never logs player-authored strings at info level.");

    /** Companion (NeroLink) module master toggle (Stage 9). */
    public static final ConfigValue<Boolean> LINK_MODULE_ENABLED = SCHEMA.bool(
            "link.module_enabled", true, true,
            "Whether NeroNotes registers its companion (NeroLink) module. When false, companion clients see no NeroNotes data and can perform no NeroNotes actions.");

    // ------------------------------------------------------------------
    // Client-local keys (serverAuthoritative = false) — EXACTLY these:
    // telemetryEnabled, per-voice-family volume, glow intensity,
    // mute other players' bases.
    // ------------------------------------------------------------------

    /** Crash telemetry — on by default, client-local opt-out (never server-synced). See PRIVACY.md. */
    public static final ConfigValue<Boolean> TELEMETRY_ENABLED = SCHEMA.bool(
            "telemetryEnabled", true, false,
            "On by default: send anonymous, NeroNotes-only crash reports (Sentry, EU servers): stack trace and mod/MC/loader/OS/Java versions; no IP, username, UUID, world data, chat, composition titles, disk names or author names; file paths are scrubbed of your account name. Set false to opt out. See PRIVACY.md.");

    /** Per-voice-family client volume: deep bass. */
    public static final ConfigValue<Double> VOLUME_DEEP_BASS = SCHEMA.doubleRange(
            "client.volume.deep_bass", 1.0, 0.0, 1.0, false,
            "Client volume multiplier for the deep-bass voice family.");

    /** Per-voice-family client volume: sub pad. */
    public static final ConfigValue<Double> VOLUME_SUB_PAD = SCHEMA.doubleRange(
            "client.volume.sub_pad", 1.0, 0.0, 1.0, false,
            "Client volume multiplier for the sub-pad voice family.");

    /** Per-voice-family client volume: low drone. */
    public static final ConfigValue<Double> VOLUME_LOW_DRONE = SCHEMA.doubleRange(
            "client.volume.low_drone", 1.0, 0.0, 1.0, false,
            "Client volume multiplier for the low-drone voice family.");

    /** Per-voice-family client volume: high lead. */
    public static final ConfigValue<Double> VOLUME_HIGH_LEAD = SCHEMA.doubleRange(
            "client.volume.high_lead", 1.0, 0.0, 1.0, false,
            "Client volume multiplier for the high-lead voice family.");

    /** Per-voice-family client volume: glassy pluck. */
    public static final ConfigValue<Double> VOLUME_GLASSY_PLUCK = SCHEMA.doubleRange(
            "client.volume.glassy_pluck", 1.0, 0.0, 1.0, false,
            "Client volume multiplier for the glassy-pluck voice family.");

    /** Per-voice-family client volume: percussion. */
    public static final ConfigValue<Double> VOLUME_PERCUSSION = SCHEMA.doubleRange(
            "client.volume.percussion", 1.0, 0.0, 1.0, false,
            "Client volume multiplier for the percussion voice family.");

    /** Per-voice-family client volume: synth texture. */
    public static final ConfigValue<Double> VOLUME_SYNTH_TEXTURE = SCHEMA.doubleRange(
            "client.volume.synth_texture", 1.0, 0.0, 1.0, false,
            "Client volume multiplier for the synth-texture voice family.");

    /** Neon edge-light glow intensity (Stage 3 visuals). */
    public static final ConfigValue<Double> GLOW_INTENSITY = SCHEMA.doubleRange(
            "client.glow_intensity", 1.0, 0.0, 1.0, false,
            "Intensity of the neon edge-light flare on Resonant Blocks and Resonators.");

    /** Mute synced audio from bases owned by other players (Stage 3 anti-griefing). */
    public static final ConfigValue<Boolean> MUTE_OTHER_BASES = SCHEMA.bool(
            "client.mute_other_bases", false, false,
            "If true, this client mutes playback from channels owned by other players.");

    private NeroNotesConfig() {
    }

    /**
     * Register the schema with Core's {@code ConfigManager} — step 1 of
     * {@code NeroNotesCommon.init()}. Later stages only READ these values.
     */
    public static void register() {
        ConfigManager.register(SCHEMA);
    }
}
