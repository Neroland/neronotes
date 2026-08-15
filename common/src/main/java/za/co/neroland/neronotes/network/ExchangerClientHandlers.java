package za.co.neroland.neronotes.network;

import java.util.Objects;
import java.util.function.Consumer;

import za.co.neroland.neronotes.NeroNotesCommon;

/**
 * Client-side sink for incoming library-page payloads — the same shape as
 * {@link SequencerClientHandlers}: a debug-logging default replaced by the
 * real sink ({@code client/ClientExchangerState}) during client init. Loader
 * receivers hand payloads here on the client main thread.
 */
public final class ExchangerClientHandlers {

    private static volatile Consumer<LibraryPagePayload> pageSink = payload ->
            NeroNotesCommon.LOGGER.debug(
                    "[NeroNotes] client received library page {} for container {} ({} row(s); no exchanger sink installed)",
                    payload.page(), payload.containerId(), payload.entries().size());

    private ExchangerClientHandlers() {
    }

    /** Client init: attach the real page consumer. */
    public static void setPageSink(Consumer<LibraryPagePayload> sink) {
        pageSink = Objects.requireNonNull(sink, "page sink");
    }

    /** Entry point for the per-loader clientbound receivers (client main thread). */
    public static void handlePage(LibraryPagePayload payload) {
        pageSink.accept(payload);
    }
}
