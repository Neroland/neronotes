package za.co.neroland.neronotes.integration;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.platform.Services;

/**
 * Init step 10 — sibling-mod soft integrations, all feature-detected through
 * {@code Services.platform().isModLoaded(...)} (resolved eagerly at step 0;
 * no {@code Class.forName}, no reflection, ever — a sibling mod's silent
 * months-long reflection-probe failure is why). Everything degrades
 * silently when a sibling is absent: NeroNotes' only hard dependency is Core.
 *
 * <p>What each detection means in 0.1.0:</p>
 * <ul>
 *   <li><strong>Core {@code ThresholdEvents}</strong> (always on — Core is
 *       required): {@link NotesThresholds} fires
 *       {@code neronotes:compositions_published} from the publish path and
 *       {@code neronotes:channel_listeners} from the subscription map. This
 *       is the whole NeroQuests trigger contract — see
 *       {@link QuestContent}.</li>
 *   <li><strong>neroquests</strong>: nothing to wire — its
 *       {@code custom_event} objective listens to Core's bus on its own. The
 *       detection is logged so a server owner reading a debug log can see
 *       the pairing is live.</li>
 *   <li><strong>neroeconomy</strong>: the {@link ExchangerPricing} seam
 *       exists and the Exchanger copy path consults it, but 0.1.0 installs
 *       no bridge — copies stay {@link ExchangerPricing#FREE} even with
 *       NeroEconomy present. A later release ships the {@code compileOnly}
 *       bridge that calls {@link #setExchangerPricing} behind this
 *       detection.</li>
 *   <li><strong>neroevents</strong>: an empty skeleton today —
 *       {@link ChannelTakeover} documents the future seam; nothing is built
 *       against it.</li>
 * </ul>
 */
public final class NeroNotesIntegrations {

    private static volatile ExchangerPricing exchangerPricing = ExchangerPricing.FREE;

    private NeroNotesIntegrations() {
    }

    /** Called once from {@code NeroNotesCommon.init()} step 10. */
    public static void init() {
        logDetection("neroquests", "ThresholdEvents crossings become quest triggers");
        logDetection("neroeconomy", "ExchangerPricing seam available (0.1.0 installs no bridge; copies stay free)");
        logDetection("neroevents", "ChannelTakeover seam documented only; nothing wired");
        NeroNotesCommon.LOGGER.debug(
                "[NeroNotes] integrations ready — ThresholdEvents channels: {}, {}",
                NotesThresholds.COMPOSITIONS_PUBLISHED, NotesThresholds.CHANNEL_LISTENERS);
    }

    /** The active Exchanger pricing provider — never null, defaults to {@link ExchangerPricing#FREE}. */
    public static ExchangerPricing exchangerPricing() {
        return exchangerPricing;
    }

    /**
     * Install a pricing provider (a future NeroEconomy bridge). {@code null}
     * restores the free default.
     */
    public static void setExchangerPricing(ExchangerPricing pricing) {
        exchangerPricing = pricing == null ? ExchangerPricing.FREE : pricing;
    }

    private static void logDetection(String modId, String meaning) {
        if (Services.platform().isModLoaded(modId)) {
            NeroNotesCommon.LOGGER.debug("[NeroNotes] detected {} — {}", modId, meaning);
        } else {
            NeroNotesCommon.LOGGER.debug("[NeroNotes] {} absent — degrading silently", modId);
        }
    }
}
