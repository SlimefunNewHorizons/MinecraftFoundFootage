package com.sp.world.events.level94;

import com.sp.entity.custom.PaperPoliceEntity;
import com.sp.init.BackroomsLevels;
import com.sp.world.generation.chunk_generator.Level94Layout;
import com.sp.init.ModEntities;
import com.sp.world.events.AbstractEvent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.List;

public class PaperPoliceEvent extends AbstractEvent {
    private int spawnDelay = 200;

    @Override
    public void init(World world) {
        this.spawnDelay = 200;
    }

    @Override
    public int duration() {
        return 2400;
    }

    @Override
    public void ticks(int ticks, World world) {
        if (world.isClient()) {
            return;
        }

        if (world.getRegistryKey() != BackroomsLevels.LEVEL94_WORLD_KEY) {
            return;
        }

        this.spawnDelay--;

        if (this.spawnDelay > 0) {
            return;
        }

        this.spawnDelay = 300 + world.random.nextInt(300);

        Random random = Random.create();
        List<? extends PlayerEntity> playerList = world.getPlayers();

        for (PlayerEntity player : playerList) {
            // The paper cutouts only live on the Stage, where everything else is a cutout too.
            if (Level94Layout.zone(player.getBlockX()) < 4 || world.random.nextInt(10) != 0) {
                continue;
            }

            PaperPoliceEntity police = ModEntities.PAPER_POLICE_ENTITY.create(world);

            if (police == null) {
                continue;
            }

            BlockPos.Mutable mutable = new BlockPos.Mutable();
            float randomAngle = random.nextFloat() * 360.0f;
            Vec3d spawnPos = new Vec3d(0, 0, 18).rotateY(randomAngle).add(player.getPos());

            if (!world.getBlockState(mutable.set(spawnPos.x, spawnPos.y, spawnPos.z)).blocksMovement()) {
                police.refreshPositionAndAngles(Math.floor(spawnPos.x) + 0.5f, spawnPos.y, Math.floor(spawnPos.z) + 0.5f, 0.0f, 0.0f);
                world.spawnEntity(police);
                this.spawnDelay = 400;
            }
        }
    }
}