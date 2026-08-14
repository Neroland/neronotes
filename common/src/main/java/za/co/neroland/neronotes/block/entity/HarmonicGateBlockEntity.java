package za.co.neroland.neronotes.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.neronotes.block.HarmonicGateBlock;
import za.co.neroland.neronotes.config.NeroNotesConfig;

/**
 * The Harmonic Gate's machine core — a Core-powered
 * {@link AbstractMachineBlockEntity} whose only job is to bank energy toward
 * Soundforge crossings. It accepts energy through Core's shared
 * {@code nerolandcore:energy} capability, registered per loader (NeoForge
 * {@code RegisterCapabilitiesEvent}, Forge {@code AttachCapabilitiesEvent},
 * Fabric {@code BlockApiLookup}); on NeoForge/Forge, Core's lookup falls back
 * to standard Forge Energy, so third-party FE sources such as Energized Power
 * charge it too.
 *
 * <p>No upgrade slots, no side config, no inventory — the gate is an anchor,
 * not a processor. The {@code charged} blockstate mirrors whether a full
 * teleport charge is banked (refreshed every {@value #STATE_SYNC_INTERVAL}
 * ticks) so the block's glow answers "can I cross?" at a glance.</p>
 *
 * <p>The teleport itself lives in {@code soundforge/SoundforgeTravel};
 * returning from inside the Soundforge never consumes charge.</p>
 */
public class HarmonicGateBlockEntity extends AbstractMachineBlockEntity {

    /** External insert/extract bound per tick (NE). */
    public static final int MAX_TRANSFER_PER_TICK = 4096;

    /** How often (ticks) the charged blockstate is reconciled with the buffer. */
    public static final int STATE_SYNC_INTERVAL = 10;

    private int syncCooldown;

    public HarmonicGateBlockEntity(BlockPos pos, BlockState state) {
        super(NeroNotesBlockEntities.HARMONIC_GATE.get(), pos, state,
                NeroNotesConfig.GATE_ENERGY_CAPACITY.get(), MAX_TRANSFER_PER_TICK,
                0, stack -> null);
    }

    /**
     * The energy cost of one crossing into the Soundforge, clamped to the
     * buffer capacity so a mis-configured cost above capacity can never make
     * the gate permanently uncrossable.
     */
    public int teleportCost() {
        return (int) Math.min(NeroNotesConfig.GATE_TELEPORT_ENERGY_COST.get(), getEnergy().getCapacity());
    }

    /** Whether a full crossing charge is banked. */
    public boolean hasTeleportCharge() {
        return energyBuffer().has(teleportCost());
    }

    /** Consume one crossing charge (internal consumption; bypasses the extract limit). */
    public void consumeTeleportCharge() {
        energyBuffer().consume(teleportCost());
        setChanged();
    }

    @Override
    protected void serverTick(Level level, BlockPos pos, BlockState state) {
        if (--syncCooldown > 0) {
            return;
        }
        syncCooldown = STATE_SYNC_INTERVAL;
        boolean charged = hasTeleportCharge();
        if (state.getBlock() instanceof HarmonicGateBlock
                && state.getValue(HarmonicGateBlock.CHARGED) != charged) {
            level.setBlock(pos, state.setValue(HarmonicGateBlock.CHARGED, charged), 3);
        }
    }
}
