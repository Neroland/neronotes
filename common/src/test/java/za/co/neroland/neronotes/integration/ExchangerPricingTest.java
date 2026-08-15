package za.co.neroland.neronotes.integration;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 8 gate: the NeroEconomy pricing seam's default is FREE — every copy
 * allowed, nothing charged — and the holder in {@code NeroNotesIntegrations}
 * hands out that default until a bridge installs a real provider.
 */
class ExchangerPricingTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @AfterEach
    void restoreDefault() {
        NeroNotesIntegrations.setExchangerPricing(null);
    }

    @Test
    void defaultAllowsEveryCopyForFree() {
        assertTrue(ExchangerPricing.FREE.chargeCopy(PLAYER, 1, Optional.of(AUTHOR)));
        assertTrue(ExchangerPricing.FREE.chargeCopy(PLAYER, 2, Optional.empty())); // anonymous/erased author
    }

    @Test
    void holderStartsAtFree() {
        assertSame(ExchangerPricing.FREE, NeroNotesIntegrations.exchangerPricing());
    }

    @Test
    void installedProviderIsConsultedAndNullRestoresFree() {
        ExchangerPricing refuseAll = new ExchangerPricing() {
            @Override
            public boolean chargeCopy(UUID player, int entryId, Optional<UUID> author) {
                return false;
            }
        };
        NeroNotesIntegrations.setExchangerPricing(refuseAll);
        assertSame(refuseAll, NeroNotesIntegrations.exchangerPricing());
        assertFalse(NeroNotesIntegrations.exchangerPricing().chargeCopy(PLAYER, 1, Optional.of(AUTHOR)));

        NeroNotesIntegrations.setExchangerPricing(null);
        assertSame(ExchangerPricing.FREE, NeroNotesIntegrations.exchangerPricing());
        assertTrue(NeroNotesIntegrations.exchangerPricing().chargeCopy(PLAYER, 1, Optional.of(AUTHOR)));
    }
}
