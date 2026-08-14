package za.co.neroland.neronotes.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.neronotes.soundforge.SequencerEdit;

/**
 * Client → server: one bounded sequencer edit from the transport-lectern
 * screen. <strong>The client only proposes</strong> — the server validates
 * the open menu, the Soundforge location and every bound in
 * {@code soundforge/SessionEditor} before anything is applied, then echoes
 * the authoritative session back. A few dozen bytes at most: an op ordinal,
 * three var-ints and a capped voice id.
 *
 * @param containerId the open {@code SequencerMenu}'s container id (matched
 *                    server-side against {@code player.containerMenu})
 * @param edit        the proposed edit
 */
public record SequencerEditPayload(int containerId, SequencerEdit edit) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SequencerEditPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    NotesNetwork.CHANNEL_NAMESPACE, "sequencer_edit"));

    private static final StreamCodec<ByteBuf, String> VOICE_ID_CODEC =
            ByteBufCodecs.stringUtf8(SequencerEdit.MAX_VOICE_ID_LENGTH);

    private static final SequencerEdit.Op[] OPS = SequencerEdit.Op.values();

    public static final StreamCodec<ByteBuf, SequencerEditPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.containerId());
                ByteBufCodecs.VAR_INT.encode(buf, payload.edit().op().ordinal());
                ByteBufCodecs.VAR_INT.encode(buf, payload.edit().a());
                ByteBufCodecs.VAR_INT.encode(buf, payload.edit().b());
                ByteBufCodecs.VAR_INT.encode(buf, payload.edit().c());
                VOICE_ID_CODEC.encode(buf, payload.edit().voiceId());
            },
            buf -> {
                int containerId = ByteBufCodecs.VAR_INT.decode(buf);
                int opOrdinal = ByteBufCodecs.VAR_INT.decode(buf);
                if (opOrdinal < 0 || opOrdinal >= OPS.length) {
                    throw new IllegalArgumentException("unknown sequencer op ordinal " + opOrdinal);
                }
                int a = ByteBufCodecs.VAR_INT.decode(buf);
                int b = ByteBufCodecs.VAR_INT.decode(buf);
                int c = ByteBufCodecs.VAR_INT.decode(buf);
                String voiceId = VOICE_ID_CODEC.decode(buf);
                return new SequencerEditPayload(containerId, new SequencerEdit(OPS[opOrdinal], a, b, c, voiceId));
            });

    public SequencerEditPayload {
        if (edit == null) {
            throw new IllegalArgumentException("edit must not be null");
        }
    }

    @Override
    public CustomPacketPayload.Type<SequencerEditPayload> type() {
        return TYPE;
    }
}
