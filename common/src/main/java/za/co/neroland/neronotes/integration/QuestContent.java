package za.co.neroland.neronotes.integration;

/**
 * The stable ids a NeroQuests pack (or any datapack) can reference to build
 * quest content around NeroNotes — <strong>documentation constants, not an
 * API</strong>. NeroNotes compiles against zero NeroQuests classes and never
 * will: the trigger contract flows entirely through Core's
 * {@code event.ThresholdEvents}, which NeroQuests' {@code custom_event}
 * objective already consumes. Both mods depend only on Core; if NeroQuests is
 * absent the crossings simply have one fewer listener.
 *
 * <p><strong>Triggers</strong> — a quests pack listens for these threshold
 * channels (see {@link NotesThresholds} for thresholds and scopes):</p>
 * <ul>
 *   <li>{@value #THRESHOLD_COMPOSITIONS_PUBLISHED} — the shared library's
 *       server-wide published count crossed a milestone (1, 10, 50, 100,
 *       500, 1000; rising; scope {@code "library"}). "A composition has been
 *       published on this server" is the 0.1.0 compose-loop trigger: a
 *       publish implies a pressed disk, so press-and-publish quests key off
 *       this one channel.</li>
 *   <li>{@value #THRESHOLD_CHANNEL_LISTENERS} — a resonance channel's
 *       listener count crossed a milestone (2, 5, 10, 25; rising; scope = the
 *       dimension id). "Someone drew a crowd."</li>
 * </ul>
 *
 * <p>Threshold crossings are server-wide by design — Core forbids
 * player-identifying scopes — so <em>per-player</em> "compose your first
 * disk" objectives are NeroQuests-side content (e.g. an inventory-change or
 * advancement objective on the disk items below), not something NeroNotes can
 * or should signal through this channel.</p>
 *
 * <p><strong>Rewards</strong> — quest rewards are quests-pack content;
 * NeroNotes just provides the items. Blank disks are the natural early
 * reward (they also have a survival recipe):</p>
 * <ul>
 *   <li>{@value #ITEM_BLANK_DISK} — a blank resonant disk, ready for the
 *       Disk Press or the Disk Exchanger;</li>
 *   <li>{@value #ITEM_CUSTOM_DISK} — a pressed disk; only meaningful with a
 *       {@code neronotes:disk_contents} component, so packs should prefer
 *       granting blank disks.</li>
 * </ul>
 *
 * <p>Voice unlocks as rewards do not exist in 0.1.0 — every voice ships
 * unlocked and the voice registry has no lock concept; a pack must not
 * pretend otherwise.</p>
 *
 * <p><strong>Personal milestones</strong> — since 0.5.0 NeroNotes records two
 * player-scope <em>record</em> gates in Core's gate store (see
 * {@link NotesMilestones}); a pack reads them with a {@code gate_open}
 * objective. They have no {@code requires} and gate nothing — NeroNotes
 * declares no <em>progression</em> gates (standalone-first): the Soundforge
 * needs only a charged Harmonic Gate.</p>
 * <ul>
 *   <li>{@value #GATE_FIRST_PUBLISH} — the player has published a
 *       composition to the shared library (opened on approval when operator
 *       approval is required; anonymous publications count and stay
 *       anonymous).</li>
 *   <li>{@value #GATE_FIRST_BROADCAST} — a listener other than the owner
 *       tuned in to one of the player's resonance channels.</li>
 * </ul>
 */
public final class QuestContent {

    /** {@link NotesThresholds#COMPOSITIONS_PUBLISHED} as a datapack-friendly string. */
    public static final String THRESHOLD_COMPOSITIONS_PUBLISHED = "neronotes:compositions_published";

    /** {@link NotesThresholds#CHANNEL_LISTENERS} as a datapack-friendly string. */
    public static final String THRESHOLD_CHANNEL_LISTENERS = "neronotes:channel_listeners";

    /** {@link NotesMilestones#FIRST_PUBLISH} as a datapack-friendly string. */
    public static final String GATE_FIRST_PUBLISH = "neronotes:first_publish";

    /** {@link NotesMilestones#FIRST_BROADCAST} as a datapack-friendly string. */
    public static final String GATE_FIRST_BROADCAST = "neronotes:first_broadcast";

    /** The blank resonant disk item id. */
    public static final String ITEM_BLANK_DISK = "neronotes:blank_disk";

    /** The pressed custom disk item id (needs a {@code disk_contents} component to play). */
    public static final String ITEM_CUSTOM_DISK = "neronotes:custom_disk";

    private QuestContent() {
    }
}
