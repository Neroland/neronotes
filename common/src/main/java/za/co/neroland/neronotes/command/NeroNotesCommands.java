package za.co.neroland.neronotes.command;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.neronotes.config.NeroNotesConfig;
import za.co.neroland.neronotes.data.DataExport;
import za.co.neroland.neronotes.library.LibraryService;
import za.co.neroland.neronotes.library.LibraryStore;
import za.co.neroland.neronotes.library.LibraryTable;
import za.co.neroland.neronotes.link.NotesLinkEvents;
import za.co.neroland.neronotes.soundforge.SoundforgeTravel;
import za.co.neroland.neronotes.soundforge.SoundforgeTravel.TravelResult;
import za.co.neroland.neronotes.telemetry.NeroNotesTelemetry;

/**
 * NeroNotes server commands, all under the {@code /neronotes} root.
 *
 * <ul>
 *   <li>{@code /neronotes soundforge return} — the Stage 4 safety hatch out
 *       of the Soundforge. Works only while inside; no op needed, because
 *       being strandable must never depend on op status.</li>
 *   <li>{@code /neronotes library browse [page]} — one page of the shared
 *       library (1-based; paginated from day one, locked decision 5).</li>
 *   <li>{@code /neronotes library publish} — publish the pressed disk in
 *       your main hand (same server-side flow as the publish lectern).</li>
 *   <li>{@code /neronotes library unpublish <id>} — remove YOUR entry;
 *       author-only, checked server-side.</li>
 *   <li>{@code /neronotes library remove <id>} — operator takedown (locked
 *       decision 6).</li>
 *   <li>{@code /neronotes library approve <id>} — operator approval, for
 *       servers running {@code library.op_approval_required}.</li>
 *   <li>{@code /neronotes data export} — Stage 7 data-subject access: export
 *       YOUR OWN stored NeroNotes data (chat summary + a JSON file under the
 *       world folder). Self-service, no op needed. The op-only
 *       {@code /neronotes data export <uuid>} variant serves access requests
 *       on a player's behalf.</li>
 *   <li>{@code /neronotes data erase-me} / {@code ... erase-me confirm} —
 *       self-service erasure through Core's shared
 *       {@code PlayerDataErasure} hook (irreversible; the bare command only
 *       warns). Operators can equally use Core's {@code /neroland data}
 *       commands.</li>
 *   <li>{@code /neronotes gallery} / {@code gallery clear} — the operator
 *       showcase (the ecosystem gallery pattern): every NeroNotes block on a
 *       labelled plaza plus a Resonator playing a looping in-code demo score
 *       on the sender's own {@code "gallery"} channel. See
 *       {@link NotesGallery}.</li>
 * </ul>
 *
 * <p>Cross-loader registration: each loader calls {@link #register} from its
 * command hook (NeoForge/Forge {@code RegisterCommandsEvent}, Fabric
 * {@code CommandRegistrationCallback}).</p>
 */
public final class NeroNotesCommands {

