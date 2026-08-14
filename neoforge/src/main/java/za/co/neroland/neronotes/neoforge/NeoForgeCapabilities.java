package za.co.neroland.neronotes.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import za.co.neroland.nerolandcore.platform.NeoForgeEnergyLookup;
import za.co.neroland.neronotes.block.entity.NeroNotesBlockEntities;

/**
 * NeoForge capability wiring: expose the Harmonic Gate's energy buffer on
 * Core's shared {@code nerolandcore:energy} {@code BlockCapability} so any
 * Nero machine — and, via Core's standard-FE fallback, any Forge Energy
 * source such as Energized Power — can charge it.
 */
public final class NeoForgeCapabilities {

    private NeoForgeCapabilities() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeCapabilities::onRegisterCapabilities);
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                NeoForgeEnergyLookup.ENERGY,
                NeroNotesBlockEntities.HARMONIC_GATE.get(),
                (gate, side) -> gate.getEnergy());
    }
}
