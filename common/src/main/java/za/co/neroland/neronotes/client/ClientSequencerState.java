package za.co.neroland.neronotes.client;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.network.NotesNetwork;
import za.co.neroland.neronotes.network.SequencerClientHandlers;
import za.co.neroland.neronotes.network.SessionScorePayload;
import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.score.ScoreFormatException;
import za.co.neroland.neronotes.telemetry.NeroNotesTelemetry;

/**
 * The client's copy of the sequencer session being edited — written only by
 * incoming {@code SessionScorePayload}s (the server's authoritative echo) and
 * read by {@code SequencerScreen} to render the note grid. The client never
 * edits this directly; it proposes edits and waits for the echo.
 *
 * <p>Installed as the {@link SequencerClientHandlers} sink from each loader's
 * client entry point, alongside {@link ClientPlaybackEngine}. Decoding goes
 * through {@link NotesNetwork#decodeScoreFromWire(byte[])} — the mandatory,
 * budget-checked decode path for every score off the wire.</p>
 */
public final class ClientSequencerState {

    private record Entry(int containerId, Score score, int activeLayer) {
    }

    @Nullable
    private static volatile Entry latest;

    private ClientSequencerState() {
    }

    /** Client init: attach as the sequencer session sink. */
    public static void install() {
        SequencerClientHandlers.setSessionSink(ClientSequencerState::accept);
        NeroNotesCommon.LOGGER.debug("[NeroNotes] client sequencer state installed");
    }

    private static void accept(SessionScorePayload payload) {
        try {
            Score score = NotesNetwork.decodeScoreFromWire(payload.scoreBytes());
            latest = new Entry(payload.containerId(), score, payload.activeLayer());
        } catch (ScoreFormatException unreadable) {
            NeroNotesTelemetry.captureHandled("sequencer", "client_decode", unreadable);
        }
    }

    /** The session score for an open menu instance, if the server has synced one. */
    public static Optional<Score> scoreFor(int containerId) {
        Entry entry = latest;
        return entry != null && entry.containerId() == containerId
                ? Optional.of(entry.score()) : Optional.empty();
    }

    /** The synced active layer for a menu instance, or {@code fallback}. */
    public static int activeLayerFor(int containerId, int fallback) {
        Entry entry = latest;
        return entry != null && entry.containerId() == containerId ? entry.activeLayer() : fallback;
    }

    /** Drop the cached state (screen closed / disconnect). */
    public static void clear() {
        latest = null;
    }
}
