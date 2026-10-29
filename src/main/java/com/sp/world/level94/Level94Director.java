package com.sp.world.level94;

import com.sp.SPBRevamped;
import com.sp.block.custom.StopSignBlock;
import com.sp.entity.custom.NeighborEntity;
import com.sp.init.BackroomsLevels;
import com.sp.init.ModBlocks;
import com.sp.init.ModEntities;
import com.sp.util.TickScheduler;
import com.sp.world.generation.chunk_generator.Level94Layout;
import com.sp.world.generation.chunk_generator.Level94Layout.Facing;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the things in Level 94 that are not part of the terrain, for every player in the level:
 * <ul>
 *     <li>the Neighbor ({@link NeighborEntity}): far away and still in the Déjà vu zone, following you from the
 *     Wrong zone on;</li>
 *     <li>the phrases that show up above the hotbar, repeating themselves a little differently every time;</li>
 *     <li>the STOP signs that turn to look at you while you are not looking at them (Wrong and Stage zones);</li>
 *     <li>the loops: walking too far from the main road, or along some streets of the Wrong zone, puts you back.</li>
 * </ul>
 */
public final class Level94Director {
    private static final String PHRASE = "spb-revamped.level94.phrase.";
    private static final String NEIGHBOR = "spb-revamped.level94.neighbor.";

    // Each zone repeats its phrases in this order; repeated keys are on purpose.
    private static final String[][] ZONE_PHRASES = {
            {"welcome", "welcome", "everyone_home", "lawn", "welcome"},
            {"same_house", "same_house", "your_house", "been_here", "been_here", "always_here", "welcome", "welcome_name"},
            {"respect_signs", "respect_signs", "signs_respect_you", "nobody_moved", "close_door", "door_open", "closed_for_you",
                    "neighbor_waves", "wave_back", "why_no_wave"},
            {"stop", "dont_stop", "stop", "everyone_stops", "almost_home", "you_are_home", "stay"}
    };
    private static final String[] NEIGHBOR_LINES = {"hello", "hello", "hello_hello", "waiting", "waiting_forever", "leaving", "nobody_leaves", "house_ready"};

    private static final Map<UUID, State> STATES = new HashMap<>();

    private Level94Director() {}

    private static final class State {
        UUID neighbor;
        int neighborCooldown = 400;
        int phraseTimer = 200;
        int phraseStep;
        int neighborLine;
        int neighborTalkCooldown = 300;
        int lastZone = -1;
        int loopCooldown;
        double lastX = Double.NaN;
    }

