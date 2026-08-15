package za.co.neroland.neronotes.integration;

/**
 * Pure rising-edge threshold-crossing detection — the plain-JVM heart of
 * {@link NotesThresholds}, kept free of Minecraft types so the semantics are
 * directly unit-testable.
 *
 * <p>A threshold {@code t} is <em>crossed rising</em> by a value change
 * {@code previous -> current} exactly when {@code previous < t && current >= t}.
 * That single rule gives the three properties the ecosystem contract needs:</p>
 *
 * <ul>
 *   <li><strong>fires once per threshold</strong> — after the crossing,
 *       {@code previous >= t} for every later change while the value stays at
 *       or above {@code t};</li>
 *   <li><strong>no repeat fire</strong> — a value sitting above a threshold
 *       never re-fires it; only dropping back below and rising again does
 *       (a genuine re-crossing);</li>
 *   <li><strong>no fire on an unchanged value</strong> — {@code previous ==
 *       current} can satisfy {@code previous < t <= current} for no
 *       {@code t}.</li>
 * </ul>
 *
 * <p>A single jump across several thresholds (say 0 → 60 over
 * {@code {1, 10, 50}}) reports every threshold passed, each exactly once.</p>
 */
public final class ThresholdCrossings {

    private ThresholdCrossings() {
    }

    /**
     * The thresholds in {@code thresholds} crossed rising by the change
     * {@code previous -> current}, in the array's order. Empty when the value
     * fell, stayed put, or rose without passing any threshold. The input
     * array is never modified.
     */
    public static long[] crossedRising(long previous, long current, long[] thresholds) {
        if (thresholds == null || thresholds.length == 0 || current <= previous) {
            return EMPTY;
        }
        int count = 0;
        for (long threshold : thresholds) {
            if (previous < threshold && current >= threshold) {
                count++;
            }
        }
        if (count == 0) {
            return EMPTY;
        }
        long[] crossed = new long[count];
        int i = 0;
        for (long threshold : thresholds) {
            if (previous < threshold && current >= threshold) {
                crossed[i++] = threshold;
            }
        }
        return crossed;
    }

    private static final long[] EMPTY = new long[0];
}
