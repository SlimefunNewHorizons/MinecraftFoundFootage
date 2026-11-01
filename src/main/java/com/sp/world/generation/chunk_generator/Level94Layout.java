package com.sp.world.generation.chunk_generator;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Where everything in Level 94 is.
 * <p>
 * A winding main road crosses the whole level. The land is divided into regions of {@link #REGION_W} x
 * {@link #REGION_H} grid crossings ({@link #GRID} blocks apart). <b>Avenues</b> run along the edges of every region,
 * crossing each other (and the main road) at 4-way intersections. Inside a region there is either a
 * <b>neighborhood</b> (an almost complete grid of streets lined with houses, entered from the avenues through a
 * few T crossings and separated from them by a green belt) or open fields with rolling hills.
 * <p>
 * The further you walk along the main road, the less the place makes sense. See {@link #zone(int)}:
 * <ol>
 *     <li>Normal: complete, correct neighborhoods.</li>
 *     <li>Déjà vu: every neighborhood is the same one, each copy with a single thing wrong.</li>
 *     <li>Wrong: dead ends, houses turned around, giant doors, black windows, signs that look at you.</li>
 *     <li>Stage: only facades are left, STOP signs everywhere. The exits are here.</li>
 * </ol>
 * Away from the main road the neighborhoods thin out into empty fields.
 * <p>
 * Everything is a deterministic function of the world coordinates (no world seed), so the level always looks the
 * same and any code (chunk generation, level transitions, the Neighbor, previews, tests) can ask where things are
 * without generating chunks. Which blocks make up each feature is decided by {@link Level94Blueprint}.
 */
public final class Level94Layout {
    // Terrain
    public static final int BASE_Y = 31;                       // Road surface level
    public static final double ROAD_HALF_WIDTH = 3.5;          // |d| <= 3.5 is asphalt (7 blocks)
    public static final double CURB_EDGE = 4.5;                // |d| <= 4.5 is the curb
    public static final double FLAT_EDGE = 6.5;                // Flat grass verge, hills start after this
    private static final double HILLS_FULL = 60.0;             // Hills reach full height at this distance

    // Zones: distance along the main road (|x|) where each zone starts
    public static final int[] ZONE_START = {0, 400, 1000, 2000};
    // Walking further than this from the main road takes you back towards it
    public static final int LATERAL_LIMIT = 1100;
    public static final int LATERAL_RETURN = 350;

    // The exits: special STOP signs in the Stage zone, on both ends of the road. Each world moves them a little and
    // may put them on the other side of the road (see specialStopX / specialStopSide).
    public static final int[] SPECIAL_STOP_X = {2200, 2500, 2800, -2200, -2500, -2800};
    public static final int[] SPECIAL_STOP_SIDE = {1, -1, 1, -1, 1, -1};
    private static final int SPECIAL_STOP_SHIFT = 100;

    // Neighborhoods
    public static final int GRID = 36;                         // Distance between parallel streets
    public static final int REGION_W = 6;                      // Crossings per neighborhood along x
    public static final int REGION_H = 5;                      // Crossings per neighborhood along z
    private static final double ROAD_CLEARANCE = 22.0;         // No street crossing this close to the main road
    private static final double BLOCK_TOWER_CHANCE = 0.06;
    private static final int DEJA_VU_TEMPLATE = 7919;          // Every Déjà vu neighborhood is built from this one

    // Other features
    public static final int STOP_CELL = 72;
    public static final int HOUSE_CELL = 104;
    public static final int TOWER_CELL = 320;
    public static final int TREE_CELL = 30;
    public static final int FIELD_SIGN_CELL = 16;

    public static final int HOUSE_STYLES = 5;
    public static final int ROOF_STYLES = 3;

    private static final long SALT_REGION = 0x2E610L;
    private static final long SALT_GRID = 0x3A2EL;
    private static final long SALT_ENTRANCE = 0xB0DL;
    private static final long SALT_LOT = 0x107L;
    private static final long SALT_QUIRK = 0x9017L;
    private static final long SALT_DEAD_END = 0xDEADL;
    private static final long SALT_STOP = 0x5170A11L;
    private static final long SALT_FIELD_SIGN = 0xF51L;
    private static final long SALT_HOUSE = 0x40053L;
    private static final long SALT_TOWER = 0x70E7L;
    private static final long SALT_TREE = 0x7EEL;
    private static final long SALT_LOOP = 0x100BL;
    private static final long SALT_EXIT = 0xE817L;

    // ------------------------------------------------------------------------------------------------
    // World seed
    // ------------------------------------------------------------------------------------------------

    // Mixed into every hash, so each world gets its own neighborhoods, road and exit spots. 0 (seed 0, and the
    // tests) is the original layout. Set on the server when it starts and on the client from the synced seed.
    private static volatile long worldSalt;
    private static volatile double roadPhaseA;
    private static volatile double roadPhaseB;

    /**
     * Lays the level out for the world with this seed. Cheap when the seed did not change.
     */
    public static void useSeed(long seed) {
        long salt = seed == 0 ? 0 : mix(seed) | 1;
        if (salt == worldSalt) {
            return;
        }
        roadPhaseA = salt == 0 ? 0 : (mix(salt ^ 0xA) >>> 11) * 0x1.0p-53 * 2 * Math.PI;
        roadPhaseB = salt == 0 ? 0 : (mix(salt ^ 0xB) >>> 11) * 0x1.0p-53 * 2 * Math.PI;
        worldSalt = salt;
        RegionGrid.CACHE.clear();
    }

    /**
     * splitmix64: independent of the current world salt, unlike hash().
     */
    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static final SimplexNoiseSampler NOISE = new SimplexNoiseSampler(Random.create(94L));

    private Level94Layout() {}

    /**
     * The four horizontal directions as unit steps on the x/z plane, in clockwise order.
     */
    public enum Facing {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

        public final int dx;
        public final int dz;

        Facing(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        public Facing opposite() {
            return values()[(ordinal() + 2) % 4];
        }

        public Facing clockwise() {
            return values()[(ordinal() + 1) % 4];
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * The direction closest to the given vector.
         */
        public static Facing towards(double dx, double dz) {
            if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? EAST : WEST;
            return dz > 0 ? SOUTH : NORTH;
        }
    }

    /**
     * Something wrong with a house. In the Déjà vu zone a single house of every copy of the neighborhood has one;
     * in the Wrong zone many have.
     */
    public enum Quirk {
        NONE,
        BACKWARDS,      // Blank wall towards the street, the door looks at the back yard
        BRICKED,        // No windows
        SUNK,           // One block lower than it should be
        RECOLORED,      // A different color than in every other copy
        TALL_DOOR,      // The doorway is 3 blocks tall
        BLACK_WINDOWS,  // Nothing behind the windows
        LIT,            // Lights on in broad daylight
        YARD_STOP       // A STOP sign in the front yard, looking at the door
    }

    private static final Quirk[] DEJA_VU_QUIRKS = {Quirk.BACKWARDS, Quirk.BRICKED, Quirk.SUNK, Quirk.RECOLORED, Quirk.LIT, Quirk.YARD_STOP};
    private static final Quirk[] WRONG_QUIRKS = {Quirk.BACKWARDS, Quirk.TALL_DOOR, Quirk.BLACK_WINDOWS, Quirk.BRICKED, Quirk.YARD_STOP, Quirk.LIT};

    // ------------------------------------------------------------------------------------------------
    // Zones
    // ------------------------------------------------------------------------------------------------

    /**
     * Zone of a position along the main road: 1 = Normal, 2 = Déjà vu, 3 = Wrong, 4 = Stage.
     */
    public static int zone(int x) {
        int distance = Math.abs(x);
        for (int z = ZONE_START.length - 1; z > 0; z--) {
            if (distance >= ZONE_START[z]) return z + 1;
        }
        return 1;
    }

    /**
     * Zone of a region, taken at its center so a whole neighborhood is in one zone.
     */
    public static int regionZone(int rx) {
        return zone(rx * REGION_W * GRID + REGION_W * GRID / 2);
    }

    /**
     * Where the given zone starts on the side of the road the x coordinate is on.
     */
    public static int zoneStartX(int zone, int x) {
        int start = ZONE_START[MathHelper.clamp(zone, 1, ZONE_START.length) - 1];
        return x < 0 ? -start : start;
    }

    /**
     * The region whose layout is used for the given region: every Déjà vu neighborhood (on each side of the road)
     * is the same one.
     */
    private static int templateX(int rx) {
        return regionZone(rx) == 2 ? DEJA_VU_TEMPLATE : rx;
    }

    // ------------------------------------------------------------------------------------------------
    // Main road
    // ------------------------------------------------------------------------------------------------

    /**
     * Z coordinate of the main road's center line at the given x. The road gently winds and passes through (0, 0);
     * every world winds it differently.
     */
    public static double roadCenterZ(double x) {
        double a = roadPhaseA;
        double b = roadPhaseB;
        return 22.0 * (Math.sin(x / 190.0 + a) - Math.sin(a)) + 8.0 * (Math.sin(x / 67.0 + 1.1 + b) - Math.sin(1.1 + b));
    }

    /**
     * Signed distance (along z) from the block column's center to the main road's center line.
     */
    public static double roadDistance(int wx, int wz) {
        return (wz + 0.5) - roadCenterZ(wx + 0.5);
    }

    // ------------------------------------------------------------------------------------------------
    // Neighborhood street grid
    // ------------------------------------------------------------------------------------------------

    /**
     * If the region is built up. Neighborhoods line the main road and thin out into empty fields away from it.
     */
    public static boolean isNeighborhood(int regionX, int regionZ) {
        // The area around the spawn is always built up.
        if ((regionX == 0 || regionX == -1) && (regionZ == 0 || regionZ == -1)) return true;

        double centerX = regionX * REGION_W * GRID + REGION_W * GRID / 2.0;
        double centerZ = regionZ * REGION_H * GRID + REGION_H * GRID / 2.0;
        double lateral = Math.abs(centerZ - roadCenterZ(centerX));
        double chance = MathHelper.clampedLerp(0.85, 0.1, (lateral - 250.0) / 650.0);
        return rand01(hash(regionX, regionZ, SALT_REGION), 0) < chance;
    }

    /**
     * If grid column i is an avenue (runs along the west edge of every region).
     */
    public static boolean isAvenueColumn(int i) {
        return Math.floorMod(i, REGION_W) == 0;
    }

    /**
     * If grid row j is an avenue (runs along the north edge of every region).
     */
    public static boolean isAvenueRow(int j) {
        return Math.floorMod(j, REGION_H) == 0;
    }

    /**
     * If there is a street crossing at grid node (i, j), which is the block (i * GRID, j * GRID).
     * Avenue crossings exist everywhere, neighborhood crossings only in built-up regions.
     */
    public static boolean isNodeActive(int i, int j) {
        if (Math.abs(j * GRID - roadCenterZ(i * GRID + 0.5)) < ROAD_CLEARANCE) return false;
        return isAvenueColumn(i) || isAvenueRow(j)
                || isNeighborhood(Math.floorDiv(i, REGION_W), Math.floorDiv(j, REGION_H));
    }

    /**
     * If a street goes from grid node (i, j) one step in the given direction.
     */
    public static boolean hasStreet(int i, int j, Facing direction) {
        int i2 = i + direction.dx;
        int j2 = j + direction.dz;
        if (!isNodeActive(i, j) || !isNodeActive(i2, j2)) return false;

        // Normalize to an edge going east or south from (a, b)
        int a = Math.min(i, i2);
        int b = Math.min(j, j2);
        boolean east = direction.dx != 0;
        int li = Math.floorMod(a, REGION_W);
        int lj = Math.floorMod(b, REGION_H);
        int rx = Math.floorDiv(a, REGION_W);
        int rz = Math.floorDiv(b, REGION_H);

        if (east) {
            if (lj == 0) return true;                                    // along a horizontal avenue
            if (li == 0) return isEntrance(rx, rz, Facing.WEST, lj);     // avenue -> neighborhood
            if (li == REGION_W - 1) return isEntrance(rx, rz, Facing.EAST, lj);
            return RegionGrid.get(rx, rz).east[li][lj];
        } else {
            if (li == 0) return true;                                    // along a vertical avenue
            if (lj == 0) return isEntrance(rx, rz, Facing.NORTH, li);
            if (lj == REGION_H - 1) return isEntrance(rx, rz, Facing.SOUTH, li);
            return RegionGrid.get(rx, rz).south[li][lj];
        }
    }

    /**
     * Streets joining a neighborhood to the avenue on one of its sides: 1 or 2 per side, so the neighborhood stays
     * apart from the avenue and is entered through a few T crossings.
     *
     * @param index the row (west/east sides) or column (north/south sides) of the street inside the region
     */
    private static boolean isEntrance(int rx, int rz, Facing side, int index) {
        if (!isNeighborhood(rx, rz)) return false;

        boolean vertical = side == Facing.WEST || side == Facing.EAST;
        int size = vertical ? REGION_H : REGION_W;
        int candidates = 0;
        int target = -1;
        for (int k = 1; k < size; k++) {
            // The neighborhood crossing right next to the avenue on this side
            int i = rx * REGION_W + (vertical ? (side == Facing.WEST ? 1 : REGION_W - 1) : k);
            int j = rz * REGION_H + (vertical ? k : (side == Facing.NORTH ? 1 : REGION_H - 1));
            if (!isNodeActive(i, j)) continue;
            if (k == index) target = candidates;
            candidates++;
        }
        if (target < 0) return false;

        long h = hash(hash(templateX(rx), rz, SALT_ENTRANCE), side.ordinal(), SALT_ENTRANCE);
        boolean two = rand01(h, 0) < 0.6;
        return target == (int) (rand01(h, 1) * candidates) || (two && target == (int) (rand01(h, 2) * candidates));
    }

    /**
     * The streets inside one neighborhood: a grid that starts from a random spanning tree (so every crossing can be
     * reached) with most of the other streets added on top. Normal neighborhoods are complete; the further along
     * the road, the more streets are missing.
     */
    private static final class RegionGrid {
        private static final Map<Long, RegionGrid> CACHE = new ConcurrentHashMap<>();

        final boolean[][] east = new boolean[REGION_W][REGION_H];
        final boolean[][] south = new boolean[REGION_W][REGION_H];

        static RegionGrid get(int rx, int rz) {
            long key = ((long) rx << 32) ^ (rz & 0xFFFFFFFFL);
            RegionGrid grid = CACHE.get(key);
            if (grid == null) {
                if (CACHE.size() > 16384) CACHE.clear();
                grid = new RegionGrid(rx, rz);
                CACHE.put(key, grid);
            }
            return grid;
        }

        private RegionGrid(int rx, int rz) {
            if (!isNeighborhood(rx, rz)) return;

            // Neighborhood crossings are the region's nodes except the avenue row and column
            boolean[][] active = new boolean[REGION_W][REGION_H];
            for (int li = 1; li < REGION_W; li++) {
                for (int lj = 1; lj < REGION_H; lj++) {
                    active[li][lj] = isNodeActive(rx * REGION_W + li, rz * REGION_H + lj);
                }
            }

            double fill = switch (regionZone(rx)) {
                case 1 -> 1.0;
                case 2 -> 0.85;
                case 3 -> 0.5;
                default -> 0.8;
            };

            SplittableRandom random = new SplittableRandom(hash(templateX(rx), rz, SALT_GRID));
            boolean[][] visited = new boolean[REGION_W][REGION_H];
            ArrayDeque<int[]> stack = new ArrayDeque<>();
            Facing[] options = new Facing[4];

            for (int si = 1; si < REGION_W; si++) {
                for (int sj = 1; sj < REGION_H; sj++) {
                    if (!active[si][sj] || visited[si][sj]) continue;

                    visited[si][sj] = true;
                    stack.push(new int[]{si, sj});
                    while (!stack.isEmpty()) {
                        int[] node = stack.peek();
                        int count = 0;
                        for (Facing f : Facing.values()) {
                            int ni = node[0] + f.dx;
                            int nj = node[1] + f.dz;
                            if (ni >= 1 && nj >= 1 && ni < REGION_W && nj < REGION_H && active[ni][nj] && !visited[ni][nj]) {
                                options[count++] = f;
                            }
                        }
                        if (count == 0) {
                            stack.pop();
                            continue;
                        }

                        Facing f = options[random.nextInt(count)];
                        connect(node[0], node[1], f);
                        visited[node[0] + f.dx][node[1] + f.dz] = true;
                        stack.push(new int[]{node[0] + f.dx, node[1] + f.dz});
                    }
                }
            }

            for (int li = 1; li < REGION_W; li++) {
                for (int lj = 1; lj < REGION_H; lj++) {
                    if (!active[li][lj]) continue;
                    if (li + 1 < REGION_W && active[li + 1][lj] && random.nextDouble() < fill) {
                        east[li][lj] = true;
                    }
                    if (lj + 1 < REGION_H && active[li][lj + 1] && random.nextDouble() < fill) {
                        south[li][lj] = true;
                    }
                }
            }
        }

        private void connect(int li, int lj, Facing f) {
            switch (f) {
                case EAST -> east[li][lj] = true;
                case WEST -> east[li - 1][lj] = true;
                case SOUTH -> south[li][lj] = true;
                case NORTH -> south[li][lj - 1] = true;
            }
        }
    }

    /**
     * If the block between crossings (i, j) and (i + 1, j + 1) is inside a neighborhood (and not part of the green
     * belt between the neighborhood and its avenues).
     */
    public static boolean isNeighborhoodBlock(int i, int j) {
        int li = Math.floorMod(i, REGION_W);
        int lj = Math.floorMod(j, REGION_H);
        return li >= 1 && li <= REGION_W - 2 && lj >= 1 && lj <= REGION_H - 2
                && isNeighborhood(Math.floorDiv(i, REGION_W), Math.floorDiv(j, REGION_H));
    }

    /**
     * Grid row of the first crossing on the given side of the main road (-1 = north, 1 = south) in column i.
     */
    public static int anchorRow(int i, int side) {
        double center = roadCenterZ(i * GRID + 0.5);
        return side < 0
                ? Math.floorDiv(MathHelper.floor(center - ROAD_CLEARANCE), GRID)
                : -Math.floorDiv(-MathHelper.ceil(center + ROAD_CLEARANCE), GRID);
    }

    /**
     * If the avenue of column i continues from its crossing {@link #anchorRow(int, int)} down to the main road,
     * crossing it.
     */
    public static boolean hasConnector(int i, int side) {
        if (!isAvenueColumn(i) || isNearSpecialStopSign(i * GRID, 48)) return false;
        return isNodeActive(i, anchorRow(i, side));
    }

    private static boolean hasConnectorNear(int x, int margin) {
        for (int i = Math.floorDiv(x - margin, GRID); i <= Math.floorDiv(x + margin, GRID) + 1; i++) {
            if (Math.abs(i * GRID - x) <= margin && (hasConnector(i, -1) || hasConnector(i, 1))) return true;
        }
        return false;
    }

    /**
     * If a street (or the connector to the main road) leaves crossing (i, j) in the given direction.
     */
    public static boolean hasStreetOrConnector(int i, int j, Facing f) {
        if (hasStreet(i, j, f)) return true;
        if (f == Facing.SOUTH && anchorRow(i, -1) == j) return hasConnector(i, -1);
        if (f == Facing.NORTH && anchorRow(i, 1) == j) return hasConnector(i, 1);
        return false;
    }

    /**
     * Number of streets meeting at crossing (i, j): 1 = dead end, 2 = turn or straight, 3 = T, 4 = 4-way.
     */
    public static int streetCount(int i, int j) {
        int count = 0;
        for (Facing f : Facing.values()) {
            if (hasStreetOrConnector(i, j, f)) count++;
        }
        return count;
    }

    /**
     * If the dead end at crossing (i, j) is closed with a barrier (otherwise it is an empty cul-de-sac).
     */
    public static boolean hasBarrier(int i, int j) {
        return rand01(hash(i, j, SALT_DEAD_END), 0) < 0.5;
    }

    /**
     * If walking along the neighborhood streets across avenue column i (in the given row of regions) takes you two
     * neighborhoods back without noticing. Only in the Wrong zone; the main road never loops.
     */
    public static boolean isLoopingAvenue(int i, int regionRow) {
        return isAvenueColumn(i) && zone(i * GRID) == 3 && rand01(hash(i, regionRow, SALT_LOOP), 0) < 0.5;
    }

    /**
     * Distance from the block column to the closest road: main road, neighborhood street or connector.
     */
    public static double nearestRoadDistance(int wx, int wz) {
        return Math.min(Math.abs(roadDistance(wx, wz)), streetDistance(wx, wz, HILLS_FULL));
    }

    /**
     * Distance from the block column to the closest neighborhood street or connector (not the main road), looking
     * at most about {@code range} blocks away.
     */
    public static double streetDistance(int wx, int wz, double range) {
        double best = Double.MAX_VALUE;
        int r = (int) Math.ceil(range);

        int i0 = Math.floorDiv(wx - r, GRID);
        int i1 = Math.floorDiv(wx + r, GRID) + 1;
        int j0 = Math.floorDiv(wz - r, GRID);
        int j1 = Math.floorDiv(wz + r, GRID) + 1;

        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                if (hasStreet(i, j, Facing.EAST)) {
                    best = Math.min(best, segmentDistance(wx, wz, i * GRID, j * GRID, (i + 1) * GRID, j * GRID));
                }
                if (hasStreet(i, j, Facing.SOUTH)) {
                    best = Math.min(best, segmentDistance(wx, wz, i * GRID, j * GRID, i * GRID, (j + 1) * GRID));
                }
            }

            for (int side = -1; side <= 1; side += 2) {
                if (hasConnector(i, side)) {
                    double anchorZ = anchorRow(i, side) * GRID;
                    double roadZ = roadCenterZ(i * GRID + 0.5);
                    best = Math.min(best, segmentDistance(wx, wz, i * GRID, Math.min(anchorZ, roadZ), i * GRID, Math.max(anchorZ, roadZ)));
                }
            }
        }

        return best;
    }

    /**
     * Distance from the column (wx, wz) to an axis-aligned street whose center line runs over the block columns
     * from (x0, z0) to (x1, z1). The ends are rounded.
     */
    private static double segmentDistance(int wx, int wz, double x0, double z0, double x1, double z1) {
        double dx = wx < x0 ? x0 - wx : wx > x1 ? wx - x1 : 0;
        double dz = wz < z0 ? z0 - wz : wz > z1 ? wz - z1 : 0;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ------------------------------------------------------------------------------------------------
    // Terrain shape
    // ------------------------------------------------------------------------------------------------

    /**
     * Height of the surface block at the given column.
     * Roads and their verges are flat at {@link #BASE_Y}; neighborhoods are almost flat and the open fields rise
     * into rolling hills.
     */
    public static int getTerrainHeight(int wx, int wz) {
        // Neighborhoods are perfectly flat, every lawn at the same height as the street.
        if (isNeighborhoodBlock(Math.floorDiv(wx, GRID), Math.floorDiv(wz, GRID))) {
            return BASE_Y;
        }

        double d = nearestRoadDistance(wx, wz);
        if (d <= FLAT_EDGE) {
            return BASE_Y;
        }

        double n = NOISE.sample(wx * 0.006, wz * 0.006)
                + 0.5 * NOISE.sample(wx * 0.017 + 100.0, wz * 0.017)
                + 0.2 * NOISE.sample(wx * 0.045, wz * 0.045 + 50.0);
        double hill = MathHelper.clamp(n / 1.7 * 0.5 + 0.5, 0.0, 1.0);

        double hilly = smoothstep(-0.25, 0.35, NOISE.sample(wx * 0.0025 + 300.0, wz * 0.0025 - 200.0));
        double elevation = 2.0 + Math.pow(hill, 1.5) * (6.0 + 20.0 * hilly);

        double blend = smoothstep(FLAT_EDGE, HILLS_FULL, d);
        return BASE_Y + (int) Math.round(blend * elevation);
    }

    // ------------------------------------------------------------------------------------------------
    // STOP signs
    // ------------------------------------------------------------------------------------------------

    /**
     * A STOP sign. The poles stand from {@code poleBaseY} up, the sign is on top of them and looks towards
     * {@code facing}. {@code special} is the index of the exit (see {@link #SPECIAL_STOP_X}) or -1.
     */
    public record StopSign(int x, int z, int poleBaseY, int poles, Facing facing, int special) {
        public int signY() {
            return poleBaseY + poles;
        }

        public BlockPos signPos() {
            return new BlockPos(x, signY(), z);
        }

        public boolean isExit() {
            return special >= 0;
        }
    }

    /**
     * Position of the pole base (first pole block) of one of the special STOP signs (the exits).
     */
    public static BlockPos getSpecialStopSign(int index) {
        int x = specialStopX(index);
        return new BlockPos(x, BASE_Y + 1, stopSignZ(x, specialStopSide(index)));
    }

    /**
     * X of an exit in this world: its base spot moved up to {@link #SPECIAL_STOP_SHIFT} blocks, which keeps every exit
     * in the Stage zone and the exits of each end of the road apart.
     */
    public static int specialStopX(int index) {
        if (worldSalt == 0) return SPECIAL_STOP_X[index];
        return SPECIAL_STOP_X[index] + (int) Math.round((rand01(hash(index, 0, SALT_EXIT), 0) * 2 - 1) * SPECIAL_STOP_SHIFT);
    }

    /**
     * Side of the main road the exit stands on in this world (1 or -1).
     */
    public static int specialStopSide(int index) {
        if (worldSalt == 0) return SPECIAL_STOP_SIDE[index];
        return rand01(hash(index, 0, SALT_EXIT), 1) < 0.5 ? -1 : 1;
    }

    /**
     * Z of a STOP sign standing on the given side of the main road, on the flat verge just past the curb.
     */
    public static int stopSignZ(int x, int side) {
        return MathHelper.floor(roadCenterZ(x + 0.5) + side * 5.5);
    }

    public static boolean isNearSpecialStopSign(int x, int margin) {
        for (int i = 0; i < SPECIAL_STOP_X.length; i++) {
            if (Math.abs(x - specialStopX(i)) < margin) return true;
        }
        return false;
    }

    /**
     * @return {x, z, side} of the regular STOP sign of this main road cell, or null if the cell has none.
     */
    public static int[] regularStopSign(int cell) {
        long h = hash(cell, 0, SALT_STOP);
        int x;
        int side;

        if (cell == 0) {
            // An iconic sign right next to the spawn.
            x = 20;
            side = 1;
        } else {
            x = cell * STOP_CELL + 8 + (int) (rand01(h, 1) * (STOP_CELL - 16));
            // In the Stage zone there is one in every cell.
            if (zone(x) < 4 && rand01(h, 0) > 0.5) return null;
            side = rand01(h, 2) < 0.5 ? -1 : 1;
        }

        if (isNearSpecialStopSign(x, 48) || hasConnectorNear(x, 24)) return null;
        return new int[]{x, stopSignZ(x, side), side};
    }

    /**
     * Passes every STOP sign around the given area (inclusive bounds) to the consumer: along the main road, at the
     * crossings, at the barriers of the dead ends, in front yards, out in the Stage fields and the exits.
     */
    public static void forEachStopSign(int minX, int minZ, int maxX, int maxZ, Consumer<StopSign> consumer) {
        Consumer<StopSign> inArea = sign -> {
            if (sign.x() >= minX && sign.x() <= maxX && sign.z() >= minZ && sign.z() <= maxZ) consumer.accept(sign);
        };

        // Along the main road
        for (int cell = Math.floorDiv(minX, STOP_CELL); cell <= Math.floorDiv(maxX, STOP_CELL); cell++) {
            int[] sign = regularStopSign(cell);
            if (sign != null) {
                inArea.accept(new StopSign(sign[0], sign[1], getTerrainHeight(sign[0], sign[1]) + 1, 2,
                        sign[2] > 0 ? Facing.EAST : Facing.WEST, -1));
            }
        }
        for (int i = 0; i < SPECIAL_STOP_X.length; i++) {
            BlockPos pos = getSpecialStopSign(i);
            inArea.accept(new StopSign(pos.getX(), pos.getZ(), pos.getY(), 3, specialStopSide(i) > 0 ? Facing.EAST : Facing.WEST, i));
        }

        // Avenues meeting the main road: a sign on the right of the drivers coming out
        for (int i = Math.floorDiv(minX - 8, GRID); i <= Math.floorDiv(maxX + 8, GRID) + 1; i++) {
            int x = i * GRID;
            if (hasConnector(i, -1)) {
                int cx = x - 6;
                inArea.accept(new StopSign(cx, MathHelper.floor(roadCenterZ(cx + 0.5) - 6.5), BASE_Y + 1, 2, Facing.NORTH, -1));
            }
            if (hasConnector(i, 1)) {
                int cx = x + 6;
                inArea.accept(new StopSign(cx, MathHelper.floor(roadCenterZ(cx + 0.5) + 6.5), BASE_Y + 1, 2, Facing.SOUTH, -1));
            }
        }

        // Crossings: one sign for every street arriving at a T or 4-way, a sign on the barrier of some dead ends
        for (int i = Math.floorDiv(minX - 8, GRID); i <= Math.floorDiv(maxX + 8, GRID) + 1; i++) {
            for (int j = Math.floorDiv(minZ - 8, GRID); j <= Math.floorDiv(maxZ + 8, GRID) + 1; j++) {
                if (!isNodeActive(i, j)) continue;
                int x = i * GRID;
                int z = j * GRID;
                int streets = streetCount(i, j);

                if (streets >= 3) {
                    for (Facing from : Facing.values()) {
                        if (!hasStreetOrConnector(i, j, from)) continue;
                        // Drivers arriving from this street see the sign on their right, just before the crossing.
                        Facing right = from.opposite().clockwise();
                        inArea.accept(new StopSign(x + from.dx * 6 + right.dx * 6, z + from.dz * 6 + right.dz * 6, BASE_Y + 1, 2, from, -1));
                    }
                } else if (streets == 1 && hasBarrier(i, j)) {
                    inArea.accept(new StopSign(x, z, BASE_Y + 1, 2, deadEndStreet(i, j), -1));
                }
            }
        }

        // Front yards of the houses with that quirk: the sign looks at the door
        forEachLot(minX - 8, minZ - 8, maxX + 8, maxZ + 8, house -> {
            if (house.quirk() == Quirk.YARD_STOP) {
                inArea.accept(new StopSign(house.worldX(-House.HALF_WIDTH - 1, -2), house.worldZ(-House.HALF_WIDTH - 1, -2),
                        house.floorY() + 1, 2, house.inward(), -1));
            }
        });

        // The Stage zone: signs standing in the middle of nowhere
        for (int cx = Math.floorDiv(minX, FIELD_SIGN_CELL); cx <= Math.floorDiv(maxX, FIELD_SIGN_CELL); cx++) {
            for (int cz = Math.floorDiv(minZ, FIELD_SIGN_CELL); cz <= Math.floorDiv(maxZ, FIELD_SIGN_CELL); cz++) {
                StopSign sign = fieldSign(cx, cz);
                if (sign != null) inArea.accept(sign);
            }
        }
    }

    /**
     * The only street of a dead end.
     */
    public static Facing deadEndStreet(int i, int j) {
        for (Facing f : Facing.values()) {
            if (hasStreetOrConnector(i, j, f)) return f;
        }
        return Facing.NORTH;
    }

    private static StopSign fieldSign(int cellX, int cellZ) {
        long h = hash(cellX, cellZ, SALT_FIELD_SIGN);
        int x = cellX * FIELD_SIGN_CELL + 3 + (int) (rand01(h, 1) * (FIELD_SIGN_CELL - 6));
        int z = cellZ * FIELD_SIGN_CELL + 3 + (int) (rand01(h, 2) * (FIELD_SIGN_CELL - 6));
        if (zone(x) < 4 || rand01(h, 0) > 0.6) return null;
        if (nearestRoadDistance(x, z) < 7 || isNearSpecialStopSign(x, 16)) return null;
        if (WaterTower.near(x, z) != null) return null;

        boolean[] blocked = {false};
        forEachLot(x, z, x, z, house -> {
            if (house.overlaps(x, z, House.YARD + 1)) blocked[0] = true;
        });
        if (blocked[0]) return null;

        return new StopSign(x, z, getTerrainHeight(x, z) + 1, 2, Facing.values()[(int) (rand01(h, 3) * 4)], -1);
    }

    // ------------------------------------------------------------------------------------------------
    // Houses
    // ------------------------------------------------------------------------------------------------

    /**
     * A small house. The door is at ({@code doorX}, {@code doorZ}) in the middle of the front wall and looks towards
     * {@code facing}, where its street is. The house is 7 blocks wide and 8 deep, with a 2 block yard around it.
     * Ghost houses are only a facade and a floor.
     */
    public record House(int doorX, int doorZ, Facing facing, int floorY, boolean ghost, int style, int roof, Quirk quirk) {
        public static final int HALF_WIDTH = 3;
        public static final int DEPTH = 8;
        public static final int YARD = 2;

        /**
         * The house next to the main road for this cell and side of the road, or null. Only away from the
         * neighborhood streets.
         */
        public static House at(int cell, int side) {
            long h = hash(cell, side, SALT_HOUSE);
            if (rand01(h, 0) > 0.38) return null;

            int centerX = cell * HOUSE_CELL + 16 + (int) (rand01(h, 1) * (HOUSE_CELL - 32));
            if (Math.abs(centerX) < 32 || isNearSpecialStopSign(centerX, 20) || hasConnectorNear(centerX, 24)) return null;

            int setback = 11 + (int) (rand01(h, 2) * 6);
            int doorZ = MathHelper.floor(roadCenterZ(centerX + 0.5) + side * setback);
            int zone = zone(centerX);
            Quirk quirk = zone == 3 && rand01(h, 6) < 0.4 ? WRONG_QUIRKS[(int) (rand01(h, 7) * WRONG_QUIRKS.length)] : Quirk.NONE;
            House house = create(centerX, doorZ, side > 0 ? Facing.NORTH : Facing.SOUTH, h, zone, quirk);

            for (int u = -HALF_WIDTH - YARD; u <= HALF_WIDTH + YARD; u += 2) {
                for (int v = -YARD; v <= DEPTH + YARD; v += 2) {
                    if (streetDistance(house.worldX(u, v), house.worldZ(u, v), 8) < 6) return null;
                }
            }
            return house;
        }

        static House create(int doorX, int doorZ, Facing facing, long h, int zone, Quirk quirk) {
            Facing inward = facing.opposite();
            int centerX = doorX + inward.dx * DEPTH / 2;
            int centerZ = doorZ + inward.dz * DEPTH / 2;
            int floorY = Math.max(BASE_Y, getTerrainHeight(centerX, centerZ));
            if (quirk == Quirk.SUNK) floorY--;

            double ghostChance = switch (zone) {
                case 1 -> 0.0;
                case 2 -> 0.04;
                case 3 -> 0.35;
                default -> 1.0;
            };
            boolean ghost = rand01(h, 3) < ghostChance;
            int style = (int) (rand01(h, 4) * HOUSE_STYLES);
            if (quirk == Quirk.RECOLORED) style = (style + 1 + (int) (rand01(h, 8) * (HOUSE_STYLES - 1))) % HOUSE_STYLES;
            int roof = (int) (rand01(h, 5) * ROOF_STYLES);
            // A facade cannot have most quirks, but it can still have a sign staring at it.
            Quirk kept = ghost && quirk != Quirk.YARD_STOP ? Quirk.NONE : quirk;
            return new House(doorX, doorZ, facing, floorY, ghost, style, roof, kept);
        }

        /** Direction from the front wall to the back of the house. */
        public Facing inward() {
            return facing.opposite();
        }

        /** Direction along the front wall (local u axis). */
        public Facing across() {
            return facing.clockwise();
        }

        /**
         * World x of the local position: u across the front wall (0 = door), v from the front wall to the back.
         */
        public int worldX(int u, int v) {
            return doorX + u * across().dx + v * inward().dx;
        }

        public int worldZ(int u, int v) {
            return doorZ + u * across().dz + v * inward().dz;
        }

        public int minX() { return Math.min(worldX(-HALF_WIDTH, 0), worldX(HALF_WIDTH, DEPTH - 1)); }
        public int maxX() { return Math.max(worldX(-HALF_WIDTH, 0), worldX(HALF_WIDTH, DEPTH - 1)); }
        public int minZ() { return Math.min(worldZ(-HALF_WIDTH, 0), worldZ(HALF_WIDTH, DEPTH - 1)); }
        public int maxZ() { return Math.max(worldZ(-HALF_WIDTH, 0), worldZ(HALF_WIDTH, DEPTH - 1)); }

        public boolean intersects(int areaMinX, int areaMinZ, int areaMaxX, int areaMaxZ, int margin) {
            return maxX() + margin >= areaMinX && minX() - margin <= areaMaxX
                    && maxZ() + margin >= areaMinZ && minZ() - margin <= areaMaxZ;
        }

        public boolean overlaps(int x, int z, int margin) {
            return x >= minX() - margin && x <= maxX() + margin && z >= minZ() - margin && z <= maxZ() + margin;
        }
    }

    /**
     * The house on lot (qx, qz) of the block between crossings (i, j) and (i + 1, j + 1), or null.
     * Each block is split into 4 lots; a lot's house faces one of the two streets next to it.
     */
    public static House lot(int i, int j, int qx, int qz) {
        int rx = Math.floorDiv(i, REGION_W);
        int rz = Math.floorDiv(j, REGION_H);
        int zone = regionZone(rx);
        long h = lotHash(i, j, qx, qz);
        if (!lotExists(i, j, qx, qz, h, zone)) return null;

        Quirk quirk = Quirk.NONE;
        if (zone == 2) {
            // Spot the difference: one house of this copy of the neighborhood is not like the others.
            long regionHash = hash(rx, rz, SALT_QUIRK);
            if (lotIndex(i, j, qx, qz) == quirkyLot(rx, rz, regionHash)) {
                quirk = DEJA_VU_QUIRKS[(int) (rand01(regionHash, 1) * DEJA_VU_QUIRKS.length)];
            }
        } else if (zone == 3) {
            long q = hash(h, i * 31L + j, SALT_QUIRK);
            if (rand01(q, 0) < 0.4) quirk = WRONG_QUIRKS[(int) (rand01(q, 1) * WRONG_QUIRKS.length)];
        } else if (zone == 4) {
            if (rand01(hash(h, j, SALT_QUIRK), 0) < 0.35) quirk = Quirk.YARD_STOP;
        }

        boolean horizontal = hasStreet(i, j + qz, Facing.EAST);
        boolean vertical = hasStreet(i + qx, j, Facing.SOUTH);
        if (horizontal && vertical) {
            horizontal = rand01(h, 1) < 0.5;
        }

        House house;
        if (horizontal) {
            int doorZ = (j + qz) * GRID + (qz == 0 ? 8 : -8);
            int doorX = i * GRID + (qx == 0 ? 12 : GRID - 12);
            house = House.create(doorX, doorZ, qz == 0 ? Facing.NORTH : Facing.SOUTH, h, zone, quirk);
        } else {
            int doorX = (i + qx) * GRID + (qx == 0 ? 8 : -8);
            int doorZ = j * GRID + (qz == 0 ? 12 : GRID - 12);
            house = House.create(doorX, doorZ, qx == 0 ? Facing.WEST : Facing.EAST, h, zone, quirk);
        }
        return house;
    }

    /**
     * Lots of the Déjà vu copies are hashed with the template's coordinates, so every copy has the same houses.
     */
    private static long lotHash(int i, int j, int qx, int qz) {
        int rx = Math.floorDiv(i, REGION_W);
        int ti = templateX(rx) * REGION_W + Math.floorMod(i, REGION_W);
        return hash(hash(ti, j, SALT_LOT), qx * 2L + qz, SALT_LOT);
    }

    private static boolean lotExists(int i, int j, int qx, int qz, long h, int zone) {
        if (!isNeighborhoodBlock(i, j) || blockTower(i, j) != null) return false;

        double chance = switch (zone) {
            case 1 -> 0.8;
            case 2 -> 0.78;
            case 3 -> 0.7;
            default -> 0.85;
        };
        if (rand01(h, 0) > chance) return false;
        if (!hasStreet(i, j + qz, Facing.EAST) && !hasStreet(i + qx, j, Facing.SOUTH)) return false;

        // The main road can cut through a block: keep the lot clear of it.
        for (int u = -6; u <= 6; u += 3) {
            for (int v = -2; v <= 10; v += 3) {
                int x = i * GRID + (qx == 0 ? 12 : GRID - 12) + u;
                int z = j * GRID + (qz == 0 ? 12 : GRID - 12) + v - 4;
                if (Math.abs(roadDistance(x, z)) < 7) return false;
            }
        }
        return true;
    }

    private static int lotIndex(int i, int j, int qx, int qz) {
        int li = Math.floorMod(i, REGION_W) - 1;
        int lj = Math.floorMod(j, REGION_H) - 1;
        return ((lj * (REGION_W - 2) + li) * 4) + qz * 2 + qx;
    }

    /**
     * The index of the one lot of a Déjà vu neighborhood that has something wrong (the first existing lot from a
     * random starting point).
     */
    private static int quirkyLot(int rx, int rz, long regionHash) {
        int lots = (REGION_W - 2) * (REGION_H - 2) * 4;
        int start = (int) (rand01(regionHash, 0) * lots);
        for (int k = 0; k < lots; k++) {
            int index = (start + k) % lots;
            int q = index % 4;
            int block = index / 4;
            int i = rx * REGION_W + 1 + block % (REGION_W - 2);
            int j = rz * REGION_H + 1 + block / (REGION_W - 2);
            int qx = q & 1;
            int qz = q >> 1;
            if (lotExists(i, j, qx, qz, lotHash(i, j, qx, qz), 2)) return index;
        }
        return -1;
    }

    /**
     * Passes every lot house of the blocks around the given area (inclusive bounds) to the consumer.
     */
    public static void forEachLot(int minX, int minZ, int maxX, int maxZ, Consumer<House> consumer) {
        for (int i = Math.floorDiv(minX, GRID) - 1; i <= Math.floorDiv(maxX, GRID); i++) {
            for (int j = Math.floorDiv(minZ, GRID) - 1; j <= Math.floorDiv(maxZ, GRID); j++) {
                for (int q = 0; q < 4; q++) {
                    House house = lot(i, j, q & 1, q >> 1);
                    if (house != null) consumer.accept(house);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Water towers
    // ------------------------------------------------------------------------------------------------

    /**
     * A water tower centered on (x, z). Legs stand at +-{@link #LEG} from the center.
     */
    public record WaterTower(int x, int z) {
        public static final int LEG = 3;
        public static final int LEG_HEIGHT = 14;
        public static final double TANK_RADIUS = 3.6;
        public static final int RADIUS = 5;

        /**
         * A water tower out in the open fields, or null.
         */
        public static WaterTower at(int cellX, int cellZ) {
            long h = hash(cellX, cellZ, SALT_TOWER);
            if (rand01(h, 0) > 0.55) return null;

            int x = cellX * TOWER_CELL + 40 + (int) (rand01(h, 1) * (TOWER_CELL - 80));
            int z = cellZ * TOWER_CELL + 40 + (int) (rand01(h, 2) * (TOWER_CELL - 80));
            if (nearestRoadDistance(x, z) < 30 || isNearSpecialStopSign(x, 24)) return null;

            return new WaterTower(x, z);
        }

        /**
         * The water tower (in the fields or in a block) whose legs or tank cover the given column, or null.
         */
        public static WaterTower near(int x, int z) {
            WaterTower tower = at(Math.floorDiv(x, TOWER_CELL), Math.floorDiv(z, TOWER_CELL));
            if (tower != null && Math.abs(tower.x - x) <= RADIUS + 3 && Math.abs(tower.z - z) <= RADIUS + 3) return tower;
            tower = blockTower(Math.floorDiv(x, GRID), Math.floorDiv(z, GRID));
            if (tower != null && Math.abs(tower.x - x) <= RADIUS + 3 && Math.abs(tower.z - z) <= RADIUS + 3) return tower;
            return null;
        }

        public boolean intersects(int areaMinX, int areaMinZ, int areaMaxX, int areaMaxZ, int margin) {
            return x + RADIUS + margin >= areaMinX && x - RADIUS - margin <= areaMaxX
                    && z + RADIUS + margin >= areaMinZ && z - RADIUS - margin <= areaMaxZ;
        }
    }

    /**
     * A water tower in the middle of a block (instead of its houses), or null. They are landmarks to find your way.
     * The Déjà vu copies have none, so they stay identical.
     */
    public static WaterTower blockTower(int i, int j) {
        if (zone(i * GRID) == 2) return null;
        if (rand01(hash(i, j, SALT_TOWER), 3) > BLOCK_TOWER_CHANCE) return null;
        boolean anyStreet = hasStreet(i, j, Facing.EAST) || hasStreet(i, j, Facing.SOUTH)
                || hasStreet(i + 1, j, Facing.SOUTH) || hasStreet(i, j + 1, Facing.EAST);
        if (!anyStreet) return null;

        int x = i * GRID + GRID / 2;
        int z = j * GRID + GRID / 2;
        if (Math.abs(roadDistance(x, z)) < 14) return null;
        return new WaterTower(x, z);
    }

    // ------------------------------------------------------------------------------------------------
    // Trees
    // ------------------------------------------------------------------------------------------------

    public record Tree(int x, int z, int height, boolean birch) {
        public static Tree at(int cellX, int cellZ) {
            long h = hash(cellX, cellZ, SALT_TREE);
            if (rand01(h, 0) > 0.3) return null;

            int x = cellX * TREE_CELL + 3 + (int) (rand01(h, 1) * (TREE_CELL - 6));
            int z = cellZ * TREE_CELL + 3 + (int) (rand01(h, 2) * (TREE_CELL - 6));
            // Nothing grows on the Stage.
            if (zone(x) == 4) return null;
            if (Math.abs(roadDistance(x, z)) < 9 || streetDistance(x, z, 12) < 9) return null;
            if (WaterTower.near(x, z) != null) return null;

            int cell = Math.floorDiv(x, HOUSE_CELL);
            for (int side = -1; side <= 1; side += 2) {
                for (int c = cell - 1; c <= cell + 1; c++) {
                    House house = House.at(c, side);
                    if (house != null && house.overlaps(x, z, 5)) return null;
                }
            }

            boolean[] blocked = {false};
            forEachLot(x, z, x, z, house -> {
                if (house.overlaps(x, z, 5)) blocked[0] = true;
            });
            if (blocked[0]) return null;

            return new Tree(x, z, 4 + (int) (rand01(h, 3) * 3), rand01(h, 4) < 0.3);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------------

    public static double smoothstep(double edge0, double edge1, double value) {
        double t = MathHelper.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static long hash(long a, long b, long salt) {
        long h = (salt + worldSalt) * 0x9E3779B97F4A7C15L + a * 0xC2B2AE3D27D4EB4FL + b * 0x165667B19E3779F9L;
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= (h >>> 33);
        return h;
    }

    /**
     * Deterministic value in [0, 1) derived from a hash and an index.
     */
    private static double rand01(long hash, int index) {
        long h = hash(hash, index, 0x94L);
        return (h >>> 11) * 0x1.0p-53;
    }
}
