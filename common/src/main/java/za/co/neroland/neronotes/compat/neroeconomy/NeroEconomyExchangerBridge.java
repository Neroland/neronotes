package za.co.neroland.neronotes.compat.neroeconomy;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neroeconomy.api.EconomyCaller;
import za.co.neroland.neroeconomy.api.EconomyResult;
import za.co.neroland.neroeconomy.api.SiblingEconomy;
import za.co.neroland.nerolandcore.economy.CoreCurrencies;
import za.co.neroland.nerolandcore.economy.CurrencyApi;
import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.config.NeroNotesConfig;
import za.co.neroland.neronotes.integration.ExchangerPricing;
import za.co.neroland.neronotes.integration.NeroNotesIntegrations;

/**
 * The NeroEconomy bridge for Disk Exchanger library copies — the ONLY place
 * in NeroNotes that imports {@code za.co.neroland.neroeconomy}. NeroEconomy is
 * {@code compileOnly}: {@link NeroNotesIntegrations#init()} calls
 * {@link #install()} only after {@code isModLoaded("neroeconomy")}, from
 * common init (where {@code SiblingEconomy.claim} must run).
 *
 * <p>With the sibling API (version 1 or later, read reflectively from the
 * jar actually loaded, since the compiled constant is inlined) a copy of an
 * entry with a composer who is not the copier is a {@code transfer} — the
 * composer receives the price less NeroEconomy's fee, which goes to the
 * Reserve; anything else (anonymous or erased authorship, or copying your
 * own entry) is a {@code charge} of the whole price to the Reserve. An
 * author is never recovered. With an older NeroEconomy the bridge falls back
 * to Core's {@link CurrencyApi} and honours its boolean result.</p>
 *
 * <p><strong>Privacy:</strong> nothing here logs a player or composer UUID
 * or name, no request key carries one, and the composer is never shown to
 * the copier — the screen only states the split.</p>
 */
public final class NeroEconomyExchangerBridge {

    /** The NeroEconomy caller id — this mod's id. */
    static final String CALLER_ID = NeroNotesCommon.MOD_ID;
    /** The single reason code NeroNotes claims. */
    static final String REASON_LIBRARY_COPY = "library_copy";

    private NeroEconomyExchangerBridge() {
    }

    /** Install the pricing provider. Common init only, behind {@code isModLoaded("neroeconomy")}. */
    public static void install() {
        int apiVersion = loadedApiVersion();
        if (apiVersion >= 1) {
            EconomyCaller caller = SiblingEconomy.claim(CALLER_ID, Set.of(REASON_LIBRARY_COPY));
            NeroNotesIntegrations.setExchangerPricing(new SiblingPricing(caller));
            NeroNotesCommon.LOGGER.debug("[NeroNotes] NeroEconomy sibling API v{} — library copies priced", apiVersion);
        } else {
            NeroNotesIntegrations.setExchangerPricing(new CorePricing());
            NeroNotesCommon.LOGGER.debug("[NeroNotes] NeroEconomy without the sibling API — library copies priced through Core's CurrencyApi");
        }
    }

    /**
     * {@code SiblingEconomy.API_VERSION} of the jar actually loaded, read reflectively ({@code javac}
     * inlines the constant). 0 when the class or field is missing (a NeroEconomy older than 0.3.0).
     */
    private static int loadedApiVersion() {
        try {
            return SiblingEconomy.class.getField("API_VERSION").getInt(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException missing) {
            return 0;
        }
    }

    /** The configured price; 0 or less means free. */
    static long configuredPrice() {
        return Math.max(0, NeroNotesConfig.LIBRARY_COPY_PRICE.get());
    }

    /** A royalty is routed only to a present composer who is not the copier. */
    static Optional<UUID> royaltyTarget(UUID player, Optional<UUID> author) {
        return author.filter(a -> !a.equals(player));
    }

    /** NeroEconomy 0.3.0+: budgeted sibling API with a composer royalty. */
    private static final class SiblingPricing implements ExchangerPricing {

        private final EconomyCaller caller;

        SiblingPricing(EconomyCaller caller) {
            this.caller = caller;
        }

        @Override
        public boolean chargeCopy(UUID player, int entryId, Optional<UUID> author) {
            // No server in hand: refuse a priced copy rather than give it away. The Exchanger always
            // calls the server-aware overload below.
            return configuredPrice() <= 0;
        }

        @Override
        public boolean chargeCopy(MinecraftServer server, UUID player, int entryId, Optional<UUID> author) {
            long price = configuredPrice();
            if (price <= 0) {
                return true;
            }
            if (server == null) {
                return false;
            }
            // No request key: one click is one copy, and a key must never carry a player id.
            EconomyResult result = royaltyTarget(player, author)
                    .map(composer -> caller.transfer(server, player, composer, price, REASON_LIBRARY_COPY, null))
                    .orElseGet(() -> caller.charge(server, player, price, REASON_LIBRARY_COPY, null));
            if (!result.ok()) {
                ServerPlayer copier = server.getPlayerList().getPlayer(player);
                if (copier != null) {
                    copier.sendSystemMessage(Component.translatable(result.translationKey()));
                }
                return false;
            }
            return true;
        }

        @Override
        public long copyPrice() {
            return configuredPrice();
        }

        @Override
        public long royaltyFeeFor(long price) {
            return price <= 0 ? 0L : Math.min(price, Math.max(0L, caller.feeFor(price)));
        }
    }

    /** NeroEconomy older than 0.3.0: Core's untyped currency seam, no fee. */
    private static final class CorePricing implements ExchangerPricing {

        @Override
        public boolean chargeCopy(UUID player, int entryId, Optional<UUID> author) {
            long price = configuredPrice();
            if (price <= 0) {
                return true;
            }
            return royaltyTarget(player, author)
                    .map(composer -> CurrencyApi.transfer(player, composer, CoreCurrencies.CREDITS, price))
                    .orElseGet(() -> CurrencyApi.withdraw(player, CoreCurrencies.CREDITS, price));
        }

        @Override
        public long copyPrice() {
            return configuredPrice();
        }
    }
}
