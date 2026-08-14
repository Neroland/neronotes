package za.co.neroland.neronotes.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.neronotes.item.DiskNames;

/**
 * Client → server: the Disk Press screen's press request — the typed title
 * and the attribution choice. The title is untrusted free text: it is
 * length-capped on the wire ({@link DiskNames#HARD_MAX_LENGTH}) and fully
 * validated server-side ({@code item/DiskNames}, config length cap +
 * blocked-word list) before anything is pressed. The anonymity flag is the
 * player's <strong>first-class opt-out of credit</strong>; the server never
 * infers it.
 *
 * @param containerId the open {@code DiskPressMenu}'s container id
 * @param title       the player-typed title (validated server-side)
 * @param anonymous   true = press without any author credit
 */
public record DiskPressPayload(int containerId, String title, boolean anonymous)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DiskPressPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    NotesNetwork.CHANNEL_NAMESPACE, "disk_press"));

    private static final StreamCodec<ByteBuf, String> TITLE_CODEC =
            ByteBufCodecs.stringUtf8(DiskNames.HARD_MAX_LENGTH);

    public static final StreamCodec<ByteBuf, DiskPressPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.containerId());
                TITLE_CODEC.encode(buf, payload.title());
                ByteBufCodecs.BOOL.encode(buf, payload.anonymous());
            },
            buf -> new DiskPressPayload(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    TITLE_CODEC.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)));

    public DiskPressPayload {
        if (title == null || title.length() > DiskNames.HARD_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "title must be non-null and at most " + DiskNames.HARD_MAX_LENGTH + " characters");
        }
    }

    @Override
    public CustomPacketPayload.Type<DiskPressPayload> type() {
        return TYPE;
    }
}
