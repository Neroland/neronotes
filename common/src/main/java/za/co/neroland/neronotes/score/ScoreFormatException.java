package za.co.neroland.neronotes.score;

/**
 * A score failed to decode. Checked on purpose: every reader of untrusted
 * score data (disk NBT, wire payloads) must handle failure explicitly — a
 * silent partial parse is the failure mode this hierarchy exists to prevent.
 *
 * <p>Subtypes name the two policy failures: {@link ScoreVersionException}
 * (score written by a newer format) and {@link ScoreSizeException} (over the
 * serialised-size budget). The base type covers structural corruption.</p>
 */
public class ScoreFormatException extends Exception {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public ScoreFormatException(String message) {
        super(message);
    }

    public ScoreFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
