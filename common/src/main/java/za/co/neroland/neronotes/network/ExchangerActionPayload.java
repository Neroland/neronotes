package za.co.neroland.neronotes.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: one Disk Exchanger request. Three tiny actions — request a
 * library page, copy a chosen entry onto a blank disk, duplicate the disk in
 * the source slot. <strong>No score ever travels serverbound</strong> (and
 * none travels clientbound for the Exchanger either): a copy names an entry
 * id and the server writes the disk from its own stored bytes. The server
 * re-validates everything against the player's <em>open</em> menu; the
 * payload asserts nothing.
 *
 * @param containerId the open {@code DiskExchangerMenu}'s container id
 * @param action      what the player asked for
 * @param value       {@code REQUEST_PAGE}: the zero-based page;
 *                    {@code COPY}: the library entry id; {@code DUPLICATE}: ignored
 */
public record ExchangerActionPayload(int containerId, Action action, int value)
        implements CustomPacketPayload {

    /** The Exchanger's request kinds. */
    public enum Action {
        /** Send me page {@code value} of the library listing. */
        REQUEST_PAGE,
        /** Copy library entry {@code value} onto the blank disk in my open menu. */
        COPY,
        /** Duplicate the disk in my open menu's source slot onto a blank disk. */
        DUPLICATE;

        static Action byOrdinal(int ordinal) {
            Action[] all = values();
            if (ordinal < 0 || ordinal >= all.length) {
                throw new IllegalArgumentException("unknown exchanger action: " + ordinal);
            }
            return all[ordinal];
        }
    }

    public static final CustomPacketPayload.Type<ExchangerActionPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    NotesNetwork.CHANNEL_NAMESPACE, "exchanger_action"));

    public static final StreamCodec<ByteBuf, ExchangerActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.containerId());
                ByteBufCodecs.VAR_INT.encode(buf, payload.action().ordinal());
                ByteBufCodecs.VAR_INT.encode(buf, payload.value());
            },
            buf -> new ExchangerActionPayload(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    Action.byOrdinal(ByteBufCodecs.VAR_INT.decode(buf)),
                    ByteBufCodecs.VAR_INT.decode(buf)));

    public ExchangerActionPayload {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (value < 0) {
            throw new IllegalArgumentException("value must be >= 0");
        }
    }

    @Override
    public CustomPacketPayload.Type<ExchangerActionPayload> type() {
        return TYPE;
    }
}
