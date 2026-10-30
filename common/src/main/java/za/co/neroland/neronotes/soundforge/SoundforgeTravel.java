package za.co.neroland.neronotes.soundforge;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.block.entity.HarmonicGateBlockEntity;

/**
 * The teleport, session-binding and return-position logic for the Soundforge.
 * Server-authoritative throughout: the energy charge and the return anchor
 * are checked and recorded here, never asserted by a client.
 *
 * <p><strong>Entering</strong> (via a charged Harmonic Gate) requires only
 * the gate's banked teleport charge, which the crossing consumes — no
 * progression requirement (standalone-first, like the rest of the ecosystem).
 * The player's exact position, look and dimension are stored in
 * {@link SoundforgeSessionStore} <em>before</em> the teleport, so a crash
 * mid-trip or a logout inside still leaves a way home.</p>
 *
 * <p><strong>Returning</strong> is always free — no charge, no checks:
 * use the Harmonic Gate on the arrival platform, or
 * {@code /neronotes soundforge return} (the safety hatch if the platform
 * gate was broken). A missing return anchor falls back to the overworld
 * spawn; a player is never stranded.</p>
 */
public final class SoundforgeTravel {

    /** Outcome of a gate use / return request, mapped to a player message by the caller. */
    public enum TravelResult {
        /** Teleported into the Soundforge. */
        ENTERED,
        /** Teleported back to the stored return anchor. */
        RETURNED,
        /** Teleported back, but the anchor was missing/unresolvable — world spawn fallback. */
        RETURNED_FALLBACK,
        /** The Harmonic Gate lacks the energy for a crossing. */
        NOT_CHARGED,
        /** Used the return path while not inside the Soundforge. */
        NOT_INSIDE,
        /** The Soundforge dimension is not present in this world (or no server). */
        UNAVAILABLE
    }

    private SoundforgeTravel() {
    }

    /**
     * A player used a Harmonic Gate. Inside the Soundforge this is the free
     * trip home; anywhere else it is a charged crossing in.
     */
    public static TravelResult useGate(ServerPlayer player, @Nullable HarmonicGateBlockEntity gate) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return TravelResult.UNAVAILABLE;
        }
        if (SoundforgeDimension.isSoundforge(player.level())) {
            return returnHome(player);
        }
        ServerLevel soundforge = server.getLevel(SoundforgeDimension.LEVEL);
        if (soundforge == null) {
            // Datapack absent or dimension failed to load: refuse gracefully,
            // never throw. (Same discipline as Core's SpaceTags: an absent
            // place means "no such place here", not an error.)
            return TravelResult.UNAVAILABLE;
        }
        if (gate == null || !gate.hasTeleportCharge()) {
            return TravelResult.NOT_CHARGED;
        }

        // Record the way home BEFORE moving: dimension id string + exact position/look.
        ReturnAnchor anchor = new ReturnAnchor(
                player.level().dimension().identifier().toString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
        SoundforgeSessionStore.get(server).beginSession(player.getUUID(), anchor,
                player.level().getGameTime());

        gate.consumeTeleportCharge();
        SoundforgeDimension.ensurePlatform(soundforge);
        BlockPos arrival = SoundforgeDimension.ARRIVAL_POS;
        teleport(player, soundforge,
                arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5,
                SoundforgeDimension.ARRIVAL_Y_ROT, SoundforgeDimension.ARRIVAL_X_ROT);
        return TravelResult.ENTERED;
    }

    /**
     * Return a player from the Soundforge to their stored anchor (or, if the
     * anchor is missing or its dimension no longer exists, to the overworld
     * spawn). Only valid while actually inside the Soundforge — the return
     * path is an exit, not a free teleport.
     */
    public static TravelResult returnHome(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return TravelResult.UNAVAILABLE;
        }
        if (!SoundforgeDimension.isSoundforge(player.level())) {
            return TravelResult.NOT_INSIDE;
        }
        SoundforgeSessionStore store = SoundforgeSessionStore.get(server);
        ReturnAnchor anchor = store.returnAnchor(player.getUUID()).orElse(null);

        if (anchor != null) {
            Identifier dimensionId = Identifier.tryParse(anchor.dimension());
            ServerLevel target = dimensionId == null ? null
                    : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
            if (target != null) {
                teleport(player, target, anchor.x(), anchor.y(), anchor.z(), anchor.yRot(), anchor.xRot());
                store.markOutside(player.getUUID());
                return TravelResult.RETURNED;
            }
        }

        // Never strand a player: no (resolvable) anchor -> the world's respawn point.
        //? if >=1.21.9 {
        LevelData.RespawnData respawn = server.overworld().getRespawnData();
        ServerLevel spawnLevel = server.getLevel(respawn.globalPos().dimension());
        if (spawnLevel == null) {
            spawnLevel = server.overworld();
        }
        BlockPos spawn = respawn.globalPos().pos();
        float spawnYaw = respawn.yaw();
        float spawnPitch = respawn.pitch();
        //?} else {
        /*ServerLevel spawnLevel = server.overworld();
        BlockPos spawn = spawnLevel.getSharedSpawnPos();
        float spawnYaw = spawnLevel.getSharedSpawnAngle();
        float spawnPitch = 0f;
        *///?}
        NeroNotesCommon.LOGGER.warn("[NeroNotes] soundforge return anchor missing or unresolvable; using world spawn");
        teleport(player, spawnLevel,
                spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, spawnYaw, spawnPitch);
        store.markOutside(player.getUUID());
        return TravelResult.RETURNED_FALLBACK;
    }

    /** Cross-dimension teleport with no relative components; 1.21.1's form has no dismount flag. */
    private static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z,
            float yRot, float xRot) {
        //? if >=1.21.2 {
        player.teleportTo(level, x, y, z, Set.of(), yRot, xRot, true);
        //?} else {
        /*player.teleportTo(level, x, y, z, Set.of(), yRot, xRot);
        *///?}
    }

    /** Convenience guard used by the block and command paths. */
    public static boolean isInsideSoundforge(ServerPlayer player) {
        Level level = player.level();
        return SoundforgeDimension.isSoundforge(level);
    }
}
