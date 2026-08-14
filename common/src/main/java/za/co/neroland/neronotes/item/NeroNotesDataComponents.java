package za.co.neroland.neronotes.item;

import net.minecraft.core.component.DataComponentType;
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

    private static final RegistrationProvider<DataComponentType<?>> COMPONENTS =
            RegistrationProvider.get(Registries.DATA_COMPONENT_TYPE, NeroNotesCommon.MOD_ID);

    /** The pressed disk payload: score + title + authorship + label palette. */
    public static final RegistrationProvider.RegistryEntry<DataComponentType<DiskContents>> DISK_CONTENTS =
            COMPONENTS.register("disk_contents", key -> DataComponentType.<DiskContents>builder()
                    .persistent(DiskContents.CODEC)
                    .build());

    private NeroNotesDataComponents() {
    }

    /** Class-load hook — step 4 of {@code NeroNotesCommon.init()}. */
    public static void init() {
        NeroNotesCommon.LOGGER.debug("[NeroNotes] data components queued for registration");
    }
}
