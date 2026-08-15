package za.co.neroland.neronotes.link;

import java.util.List;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerolandcore.link.LinkModuleInfo;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.platform.Services;
import za.co.neroland.neronotes.telemetry.NeroNotesTelemetry;

/**
 * NeroNotes' NeroLink module — the declaration half. Every section id, action
 * id and event topic the mod exposes is a named constant here, and
 * {@link #init()} is the single registration entry point that
 * {@code NeroNotesCommon.init()} calls LAST (step 11).
 *
 * <p>NeroNotes backs the companion app's "music" screen: the shared library
 * of published compositions, the requester's own carried disks, their own
 * resonance channels, and what is playing right now.</p>
 *
 * <p><b>POPIA/GDPR — the visibility rule.</b> Every section is scoped to the
 * <em>requesting</em> player's UUID and nothing else:</p>
 * <ul>
 *   <li>{@link #SECTION_LIBRARY} is the shared library — public by design in
 *       the game itself — but anonymous entries carry <b>no author field at
 *       all</b> (not an empty one), exactly like every in-game surface.</li>
 *   <li>{@link #SECTION_DISKS} lists the pressed disks the requester carries.
 *       Inventories exist only while a player is online, so offline the
 *       section answers an empty list with {@code player_online: false} —
 *       the honest trade, not a defect.</li>
 *   <li>{@link #SECTION_CHANNELS} and {@link #SECTION_NOW_PLAYING} answer
 *       ONLY channels the requester owns or is trusted on. There is no
 *       server-wide channel roster on any path — one would map every base on
 *       the server. Trusted channels are referenced by an opaque one-way id;
 *       their owners' UUIDs never leave the server.</li>
 * </ul>
 *
 * <p>Actions are {@link #ACTION_PLAY} and {@link #ACTION_STOP} on the
 * requester's own (owned/trusted) channels — nothing creates, publishes,
 * renames or deletes anything: publishing is a committal in-world decision,
 * not an API call. There is deliberately <b>no {@code skip}</b>: 0.1.0 has no
 * playlists or queues, so "skip" has nothing honest to skip to (a Resonator
 * plays one disk); it can arrive with playlists in a later release.
 * Operator status is never honoured over the bridge.</p>
 *
 * @see NotesLinkSnapshots
 * @see NotesLinkActions
 * @see NotesLinkEvents
 */
public final class NotesLinkModule {

    /** The link module id — the same string as the mod id, as the ecosystem convention requires. */
    public static final String MODULE_ID = NeroNotesCommon.MOD_ID;

    /** The snapshot schema revision. Bump on any change to a section's shape. */
    public static final int SCHEMA_VERSION = 1;

    /** The shared library of published compositions (public by design; anonymous entries name nobody). */
    public static final String SECTION_LIBRARY = "library";
    /** The pressed disks the ONLINE requester carries. Empty (with {@code player_online: false}) offline. */
    public static final String SECTION_DISKS = "disks";
    /** Only channels the requester owns or is trusted on. Never a roster. */
    public static final String SECTION_CHANNELS = "channels";
    /** The currently playing subset of those same channels. */
    public static final String SECTION_NOW_PLAYING = "now_playing";

    /** Start the Resonator(s) bound to one of the requester's own channels. Owner/trust-gated. */
    public static final String ACTION_PLAY = "play";
    /** Stop the Resonator(s) bound to one of the requester's own channels. Owner/trust-gated. */
    public static final String ACTION_STOP = "stop";

    /** Owner-scoped: one of the owner's channels started or stopped playing. */
    public static final String TOPIC_NOW_PLAYING = "now_playing";
    /** BROADCAST: the shared library changed. Carries counts only — no titles, no authors. */
    public static final String TOPIC_LIBRARY = "library";

    private NotesLinkModule() {
    }

    /**
     * Registers the snapshot provider and the action handler with Core's link
     * registry, and arms the event publisher. Wrapped end to end: a link
     * module that cannot register must never take NeroNotes down with it —
     * the worst outcome is a companion app that reports NeroNotes as absent.
     */
    public static void init() {
        try {
            if (!NotesLinkAccess.enabled()) {
                NeroNotesCommon.LOGGER.info(
                        "[NeroNotes] The NeroLink module is disabled by config; companion clients will "
                                + "not see NeroNotes data.");
                return;
            }
            LinkModuleInfo info = new LinkModuleInfo(MODULE_ID, modVersion(), SCHEMA_VERSION,
                    List.of(SECTION_LIBRARY, SECTION_DISKS, SECTION_CHANNELS, SECTION_NOW_PLAYING),
                    List.of(ACTION_PLAY, ACTION_STOP));
            // One provider and one handler cover the whole module; Core keys both on the module id.
            NeroLinkRegistry.registerSnapshotProvider(new NotesLinkSnapshots(), info);
            NeroLinkRegistry.registerActionHandler(new NotesLinkActions(), info);
            NotesLinkEvents.init();
            NeroNotesCommon.LOGGER.info("[NeroNotes] NeroLink module registered (schema v{}).",
                    SCHEMA_VERSION);
        } catch (RuntimeException | LinkageError e) {
            // LinkageError too: an older Neroland Core without the link package would otherwise abort
            // mod construction with NoClassDefFoundError rather than merely losing the companion surface.
            NeroNotesCommon.LOGGER.warn(
                    "[NeroNotes] Could not register the NeroLink module; companion clients will not see "
                            + "NeroNotes data. Channels, playback and the library are unaffected.", e);
            if (e instanceof RuntimeException runtime) {
                NeroNotesTelemetry.captureHandled("link", "register", runtime);
            }
        }
    }

    /**
     * Captures the running server so snapshots and actions can resolve players
     * and SavedData. Core's SPI hands a provider nothing but a UUID, so the
     * module needs its own handle; each loader's server-tick hook calls this
     * beside the retention-sweep driver. Cheap (one volatile write) and
     * idempotent.
     */
    public static void rememberServer(MinecraftServer runningServer) {
        NotesLinkAccess.rememberServer(runningServer);
    }

    /** This mod's public version string for discovery, or {@code "unknown"} if the seam is unhappy. */
    private static String modVersion() {
        try {
            String version = Services.platform().getModVersion();
            return version == null || version.isBlank() ? "unknown" : version;
        } catch (RuntimeException e) {
            return "unknown";
        }
    }
}
