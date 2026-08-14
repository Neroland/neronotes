package za.co.neroland.neronotes.soundforge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.score.ScoreCodec;
import za.co.neroland.neronotes.score.ScoreFormatException;
import za.co.neroland.neronotes.voice.VoiceDefinition;
import za.co.neroland.neronotes.voice.VoiceFamily;
import za.co.neroland.neronotes.voice.VoiceRegistry;

/**
 * Server-side access to a player's <strong>sequencer session</strong>: the
 * session score plus the active-layer selection, persisted in the opaque
 * per-player session-data slot of {@link SoundforgeSessionStore} (behind
 * Core's {@code SavedDataRecovery}; the row is created when the player first
 * crosses into the Soundforge).
 *
 * <p>Everything here is server-authoritative. Edits arrive as
 * {@link SequencerEdit}s, are applied through the pure {@link SessionEditor},
 * validated against the {@link VoiceRegistry} where they name a voice, and
 * only then persisted. A refusal leaves the stored session untouched — the
 * caller resyncs the client from the authoritative state either way.</p>
 */
public final class SequencerSessions {

    private static final String KEY_SCORE = "score";
    private static final String KEY_ACTIVE_LAYER = "active_layer";

    private SequencerSessions() {
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    /**
     * The player's session score. A missing row, missing score or unreadable
     * score yields a fresh default (120 BPM, 4 ticks per beat, one empty
     * layer on the first registered voice) — never {@code null}, never a
     * throw; an unreadable stored score is logged at WARN without detail.
     */
    public static Score sessionScore(MinecraftServer server, UUID player) {
        Optional<CompoundTag> data = SoundforgeSessionStore.get(server).sessionData(player);
        if (data.isPresent()) {
            Optional<CompoundTag> scoreTag = data.get().getCompound(KEY_SCORE);
            if (scoreTag.isPresent()) {
                try {
                    return ScoreCodec.fromNbt(scoreTag.get());
                } catch (ScoreFormatException unreadable) {
                    NeroNotesCommon.LOGGER.warn(
                            "[NeroNotes] a stored sequencer session score was unreadable ({}); starting fresh",
                            unreadable.getClass().getSimpleName());
                }
            }
        }
        return defaultScore();
    }

    /** The player's active layer index, clamped to the session's layer count. */
    public static int activeLayer(MinecraftServer server, UUID player) {
        int stored = SoundforgeSessionStore.get(server).sessionData(player)
                .flatMap(tag -> tag.getInt(KEY_ACTIVE_LAYER))
                .orElse(0);
        int layerCount = sessionScore(server, player).layers().size();
        return Math.max(0, Math.min(stored, layerCount - 1));
    }

    /** A fresh default session score. */
    public static Score defaultScore() {
        return new Score(Score.CURRENT_FORMAT_VERSION, 120, 4, 0, 0,
                List.of(new Score.Layer(firstVoiceId(), List.of())));
    }

    /** The first registered non-fallback voice id (the default layer voice). */
    public static String firstVoiceId() {
        for (String id : VoiceRegistry.shared().voiceIds()) {
            if (!VoiceRegistry.FALLBACK_VOICE_ID.equals(id)) {
                return id;
            }
        }
        return VoiceRegistry.FALLBACK_VOICE_ID;
    }

    // ------------------------------------------------------------------
    // Mutations (server-authoritative; quiet refusals)
    // ------------------------------------------------------------------

    /**
     * Apply one score edit: voice-naming ops are checked against the voice
     * registry first, then the pure editor applies, then the result persists.
     * Returns the authoritative post-edit score (unchanged on refusal), or
     * empty when the player has no session row at all (never entered the
     * Soundforge).
     */
    public static Optional<Score> applyEdit(MinecraftServer server, UUID player, SequencerEdit edit) {
        if (!SoundforgeSessionStore.get(server).hasRow(player)) {
            return Optional.empty();
        }
        Score current = sessionScore(server, player);
        if (namesUnknownVoice(edit)) {
            return Optional.of(current); // quiet refusal — the resync corrects the client
        }
        SessionEditor.Result result = SessionEditor.apply(current, edit);
        if (result.ok()) {
            persist(server, player, result.score(), activeLayerAfter(server, player, edit, result.score()));
        }
        return Optional.of(result.ok() ? result.score() : current);
    }

    /**
     * Select the active layer (session state, not a score edit). Returns
     * false when the index is out of range or the player has no session row.
     */
    public static boolean setActiveLayer(MinecraftServer server, UUID player, int layerIndex) {
        SoundforgeSessionStore store = SoundforgeSessionStore.get(server);
        if (!store.hasRow(player)) {
            return false;
        }
        Score score = sessionScore(server, player);
        if (layerIndex < 0 || layerIndex >= score.layers().size()) {
            return false;
        }
        persist(server, player, score, layerIndex);
        return true;
    }

    /**
     * Cycle the active layer's voice to the next voice of {@code family}
     * (voice-pedestal interaction). Returns the new voice id, or empty when
     * the family has no voices or the player has no session.
     */
    public static Optional<String> cycleActiveLayerVoice(MinecraftServer server, UUID player, VoiceFamily family) {
        if (!SoundforgeSessionStore.get(server).hasRow(player)) {
            return Optional.empty();
        }
        List<VoiceDefinition> voices = VoiceRegistry.shared().byFamily(family);
        if (voices.isEmpty()) {
            return Optional.empty();
        }
        Score score = sessionScore(server, player);
        int layer = activeLayer(server, player);
        String current = score.layers().get(layer).voiceId();
        int at = -1;
        for (int i = 0; i < voices.size(); i++) {
            if (voices.get(i).voiceId().equals(current)) {
                at = i;
                break;
            }
        }
        String next = voices.get((at + 1) % voices.size()).voiceId();
        SessionEditor.Result result = SessionEditor.apply(score, SequencerEdit.setLayerVoice(layer, next));
        if (!result.ok()) {
            return Optional.empty();
        }
        persist(server, player, result.score(), layer);
        return Optional.of(next);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** The active layer to persist after {@code edit} (removal shifts it; add selects the new layer). */
    private static int activeLayerAfter(MinecraftServer server, UUID player, SequencerEdit edit, Score after) {
        int current = activeLayer(server, player);
        int clampedMax = after.layers().size() - 1;
        return switch (edit.op()) {
            case ADD_LAYER -> clampedMax;
            case REMOVE_LAYER -> Math.min(edit.a() <= current ? Math.max(0, current - 1) : current, clampedMax);
            default -> Math.min(current, clampedMax);
        };
    }

    private static void persist(MinecraftServer server, UUID player, Score score, int activeLayer) {
        SoundforgeSessionStore store = SoundforgeSessionStore.get(server);
        CompoundTag data = store.sessionData(player).map(CompoundTag::copy).orElseGet(CompoundTag::new);
        data.put(KEY_SCORE, ScoreCodec.toNbt(score));
        data.putInt(KEY_ACTIVE_LAYER, activeLayer);
        store.putSessionData(player, data);
    }

    /** Whether the edit names a voice the registry does not know. */
    private static boolean namesUnknownVoice(SequencerEdit edit) {
        return switch (edit.op()) {
            case ADD_LAYER, SET_LAYER_VOICE ->
                    VoiceRegistry.shared().lookup(edit.voiceId()).isEmpty();
            default -> false;
        };
    }
}
