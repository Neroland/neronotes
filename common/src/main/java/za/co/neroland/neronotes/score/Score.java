package za.co.neroland.neronotes.score;

import java.util.List;

/**
 * The versioned NeroNotes score — <strong>music is data, not audio</strong>.
 * A composition is this compact record, rendered client-side from registered
 * sound events; the server stores and syncs scores only and never streams
 * audio.
 *
 * <p>Deliberately minimal for 0.1.0 (locked design decision 1): tempo, tick
 * resolution, loop points and an ordered list of layers, each layer a voice id
 * plus notes of {@code (tick, pitch, velocity, lengthTicks)}. <em>No
 * envelopes, filters, automation or patch data.</em> {@link #formatVersion}
 * exists so a later release can add them without breaking existing disks —
 * {@link ScoreCodec} rejects a newer version with a named error rather than
 * guessing.</p>
 *
 * <p>All collections are defensively copied and immutable; records validate
 * their ranges on construction, so an in-memory {@code Score} is always
 * structurally sound. Serialisation and the size budget live in
 * {@link ScoreCodec}.</p>
 *
 * <p><strong>Timing grid.</strong> One score tick lasts
 * {@code 60000 / (tempoBpm * ticksPerBeat)} milliseconds, but Minecraft can
 * only emit and schedule audio on whole game ticks (50&nbsp;ms, 20&nbsp;TPS)
 * — on both sides: the server Resonator emits notes on game ticks and the
 * client schedules them via the sound engine's game-tick delay queue. Any
 * tempo/resolution combination is <em>valid</em>, but only combinations
 * whose tick duration is a whole multiple of 50&nbsp;ms play back exactly as
 * written; anything else quantises each note to the nearest game tick (up to
 * &plusmn;50&nbsp;ms — an audible limp on fast material). Tick-perfect
 * combos are exactly those where {@code tempoBpm * ticksPerBeat} divides
 * 1200: e.g. 150&nbsp;BPM &times; 4 or 120 &times; 5 (product 600 &rarr;
 * 100&nbsp;ms = 2 game ticks), 100 &times; 4 (400 &rarr; 150&nbsp;ms = 3),
 * 120 &times; 2 (240 &rarr; 250&nbsp;ms = 5), 300 &times; 4 (1200 &rarr;
 * 50&nbsp;ms = 1). 120 &times; 4 (480 &rarr; 125&nbsp;ms = 2.5 game ticks)
 * is the classic near-miss. Documented, not enforced — the sequencer accepts
 * any tempo in range.</p>
 *
 * @param formatVersion score format version; {@link #CURRENT_FORMAT_VERSION}
 *                      for scores authored by this release
 * @param tempoBpm      tempo in beats per minute ({@value #MIN_TEMPO_BPM}–{@value #MAX_TEMPO_BPM})
 * @param ticksPerBeat  score ticks per beat ({@value #MIN_TICKS_PER_BEAT}–{@value #MAX_TICKS_PER_BEAT})
 * @param loopStartTick loop start position in score ticks (>= 0)
 * @param loopEndTick   loop end position in score ticks; {@code 0} means the
 *                      score does not loop, otherwise must be > {@code loopStartTick}
 * @param layers        ordered layers; may be empty (an empty score is valid)
 */
public record Score(int formatVersion, int tempoBpm, int ticksPerBeat,
                    int loopStartTick, int loopEndTick, List<Layer> layers) {

    /** The score format version written by this release. */
    public static final int CURRENT_FORMAT_VERSION = 1;

    public static final int MIN_TEMPO_BPM = 1;
    public static final int MAX_TEMPO_BPM = 960;
    public static final int MIN_TICKS_PER_BEAT = 1;
    public static final int MAX_TICKS_PER_BEAT = 192;
    /** Pitch is a MIDI-style note number: 0–127. Voice pitch bands live inside this range. */
    public static final int MIN_PITCH = 0;
    public static final int MAX_PITCH = 127;
    /** Velocity 0 (silent) – 127 (full). */
    public static final int MIN_VELOCITY = 0;
    public static final int MAX_VELOCITY = 127;

    public Score {
        requireRange("formatVersion", formatVersion, 1, Integer.MAX_VALUE);
        requireRange("tempoBpm", tempoBpm, MIN_TEMPO_BPM, MAX_TEMPO_BPM);
        requireRange("ticksPerBeat", ticksPerBeat, MIN_TICKS_PER_BEAT, MAX_TICKS_PER_BEAT);
        requireRange("loopStartTick", loopStartTick, 0, Integer.MAX_VALUE);
        requireRange("loopEndTick", loopEndTick, 0, Integer.MAX_VALUE);
        if (loopEndTick != 0 && loopEndTick <= loopStartTick) {
            throw new IllegalArgumentException(
                    "loopEndTick (" + loopEndTick + ") must be 0 (no loop) or greater than loopStartTick (" + loopStartTick + ")");
        }
        layers = List.copyOf(layers);
    }

    /** Whether the score defines a loop region. */
    public boolean hasLoop() {
        return loopEndTick > 0;
    }

    /** Total note count across all layers. */
    public int noteCount() {
        int count = 0;
        for (Layer layer : layers) {
            count += layer.notes().size();
        }
        return count;
    }

    /**
     * An ordered layer: one voice playing a sequence of notes. The voice id is
     * resolved through the {@code voice/VoiceRegistry} at play time (with a
     * defined fallback for unknown ids) — a score never references a
     * {@code SoundEvent} directly, so it stays a plain data record.
     *
     * @param voiceId voice id, e.g. {@code neronotes:void_bass}; never blank
     * @param notes   the layer's notes, kept in the order provided
     */
    public record Layer(String voiceId, List<Note> notes) {

        public Layer {
            if (voiceId == null || voiceId.isBlank()) {
                throw new IllegalArgumentException("voiceId must not be blank");
            }
            notes = List.copyOf(notes);
        }
    }

    /**
     * A single note event.
     *
     * @param tick        position in score ticks (>= 0)
     * @param pitch       MIDI-style note number ({@value Score#MIN_PITCH}–{@value Score#MAX_PITCH})
     * @param velocity    {@value Score#MIN_VELOCITY}–{@value Score#MAX_VELOCITY}
     * @param lengthTicks note length in score ticks (>= 1)
     */
    public record Note(int tick, int pitch, int velocity, int lengthTicks) {

        public Note {
            requireRange("tick", tick, 0, Integer.MAX_VALUE);
            requireRange("pitch", pitch, MIN_PITCH, MAX_PITCH);
            requireRange("velocity", velocity, MIN_VELOCITY, MAX_VELOCITY);
            requireRange("lengthTicks", lengthTicks, 1, Integer.MAX_VALUE);
        }
    }

    private static void requireRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    name + " (" + value + ") out of range [" + min + ", "
                            + (max == Integer.MAX_VALUE ? "unbounded" : String.valueOf(max)) + "]");
        }
    }
}
