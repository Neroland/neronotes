package za.co.neroland.neronotes.link;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerolandcore.link.LinkSnapshotProvider;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.config.NeroNotesConfig;
import za.co.neroland.neronotes.item.CustomDiskItem;
import za.co.neroland.neronotes.item.DiskContents;
import za.co.neroland.neronotes.item.NeroNotesItems;
import za.co.neroland.neronotes.library.LibraryStore;
import za.co.neroland.neronotes.library.LibraryTable;
import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.signal.ChannelKey;
import za.co.neroland.neronotes.signal.ChannelStore;
import za.co.neroland.neronotes.signal.ResonanceChannel;
import za.co.neroland.neronotes.signal.ResonanceService;

/**
 * NeroNotes' read side of the NeroLink SPI. Every section is player-scoped by
 * construction — see the visibility rule documented on
 * {@link NotesLinkModule} and implemented in
 * {@code NotesLinkAccess.controllableChannel} / the store reads below, which
 * only ever start from {@code channelsOwnedBy(requester)} and
 * {@code channelsTrusting(requester)}.
 *
 * <p>Data-shaping conventions (matching the rest of the ecosystem):
 * {@code snake_case} keys, a {@code schema_version} in every root, dimensions
 * as their identifier string, counts instead of rosters, and a section that
 * has nothing to say returns an envelope with an empty array rather than an
 * error. Nothing here loads a chunk to answer a question, and no snapshot
 * ever carries a player UUID — not the requester's, and certainly not a
 * channel owner's or a composer's (anonymous entries carry no author field at
 * all).</p>
 */
public final class NotesLinkSnapshots implements LinkSnapshotProvider {

    private static final List<String> SECTIONS = List.of(
            NotesLinkModule.SECTION_LIBRARY,
            NotesLinkModule.SECTION_DISKS,
            NotesLinkModule.SECTION_CHANNELS,
            NotesLinkModule.SECTION_NOW_PLAYING);

    @Override
    public String moduleId() {
        return NotesLinkModule.MODULE_ID;
    }

    @Override
    public int schemaVersion() {
        return NotesLinkModule.SCHEMA_VERSION;
    }

    @Override
    public List<String> sections() {
        return SECTIONS;
    }

    @Override
    public JsonObject snapshot(UUID playerId, String section, Map<String, String> params) {
        if (playerId == null || section == null) {
            return new JsonObject();
        }
        MinecraftServer server = NotesLinkAccess.server();
        if (server == null || !NotesLinkAccess.enabled()) {
            return new JsonObject();
        }
        try {
            return switch (section) {
                case NotesLinkModule.SECTION_LIBRARY -> library(server, playerId, params);
                case NotesLinkModule.SECTION_DISKS -> disks(server, playerId);
                case NotesLinkModule.SECTION_CHANNELS -> channels(server, playerId, false);
                case NotesLinkModule.SECTION_NOW_PLAYING -> channels(server, playerId, true);
                default -> new JsonObject();   // Unknown section: nothing to say.
            };
        } catch (RuntimeException e) {
            // Section name only — never who asked (POPIA/GDPR).
            NeroNotesCommon.LOGGER.warn(
                    "[NeroNotes] NeroLink snapshot section '{}' failed; returning nothing for it.",
                    section, e);
            return new JsonObject();
        }
    }

    // --- library ------------------------------------------------------------

    /**
     * One page of the shared library — visible (approved) entries only, the
     * same listing every player sees at the Disk Exchanger. Paginated from
     * day one via the {@code page} query parameter (zero-based; an
     * unparseable or out-of-range page fails closed to an empty page, never
     * the full set). Scores never cross this surface — metadata only.
     */
    private static JsonObject library(MinecraftServer server, UUID playerId, Map<String, String> params) {
        JsonObject root = envelope(server, playerId);
        LibraryStore store = LibraryStore.get(server);
        int pageSize = NeroNotesConfig.LIBRARY_PAGE_SIZE.get();
        int page = NotesLinkAccess.pageParam(params, "page", 0);

        JsonArray rows = new JsonArray();
        for (LibraryTable.Entry entry : store.visiblePage(page, pageSize)) {
            rows.add(libraryRow(entry));
        }
        root.add("entries", rows);
        root.addProperty("page", page);
        root.addProperty("page_size", pageSize);
        root.addProperty("page_count", store.pageCount(pageSize));
        root.addProperty("visible_count", store.visibleCount());
        return root;
    }

    /**
     * One library row. <b>Anonymous entries (and entries whose author was
     * erased) carry NO author key at all</b> — not an empty string — so no
     * client can distinguish "chose anonymity" from "was erased", and nothing
     * author-shaped exists to render. The author UUID appears nowhere on any
     * link surface. Package-visible so the anonymity invariant is directly
     * unit-testable.
     */
    static JsonObject libraryRow(LibraryTable.Entry entry) {
        JsonObject row = new JsonObject();
        row.addProperty("id", entry.id());
        row.addProperty("title", entry.title());
        entry.authorDisplay().ifPresent(name -> row.addProperty("author", name));
        row.addProperty("anonymous", entry.authorDisplay().isEmpty());
        row.addProperty("family", entry.familyId());
        row.addProperty("downloads", entry.downloads());
        return row;
    }

