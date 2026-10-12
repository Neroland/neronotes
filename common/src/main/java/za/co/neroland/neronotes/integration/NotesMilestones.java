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

    private NotesMilestones() {
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
