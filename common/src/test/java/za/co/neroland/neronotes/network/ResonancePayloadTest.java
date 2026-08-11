package za.co.neroland.neronotes.network;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import za.co.neroland.neronotes.signal.ChannelNames;
import za.co.neroland.neronotes.signal.TransportAction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The resonance payloads stay tiny and honest: exact wire round-trips, hard
 * field validation, and a byte-size ceiling proving note/transport events
 * never approach score territory (scores travel only through the
 * budget-guarded score path — {@code NotesNetwork.decodeScoreFromWire}).
 */
class ResonancePayloadTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** Generous ceiling for a "tiny" event payload — far below any score budget. */
    private static final int TINY_PAYLOAD_CEILING_BYTES = 128;

    @Test
    void notePayloadRoundTripsExactly() {
        ResonanceNotePayload payload = new ResonanceNotePayload(
                OWNER, "atrium", true, "neronotes:void_bass", 64, 100);
        ByteBuf buf = Unpooled.buffer();
        try {
            ResonanceNotePayload.STREAM_CODEC.encode(buf, payload);
            int size = buf.readableBytes();
            assertTrue(size <= TINY_PAYLOAD_CEILING_BYTES,
                    "note event must stay tiny, was " + size + " bytes");
            assertEquals(payload, ResonanceNotePayload.STREAM_CODEC.decode(buf));
            assertEquals(0, buf.readableBytes(), "decode consumed everything");
        } finally {
            buf.release();
        }
    }

    @Test
    void transportPayloadRoundTripsExactly() {
        for (TransportAction action : TransportAction.values()) {
            ResonanceTransportPayload payload = new ResonanceTransportPayload(
                    OWNER, "atrium", action, 1234L, 987654321L);
            ByteBuf buf = Unpooled.buffer();
            try {
                ResonanceTransportPayload.STREAM_CODEC.encode(buf, payload);
                int size = buf.readableBytes();
                assertTrue(size <= TINY_PAYLOAD_CEILING_BYTES,
                        "transport event must stay tiny, was " + size + " bytes");
                assertEquals(payload, ResonanceTransportPayload.STREAM_CODEC.decode(buf));
            } finally {
                buf.release();
            }
        }
    }

    @Test
    void notePayloadValidatesItsFields() {
        assertThrows(IllegalArgumentException.class, () -> new ResonanceNotePayload(
                OWNER, "a".repeat(ChannelNames.MAX_LENGTH + 1), true, "neronotes:void_bass", 64, 100));
        assertThrows(IllegalArgumentException.class, () -> new ResonanceNotePayload(
                OWNER, "atrium", true, "v".repeat(ResonanceNotePayload.MAX_VOICE_ID_LENGTH + 1), 64, 100));
        assertThrows(IllegalArgumentException.class, () -> new ResonanceNotePayload(
                OWNER, "atrium", true, "neronotes:void_bass", 128, 100));
        assertThrows(IllegalArgumentException.class, () -> new ResonanceNotePayload(
                OWNER, "atrium", true, "neronotes:void_bass", 64, -1));
        assertThrows(IllegalArgumentException.class, () -> new ResonanceNotePayload(
                null, "atrium", true, "neronotes:void_bass", 64, 100));
    }

    @Test
    void transportPayloadValidatesItsFields() {
        assertThrows(IllegalArgumentException.class, () -> new ResonanceTransportPayload(
                OWNER, "atrium", null, 0L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new ResonanceTransportPayload(
                OWNER, "atrium", TransportAction.PLAY, -1L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new ResonanceTransportPayload(
                OWNER, "", TransportAction.PLAY, 0L, 0L));
    }

    @Test
    void unknownTransportWireIdIsRejectedNotGuessed() {
        assertThrows(IllegalArgumentException.class, () -> TransportAction.fromWireId((byte) 99));
    }
}
