package za.co.neroland.neronotes.network;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import za.co.neroland.neronotes.item.DiskNames;
import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.score.ScoreCodec;
import za.co.neroland.neronotes.score.ScoreFormatException;
import za.co.neroland.neronotes.soundforge.SequencerEdit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Stage 5 payloads: tiny, bounded, exact round-trips — and the session
 * sync (the one score-carrying payload) is enforceably capped at the same
 * ceiling as the disk budget, decoding only through
 * {@link NotesNetwork#decodeScoreFromWire}.
 */
class SequencerPayloadsTest {

    /** Generous ceiling for the tiny control payloads. */
    private static final int TINY_PAYLOAD_CEILING_BYTES = 192;

    @Test
    void editPayloadRoundTripsExactly() {
        SequencerEditPayload payload = new SequencerEditPayload(7,
                SequencerEdit.toggleNote(2, 123, 64));
        ByteBuf buf = Unpooled.buffer();
        try {
            SequencerEditPayload.STREAM_CODEC.encode(buf, payload);
            assertTrue(buf.readableBytes() <= TINY_PAYLOAD_CEILING_BYTES);
            assertEquals(payload, SequencerEditPayload.STREAM_CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    @Test
    void editPayloadCarriesVoiceIdsBounded() {
        SequencerEditPayload payload = new SequencerEditPayload(1,
                SequencerEdit.setLayerVoice(0, "neronotes:crystal_pluck"));
        ByteBuf buf = Unpooled.buffer();
        try {
            SequencerEditPayload.STREAM_CODEC.encode(buf, payload);
            assertEquals(payload, SequencerEditPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
        assertThrows(IllegalArgumentException.class, () -> new SequencerEdit(
                SequencerEdit.Op.ADD_LAYER, 0, 0, 0,
                "x".repeat(SequencerEdit.MAX_VOICE_ID_LENGTH + 1)));
    }

    @Test
    void pressPayloadRoundTripsAndCapsTheTitle() {
        DiskPressPayload payload = new DiskPressPayload(3, "Starlight Over Neroland", true);
        ByteBuf buf = Unpooled.buffer();
        try {
            DiskPressPayload.STREAM_CODEC.encode(buf, payload);
            assertTrue(buf.readableBytes() <= TINY_PAYLOAD_CEILING_BYTES);
            assertEquals(payload, DiskPressPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
        assertThrows(IllegalArgumentException.class, () -> new DiskPressPayload(
                3, "x".repeat(DiskNames.HARD_MAX_LENGTH + 1), false));
    }

    @Test
    void sessionPayloadRoundTripsAndDecodesThroughTheGuardedPath() throws ScoreFormatException {
        Score score = new Score(Score.CURRENT_FORMAT_VERSION, 120, 4, 0, 0, List.of(
                new Score.Layer("neronotes:void_bass", List.of(new Score.Note(0, 40, 100, 2)))));
        byte[] bytes = ScoreCodec.toBytes(score);
        SessionScorePayload payload = new SessionScorePayload(9, 0, bytes);
        ByteBuf buf = Unpooled.buffer();
        try {
            SessionScorePayload.STREAM_CODEC.encode(buf, payload);
            SessionScorePayload decoded = SessionScorePayload.STREAM_CODEC.decode(buf);
            assertEquals(payload.containerId(), decoded.containerId());
            assertEquals(payload.activeLayer(), decoded.activeLayer());
            assertArrayEquals(bytes, decoded.scoreBytes());
            // The mandatory decode path for score bytes off the wire.
            assertEquals(score, NotesNetwork.decodeScoreFromWire(decoded.scoreBytes()));
        } finally {
            buf.release();
        }
    }

    @Test
    void sessionPayloadRefusesAnythingOverTheWireCeiling() {
        byte[] oversized = new byte[NotesNetwork.MAX_SCORE_PAYLOAD_BYTES + 1];
        assertThrows(IllegalArgumentException.class, () -> new SessionScorePayload(1, 0, oversized));
    }
}
