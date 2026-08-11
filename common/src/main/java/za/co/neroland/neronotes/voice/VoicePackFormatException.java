package za.co.neroland.neronotes.voice;

/**
 * A voice definition file failed to parse. Unchecked: the bundled default
 * pack failing is a packaging bug that should fail loud at init, and callers
 * merging external packs catch this one type to reject the pack whole —
 * a half-merged voice pack is worse than a rejected one.
 */
public final class VoicePackFormatException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public VoicePackFormatException(String message) {
        super(message);
    }

    public VoicePackFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
