package za.co.neroland.neronotes.integration;

import java.util.Optional;
import java.util.UUID;

/**
 * The NeroEconomy pricing seam for Disk Exchanger <em>library copies</em> —
 * consulted by the Exchanger copy path (after every other validation, before
 * the disk is written), <strong>with no pricing logic in NeroNotes</strong>.
 * 0.1.0 ships only {@link #FREE}: every copy is allowed at no cost. A future
 * NeroEconomy bridge (feature-detected in {@link NeroNotesIntegrations},
 * {@code compileOnly} + {@code isModLoaded}, never reflection) installs a
 * real implementation via
 * {@link NeroNotesIntegrations#setExchangerPricing(ExchangerPricing)} that
 * charges the copier and can route a composer royalty.
 *
 * <p>Signatures are UUID-based (no {@code ServerPlayer}) so the seam and its
 * default stay plain-JVM testable, matching {@code signal/ChannelAccess}. An
 * implementation resolves the player server-side from the UUID.</p>
 *
 * <p><strong>Royalties and anonymity:</strong> {@code author} is the library
 * entry's composer UUID — the royalty target. It is empty for anonymous
 * entries and for entries whose author was erased ("sever the link, keep the
 * work"): no author, no royalty, and an implementation must not try to
 * recover one. The author UUID is handed to the implementation for payment
 * routing only and must never surface to the copying player.</p>
 *
 * <p>Duplicating a disk the player already owns is deliberately outside this
 * seam — the library is not involved and no download is counted.</p>
 */
public interface ExchangerPricing {

    /** The built-in default: every copy allowed, nothing charged. */
    ExchangerPricing FREE = new ExchangerPricing() {
    };

    /**
     * Charge {@code player} for copying library entry {@code entryId}.
     * Returns {@code true} when the copy may proceed (payment taken, or
     * free); {@code false} refuses the copy — the Exchanger shows a
     * translated refusal and writes nothing. Called on the server thread
     * only. The default is free.
     *
     * @param player  the copying player's UUID
     * @param entryId the library entry being copied
     * @param author  the composer's UUID (royalty target), empty for
     *                anonymous or erased authorship
     */
    default boolean chargeCopy(UUID player, int entryId, Optional<UUID> author) {
        return true;
    }
}
