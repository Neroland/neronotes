package za.co.neroland.neronotes.score;

/**
 * A serialised score exceeded the size budget. Thrown by the Disk Press path
 * (encode) and by every wire decode BEFORE parsing — an unbounded score
 * payload is a server-crash vector, not a nuisance. The message names both
 * the actual size and the limit; nothing is ever silently truncated.
 */
public final class ScoreSizeException extends ScoreFormatException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final long actualBytes;
    private final int budgetBytes;

    public ScoreSizeException(long actualBytes, int budgetBytes) {
        super("serialised score is " + actualBytes + " bytes, over the " + budgetBytes
                + "-byte budget — refusing (scores are never truncated)");
        this.actualBytes = actualBytes;
        this.budgetBytes = budgetBytes;
    }

    /** The serialised size that broke the budget, in bytes. */
    public long actualBytes() {
        return actualBytes;
    }

    /** The budget that was exceeded, in bytes. */
    public int budgetBytes() {
        return budgetBytes;
    }
}
