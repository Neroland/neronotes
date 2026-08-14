package za.co.neroland.neronotes.soundforge;

/**
 * One bounded, server-validated sequencer edit — the unit the transport
 * lectern's screen sends client→server (wrapped in
 * {@code network/SequencerEditPayload}) and {@link SessionEditor} applies.
 * The client never mutates the session directly; it only ever proposes one
 * of these and re-renders from the server's echoed session state.
 *
 * <p>Deliberately tiny and fixed-shape: an op plus three small ints and an
 * optional voice id, so the wire form stays a few dozen bytes and there is
 * nothing unbounded for a hostile client to inflate.</p>
 *
 * @param op      the operation
 * @param a       first argument (see the factory methods for meaning)
 * @param b       second argument
 * @param c       third argument
 * @param voiceId voice id for the voice ops; {@code ""} otherwise
 */
public record SequencerEdit(Op op, int a, int b, int c, String voiceId) {

    /** Wire cap on the voice id (matches the note payload's cap). */
    public static final int MAX_VOICE_ID_LENGTH = 64;

    /** The sequencer operations. */
    public enum Op {
        /** {@code a} = new tempo in BPM. */
        SET_TEMPO,
        /** {@code a} = loop start tick, {@code b} = loop end tick ({@code 0,0} clears). */
        SET_LOOP,
        /** {@code voiceId} = the new layer's voice. */
        ADD_LAYER,
        /** {@code a} = layer index to remove. */
        REMOVE_LAYER,
        /** {@code a} = layer index, {@code voiceId} = the layer's new voice. */
        SET_LAYER_VOICE,
        /** {@code a} = layer index, {@code b} = tick, {@code c} = pitch — add if absent, remove if present. */
        TOGGLE_NOTE,
        /** {@code a} = the newly active layer index (session state, not score). */
        SET_ACTIVE_LAYER,
        /** Preview playback at the lectern (server-side; no score change). */
        PREVIEW_START,
        /** Stop preview playback. */
        PREVIEW_STOP
    }

    public SequencerEdit {
        if (op == null) {
            throw new IllegalArgumentException("op must not be null");
        }
        if (voiceId == null || voiceId.length() > MAX_VOICE_ID_LENGTH) {
            throw new IllegalArgumentException("voiceId must be non-null and at most "
                    + MAX_VOICE_ID_LENGTH + " characters");
        }
    }

    public static SequencerEdit setTempo(int tempoBpm) {
        return new SequencerEdit(Op.SET_TEMPO, tempoBpm, 0, 0, "");
    }

    public static SequencerEdit setLoop(int startTick, int endTick) {
        return new SequencerEdit(Op.SET_LOOP, startTick, endTick, 0, "");
    }

    public static SequencerEdit addLayer(String voiceId) {
        return new SequencerEdit(Op.ADD_LAYER, 0, 0, 0, voiceId);
    }

    public static SequencerEdit removeLayer(int layerIndex) {
        return new SequencerEdit(Op.REMOVE_LAYER, layerIndex, 0, 0, "");
    }

    public static SequencerEdit setLayerVoice(int layerIndex, String voiceId) {
        return new SequencerEdit(Op.SET_LAYER_VOICE, layerIndex, 0, 0, voiceId);
    }

    public static SequencerEdit toggleNote(int layerIndex, int tick, int pitch) {
        return new SequencerEdit(Op.TOGGLE_NOTE, layerIndex, tick, pitch, "");
    }

    public static SequencerEdit setActiveLayer(int layerIndex) {
        return new SequencerEdit(Op.SET_ACTIVE_LAYER, layerIndex, 0, 0, "");
    }

    public static SequencerEdit previewStart() {
        return new SequencerEdit(Op.PREVIEW_START, 0, 0, 0, "");
    }

    public static SequencerEdit previewStop() {
        return new SequencerEdit(Op.PREVIEW_STOP, 0, 0, 0, "");
    }
}
