package za.co.neroland.neronotes.client;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.network.ExchangerClientHandlers;
import za.co.neroland.neronotes.network.LibraryPagePayload;

/**
 * The client's copy of the last shared-library page the server sent — written
 * only by incoming {@code LibraryPagePayload}s (metadata only: titles,
 * display authors, families, aggregate counts — never a score, never a UUID)
 * and read by {@code DiskExchangerScreen} to render the listing. The client
 * never edits this; it requests pages and waits for the echo.
 *
 * <p>Installed as the {@link ExchangerClientHandlers} sink from each loader's
 * client entry point, alongside {@link ClientSequencerState}.</p>
 */
public final class ClientExchangerState {

    @Nullable
    private static volatile LibraryPagePayload latest;

    private ClientExchangerState() {
    }

    /** Client init: attach as the library page sink. */
    public static void install() {
        ExchangerClientHandlers.setPageSink(ClientExchangerState::accept);
        NeroNotesCommon.LOGGER.debug("[NeroNotes] client exchanger state installed");
    }

    private static void accept(LibraryPagePayload payload) {
        latest = payload;
    }

    /** The last synced page for an open menu instance, if any. */
    public static Optional<LibraryPagePayload> pageFor(int containerId) {
        LibraryPagePayload payload = latest;
        return payload != null && payload.containerId() == containerId
                ? Optional.of(payload) : Optional.empty();
    }

    /** Drop the cached state (screen closed / disconnect). */
    public static void clear() {
        latest = null;
    }
}
