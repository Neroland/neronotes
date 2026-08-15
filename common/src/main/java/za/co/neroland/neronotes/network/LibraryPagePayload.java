package za.co.neroland.neronotes.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.neronotes.item.DiskNames;
import za.co.neroland.neronotes.library.LibraryTable;

/**
 * Server → client: one page of shared-library <strong>metadata</strong> for
 * an open Disk Exchanger menu. <em>Deliberately carries no score</em>: a page
 * is titles, author display names, voice families and aggregate download
 * counts only. The score never crosses the wire for the Exchanger at all —
 * copying happens entirely server-side (the client sends "copy entry N", the
 * server writes the disk), so this payload stays small and bounded whatever
 * the library holds.
 *
 * <p>Author display is {@code ""} for anonymous entries (and for entries
 * whose author was erased) — the author UUID is never part of any payload.</p>
 *
 * @param containerId  the open {@code DiskExchangerMenu}'s container id
 * @param page         the zero-based page this payload carries
 * @param pageCount    total visible pages (0 for an empty library)
 * @param visibleCount total visible entries server-wide
 * @param entries      the page's entries, at most {@link LibraryTable#MAX_PAGE_SIZE}
 */
public record LibraryPagePayload(int containerId, int page, int pageCount, int visibleCount,
                                 List<PageEntry> entries) implements CustomPacketPayload {

    /**
     * One listed composition: metadata only, never a score, never a UUID.
     *
     * @param id            the library entry id
     * @param title         the published title
     * @param authorDisplay the author's display name; {@code ""} = anonymous
     * @param familyId      the voice family styling the row
     * @param downloads     the aggregate download count
     */
    public record PageEntry(int id, String title, String authorDisplay, String familyId, int downloads) {

        public PageEntry {
            if (title == null || title.length() > DiskNames.HARD_MAX_LENGTH) {
                throw new IllegalArgumentException("title must be non-null and at most "
                        + DiskNames.HARD_MAX_LENGTH + " characters");
            }
            if (authorDisplay == null || authorDisplay.length() > MAX_AUTHOR_LENGTH) {
                throw new IllegalArgumentException("authorDisplay must be non-null and at most "
                        + MAX_AUTHOR_LENGTH + " characters");
            }
            if (familyId == null || familyId.length() > MAX_FAMILY_LENGTH) {
                throw new IllegalArgumentException("familyId must be non-null and at most "
                        + MAX_FAMILY_LENGTH + " characters");
            }
        }

        /** Whether the row shows the translated "anonymous" author line. */
        public boolean anonymous() {
            return authorDisplay.isEmpty();
        }
    }

    /** Wire bound on an author display name. */
    public static final int MAX_AUTHOR_LENGTH = 64;
    /** Wire bound on a voice-family id. */
    public static final int MAX_FAMILY_LENGTH = 64;

    public static final CustomPacketPayload.Type<LibraryPagePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    NotesNetwork.CHANNEL_NAMESPACE, "library_page"));

    private static final StreamCodec<ByteBuf, String> TITLE_CODEC =
            ByteBufCodecs.stringUtf8(DiskNames.HARD_MAX_LENGTH);
    private static final StreamCodec<ByteBuf, String> AUTHOR_CODEC =
            ByteBufCodecs.stringUtf8(MAX_AUTHOR_LENGTH);
    private static final StreamCodec<ByteBuf, String> FAMILY_CODEC =
            ByteBufCodecs.stringUtf8(MAX_FAMILY_LENGTH);

    public static final StreamCodec<ByteBuf, LibraryPagePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.containerId());
                ByteBufCodecs.VAR_INT.encode(buf, payload.page());
                ByteBufCodecs.VAR_INT.encode(buf, payload.pageCount());
                ByteBufCodecs.VAR_INT.encode(buf, payload.visibleCount());
                ByteBufCodecs.VAR_INT.encode(buf, payload.entries().size());
                for (PageEntry entry : payload.entries()) {
                    ByteBufCodecs.VAR_INT.encode(buf, entry.id());
                    TITLE_CODEC.encode(buf, entry.title());
                    AUTHOR_CODEC.encode(buf, entry.authorDisplay());
                    FAMILY_CODEC.encode(buf, entry.familyId());
                    ByteBufCodecs.VAR_INT.encode(buf, entry.downloads());
                }
            },
            buf -> {
                int containerId = ByteBufCodecs.VAR_INT.decode(buf);
                int page = ByteBufCodecs.VAR_INT.decode(buf);
                int pageCount = ByteBufCodecs.VAR_INT.decode(buf);
                int visibleCount = ByteBufCodecs.VAR_INT.decode(buf);
                int count = ByteBufCodecs.VAR_INT.decode(buf);
                if (count < 0 || count > LibraryTable.MAX_PAGE_SIZE) {
                    throw new IllegalArgumentException("library page entry count out of bounds: " + count);
                }
                List<PageEntry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    entries.add(new PageEntry(
                            ByteBufCodecs.VAR_INT.decode(buf),
                            TITLE_CODEC.decode(buf),
                            AUTHOR_CODEC.decode(buf),
                            FAMILY_CODEC.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf)));
                }
                return new LibraryPagePayload(containerId, page, pageCount, visibleCount, entries);
            });

    public LibraryPagePayload {
        if (entries == null || entries.size() > LibraryTable.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("entries must be non-null and at most "
                    + LibraryTable.MAX_PAGE_SIZE + " rows");
        }
        entries = List.copyOf(entries);
        if (page < 0 || pageCount < 0 || visibleCount < 0) {
            throw new IllegalArgumentException("page, pageCount and visibleCount must be >= 0");
        }
    }

    @Override
    public CustomPacketPayload.Type<LibraryPagePayload> type() {
        return TYPE;
    }
}
