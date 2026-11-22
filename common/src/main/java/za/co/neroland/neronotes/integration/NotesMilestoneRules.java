package za.co.neroland.neronotes.integration;

/**
 * Pure rung decisions for {@link NotesMilestones}, free of game classes so they
 * can be unit tested. Every input is data NeroNotes already holds (the live
 * subscriber sets and the library table); nothing here adds a stored counter.
 */
public final class NotesMilestoneRules {

    /** Listener rungs for one channel (listeners other than its owner). */
    public static final int[] LISTENER_RUNGS = {10, 25};

    /**
     * Published rungs for one author (visible library entries). 25 is the
     * default {@code library.per_player_quota}, so the top rung is reachable
     * on a default server.
     */
    public static final int[] PUBLISHED_RUNGS = {10, 25};

    private NotesMilestoneRules() {
    }

    /** Whether a count moving from {@code before} to {@code after} crossed {@code rung} upwards. */
    public static boolean crossed(int before, int after, int rung) {
        return before < rung && after >= rung;
    }

    /** Whether {@code count} has reached {@code rung}. */
    public static boolean reached(int count, int rung) {
        return count >= rung;
    }
}
