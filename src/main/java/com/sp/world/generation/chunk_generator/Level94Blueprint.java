package com.sp.world.generation.chunk_generator;

import com.sp.world.generation.chunk_generator.Level94Layout.Facing;
import com.sp.world.generation.chunk_generator.Level94Layout.House;
import com.sp.world.generation.chunk_generator.Level94Layout.Quirk;
import com.sp.world.generation.chunk_generator.Level94Layout.StopSign;
import com.sp.world.generation.chunk_generator.Level94Layout.Tree;
import com.sp.world.generation.chunk_generator.Level94Layout.WaterTower;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import static com.sp.world.generation.chunk_generator.Level94Layout.*;

/**
 * Which blocks make up Level 94: the ground columns, roads, STOP signs, barriers, houses, water towers and trees.
 * <p>
 * Blocks are described by id and properties ({@link Piece}) instead of {@code BlockState}s, so the level can also
 * be built outside of a running game (previews, tests). {@link Level94ChunkGenerator} turns the pieces into block
 * states and writes them into chunks.
 */
public final class Level94Blueprint {

    /**
     * A block id with its block state properties in the "key=value,key=value" format, e.g.
     * {@code new Piece("minecraft:oak_door", "facing=south,half=lower")}.
     */
    public record Piece(String id, String properties) {
        public static Piece of(String id) {
            return new Piece(id, "");
        }

        public static Piece of(String id, String properties) {
            return new Piece(id, properties);
        }
    }

    /**
     * Receives the blocks. Writes outside of the area the sink covers must be ignored.
     */
    public interface Sink {
        boolean contains(int x, int z);

        void set(int x, int y, int z, Piece piece);
    }

    private static final String MOD = "spb-revamped:";

    public static final Piece AIR = Piece.of("minecraft:air");
    public static final Piece STONE = Piece.of("minecraft:stone");
    public static final Piece DIRT = Piece.of(MOD + "dirt");
    public static final Piece GRASS = Piece.of("minecraft:grass_block");
    public static final Piece ROAD = Piece.of(MOD + "road");
    public static final Piece CURB = Piece.of(MOD + "concrete9");
    private static final Piece SIGN_POLE = Piece.of(MOD + "sign_pole");
    private static final Piece DIRT_PATH = Piece.of("minecraft:dirt_path");

    private static final Piece[] HOUSE_WALLS = {
            Piece.of("minecraft:white_terracotta"),
            Piece.of("minecraft:yellow_terracotta"),
            Piece.of("minecraft:light_blue_terracotta"),
            Piece.of("minecraft:pink_terracotta"),
            Piece.of("minecraft:white_concrete")
    };
    private static final String[] ROOF_STAIRS = {"minecraft:dark_oak_stairs", "minecraft:brick_stairs", "minecraft:spruce_stairs"};
    private static final Piece[] ROOF_GABLE = {
            Piece.of("minecraft:dark_oak_planks"),
            Piece.of("minecraft:bricks"),
            Piece.of("minecraft:spruce_planks")
    };

    private Level94Blueprint() {}

    // ------------------------------------------------------------------------------------------------
    // Ground
    // ------------------------------------------------------------------------------------------------

    public static Piece surface(int x, int z) {
        double d = nearestRoadDistance(x, z);
        if (d <= ROAD_HALF_WIDTH) return ROAD;
        if (d <= CURB_EDGE) return CURB;
        return GRASS;
    }

    /**
     * Stone, a few layers of dirt and the surface block (grass, curb or road) from {@code minY} up.
     */
    public static void fillColumn(Sink sink, int x, int z, int minY) {
        int height = getTerrainHeight(x, z);
        for (int y = Math.max(0, minY); y < height; y++) {
            sink.set(x, y, z, y < height - 4 ? STONE : DIRT);
        }
        sink.set(x, height, z, surface(x, z));
    }

    // ------------------------------------------------------------------------------------------------
    // Features
    // ------------------------------------------------------------------------------------------------

