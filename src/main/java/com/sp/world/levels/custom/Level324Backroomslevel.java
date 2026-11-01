package com.sp.world.levels.custom;

import com.sp.SPBRevamped;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.init.BackroomsLevels;
import com.sp.init.ModBlocks;
import com.sp.world.events.generic.lights.LightLevelFlicker;
import com.sp.world.events.level324.ScreechSoundEvent;
import com.sp.world.generation.chunk_generator.Level324ChunkGenerator;
import com.sp.world.levels.BackroomsLevel;
import com.sp.world.levels.BackroomsLevelWithLights;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class Level324Backroomslevel extends BackroomsLevel implements BackroomsLevelWithLights {
    private Level0BackroomsLevel.LightState lightState = BackroomsLevelWithLights.LightState.ON;

    public Level324Backroomslevel() {
        super("level324", Level324ChunkGenerator.CODEC, new Vec3d(52,65,21), BackroomsLevels.LEVEL324_WORLD_KEY);

        this.registerEvent("flicker", LightLevelFlicker::new);
        this.registerEvent("ambience", ScreechSoundEvent::new);

        this.registerTransition((world, playerComponent, from) -> {
            List<LevelTransition> playerList = new ArrayList<>();

            int exitRadius = SPBRevamped.getExitSpawnRadius(world);

            if (from instanceof Level324Backroomslevel &&
                    hasGrassBeneath(playerComponent) &&
                    playerComponent.player.getPos().squaredDistanceTo(new Vec3d(0, 65, 0)) >= (double) ((exitRadius / 3) * (exitRadius / 3)) ) {
                playerList.add(getInfiniteFieldsTransition(playerComponent));
            }

            return playerList;
        }, this.getLevelId() + " -> " + BackroomsLevels.INFINITE_FIELD_BACKROOMS_LEVEL.getLevelId());

        this.registerTransition((world, playerComponent, from) -> {
            List<LevelTransition> playerList = new ArrayList<>();

            if (from instanceof Level324Backroomslevel && playerComponent.player.getY() < 20) {
                float[] puddles = puddleCenters(playerComponent.getRouteSeed());
                Vec2f player = new Vec2f((float) playerComponent.player.getX(), (float) playerComponent.player.getZ());
                for (int i = 0; i < puddles.length; i += 2) {
                    if (4 > new Vec2f(puddles[i], puddles[i + 1]).distanceSquared(player)) {
                        playerList.add(getPoolRoomsTransition(playerComponent));
                        break;
                    }
                }
            }

            return playerList;
        }, this.getLevelId() + " -> " + BackroomsLevels.POOLROOMS_BACKROOMS_LEVEL.getLevelId());
    }

    // The exit puddles of the underground floor, before the world turns them.
    private static final float[] PUDDLE_PATTERN = {
            300, 0, -300, 0, 0, 300, 0, -300,
            150, 150, 150, -150, -150, 150, -150, -150,
            100, 200, 100, -200, -100, 200, -100, -200,
            200, 100, -200, 100, 200, -100, -200, -100
    };

    /**
     * The 16 exit puddles as x, z pairs: the same pattern in every world, turned around the center by an angle taken
     * from the world seed (seed 0 keeps it as is). Read by the exit check and by the puddle shader.
     */
    public static float[] puddleCenters(long seed) {
        double angle = seed == 0 ? 0 : (new java.util.Random(seed ^ 0x324L).nextDouble() * Math.PI * 2);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float[] centers = new float[PUDDLE_PATTERN.length];
        for (int i = 0; i < centers.length; i += 2) {
            float x = PUDDLE_PATTERN[i];
            float z = PUDDLE_PATTERN[i + 1];
            centers[i] = x * cos - z * sin;
            centers[i + 1] = x * sin + z * cos;
        }
        return centers;
    }

    private static boolean hasGrassBeneath(PlayerComponent playerComponent) {
        return playerComponent.player.getWorld().getBlockState(playerComponent.player.supportingBlockPos.orElseGet(() ->
                playerComponent.player.getBlockPos().subtract(new Vec3i(0,1,0)))).isOf(ModBlocks.RED_DIRT);
    }

    private LevelTransition getInfiniteFieldsTransition(PlayerComponent playerComponent) {
        return new LevelTransition(
                40,
                (teleport, tick) -> {
                    if (tick == 20) {
                        teleport.playerComponent().setShouldNoClip(true);
                        teleport.playerComponent().sync();
                    }

                    if (tick == 14) {
                        SPBRevamped.sendBlackScreenPacket((ServerPlayerEntity) teleport.playerComponent().player, 20, true, false);
                    }

                    //After the screen turns black THEN teleport
                    if (tick == 1) {
                        teleport.playerComponent().setShouldNoClip(false);
                        teleport.playerComponent().sync();
                    }
                }, // Tick
                new CrossDimensionTeleport(
                        playerComponent,
                        BackroomsLevels.INFINITE_FIELD_BACKROOMS_LEVEL.getSafeSpawnPos(),
                        this,
                        BackroomsLevels.INFINITE_FIELD_BACKROOMS_LEVEL
                ),
                (teleport, tick) -> {
                    teleport.playerComponent().setShouldNoClip(false);
                    teleport.playerComponent().sync();
                }); // Cancel
    }

    private LevelTransition getPoolRoomsTransition(PlayerComponent playerComponent) {
        return new LevelTransition(
                10,
                (teleport, tick) -> {
                    if (tick == 9) {
                        teleport.playerComponent().setShouldNoClip(true);
                        teleport.playerComponent().sync();
                    }

                    if (tick == 4) {
                        SPBRevamped.sendBlackScreenPacket((ServerPlayerEntity) teleport.playerComponent().player, 20, true, false);
                    }

                    //After the screen turns black THEN teleport
                    if (tick == 1) {
                        teleport.playerComponent().setShouldNoClip(false);
                        teleport.playerComponent().sync();
                    }
                }, // Tick
                new CrossDimensionTeleport(
                        playerComponent,
                        BackroomsLevels.POOLROOMS_BACKROOMS_LEVEL.getSafeSpawnPos(),
                        this,
                        BackroomsLevels.POOLROOMS_BACKROOMS_LEVEL
                ),
                (teleport, tick) -> {
                    teleport.playerComponent().setShouldNoClip(false);
                    teleport.playerComponent().sync();
                }); // Cancel
    }

    @Override
    public boolean rendersSky() {
        return false;
    }

    @Override
    public boolean rendersClouds() {
        return false;
    }

    public int nextEventDelay() {
        return random.nextInt(1000, 1200);
    }

    @Override
    public void writeToNbt(NbtCompound nbt) {
        nbt.putString("lightState", lightState.name());
    }

    @Override
    public void readFromNbt(NbtCompound nbt) {
        try {
            this.lightState = BackroomsLevelWithLights.LightState.valueOf(nbt.getString("lightState"));
        } catch (Exception e) {
            this.lightState = BackroomsLevelWithLights.LightState.ON;
        }
    }

    @Override
    public void transitionOut(CrossDimensionTeleport crossDimensionTeleport) {

    }

    @Override
    public void transitionIn(CrossDimensionTeleport crossDimensionTeleport) {

    }

    public void setLightState(Level0BackroomsLevel.LightState lightState) {
        this.justChanged();
        this.lightState = lightState;
    }

    public Level0BackroomsLevel.LightState getLightState() {
        return this.lightState;
    }
}