    // --- disks --------------------------------------------------------------

    /**
     * The pressed custom disks the requester carries. An inventory exists
     * only while its player is online, so offline this is an empty list under
     * {@code player_online: false} — the honest trade, documented on
     * {@link NotesLinkModule#SECTION_DISKS}. Reads the requester's own live
     * {@code ServerPlayer} handle; there is no path to another player's
     * inventory.
     */
    private static JsonObject disks(MinecraftServer server, UUID playerId) {
        JsonObject root = envelope(server, playerId);
        JsonArray rows = new JsonArray();
        root.add("disks", rows);

        ServerPlayer player = NotesLinkAccess.online(server, playerId);
        if (player == null) {
            return root;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.is(NeroNotesItems.CUSTOM_DISK.get())) {
                continue;
            }
            DiskContents contents = CustomDiskItem.contentsOf(stack);
            if (contents != null) {
                rows.add(diskRow(contents, playerId));
            }
        }
        return root;
    }

    /**
     * One carried-disk row. The same anonymity invariant as the library:
     * an anonymous disk carries NO author key (the stored author UUID exists
     * solely for erasure and is never emitted). {@code authored_by_you} is a
     * boolean about the REQUESTER — the only authorship question this module
     * answers. Package-visible for the unit tests.
     */
    static JsonObject diskRow(DiskContents contents, UUID requester) {
        JsonObject row = new JsonObject();
        row.addProperty("title", contents.title());
        contents.authorDisplay().ifPresent(name -> row.addProperty("author", name));
        row.addProperty("anonymous", contents.authorDisplay().isEmpty());
        row.addProperty("authored_by_you", contents.author().equals(requester));
        row.addProperty("family", contents.familyId());
        Score score = contents.score();
        row.addProperty("layers", score.layers().size());
        row.addProperty("notes", score.noteCount());
        row.addProperty("tempo_bpm", score.tempoBpm());
        return row;
    }

    // --- channels / now_playing ----------------------------------------------

    /**
     * Only channels the requester owns or is trusted on — read via
     * {@code channelsOwnedBy(requester)} and {@code channelsTrusting(requester)},
     * so a stranger's channel is unreachable by construction and a requester
     * with no channels gets an empty list, never a roster.
     * {@code onlyPlaying = true} is the {@code now_playing} section: the
     * currently playing subset of exactly the same visible set.
     */
    private static JsonObject channels(MinecraftServer server, UUID playerId, boolean onlyPlaying) {
        JsonObject root = envelope(server, playerId);
        ChannelStore store = ChannelStore.get(server);
        JsonArray rows = channelRows(
                store.channelsOwnedBy(playerId),
                store.channelsTrusting(playerId),
                ResonanceService::isPlaying,
                ResonanceService::subscriberCount,
                onlyPlaying);
        root.add("channels", rows);
        return root;
    }

    /**
     * Builds the channel rows from the requester's owned and trusted lists.
     * Pure over its inputs (playing state and subscriber counts arrive as
     * functions) so the no-roster and no-owner-UUID guarantees are directly
     * unit-testable. Rows carry the opaque {@code id}
     * ({@link NotesLinkAccess#channelRef}), the name, the dimension, the
     * requester's role, live playing state and a subscriber COUNT; an owned
     * row additionally carries the trust-list SIZE. No owner UUID and no
     * trusted-player identities, ever.
     */
    static JsonArray channelRows(List<ResonanceChannel> owned, List<ResonanceChannel> trusted,
            Predicate<ChannelKey> playing, ToIntFunction<ChannelKey> subscribers, boolean onlyPlaying) {
        JsonArray rows = new JsonArray();
        for (ResonanceChannel channel : owned) {
            addChannelRow(rows, channel, "owner", playing, subscribers, onlyPlaying);
        }
        for (ResonanceChannel channel : trusted) {
            addChannelRow(rows, channel, "trusted", playing, subscribers, onlyPlaying);
        }
        return rows;
    }

    private static void addChannelRow(JsonArray rows, ResonanceChannel channel, String role,
            Predicate<ChannelKey> playing, ToIntFunction<ChannelKey> subscribers, boolean onlyPlaying) {
        boolean isPlaying = playing.test(channel.key());
        if (onlyPlaying && !isPlaying) {
            return;
        }
        JsonObject row = new JsonObject();
        row.addProperty("id", NotesLinkAccess.channelRef(channel.key()));
        row.addProperty("name", channel.name());
        row.addProperty("dimension", channel.dimension());
        row.addProperty("role", role);
        row.addProperty("playing", isPlaying);
        row.addProperty("subscribers", subscribers.applyAsInt(channel.key()));
        if ("owner".equals(role)) {
            // The trust list itself is other players' identities — a COUNT only.
            row.addProperty("trusted_count", channel.trusted().size());
        }
        rows.add(row);
    }

    // --- helpers ------------------------------------------------------------

    /** The two fields every NeroNotes snapshot root starts with. */
    private static JsonObject envelope(MinecraftServer server, UUID playerId) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", NotesLinkModule.SCHEMA_VERSION);
        root.addProperty("player_online", NotesLinkAccess.isOnline(server, playerId));
        return root;
    }
}