    /**
     * Places every feature that overlaps the given area (inclusive bounds).
     */
    public static void placeFeatures(Sink sink, int minX, int minZ, int maxX, int maxZ) {
        // Water towers in the open fields and in neighborhood blocks
        for (int cx = Math.floorDiv(minX - 8, TOWER_CELL); cx <= Math.floorDiv(maxX + 8, TOWER_CELL); cx++) {
            for (int cz = Math.floorDiv(minZ - 8, TOWER_CELL); cz <= Math.floorDiv(maxZ + 8, TOWER_CELL); cz++) {
                WaterTower tower = WaterTower.at(cx, cz);
                if (tower != null && tower.intersects(minX, minZ, maxX, maxZ, 0)) {
                    placeWaterTower(sink, tower);
                }
            }
        }
        for (int i = Math.floorDiv(minX, GRID) - 1; i <= Math.floorDiv(maxX, GRID); i++) {
            for (int j = Math.floorDiv(minZ, GRID) - 1; j <= Math.floorDiv(maxZ, GRID); j++) {
                WaterTower tower = blockTower(i, j);
                if (tower != null && tower.intersects(minX, minZ, maxX, maxZ, 0)) {
                    placeWaterTower(sink, tower);
                }
            }
        }

        // Houses: lots in the neighborhood blocks and lonely houses along the main road
        forEachLot(minX, minZ, maxX, maxZ, house -> {
            if (house.intersects(minX, minZ, maxX, maxZ, House.YARD + 1)) {
                placeHouse(sink, house);
            }
        });
        for (int cell = Math.floorDiv(minX - 24, HOUSE_CELL); cell <= Math.floorDiv(maxX + 24, HOUSE_CELL); cell++) {
            for (int side = -1; side <= 1; side += 2) {
                House house = House.at(cell, side);
                if (house != null && house.intersects(minX, minZ, maxX, maxZ, House.YARD + 1)) {
                    placeHouse(sink, house);
                }
            }
        }

        // Barriers closing some dead ends (their STOP sign comes with the other signs)
        for (int i = Math.floorDiv(minX - 8, GRID); i <= Math.floorDiv(maxX + 8, GRID) + 1; i++) {
            for (int j = Math.floorDiv(minZ - 8, GRID); j <= Math.floorDiv(maxZ + 8, GRID) + 1; j++) {
                if (isNodeActive(i, j) && streetCount(i, j) == 1 && hasBarrier(i, j)) {
                    placeBarrier(sink, i, j);
                }
            }
        }

        // Trees
        for (int cx = Math.floorDiv(minX - 4, TREE_CELL); cx <= Math.floorDiv(maxX + 4, TREE_CELL); cx++) {
            for (int cz = Math.floorDiv(minZ - 4, TREE_CELL); cz <= Math.floorDiv(maxZ + 4, TREE_CELL); cz++) {
                Tree tree = Tree.at(cx, cz);
                if (tree != null && tree.x() + 4 >= minX && tree.x() - 4 <= maxX && tree.z() + 4 >= minZ && tree.z() - 4 <= maxZ) {
                    placeTree(sink, tree);
                }
            }
        }

        // Every STOP sign: main road, crossings, dead ends, front yards, the Stage and the exits
        forEachStopSign(minX, minZ, maxX, maxZ, sign -> placeStopSign(sink, sign));
    }

    /**
     * A STOP sign on its poles. The exits have a taller pole and a marker block on top.
     */
    private static void placeStopSign(Sink sink, StopSign sign) {
        if (!sink.contains(sign.x(), sign.z())) return;

        for (int k = 0; k < sign.poles(); k++) {
            sink.set(sign.x(), sign.poleBaseY() + k, sign.z(), SIGN_POLE);
        }
        sink.set(sign.x(), sign.signY(), sign.z(), Piece.of(MOD + "stop_sign", "facing=" + sign.facing().id()));
        if (sign.isExit()) {
            // A marker above the exits so attentive players can tell them apart.
            Piece marker = sign.special() % 3 == 1 ? Piece.of(MOD + "wall_text_99") : Piece.of(MOD + "red_metal_casing");
            sink.set(sign.x(), sign.signY() + 1, sign.z(), marker);
        }
    }

    /**
     * A fence across a dead end (the STOP sign in its middle is placed with the other signs).
     */
    private static void placeBarrier(Sink sink, int i, int j) {
        Facing across = deadEndStreet(i, j).clockwise();
        String connections = across.dx != 0 ? "east=true,west=true" : "north=true,south=true";
        for (int k = -3; k <= 3; k++) {
            if (k == 0) continue;
            sink.set(i * GRID + across.dx * k, BASE_Y + 1, j * GRID + across.dz * k, Piece.of("minecraft:birch_fence", connections));
        }
    }

