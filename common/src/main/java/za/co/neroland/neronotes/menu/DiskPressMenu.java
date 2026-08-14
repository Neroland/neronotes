package za.co.neroland.neronotes.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neronotes.config.NeroNotesConfig;
import za.co.neroland.neronotes.item.CustomDiskItem;
import za.co.neroland.neronotes.item.DiskNames;
import za.co.neroland.neronotes.item.DiskPressLogic;
import za.co.neroland.neronotes.item.NeroNotesItems;
import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.score.ScoreCodec;
import za.co.neroland.neronotes.soundforge.SequencerSessions;
import za.co.neroland.neronotes.soundforge.SoundforgeDimension;
import za.co.neroland.neronotes.voice.VoiceDefinition;
import za.co.neroland.neronotes.voice.VoiceRegistry;

/**
 * The Disk Press menu: one blank-disk input slot, one take-only output slot,
 * the player inventory, and a data-slot-backed size-vs-budget gauge
 * (convention 10). The press action itself arrives as a
 * {@code DiskPressPayload} (title + the first-class anonymity choice) and is
 * executed server-side in {@link #tryPress} — the client asserts nothing.
 *
 * <p><strong>The budget is enforced through
 * {@code ScoreCodec.toBytes(score, budget)} inside {@link DiskPressLogic}</strong>
 * and an over-budget score is refused with a translated message naming both
 * byte counts. The press never truncates (locked decision 5). Name validation
 * (locked decision 6) runs here too, server-side, at press time.</p>
 */
public class DiskPressMenu extends AbstractContainerMenu {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    private static final int MACHINE_SLOTS = 2;

    public static final int DATA_SIZE_UNITS = 0;
    public static final int DATA_BUDGET_UNITS = 1;
    public static final int DATA_COUNT = 2;

    /** How far a player may drift from the press before the menu closes. */
    private static final double MAX_DISTANCE_SQ = 64.0;

    private final SimpleContainer container = new SimpleContainer(MACHINE_SLOTS);
    private final SimpleContainerData data;
    @Nullable
    private final ServerPlayer serverPlayer;
    @Nullable
    private final net.minecraft.core.BlockPos pressPos;

    /** Client-side reconstruction (the {@code MenuType} factory). */
    public DiskPressMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, null, null);
    }

    /** Server-side: bound to the interacting player and the press block. */
    public DiskPressMenu(int containerId, Inventory playerInventory,
                         @Nullable ServerPlayer player, @Nullable net.minecraft.core.BlockPos pressPos) {
        super(NeroNotesMenus.DISK_PRESS.get(), containerId);
        this.data = new SimpleContainerData(DATA_COUNT);
        this.serverPlayer = player;
        this.pressPos = pressPos;

        addSlot(new Slot(container, SLOT_INPUT, 49, 61) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(NeroNotesItems.BLANK_DISK.get());
            }
        });
        addSlot(new Slot(container, SLOT_OUTPUT, 107, 61) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false; // pressed disks are taken, never inserted
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 98 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 156));
        }
        addDataSlots(data); // convention 10 — the gauge is data-slot backed

        if (player != null) {
            Score score = SequencerSessions.sessionScore(player.level().getServer(), player.getUUID());
            data.set(DATA_SIZE_UNITS, SequencerMenu.toUnits(ScoreCodec.serialisedSize(score)));
            data.set(DATA_BUDGET_UNITS, SequencerMenu.toUnits(NeroNotesConfig.DISK_SCORE_BUDGET_BYTES.get()));
        }
    }

    /**
     * Execute a press request, server-side. Every refusal is a translated
     * message; the over-budget refusal names both the actual size and the
     * configured limit.
     */
    public void tryPress(ServerPlayer player, String rawTitle, boolean anonymous) {
        if (!SoundforgeDimension.isSoundforge(player.level())) {
            player.sendSystemMessage(Component.translatable("neronotes.press.outside"));
            return;
        }
        if (!container.getItem(SLOT_OUTPUT).isEmpty()) {
            player.sendSystemMessage(Component.translatable("neronotes.press.output_full"));
            return;
        }
        ItemStack input = container.getItem(SLOT_INPUT);
        if (input.isEmpty() || !input.is(NeroNotesItems.BLANK_DISK.get())) {
            player.sendSystemMessage(Component.translatable("neronotes.press.no_blank"));
            return;
        }
        Score score = SequencerSessions.sessionScore(player.level().getServer(), player.getUUID());
        int budget = NeroNotesConfig.DISK_SCORE_BUDGET_BYTES.get();
        DiskPressLogic.Result result = DiskPressLogic.press(
                score, rawTitle, anonymous,
                player.getUUID(), player.getGameProfile().name(),
                budget,
                NeroNotesConfig.DISK_NAME_MAX_LENGTH.get(),
                DiskNames.parseBlockedWords(NeroNotesConfig.MODERATION_BLOCKED_WORDS.get()),
                voiceId -> VoiceRegistry.shared().lookup(voiceId).map(VoiceDefinition::family));
        switch (result.error()) {
            case EMPTY_SCORE ->
                    player.sendSystemMessage(Component.translatable("neronotes.press.empty_score"));
            case OVER_BUDGET ->
                    // The named refusal (locked decision 5): actual bytes, then the limit.
                    player.sendSystemMessage(Component.translatable("neronotes.press.over_budget",
                            result.sizeBytes(), result.budgetBytes()));
            case BAD_NAME -> player.sendSystemMessage(switch (result.nameStatus()) {
                case TOO_LONG -> Component.translatable("neronotes.press.name_too_long",
                        NeroNotesConfig.DISK_NAME_MAX_LENGTH.get());
                case BLOCKED_WORD -> Component.translatable("neronotes.press.name_blocked");
                default -> Component.translatable("neronotes.press.name_empty");
            });
            case NONE -> {
                input.shrink(1);
                container.setItem(SLOT_OUTPUT,
                        CustomDiskItem.createStack(NeroNotesItems.CUSTOM_DISK.get(), result.contents()));
                container.setChanged();
                player.sendSystemMessage(Component.translatable("neronotes.press.done"));
            }
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, container);
    }

    @Override
    public boolean stillValid(Player player) {
        if (serverPlayer == null || pressPos == null) {
            return true; // client copy is never the authority
        }
        return SoundforgeDimension.isSoundforge(player.level())
                && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pressPos)) <= MAX_DISTANCE_SQ;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            int invStart = MACHINE_SLOTS;
            int invEnd = invStart + 36;
            if (index < invStart) {
                if (!moveItemStackTo(stack, invStart, invEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, SLOT_INPUT, SLOT_INPUT + 1, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Screen readouts (data-slot backed)
    // ------------------------------------------------------------------

    public int sizeUnits() {
        return data.get(DATA_SIZE_UNITS);
    }

    public int budgetUnits() {
        return Math.max(1, data.get(DATA_BUDGET_UNITS));
    }
}
