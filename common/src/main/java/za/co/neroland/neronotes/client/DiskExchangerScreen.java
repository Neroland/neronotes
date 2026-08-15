package za.co.neroland.neronotes.client;

import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.neronotes.menu.DiskExchangerMenu;
import za.co.neroland.neronotes.network.ExchangerActionPayload;
import za.co.neroland.neronotes.network.LibraryPagePayload;
import za.co.neroland.neronotes.platform.Services;
import za.co.neroland.neronotes.voice.VoiceFamily;

/**
 * The Disk Exchanger screen: the shared-library listing (server-paginated
 * from day one; the visible rows scroll within the received page), the
 * source / blank / output slots, and the Copy and Duplicate actions. The
 * listing renders from {@link ClientExchangerState} — metadata only, no
 * scores — and every action is a tiny {@code ExchangerActionPayload}; the
 * server decides everything.
 *
 * <p>Anonymous entries show the translated "anonymous" line; no author UUID
 * exists anywhere client-side.</p>
 */
public class DiskExchangerScreen extends AbstractContainerScreen<DiskExchangerMenu> {

    private static final int W = 176;
    private static final int H = 222;

    /** Visible listing rows (a window onto the received page). */
    private static final int VISIBLE_ROWS = 5;
    private static final int ROW_H = 14;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 17;
    private static final int LIST_W = W - 30; // room for the scroll buttons

    // Palette (the shared NeroNotes screen look).
    private static final int INK = 0xFF05080D;
    private static final int PANEL = 0xFF0B0F16;
    private static final int PANEL_EDGE = 0xFF232B36;
    private static final int TROUGH = 0xFF10161F;
    private static final int TITLE = 0xFFD6ECFF;
    private static final int SUBTLE = 0xFF8DA0B4;
    private static final int ACCENT = 0xFF00E5FF;

    /** Selected library entry id, or -1. Client view state only. */
    private int selectedId = -1;
    /** First visible row within the received page. Client view state only. */
    private int rowOffset;

    private NotesButton prevButton;
    private NotesButton nextButton;
    private NotesButton copyButton;