    private static void placeHouse(Sink w, House house) {
        int floorY = house.floorY();
        boolean ghost = house.ghost();
        int halfWidth = House.HALF_WIDTH;
        int back = House.DEPTH - 1;

        Piece wall = HOUSE_WALLS[house.style() % HOUSE_WALLS.length];
        Piece log = Piece.of("minecraft:oak_log");
        Piece floor = Piece.of("minecraft:oak_planks");

        // Level the ground: the yard around the house is cut into or filled up to the floor.
        for (int u = -halfWidth - House.YARD; u <= halfWidth + House.YARD; u++) {
            for (int v = -House.YARD; v <= back + House.YARD; v++) {
                int x = house.worldX(u, v);
                int z = house.worldZ(u, v);
                if (!w.contains(x, z)) continue;

                int ground = getTerrainHeight(x, z);
                for (int y = Math.min(ground, floorY); y < floorY; y++) {
                    w.set(x, y, z, DIRT);
                }
                for (int y = floorY + 1; y <= Math.max(ground, floorY) + 1; y++) {
                    w.set(x, y, z, AIR);
                }
                boolean inside = Math.abs(u) <= halfWidth && v >= 0 && v <= back;
                w.set(x, floorY, z, inside ? floor : GRASS);
            }
        }

        // Walls
        for (int u = -halfWidth; u <= halfWidth; u++) {
            for (int v = 0; v <= back; v++) {
                boolean side = Math.abs(u) == halfWidth;
                boolean frontOrBack = v == 0 || v == back;
                if (!side && !frontOrBack) continue;
                if (ghost && v != 0) continue;

                for (int y = floorY + 1; y <= floorY + 4; y++) {
                    set(w, house, u, y, v, side && frontOrBack ? log : wall);
                }
            }
        }

        Quirk quirk = house.quirk();
        boolean backwards = quirk == Quirk.BACKWARDS;
        int doorV = backwards ? back : 0;      // the wall with the door
        int otherV = backwards ? 0 : back;     // the wall across from it

        // Door: towards the street, or towards the back yard for the houses turned around
        String door = "facing=" + (backwards ? house.facing() : house.inward()).id() + ",hinge=left,open=false,half=";
        set(w, house, 0, floorY + 1, doorV, Piece.of("minecraft:oak_door", door + "lower"));
        set(w, house, 0, floorY + 2, doorV, Piece.of("minecraft:oak_door", door + "upper"));
        if (quirk == Quirk.TALL_DOOR) {
            // A doorway one block too tall, dark all the way up
            set(w, house, 0, floorY + 3, doorV, Piece.of("minecraft:black_concrete"));
        }

        // Windows
        String glass = switch (quirk) {
            case BLACK_WINDOWS -> "minecraft:black_stained_glass_pane";
            case LIT -> "minecraft:yellow_stained_glass_pane";
            default -> "minecraft:glass_pane";
        };
        boolean acrossIsX = house.across().dx != 0;
        Piece paneAcross = Piece.of(glass, acrossIsX ? "east=true,west=true" : "north=true,south=true");
        Piece paneInward = Piece.of(glass, acrossIsX ? "north=true,south=true" : "east=true,west=true");

        if (quirk != Quirk.BRICKED) {
            for (int y = floorY + 2; y <= floorY + 3; y++) {
                // The houses turned around show a blank wall to the street.
                if (!backwards) {
                    set(w, house, -2, y, 0, paneAcross);
                    set(w, house, 2, y, 0, paneAcross);
                }
                if (!ghost) {
                    set(w, house, 0, y, otherV, paneAcross);
                    if (backwards) {
                        set(w, house, -2, y, back, paneAcross);
                        set(w, house, 2, y, back, paneAcross);
                    }
                    set(w, house, -halfWidth, y, back / 2, paneInward);
                    set(w, house, halfWidth, y, back / 2, paneInward);
                }
            }
        }

        // Path from the door to the street (the houses turned around have none)
        for (int k = 1; k < 32 && !backwards; k++) {
            int x = house.worldX(0, -k);
            int z = house.worldZ(0, -k);
            if (nearestRoadDistance(x, z) <= CURB_EDGE) break;
            if (w.contains(x, z)) {
                w.set(x, k <= House.YARD ? floorY : getTerrainHeight(x, z), z, DIRT_PATH);
            }
        }

        if (ghost) {
            // Only a facade and an empty floor remain: an eerie half-built house.
            return;
        }

        // Interior
        set(w, house, -2, floorY + 1, backwards ? 1 : back - 1, Piece.of(MOD + "wooden_crate"));
        set(w, house, 2, floorY + 1, backwards ? 1 : back - 1, Piece.of("minecraft:lantern"));
        if (quirk == Quirk.LIT) {
            set(w, house, 0, floorY + 4, back / 2, Piece.of("minecraft:glowstone"));
        }
        if (quirk == Quirk.BLACK_WINDOWS) {
            // Nothing inside: the rooms are filled with darkness.
            for (int u = -halfWidth + 1; u < halfWidth; u++) {
                for (int v = 1; v < back; v++) {
                    for (int y = floorY + 1; y <= floorY + 4; y++) {
                        set(w, house, u, y, v, Piece.of("minecraft:black_concrete"));
                    }
                }
            }
        }

        // Gable roof, ridge parallel to the front wall
        String stairs = ROOF_STAIRS[house.roof() % ROOF_STAIRS.length];
        Piece frontStairs = Piece.of(stairs, "facing=" + house.inward().id() + ",half=bottom");
        Piece backStairs = Piece.of(stairs, "facing=" + house.facing().id() + ",half=bottom");
        Piece gable = ROOF_GABLE[house.roof() % ROOF_GABLE.length];

        for (int k = 0; -1 + k < back + 1 - k; k++) {
            int y = floorY + 5 + k;
            int rowFront = -1 + k;
            int rowBack = back + 1 - k;
            for (int u = -halfWidth - 1; u <= halfWidth + 1; u++) {
                set(w, house, u, y, rowFront, frontStairs);
                set(w, house, u, y, rowBack, backStairs);
            }
            // Fill the gable ends, and the ceiling on the first layer
            for (int v = rowFront + 1; v < rowBack; v++) {
                set(w, house, -halfWidth, y, v, gable);
                set(w, house, halfWidth, y, v, gable);
                if (k == 0) {
                    for (int u = -halfWidth + 1; u < halfWidth; u++) {
                        set(w, house, u, y, v, gable);
                    }
                }
            }
        }
    }

