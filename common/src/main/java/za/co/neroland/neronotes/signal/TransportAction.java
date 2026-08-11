package za.co.neroland.neronotes.signal;

/**
 * Transport events a channel controller can broadcast (Stage 2 of the
 * resonance signal): {@code play}, {@code stop}, {@code seek}. Note events
 * ({@code note_on} / {@code note_off}) travel separately as
 * {@code network/ResonanceNotePayload}.
 */
public enum TransportAction {

    PLAY((byte) 0),
    STOP((byte) 1),
    SEEK((byte) 2);

    private final byte wireId;

    TransportAction(byte wireId) {
        this.wireId = wireId;
    }

    /** Stable single-byte wire id. */
    public byte wireId() {
        return wireId;
    }

    /** Decode a wire id; throws on unknown values so the codec never guesses. */
    public static TransportAction fromWireId(byte id) {
        for (TransportAction action : values()) {
            if (action.wireId == id) {
                return action;
            }
        }
        throw new IllegalArgumentException("unknown transport action wire id " + id);
    }
}
