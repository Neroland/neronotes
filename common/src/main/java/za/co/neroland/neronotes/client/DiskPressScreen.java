package za.co.neroland.neronotes.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.neronotes.item.DiskNames;
import za.co.neroland.neronotes.menu.DiskPressMenu;
import za.co.neroland.neronotes.network.DiskPressPayload;
import za.co.neroland.neronotes.platform.Services;

/**
 * The Disk Press screen: title entry, the <strong>first-class anonymity
 * choice</strong> (a full-width toggle between "credited as you" and
 * "anonymous" — opt-out of credit is a button, not a buried setting), the
 * blank-disk input and pressed-disk output slots, and the data-slot-backed
 * size-vs-budget gauge. The press itself is a {@code DiskPressPayload}; every
 * decision (name validation, budget, item movement) is the server's.
 */
public class DiskPressScreen extends AbstractContainerScreen<DiskPressMenu> {

    private static final int W = 176;
    private static final int H = 180;

    private static final int INK = 0xFF05080D;
    private static final int PANEL = 0xFF0B0F16;
    private static final int PANEL_EDGE = 0xFF232B36;
    private static final int TROUGH = 0xFF10161F;
    private static final int TITLE = 0xFFD6ECFF;
    private static final int SUBTLE = 0xFF8DA0B4;
    private static final int ACCENT = 0xFFBF40FF; // the Soundforge violet
    private static final int WARN = 0xFFFF2975;

    private EditBox titleBox;
    private NotesButton anonymityButton;
    private boolean anonymous; // default: credited — anonymity is the opt-out

    public DiskPressScreen(DiskPressMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, W, H);
        this.titleLabelX = 8;
        this.titleLabelY = 5;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 89; // below the gauge, above the inventory rows
    }

    @Override
    protected void init() {
        super.init();
        titleBox = new EditBox(font, leftPos + 8, topPos + 18, W - 16, 14,
                Component.translatable("neronotes.press.title_hint"));
        titleBox.setMaxLength(DiskNames.HARD_MAX_LENGTH);
        titleBox.setHint(Component.translatable("neronotes.press.title_hint"));
        addRenderableWidget(titleBox);
        setInitialFocus(titleBox);
        titleBox.setFocused(true);

        anonymityButton = addRenderableWidget(new NotesButton(leftPos + 8, topPos + 38, W - 16, 14,
                Component.translatable("neronotes.press.credited"), ACCENT, b -> toggleAnonymity()));

        addRenderableWidget(new NotesButton(leftPos + 71, topPos + 60, 34, 18,
                Component.translatable("neronotes.press.press"), ACCENT, b -> press()));
    }

    private void toggleAnonymity() {
        anonymous = !anonymous;
        anonymityButton.setMessage(Component.translatable(
                anonymous ? "neronotes.press.anonymous" : "neronotes.press.credited"));
        anonymityButton.setSelected(anonymous);
    }

    private void press() {
        Services.network().sendToServer(new DiskPressPayload(
                menu.containerId, titleBox.getValue(), anonymous));
    }

    /** Let the focused title box swallow typing (incl. the inventory key). */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { // Escape
            onClose();
            return true;
        }
        if (titleBox != null && (titleBox.keyPressed(event) || titleBox.canConsumeInput())) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        drawPanel(g);
        super.extractContents(g, mouseX, mouseY, partialTick);
        drawGauge(g);
    }

    private void drawPanel(GuiGraphicsExtractor g) {
        int l = leftPos;
        int t = topPos;
        g.fill(l - 2, t - 2, l + W + 2, t + H + 2, INK);
        g.fill(l, t, l + W, t + H, PANEL);
        g.fill(l, t + 15, l + W, t + 16, PANEL_EDGE);
        // Slot frames (input, output); the press button sits between them.
        drawSlotFrame(g, 49, 61);
        drawSlotFrame(g, 107, 61);
        // Player inventory slot frames.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotFrame(g, 8 + col * 18, 98 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotFrame(g, 8 + col * 18, 156);
        }
    }

    private void drawSlotFrame(GuiGraphicsExtractor g, int dx, int dy) {
        int x = leftPos + dx;
        int y = topPos + dy;
        g.fill(x - 1, y - 1, x + 17, y + 17, PANEL_EDGE);
        g.fill(x, y, x + 16, y + 16, TROUGH);
    }

    private void drawGauge(GuiGraphicsExtractor g) {
        int x = leftPos + 8;
        int y = topPos + 82;
        int w = W - 16;
        g.fill(x - 1, y - 1, x + w + 1, y + 7, INK);
        g.fill(x, y, x + w, y + 6, TROUGH);
        float frac = Math.min(1.0f, menu.sizeUnits() / (float) menu.budgetUnits());
        int fw = Math.round(w * frac);
        if (fw > 0) {
            g.fill(x, y, x + fw, y + 6, frac >= 1.0f ? WARN : ACCENT);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, titleLabelX, titleLabelY, TITLE, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, SUBTLE, false);
    }
}
