package za.co.neroland.neronotes.link;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import za.co.neroland.nerolandcore.link.LinkActionHandler;
import za.co.neroland.nerolandcore.link.LinkActionResult;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.block.ResonatorIndex;
import za.co.neroland.neronotes.block.entity.ResonatorBlockEntity;
import za.co.neroland.neronotes.signal.ChannelStore;
import za.co.neroland.neronotes.signal.ResonanceChannel;
import za.co.neroland.neronotes.signal.ResonanceService;
import za.co.neroland.neronotes.signal.ResonanceService.SignalResult;
import za.co.neroland.neronotes.telemetry.NeroNotesTelemetry;

/**
 * NeroNotes' write side of the NeroLink SPI — deliberately the smallest
 * surface that is still useful: {@code play} and {@code stop} on the loaded
 * Resonators bound to a channel the requester owns or is trusted on.
 *
 * <p><b>What is NOT here, on purpose.</b> Nothing in this handler creates a
 * channel, renames one, edits a trust list, presses a disk, publishes,
 * unpublishes or approves anything — publishing is a committal in-world
 * decision, not an API call, and management operations stay behind the
 * in-game surfaces where the player is physically present. There is no
 * {@code skip} action: 0.1.0 has no playlists or queues (a Resonator plays
 * one disk), so a skip would be a dishonest alias for {@code stop}; it can
 * arrive with playlists in a later release.</p>
 *
 * <p><b>Authorisation ladder</b>: the channel reference resolves ONLY inside
 * the requester's own owned-or-trusted channels (so "not yours" and "does not
 * exist" are the same {@code NOT_OWNER}), control is re-checked through
 * {@code ChannelAccess.canControl} with no operator bypass, and the
 * Resonator's transport path authorises the requester's UUID a third time
 * inside {@code ResonanceService}. Both actions keep the SPI's
 * {@code allowOffline} default of {@code false}: starting or silencing a
 * base's audio is something you do while you are in the game — an offline
 * request is refused by the bridge before it reaches this handler.</p>
 */
public final class NotesLinkActions implements LinkActionHandler {

    private static final List<String> ACTIONS = List.of(
            NotesLinkModule.ACTION_PLAY,
            NotesLinkModule.ACTION_STOP);

    @Override
    public String moduleId() {
        return NotesLinkModule.MODULE_ID;
    }

    @Override
    public List<String> actionIds() {
        return ACTIONS;
    }

    // allowOffline(String) is deliberately NOT overridden: the interface default
    // (false — online only) is exactly the policy for both transport actions.

