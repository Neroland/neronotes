package za.co.neroland.neronotes.data;

import java.util.Optional;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
//? if <1.21.5 {
/*import net.minecraft.nbt.Tag;
*///?}

/**
 * Version seam for reading NBT. Minecraft 1.21.5 made the {@link CompoundTag} / {@link ListTag} getters
 * return {@link Optional} (or take a fallback); 1.21.1 - 1.21.4 return defaults for a missing or mistyped
 * key instead. Every read in this mod goes through here so the stored format and the "missing means
 * absent" semantics are identical on every Minecraft line. On 1.21.5 and later each method is a plain
 * delegate to the vanilla getter of the same meaning.
 */
public final class NbtCompat {

    private NbtCompat() {
    }

    public static Optional<ListTag> list(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getList(key);
        //?} else {
        /*return tag.contains(key, Tag.TAG_LIST) ? Optional.of((ListTag) tag.get(key)) : Optional.empty();
        *///?}
    }

    public static Optional<CompoundTag> compound(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getCompound(key);
        //?} else {
        /*return tag.contains(key, Tag.TAG_COMPOUND) ? Optional.of(tag.getCompound(key)) : Optional.empty();
        *///?}
    }

    public static Optional<CompoundTag> compound(ListTag list, int index) {
        //? if >=1.21.5 {
        return list.getCompound(index);
        //?} else {
        /*return index >= 0 && index < list.size() && list.get(index) instanceof CompoundTag compound
                ? Optional.of(compound) : Optional.empty();
        *///?}
    }

    public static Optional<String> string(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getString(key);
        //?} else {
        /*return tag.contains(key, Tag.TAG_STRING) ? Optional.of(tag.getString(key)) : Optional.empty();
        *///?}
    }

    public static Optional<String> string(ListTag list, int index) {
        //? if >=1.21.5 {
        return list.getString(index);
        //?} else {
        /*return index >= 0 && index < list.size() && list.get(index).getId() == Tag.TAG_STRING
                ? Optional.of(list.getString(index)) : Optional.empty();
        *///?}
    }

    public static Optional<Integer> intValue(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getInt(key);
        //?} else {
        /*return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? Optional.of(tag.getInt(key)) : Optional.empty();
        *///?}
    }

    public static Optional<Long> longValue(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getLong(key);
        //?} else {
        /*return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? Optional.of(tag.getLong(key)) : Optional.empty();
        *///?}
    }

    public static Optional<int[]> intArray(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getIntArray(key);
        //?} else {
        /*return tag.contains(key, Tag.TAG_INT_ARRAY) ? Optional.of(tag.getIntArray(key)) : Optional.empty();
        *///?}
    }

    public static Optional<byte[]> byteArray(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getByteArray(key);
        //?} else {
        /*return tag.contains(key, Tag.TAG_BYTE_ARRAY) ? Optional.of(tag.getByteArray(key)) : Optional.empty();
        *///?}
    }

    public static int getIntOr(CompoundTag tag, String key, int fallback) {
        //? if >=1.21.5 {
        return tag.getIntOr(key, fallback);
        //?} else {
        /*return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : fallback;
        *///?}
    }

    public static long getLongOr(CompoundTag tag, String key, long fallback) {
        //? if >=1.21.5 {
        return tag.getLongOr(key, fallback);
        //?} else {
        /*return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getLong(key) : fallback;
        *///?}
    }

    public static float getFloatOr(CompoundTag tag, String key, float fallback) {
        //? if >=1.21.5 {
        return tag.getFloatOr(key, fallback);
        //?} else {
        /*return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getFloat(key) : fallback;
        *///?}
    }

    public static double getDoubleOr(CompoundTag tag, String key, double fallback) {
        //? if >=1.21.5 {
        return tag.getDoubleOr(key, fallback);
        //?} else {
        /*return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getDouble(key) : fallback;
        *///?}
    }

    public static boolean getBooleanOr(CompoundTag tag, String key, boolean fallback) {
        //? if >=1.21.5 {
        return tag.getBooleanOr(key, fallback);
        //?} else {
        /*return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getBoolean(key) : fallback;
        *///?}
    }

    public static String getStringOr(CompoundTag tag, String key, String fallback) {
        //? if >=1.21.5 {
        return tag.getStringOr(key, fallback);
        //?} else {
        /*return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : fallback;
        *///?}
    }
}
