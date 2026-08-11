package za.co.neroland.neronotes.menu;

import java.util.OptionalInt;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;

import za.co.neroland.neronotes.telemetry.NeroNotesTelemetry;

/**
 * The single seam through which EVERY NeroNotes menu is opened. Raw
 * {@code player.openMenu(...)} crashed on Paper hybrids in a sibling mod, so
 * all call sites route through here: failures are caught, the container is
 * closed, the player gets a translated message, and telemetry receives the
 * throwable with a non-identifying menu description.
 */
public final class MenuOpener {

    private MenuOpener() {
    }

    /**
     * Open a menu, returning the container id on success and
     * {@link OptionalInt#empty()} on refusal or failure.
     */
    public static OptionalInt open(Player player, MenuProvider provider) {
        try {
            return player.openMenu(provider);
        } catch (RuntimeException | LinkageError failure) {
            handleFailure(player, provider, failure);
            return OptionalInt.empty();
        }
    }

    /**
     * Open a menu from an interaction, returning {@link InteractionResult#SUCCESS}
     * when the menu opened and {@link InteractionResult#CONSUME} on refusal —
     * never {@code FAIL}, so a hybrid-server refusal degrades to a no-op
     * instead of an arm-swing crash loop.
     */
    public static InteractionResult openOrConsume(Player player, MenuProvider provider) {
        return open(player, provider).isPresent() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    private static void handleFailure(Player player, MenuProvider provider, Throwable failure) {
        // 26.x: Player.closeContainer() is protected; ServerPlayer's override is public.
        // Menus are opened server-side, so the instanceof branch is the normal path.
        if (player instanceof ServerPlayer serverPlayer) {
            try {
                serverPlayer.closeContainer();
            } catch (RuntimeException ignored) {
                // Best effort — the container may never have opened.
            }
            serverPlayer.sendSystemMessage(Component.translatable("neronotes.menu.open_failed"), true);
        } else {
            player.sendSystemMessage(Component.translatable("neronotes.menu.open_failed"));
        }
        // Telemetry gets the CLASS NAME only — never the rendered display title,
        // which may be player-authored text.
        NeroNotesTelemetry.captureHandled("menu", "open:" + describe(provider), failure);
    }

    private static String describe(MenuProvider provider) {
        return provider == null ? "null" : provider.getClass().getName();
    }
}
