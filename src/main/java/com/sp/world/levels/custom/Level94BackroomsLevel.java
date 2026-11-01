package com.sp.world.levels.custom;

import com.sp.SPBRevamped;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.init.BackroomsLevels;
import com.sp.world.events.infinite_grass.InfiniteGrassAmbience;
import com.sp.world.events.level94.PaperPoliceEvent;
import com.sp.world.generation.chunk_generator.Level94ChunkGenerator;
import com.sp.world.generation.chunk_generator.Level94Layout;
import com.sp.world.levels.BackroomsLevel;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Level94BackroomsLevel extends BackroomsLevel {

    public Level94BackroomsLevel() {
        super("level94", Level94ChunkGenerator.CODEC, new Vec3d(0.5, Level94Layout.BASE_Y, 0.5), BackroomsLevels.LEVEL94_WORLD_KEY);
    }

    @Override
    public void register() {
        super.register();

        this.registerEvent("ambience", InfiniteGrassAmbience::new);
        this.registerEvent("paper", PaperPoliceEvent::new);

        // The exits: the special STOP signs of the Stage zone. Stand still in front of one long enough and it
        // lets you go; the Neighbor only comes closer while you are not looking.
        BackroomsLevel[] destinations = {
                BackroomsLevels.LEVEL1_BACKROOMS_LEVEL,
                BackroomsLevels.POOLROOMS_BACKROOMS_LEVEL,
                BackroomsLevels.LEVEL324_BACKROOMS_LEVEL
        };
        for (int i = 0; i < Level94Layout.SPECIAL_STOP_X.length; i++) {
            registerStopSignExit(i, destinations[i % destinations.length]);
        }
    }

    /** How long (in ticks) the player has to stand still at an exit sign. */
    public static final int EXIT_STILL_TICKS = 200;

    private final Map<UUID, Integer> stillTicks = new HashMap<>();
    private final Map<UUID, Vec3d> lastPositions = new HashMap<>();

    private void registerStopSignExit(int signIndex, BackroomsLevel to) {
        this.registerTransition((world, playerComponent, from) -> {
            List<LevelTransition> playerList = new ArrayList<>();
            // Looked up every time: where the sign stands depends on the world's seed
            Vec3d signPos = Vec3d.ofBottomCenter(Level94Layout.getSpecialStopSign(signIndex));
            if (!(from instanceof Level94BackroomsLevel) || !isAtStopSign(playerComponent, signPos)) {
                return playerList;
            }

            UUID uuid = playerComponent.player.getUuid();
            Vec3d pos = playerComponent.player.getPos();
            Vec3d last = this.lastPositions.put(uuid, pos);
            boolean still = last != null && last.squaredDistanceTo(pos) < 0.0004;
            int ticks = still ? this.stillTicks.getOrDefault(uuid, 0) + 1 : 0;
            this.stillTicks.put(uuid, ticks);

            if (ticks == 20 && playerComponent.player instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.sendMessage(Text.translatable("spb-revamped.level94.exit.wait").formatted(Formatting.GRAY, Formatting.ITALIC), true);
            }

            if (ticks >= EXIT_STILL_TICKS) {
                this.stillTicks.remove(uuid);
                this.lastPositions.remove(uuid);
                playerList.add(getStopSignTransition(playerComponent, to));
            }

            return playerList;
        }, this.getLevelId() + " -> exit " + signIndex);
    }

    /**
     * Ticks the player has been standing still at an exit sign (0 if not).
     */
    public int getStillTicks(UUID player) {
        return this.stillTicks.getOrDefault(player, 0);
    }

    private static boolean isAtStopSign(PlayerComponent playerComponent, Vec3d signPos) {
        Vec3d playerPos = playerComponent.player.getPos();
        if (Math.abs(playerPos.y - signPos.y) > 4) {
            return false;
        }

        double dx = playerPos.x - signPos.x;
        double dz = playerPos.z - signPos.z;
        return dx * dx + dz * dz < 2.5 * 2.5;
    }

    private LevelTransition getStopSignTransition(PlayerComponent playerComponent, BackroomsLevel to) {
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
