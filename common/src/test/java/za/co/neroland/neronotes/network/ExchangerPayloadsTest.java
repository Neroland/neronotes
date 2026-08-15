package za.co.neroland.neronotes.network;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import za.co.neroland.neronotes.item.DiskNames;
import za.co.neroland.neronotes.library.LibraryTable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Stage 6 Exchanger payloads: the serverbound action is tiny and exact;
 * the clientbound library page is metadata-only (titles, display authors,
 * families, aggregate counts — <em>no scores, no UUIDs</em>) and hard-bounded
 * at {@link LibraryTable#MAX_PAGE_SIZE} rows so it can never balloon whatever
 * the library holds.
 */
class ExchangerPayloadsTest {

    private static final int TINY_PAYLOAD_CEILING_BYTES = 64;

    @Test
    void actionPayloadRoundTripsExactly() {
        for (ExchangerActionPayload payload : List.of(
                new ExchangerActionPayload(3, ExchangerActionPayload.Action.REQUEST_PAGE, 7),
                new ExchangerActionPayload(3, ExchangerActionPayload.Action.COPY, 42),
                new ExchangerActionPayload(3, ExchangerActionPayload.Action.DUPLICATE, 0))) {
            ByteBuf buf = Unpooled.buffer();
            try {
                ExchangerActionPayload.STREAM_CODEC.encode(buf, payload);
                assertTrue(buf.readableBytes() <= TINY_PAYLOAD_CEILING_BYTES);
                assertEquals(payload, ExchangerActionPayload.STREAM_CODEC.decode(buf));
                assertEquals(0, buf.readableBytes());
            } finally {
                buf.release();
            }
        }
    }

    @Test
    void actionPayloadRefusesNegativeValues() {
        assertThrows(IllegalArgumentException.class, () ->
                new ExchangerActionPayload(1, ExchangerActionPayload.Action.COPY, -1));
    }

    @Test
    void pagePayloadRoundTripsMetadataExactly() {
        List<LibraryPagePayload.PageEntry> rows = List.of(
                new LibraryPagePayload.PageEntry(1, "Starlight Over Neroland", "Dario", "high_lead", 12),
                new LibraryPagePayload.PageEntry(9, "Nameless Hum", "", "deep_bass", 0));
        LibraryPagePayload payload = new LibraryPagePayload(5, 2, 4, 180, rows);
        ByteBuf buf = Unpooled.buffer();
        try {
            LibraryPagePayload.STREAM_CODEC.encode(buf, payload);
            LibraryPagePayload decoded = LibraryPagePayload.STREAM_CODEC.decode(buf);
            assertEquals(payload, decoded);
            assertEquals(0, buf.readableBytes());
            // The anonymous row exposes no author whatsoever.
            assertTrue(decoded.entries().get(1).anonymous());
            assertEquals("", decoded.entries().get(1).authorDisplay());
        } finally {
            buf.release();
        }
    }

    @Test
    void pagePayloadIsHardBoundedAtTheMaxPageSize() {
        List<LibraryPagePayload.PageEntry> tooMany = new ArrayList<>();
        for (int i = 0; i <= LibraryTable.MAX_PAGE_SIZE; i++) {
            tooMany.add(new LibraryPagePayload.PageEntry(i, "Song " + i, "", "high_lead", 0));
        }
        assertThrows(IllegalArgumentException.class, () ->
                new LibraryPagePayload(1, 0, 1, tooMany.size(), tooMany));
        assertThrows(IllegalArgumentException.class, () -> new LibraryPagePayload.PageEntry(
                1, "x".repeat(DiskNames.HARD_MAX_LENGTH + 1), "", "high_lead", 0));
        assertThrows(IllegalArgumentException.class, () -> new LibraryPagePayload.PageEntry(
                1, "ok", "x".repeat(LibraryPagePayload.MAX_AUTHOR_LENGTH + 1), "high_lead", 0));
    }
}
