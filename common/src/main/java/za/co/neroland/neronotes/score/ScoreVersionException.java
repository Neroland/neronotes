package za.co.neroland.neronotes.score;

/**
 * The score was written by a NEWER format version than this release
 * understands. Rejected by name rather than partially parsed — guessing at
 * fields a future version added is how a disk silently loses data.
 */
public final class ScoreVersionException extends ScoreFormatException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final int scoreVersion;
    private final int supportedVersion;

    public ScoreVersionException(int scoreVersion, int supportedVersion) {
        super("score format version " + scoreVersion + " is newer than the newest supported version "
                + supportedVersion + " — refusing to guess at a partial parse (update NeroNotes to read this score)");
        this.scoreVersion = scoreVersion;
        this.supportedVersion = supportedVersion;
    }

    /** The format version the rejected score declared. */
    public int scoreVersion() {
        return scoreVersion;
    }

    /** The newest format version this release can read. */
    public int supportedVersion() {
        return supportedVersion;
    }
}
