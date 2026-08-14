package za.co.neroland.neronotes.network;

import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.block.entity.TransportLecternBlockEntity;
import za.co.neroland.neronotes.menu.DiskPressMenu;
import za.co.neroland.neronotes.menu.SequencerMenu;
import za.co.neroland.neronotes.platform.Services;
import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.score.ScoreCodec;
import za.co.neroland.neronotes.score.ScoreSizeException;
import za.co.neroland.neronotes.soundforge.SequencerSessions;
import za.co.neroland.neronotes.soundforge.SoundforgeDimension;
import za.co.neroland.neronotes.telemetry.NeroNotesTelemetry;

/**
 * Server-side handlers for the Stage 5 serverbound payloads. Every request is
 * re-validated here regardless of what the client claimed: the payload must
 * match the player's <em>open</em> menu (container id and type), the player
 * must actually be inside the Soundforge, and each edit passes through
 * {@code soundforge/SessionEditor}'s bounds. Refusals are quiet; the
 * authoritative session echo corrects the client either way.
 */
public final class SequencerServerHandlers {

    private SequencerServerHandlers() {
    }

    /** Handle one sequencer edit (server thread; wired by the loader receivers). */
    public static void handleEdit(SequencerEditPayload payload, ServerPlayer player) {
        try {
            if (!(player.containerMenu instanceof SequencerMenu menu)
                    || menu.containerId != payload.containerId()
                    || !SoundforgeDimension.isSoundforge(player.level())) {
                return; // stale or forged request — ignore quietly
            }
            switch (payload.edit().op()) {
                case PREVIEW_START -> {
                    TransportLecternBlockEntity lectern = menu.lectern();
                    if (lectern != null) {
                        lectern.startPreview(player, SequencerSessions.sessionScore(
                                player.level().getServer(), player.getUUID()));
                    }
                }
                case PREVIEW_STOP -> {
                    TransportLecternBlockEntity lectern = menu.lectern();
                    if (lectern != null && lectern.isPreviewingFor(player.getUUID())) {
                        lectern.stopPreview();
                    }
                }
                case SET_ACTIVE_LAYER -> SequencerSessions.setActiveLayer(
                        player.level().getServer(), player.getUUID(), payload.edit().a());
                default -> SequencerSessions.applyEdit(
                        player.level().getServer(), player.getUUID(), payload.edit());
            }
            menu.refreshFromSession();
            sendSession(player, menu);
        } catch (RuntimeException failure) {
            // Never a player-authored string in telemetry — op name only.
            NeroNotesTelemetry.captureHandled("sequencer", "edit:" + payload.edit().op(), failure);
        }
    }

    /** Handle a Disk Press request (server thread; wired by the loader receivers). */
    public static void handlePress(DiskPressPayload payload, ServerPlayer player) {
        try {
            if (!(player.containerMenu instanceof DiskPressMenu menu)
                    || menu.containerId != payload.containerId()) {
                return; // stale or forged request — ignore quietly
            }
            menu.tryPress(player, payload.title(), payload.anonymous());
        } catch (RuntimeException failure) {
            NeroNotesTelemetry.captureHandled("press", "press", failure);
        }
    }

    /**
     * Send the authoritative session state to {@code player}'s open sequencer
     * menu — on open and after every handled edit. The session is bounded by
     * the editor's caps, so serialisation against the wire ceiling cannot
     * fail in practice; if it ever does, the failure is captured and the
     * client simply keeps its previous state.
     */
    public static void sendSession(ServerPlayer player, SequencerMenu menu) {
        Score score = SequencerSessions.sessionScore(player.level().getServer(), player.getUUID());
        int activeLayer = SequencerSessions.activeLayer(player.level().getServer(), player.getUUID());
        byte[] bytes;
        try {
            bytes = ScoreCodec.toBytes(score, NotesNetwork.MAX_SCORE_PAYLOAD_BYTES);
        } catch (ScoreSizeException impossible) {
            NeroNotesCommon.LOGGER.warn("[NeroNotes] session score exceeded the wire ceiling; not syncing");
            NeroNotesTelemetry.captureHandled("sequencer", "sync_over_ceiling", impossible);
            return;
        }
        Services.network().sendToPlayer(player, new SessionScorePayload(menu.containerId, activeLayer, bytes));
    }
}
