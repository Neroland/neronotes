package za.co.neroland.neronotes.network;

import java.util.Objects;
import java.util.function.Consumer;

import za.co.neroland.neronotes.NeroNotesCommon;

/**
 * Client-side sinks for incoming resonance payloads. Stage 2 ships the
 * plumbing with debug-logging defaults; Stage 3's playback engine replaces
 * the sinks via the setters during client init. Loader receivers hand
 * payloads here on the client main thread.
 *
 * <p>The default sinks log only the payload kind and voice id — never the
 * channel name, which is player-authored free text.</p>
 */
public final class ResonanceClientHandlers {

    private static volatile Consumer<ResonanceNotePayload> noteSink = payload ->
            NeroNotesCommon.LOGGER.debug("[NeroNotes] client received {} for voice {} (no playback engine yet)",
                    payload.noteOn() ? "note_on" : "note_off", payload.voiceId());

    private static volatile Consumer<ResonanceTransportPayload> transportSink = payload ->
            NeroNotesCommon.LOGGER.debug("[NeroNotes] client received transport {} (no playback engine yet)",
                    payload.action());

    private ResonanceClientHandlers() {
    }

    /** Stage 3+: attach the real note renderer. */
    public static void setNoteSink(Consumer<ResonanceNotePayload> sink) {
        noteSink = Objects.requireNonNull(sink, "note sink");
    }

    /** Stage 3+: attach the real transport/sync engine. */
    public static void setTransportSink(Consumer<ResonanceTransportPayload> sink) {
        transportSink = Objects.requireNonNull(sink, "transport sink");
    }

    /** Entry point for the per-loader clientbound receivers (client main thread). */
    public static void handleNote(ResonanceNotePayload payload) {
        noteSink.accept(payload);
    }

    /** Entry point for the per-loader clientbound receivers (client main thread). */
    public static void handleTransport(ResonanceTransportPayload payload) {
        transportSink.accept(payload);
    }
}
