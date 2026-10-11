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
 *   <li><strong>neroeconomy</strong>: since 0.4.0 the {@code compileOnly}
 *       bridge ({@code compat/neroeconomy/NeroEconomyExchangerBridge}) is
 *       installed through {@link #setExchangerPricing} behind this
 *       detection: library copies cost {@code exchanger.library_copy_price}
 *       credits, routed to the composer as a royalty (less NeroEconomy's
 *       fee) or to the Reserve for anonymous, erased or own entries. With
 *       NeroEconomy absent copies stay {@link ExchangerPricing#FREE}.</li>
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
        logDetection("neroeconomy", "Disk Exchanger library copies priced through the NeroEconomy bridge");
        if (Services.platform().isModLoaded("neroeconomy")) {
            // The ONLY path into compat/neroeconomy — the bridge class (the one place that imports
            // NeroEconomy) is never touched unless the mod is loaded. A failure costs pricing, never
            // the mod: copies stay free.
            try {
                za.co.neroland.neronotes.compat.neroeconomy.NeroEconomyExchangerBridge.install();
            } catch (RuntimeException | LinkageError failure) {
                NeroNotesCommon.LOGGER.warn("[NeroNotes] NeroEconomy pricing bridge unavailable; library copies stay free ({})",
                        failure.getClass().getSimpleName());
            }
        }
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
     * Install a pricing provider (the NeroEconomy bridge). {@code null}
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
