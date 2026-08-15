package za.co.neroland.neronotes.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neronotes.config.NeroNotesConfig;
import za.co.neroland.neronotes.integration.NeroNotesIntegrations;
import za.co.neroland.neronotes.item.CustomDiskItem;
import za.co.neroland.neronotes.item.DiskContents;
import za.co.neroland.neronotes.item.NeroNotesItems;
import za.co.neroland.neronotes.library.LibraryStore;
import za.co.neroland.neronotes.library.LibraryTable;
import za.co.neroland.neronotes.network.LibraryPagePayload;
import za.co.neroland.neronotes.network.NotesNetwork;
import za.co.neroland.neronotes.platform.Services;
import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.score.ScoreCodec;
import za.co.neroland.neronotes.score.ScoreFormatException;
import za.co.neroland.neronotes.telemetry.NeroNotesTelemetry;

/**
 * The Disk Exchanger menu: a source slot (the disk to duplicate), a
 * blank-disk slot, a take-only output slot, the player inventory, and
 * data-slot-backed page gauges (convention 10). The library listing itself
 * travels as the metadata-only {@code LibraryPagePayload} — paginated from
 * day one (locked decision 5) — and every action arrives as an
 * {@code ExchangerActionPayload} validated server-side here.
 *
 * <p><strong>Copying is entirely server-side.</strong> The client names an
 * entry id; the server decodes its own stored score bytes (through the
 * budget-checked {@link ScoreCodec#fromBytes}) and writes the disk — no
 * score crosses the wire for the Exchanger in either direction. A copy
 * increments the entry's <em>aggregate</em> download count and records
 * nothing else: no who, no when.</p>
 */
public class DiskExchangerMenu extends AbstractContainerMenu {

    public static final int SLOT_SOURCE = 0;
    public static final int SLOT_BLANK = 1;
    public static final int SLOT_OUTPUT = 2;
    private static final int MACHINE_SLOTS = 3;

    public static final int DATA_PAGE = 0;
    public static final int DATA_PAGE_COUNT = 1;
    public static final int DATA_VISIBLE_COUNT = 2;
    public static final int DATA_COUNT = 3;

    /** Data slots are 16-bit on the wire; gauge values clamp to this. */
    private static final int DATA_SLOT_MAX = 32767;

    /** How far a player may drift from the Exchanger before the menu closes. */
    private static final double MAX_DISTANCE_SQ = 64.0;

    private final SimpleContainer container = new SimpleContainer(MACHINE_SLOTS);
    private final SimpleContainerData data;
    @Nullable
    private final ServerPlayer serverPlayer;
    @Nullable
    private final BlockPos exchangerPos;

