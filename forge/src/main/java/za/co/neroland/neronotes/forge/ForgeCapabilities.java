package za.co.neroland.neronotes.forge;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.platform.ForgeEnergyLookup;
import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.block.entity.HarmonicGateBlockEntity;

/**
 * Forge capability wiring: attach the Harmonic Gate's energy buffer to Core's
 * shared {@link ForgeEnergyLookup#ENERGY} capability so any Nero machine —
 * and, via Core's standard-FE fallback, any Forge Energy source such as
 * Energized Power — can charge it.
 */
public final class ForgeCapabilities {

    private static final Identifier GATE_ENERGY =
            Identifier.fromNamespaceAndPath(NeroNotesCommon.MOD_ID, "gate_energy");

    private ForgeCapabilities() {
    }

    public static void register() {
        AttachCapabilitiesEvent.BlockEntities.BUS.addListener(ForgeCapabilities::onAttachBlockEntity);
    }

    private static void onAttachBlockEntity(AttachCapabilitiesEvent.BlockEntities event) {
        if (!(event.getObject() instanceof HarmonicGateBlockEntity gate)) {
            return;
        }
        GateEnergyProvider provider = new GateEnergyProvider(gate);
        event.addCapability(GATE_ENERGY, provider);
        event.addListener(provider::invalidate);
    }

    private static final class GateEnergyProvider implements ICapabilityProvider {

        private final LazyOptional<NeroEnergyStorage> energy;

        GateEnergyProvider(HarmonicGateBlockEntity gate) {
            this.energy = LazyOptional.of(gate::getEnergy);
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            return cap == ForgeEnergyLookup.ENERGY ? energy.cast() : LazyOptional.empty();
        }

        void invalidate() {
            energy.invalidate();
        }
    }
}
