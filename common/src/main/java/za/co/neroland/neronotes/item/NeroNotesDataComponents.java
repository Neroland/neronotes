package za.co.neroland.neronotes.item;

//? if >=1.20.5 {
import net.minecraft.core.component.DataComponentType;
//?}
import net.minecraft.core.registries.Registries;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.neronotes.NeroNotesCommon;

/**
 * NeroNotes item data components — init step 4 alongside the items, via
 * Core's {@link RegistrationProvider} over the vanilla
 * {@code DATA_COMPONENT_TYPE} registry.
 *
 * <p>Only one component exists: {@code neronotes:disk_contents}, the pressed
 * disk's payload. Its persistent codec doubles as the network codec (vanilla
 * falls back to the persistent codec when no stream codec is given), and the
 * decode path refuses unreadable or newer-format scores through
 * {@link DiskContents#SCORE_CODEC} rather than guessing. Component data is
 * kept within the disk score budget because the score inside it was budget-
 * checked at press time — the component never stores anything unbounded.</p>
 */
public final class NeroNotesDataComponents {

    //? if >=1.20.5 {
    private static final RegistrationProvider<DataComponentType<?>> COMPONENTS =
            RegistrationProvider.get(Registries.DATA_COMPONENT_TYPE, NeroNotesCommon.MOD_ID);

    /** The pressed disk payload: score + title + authorship + label palette. */
    public static final RegistrationProvider.RegistryEntry<DataComponentType<DiskContents>> DISK_CONTENTS =
            COMPONENTS.register("disk_contents", key -> DataComponentType.<DiskContents>builder()
                    .persistent(DiskContents.CODEC)
                    .build());

    //?} else {
    /*private static final String DISK_CONTENTS_KEY = "neronotes:disk_contents";

    static DiskContents read(net.minecraft.world.item.ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(DISK_CONTENTS_KEY)) {
            return null;
        }
        return DiskContents.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get(DISK_CONTENTS_KEY))
                .result().orElse(null);
    }

    static void write(net.minecraft.world.item.ItemStack stack, DiskContents contents) {
        var encoded = DiskContents.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, contents)
                .result().orElseThrow(() -> new IllegalArgumentException("Cannot encode custom disk contents"));
        stack.getOrCreateTag().put(DISK_CONTENTS_KEY, encoded);
    }
    *///?}

    private NeroNotesDataComponents() {
    }

    /** Class-load hook — step 4 of {@code NeroNotesCommon.init()}. */
    public static void init() {
        NeroNotesCommon.LOGGER.debug("[NeroNotes] data components queued for registration");
    }
}
