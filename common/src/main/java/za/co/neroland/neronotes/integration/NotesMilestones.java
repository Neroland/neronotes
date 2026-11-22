package za.co.neroland.neronotes.integration;

import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerolandcore.progression.ProgressionGates;
import za.co.neroland.neronotes.NeroNotesCommon;

/**
 * NeroNotes' <em>personal</em> milestones — player-scope Neroland Core record
 * gates, the per-player counterpart of the server-wide crossings in
 * {@link NotesThresholds}. A crossing can never credit one player (its scope
 * is a place or system key), so "this player has published something" and
 * "someone tuned in to this player's channel" are recorded here instead, in
 * Core's gate store, and a quest pack reads them with a {@code gate_open}
 * objective. No compile edge to any quest mod exists in either direction.
 *
 * <p>Both gates are <strong>record gates</strong>: shipped under
 * {@code data/neronotes/neroland_gates/} with no {@code requires}, opened
 * from the paths below and <strong>never checked for access</strong> to
 * anything — nothing in NeroNotes is progression-gated.</p>
 *
 * <p><strong>Privacy (POPIA / GDPR):</strong> the owner UUID goes into
 * {@code ProgressionGates.open} and nowhere else — not into a log, a
 * crossing or a broadcast. The gate records only that the player has
 * published <em>something</em>, never which composition, so an anonymous
 * publication stays anonymous; Core's gate store is covered by Core's own
 * privacy notice and erasure.</p>
 */
public final class NotesMilestones {

    /** Opened for a composer the first time one of their disks is published (or approved). */
    public static final Identifier FIRST_PUBLISH =
            Identifier.fromNamespaceAndPath(NeroNotesCommon.MOD_ID, "first_publish");

    /** Opened for a channel owner the first time a listener other than them tunes in. */
    public static final Identifier FIRST_BROADCAST =
            Identifier.fromNamespaceAndPath(NeroNotesCommon.MOD_ID, "first_broadcast");

    /** Opened for a player the first time they cross into the Soundforge. */
    public static final Identifier ENTERED_SOUNDFORGE =
            Identifier.fromNamespaceAndPath(NeroNotesCommon.MOD_ID, "entered_soundforge");

    private NotesMilestones() {
    }

    /** The rung gate {@code neronotes:listeners/<rung>}. */
    public static Identifier listenersGate(int rung) {
        return Identifier.fromNamespaceAndPath(NeroNotesCommon.MOD_ID, "listeners/" + rung);
    }

    /** The rung gate {@code neronotes:published/<rung>}. */
    public static Identifier publishedGate(int rung) {
        return Identifier.fromNamespaceAndPath(NeroNotesCommon.MOD_ID, "published/" + rung);
    }

    /**
     * One of {@code owner}'s channels went from {@code before} to {@code after}
     * listeners other than the owner: open every listener rung it crossed. The
     * count comes from the live, in-memory subscriber set; nothing is stored.
     */
    public static void listeners(MinecraftServer server, UUID owner, int before, int after) {
        for (int rung : NotesMilestoneRules.LISTENER_RUNGS) {
            if (NotesMilestoneRules.crossed(before, after, rung)) {
                ProgressionGates.open(server, owner, listenersGate(rung));
            }
        }
    }

    /**
     * {@code author} now has {@code visibleCount} visible compositions in the
     * library: open every published rung reached (so earlier publications count
     * the next time they publish). The count comes from the library table.
     */
    public static void publishedCount(MinecraftServer server, UUID author, int visibleCount) {
        for (int rung : NotesMilestoneRules.PUBLISHED_RUNGS) {
            if (NotesMilestoneRules.reached(visibleCount, rung)) {
                ProgressionGates.open(server, author, publishedGate(rung));
            }
        }
    }

    /** Record that {@code player} is in the Soundforge. Idempotent. */
    public static void enteredSoundforge(MinecraftServer server, UUID player) {
        ProgressionGates.open(server, player, ENTERED_SOUNDFORGE);
    }

    /**
     * Record that {@code author} has a composition in the library. Call once
     * the entry is visible: straight after a successful publish, or on
     * operator approval when {@code library.op_approval_required} is on.
     * Offline-capable; idempotent (re-opening an open gate changes nothing).
     */
    public static void published(MinecraftServer server, UUID author) {
        ProgressionGates.open(server, author, FIRST_PUBLISH);
    }

    /**
     * Record that someone other than {@code owner} is listening to one of
     * their channels. The owner need not be online. Idempotent.
     */
    public static void broadcast(MinecraftServer server, UUID owner) {
        ProgressionGates.open(server, owner, FIRST_BROADCAST);
    }
}
