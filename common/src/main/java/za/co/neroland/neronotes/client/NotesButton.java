package za.co.neroland.neronotes.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A neon-on-matte-black button for the NeroNotes screens: dark recessed body,
 * accent border that brightens on hover or when selected, centred text —
 * drawn procedurally so no GUI texture asset is needed.
 */
public class NotesButton extends Button {

    private final int accent;
    private boolean selected;

    public NotesButton(int x, int y, int width, int height, Component message, int accent, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.accent = accent;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hovered = isHoveredOrFocused() && this.active;

        int border = !this.active ? 0xFF23262E : (this.selected || hovered ? this.accent : 0xFF2B3542);
        int body = !this.active ? 0xFF101318 : (this.selected ? 0xFF1A2430 : (hovered ? 0xFF182230 : 0xFF0C1016));

        extractor.fill(x, y, x + w, y + h, border);
        extractor.fill(x + 1, y + 1, x + w - 1, y + h - 1, body);
        extractor.fill(x + 1, y + 1, x + w - 1, y + 2, 0x1FFFFFFF); // top sheen

        Font font = Minecraft.getInstance().font;
        int textColor = !this.active ? 0xFF5C6470 : (hovered || this.selected ? 0xFFFFFFFF : 0xFFC9D8E8);
        extractor.centeredText(font, getMessage(), x + w / 2, y + (h - 8) / 2, textColor);
    }
}
