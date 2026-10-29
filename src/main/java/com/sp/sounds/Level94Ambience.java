package com.sp.sounds;

import com.sp.init.BackroomsLevels;
import com.sp.util.TickScheduler;
import com.sp.world.generation.chunk_generator.Level94Layout;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * The sounds of Level 94, all made of things that should be harmless:
 * <ul>
 *     <li>your footsteps echo, one step late, from behind you (from the Déjà vu zone on);</li>
 *     <li>somewhere far away a lawnmower is running; it stops the moment you turn towards it;</li>
 *     <li>doors close behind your back, in houses you just walked past.</li>
 * </ul>
 * The Stage zone has none of it except the echo: there is nothing left to make a sound.
 */
public final class Level94Ambience {
    private static Vec3d lastPos;
    private static double stepDistance;
    private static int mowerCooldown = 600;
    private static DistantSound mower;
    private static int doorCooldown = 500;

    private Level94Ambience() {}

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.world.getRegistryKey() != BackroomsLevels.LEVEL94_WORLD_KEY) {
            lastPos = null;
            mower = null;
            return;
        }

        int zone = Level94Layout.zone(player.getBlockX());
        tickEcho(client, player, zone);
        tickMower(client, player, zone);
        tickDoors(client, player, zone);
    }

    private static void tickEcho(MinecraftClient client, ClientPlayerEntity player, int zone) {
        Vec3d pos = player.getPos();
        double moved = lastPos == null ? 0 : pos.subtract(lastPos).horizontalLength();
        lastPos = pos;
        if (zone < 2 || !player.isOnGround() || player.isSneaking() || moved > 1.0) return;

        stepDistance += moved;
        if (stepDistance < 1.7) return;
        stepDistance = 0;

        // One step late, from right behind you.
        float volume = 0.08F + 0.06F * zone;
        TickScheduler.CLIENT.schedule(zone >= 4 ? 5 : 7, () -> {
            if (client.player == null || client.world == null) return;
            BlockPos below = client.player.getBlockPos().down();
            BlockState state = client.world.getBlockState(below);
            if (state.isAir()) return;
            Vec3d behind = client.player.getPos().subtract(client.player.getRotationVector().multiply(1, 0, 1).normalize().multiply(2.0));
            client.world.playSound(behind.x, behind.y, behind.z, state.getSoundGroup().getStepSound(), SoundCategory.PLAYERS, volume, 0.86F, false);
        });
    }

    private static void tickMower(MinecraftClient client, ClientPlayerEntity player, int zone) {
        if (mower != null && mower.isDone()) mower = null;
        if (mower != null || zone >= 4 || --mowerCooldown > 0) return;

        mowerCooldown = 1800 + player.getRandom().nextInt(2400);
        double angle = player.getRandom().nextDouble() * Math.PI * 2;
        Vec3d pos = player.getPos().add(Math.cos(angle) * 34, 1, Math.sin(angle) * 34);
        mower = new DistantSound(SoundEvents.ENTITY_MINECART_RIDING, pos, 2.2F, 0.55F, 900);
        client.getSoundManager().play(mower);
    }

    private static void tickDoors(MinecraftClient client, ClientPlayerEntity player, int zone) {
        if (zone < 2 || zone >= 4 || --doorCooldown > 0) return;
        doorCooldown = 700 + player.getRandom().nextInt(900);

        Vec3d look = player.getRotationVector().multiply(1, 0, 1).normalize();
        Vec3d eye = player.getPos();
        Vec3d[] best = {null};
        double[] bestDistance = {Double.MAX_VALUE};
        int px = player.getBlockX();
        int pz = player.getBlockZ();

        Level94Layout.forEachLot(px - 16, pz - 16, px + 16, pz + 16, house -> {
            Vec3d door = new Vec3d(house.worldX(0, 0) + 0.5, house.floorY() + 1.5, house.worldZ(0, 0) + 0.5);
            Vec3d to = door.subtract(eye).multiply(1, 0, 1);
            double distance = to.length();
            // Only doors behind you
            if (distance < 5 || distance > 16 || to.normalize().dotProduct(look) > -0.3) return;
            if (distance < bestDistance[0]) {
                bestDistance[0] = distance;
                best[0] = door;
            }
        });

        if (best[0] != null) {
            client.world.playSound(best[0].x, best[0].y, best[0].z, SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 0.9F, 0.9F, false);
        }
    }

    /**
     * A looping sound standing still somewhere far away. It stops as soon as the player looks towards it.
     */
    private static final class DistantSound extends MovingSoundInstance {
        private final int lifetime;
        private int age;

        DistantSound(SoundEvent sound, Vec3d pos, float volume, float pitch, int lifetime) {
            super(sound, SoundCategory.AMBIENT, SoundInstance.createRandom());
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
            this.volume = volume;
            this.pitch = pitch;
            this.repeat = true;
            this.repeatDelay = 0;
            this.attenuationType = AttenuationType.LINEAR;
            this.lifetime = lifetime;
        }

        @Override
        public void tick() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null
                    || client.world.getRegistryKey() != BackroomsLevels.LEVEL94_WORLD_KEY || ++this.age > this.lifetime) {
                this.setDone();
                return;
            }

            Vec3d toSound = new Vec3d(this.x, this.y, this.z).subtract(client.player.getEyePos()).normalize();
            if (toSound.dotProduct(client.player.getRotationVector()) > 0.85) {
                this.setDone();
            }
        }
    }
}
