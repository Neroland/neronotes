package za.co.neroland.neronotes.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.neronotes.block.VoicePedestalBlock;
import za.co.neroland.neronotes.menu.SequencerMenu;
import za.co.neroland.neronotes.network.SequencerEditPayload;
import za.co.neroland.neronotes.platform.Services;
import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.soundforge.SequencerEdit;
import za.co.neroland.neronotes.soundforge.SessionEditor;
import za.co.neroland.neronotes.voice.VoiceRegistry;

/**
 * The transport lectern's sequencer screen — deliberately a <strong>pragmatic
 * grid editor, not a DAW</strong> (locked decision 2): a paged note grid for
 * the active layer, layer/voice selection, tempo, loop region and preview.
 *
 * <p>Every interaction only ever <em>proposes</em> a {@link SequencerEdit} to
 * the server; the grid re-renders from the server's authoritative echo in
 * {@link ClientSequencerState}, and the readouts (tempo, loop, layer count,
 * size-vs-budget, previewing) come from the menu's data slots. Nothing here
 * asserts anything.</p>
 */
public class SequencerScreen extends AbstractContainerScreen<SequencerMenu> {

    private static final int W = 252;
    private static final int H = 214;

    // The note grid (panel-relative).
    private static final int GRID_X = 34;
    private static final int GRID_Y = 58;
    private static final int COLS = 24;
    private static final int ROWS = 12;
    private static final int CELL = 8;

    // Palette.
    private static final int INK = 0xFF05080D;
    private static final int PANEL = 0xFF0B0F16;
    private static final int PANEL_EDGE = 0xFF232B36;
    private static final int TROUGH = 0xFF10161F;
    private static final int GRID_LINE = 0xFF1A222D;
    private static final int BEAT_LINE = 0xFF2A3644;
    private static final int TITLE = 0xFFD6ECFF;
    private static final int SUBTLE = 0xFF8DA0B4;
    private static final int ACCENT = 0xFF00E5FF;
    private static final int LOOP_TINT = 0x3300E5FF;

    /** First visible score tick (page scroll). */
    private int tickOffset;
    /** Pitch of the bottom grid row (octave scroll). */
    private int pitchBase = 48;

    private final List<NotesButton> layerButtons = new ArrayList<>();
    private NotesButton previewButton;
    private NotesButton voiceButton;
    private NotesButton removeLayerButton;
    private NotesButton addLayerButton;

