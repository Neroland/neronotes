package za.co.neroland.neronotes.link;

import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerolandcore.link.LinkEvent;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.library.LibraryStore;
import za.co.neroland.neronotes.signal.ChannelKey;

/**
 * NeroNotes' live half of the NeroLink module: the state changes worth
 * pushing to a companion client rather than waiting for it to poll.
 *
 * <p>Scoping follows the ecosystem rule.
 * {@link NotesLinkModule#TOPIC_NOW_PLAYING} is the channel <em>owner's</em>
 * own state and goes only to that owner ({@link LinkEvent#forPlayer} with the
 * owner's UUID — the relay routes it to their sessions and nobody else's).
 * {@link NotesLinkModule#TOPIC_LIBRARY} is a broadcast, so it carries the
 * absolute minimum that still means something: <b>counts only</b> — no
 * titles, no authors, no entry ids. A listener learns that the shared
 * library moved, and nothing about whose work moved it.</p>
 *
 * <p><b>POPIA/GDPR.</b> No player data is held here at all — publications are
 * fired straight from the gameplay paths (the resonance transport choke point
 * and the library mutation sites) with no per-player bookkeeping, so there is
 * nothing for the erasure hook to clear. Warnings log a topic, never who an
 * event was for.</p>
 */
public final class NotesLinkEvents {

    private NotesLinkEvents() {
    }

    /**
     * Intentionally empty. NeroNotes publishes from the gameplay code paths
     * themselves ({@code ResonanceService.applyTransport} for playback
     * transitions; {@code LibraryService} and the library commands for
     * library changes), so there is nothing to subscribe. This exists so the
     * module has the same three-surface shape as every other Nero mod and has
     * one obvious place to grow.
     */
    static void init() {
    }

    // --- now_playing (owner-scoped) ----------------------------------------

    /**
     * Called from {@code ResonanceService.applyTransport} on genuine playback
     * transitions only (started when it was not playing; stopped when it
     * was) — a periodic re-anchor or seek never fires this. The event goes to
     * the channel OWNER, whoever's transport request caused the transition
     * (an owner should learn that a trusted friend — or their own Resonator
     * re-arming after a restart — started their base playing).
     */
    public static void nowPlayingChanged(ChannelKey key, boolean playing, int subscribers) {
        if (key == null || !NotesLinkAccess.enabled()) {
            return;
        }
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("schema_version", NotesLinkModule.SCHEMA_VERSION);
            payload.addProperty("channel", NotesLinkAccess.channelRef(key));
            payload.addProperty("name", key.name());
            payload.addProperty("dimension", key.dimension());
            payload.addProperty("playing", playing);
            payload.addProperty("subscribers", subscribers);
            payload.addProperty("timestamp", System.currentTimeMillis());
            publish(LinkEvent.forPlayer(NotesLinkModule.MODULE_ID,
                    NotesLinkModule.TOPIC_NOW_PLAYING, key.owner(), payload));
        } catch (RuntimeException e) {
            warn(NotesLinkModule.TOPIC_NOW_PLAYING, e);
        }
    }

    // --- library (BROADCAST, counts only) ----------------------------------

    /**
     * The shared library changed (publish, unpublish, operator takedown or
     * approval). This is the one broadcast in the module, so the payload is
     * deliberately anaemic: the visible and total entry counts. No titles, no
     * authors, no entry ids — player-chosen text and authorship never ride a
     * broadcast.
     */
    public static void libraryChanged(@Nullable MinecraftServer server) {
        if (server == null || !NotesLinkAccess.enabled()) {
            return;
        }
        try {
            LibraryStore store = LibraryStore.get(server);
            JsonObject payload = new JsonObject();
            payload.addProperty("schema_version", NotesLinkModule.SCHEMA_VERSION);
            payload.addProperty("visible_count", store.visibleCount());
            payload.addProperty("total_count", store.totalCount());
            payload.addProperty("timestamp", System.currentTimeMillis());
            publish(LinkEvent.broadcast(NotesLinkModule.MODULE_ID,
                    NotesLinkModule.TOPIC_LIBRARY, payload));
        } catch (RuntimeException e) {
            warn(NotesLinkModule.TOPIC_LIBRARY, e);
        }
    }

    // --- plumbing ----------------------------------------------------------

    /** Publish to Core's shared bus; a failure there is logged, never thrown at the gameplay caller. */
    private static void publish(LinkEvent event) {
        try {
            NeroLinkRegistry.eventBus().publish(event);
        } catch (RuntimeException e) {
            warn(event.topic(), e);
        }
    }

    /** Topic only — never who the event was for (POPIA/GDPR). */
    private static void warn(String topic, RuntimeException e) {
        NeroNotesCommon.LOGGER.warn("[NeroNotes] Publishing the NeroLink '{}' event failed.", topic, e);
    }
}
