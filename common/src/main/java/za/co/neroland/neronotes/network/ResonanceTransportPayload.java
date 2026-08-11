package za.co.neroland.neronotes.network;

import java.util.UUID;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.neronotes.signal.ChannelNames;
import za.co.neroland.neronotes.signal.TransportAction;

/**
 * Server → client: a transport event ({@code play} / {@code stop} /
 * {@code seek}) on a channel. Tiny by design — no score data ever.
 *
 * <p>This payload IS the server's timeline anchor from locked design
 * decision 4 — {@code (channel, trackId, startGameTick)}:
 * {@code anchorGameTick} is the server game tick at which the transport
 * state took effect, {@code positionTick} the score position in score ticks
 * at that anchor, and {@code tempoBpm}/{@code ticksPerBeat} convert score
 * ticks to time so the client can run a local playhead against the anchor.
 * Clients only ever schedule or hard-seek against it; the server owns the
 * timeline. {@code stop} carries no tempo (zeros).</p>
 *
 * @param owner          channel owner (part of the channel identity)
 * @param channelName    channel display name
 * @param action         play / stop / seek
 * @param positionTick   score position (score ticks, ≥ 0) at the anchor
 * @param anchorGameTick server game tick the transport state took effect (≥ 0)
 * @param trackId        server-assigned id of the playing track ({@code 0} =
 *                       unset; real disk ids arrive with Stage 5)
 * @param tempoBpm       score tempo, or {@code 0} when not applicable (stop)
 * @param ticksPerBeat   score tick resolution, or {@code 0} (stop)
 */
public record ResonanceTransportPayload(UUID owner, String channelName, TransportAction action,
                                        long positionTick, long anchorGameTick, int trackId,
                                        int tempoBpm, int ticksPerBeat)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ResonanceTransportPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    NotesNetwork.CHANNEL_NAMESPACE, "resonance_transport"));

    private static final StreamCodec<ByteBuf, String> CHANNEL_NAME_CODEC =
            ByteBufCodecs.stringUtf8(ChannelNames.MAX_LENGTH);

    /** Hand-rolled, bounded element codecs — see the note payload for why not {@code composite}. */
    public static final StreamCodec<ByteBuf, ResonanceTransportPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, payload.owner());
                CHANNEL_NAME_CODEC.encode(buf, payload.channelName());
                buf.writeByte(payload.action().wireId());
                ByteBufCodecs.VAR_LONG.encode(buf, payload.positionTick());
                ByteBufCodecs.VAR_LONG.encode(buf, payload.anchorGameTick());
                ByteBufCodecs.VAR_INT.encode(buf, payload.trackId());
                ByteBufCodecs.VAR_INT.encode(buf, payload.tempoBpm());
                ByteBufCodecs.VAR_INT.encode(buf, payload.ticksPerBeat());
            },
            buf -> {
                UUID owner = UUIDUtil.STREAM_CODEC.decode(buf);
                String channelName = CHANNEL_NAME_CODEC.decode(buf);
                TransportAction action = TransportAction.fromWireId(buf.readByte());
                long positionTick = ByteBufCodecs.VAR_LONG.decode(buf);
                long anchorGameTick = ByteBufCodecs.VAR_LONG.decode(buf);
                int trackId = ByteBufCodecs.VAR_INT.decode(buf);
                int tempoBpm = ByteBufCodecs.VAR_INT.decode(buf);
                int ticksPerBeat = ByteBufCodecs.VAR_INT.decode(buf);
                return new ResonanceTransportPayload(owner, channelName, action,
                        positionTick, anchorGameTick, trackId, tempoBpm, ticksPerBeat);
            });

    public ResonanceTransportPayload {
        if (owner == null) {
            throw new IllegalArgumentException("owner must not be null");
        }
        if (channelName == null || channelName.isBlank() || channelName.length() > ChannelNames.MAX_LENGTH) {
            throw new IllegalArgumentException("channelName must be 1.." + ChannelNames.MAX_LENGTH + " characters");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (positionTick < 0) {
            throw new IllegalArgumentException("positionTick (" + positionTick + ") must be >= 0");
        }
        if (anchorGameTick < 0) {
            throw new IllegalArgumentException("anchorGameTick (" + anchorGameTick + ") must be >= 0");
        }
        if (trackId < 0) {
            throw new IllegalArgumentException("trackId (" + trackId + ") must be >= 0");
        }
        if (tempoBpm < 0 || ticksPerBeat < 0) {
            throw new IllegalArgumentException("tempoBpm/ticksPerBeat must be >= 0 (0 = not applicable)");
        }
    }

    @Override
    public CustomPacketPayload.Type<ResonanceTransportPayload> type() {
        return TYPE;
    }
}
