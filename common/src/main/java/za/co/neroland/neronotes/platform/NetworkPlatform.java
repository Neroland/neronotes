package za.co.neroland.neronotes.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loader-neutral send seam for NeroNotes' own network channel
 * ({@code neronotes:main} — see {@code network/NotesNetwork}). One
 * implementation per loader, resolved via {@link java.util.ServiceLoader}
 * eagerly in {@link Services#init()}.
 *
 * <p>Payload types are registered on NeroNotes' own channel in Stage 1+ —
 * never into Core's {@code CoreNetwork}, whose payload lists are drained
 * during Core's own bootstrap. Until payloads exist the loader
 * implementations are deliberate no-ops (they log at debug and drop).</p>
 */
public interface NetworkPlatform {

    /** Server → client. */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Client → server. */
    void sendToServer(CustomPacketPayload payload);
}
