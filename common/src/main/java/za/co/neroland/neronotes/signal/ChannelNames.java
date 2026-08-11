package za.co.neroland.neronotes.signal;

/**
 * Server-side validation for player-chosen channel names. Channel names are
 * untrusted free text (locked design decision 6 applies to every
 * player-authored string in this mod): they are validated here at create and
 * rename time, capped again on the wire
 * ({@code network/ResonanceNotePayload}), and never logged at info level nor
 * sent to telemetry.
 */
public final class ChannelNames {

    /** Maximum channel-name length in characters; also the wire cap. */
    public static final int MAX_LENGTH = 32;

    private ChannelNames() {
    }

    /**
     * Whether {@code name} is acceptable as a channel name: non-blank, at
     * most {@link #MAX_LENGTH} characters, no control characters and no
     * legacy formatting codes ({@code §}).
     */
    public static boolean isValid(String name) {
        if (name == null || name.isBlank() || name.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c == 0x7F || c == '§') {
                return false;
            }
        }
        return true;
    }
}
