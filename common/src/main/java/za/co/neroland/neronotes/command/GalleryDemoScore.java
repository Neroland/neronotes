package za.co.neroland.neronotes.command;

import java.util.ArrayList;
import java.util.List;

import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.score.Score.Layer;
import za.co.neroland.neronotes.score.Score.Note;

/**
 * The fixed demo composition the {@code /neronotes gallery} Resonator plays —
 * a four-layer, four-bar loop written entirely in code (no disk, no
 * player-authored data): a four-on-the-floor kick with off-beat accents, a
 * bass line locked to the kick, a syncopated lead motif and tight off-beat
 * plucks, with loop points set so it repeats indefinitely until stopped.
 *
 * <p><strong>Tick-perfect on purpose.</strong> Playback quantises to the
 * 50&nbsp;ms game-tick grid (see the timing note on {@link Score}), so the
 * demo uses {@value #TEMPO_BPM}&nbsp;BPM at {@value #TICKS_PER_BEAT} score
 * ticks per beat: one score tick = 60000 / (150 &times; 4) = 100&nbsp;ms =
 * exactly 2 game ticks. Every note therefore lands exactly on the grid —
 * including the syncopation and the off-beats. The first gallery cut ran
 * 120&nbsp;BPM &times; 4 (125&nbsp;ms = 2.5 game ticks), which forced
 * alternate notes 100/150&nbsp;ms apart: an audible limp.</p>
 *
 * <p>Deliberately representative of what the in-game sequencer can produce:
 * at most {@code SessionEditor.MAX_LAYERS} (4) layers, every voice id taken
 * from the bundled default voice pack, every pitch inside its voice's band,
 * and a serialised size far under the disk budget. Plain-JVM: only the score
 * model is referenced, so the unit tests exercise this class directly.</p>
 *
 * <p>Structure: a 4/4 bar is 16 ticks; the loop spans four bars
 * (0&ndash;{@value #LOOP_END_TICK}, end exclusive — no note sits at tick
 * {@value #LOOP_END_TICK}, which IS tick 0 of the next pass). Harmony is a
 * C-minor i&ndash;iv&ndash;v&ndash;III walk (roots 36, 41, 43, 39) with a
 * chromatic bass pickup into each bar, so the wrap resolves onto the
 * downbeat instead of stuttering.</p>
 */
public final class GalleryDemoScore {

    public static final int TEMPO_BPM = 150;
    public static final int TICKS_PER_BEAT = 4;
    public static final int LOOP_START_TICK = 0;
    public static final int LOOP_END_TICK = 64; // four 4/4 bars of 16 ticks, exclusive

    /** The four bundled voices the demo plays (one layer each). */
    public static final String PERCUSSION_VOICE = "neronotes:plasma_kick";
    public static final String BASS_VOICE = "neronotes:void_bass";
    public static final String LEAD_VOICE = "neronotes:pulse_lead";
    public static final String PLUCK_VOICE = "neronotes:crystal_pluck";

    /** Bar roots of the i–iv–v–III walk (C minor: C, F, G, E♭). */
    private static final int[] BAR_ROOTS = {36, 41, 43, 39};

    /** Chromatic pickup into the NEXT bar's root, played on tick 14 of each bar. */
    private static final int[] BAR_PICKUPS = {40, 42, 40, 37};

    private GalleryDemoScore() {
    }

    /** Build the demo score. Always structurally valid — the record validates on construction. */
    public static Score build() {
        List<Note> percussion = new ArrayList<>();
        List<Note> bass = new ArrayList<>();

        for (int bar = 0; bar < 4; bar++) {
            int at = bar * 16;
            int root = BAR_ROOTS[bar];

            // Four-on-the-floor kicks with off-beat eighth accents — the
            // accents now land exactly halfway between kicks (200 ms grid).
            for (int beat = 0; beat < 4; beat++) {
                percussion.add(new Note(at + beat * 4, 36, 120, 2));
                percussion.add(new Note(at + beat * 4 + 2, 52, 72, 1));
            }

            // Bass locked to the kick: root on every beat, a lift to the
            // fifth on beat four, then the chromatic pickup into the next bar.
            bass.add(new Note(at, root, 112, 3));
            bass.add(new Note(at + 4, root, 100, 3));
            bass.add(new Note(at + 8, root, 108, 3));
            bass.add(new Note(at + 12, Math.min(48, root + 7), 96, 2));
            bass.add(new Note(at + 14, BAR_PICKUPS[bar], 92, 2));
        }

        // The lead motif: rises over bars one to three, resolves in bar four.
        // Sixteenth-note syncopation (ticks 3, 35, 51) is exact on this grid.
        List<Note> lead = List.of(
                new Note(0, 72, 96, 2), new Note(3, 75, 84, 1), new Note(4, 79, 96, 4),
                new Note(10, 77, 88, 2), new Note(12, 75, 92, 2), new Note(14, 72, 84, 2),
                new Note(16, 77, 96, 4), new Note(22, 79, 88, 2), new Note(24, 82, 96, 4),
                new Note(30, 79, 84, 2),
                new Note(32, 84, 100, 2), new Note(35, 82, 84, 1), new Note(36, 79, 96, 4),
                new Note(42, 77, 88, 2), new Note(44, 75, 92, 2), new Note(46, 72, 84, 2),
                new Note(48, 77, 92, 4), new Note(54, 75, 88, 2), new Note(56, 72, 96, 6));

        // Glassy plucks doubling the off-beats with chord tones — tight now
        // that the off-beats are exact.
        List<Note> plucks = List.of(
                new Note(6, 84, 68, 1), new Note(14, 91, 68, 1),
                new Note(22, 89, 68, 1), new Note(30, 84, 68, 1),
                new Note(38, 91, 68, 1), new Note(46, 86, 68, 1),
                new Note(54, 87, 68, 1), new Note(62, 84, 68, 1));

        return new Score(Score.CURRENT_FORMAT_VERSION, TEMPO_BPM, TICKS_PER_BEAT,
                LOOP_START_TICK, LOOP_END_TICK, List.of(
                        new Layer(PERCUSSION_VOICE, List.copyOf(percussion)),
                        new Layer(BASS_VOICE, List.copyOf(bass)),
                        new Layer(LEAD_VOICE, lead),
                        new Layer(PLUCK_VOICE, plucks)));
    }
}
