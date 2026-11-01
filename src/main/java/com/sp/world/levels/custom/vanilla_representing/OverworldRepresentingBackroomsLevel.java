package com.sp.world.levels.custom.vanilla_representing;

import com.sp.cca_stuff.PlayerComponent;
import com.sp.world.levels.WorldRepresentingBackroomsLevel;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Optional;

public class OverworldRepresentingBackroomsLevel extends WorldRepresentingBackroomsLevel {
    public OverworldRepresentingBackroomsLevel() {
        super("overworld", new Vec3d(0,200,0), World.OVERWORLD);
    }

    @Override
    public int nextEventDelay() {
        return 0;
    }

    @Override
    public void writeToNbt(NbtCompound nbt) {

    }

    @Override
    public void readFromNbt(NbtCompound nbt) {

    }

    @Override
    public void transitionOut(CrossDimensionTeleport crossDimensionTeleport) {

    }

    @Override
    public void transitionIn(CrossDimensionTeleport crossDimensionTeleport) {
        crossDimensionTeleport.playerComponent().loadPlayerSavedInventory();
    }

    /**
     * Where a player coming back from the Backrooms lands: their bed (if it is in the overworld), else the world
     * spawn. On the client, which can't know either, just the world origin.
     */
    public static Vec3d returnPosition(PlayerComponent playerComponent) {
        if (!(playerComponent.player instanceof ServerPlayerEntity serverPlayer)) {
            return new Vec3d(0.5, 64, 0.5);
        }

        ServerWorld overworld = serverPlayer.getServer().getWorld(World.OVERWORLD);
        BlockPos bed = serverPlayer.getSpawnPointPosition();
        // A nether respawn anchor position means nothing in the overworld.
        if (overworld != null && bed != null && serverPlayer.getSpawnPointDimension() == World.OVERWORLD) {
            Optional<Vec3d> respawn = PlayerEntity.findRespawnPosition(overworld, bed, serverPlayer.getSpawnAngle(), serverPlayer.isSpawnForced(), true);
            if (respawn.isPresent()) {
                return respawn.get();
            }
        }
        return overworld != null ? overworld.getSpawnPos().toCenterPos() : new Vec3d(0.5, 64, 0.5);
    }
}