    /** Client-side reconstruction (the {@code MenuType} factory). */
    public DiskExchangerMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, null, null);
    }

    /** Server-side: bound to the interacting player and the Exchanger block. */
    public DiskExchangerMenu(int containerId, Inventory playerInventory,
                             @Nullable ServerPlayer player, @Nullable BlockPos exchangerPos) {
        super(NeroNotesMenus.DISK_EXCHANGER.get(), containerId);
        this.data = new SimpleContainerData(DATA_COUNT);
        this.serverPlayer = player;
        this.exchangerPos = exchangerPos;

        addSlot(new Slot(container, SLOT_SOURCE, 8, 105) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(NeroNotesItems.CUSTOM_DISK.get());
            }
        });
        addSlot(new Slot(container, SLOT_BLANK, 44, 105) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(NeroNotesItems.BLANK_DISK.get());
            }
        });
        addSlot(new Slot(container, SLOT_OUTPUT, 152, 105) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false; // copies are taken, never inserted
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }
        addDataSlots(data); // convention 10 — the page gauges are data-slot backed

        if (player != null) {
            refreshGauges(0);
        }
    }

    // ------------------------------------------------------------------
    // Server-side actions (invoked by network/ExchangerServerHandlers)
    // ------------------------------------------------------------------

    /** Send the requested (clamped) library page to the player. */
    public void sendPage(ServerPlayer player, int requestedPage) {
        MinecraftServer server = player.level().getServer();
        LibraryStore library = LibraryStore.get(server);
        int pageSize = NeroNotesConfig.LIBRARY_PAGE_SIZE.get();
        int pageCount = library.pageCount(pageSize);
        int page = Math.max(0, Math.min(requestedPage, Math.max(0, pageCount - 1)));
        List<LibraryPagePayload.PageEntry> rows = new ArrayList<>();
        for (LibraryTable.Entry entry : library.visiblePage(page, pageSize)) {
            // Metadata only: no score, no UUID. Anonymous (or erased-author)
            // entries carry "" and render as the translated anonymous line.
            rows.add(new LibraryPagePayload.PageEntry(
                    entry.id(), entry.title(),
                    truncate(entry.authorDisplay().orElse(""), LibraryPagePayload.MAX_AUTHOR_LENGTH),
                    truncate(entry.familyId(), LibraryPagePayload.MAX_FAMILY_LENGTH),
                    entry.downloads()));
        }
        refreshGauges(page);
        Services.network().sendToPlayer(player,
                new LibraryPagePayload(containerId, page, pageCount, library.visibleCount(), rows));
    }

    /**
     * Copy library entry {@code entryId} onto the blank disk in the blank
     * slot. Every refusal is a translated message. On success the aggregate
     * download count is incremented — nothing else is recorded.
     */
    public void tryCopy(ServerPlayer player, int entryId) {
        if (!NeroNotesConfig.EXCHANGER_ENABLED.get()) {
            player.sendSystemMessage(Component.translatable("neronotes.exchanger.disabled"));
            return;
        }
        if (!container.getItem(SLOT_OUTPUT).isEmpty()) {
            player.sendSystemMessage(Component.translatable("neronotes.exchanger.output_full"));
            return;
        }
        ItemStack blank = container.getItem(SLOT_BLANK);
        if (blank.isEmpty() || !blank.is(NeroNotesItems.BLANK_DISK.get())) {
            player.sendSystemMessage(Component.translatable("neronotes.exchanger.no_blank"));
            return;
        }
        MinecraftServer server = player.level().getServer();
        LibraryStore library = LibraryStore.get(server);
        LibraryTable.Entry entry = library.entry(entryId).filter(LibraryTable.Entry::visible).orElse(null);
        if (entry == null) {
            player.sendSystemMessage(Component.translatable("neronotes.exchanger.entry_gone"));
            sendPage(player, data.get(DATA_PAGE));
            return;
        }
        Score score;
        try {
            score = ScoreCodec.fromBytes(entry.scoreBytes(), NotesNetwork.MAX_SCORE_PAYLOAD_BYTES);
        } catch (ScoreFormatException unreadable) {
            player.sendSystemMessage(Component.translatable("neronotes.exchanger.entry_unreadable"));
            NeroNotesTelemetry.captureHandled("exchanger", "copy_decode", unreadable);
            return;
        }
        // Stage 8: the NeroEconomy pricing seam — consulted LAST, once every
        // other refusal is ruled out, so a paid copy can never be charged and
        // then refused. The default is free; 0.1.0 installs no bridge, so
        // this always allows. The composer UUID is the royalty target (empty
        // for anonymous/erased entries) and never surfaces to the client.
        if (!NeroNotesIntegrations.exchangerPricing().chargeCopy(player.getUUID(), entryId, entry.author())) {
            player.sendSystemMessage(Component.translatable("neronotes.exchanger.payment_refused"));
            return;
        }
        // An erased author leaves no UUID behind; copies of such entries carry
        // the nil UUID (matches no real player) and stay anonymous.
        UUID author = entry.author().orElse(new UUID(0L, 0L));
        DiskContents contents = new DiskContents(score, entry.title(), author,
                entry.authorDisplay().orElse(""), entry.authorDisplay().isEmpty(), entry.familyId());
        blank.shrink(1);
        container.setItem(SLOT_OUTPUT, CustomDiskItem.createStack(NeroNotesItems.CUSTOM_DISK.get(), contents));
        container.setChanged();
        library.incrementDownloads(entryId); // aggregate count only — no identity, no timestamp
        player.sendSystemMessage(Component.translatable("neronotes.exchanger.copied"));
        sendPage(player, data.get(DATA_PAGE)); // refresh the shown download count
    }

    /**
     * Duplicate the disk in the source slot onto a blank disk. The copy is
     * exact — title, palette and the attribution choice included, so an
     * anonymous disk duplicates anonymously. The library is not involved and
     * no count changes.
     */
    public void tryDuplicate(ServerPlayer player) {
        if (!NeroNotesConfig.EXCHANGER_ENABLED.get()) {
            player.sendSystemMessage(Component.translatable("neronotes.exchanger.disabled"));
            return;
        }
        if (!container.getItem(SLOT_OUTPUT).isEmpty()) {
            player.sendSystemMessage(Component.translatable("neronotes.exchanger.output_full"));
            return;
        }
        ItemStack source = container.getItem(SLOT_SOURCE);
        DiskContents contents = source.is(NeroNotesItems.CUSTOM_DISK.get())
                ? CustomDiskItem.contentsOf(source) : null;
        if (contents == null) {
            player.sendSystemMessage(Component.translatable("neronotes.exchanger.no_source"));
            return;
        }
        ItemStack blank = container.getItem(SLOT_BLANK);
        if (blank.isEmpty() || !blank.is(NeroNotesItems.BLANK_DISK.get())) {
            player.sendSystemMessage(Component.translatable("neronotes.exchanger.no_blank"));
            return;
        }
        blank.shrink(1);
        container.setItem(SLOT_OUTPUT, CustomDiskItem.createStack(NeroNotesItems.CUSTOM_DISK.get(), contents));
        container.setChanged();
        player.sendSystemMessage(Component.translatable("neronotes.exchanger.duplicated"));
    }

    private void refreshGauges(int page) {
        MinecraftServer server = serverPlayer == null ? null : serverPlayer.level().getServer();
        if (server == null) {
            return;
        }
        LibraryStore library = LibraryStore.get(server);
        int pageSize = NeroNotesConfig.LIBRARY_PAGE_SIZE.get();
        data.set(DATA_PAGE, Math.min(page, DATA_SLOT_MAX));
        data.set(DATA_PAGE_COUNT, Math.min(library.pageCount(pageSize), DATA_SLOT_MAX));
        data.set(DATA_VISIBLE_COUNT, Math.min(library.visibleCount(), DATA_SLOT_MAX));
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, container);
    }

    @Override
    public boolean stillValid(Player player) {
        if (serverPlayer == null || exchangerPos == null) {
            return true; // client copy is never the authority
        }
        return player.distanceToSqr(Vec3.atCenterOf(exchangerPos)) <= MAX_DISTANCE_SQ;
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
            } else if (stack.is(NeroNotesItems.BLANK_DISK.get())) {
                if (!moveItemStackTo(stack, SLOT_BLANK, SLOT_BLANK + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(NeroNotesItems.CUSTOM_DISK.get())) {
                if (!moveItemStackTo(stack, SLOT_SOURCE, SLOT_SOURCE + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
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

    public int page() {
        return data.get(DATA_PAGE);
    }

    public int pageCount() {
        return data.get(DATA_PAGE_COUNT);
    }

    public int visibleCount() {
        return data.get(DATA_VISIBLE_COUNT);
    }
}
