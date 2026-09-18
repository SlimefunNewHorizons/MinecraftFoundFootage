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

            Vec2f[] puddleLocations = new Vec2f[]{
                    new Vec2f(300.0f, 0.0f),
                    new Vec2f(-300.0f, 0.0f),
                    new Vec2f(0.0f, 300.0f),
                    new Vec2f(0.0f, -300.0f),
                    new Vec2f(150.0f, 150.0f),
                    new Vec2f(150.0f, -150.0f),
                    new Vec2f(-150.0f, 150.0f),
                    new Vec2f(-150.0f, -150.0f),
                    new Vec2f(100.0f, 200.0f),
                    new Vec2f(100.0f, -200.0f),
                    new Vec2f(-100.0f, 200.0f),
                    new Vec2f(-100.0f, -200.0f),
                    new Vec2f(200.0f, 100.0f),
                    new Vec2f(-200.0f, 100.0f),
                    new Vec2f(200.0f, -100.0f),
                    new Vec2f(-200.0f, -100.0f)
            };

            if (from instanceof Level324Backroomslevel && playerComponent.player.getY() < 20) {
                for (Vec2f vec2f : puddleLocations) {
                    if (4 > vec2f.distanceSquared(new Vec2f((float) playerComponent.player.getX(), (float) playerComponent.player.getZ()))) {
                        playerList.add(getPoolRoomsTransition(playerComponent));
                    }
                }
            }

            return playerList;
        }, this.getLevelId() + " -> " + BackroomsLevels.POOLROOMS_BACKROOMS_LEVEL.getLevelId());
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
