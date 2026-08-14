package za.co.neroland.neronotes.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neronotes.block.entity.TransportLecternBlockEntity;
import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.score.ScoreCodec;
import za.co.neroland.neronotes.soundforge.SequencerSessions;
import za.co.neroland.neronotes.soundforge.SoundforgeDimension;

/**
 * The transport lectern's sequencer menu — a slotless container menu whose
 * job is the data-slot channel (convention 10: <strong>every GUI gauge is
 * data-slot backed</strong>). The note grid itself travels separately as the
 * budget-bounded {@code SessionScorePayload}; the readouts (tempo, loop,
 * layer, size-vs-budget, previewing) come from the data slots synced by
 * {@link #broadcastChanges()}.
 *
 * <p>Server-authoritative: the menu never applies an edit. Edits arrive as
 * {@code SequencerEditPayload}s, are validated in
 * {@code network/SequencerServerHandlers}, and the menu merely re-reads the
 * stored session ({@link #refreshFromSession()}).</p>
 */
public class SequencerMenu extends AbstractContainerMenu {

    // Data-slot indices (values are shorts on the wire — sizes travel in
    // SIZE_UNIT_BYTES units so the 64 KiB ceiling fits comfortably).
    public static final int DATA_TEMPO = 0;
    public static final int DATA_TICKS_PER_BEAT = 1;
    public static final int DATA_LOOP_START = 2;
    public static final int DATA_LOOP_END = 3;
    public static final int DATA_ACTIVE_LAYER = 4;
    public static final int DATA_LAYER_COUNT = 5;
    public static final int DATA_SIZE_UNITS = 6;
    public static final int DATA_BUDGET_UNITS = 7;
    public static final int DATA_PREVIEWING = 8;
    public static final int DATA_COUNT = 9;

    /** Bytes per size data-slot unit. */
    public static final int SIZE_UNIT_BYTES = 16;

    /** How far a player may drift from the lectern before the menu closes. */
    private static final double MAX_DISTANCE_SQ = 64.0;

    private final SimpleContainerData data;
    @Nullable
    private final ServerPlayer serverPlayer;
    @Nullable
    private final BlockPos lecternPos;

    private int budgetBytes;

    /** Client-side reconstruction (the {@code MenuType} factory). */
    public SequencerMenu(int containerId, Inventory playerInventory) {
        this(containerId, null, null);
    }

    /** Server-side: bound to the interacting player and the lectern. */
    public SequencerMenu(int containerId, @Nullable ServerPlayer player, @Nullable BlockPos lecternPos) {
        super(NeroNotesMenus.SEQUENCER.get(), containerId);
        this.data = new SimpleContainerData(DATA_COUNT);
        this.serverPlayer = player;
        this.lecternPos = lecternPos;
        addDataSlots(data); // convention 10 — without this every gauge renders dead
        if (player != null) {
            refreshFromSession();
        }
    }

    /**
     * Re-read the authoritative session into the data slots — called on open
     * and after every edit the server handler processes.
     */
    public void refreshFromSession() {
        if (serverPlayer == null) {
            return;
        }
        Score score = SequencerSessions.sessionScore(serverPlayer.level().getServer(), serverPlayer.getUUID());
        int activeLayer = SequencerSessions.activeLayer(serverPlayer.level().getServer(), serverPlayer.getUUID());
        budgetBytes = za.co.neroland.neronotes.config.NeroNotesConfig.DISK_SCORE_BUDGET_BYTES.get();
        data.set(DATA_TEMPO, score.tempoBpm());
        data.set(DATA_TICKS_PER_BEAT, score.ticksPerBeat());
        data.set(DATA_LOOP_START, score.loopStartTick());
        data.set(DATA_LOOP_END, score.loopEndTick());
        data.set(DATA_ACTIVE_LAYER, activeLayer);
        data.set(DATA_LAYER_COUNT, score.layers().size());
        data.set(DATA_SIZE_UNITS, toUnits(ScoreCodec.serialisedSize(score)));
        data.set(DATA_BUDGET_UNITS, toUnits(budgetBytes));
    }

    /** Round bytes UP to data-slot units so the gauge never under-reports. */
    public static int toUnits(int bytes) {
        return (bytes + SIZE_UNIT_BYTES - 1) / SIZE_UNIT_BYTES;
    }

    @Override
    public void broadcastChanges() {
        if (serverPlayer != null) {
            data.set(DATA_PREVIEWING, lectern() instanceof TransportLecternBlockEntity lectern
                    && lectern.isPreviewingFor(serverPlayer.getUUID()) ? 1 : 0);
        }
        super.broadcastChanges();
    }

    /** The lectern block entity this menu was opened at, server-side only. */
    @Nullable
    public TransportLecternBlockEntity lectern() {
        if (serverPlayer == null || lecternPos == null) {
            return null;
        }
        return serverPlayer.level().getBlockEntity(lecternPos) instanceof TransportLecternBlockEntity lectern
                ? lectern : null;
    }

    @Override
    public boolean stillValid(Player player) {
        if (serverPlayer == null || lecternPos == null) {
            return true; // client copy is never the authority
        }
        return SoundforgeDimension.isSoundforge(player.level())
                && player.distanceToSqr(Vec3.atCenterOf(lecternPos)) <= MAX_DISTANCE_SQ;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // no slots
    }

    // ------------------------------------------------------------------
    // Screen readouts (data-slot backed)
    // ------------------------------------------------------------------

    public int tempoBpm() {
        return data.get(DATA_TEMPO);
    }

    public int ticksPerBeat() {
        return Math.max(1, data.get(DATA_TICKS_PER_BEAT));
    }

    public int loopStartTick() {
        return data.get(DATA_LOOP_START);
    }

    public int loopEndTick() {
        return data.get(DATA_LOOP_END);
    }

    public int activeLayer() {
        return data.get(DATA_ACTIVE_LAYER);
    }

    public int layerCount() {
        return Math.max(1, data.get(DATA_LAYER_COUNT));
    }

    public int sizeUnits() {
        return data.get(DATA_SIZE_UNITS);
    }

    public int budgetUnits() {
        return Math.max(1, data.get(DATA_BUDGET_UNITS));
    }

    public boolean previewing() {
        return data.get(DATA_PREVIEWING) != 0;
    }
}
