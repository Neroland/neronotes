package za.co.neroland.neronotes.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The gauge hover tooltips for NeroNotes screens: each gauge names itself, says how much, and gives its rate
 * (a size-vs-budget gauge also says how much room is left). Builders append lines to a list; the screen
 * emits them through {@link #show}. Numbers come from {@link GaugeMath}; every word is an English source lang
 * key. Machine-scoped numbers already on screen only — no player identities, no coordinates.
 */
public final class GaugeTooltips {

    /** Size change per second treated as steady. */
    public static final double BUDGET_DEADBAND_PER_SECOND = 0.5D;

    private GaugeTooltips() {
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("neronotes.gui.gauge." + key, args);
    }

    /**
     * A size-vs-budget gauge: its name, used / limit in bytes (and %), how much room is left or how far over the
     * limit it is, and how fast it is changing ({@code Double.NaN} omits the rate for a size that cannot change
     * on this screen).
     */
    public static void budget(List<Component> out, Component name, long usedBytes, long limitBytes,
            double bytesPerSecond) {
        out.add(name);
        out.add(tr("budget.amount", GaugeMath.grouped(Math.max(0L, usedBytes)),
                GaugeMath.grouped(Math.max(0L, limitBytes)), GaugeMath.percent(usedBytes, limitBytes)));
        if (!Double.isNaN(bytesPerSecond)) {
            int trend = GaugeMath.trend(bytesPerSecond, BUDGET_DEADBAND_PER_SECOND);
            out.add(trend > 0 ? tr("budget.growing", GaugeMath.rate(bytesPerSecond))
                    : trend < 0 ? tr("budget.shrinking", GaugeMath.rate(bytesPerSecond)) : tr("budget.steady"));
        }
        out.add(usedBytes > limitBytes ? tr("budget.over", GaugeMath.grouped(usedBytes - limitBytes))
                : tr("budget.room", GaugeMath.grouped(Math.max(0L, limitBytes - usedBytes))));
    }

    /** The wrap width for a screen {@code screenWidth} wide: half the window, 100..220 px. */
    public static int wrapWidth(int screenWidth) {
        return Math.max(100, Math.min(220, screenWidth / 2));
    }

    /**
     * Emit a tooltip at the mouse, every line wrapped to {@link #wrapWidth} so a long cause never runs off a small
     * window (deferred on 1.21.6+, drawn immediately before that).
     */
    public static void show(GuiGraphicsExtractor g, Font font, List<Component> lines, int screenWidth, int mouseX,
            int mouseY) {
        if (lines.isEmpty()) {
            return;
        }
        int width = wrapWidth(screenWidth);
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component line : lines) {
            out.addAll(font.split(line, width));
        }
        //? if >=1.21.6 {
        g.setTooltipForNextFrame(font, out, mouseX, mouseY);
        //?} else {
        /*g.renderTooltip(font, out, mouseX, mouseY);
        *///?}
    }
}
