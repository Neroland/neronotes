package za.co.neroland.neronotes.network;

import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neronotes.menu.DiskExchangerMenu;
import za.co.neroland.neronotes.telemetry.NeroNotesTelemetry;

/**
 * Server-side handler for the Stage 6 serverbound Exchanger payload. Same
 * discipline as {@link SequencerServerHandlers}: the payload must match the
 * player's <em>open</em> menu (container id and type) or it is ignored
 * quietly, and every decision — page clamping, entry visibility, item
 * movement, the aggregate download count — is made server-side in
 * {@link DiskExchangerMenu}. The client asserts nothing.
 */
public final class ExchangerServerHandlers {

    private ExchangerServerHandlers() {
    }

    /** Handle one Exchanger action (server thread; wired by the loader receivers). */
    public static void handleAction(ExchangerActionPayload payload, ServerPlayer player) {
        try {
            if (!(player.containerMenu instanceof DiskExchangerMenu menu)
                    || menu.containerId != payload.containerId()) {
                return; // stale or forged request — ignore quietly
            }
            switch (payload.action()) {
                case REQUEST_PAGE -> menu.sendPage(player, payload.value());
                case COPY -> menu.tryCopy(player, payload.value());
                case DUPLICATE -> menu.tryDuplicate(player);
            }
        } catch (RuntimeException failure) {
            NeroNotesTelemetry.captureHandled("exchanger", "action:" + payload.action(), failure);
        }
    }
}
