package za.co.neroland.neronotes.network;

//? if <1.20.5 {
/*import net.minecraft.network.FriendlyByteBuf;
import za.co.neroland.nerolandcore.network.legacy.LegacyStreamCodec;

final class LegacyNotesCodecs {
    static final LegacyStreamCodec<FriendlyByteBuf, Integer> VAR_INT =
            LegacyStreamCodec.of(FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt);
    static final LegacyStreamCodec<FriendlyByteBuf, Long> VAR_LONG =
            LegacyStreamCodec.of(FriendlyByteBuf::writeVarLong, FriendlyByteBuf::readVarLong);
    static final LegacyStreamCodec<FriendlyByteBuf, Boolean> BOOL =
            LegacyStreamCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean);

    static LegacyStreamCodec<FriendlyByteBuf, String> stringUtf8(int maximumLength) {
        return LegacyStreamCodec.of((buffer, value) -> buffer.writeUtf(value, maximumLength),
                buffer -> buffer.readUtf(maximumLength));
    }

    static LegacyStreamCodec<FriendlyByteBuf, byte[]> byteArray(int maximumLength) {
        return LegacyStreamCodec.of((buffer, value) -> {
            if (value.length > maximumLength) {
                throw new IllegalArgumentException("NeroNotes payload exceeds its byte limit");
            }
            buffer.writeByteArray(value);
        }, buffer -> buffer.readByteArray(maximumLength));
    }

    private LegacyNotesCodecs() {
    }
}
*///?}