    private NeroNotesCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("neronotes")
                        .then(Commands.literal("soundforge")
                                .then(Commands.literal("return")
                                        .executes(ctx -> soundforgeReturn(ctx.getSource()))))
                        .then(Commands.literal("library")
                                .then(Commands.literal("browse")
                                        .executes(ctx -> browse(ctx.getSource(), 1))
                                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                .executes(ctx -> browse(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "page")))))
                                .then(Commands.literal("publish")
                                        .executes(ctx -> publish(ctx.getSource())))
                                .then(Commands.literal("unpublish")
                                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                                .executes(ctx -> unpublish(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "id")))))
                                .then(Commands.literal("remove")
                                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                                .executes(ctx -> remove(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "id")))))
                                .then(Commands.literal("approve")
                                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                                .executes(ctx -> approve(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "id"))))))
                        .then(Commands.literal("gallery")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(ctx -> NotesGallery.build(ctx.getSource()))
                                .then(Commands.literal("clear")
                                        .executes(ctx -> NotesGallery.clear(ctx.getSource()))))
                        .then(Commands.literal("data")
                                .then(Commands.literal("export")
                                        .executes(ctx -> exportSelf(ctx.getSource()))
                                        .then(Commands.argument("uuid", StringArgumentType.string())
                                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                                .executes(ctx -> exportOther(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "uuid")))))
                                .then(Commands.literal("erase-me")
                                        .executes(ctx -> eraseMeWarn(ctx.getSource()))
                                        .then(Commands.literal("confirm")
                                                .executes(ctx -> eraseMeConfirm(ctx.getSource()))))));
    }

    private static int soundforgeReturn(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.translatable("neronotes.command.player_only"));
            return 0;
        }
        TravelResult result = SoundforgeTravel.returnHome(player);
        switch (result) {
            case RETURNED -> player.sendSystemMessage(Component.translatable("neronotes.gate.returned"));
            case RETURNED_FALLBACK ->
                    player.sendSystemMessage(Component.translatable("neronotes.gate.returned_fallback"));
            case NOT_INSIDE ->
                    player.sendSystemMessage(Component.translatable("neronotes.command.soundforge_return.not_inside"));
            default -> player.sendSystemMessage(Component.translatable("neronotes.gate.unavailable"));
        }
        return result == TravelResult.RETURNED || result == TravelResult.RETURNED_FALLBACK ? 1 : 0;
    }

    // ------------------------------------------------------------------
    // Library
    // ------------------------------------------------------------------

    /** One page of the shared library, 1-based for humans. Anonymous entries name nobody. */
    private static int browse(CommandSourceStack source, int humanPage) {
        LibraryStore library = LibraryStore.get(source.getServer());
        int pageSize = NeroNotesConfig.LIBRARY_PAGE_SIZE.get();
        int pageCount = library.pageCount(pageSize);
        if (pageCount == 0) {
            source.sendSuccess(() -> Component.translatable("neronotes.command.library.empty"), false);
            return 0;
        }
        int page = Math.min(humanPage - 1, pageCount - 1);
        List<LibraryTable.Entry> entries = library.visiblePage(page, pageSize);
        source.sendSuccess(() -> Component.translatable("neronotes.command.library.header",
                page + 1, pageCount, library.visibleCount()), false);
        for (LibraryTable.Entry entry : entries) {
            Component author = entry.authorDisplay()
                    .<Component>map(Component::literal)
                    .orElseGet(() -> Component.translatable("neronotes.exchanger.row_anonymous"));
            source.sendSuccess(() -> Component.translatable("neronotes.command.library.row",
                    entry.id(), entry.title(), author, entry.downloads()), false);
        }
        return entries.size();
    }

    /** Publish the disk in the player's main hand — the same flow as the publish lectern. */
    private static int publish(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.translatable("neronotes.command.player_only"));
            return 0;
        }
        return LibraryService.publishHeldDisk(player) > 0 ? 1 : 0;
    }

    /** Author-only unpublish — the server checks the entry's author, never the client. */
    private static int unpublish(CommandSourceStack source, int id) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.translatable("neronotes.command.player_only"));
            return 0;
        }
        LibraryTable.UnpublishResult result =
                LibraryStore.get(source.getServer()).unpublish(id, player.getUUID());
        switch (result) {
            case REMOVED -> {
                player.sendSystemMessage(
                        Component.translatable("neronotes.command.library.unpublished", id));
                // Stage 9: broadcast link event — counts only, no titles, no authors.
                NotesLinkEvents.libraryChanged(source.getServer());
            }
            case NOT_AUTHOR -> player.sendSystemMessage(
                    Component.translatable("neronotes.command.library.not_author"));
            case NO_SUCH_ENTRY -> player.sendSystemMessage(
                    Component.translatable("neronotes.command.library.not_found", id));
        }
        return result == LibraryTable.UnpublishResult.REMOVED ? 1 : 0;
    }

    /** Operator takedown (locked decision 6). */
    private static int remove(CommandSourceStack source, int id) {
        if (LibraryStore.get(source.getServer()).remove(id)) {
            source.sendSuccess(() -> Component.translatable("neronotes.command.library.removed", id), true);
            // Stage 9: broadcast link event — counts only, no titles, no authors.
            NotesLinkEvents.libraryChanged(source.getServer());
            return 1;
        }
        source.sendFailure(Component.translatable("neronotes.command.library.not_found", id));
        return 0;
    }

    // ------------------------------------------------------------------
    // Data-subject access + erasure (Stage 7; POPIA / GDPR)
    // ------------------------------------------------------------------

    /** Self-service data-subject access: export the caller's own stored data. */
    private static int exportSelf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.translatable("neronotes.command.player_only"));
            return 0;
        }
        return doExport(source, player.getUUID(), true);
    }

    /** Operator variant: export a named player's data (serving an access request on their behalf). */
    private static int exportOther(CommandSourceStack source, String rawUuid) {
        UUID subject;
        try {
            subject = UUID.fromString(rawUuid);
        } catch (IllegalArgumentException invalid) {
            source.sendFailure(Component.translatable("neronotes.command.data.invalid_uuid"));
            return 0;
        }
        return doExport(source, subject, false);
    }

    private static int doExport(CommandSourceStack source, UUID subject, boolean self) {
        try {
            DataExport.Result result = DataExport.export(source.getServer(), subject);
            source.sendSuccess(() -> Component.translatable(
                    "neronotes.command.data.export.done", result.relativePath()), false);
            if (self) {
                Component session = Component.translatable(result.sessionPresent()
                        ? "neronotes.command.data.export.session_yes"
                        : "neronotes.command.data.export.session_no");
                source.sendSuccess(() -> Component.translatable("neronotes.command.data.export.summary",
                        result.channelsOwned(), result.trustMemberships(),
                        result.libraryEntries(), session), false);
            }
            return 1;
        } catch (IOException | RuntimeException failure) {
            // The failure detail goes to the log/telemetry, never chat; no
            // player identity is attached (a UUID is personal data).
            NeroNotesTelemetry.captureHandled("command", "data_export", failure);
            source.sendFailure(Component.translatable("neronotes.command.data.export.failed"));
            return 0;
        }
    }

    /** The bare {@code erase-me}: warn about irreversibility, do nothing else. */
    private static int eraseMeWarn(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.translatable("neronotes.command.player_only"));
            return 0;
        }
        player.sendSystemMessage(Component.translatable("neronotes.command.data.erase_me.warning"));
        player.sendSystemMessage(Component.translatable("neronotes.command.data.erase_me.confirm_hint"));
        return 1;
    }

    /**
     * Self-service erasure through Core's shared hook — the whole Neroland
     * fan-out, exactly like Core's own {@code /neroland data eraseme}, so one
     * request purges the player across every registered mod.
     */
    private static int eraseMeConfirm(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.translatable("neronotes.command.player_only"));
            return 0;
        }
        PlayerDataErasure.erase(source.getServer(), player.getUUID());
        player.sendSystemMessage(Component.translatable("neronotes.command.data.erase_me.done"));
        return 1;
    }

    /** Operator approval — only meaningful with {@code library.op_approval_required} on. */
    private static int approve(CommandSourceStack source, int id) {
        LibraryStore library = LibraryStore.get(source.getServer());
        if (library.approve(id)) {
            source.sendSuccess(() -> Component.translatable("neronotes.command.library.approved", id), true);
            // Stage 9: approval changes the VISIBLE count — broadcast counts only.
            NotesLinkEvents.libraryChanged(source.getServer());
            return 1;
        }
        source.sendFailure(Component.translatable(library.entry(id).isPresent()
                ? "neronotes.command.library.not_pending" : "neronotes.command.library.not_found", id));
        return 0;
    }
}
