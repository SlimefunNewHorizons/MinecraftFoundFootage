package com.sp.world.levels.custom;

import com.sp.SPBRevamped;
import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.init.BackroomsLevels;
import com.sp.world.events.AbstractEvent;
import com.sp.world.events.generic.lights.LightLevelBlackout;
import com.sp.world.events.generic.lights.LightLevelFlicker;
import com.sp.world.events.level0.Level0IntercomBasic;
import com.sp.world.events.level0.Level0Music;
import com.sp.world.generation.chunk_generator.Level0ChunkGenerator;
import com.sp.world.levels.BackroomsLevel;
import com.sp.world.levels.BackroomsLevelWithLights;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class Level0BackroomsLevel extends BackroomsLevel implements BackroomsLevelWithLights {
    ///execute in spb-revamped:level0 run tp 1063 15 24

    private int blackoutCount = 0;
    private int intercomCount = 0;
    private LightState lightState = LightState.ON;

    public Level0BackroomsLevel() {
        super("level0", Level0ChunkGenerator.CODEC, new RoomCount(8), new Vec3d(0, 21, 0), BackroomsLevels.LEVEL0_WORLD_KEY);
    }

    @Override
    public boolean rendersClouds() {
        return false;
    }

    @Override
    public boolean rendersSky() {
        return false;
    }

    @Override
    public void register() {
        super.register();
        this.registerEvent("blackout", LightLevelBlackout::new);
        this.registerEvent("flicker", LightLevelFlicker::new);
        this.registerEvent("intercom", Level0IntercomBasic::new);
        this.registerEvent("music", Level0Music::new);

        this.registerTransition((world, playerComponent, from) -> {
            List<LevelTransition> playerList = new ArrayList<>();

            if (from instanceof Level0BackroomsLevel && playerComponent.player.getPos().getY() <= 11 && playerComponent.player.isOnGround()) {
                playerList.add(getLevel1Transition(playerComponent));
            }

            return playerList;
        }, this.getLevelId() + "->" + BackroomsLevels.LEVEL1_BACKROOMS_LEVEL.getLevelId());
    }

    private LevelTransition getLevel1Transition(PlayerComponent playerComponent) {
        return new LevelTransition(
            30,
            (teleport, tick) -> {
                if (!teleport.playerComponent().player.getWorld().isClient() && tick == 30) {
                    if(!teleport.playerComponent().isTeleporting()) {
                        SPBRevamped.sendLevelTransitionLightsOutPacket((ServerPlayerEntity) teleport.playerComponent().player, 80);
                    }
                }
            },
            new CrossDimensionTeleport(playerComponent,
                calculateLevel1TeleportCoords(
                    playerComponent.player,
                    playerComponent.player.getChunkPos()),
                this,
                BackroomsLevels.LEVEL1_BACKROOMS_LEVEL),
        (teleport, tick) -> {});
    }

    private Vec3d calculateLevel1TeleportCoords(PlayerEntity player, ChunkPos chunkPos) {
        if(chunkPos.x == player.getChunkPos().x && chunkPos.z == player.getChunkPos().z) {
            int chunkX = chunkPos.getStartX();
            int chunkZ = chunkPos.getStartZ();

            double playerX = player.getPos().x;
            double playerZ = player.getPos().z;

            return new Vec3d(playerX - chunkX, player.getPos().y + 15, playerZ - chunkZ);
        } else {
            return this.getSpawnPos();
        }
    }

    @Override
    public AbstractEvent getRandomEvent(World world) {
        AbstractEvent activeEvent = super.getRandomEvent(world);

        if (activeEvent instanceof LightLevelBlackout) {
            this.blackoutCount++;
            if (this.blackoutCount > 2) {
                int attempts = 0;
                while (activeEvent instanceof LightLevelBlackout && attempts++ < 10) {
                    activeEvent = super.getRandomEvent(world);
                }
            }
        }

        return activeEvent;
    }

    @Override
    public int nextEventDelay() {
        return random.nextInt(1000, 1500);
    }

    @Override
    public void writeToNbt(NbtCompound nbt) {
        nbt.putInt("blackoutCount", blackoutCount);
        nbt.putInt("intercomCount", intercomCount);
        nbt.putString("lightState", lightState.name());
    }

    @Override
    public void readFromNbt(NbtCompound nbt) {
        this.blackoutCount = nbt.getInt("blackoutCount");
        this.intercomCount = nbt.getInt("intercomCount");
        try {
            this.lightState = LightState.valueOf(nbt.getString("lightState"));
        } catch (Exception e) {
            this.lightState = LightState.ON;
        }
    }

    @Override
    public void transitionOut(CrossDimensionTeleport crossDimensionTeleport) {
    }

    @Override
    public void transitionIn(CrossDimensionTeleport crossDimensionTeleport) {

    }

    @Override
    public BlockPos findCheckpoint(Vec3d pos) {
        int gridX = Math.floorDiv((int) pos.x, 80) * 80;
        int gridZ = Math.floorDiv((int) pos.z, 80) * 80;
        return new BlockPos(gridX + 15, 21, gridZ + 15);
    }

    public int getIntercomCount() {
        return intercomCount;
    }

    public void setIntercomCount(int intercomCount) {
        this.justChanged();
        this.intercomCount = intercomCount;
    }

    public void addIntercomCount() {
        this.justChanged();
        this.intercomCount++;
    }

    public void setLightState(LightState lightState) {
        this.justChanged();
        this.lightState = lightState;
    }

    public LightState getLightState() {
        return this.lightState;
    }
}
