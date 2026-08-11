package za.co.neroland.neronotes.signal;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 2 gate: a non-owner cannot emit, transport or rename; a trusted
 * player and an operator can. Authorisation decisions take
 * {@code (UUID requester, boolean isOperator, Channel)} so they are
 * plain-JVM testable — proximity appears nowhere.
 */
class ChannelAccessTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TRUSTED = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private static final ResonanceChannel CHANNEL = new ResonanceChannel(
            new ChannelKey("minecraft:overworld", OWNER, "atrium"), Set.of(TRUSTED));

    @Test
    void ownerCanControl() {
        assertTrue(ChannelAccess.canControl(OWNER, false, CHANNEL));
    }

    @Test
    void trustedPlayerCanControl() {
        assertTrue(ChannelAccess.canControl(TRUSTED, false, CHANNEL));
    }

    @Test
    void operatorCanControlWithoutTrust() {
        assertTrue(ChannelAccess.canControl(STRANGER, true, CHANNEL));
    }

    @Test
    void strangerCannotControl() {
        // The one decision this mod must never get wrong: not owner, not
        // trusted, not operator -> no emit, no transport, no rename.
        assertFalse(ChannelAccess.canControl(STRANGER, false, CHANNEL));
    }

    @Test
    void nullRequesterCannotControl() {
        UUID nobody = null;
        assertFalse(ChannelAccess.canControl(nobody, true, CHANNEL));
        assertFalse(ChannelAccess.canControl(OWNER, true, null));
    }

    @Test
    void trustGrantsControlButNotManagement() {
        assertTrue(ChannelAccess.canControl(TRUSTED, false, CHANNEL));
        assertFalse(ChannelAccess.canManage(TRUSTED, false, CHANNEL));
    }

    @Test
    void ownerAndOperatorCanManage() {
        assertTrue(ChannelAccess.canManage(OWNER, false, CHANNEL));
        assertTrue(ChannelAccess.canManage(STRANGER, true, CHANNEL));
        assertFalse(ChannelAccess.canManage(STRANGER, false, CHANNEL));
    }
}
