package za.co.neroland.neronotes.integration;

import java.util.UUID;

/**
 * <strong>Documented seam only — nothing implements or consults this in
 * 0.1.0.</strong> NeroEvents is an empty skeleton today; this interface
 * records the shape a channel-takeover integration would take so the contract
 * is designed now rather than improvised later. No code in NeroNotes calls
 * it, no registration point exists, and none should be added until NeroEvents
 * actually ships event content.
 *
 * <p>The intended semantics, when built:</p>
 * <ul>
 *   <li>a server event (a concert, a station-wide alert theme) may
 *       temporarily assume <em>transport control</em> of specific consenting
 *       channels — never emit-as-owner identity, never trust-list edits, and
 *       never a bypass of {@code signal/ChannelAccess} for players;</li>
 *   <li>consent is opt-in per channel by its owner (a config default of
 *       "off"), and a takeover always ends — implementations must carry an
 *       expiry game tick, not an open-ended flag;</li>
 *   <li>the seam would be consulted by the resonance transport path only,
 *       additively: a takeover can start/stop playback on the consenting
 *       channel, it can never silence the owner's own control.</li>
 * </ul>
 *
 * <p>Like every sibling integration: {@code compileOnly} + a runtime
 * {@code isModLoaded("neroevents")} guard behind this interface, no
 * reflection, silent degradation when absent.</p>
 */
public interface ChannelTakeover {

    /**
     * Whether the identified channel is currently taken over by a server
     * event. Parameters mirror {@code signal/ChannelKey} without importing it
     * (plain-JVM seam): dimension id string, owner UUID, channel name.
     */
    boolean isTakenOver(String dimensionId, UUID owner, String channelName);

    /** The game tick at which the takeover ends, or {@code 0} when not taken over. */
    long takeoverEndsAtTick(String dimensionId, UUID owner, String channelName);
}
