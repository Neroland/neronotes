package za.co.neroland.neronotes.library;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.neronotes.config.NeroNotesConfig;
import za.co.neroland.neronotes.integration.NotesThresholds;
import za.co.neroland.neronotes.item.CustomDiskItem;
import za.co.neroland.neronotes.item.DiskContents;
import za.co.neroland.neronotes.item.DiskNames;
import za.co.neroland.neronotes.item.NeroNotesItems;
import za.co.neroland.neronotes.link.NotesLinkEvents;
import za.co.neroland.neronotes.score.ScoreCodec;
import za.co.neroland.neronotes.score.ScoreSizeException;

/**
 * The server-side publish flow, shared by the publish lectern and the
 * {@code /neronotes library publish} command. Everything is re-validated here
 * regardless of surface (locked decision 6 — name validation runs at press
 * time <em>and</em> publish time):
 *
 * <ul>
 *   <li>{@code library.publishing_enabled} — the master toggle;</li>
 *   <li>the held item must be a pressed disk, and <strong>only the disk's
 *       author may publish it</strong> — publishing someone else's work is
 *       refused (quota and attribution stay coherent);</li>
 *   <li>the title re-passes {@link DiskNames} (the word list may have changed
 *       since the disk was pressed);</li>
 *   <li>the score re-passes the configured budget;</li>
 *   <li>the library size cap and the per-player quota
 *       ({@link LibraryTable});</li>
 *   <li>{@code library.op_approval_required} — when on, the entry is created
 *       pending and stays hidden from every listing until an operator
 *       approves it.</li>
 * </ul>
 *
 * <p>Anonymous publishing is first-class and carried by the disk: a disk
 * pressed anonymously publishes anonymously, and the library entry stores no
 * display name. The disk itself stays with the player — the library keeps a
 * copy of the score, not the item.</p>
 */
public final class LibraryService {

    private LibraryService() {
    }

    /**
     * Publish the disk in the player's main hand. Every refusal is a
     * translated message; returns the new entry id, or {@code -1}.
     */
    public static int publishHeldDisk(ServerPlayer player) {
        if (!NeroNotesConfig.PUBLISHING_ENABLED.get()) {
            player.sendSystemMessage(Component.translatable("neronotes.library.publish.disabled"));
            return -1;
        }
        ItemStack held = player.getMainHandItem();
        DiskContents contents = held.is(NeroNotesItems.CUSTOM_DISK.get())
                ? CustomDiskItem.contentsOf(held) : null;
        if (contents == null) {
            player.sendSystemMessage(Component.translatable("neronotes.library.publish.no_disk"));
            return -1;
        }
        if (!player.getUUID().equals(contents.author())) {
            player.sendSystemMessage(Component.translatable("neronotes.library.publish.not_author"));
            return -1;
        }
        // Publish-time name validation (locked decision 6) — the configured
        // rules may have tightened since the disk was pressed.
        DiskNames.Result name = DiskNames.cleanConfigured(contents.title());
        if (!name.ok()) {
            player.sendSystemMessage(Component.translatable("neronotes.library.publish.bad_title"));
            return -1;
        }
        // Publish-time budget re-check (locked decision 5) — refused, never trimmed.
        byte[] scoreBytes;
        try {
            scoreBytes = ScoreCodec.toBytes(contents.score(), NeroNotesConfig.DISK_SCORE_BUDGET_BYTES.get());
        } catch (ScoreSizeException overBudget) {
            player.sendSystemMessage(Component.translatable("neronotes.press.over_budget",
                    overBudget.actualBytes(), overBudget.budgetBytes()));
            return -1;
        }
        MinecraftServer server = player.level().getServer();
        boolean approvalRequired = NeroNotesConfig.OP_APPROVAL_REQUIRED.get();
        LibraryStore store = LibraryStore.get(server);
        int countBefore = store.totalCount();
        LibraryTable.PublishResult result = store.publish(
                name.name(), contents.author(),
                contents.anonymous() ? "" : contents.authorName(), contents.anonymous(),
                contents.familyId(), scoreBytes,
                NeroNotesConfig.LIBRARY_SIZE_CAP.get(),
                NeroNotesConfig.LIBRARY_PER_PLAYER_QUOTA.get(),
                approvalRequired);
        switch (result.error()) {
            case LIBRARY_FULL -> player.sendSystemMessage(Component.translatable(
                    "neronotes.library.publish.full", NeroNotesConfig.LIBRARY_SIZE_CAP.get()));
            case QUOTA_EXCEEDED -> player.sendSystemMessage(Component.translatable(
                    "neronotes.library.publish.quota", NeroNotesConfig.LIBRARY_PER_PLAYER_QUOTA.get()));
            case NONE -> player.sendSystemMessage(Component.translatable(
                    approvalRequired ? "neronotes.library.publish.pending" : "neronotes.library.publish.done",
                    result.id()));
        }
        if (result.ok()) {
            // Stage 8: server-wide milestone crossings on Core's ThresholdEvents
            // (scope "library" — a system key, never a player). Server thread:
            // this whole method is the server-side publish path.
            NotesThresholds.publishedCountChanged(countBefore, store.totalCount());
            // Stage 9: broadcast link event — counts only, no titles, no authors.
            NotesLinkEvents.libraryChanged(server);
        }
        return result.ok() ? result.id() : -1;
    }
}