    public SequencerScreen(SequencerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, W, H);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelY = 10_000; // no player inventory on this screen
    }

    @Override
    protected void init() {
        super.init();
        int l = leftPos;
        int t = topPos;

        // Transport row.
        addRenderableWidget(new NotesButton(l + 8, t + 18, 12, 12,
                Component.literal("-"), ACCENT, b -> nudgeTempo(-5)));
        addRenderableWidget(new NotesButton(l + 58, t + 18, 12, 12,
                Component.literal("+"), ACCENT, b -> nudgeTempo(5)));
        previewButton = addRenderableWidget(new NotesButton(l + 190, t + 18, 54, 12,
                Component.translatable("neronotes.sequencer.preview"), ACCENT, b -> togglePreview()));
        addRenderableWidget(new NotesButton(l + 76, t + 18, 24, 12,
                Component.translatable("neronotes.sequencer.loop_start"), ACCENT, b -> setLoopStart()));
        addRenderableWidget(new NotesButton(l + 102, t + 18, 24, 12,
                Component.translatable("neronotes.sequencer.loop_end"), ACCENT, b -> setLoopEnd()));
        addRenderableWidget(new NotesButton(l + 128, t + 18, 16, 12,
                Component.literal("×"), ACCENT, b -> send(SequencerEdit.setLoop(0, 0))));

        // Layer row.
        layerButtons.clear();
        for (int i = 0; i < SessionEditor.MAX_LAYERS; i++) {
            final int layer = i;
            layerButtons.add(addRenderableWidget(new NotesButton(l + 8 + i * 22, t + 36, 20, 12,
                    Component.literal("L" + (i + 1)), ACCENT,
                    b -> send(SequencerEdit.setActiveLayer(layer)))));
        }
        addLayerButton = addRenderableWidget(new NotesButton(l + 100, t + 36, 20, 12,
                Component.literal("+L"), ACCENT, b -> addLayer()));
        removeLayerButton = addRenderableWidget(new NotesButton(l + 122, t + 36, 20, 12,
                Component.literal("-L"), ACCENT,
                b -> send(SequencerEdit.removeLayer(clientActiveLayer()))));
        voiceButton = addRenderableWidget(new NotesButton(l + 148, t + 36, 96, 12,
                Component.translatable("neronotes.sequencer.voice"), ACCENT, b -> cycleVoice()));

        // Page / octave scrolling (client-only view state).
        addRenderableWidget(new NotesButton(l + 8, t + 160, 16, 12,
                Component.literal("◀"), ACCENT, b -> page(-COLS)));
        addRenderableWidget(new NotesButton(l + 26, t + 160, 16, 12,
                Component.literal("▶"), ACCENT, b -> page(COLS)));
        addRenderableWidget(new NotesButton(l + 210, t + 160, 16, 12,
                Component.literal("▲"), ACCENT, b -> octave(12)));
        addRenderableWidget(new NotesButton(l + 228, t + 160, 16, 12,
                Component.literal("▼"), ACCENT, b -> octave(-12)));
    }

    // ------------------------------------------------------------------
    // Proposals (client → server; the echo re-renders everything)
    // ------------------------------------------------------------------

    private void send(SequencerEdit edit) {
        Services.network().sendToServer(new SequencerEditPayload(menu.containerId, edit));
    }

    private void nudgeTempo(int delta) {
        int target = Math.max(Score.MIN_TEMPO_BPM,
                Math.min(Score.MAX_TEMPO_BPM, menu.tempoBpm() + delta));
        send(SequencerEdit.setTempo(target));
    }

    private void togglePreview() {
        send(menu.previewing() ? SequencerEdit.previewStop() : SequencerEdit.previewStart());
    }

    private void setLoopStart() {
        int end = menu.loopEndTick();
        send(SequencerEdit.setLoop(tickOffset, end > tickOffset ? end : tickOffset + COLS));
    }

    private void setLoopEnd() {
        int start = Math.min(menu.loopStartTick(), Math.max(0, tickOffset + COLS - 1));
        send(SequencerEdit.setLoop(start, tickOffset + COLS));
    }

    private void addLayer() {
        // Propose the first registered voice; the server validates it anyway.
        String first = VoiceRegistry.shared().voiceIds().stream()
                .filter(id -> !VoiceRegistry.FALLBACK_VOICE_ID.equals(id))
                .findFirst().orElse(VoiceRegistry.FALLBACK_VOICE_ID);
        send(SequencerEdit.addLayer(first));
    }

    private void cycleVoice() {
        int layer = clientActiveLayer();
        Optional<Score> score = clientScore();
        if (score.isEmpty() || layer >= score.get().layers().size()) {
            return;
        }
        String current = score.get().layers().get(layer).voiceId();
        List<String> ids = new ArrayList<>(VoiceRegistry.shared().voiceIds());
        ids.remove(VoiceRegistry.FALLBACK_VOICE_ID);
        if (ids.isEmpty()) {
            return;
        }
        int at = ids.indexOf(current);
        send(SequencerEdit.setLayerVoice(layer, ids.get((at + 1) % ids.size())));
    }

    private void page(int delta) {
        tickOffset = Math.max(0, Math.min(SessionEditor.MAX_TICK - COLS, tickOffset + delta));
    }

    private void octave(int delta) {
        pitchBase = Math.max(0, Math.min(Score.MAX_PITCH - ROWS + 1, pitchBase + delta));
    }

    private Optional<Score> clientScore() {
        return ClientSequencerState.scoreFor(menu.containerId);
    }

    private int clientActiveLayer() {
        return ClientSequencerState.activeLayerFor(menu.containerId, menu.activeLayer());
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int col = (int) Math.floor((event.x() - (leftPos + GRID_X)) / CELL);
        int row = (int) Math.floor((event.y() - (topPos + GRID_Y)) / CELL);
        if (col >= 0 && col < COLS && row >= 0 && row < ROWS) {
            int tick = tickOffset + col;
            int pitch = pitchBase + (ROWS - 1 - row);
            send(SequencerEdit.toggleNote(clientActiveLayer(), tick, pitch));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        int layerCount = menu.layerCount();
        int active = clientActiveLayer();
        for (int i = 0; i < layerButtons.size(); i++) {
            NotesButton button = layerButtons.get(i);
            button.active = i < layerCount;
            button.setSelected(i == active);
        }
        addLayerButton.active = layerCount < SessionEditor.MAX_LAYERS;
        removeLayerButton.active = layerCount > 1;
        previewButton.setMessage(Component.translatable(menu.previewing()
                ? "neronotes.sequencer.preview_stop" : "neronotes.sequencer.preview"));
        previewButton.setSelected(menu.previewing());
        clientScore().ifPresent(score -> {
            if (active < score.layers().size()) {
                voiceButton.setMessage(Component.translatable(
                        VoicePedestalBlock.voiceNameKey(score.layers().get(active).voiceId())));
            }
        });
    }

    @Override
    public void removed() {
        super.removed();
        ClientSequencerState.clear();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        drawPanel(g);
        super.extractContents(g, mouseX, mouseY, partialTick);
        drawReadouts(g);
        drawGrid(g);
    }

    private void drawPanel(GuiGraphicsExtractor g) {
        int l = leftPos;
        int t = topPos;
        g.fill(l - 2, t - 2, l + W + 2, t + H + 2, INK);
        g.fill(l, t, l + W, t + H, PANEL);
        g.fill(l, t, l + W, t + 1, PANEL_EDGE);
        g.fill(l, t + 15, l + W, t + 16, PANEL_EDGE);
    }

    private void drawReadouts(GuiGraphicsExtractor g) {
        int l = leftPos;
        int t = topPos;
        // Tempo between its nudge buttons.
        g.centeredText(font, Component.literal(menu.tempoBpm() + " BPM"), l + 39, t + 20, TITLE);
        // Loop region readout.
        String loop = menu.loopEndTick() > 0
                ? menu.loopStartTick() + "–" + menu.loopEndTick()
                : "—";
        g.text(font, Component.translatable("neronotes.sequencer.loop", loop), l + 148, t + 20, SUBTLE, false);
        // Page + octave readouts beside their buttons.
        g.text(font, Component.literal(tickOffset + "–" + (tickOffset + COLS - 1)),
                l + 48, t + 162, SUBTLE, false);
        g.text(font, Component.literal("p" + pitchBase), l + 184, t + 162, SUBTLE, false);
        // Size-vs-budget gauge (data-slot backed).
        int gaugeX = l + 8;
        int gaugeY = t + 178;
        int gaugeW = 160;
        g.fill(gaugeX - 1, gaugeY - 1, gaugeX + gaugeW + 1, gaugeY + 7, INK);
        g.fill(gaugeX, gaugeY, gaugeX + gaugeW, gaugeY + 6, TROUGH);
        float frac = Math.min(1.0f, menu.sizeUnits() / (float) menu.budgetUnits());
        int fw = Math.round(gaugeW * frac);
        if (fw > 0) {
            g.fill(gaugeX, gaugeY, gaugeX + fw, gaugeY + 6, frac >= 1.0f ? 0xFFFF2975 : ACCENT);
        }
        g.text(font, Component.translatable("neronotes.sequencer.size",
                        menu.sizeUnits() * SequencerMenu.SIZE_UNIT_BYTES,
                        menu.budgetUnits() * SequencerMenu.SIZE_UNIT_BYTES),
                gaugeX + gaugeW + 6, gaugeY - 1, SUBTLE, false);
        // Hint line.
        g.text(font, Component.translatable("neronotes.sequencer.hint"), l + 8, t + 192, 0xFF5C6470, false);
    }

    private void drawGrid(GuiGraphicsExtractor g) {
        int gx = leftPos + GRID_X;
        int gy = topPos + GRID_Y;
        int gw = COLS * CELL;
        int gh = ROWS * CELL;

        g.fill(gx - 1, gy - 1, gx + gw + 1, gy + gh + 1, INK);
        g.fill(gx, gy, gx + gw, gy + gh, TROUGH);

        int tpb = menu.ticksPerBeat();
        // Loop-region tint on visible columns.
        if (menu.loopEndTick() > 0) {
            int from = Math.max(menu.loopStartTick(), tickOffset);
            int to = Math.min(menu.loopEndTick(), tickOffset + COLS);
            if (to > from) {
                g.fill(gx + (from - tickOffset) * CELL, gy, gx + (to - tickOffset) * CELL, gy + gh, LOOP_TINT);
            }
        }
        // Grid lines (beat columns brighter).
        for (int c = 0; c <= COLS; c++) {
            int lineColor = (tickOffset + c) % tpb == 0 ? BEAT_LINE : GRID_LINE;
            g.fill(gx + c * CELL, gy, gx + c * CELL + 1, gy + gh, lineColor);
        }
        for (int r = 0; r <= ROWS; r++) {
            g.fill(gx, gy + r * CELL, gx + gw, gy + r * CELL + 1, GRID_LINE);
        }
        // Pitch labels on octave rows.
        for (int r = 0; r < ROWS; r++) {
            int pitch = pitchBase + (ROWS - 1 - r);
            if (pitch % 12 == 0) {
                g.text(font, Component.literal(String.valueOf(pitch)),
                        leftPos + 10, gy + r * CELL, SUBTLE, false);
            }
        }
        // Notes: ghost every layer, highlight the active one.
        Optional<Score> maybeScore = clientScore();
        if (maybeScore.isEmpty()) {
            return;
        }
        Score score = maybeScore.get();
        int active = clientActiveLayer();
        for (int layerIndex = 0; layerIndex < score.layers().size(); layerIndex++) {
            Score.Layer layer = score.layers().get(layerIndex);
            boolean isActive = layerIndex == active;
            int accent = VoiceRegistry.shared().resolve(layer.voiceId()).family().accentColour();
            int color = isActive ? accent : (accent & 0x00FFFFFF) | 0x55000000;
            for (Score.Note note : layer.notes()) {
                int col = note.tick() - tickOffset;
                int row = ROWS - 1 - (note.pitch() - pitchBase);
                if (col < 0 || col >= COLS || row < 0 || row >= ROWS) {
                    continue;
                }
                int x = gx + col * CELL;
                int y = gy + row * CELL;
                g.fill(x + 1, y + 1, x + CELL, y + CELL, color);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, titleLabelX, titleLabelY, TITLE, false);
    }
}
