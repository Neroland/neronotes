package za.co.neroland.neronotes.integration;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerolandcore.event.ThresholdEvents;
import za.co.neroland.neronotes.NeroNotesCommon;

/**
 * NeroNotes' Core {@code ThresholdEvents} crossings — the mod's public,
 * compile-edge-free integration surface (init step 10). Any Nero mod that
 * depends only on Core can listen: NeroQuests' {@code custom_event} objective
 * consumes exactly this contract, so "the server library reached its first
 * composition" becomes quest content with no dependency in either direction.
 *
 * <p><strong>Privacy (POPIA / GDPR) — load-bearing, not advisory:</strong> a
 * {@code ThresholdCrossing}'s {@code scope} identifies a <em>place or
 * system</em>, never a person. The channels below use the system key
 * {@value #SCOPE_LIBRARY} and a dimension id string; a player UUID or name
 * must never appear in a crossing. (Core's own javadoc states the same rule —
 * this class is where NeroNotes enforces it.)</p>
 *
 * <p>Both fire points are server-side code paths (the publish flow and the
 * server-tracked subscription map), satisfying Core's server-thread-only
 * contract. Crossing detection itself is the pure
 * {@link ThresholdCrossings#crossedRising} — rising edges only, once per
 * genuine crossing, never on an unchanged value.</p>
 */
public final class NotesThresholds {

    /**
     * Fired when the server-wide count of published compositions (the shared
     * library's entries, pending-approval ones included — publishing is the
     * author's act, approval only gates visibility) rises past a threshold.
     * Scope: {@value #SCOPE_LIBRARY}.
     */
    public static final Identifier COMPOSITIONS_PUBLISHED =
            Identifier.fromNamespaceAndPath(NeroNotesCommon.MOD_ID, "compositions_published");

    /**
     * Fired when one resonance channel's server-tracked listener count rises
     * past a threshold. Scope: the channel's <em>dimension id</em> (e.g.
     * {@code minecraft:overworld}) — a place key. The channel's owner UUID
     * and name are deliberately absent: a crossing that named its channel
     * would map bases and people.
     */
    public static final Identifier CHANNEL_LISTENERS =
            Identifier.fromNamespaceAndPath(NeroNotesCommon.MOD_ID, "channel_listeners");

    /** The system scope key for library-wide crossings. */
    public static final String SCOPE_LIBRARY = "library";

    /** Published-composition milestones (rising). */
    static final long[] PUBLISH_THRESHOLDS = {1, 10, 50, 100, 500, 1000};

    /** Per-channel listener milestones (rising). */
    static final long[] LISTENER_THRESHOLDS = {2, 5, 10, 25};

    private NotesThresholds() {
    }

    /**
     * Report a change in the library's total published count. Call from the
     * publish path (server thread) with the counts before and after the
     * mutation; unpublish/takedown lowers the count silently (no falling
     * crossings in 0.1.0) and a later re-crossing legitimately re-fires.
     */
    public static void publishedCountChanged(long previous, long current) {
        for (long threshold : ThresholdCrossings.crossedRising(previous, current, PUBLISH_THRESHOLDS)) {
            ThresholdEvents.fire(new ThresholdEvents.ThresholdCrossing(
                    COMPOSITIONS_PUBLISHED, SCOPE_LIBRARY, current, threshold, true));
        }
    }

    /**
     * Report a change in one channel's listener count. {@code dimensionId} is
     * the channel's dimension id string — the whole scope; nothing
     * channel- or owner-identifying is carried.
     */
    public static void listenerCountChanged(String dimensionId, long previous, long current) {
        for (long threshold : ThresholdCrossings.crossedRising(previous, current, LISTENER_THRESHOLDS)) {
            ThresholdEvents.fire(new ThresholdEvents.ThresholdCrossing(
                    CHANNEL_LISTENERS, dimensionId, current, threshold, true));
        }
    }
}
