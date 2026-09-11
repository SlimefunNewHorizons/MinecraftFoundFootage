package com.sp.world.levels.custom;

import com.sp.SPBRevamped;
import com.sp.SPBRevampedClient;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.init.BackroomsLevels;
import com.sp.world.events.infinite_grass.InfiniteGrassAmbience;
import com.sp.world.events.level94.PaperPoliceEvent;
import com.sp.world.generation.chunk_generator.Level94ChunkGenerator;
import com.sp.world.levels.BackroomsLevel;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Level94BackroomsLevel extends BackroomsLevel {

    private static final Vec3d STOP_LEVEL1_POS = new Vec3d(256, 32, 7);
    private static final Vec3d STOP_POOLROOMS_POS = new Vec3d(512, 32, -7);
    private static final Vec3d STOP_LEVEL324_POS = new Vec3d(1024, 32, 7);

    public Level94BackroomsLevel() {
        super("level94", Level94ChunkGenerator.CODEC, new Vec3d(0, 31, 0), BackroomsLevels.LEVEL94_WORLD_KEY);

        this.registerEvent("ambience", InfiniteGrassAmbience::new);
        this.registerEvent("paper", PaperPoliceEvent::new);

        // Exit 1: reach the floating castle and pass the Animated King's trial -> random main level.
        this.registerTransition((world, playerComponent, from) -> {
            List<LevelTransition> playerList = new ArrayList<>();

            if (from instanceof Level94BackroomsLevel
                    && playerComponent.player.getPos().y > 115
                    && playerComponent.player.getPos().squaredDistanceTo(new Vec3d(0, playerComponent.player.getPos().y, 0)) < 40 * 40) {
                playerList.add(getCastleExit(playerComponent));
            }

            return playerList;
        }, this.getLevelId() + " -> castle_exit");

        // Exit 2a: the special STOP sign on the road -> Level 1.
        this.registerTransition((world, playerComponent, from) -> {
            List<LevelTransition> playerList = new ArrayList<>();

            if (from instanceof Level94BackroomsLevel && isAtStopSign(playerComponent, STOP_LEVEL1_POS)) {
                playerList.add(getStopSignTransition(playerComponent, BackroomsLevels.LEVEL1_BACKROOMS_LEVEL));
            }

            return playerList;
        }, this.getLevelId() + " -> " + BackroomsLevels.LEVEL1_BACKROOMS_LEVEL.getLevelId());

        // Exit 2b: the special STOP sign -> Poolrooms.
        this.registerTransition((world, playerComponent, from) -> {
            List<LevelTransition> playerList = new ArrayList<>();

            if (from instanceof Level94BackroomsLevel && isAtStopSign(playerComponent, STOP_POOLROOMS_POS)) {
                playerList.add(getStopSignTransition(playerComponent, BackroomsLevels.POOLROOMS_BACKROOMS_LEVEL));
            }

            return playerList;
        }, this.getLevelId() + " -> " + BackroomsLevels.POOLROOMS_BACKROOMS_LEVEL.getLevelId());

        // Exit 2c: the special STOP sign -> Level 324.
        this.registerTransition((world, playerComponent, from) -> {
            List<LevelTransition> playerList = new ArrayList<>();

            if (from instanceof Level94BackroomsLevel && isAtStopSign(playerComponent, STOP_LEVEL324_POS)) {
                playerList.add(getStopSignTransition(playerComponent, BackroomsLevels.LEVEL324_BACKROOMS_LEVEL));
            }

            return playerList;
        }, this.getLevelId() + " -> " + BackroomsLevels.LEVEL324_BACKROOMS_LEVEL.getLevelId());
    }

    private static boolean isAtStopSign(PlayerComponent playerComponent, Vec3d signPos) {
        Vec3d playerPos = playerComponent.player.getPos();
        if (playerPos.y < 30 || playerPos.y > 38) {
            return false;
        }

        return playerPos.squaredDistanceTo(signPos) < 2.5 * 2.5;
    }

    private LevelTransition getCastleExit(PlayerComponent playerComponent) {
        BackroomsLevel[] mainLevels = {
                BackroomsLevels.LEVEL0_BACKROOMS_LEVEL,
                BackroomsLevels.LEVEL1_BACKROOMS_LEVEL,
                BackroomsLevels.LEVEL2_BACKROOMS_LEVEL
        };
        BackroomsLevel to = mainLevels[this.random.nextInt(mainLevels.length)];

        return new LevelTransition(
                20,
                (teleport, tick) -> {
                    World world = teleport.playerComponent().player.getWorld();

                    if (world.isClient()) {
                        if (tick == 14) {
                            SPBRevampedClient.getCutsceneManager().blackScreen.showBlackScreen(20, true, false);
                        }
                        return;
                    }

                    if (tick == 9) {
                        teleport.playerComponent().setShouldNoClip(true);
                        teleport.playerComponent().sync();
                    }

                    if (tick == 14) {
                        SPBRevamped.sendBlackScreenPacket((ServerPlayerEntity) teleport.playerComponent().player, 20, true, false);
                    }

                    if (tick == 1) {
                        teleport.playerComponent().setShouldNoClip(false);
                        teleport.playerComponent().sync();
                    }
                },
                new CrossDimensionTeleport(playerComponent, to.getSafeSpawnPos(), this, to),
                (teleport, tick) -> {
                    teleport.playerComponent().setShouldNoClip(false);
                    teleport.playerComponent().sync();
                });
    }

    private LevelTransition getStopSignTransition(PlayerComponent playerComponent, BackroomsLevel to) {
        return new LevelTransition(
                10,
                (teleport, tick) -> {
                    World world = teleport.playerComponent().player.getWorld();

                    if (world.isClient()) {
                        if (tick == 4) {
                            SPBRevampedClient.getCutsceneManager().blackScreen.showBlackScreen(20, true, false);
                        }
                        return;
                    }

                    if (tick == 9) {
                        teleport.playerComponent().setShouldNoClip(true);
                        teleport.playerComponent().sync();
                    }

                    if (tick == 4) {
                        SPBRevamped.sendBlackScreenPacket((ServerPlayerEntity) teleport.playerComponent().player, 20, true, false);
                    }

                    if (tick == 1) {
                        teleport.playerComponent().setShouldNoClip(false);
                        teleport.playerComponent().sync();
                    }
                },
                new CrossDimensionTeleport(playerComponent, to.getSafeSpawnPos(), this, to),
                (teleport, tick) -> {
                    teleport.playerComponent().setShouldNoClip(false);
                    teleport.playerComponent().sync();
                });
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
    public boolean hasVanillaLighting() {
        return true;
    }

    @Override
    public boolean rendersSky() {
        return true;
    }

    @Override
    public boolean rendersClouds() {
        return true;
    }
}