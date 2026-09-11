package com.sp.entity.voice;

import com.javaai.core.JavaAI;
import com.javaai.player.PlayerInputSnapshot;
import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.SkinWalkerComponent;
import com.sp.entity.custom.SkinWalkerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SkinWalkerMovement {
    private final SkinWalkerEntity entity;
    private final SkinWalkerComponent component;
    private final JavaAI javaAI;

    private Vec3d currentTarget;
    private int stuckCounter = 0;
    private Vec3d lastPos;
    private FollowMode currentMode = FollowMode.DIRECT;
    private float speedMultiplier = 1.0f;
    private int modeChangeCooldown = 0;
    private int tickCount = 0;
    private boolean learningActive = false;
    private int pathRecalcCooldown = 0;
    private UUID trackedTargetUUID;
    private FollowMode lastMode;

    private static final int MIMIC_DELAY_TICKS = 10;
    private final ArrayDeque<MimicSnapshot> inputBuffer = new ArrayDeque<>();

    private record MimicSnapshot(boolean sprinting, boolean sneaking, boolean jumping, Vec3d position) {}

    private static final float MIN_DISTANCE = 5.0f;
    private static final float MAX_DISTANCE = 15.0f;
    private static final float PURSUIT_DISTANCE = 20.0f;

    public enum FollowMode {
        DIRECT, ORBITAL, RANDOM, PURSUIT, MIMIC
    }

    public SkinWalkerMovement(SkinWalkerEntity entity) {
        this.entity = entity;
        this.component = InitializeComponents.SKIN_WALKER.get(entity);
        this.javaAI = JavaAI.getInstance();
    }

    public void tick() {
        if (entity.getWorld().isClient) return;

        PlayerEntity target = findTargetPlayer();
        if (target == null) return;

        recordPlayerInput(target);
        recordMimicInput(target);
        updateFollowMode(target);

        if (lastMode != null && lastMode == FollowMode.MIMIC && currentMode != FollowMode.MIMIC) {
            entity.setSprinting(false);
            entity.setSneaking(false);
        }
        lastMode = currentMode;

        calculateMovement(target);
        moveTowardTarget();
        applyLookDirection(target);
        checkIfStuck();
        tickCount++;
    }

    private void recordPlayerInput(PlayerEntity player) {
        UUID playerUUID = player.getUuid();
        boolean jumping = !player.isOnGround() && player.getVelocity().y > 0.1;
        PlayerInputSnapshot snapshot = new PlayerInputSnapshot(
            player.getX(), player.getY(), player.getZ(),
            player.getVelocity().x, player.getVelocity().y, player.getVelocity().z,
            player.headYaw, player.getPitch(),
            player.forwardSpeed > 0, player.forwardSpeed < 0,
            player.sidewaysSpeed < 0, player.sidewaysSpeed > 0,
            player.isSprinting(), player.isSneaking(), jumping,
            player.isOnGround()
        );
        javaAI.recordInput(playerUUID, snapshot);

        if (!learningActive && javaAI.getHistorySize(playerUUID) > 20) {
            learningActive = true;
        }
    }

    private void recordMimicInput(PlayerEntity player) {
        UUID playerUUID = player.getUuid();
        if (!playerUUID.equals(trackedTargetUUID)) {
            trackedTargetUUID = playerUUID;
            inputBuffer.clear();
        }

        boolean jumping = !player.isOnGround() && player.getVelocity().y > 0.1;
        inputBuffer.addLast(new MimicSnapshot(
                player.isSprinting(), player.isSneaking(), jumping, player.getPos()
        ));
        while (inputBuffer.size() > MIMIC_DELAY_TICKS) {
            inputBuffer.removeFirst();
        }
    }

    private void updateFollowMode(PlayerEntity target) {
        if (modeChangeCooldown > 0) {
            modeChangeCooldown--;
            return;
        }

        float currentDistance = (float) target.getPos().distanceTo(entity.getPos());

        if (currentDistance > PURSUIT_DISTANCE) {
            currentMode = FollowMode.PURSUIT;
            speedMultiplier = 1.3f + (float)(Math.random() * 0.2);
        } else if (currentDistance > MAX_DISTANCE) {
            currentMode = FollowMode.DIRECT;
            speedMultiplier = 1.0f + (float)(Math.random() * 0.2);
        } else if (currentDistance < MIN_DISTANCE) {
            currentMode = FollowMode.ORBITAL;
            speedMultiplier = 0.6f + (float)(Math.random() * 0.2);
        } else if (learningActive && Math.random() < 0.3) {
            currentMode = FollowMode.MIMIC;
            speedMultiplier = calculateMimicSpeed(target);
        } else {
            double roll = Math.random();
            if (roll < 0.5) {
                currentMode = FollowMode.DIRECT;
                speedMultiplier = 0.85f + (float)(Math.random() * 0.3);
            } else if (roll < 0.8) {
                currentMode = FollowMode.ORBITAL;
                speedMultiplier = 0.7f + (float)(Math.random() * 0.3);
            } else {
                currentMode = FollowMode.RANDOM;
                speedMultiplier = 0.6f + (float)(Math.random() * 0.4);
            }
        }

        modeChangeCooldown = 20 + (int)(Math.random() * 40);
    }

    private float calculateMimicSpeed(PlayerEntity target) {
        UUID targetUUID = target.getUuid();
        Map<String, Double> style = javaAI.getPlayerStyle(targetUUID);

        if (style.isEmpty()) return 0.9f;

        double avgSpeed = style.getOrDefault("avg_speed", 0.15);
        double sprintTendency = style.getOrDefault("sprint_tendency", 0.3);
        double caution = style.getOrDefault("caution", 0.5);

        float baseSpeed = (float) (0.7 + avgSpeed * 2);
        if (sprintTendency > 0.5) baseSpeed += 0.2f;
        if (caution > 0.7) baseSpeed -= 0.1f;

        return Math.max(0.6f, Math.min(1.4f, baseSpeed + (float)(Math.random() * 0.2 - 0.1)));
    }

    private void calculateMovement(PlayerEntity target) {
        Vec3d playerPos = target.getPos();
        Vec3d swPos = entity.getPos();

        switch (currentMode) {
            case MIMIC:
                currentTarget = getDelayedMimicTarget(target);
                break;
            case DIRECT:
                currentTarget = calculateDirectPosition(playerPos, swPos);
                break;
            case ORBITAL:
                currentTarget = calculateOrbitalPosition(playerPos, swPos);
                break;
            case RANDOM:
                currentTarget = calculateRandomPosition(playerPos);
                break;
            case PURSUIT:
                currentTarget = calculatePursuitPosition(swPos, playerPos);
                break;
        }

        currentTarget = addNaturalVariation(currentTarget, playerPos);
    }

    private Vec3d getDelayedMimicTarget(PlayerEntity target) {
        MimicSnapshot snapshot = inputBuffer.peekFirst();
        if (snapshot != null) {
            return snapshot.position();
        }
        return target.getPos();
    }

    private void applyMimicBehaviour() {
        if (currentMode != FollowMode.MIMIC) return;

        MimicSnapshot snapshot = inputBuffer.peekFirst();
        if (snapshot == null) return;

        entity.setSprinting(snapshot.sprinting());
        entity.setSneaking(snapshot.sneaking());
        if (snapshot.jumping()) {
            entity.doJump();
        }

        if (Math.random() < 0.3) {
            ((SkinWalkerEntity.SkinWalkerLookControl) entity.getLookControl())
                    .lookAt(new Vec3d(snapshot.position().x, entity.getY(), snapshot.position().z), 5);
        }
    }

    private Vec3d calculateDirectPosition(Vec3d playerPos, Vec3d swPos) {
        Vec3d toEntity = swPos.subtract(playerPos);
        double angleToEntity = Math.atan2(toEntity.z, toEntity.x);
        double wanderAngle = angleToEntity + (Math.random() - 0.5) * 0.8;

        double offsetX = -Math.cos(wanderAngle) * MAX_DISTANCE * 0.7;
        double offsetZ = -Math.sin(wanderAngle) * MAX_DISTANCE * 0.7;

        offsetX += (Math.random() - 0.5) * 3;
        offsetZ += (Math.random() - 0.5) * 3;

        return new Vec3d(playerPos.x + offsetX, playerPos.y, playerPos.z + offsetZ);
    }

    private Vec3d calculateOrbitalPosition(Vec3d playerPos, Vec3d swPos) {
        Vec3d toSw = swPos.subtract(playerPos);
        double currentAngle = Math.atan2(toSw.z, toSw.x);
        double newAngle = currentAngle + 0.05;
        double radius = 8 + Math.sin(tickCount * 0.02) * 2;

        return new Vec3d(
            playerPos.x + Math.cos(newAngle) * radius,
            playerPos.y,
            playerPos.z + Math.sin(newAngle) * radius
        );
    }

    private Vec3d calculateRandomPosition(Vec3d playerPos) {
        double angle = Math.random() * Math.PI * 2;
        double distance = 6 + Math.random() * 8;

        return new Vec3d(
            playerPos.x + Math.cos(angle) * distance,
            playerPos.y,
            playerPos.z + Math.sin(angle) * distance
        );
    }

    private Vec3d calculatePursuitPosition(Vec3d swPos, Vec3d playerPos) {
        Vec3d direction = playerPos.subtract(swPos).normalize();
        double distance = swPos.distanceTo(playerPos);
        return swPos.add(direction.multiply(distance * 0.6));
    }

    private Vec3d addNaturalVariation(Vec3d pos, Vec3d playerPos) {
        double variation = 1.5;
        Vec3d varied = new Vec3d(
            pos.x + (Math.random() - 0.5) * variation,
            pos.y,
            pos.z + (Math.random() - 0.5) * variation
        );

        Vec3d refPos = playerPos;
        double distToPlayer = varied.distanceTo(refPos);

        if (distToPlayer < MIN_DISTANCE) {
            Vec3d away = varied.subtract(refPos);
            if (away.length() > 0.001) {
                away = away.normalize();
                varied = varied.add(away.multiply(MIN_DISTANCE - distToPlayer + 1));
            }
        }
        if (distToPlayer > MAX_DISTANCE * 1.5) {
            Vec3d toward = refPos.subtract(varied);
            if (toward.length() > 0.001) {
                toward = toward.normalize();
                varied = varied.add(toward.multiply(distToPlayer - MAX_DISTANCE));
            }
        }

        return varied;
    }

    private void moveTowardTarget() {
        if (currentTarget == null) return;

        if (pathRecalcCooldown <= 0) {
            entity.getNavigation().startMovingTo(currentTarget.x, currentTarget.y, currentTarget.z, speedMultiplier);
            pathRecalcCooldown = 10;
        } else {
            pathRecalcCooldown--;
        }

        applyMimicBehaviour();

        if (currentMode != FollowMode.MIMIC) {
            if (Math.random() < 0.15) {
                entity.setSprinting(true);
            } else if (Math.random() < 0.05) {
                entity.setSprinting(false);
            }

            entity.setSneaking(Math.random() < 0.08);
        }

        if (entity.horizontalCollision && Math.random() < 0.3) {
            entity.doJump();
        }
    }

    private void applyLookDirection(PlayerEntity target) {
        if (Math.random() < 0.85) {
            ((SkinWalkerEntity.SkinWalkerLookControl) entity.getLookControl())
                .lookAt(target, 10);
        }
    }

    private void checkIfStuck() {
        if (lastPos != null) {
            double distanceMoved = entity.getPos().distanceTo(lastPos);
            if (distanceMoved < 0.1) {
                stuckCounter++;
                if (stuckCounter > 20) {
                    if (Math.random() < 0.5) {
                        entity.doJump();
                    } else {
                        currentMode = FollowMode.RANDOM;
                    }
                    stuckCounter = 0;
                }
            } else {
                stuckCounter = 0;
            }
        }
        lastPos = entity.getPos();
    }

    private PlayerEntity findTargetPlayer() {
        UUID targetUUID = component.getTargetPlayerUUID();
        if (targetUUID != null) {
            PlayerEntity target = entity.getWorld().getPlayerByUuid(targetUUID);
            if (target != null) return target;
        }

        List<? extends PlayerEntity> players = entity.getWorld().getPlayers();
        PlayerEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (PlayerEntity p : players) {
            if (p.isSpectator() || p.isCreative()) continue;
            double dist = p.distanceTo(entity);
            if (dist < closestDist && dist < 50) {
                closestDist = dist;
                closest = p;
            }
        }
        return closest;
    }

    public boolean isLearningActive() { return learningActive; }
    public FollowMode getCurrentMode() { return currentMode; }
    public float getSpeedMultiplier() { return speedMultiplier; }

    public void resetPathCooldown() { pathRecalcCooldown = 0; }
}
