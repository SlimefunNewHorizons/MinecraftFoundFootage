package com.sp.entity.custom;

import com.sp.world.generation.chunk_generator.Level94Layout;
import com.sp.world.level94.Level94Director;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Level 94's "Vecino" (the Neighbor): a tall, too thin man in a white shirt and a tie, with a smile that is too
 * wide. He never attacks. He is just always there.
 * <ul>
 *     <li>Only the player he belongs to can see him.</li>
 *     <li>He always faces you. When you stare at him he waves, once.</li>
 *     <li>He only moves when nobody is looking: look away and he is standing somewhere else, in a yard or a
 *     doorway. While you walk he shows up ahead of you, on the edge of your sight; when you stand still he comes
 *     closer.</li>
 *     <li>If he reaches you, everything goes black and you are back at the start of the zone.</li>
 * </ul>
 * A {@link Mode#WATCHER} (Déjà vu zone) only stands far away and is gone the moment you look away from him.
 */
public class NeighborEntity extends MobEntity {
    public enum Mode { WATCHER, FOLLOWER }

    private static final TrackedData<Boolean> WAVING = DataTracker.registerData(NeighborEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> HEAD_TILT = DataTracker.registerData(NeighborEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public static final double CATCH_DISTANCE = 1.6;
    private static final double SEEN_DOT = 0.8;     // cos(~37 degrees): inside this cone he counts as watched

    private UUID target;
    private Mode mode = Mode.WATCHER;
    private int seenTicks;
    private int unseenTicks;
    private int lifeTicks;
    private boolean wasSeen;
    private boolean waved;
    private Vec3d lastTargetPos;
    private double targetSpeed;
    private Vec3d targetHeading = Vec3d.ZERO;

    public NeighborEntity(EntityType<? extends MobEntity> entityType, World world) {
        super(entityType, world);
        this.setInvulnerable(true);
        this.setSilent(true);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createNeighborAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 100)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(WAVING, false);
        this.dataTracker.startTracking(HEAD_TILT, 14);
    }

    public void setup(PlayerEntity target, Mode mode) {
        this.target = target.getUuid();
        this.mode = mode;
        this.lastTargetPos = target.getPos();
        this.dataTracker.set(HEAD_TILT, randomTilt());
    }

    public UUID getTargetPlayer() {
        return this.target;
    }

    public Mode getMode() {
        return this.mode;
    }

    public boolean isWaving() {
        return this.dataTracker.get(WAVING);
    }

    /**
     * Head tilt in degrees, sideways. It changes a little every time he moves.
     */
    public int getHeadTilt() {
        return this.dataTracker.get(HEAD_TILT);
    }

    private int randomTilt() {
        int tilt = 9 + this.random.nextInt(16);
        return this.random.nextBoolean() ? tilt : -tilt;
    }

    @Override
    public void tick() {
        super.tick();
        //*NoAI (e.g. /preview): just stand there to be looked at
        if (this.getWorld().isClient() || this.isAiDisabled()) {
            return;
        }

        this.lifeTicks++;
        ServerPlayerEntity player = this.target == null || this.getServer() == null ? null
                : this.getServer().getPlayerManager().getPlayer(this.target);
        if (player == null || player.getWorld() != this.getWorld() || player.isSpectator()) {
            this.discard();
            return;
        }

        this.faceTowards(player.getEyePos());

        Vec3d pos = player.getPos();
        if (this.lastTargetPos != null) {
            Vec3d move = pos.subtract(this.lastTargetPos).multiply(1, 0, 1);
            this.targetSpeed = this.targetSpeed * 0.8 + move.length() * 0.2;
            this.targetHeading = this.targetHeading.multiply(0.8).add(move.multiply(0.2));
        }
        this.lastTargetPos = pos;

        boolean seen = isSeenBy(player, this.getPos().add(0, 1.6, 0));
        if (seen) {
            this.seenTicks++;
            this.unseenTicks = 0;
            this.wasSeen = true;
        } else {
            this.unseenTicks++;
            this.seenTicks = 0;
        }

        // When you stare at him long enough, he waves. Once.
        boolean waving = seen && !this.waved && this.seenTicks > 15 && this.seenTicks < 80;
        if (!waving && this.isWaving()) this.waved = true;
        this.dataTracker.set(WAVING, waving);

        double distance = this.distanceTo(player);

        if (this.mode == Mode.WATCHER) {
            // Far away and still. Look away and he was never there.
            if ((this.wasSeen && this.unseenTicks > 8) || (!seen && distance < 22) || this.lifeTicks > 1800) {
                this.discard();
            }
            return;
        }

        if (Level94Layout.zone(player.getBlockX()) < 3 && !seen) {
            this.discard();
            return;
        }

        if (distance < CATCH_DISTANCE) {
            Level94Director.onCaught(player, this);
            this.discard();
            return;
        }

        // He only ever moves while nobody is looking.
        if (!seen && this.unseenTicks >= repositionDelay(player)) {
            this.unseenTicks = 0;
            this.reposition(player, distance);
        }
    }

    private int repositionDelay(ServerPlayerEntity player) {
        int delay = Level94Layout.zone(player.getBlockX()) >= 4 ? 25 : 45;
        // Standing still makes him bolder.
        return this.targetSpeed < 0.03 ? delay / 2 : delay;
    }

    private void reposition(ServerPlayerEntity player, double distance) {
        boolean moving = this.targetSpeed > 0.06;
        Vec3d look = player.getRotationVector().multiply(1, 0, 1).normalize();
        Vec3d heading = moving ? this.targetHeading : Vec3d.ZERO;
        if (heading.lengthSquared() < 1.0E-4) heading = look;
        heading = heading.normalize();

        double newDistance = moving ? MathHelper.clamp(distance, 18, 40) : Math.max(CATCH_DISTANCE + 0.4, distance * 0.62);

        for (int attempt = 0; attempt < 12; attempt++) {
            double angle;
            if (moving) {
                // Ahead of you, on the edge of your sight
                angle = (this.random.nextBoolean() ? 1 : -1) * Math.toRadians(55 + this.random.nextInt(30));
            } else {
                // Somewhere you are not looking
                angle = Math.toRadians(110 + this.random.nextInt(140));
            }
            Vec3d dir = rotateY(moving ? heading : look, angle);
            Vec3d candidate = player.getPos().add(dir.multiply(newDistance));
            Vec3d spot = findSpot(this.getWorld(), player, candidate, 10);
            if (spot != null) {
                this.refreshPositionAndAngles(spot.x, spot.y, spot.z, this.getYaw(), 0);
                this.dataTracker.set(HEAD_TILT, randomTilt());
                this.waved = false;
                return;
            }
        }
    }

    /**
     * A place to stand near the candidate position that the player cannot see: preferably right in front of a
     * house door (or in a yard), otherwise anywhere on the ground. Null if nothing fits.
     *
     * @param doorRadius how far from the candidate a door may be
     */
    public static Vec3d findSpot(World world, PlayerEntity player, Vec3d candidate, int doorRadius) {
        Vec3d[] best = {null};
        double[] bestDistance = {doorRadius * doorRadius};
        int cx = MathHelper.floor(candidate.x);
        int cz = MathHelper.floor(candidate.z);

        Level94Layout.forEachLot(cx - doorRadius, cz - doorRadius, cx + doorRadius, cz + doorRadius, house -> {
            if (house.ghost()) return;
            // Standing on the doorstep, or in the middle of the front yard
            int v = house.quirk() == Level94Layout.Quirk.BACKWARDS ? Level94Layout.House.DEPTH : -1;
            Vec3d door = new Vec3d(house.worldX(0, v) + 0.5, house.floorY() + 1, house.worldZ(0, v) + 0.5);
            double d = door.squaredDistanceTo(candidate.x, door.y, candidate.z);
            if (d < bestDistance[0] && standable(world, door) && !isSeenBy(player, door.add(0, 1.6, 0))) {
                bestDistance[0] = d;
                best[0] = door;
            }
        });
        if (best[0] != null) return best[0];

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int startY = MathHelper.floor(player.getY()) + 8;
        for (int y = startY; y > startY - 18; y--) {
            mutable.set(cx, y, cz);
            Vec3d feet = new Vec3d(cx + 0.5, y + 1, cz + 0.5);
            if (world.getBlockState(mutable).isSolidBlock(world, mutable) && standable(world, feet)) {
                return isSeenBy(player, feet.add(0, 1.6, 0)) ? null : feet;
            }
        }
        return null;
    }

    private static boolean standable(World world, Vec3d feet) {
        BlockPos pos = BlockPos.ofFloored(feet);
        if (!world.isChunkLoaded(pos)) return false;
        for (int k = 0; k < 3; k++) {
            if (!world.getBlockState(pos.up(k)).getCollisionShape(world, pos.up(k)).isEmpty()) return false;
        }
        BlockPos below = pos.down();
        return !world.getBlockState(below).getCollisionShape(world, below).isEmpty();
    }

    /**
     * If the player is looking at the given point and nothing is in the way.
     */
    public static boolean isSeenBy(PlayerEntity player, Vec3d point) {
        Vec3d eyes = player.getEyePos();
        Vec3d toPoint = point.subtract(eyes);
        double length = toPoint.length();
        if (length < 1.0E-3) return true;
        double dot = player.getRotationVec(1.0F).dotProduct(toPoint.multiply(1.0 / length));
        if (dot < SEEN_DOT) return false;

        return player.getWorld().raycast(new net.minecraft.world.RaycastContext(eyes, point,
                net.minecraft.world.RaycastContext.ShapeType.VISUAL, net.minecraft.world.RaycastContext.FluidHandling.NONE, player))
                .getType() == net.minecraft.util.hit.HitResult.Type.MISS;
    }

    private static Vec3d rotateY(Vec3d v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3d(v.x * cos - v.z * sin, 0, v.x * sin + v.z * cos);
    }

    private void faceTowards(Vec3d point) {
        double dx = point.x - this.getX();
        double dz = point.z - this.getZ();
        float yaw = (float) (MathHelper.atan2(dz, dx) * (180F / Math.PI)) - 90.0F;
        this.setYaw(yaw);
        this.setBodyYaw(yaw);
        this.setHeadYaw(yaw);
        double dy = point.y - this.getEyeY();
        this.setPitch((float) -(MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180F / Math.PI)));
    }

    // Only the player he belongs to ever sees him. Without one (spawned by /preview) everybody does.
    @Override
    public boolean canBeSpectated(ServerPlayerEntity spectator) {
        return this.target == null || spectator.getUuid().equals(this.target);
    }

    // /kill goes through damage(), which he ignores: remove him directly so previews can be cleaned up.
    @Override
    public void kill() {
        this.remove(RemovalReason.KILLED);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushAway(net.minecraft.entity.Entity entity) {
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean canTakeDamage() {
        return false;
    }

    @Override
    public void checkDespawn() {
    }

    // He is never saved with the world: he belongs to the moment.
    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
    }
}
