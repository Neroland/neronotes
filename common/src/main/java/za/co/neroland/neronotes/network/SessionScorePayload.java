package za.co.neroland.neronotes.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: the authoritative sequencer session state for an open
 * {@code SequencerMenu} — sent on open and after every applied (or refused)
 * edit, so the client always re-renders from what the server actually holds.
 *
 * <p><strong>This is a score-carrying payload</strong>, so both rules from
 * Stage 1 apply: the byte array is bounded by
 * {@link NotesNetwork#MAX_SCORE_PAYLOAD_BYTES} in the codec AND the
 * constructor, and the receiving client decodes exclusively through
 * {@link NotesNetwork#decodeScoreFromWire(byte[])} (budget checked before any
 * NBT parsing).</p>
 *
 * @param containerId the menu instance this state belongs to
 * @param activeLayer the session's active layer index
 * @param scoreBytes  the serialised session score (uncompressed NBT)
 */
public record SessionScorePayload(int containerId, int activeLayer, byte[] scoreBytes)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SessionScorePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    NotesNetwork.CHANNEL_NAMESPACE, "session_score"));

    public static final StreamCodec<ByteBuf, SessionScorePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.containerId());
                ByteBufCodecs.VAR_INT.encode(buf, payload.activeLayer());
                ByteBufCodecs.byteArray(NotesNetwork.MAX_SCORE_PAYLOAD_BYTES).encode(buf, payload.scoreBytes());
            },
            buf -> new SessionScorePayload(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.byteArray(NotesNetwork.MAX_SCORE_PAYLOAD_BYTES).decode(buf)));

    public SessionScorePayload {
        if (scoreBytes == null || scoreBytes.length > NotesNetwork.MAX_SCORE_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("scoreBytes must be non-null and at most "
                    + NotesNetwork.MAX_SCORE_PAYLOAD_BYTES + " bytes");
        }
        if (activeLayer < 0) {
            throw new IllegalArgumentException("activeLayer must be >= 0");
        }
    }

    @Override
    public CustomPacketPayload.Type<SessionScorePayload> type() {
        return TYPE;
    }
}
