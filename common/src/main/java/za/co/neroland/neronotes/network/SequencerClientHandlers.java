package za.co.neroland.neronotes.network;

import java.util.Objects;
import java.util.function.Consumer;

import za.co.neroland.neronotes.NeroNotesCommon;

/**
 * Client-side sink for incoming sequencer-session payloads — the same shape
 * as {@link ResonanceClientHandlers}: a debug-logging default replaced by the
 * real sink ({@code client/ClientSequencerState}) during client init. Loader
 * receivers hand payloads here on the client main thread.
 */
public final class SequencerClientHandlers {

    private static volatile Consumer<SessionScorePayload> sessionSink = payload ->
            NeroNotesCommon.LOGGER.debug(
                    "[NeroNotes] client received session state for container {} ({} bytes; no sequencer sink installed)",
                    payload.containerId(), payload.scoreBytes().length);

    private SequencerClientHandlers() {
    }

    /** Client init: attach the real session-state consumer. */
    public static void setSessionSink(Consumer<SessionScorePayload> sink) {
        sessionSink = Objects.requireNonNull(sink, "session sink");
    }

    /** Entry point for the per-loader clientbound receivers (client main thread). */
    public static void handleSession(SessionScorePayload payload) {
        sessionSink.accept(payload);
    }
}
