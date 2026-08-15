package za.co.neroland.neronotes.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.library.LibraryStore;
import za.co.neroland.neronotes.library.LibraryTable;
import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.score.ScoreCodec;
import za.co.neroland.neronotes.signal.ChannelStore;
import za.co.neroland.neronotes.signal.ResonanceChannel;
import za.co.neroland.neronotes.soundforge.SequencerSessions;
import za.co.neroland.neronotes.soundforge.SoundforgeSessionStore;

/**
 * The Stage 7 data-subject-access export (GDPR Art. 15/20, POPIA §23),
 * behind {@code /neronotes data export}: everything NeroNotes stores about
 * <strong>one player</strong>, written as one JSON file under the world
 * folder at {@code <world>/neronotes/exports/<uuid>.json}.
 *
 * <p><strong>Never another player's data.</strong> Owned channels include a
 * trusted-player <em>count</em> only (the trusted players' identities are
 * other people's personal data); trust memberships name the channel and
 * dimension but deliberately omit the owner's identity for the same reason.
 * Published library entries are matched by the stored author UUID, so
 * anonymous entries the subject authored are included — and the full score
 * bytes ship base64-encoded for genuine data portability.</p>
 *
 * <p>The file lives beside {@code playerdata/<uuid>.dat} conceptually: in the
 * world save, under the server operator's control, named by the subject's
 * own UUID.</p>
 */
public final class DataExport {

    /** The current export file format version. */
    public static final int FORMAT_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** What the export contained — for the requester's chat summary only. */
    public record Result(String relativePath, int channelsOwned, int trustMemberships,
                         int libraryEntries, boolean sessionPresent) {
    }

    private DataExport() {
    }

    /**
     * Export everything NeroNotes stores for {@code subject} to
     * {@code <world>/neronotes/exports/<uuid>.json} (overwriting any previous
     * export for the same player). Server thread only.
     */
    public static Result export(MinecraftServer server, java.util.UUID subject) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("mod", NeroNotesCommon.MOD_ID);
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("exported_at", Instant.now().toString());
        root.addProperty("subject", subject.toString());

        // Activity (the retention-sweep record).
        JsonObject activity = new JsonObject();
        ActivityStore activityStore = ActivityStore.get(server);
        boolean seen = activityStore.hasRow(subject);
        activity.addProperty("recorded", seen);
        activityStore.lastSeen(subject).ifPresent(millis -> {
            activity.addProperty("last_seen_epoch_ms", millis);
            activity.addProperty("last_seen", Instant.ofEpochMilli(millis).toString());
        });
        root.add("activity", activity);

        // Channels the subject owns (trusted players appear as a count only).
        ChannelStore channels = ChannelStore.get(server);
        JsonArray owned = new JsonArray();
        for (ResonanceChannel channel : channels.channelsOwnedBy(subject)) {
            JsonObject row = new JsonObject();
            row.addProperty("dimension", channel.dimension());
            row.addProperty("name", channel.name());
            row.addProperty("trusted_player_count", channel.trusted().size());
            owned.add(row);
        }
        root.add("channels_owned", owned);

        // Channels the subject is trusted on (the owner's identity is omitted —
        // it is another player's personal data).
        JsonArray memberships = new JsonArray();
        for (ResonanceChannel channel : channels.channelsTrusting(subject)) {
            JsonObject row = new JsonObject();
            row.addProperty("dimension", channel.dimension());
            row.addProperty("channel_name", channel.name());
            memberships.add(row);
        }
        root.add("channel_trust_memberships", memberships);

        // Soundforge session: return anchor + a summary of the session score.
        SoundforgeSessionStore sessions = SoundforgeSessionStore.get(server);
        JsonObject session = new JsonObject();
        boolean sessionPresent = sessions.hasRow(subject);
        session.addProperty("present", sessionPresent);
        if (sessionPresent) {
            session.addProperty("inside_soundforge", sessions.isInside(subject));
            sessions.returnAnchor(subject).ifPresent(anchor -> {
                JsonObject a = new JsonObject();
                a.addProperty("dimension", anchor.dimension());
                a.addProperty("x", anchor.x());
                a.addProperty("y", anchor.y());
                a.addProperty("z", anchor.z());
                session.add("return_anchor", a);
            });
            Score score = SequencerSessions.sessionScore(server, subject);
            JsonObject summary = new JsonObject();
            summary.addProperty("tempo_bpm", score.tempoBpm());
            summary.addProperty("ticks_per_beat", score.ticksPerBeat());
            summary.addProperty("layers", score.layers().size());
            summary.addProperty("notes", score.noteCount());
            summary.addProperty("serialised_bytes", ScoreCodec.serialisedSize(score));
            session.add("session_score_summary", summary);
        }
        root.add("soundforge_session", session);

        // Published library entries authored by the subject — including
        // anonymous ones (matched by the stored author UUID), with the full
        // score for portability.
        LibraryStore library = LibraryStore.get(server);
        JsonArray entries = new JsonArray();
        int libraryCount = 0;
        for (LibraryTable.Entry entry : library.allEntries()) {
            if (entry.author().filter(subject::equals).isEmpty()) {
                continue;
            }
            JsonObject row = new JsonObject();
            row.addProperty("id", entry.id());
            row.addProperty("title", entry.title());
            row.addProperty("anonymous", entry.anonymous());
            row.addProperty("credited_display_name", entry.authorDisplay().orElse(""));
            row.addProperty("voice_family", entry.familyId());
            row.addProperty("downloads", entry.downloads());
            row.addProperty("pending_approval", entry.pending());
            byte[] scoreBytes = entry.scoreBytes();
            row.addProperty("score_size_bytes", scoreBytes.length);
            row.addProperty("score_nbt_base64", Base64.getEncoder().encodeToString(scoreBytes));
            entries.add(row);
            libraryCount++;
        }
        root.add("published_library_entries", entries);

        // Policy notes, so the export is self-explanatory.
        JsonObject policy = new JsonObject();
        policy.addProperty("pressed_disks",
                "Pressed disks already in circulation carry the attribution chosen at press time as item "
                + "data inside the world save; the server cannot enumerate them, so they are world data "
                + "outside this export and outside erasure. The shared library above is the authoritative "
                + "record — erasure severs your identity there while the compositions keep working.");
        policy.addProperty("erasure",
                "'/neronotes data erase-me confirm' irreversibly erases this data: library entries become "
                + "anonymous (the work is kept, the link is severed), sessions, channels, trust entries and "
                + "this activity record are deleted. Core's '/neroland data eraseme' does the same across "
                + "all Neroland mods.");
        policy.addProperty("downloads",
                "Download counts are aggregate integers; who downloaded, and when, is never recorded.");
        root.add("policy_notes", policy);

        // Write under the world folder — the operator-controlled place the
        // rest of this data already lives.
        Path directory = server.getWorldPath(LevelResource.ROOT).resolve("neronotes").resolve("exports");
        Files.createDirectories(directory);
        Path file = directory.resolve(subject + ".json");
        Files.writeString(file, GSON.toJson(root));

        return new Result("neronotes/exports/" + subject + ".json",
                owned.size(), memberships.size(), libraryCount, sessionPresent);
    }
}
