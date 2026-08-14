package za.co.neroland.neronotes.soundforge;

import java.util.ArrayList;
import java.util.List;

import za.co.neroland.neronotes.score.Score;

/**
 * The pure, plain-JVM heart of the sequencer: applies one {@link SequencerEdit}
 * to an immutable {@link Score}, producing either a new score or a quiet
 * refusal. <strong>All bounds are enforced here, server-side</strong> — the
 * client only ever proposes edits, and a hostile client proposing absurd
 * values gets a refusal, never a partial application.
 *
 * <p>The bounds are deliberate scope caps for 0.1.0: at most
 * {@value #MAX_LAYERS} layers (matching the four pattern walls on the
 * Soundforge platform), ticks below {@value #MAX_TICK}, and at most
 * {@value #MAX_NOTES_PER_LAYER} notes per layer — which keeps every possible
 * session comfortably under the wire ceiling.</p>
 *
 * <p>{@code SET_ACTIVE_LAYER} and the preview ops are session/lectern state,
 * not score edits — they are handled by {@code SequencerSessions} and the
 * lectern block entity, and refused here.</p>
 */
public final class SessionEditor {

    /** Maximum layers per session score (one per pattern wall). */
    public static final int MAX_LAYERS = 4;
    /** Exclusive bound on note/loop tick positions. */
    public static final int MAX_TICK = 16384;
    /**
     * Maximum notes in one layer. Chosen so even {@link #MAX_LAYERS} full
     * layers (16 bytes per packed note + NBT overhead) stay below the 64 KiB
     * wire ceiling — asserted in the test suite.
     */
    public static final int MAX_NOTES_PER_LAYER = 1000;
    /** Velocity given to grid-entered notes. */
    public static final int DEFAULT_VELOCITY = 100;
    /** Length in score ticks given to grid-entered notes. */
    public static final int DEFAULT_NOTE_LENGTH_TICKS = 2;

    /** Why an edit was refused. Refusals are quiet; the caller resyncs. */
    public enum Error {
        NONE,
        /** An argument is outside its valid range. */
        OUT_OF_RANGE,
        /** Adding a layer past {@link #MAX_LAYERS}. */
        LAYER_LIMIT,
        /** Removing the last remaining layer. */
        LAST_LAYER,
        /** Adding a note past {@link #MAX_NOTES_PER_LAYER}. */
        NOTE_LIMIT,
        /** An op this editor does not handle (session/preview ops). */
        UNSUPPORTED_OP
    }

    /** Result: the (possibly unchanged) score plus the error, if any. */
    public record Result(Score score, Error error) {

        public boolean ok() {
            return error == Error.NONE;
        }

        static Result ok(Score score) {
            return new Result(score, Error.NONE);
        }

        static Result refuse(Score unchanged, Error error) {
            return new Result(unchanged, error);
        }
    }

    private SessionEditor() {
    }

    /** Apply one edit. Never mutates {@code score}; never throws on bad input. */
    public static Result apply(Score score, SequencerEdit edit) {
        return switch (edit.op()) {
            case SET_TEMPO -> setTempo(score, edit.a());
            case SET_LOOP -> setLoop(score, edit.a(), edit.b());
            case ADD_LAYER -> addLayer(score, edit.voiceId());
            case REMOVE_LAYER -> removeLayer(score, edit.a());
            case SET_LAYER_VOICE -> setLayerVoice(score, edit.a(), edit.voiceId());
            case TOGGLE_NOTE -> toggleNote(score, edit.a(), edit.b(), edit.c());
            case SET_ACTIVE_LAYER, PREVIEW_START, PREVIEW_STOP ->
                    Result.refuse(score, Error.UNSUPPORTED_OP);
        };
    }

    private static Result setTempo(Score score, int tempoBpm) {
        if (tempoBpm < Score.MIN_TEMPO_BPM || tempoBpm > Score.MAX_TEMPO_BPM) {
            return Result.refuse(score, Error.OUT_OF_RANGE);
        }
        return Result.ok(new Score(score.formatVersion(), tempoBpm, score.ticksPerBeat(),
                score.loopStartTick(), score.loopEndTick(), score.layers()));
    }

    private static Result setLoop(Score score, int startTick, int endTick) {
        boolean clear = startTick == 0 && endTick == 0;
        if (!clear && (startTick < 0 || endTick <= startTick || endTick > MAX_TICK)) {
            return Result.refuse(score, Error.OUT_OF_RANGE);
        }
        return Result.ok(new Score(score.formatVersion(), score.tempoBpm(), score.ticksPerBeat(),
                clear ? 0 : startTick, clear ? 0 : endTick, score.layers()));
    }

    private static Result addLayer(Score score, String voiceId) {
        if (voiceId == null || voiceId.isBlank()) {
            return Result.refuse(score, Error.OUT_OF_RANGE);
        }
        if (score.layers().size() >= MAX_LAYERS) {
            return Result.refuse(score, Error.LAYER_LIMIT);
        }
        List<Score.Layer> layers = new ArrayList<>(score.layers());
        layers.add(new Score.Layer(voiceId, List.of()));
        return Result.ok(withLayers(score, layers));
    }

    private static Result removeLayer(Score score, int layerIndex) {
        if (layerIndex < 0 || layerIndex >= score.layers().size()) {
            return Result.refuse(score, Error.OUT_OF_RANGE);
        }
        if (score.layers().size() <= 1) {
            return Result.refuse(score, Error.LAST_LAYER);
        }
        List<Score.Layer> layers = new ArrayList<>(score.layers());
        layers.remove(layerIndex);
        return Result.ok(withLayers(score, layers));
    }

    private static Result setLayerVoice(Score score, int layerIndex, String voiceId) {
        if (layerIndex < 0 || layerIndex >= score.layers().size()
                || voiceId == null || voiceId.isBlank()) {
            return Result.refuse(score, Error.OUT_OF_RANGE);
        }
        List<Score.Layer> layers = new ArrayList<>(score.layers());
        layers.set(layerIndex, new Score.Layer(voiceId, layers.get(layerIndex).notes()));
        return Result.ok(withLayers(score, layers));
    }

    private static Result toggleNote(Score score, int layerIndex, int tick, int pitch) {
        if (layerIndex < 0 || layerIndex >= score.layers().size()
                || tick < 0 || tick >= MAX_TICK
                || pitch < Score.MIN_PITCH || pitch > Score.MAX_PITCH) {
            return Result.refuse(score, Error.OUT_OF_RANGE);
        }
        Score.Layer layer = score.layers().get(layerIndex);
        List<Score.Note> notes = new ArrayList<>(layer.notes());
        boolean removed = notes.removeIf(note -> note.tick() == tick && note.pitch() == pitch);
        if (!removed) {
            if (notes.size() >= MAX_NOTES_PER_LAYER) {
                return Result.refuse(score, Error.NOTE_LIMIT);
            }
            notes.add(new Score.Note(tick, pitch, DEFAULT_VELOCITY, DEFAULT_NOTE_LENGTH_TICKS));
            notes.sort((left, right) -> left.tick() != right.tick()
                    ? Integer.compare(left.tick(), right.tick())
                    : Integer.compare(left.pitch(), right.pitch()));
        }
        List<Score.Layer> layers = new ArrayList<>(score.layers());
        layers.set(layerIndex, new Score.Layer(layer.voiceId(), notes));
        return Result.ok(withLayers(score, layers));
    }

    private static Score withLayers(Score score, List<Score.Layer> layers) {
        return new Score(score.formatVersion(), score.tempoBpm(), score.ticksPerBeat(),
                score.loopStartTick(), score.loopEndTick(), layers);
    }
}