    @Override
    public LinkActionResult execute(UUID playerId, String actionId, JsonObject params) {
        if (playerId == null) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION, "No player was supplied.");
        }
        if (!NotesLinkAccess.enabled()) {
            return LinkActionResult.error(LinkActionResult.Error.ACTION_DISABLED,
                    "The NeroNotes link module is disabled on this server.");
        }
        MinecraftServer server = NotesLinkAccess.server();
        if (server == null) {
            return LinkActionResult.error(LinkActionResult.Error.INTERNAL,
                    "The server is not running a world yet.");
        }
        try {
            if (NotesLinkModule.ACTION_PLAY.equals(actionId)) {
                return transport(server, playerId, params, true);
            }
            if (NotesLinkModule.ACTION_STOP.equals(actionId)) {
                return transport(server, playerId, params, false);
            }
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION,
                    "NeroNotes does not know the action '" + actionId + "'.");
        } catch (RuntimeException e) {
            // Action id only — never who asked (POPIA/GDPR).
            NeroNotesCommon.LOGGER.warn("[NeroNotes] NeroLink action '{}' failed.", actionId, e);
            NeroNotesTelemetry.captureHandled("link", "action_" + actionId, e);
            return LinkActionResult.error(LinkActionResult.Error.INTERNAL,
                    "The action could not be processed.");
        }
    }

    /**
     * Start or stop the loaded Resonators bound to one of the requester's own
     * channels. Defense in depth: the bridge already enforced online-only via
     * the {@code allowOffline} default, but the handler re-checks — a link
     * action must never act for a player who is not actually in the game.
     */
    private static LinkActionResult transport(MinecraftServer server, UUID playerId, JsonObject params,
            boolean start) {
        if (NotesLinkAccess.online(server, playerId) == null) {
            return LinkActionResult.error(LinkActionResult.Error.PLAYER_OFFLINE_REQUIRED,
                    "Playback can only be controlled while you are online.");
        }
        ChannelStore store = ChannelStore.get(server);
        String ref = NotesLinkAccess.string(params, "channel");
        LinkActionResult refusal = refusalFor(playerId, ref,
                store.channelsOwnedBy(playerId), store.channelsTrusting(playerId));
        if (refusal != null) {
            return refusal;
        }
        ResonanceChannel channel = NotesLinkAccess.controllableChannel(playerId, ref,
                store.channelsOwnedBy(playerId), store.channelsTrusting(playerId)).orElseThrow();

        ServerLevel level = levelOf(server, channel.dimension());
        List<ResonatorBlockEntity> resonators = level == null
                ? List.of()
                : ResonatorIndex.boundTo(channel.dimension(), channel.key());
        if (resonators.isEmpty()) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION,
                    "No loaded Resonator is bound to that channel right now. Resonators only respond "
                            + "while their chunk is loaded.");
        }

        int affected = 0;
        boolean capRefused = false;
        boolean denied = false;
        for (ResonatorBlockEntity resonator : resonators) {
            SignalResult result = start
                    ? resonator.startPlaybackAs(playerId)
                    : resonator.stopPlaybackAs(playerId);
            switch (result) {
                case OK -> affected++;
                case CHANNEL_CAP_REACHED -> capRefused = true;
                case DENIED -> denied = true;
                default -> {
                    // NOT_PLAYING (no disk / already stopped) and the rest are
                    // per-resonator no-ops, not action failures.
                }
            }
        }
        if (affected == 0) {
            if (denied) {
                // Trust revoked between resolution and transport — the store is authoritative.
                return LinkActionResult.error(LinkActionResult.Error.NOT_OWNER,
                        "You no longer control that channel.");
            }
            if (capRefused) {
                return LinkActionResult.error(LinkActionResult.Error.VALIDATION,
                        "The audio limit for that area is reached; playback was quietly refused.");
            }
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION, start
                    ? "No Resonator on that channel has a disk to play."
                    : "Nothing on that channel is playing.");
        }

        JsonObject state = new JsonObject();
        state.addProperty("schema_version", NotesLinkModule.SCHEMA_VERSION);
        state.addProperty("channel", ref);
        state.addProperty("name", channel.name());
        state.addProperty("dimension", channel.dimension());
        state.addProperty("playing", start);
        state.addProperty("resonators_affected", affected);
        return LinkActionResult.ok(state);
    }

    /**
     * The pure refusal decision for a transport request, shared by both
     * actions and directly unit-testable: {@code null} means "authorised,
     * proceed". A missing/blank reference is a {@code VALIDATION} error; a
     * reference that does not resolve inside the requester's OWN
     * owned-or-trusted channels — whether it belongs to someone else or to
     * nobody — is the single indistinguishable {@code NOT_OWNER} refusal.
     */
    @Nullable
    static LinkActionResult refusalFor(UUID requester, @Nullable String channelRef,
            List<ResonanceChannel> owned, List<ResonanceChannel> trusted) {
        if (channelRef == null || channelRef.isBlank()) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION,
                    "The 'channel' parameter must be a channel id from your own channels section.");
        }
        Optional<ResonanceChannel> resolved =
                NotesLinkAccess.controllableChannel(requester, channelRef, owned, trusted);
        if (resolved.isEmpty()) {
            return LinkActionResult.error(LinkActionResult.Error.NOT_OWNER,
                    "You do not own and are not trusted on a channel with that id.");
        }
        return null;
    }

    /** The level for a channel's dimension id string, or {@code null} when that dimension is not loaded. */
    @Nullable
    private static ServerLevel levelOf(MinecraftServer server, String dimensionId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (ResonanceService.dimensionId(level).equals(dimensionId)) {
                return level;
            }
        }
        return null;
    }
}
