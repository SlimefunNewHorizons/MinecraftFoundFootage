package com.sp.world.levels.custom;

import com.sp.SPBRevamped;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.init.BackroomsLevels;
import com.sp.world.events.infinite_grass.InfiniteGrassAmbience;
import com.sp.world.generation.chunk_generator.InfGrassChunkGenerator;
import com.sp.world.levels.BackroomsLevel;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InfiniteGrassBackroomsLevel extends BackroomsLevel {

    public InfiniteGrassBackroomsLevel() {
        super("inf_grass", InfGrassChunkGenerator.CODEC, new Vec3d(0, 31, 0), BackroomsLevels.INFINITE_FIELD_WORLD_KEY);
    }

    @Override
    public void register() {
        super.register();

        this.registerEvent("ambience", InfiniteGrassAmbience::new);

        this.registerTransition((world, playerComponent, from) -> {
            List<LevelTransition> playerList = new ArrayList<>();

            if (from instanceof InfiniteGrassBackroomsLevel && playerComponent.player.getPos().y > 57.5 && playerComponent.player.isOnGround()) {
                playerList.add(getOverworldTransition(playerComponent));
            }

            return playerList;
        }, this.getLevelId() + "->" + BackroomsLevels.OVERWORLD_REPRESENTING_BACKROOMS_LEVEL.getLevelId());

        // Wandering far enough through the endless field slowly turns it into the hills of Level 94.
        this.registerTransition((world, playerComponent, from) -> {
            List<LevelTransition> playerList = new ArrayList<>();
            double distance = SPBRevamped.getExitSpawnRadius(world) * 2.0;
            Vec3d pos = playerComponent.player.getPos();

            if (from instanceof InfiniteGrassBackroomsLevel && playerComponent.player.isOnGround()
                    && pos.x * pos.x + pos.z * pos.z >= distance * distance) {
                playerList.add(getLevel94Transition(playerComponent));
            }

            return playerList;
        }, this.getLevelId() + "->" + BackroomsLevels.LEVEL94_BACKROOMS_LEVEL.getLevelId());
    }

    private LevelTransition getLevel94Transition(PlayerComponent playerComponent) {
        return new LevelTransition(
                40,
                (teleport, tick) -> {
                    if (tick == 20) {
                        SPBRevamped.sendBlackScreenPacket((ServerPlayerEntity) teleport.playerComponent().player, 40, true, false);
                    }
                },
                new CrossDimensionTeleport(
                        playerComponent,
                        BackroomsLevels.LEVEL94_BACKROOMS_LEVEL.getSafeSpawnPos(),
                        this,
                        BackroomsLevels.LEVEL94_BACKROOMS_LEVEL),
                (teleport, tick) -> {});
    }

    private LevelTransition getOverworldTransition(PlayerComponent playerComponent) {
        Optional<Vec3d> optional = Optional.empty();
        BlockPos blockPos1 = new BlockPos(0, 64, 0);
        if (playerComponent.player instanceof ServerPlayerEntity serverPlayer) {
            BlockPos blockPos = serverPlayer.getSpawnPointPosition();
            float f = serverPlayer.getSpawnAngle();
            boolean bl = serverPlayer.isSpawnForced();
            ServerWorld serverWorld = playerComponent.player.getWorld().getServer().getWorld(World.OVERWORLD);

            // Only use the personal spawn point (bed) if it is in the overworld; a nether respawn anchor
            // position means nothing in the overworld.
            if (serverWorld != null && blockPos != null && serverPlayer.getSpawnPointDimension() == World.OVERWORLD) {
                optional = PlayerEntity.findRespawnPosition(serverWorld, blockPos, f, bl, true);
            }

            World overworld = playerComponent.player.getWorld().getServer().getWorld(World.OVERWORLD);
            blockPos1 = overworld.getSpawnPos();
        }

        return new LevelTransition(
                1,
                (teleport, tick) -> {
                    if (!teleport.playerComponent().player.getWorld().isClient()) {
                        teleport.playerComponent().loadPlayerSavedInventory();
                    }
                },
                new CrossDimensionTeleport(
                        playerComponent,
                        optional.orElse(blockPos1.toCenterPos()),
                        this,
                        BackroomsLevels.OVERWORLD_REPRESENTING_BACKROOMS_LEVEL),
                (teleport, tick) -> {});
    }

    @Override
    public int nextEventDelay() {
        return random.nextInt(1000, 1200);
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
        crossDimensionTeleport.playerComponent().player.fallDistance = 0;
    }

    @Override
    public BoolTextPair allowsTorch() {
        return new BoolTextPair(false, Text.translatable("spb-revamped.flashlight.wet1").append(Text.translatable("spb-revamped.flashlight.wet2").formatted(Formatting.RED)));
    }

    @Override
    public boolean hasVanillaLighting() {
        return true;
    }
}
