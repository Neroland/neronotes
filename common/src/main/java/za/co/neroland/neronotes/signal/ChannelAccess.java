package za.co.neroland.neronotes.signal;

import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * The single authorisation decision point for resonance channels.
 * <strong>Authorisation is server-side and owner-based</strong>: emitting,
 * transport control and renaming require the channel's owner, an entry on its
 * trust list, or operator permission. Anyone in range may listen — listening
 * is never gated here.
 *
 * <p><strong>Proximity is not permission.</strong> Nothing in this class (or
 * anywhere near a permission decision in this mod) may ever consult a
 * nearest-player or any other spatial lookup — a sibling mod still carries
 * exactly that as a known open defect. The client never asserts what it may
 * control either: every decision runs on the server from a
 * {@link ServerPlayer} or a server-verified UUID.</p>
 *
 * <p>The UUID-based overloads exist so authorisation logic is plain-JVM
 * testable without a live server; the {@link ServerPlayer} overloads are the
 * ones production call sites use.</p>
 */
public final class ChannelAccess {

    private ChannelAccess() {
    }

    /**
     * May {@code requester} control (emit on, transport, rename) this
     * channel? True for the owner, a trust-list entry, or an operator.
     */
    public static boolean canControl(UUID requester, boolean isOperator, ResonanceChannel channel) {
        if (requester == null || channel == null) {
            return false;
        }
        return isOperator || channel.owner().equals(requester) || channel.isTrusted(requester);
    }

    /** {@link #canControl(UUID, boolean, ResonanceChannel)} for a live player. */
    public static boolean canControl(ServerPlayer requester, ResonanceChannel channel) {
        return requester != null && canControl(requester.getUUID(), isOperator(requester), channel);
    }

    /**
     * May {@code requester} manage the channel itself (edit its trust list,
     * delete it)? Owner or operator only — being trusted grants control, not
     * the right to extend trust to others.
     */
    public static boolean canManage(UUID requester, boolean isOperator, ResonanceChannel channel) {
        if (requester == null || channel == null) {
            return false;
        }
        return isOperator || channel.owner().equals(requester);
    }

    /** {@link #canManage(UUID, boolean, ResonanceChannel)} for a live player. */
    public static boolean canManage(ServerPlayer requester, ResonanceChannel channel) {
        return requester != null && canManage(requester.getUUID(), isOperator(requester), channel);
    }

    /** Operator check — the 26.x permission set, gamemaster level (classic op level 2). */
    public static boolean isOperator(ServerPlayer player) {
        return player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
}