    /**
     * Sets a block at the house's local position (u across the front wall, v towards the back).
     */
    private static void set(Sink w, House house, int u, int y, int v, Piece piece) {
        w.set(house.worldX(u, v), y, house.worldZ(u, v), piece);
    }

    private static void placeWaterTower(Sink w, WaterTower tower) {
        int x = tower.x();
        int z = tower.z();
        int legOffset = WaterTower.LEG;

        int base = 0;
        for (int dx = -legOffset; dx <= legOffset; dx += 2 * legOffset) {
            for (int dz = -legOffset; dz <= legOffset; dz += 2 * legOffset) {
                base = Math.max(base, getTerrainHeight(x + dx, z + dz));
            }
        }
        int top = base + WaterTower.LEG_HEIGHT;
        int brace = base + WaterTower.LEG_HEIGHT / 2;

        Piece leg = Piece.of("minecraft:andesite_wall", "up=true");
        Piece barX = Piece.of("minecraft:iron_bars", "east=true,west=true");
        Piece barZ = Piece.of("minecraft:iron_bars", "north=true,south=true");

        // Legs, from the ground under each leg up to the platform
        for (int dx = -legOffset; dx <= legOffset; dx += 2 * legOffset) {
            for (int dz = -legOffset; dz <= legOffset; dz += 2 * legOffset) {
                int lx = x + dx;
                int lz = z + dz;
                for (int y = getTerrainHeight(lx, lz) + 1; y < top; y++) {
                    w.set(lx, y, lz, leg);
                }
            }
        }

        // Cross braces half way up
        for (int k = -legOffset + 1; k <= legOffset - 1; k++) {
            w.set(x + k, brace, z - legOffset, barX);
            w.set(x + k, brace, z + legOffset, barX);
            w.set(x - legOffset, brace, z + k, barZ);
            w.set(x + legOffset, brace, z + k, barZ);
        }

        Piece platform = Piece.of("minecraft:spruce_planks");
        Piece tank = Piece.of("minecraft:white_concrete");
        Piece band = Piece.of("minecraft:light_gray_concrete");
        int radius = WaterTower.RADIUS;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                int bx = x + dx;
                int bz = z + dz;

                if (r <= 4.4) {
                    w.set(bx, top, bz, platform);
                }
                if (r <= WaterTower.TANK_RADIUS) {
                    if (r > WaterTower.TANK_RADIUS - 1.0) {
                        for (int y = top + 1; y <= top + 6; y++) {
                            w.set(bx, y, bz, y == top + 4 ? band : tank);
                        }
                    }
                    w.set(bx, top + 7, bz, tank);
                }
                // Conical roof
                if (r <= 2.6) w.set(bx, top + 8, bz, band);
                if (r <= 1.5) w.set(bx, top + 9, bz, band);
            }
        }
        w.set(x, top + 10, z, Piece.of("minecraft:lightning_rod"));
    }

    private static void placeTree(Sink w, Tree tree) {
        int x = tree.x();
        int z = tree.z();
        int ground = getTerrainHeight(x, z);
        String wood = tree.birch() ? "birch" : "oak";
        Piece log = Piece.of("minecraft:" + wood + "_log");
        Piece leaves = Piece.of("minecraft:" + wood + "_leaves", "persistent=true");

        int crown = ground + tree.height();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    double r = Math.sqrt(dx * dx + dy * dy * 1.4 + dz * dz);
                    if (r <= 2.8) {
                        w.set(x + dx, crown + dy, z + dz, leaves);
                    }
                }
            }
        }
        for (int y = ground + 1; y <= crown; y++) {
            w.set(x, y, z, log);
        }
    }
}
