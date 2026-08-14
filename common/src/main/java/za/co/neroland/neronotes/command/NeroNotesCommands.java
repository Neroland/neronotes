package za.co.neroland.neronotes.command;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neronotes.soundforge.SoundforgeTravel;
import za.co.neroland.neronotes.soundforge.SoundforgeTravel.TravelResult;

/**
 * NeroNotes server commands. Stage 4 ships exactly one:
 * {@code /neronotes soundforge return} — the safety hatch out of the
 * Soundforge for a player whose platform gate was broken (or who logged out
 * inside and finds the way home missing). It only works while actually
 * inside the Soundforge, so it is an exit, never a free teleport; no
 * operator permission is required because being strandable must never depend
 * on op status.
 *
 * <p>Cross-loader registration: each loader calls {@link #register} from its
 * command hook (NeoForge/Forge {@code RegisterCommandsEvent}, Fabric
 * {@code CommandRegistrationCallback}). Later stages add their subcommands
 * under the same {@code /neronotes} root.</p>
 */
public final class NeroNotesCommands {

    private NeroNotesCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("neronotes")
                        .then(Commands.literal("soundforge")
                                .then(Commands.literal("return")
                                        .executes(ctx -> soundforgeReturn(ctx.getSource())))));
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
}
