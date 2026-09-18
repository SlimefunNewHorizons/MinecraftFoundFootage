package com.sp.entity.custom;

import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Level 94's "Policía de Papel": a flat paper cutout of a policeman that only moves when
 * nobody is looking (the opposite of the Smiler creature). When it catches a player it
 * silences them in the voice chat for 15-30 seconds and then crumbles away.
 */
public class PaperPoliceEntity extends MobEntity {

    private static final double ACTIVATION_DISTANCE = 24.0;
    private static final double CATCH_DISTANCE = 1.7;

    private UUID silencedPlayer;
    private int silenceTicksLeft;
    private int leaveTicks = -1;

    public PaperPoliceEntity(EntityType<? extends MobEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    protected void initGoals() {
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, 0, true, false, null));
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient()) {
            return;
        }

        // Un-mute the captured player once the silence has run out.
        if (this.silencedPlayer != null) {
            this.silenceTicksLeft--;
            if (this.silenceTicksLeft <= 0) {
                this.unmutePlayer();
            }
        }

        // After a capture it stands frozen for a moment, then crumbles to dust.
        if (this.leaveTicks >= 0) {
            if (--this.leaveTicks <= 0) {
                this.discard();
            }
            this.getNavigation().stop();
            return;
        }

        PlayerEntity player = this.getWorld().getClosestPlayer(this, ACTIVATION_DISTANCE);
        if (player == null) {
            this.getNavigation().stop();
            return;
        }

        if (this.isPlayerStaring(player)) {
            // Only moves when nobody is looking.
            this.getNavigation().stop();
        } else {
            this.getNavigation().startMovingTo(player, 1.0);

            if (this.squaredDistanceTo(player) < CATCH_DISTANCE * CATCH_DISTANCE) {
                this.capture(player);
            }
        }
    }

    private void capture(PlayerEntity player) {
        PlayerComponent component = InitializeComponents.PLAYER.get(player);
        this.silenceTicksLeft = 300 + this.random.nextInt(300); // 15 to 30 real seconds
        component.setMuteTimer(this.silenceTicksLeft);

        player.damage(this.getDamageSources().mobAttack(this), 3.0F);

        this.silencedPlayer = player.getUuid();
        this.leaveTicks = 60;
        this.getNavigation().stop();
    }

    private void unmutePlayer() {
        if (this.silencedPlayer == null) {
            return;
        }

        PlayerEntity player = this.getWorld().getPlayerByUuid(this.silencedPlayer);
        if (player != null) {
            PlayerComponent component = InitializeComponents.PLAYER.get(player);
            component.setShouldBeMuted(false);
            component.sync();
        }

        this.silencedPlayer = null;
    }

    //From Enderman. Don't need anything too fancy
    private boolean isPlayerStaring(PlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d toEntity = new Vec3d(this.getX() - player.getX(), this.getEyeY() - player.getEyeY(), this.getZ() - player.getZ());
        double distance = toEntity.length();
        toEntity = toEntity.normalize();
        double dot = look.dotProduct(toEntity);
        return dot > 1.0 - 0.35 / distance && player.canSee(this);
    }

    public static DefaultAttributeContainer.Builder createPaperPoliceAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }
}