    public static void tick(ServerWorld world) {
        if (world.getRegistryKey() != BackroomsLevels.LEVEL94_WORLD_KEY) return;

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) continue;
            tickPlayer(world, player, STATES.computeIfAbsent(player.getUuid(), uuid -> new State()));
        }

        if (world.getTime() % 200 == 0) {
            STATES.keySet().removeIf(uuid -> world.getPlayerByUuid(uuid) == null);
        }
    }

    private static void tickPlayer(ServerWorld world, ServerPlayerEntity player, State state) {
        int x = player.getBlockX();
        int zone = Level94Layout.zone(x);

        if (zone != state.lastZone) {
            state.lastZone = zone;
            state.phraseTimer = Math.min(state.phraseTimer, 80);
        }

        tickPhrases(player, state, zone);
        tickNeighbor(world, player, state, zone);
        if (zone >= 3 && world.getTime() % 10 == 0) {
            turnSigns(world, player);
        }
        tickLoops(player, state, zone);
    }

    // ------------------------------------------------------------------------------------------------
    // Phrases
    // ------------------------------------------------------------------------------------------------

    private static void tickPhrases(ServerPlayerEntity player, State state, int zone) {
        if (--state.phraseTimer > 0) return;

        String[] phrases = ZONE_PHRASES[zone - 1];
        String key = phrases[state.phraseStep % phrases.length];
        state.phraseStep++;
        sendPhrase(player, PHRASE + key);

        // The deeper you go, the more often the place talks to you.
        int base = switch (zone) {
            case 1 -> 1400;
            case 2 -> 800;
            case 3 -> 500;
            default -> 320;
        };
        state.phraseTimer = base + player.getRandom().nextInt(base / 2);
    }

    public static void sendPhrase(ServerPlayerEntity player, String key) {
        player.sendMessage(Text.translatable(key, player.getName().getString()).formatted(Formatting.GRAY, Formatting.ITALIC), true);
    }

    private static void neighborSays(ServerPlayerEntity player, String key) {
        player.sendMessage(Text.literal("<").append(Text.translatable("entity.spb-revamped.neighbor")).append("> ")
                .append(Text.translatable(key, player.getName().getString())).formatted(Formatting.GRAY));
    }

    // ------------------------------------------------------------------------------------------------
    // The Neighbor
    // ------------------------------------------------------------------------------------------------

    private static void tickNeighbor(ServerWorld world, ServerPlayerEntity player, State state, int zone) {
        Entity existing = state.neighbor == null ? null : world.getEntity(state.neighbor);
        if (existing != null && !existing.isRemoved()) {
            if (existing instanceof NeighborEntity neighbor && neighbor.getMode() == NeighborEntity.Mode.FOLLOWER) {
                // Now and then he says something, always from somewhere you are not looking.
                if (--state.neighborTalkCooldown <= 0 && neighbor.distanceTo(player) < 24
                        && !NeighborEntity.isSeenBy(player, neighbor.getPos().add(0, 1.6, 0))) {
                    neighborSays(player, NEIGHBOR + NEIGHBOR_LINES[state.neighborLine++ % NEIGHBOR_LINES.length]);
                    state.neighborTalkCooldown = 500 + player.getRandom().nextInt(400);
                }
            }
            return;
        }

        state.neighbor = null;
        if (zone < 2 || --state.neighborCooldown > 0) return;

        NeighborEntity.Mode mode = zone == 2 ? NeighborEntity.Mode.WATCHER : NeighborEntity.Mode.FOLLOWER;
        NeighborEntity neighbor = ModEntities.NEIGHBOR_ENTITY.create(world);
        if (neighbor == null) return;

        Vec3d spot = findSpawnSpot(world, player, mode);
        if (spot == null) {
            state.neighborCooldown = 40;
            return;
        }

        neighbor.setup(player, mode);
        neighbor.refreshPositionAndAngles(spot.x, spot.y, spot.z, 0, 0);
        world.spawnEntity(neighbor);
        state.neighbor = neighbor.getUuid();
        state.neighborCooldown = mode == NeighborEntity.Mode.WATCHER ? 900 + player.getRandom().nextInt(900) : 300;
    }

    private static Vec3d findSpawnSpot(ServerWorld world, ServerPlayerEntity player, NeighborEntity.Mode mode) {
        Vec3d look = player.getRotationVector().multiply(1, 0, 1).normalize();
        for (int attempt = 0; attempt < 10; attempt++) {
            double distance;
            double angle;
            if (mode == NeighborEntity.Mode.WATCHER) {
                // Far ahead, somewhere you will turn your head towards
                distance = 40 + player.getRandom().nextInt(20);
                angle = Math.toRadians(40 + player.getRandom().nextInt(50)) * (player.getRandom().nextBoolean() ? 1 : -1);
            } else {
                distance = 28 + player.getRandom().nextInt(16);
                angle = Math.toRadians(100 + player.getRandom().nextInt(160));
            }
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vec3d dir = new Vec3d(look.x * cos - look.z * sin, 0, look.x * sin + look.z * cos);
            Vec3d spot = NeighborEntity.findSpot(world, player, player.getPos().add(dir.multiply(distance)), 14);
            if (spot != null) return spot;
        }
        return null;
    }

    /**
     * The Neighbor reached the player: darkness, and then you are back at the start of the zone.
     */
    public static void onCaught(ServerPlayerEntity player, NeighborEntity neighbor) {
        State state = STATES.computeIfAbsent(player.getUuid(), uuid -> new State());
        state.neighbor = null;
        state.neighborCooldown = 600;

        SPBRevamped.sendBlackScreenPacket(player, 70, true, false);
        neighborSays(player, NEIGHBOR + (player.getRandom().nextBoolean() ? "late" : "welcome_home"));

        int zone = Level94Layout.zone(player.getBlockX());
        int x = Level94Layout.zoneStartX(zone, player.getBlockX());
        int startX = x + (x < 0 ? -10 : 10);
        double z = Level94Layout.roadCenterZ(startX + 0.5);
        TickScheduler.SERVER.schedule(20, () -> {
            if (player.isRemoved() || player.getWorld().getRegistryKey() != BackroomsLevels.LEVEL94_WORLD_KEY) return;
            player.networkHandler.requestTeleport(startX + 0.5, Level94Layout.BASE_Y + 1, z, player.getYaw(), player.getPitch());
            player.fallDistance = 0;
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Signs that look at you
    // ------------------------------------------------------------------------------------------------

    private static void turnSigns(ServerWorld world, ServerPlayerEntity player) {
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        int radius = 28;

        Level94Layout.forEachStopSign(px - radius, pz - radius, px + radius, pz + radius, sign -> {
            // The exits are the only signs that never look at you.
            if (sign.isExit()) return;

            BlockPos pos = sign.signPos();
            if (!world.isChunkLoaded(pos)) return;
            BlockState state = world.getBlockState(pos);
            if (!state.isOf(ModBlocks.STOP_SIGN)) return;

            Facing towards = Facing.towards(player.getX() - (pos.getX() + 0.5), player.getZ() - (pos.getZ() + 0.5));
            Direction direction = Direction.byName(towards.id());
            if (direction == null || state.get(StopSignBlock.FACING) == direction) return;

            // Only while you are not looking, and slowly: one sign at a time.
            if (NeighborEntity.isSeenBy(player, Vec3d.ofCenter(pos))) return;
            if (player.getRandom().nextInt(3) != 0) return;
            world.setBlockState(pos, state.with(StopSignBlock.FACING, direction), Block.NOTIFY_LISTENERS);
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Loops
    // ------------------------------------------------------------------------------------------------

    private static void tickLoops(ServerPlayerEntity player, State state, int zone) {
        double x = player.getX();
        double z = player.getZ();
        double previousX = state.lastX;
        state.lastX = x;
        if (state.loopCooldown > 0) {
            state.loopCooldown--;
            return;
        }

        double fromRoad = Level94Layout.roadDistance(MathHelper.floor(x), MathHelper.floor(z));

        // Too far from the main road: the fields fold back towards it.
        if (Math.abs(fromRoad) > Level94Layout.LATERAL_LIMIT) {
            double newZ = Level94Layout.roadCenterZ(x) + Math.signum(fromRoad) * Level94Layout.LATERAL_RETURN;
            blinkTeleport(player, state, x, newZ, "been_here");
            return;
        }

        // In the Wrong zone some avenues, crossed through the neighborhoods, take you two neighborhoods back.
        if (zone == 3 && !Double.isNaN(previousX) && Math.abs(fromRoad) > 40 && Math.abs(x) > Math.abs(previousX)) {
            int before = Math.floorDiv(MathHelper.floor(previousX), Level94Layout.GRID);
            int after = Math.floorDiv(MathHelper.floor(x), Level94Layout.GRID);
            if (before != after) {
                int avenue = Math.max(before, after);
                int regionRow = Math.floorDiv(Math.floorDiv(MathHelper.floor(z), Level94Layout.GRID), Level94Layout.REGION_H);
                if (Level94Layout.isLoopingAvenue(avenue, regionRow)) {
                    double back = 2.0 * Level94Layout.REGION_W * Level94Layout.GRID * Math.signum(x);
                    blinkTeleport(player, state, x - back, z, "always_here");
                }
            }
        }
    }

    private static void blinkTeleport(ServerPlayerEntity player, State state, double x, double z, String phrase) {
        state.loopCooldown = 100;
        state.lastX = Double.NaN;
        SPBRevamped.sendBlackScreenPacket(player, 8, false, false);
        TickScheduler.SERVER.schedule(3, () -> {
            if (player.isRemoved()) return;
            int y = Level94Layout.getTerrainHeight(MathHelper.floor(x), MathHelper.floor(z)) + 1;
            player.networkHandler.requestTeleport(x, y, z, player.getYaw(), player.getPitch());
            player.fallDistance = 0;
            sendPhrase(player, PHRASE + phrase);
        });
    }
}