    public DiskExchangerScreen(DiskExchangerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, W, H);
        this.titleLabelX = 8;
        this.titleLabelY = 5;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 130;
    }

    @Override
    protected void init() {
        super.init();
        int l = leftPos;
        int t = topPos;

        // In-page scroll (client-only view state).
        addRenderableWidget(new NotesButton(l + W - 20, t + LIST_Y, 12, 12,
                Component.literal("▲"), ACCENT, b -> scroll(-1)));
        addRenderableWidget(new NotesButton(l + W - 20, t + LIST_Y + (VISIBLE_ROWS - 1) * ROW_H + 2, 12, 12,
                Component.literal("▼"), ACCENT, b -> scroll(1)));

        // Server-side pagination (locked decision 5: paginated from day one).
        prevButton = addRenderableWidget(new NotesButton(l + 8, t + 89, 16, 12,
                Component.literal("◀"), ACCENT, b -> requestPage(currentPage() - 1)));
        nextButton = addRenderableWidget(new NotesButton(l + W - 24, t + 89, 16, 12,
                Component.literal("▶"), ACCENT, b -> requestPage(currentPage() + 1)));

        copyButton = addRenderableWidget(new NotesButton(l + 66, t + 105, 40, 18,
                Component.translatable("neronotes.exchanger.copy"), ACCENT, b -> copySelected()));
        addRenderableWidget(new NotesButton(l + 108, t + 105, 40, 18,
                Component.translatable("neronotes.exchanger.duplicate"), ACCENT, b -> duplicate()));

        // Ask for the first page as soon as the menu is open.
        requestPage(0);
    }

    // ------------------------------------------------------------------
    // Requests (client → server; the page echo re-renders the listing)
    // ------------------------------------------------------------------

    private void requestPage(int page) {
        rowOffset = 0;
        selectedId = -1;
        Services.network().sendToServer(new ExchangerActionPayload(
                menu.containerId, ExchangerActionPayload.Action.REQUEST_PAGE, Math.max(0, page)));
    }

    private void copySelected() {
        if (selectedId >= 0) {
            Services.network().sendToServer(new ExchangerActionPayload(
                    menu.containerId, ExchangerActionPayload.Action.COPY, selectedId));
        }
    }

    private void duplicate() {
        Services.network().sendToServer(new ExchangerActionPayload(
                menu.containerId, ExchangerActionPayload.Action.DUPLICATE, 0));
    }

    private void scroll(int delta) {
        int max = Math.max(0, entries().size() - VISIBLE_ROWS);
        rowOffset = Math.max(0, Math.min(rowOffset + delta, max));
    }

    private Optional<LibraryPagePayload> page() {
        return ClientExchangerState.pageFor(menu.containerId);
    }

    private List<LibraryPagePayload.PageEntry> entries() {
        return page().map(LibraryPagePayload::entries).orElse(List.of());
    }

    /** The shown page index — the synced payload wins, the data slot is the fallback. */
    private int currentPage() {
        return page().map(LibraryPagePayload::page).orElse(menu.page());
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = (int) (event.x() - (leftPos + LIST_X));
        int y = (int) (event.y() - (topPos + LIST_Y));
        if (x >= 0 && x < LIST_W && y >= 0 && y < VISIBLE_ROWS * ROW_H) {
            int row = rowOffset + y / ROW_H;
            List<LibraryPagePayload.PageEntry> rows = entries();
            if (row >= 0 && row < rows.size()) {
                selectedId = rows.get(row).id();
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        int pageIndex = currentPage();
        int pageCount = page().map(LibraryPagePayload::pageCount).orElse(menu.pageCount());
        prevButton.active = pageIndex > 0;
        nextButton.active = pageIndex + 1 < pageCount;
        copyButton.active = selectedId >= 0;
    }

    @Override
    public void removed() {
        super.removed();
        ClientExchangerState.clear();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        drawPanel(g);
        super.extractContents(g, mouseX, mouseY, partialTick);
        drawListing(g);
        drawPageReadout(g);
    }

    private void drawPanel(GuiGraphicsExtractor g) {
        int l = leftPos;
        int t = topPos;
        g.fill(l - 2, t - 2, l + W + 2, t + H + 2, INK);
        g.fill(l, t, l + W, t + H, PANEL);
        g.fill(l, t + 15, l + W, t + 16, PANEL_EDGE);
        // Machine slot frames (source, blank, output).
        drawSlotFrame(g, 8, 105);
        drawSlotFrame(g, 44, 105);
        drawSlotFrame(g, 152, 105);
        // Player inventory slot frames.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotFrame(g, 8 + col * 18, 140 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotFrame(g, 8 + col * 18, 198);
        }
    }

    private void drawSlotFrame(GuiGraphicsExtractor g, int dx, int dy) {
        int x = leftPos + dx;
        int y = topPos + dy;
        g.fill(x - 1, y - 1, x + 17, y + 17, PANEL_EDGE);
        g.fill(x, y, x + 16, y + 16, TROUGH);
    }

    private void drawListing(GuiGraphicsExtractor g) {
        int lx = leftPos + LIST_X;
        int ly = topPos + LIST_Y;
        g.fill(lx - 1, ly - 1, lx + LIST_W + 1, ly + VISIBLE_ROWS * ROW_H + 1, INK);
        g.fill(lx, ly, lx + LIST_W, ly + VISIBLE_ROWS * ROW_H, TROUGH);

        List<LibraryPagePayload.PageEntry> rows = entries();
        if (rows.isEmpty()) {
            g.text(font, Component.translatable("neronotes.exchanger.empty"),
                    lx + 4, ly + 4, SUBTLE, false);
            return;
        }
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = rowOffset + i;
            if (index >= rows.size()) {
                break;
            }
            LibraryPagePayload.PageEntry entry = rows.get(index);
            int ry = ly + i * ROW_H;
            int accent = VoiceFamily.byId(entry.familyId()).orElse(VoiceFamily.HIGH_LEAD).accentColour();
            boolean selected = entry.id() == selectedId;
            if (selected) {
                g.fill(lx, ry, lx + LIST_W, ry + ROW_H, 0xFF1A2430);
                g.fill(lx, ry, lx + 2, ry + ROW_H, accent | 0xFF000000);
            }
            // Downloads at the right edge; title — author fills the rest.
            String downloads = "↓" + entry.downloads();
            int downloadsW = font.width(downloads);
            Component author = entry.anonymous()
                    ? Component.translatable("neronotes.exchanger.row_anonymous")
                    : Component.literal(entry.authorDisplay());
            String line = entry.title() + " — " + author.getString();
            line = clip(line, LIST_W - downloadsW - 10);
            g.text(font, Component.literal(line),
                    lx + 4, ry + 3, selected ? 0xFFFFFFFF : (accent | 0xFF000000), false);
            g.text(font, Component.literal(downloads),
                    lx + LIST_W - downloadsW - 3, ry + 3, SUBTLE, false);
        }
    }

    private void drawPageReadout(GuiGraphicsExtractor g) {
        int pageIndex = currentPage();
        int pageCount = page().map(LibraryPagePayload::pageCount).orElse(menu.pageCount());
        int total = page().map(LibraryPagePayload::visibleCount).orElse(menu.visibleCount());
        Component readout = Component.translatable("neronotes.exchanger.page",
                pageCount == 0 ? 0 : pageIndex + 1, pageCount, total);
        g.centeredText(font, readout, leftPos + W / 2, topPos + 91, SUBTLE);
    }

    private String clip(String text, int width) {
        String clipped = text;
        while (clipped.length() > 1 && font.width(clipped + "…") > width) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        return clipped.equals(text) ? text : clipped + "…";
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, titleLabelX, titleLabelY, TITLE, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, SUBTLE, false);
    }
}
