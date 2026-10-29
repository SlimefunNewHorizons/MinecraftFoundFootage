package com.sp;

import com.sp.world.generation.chunk_generator.Level94Layout;
import com.sp.world.generation.chunk_generator.Level94Layout.Facing;
import com.sp.world.generation.chunk_generator.Level94Layout.House;
import com.sp.world.generation.chunk_generator.Level94Layout.StopSign;
import com.sp.world.generation.chunk_generator.Level94Layout.Tree;
import com.sp.world.generation.chunk_generator.Level94Layout.WaterTower;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.sp.world.generation.chunk_generator.Level94Layout.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the Level 94 layout: roads, neighborhoods, houses, signs and zones, over a large part of the level on both
 * sides of the spawn.
 */
class Level94LayoutTest {
    // Areas checked: around the spawn, deep in every zone, on both ends of the road
    private static final int[][] AREAS = {
            {-250, -250, 250, 250},
            {500, -300, 900, 300},
            {1200, -300, 1600, 300},
            {2100, -300, 2900, 300},
            {-2900, -300, -2100, 300},
    };

    @Test
    @DisplayName("Zones are symmetric around the spawn and go 1 to 4")
    void zonesAreSymmetric() {
        int previous = 1;
        for (int x = 0; x <= 4000; x += 25) {
            int zone = zone(x);
            assertEquals(zone, zone(-x), "Zone at " + x + " differs from " + -x);
            assertTrue(zone >= previous && zone <= 4, "Zones must only grow along the road");
            previous = zone;
        }
        assertEquals(4, previous);
        assertTrue(LATERAL_RETURN < LATERAL_LIMIT, "The lateral loop must send players back towards the road");
    }

    @Test
    @DisplayName("No house, yard or water tower stands on a road")
    void nothingBuiltOnRoads() {
        for (int[] area : AREAS) {
            forEachLot(area[0], area[1], area[2], area[3], house -> assertHouseOffRoads(house, "lot"));
            for (int cell = Math.floorDiv(area[0], HOUSE_CELL); cell <= Math.floorDiv(area[2], HOUSE_CELL); cell++) {
                for (int side = -1; side <= 1; side += 2) {
                    House house = House.at(cell, side);
                    if (house != null) assertHouseOffRoads(house, "main road house");
                }
            }
            for (int i = Math.floorDiv(area[0], GRID); i <= Math.floorDiv(area[2], GRID); i++) {
                for (int j = Math.floorDiv(area[1], GRID); j <= Math.floorDiv(area[3], GRID); j++) {
                    WaterTower tower = blockTower(i, j);
                    if (tower != null) {
                        assertTrue(nearestRoadDistance(tower.x(), tower.z()) > WaterTower.RADIUS + CURB_EDGE,
                                "Water tower on a road at " + tower);
                    }
                }
            }
        }
    }

    private static void assertHouseOffRoads(House house, String kind) {
        for (int u = -House.HALF_WIDTH; u <= House.HALF_WIDTH; u++) {
            for (int v = 0; v < House.DEPTH; v++) {
                int x = house.worldX(u, v);
                int z = house.worldZ(u, v);
                assertTrue(nearestRoadDistance(x, z) > CURB_EDGE, kind + " " + house + " stands on a road at " + x + ", " + z);
            }
        }
    }

    @Test
    @DisplayName("Houses of a block never overlap each other")
    void housesDoNotOverlap() {
        for (int[] area : AREAS) {
            List<House> houses = new ArrayList<>();
            forEachLot(area[0], area[1], area[2], area[3], houses::add);
            Set<Long> taken = new HashSet<>();
            for (House house : new LinkedHashSet<>(houses)) {
                for (int u = -House.HALF_WIDTH; u <= House.HALF_WIDTH; u++) {
                    for (int v = 0; v < House.DEPTH; v++) {
                        long key = ((long) house.worldX(u, v) << 32) ^ (house.worldZ(u, v) & 0xFFFFFFFFL);
                        assertTrue(taken.add(key), "Two houses overlap at " + house.worldX(u, v) + ", " + house.worldZ(u, v));
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Doors look at an existing street (except houses turned around)")
    void doorsFaceAStreet() {
        for (int[] area : AREAS) {
            forEachLot(area[0], area[1], area[2], area[3], house -> {
                if (house.quirk() == Quirk.BACKWARDS) return;
                // Walking out of the door you reach a road within 12 blocks
                boolean reached = false;
                for (int k = 1; k <= 12 && !reached; k++) {
                    reached = nearestRoadDistance(house.worldX(0, -k), house.worldZ(0, -k)) <= ROAD_HALF_WIDTH;
                }
                assertTrue(reached, "The door of " + house + " leads nowhere");
            });
        }
    }

    @Test
    @DisplayName("Neighborhoods are flat, roads are flat")
    void neighborhoodsAndRoadsAreFlat() {
        for (int[] area : AREAS) {
            for (int x = area[0]; x <= area[2]; x += 5) {
                for (int z = area[1]; z <= area[3]; z += 5) {
                    int height = getTerrainHeight(x, z);
                    if (isNeighborhoodBlock(Math.floorDiv(x, GRID), Math.floorDiv(z, GRID)) || nearestRoadDistance(x, z) <= FLAT_EDGE) {
                        assertEquals(BASE_Y, height, "Not flat at " + x + ", " + z);
                    }
                    assertTrue(height >= BASE_Y && height < BASE_Y + 40, "Height out of range at " + x + ", " + z);
                }
            }
        }
    }

    @Test
    @DisplayName("Every crossing of a neighborhood can be reached from the avenues")
    void neighborhoodsAreReachable() {
        int checked = 0;
        for (int rx = -14; rx <= 14; rx++) {
            for (int rz = -3; rz <= 2; rz++) {
                if (!isNeighborhood(rx, rz)) continue;
                checked++;

                // Flood fill over the streets, starting from every avenue crossing of the region and its neighbors
                Set<Long> reached = new HashSet<>();
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                for (int i = rx * REGION_W - REGION_W; i <= (rx + 2) * REGION_W; i++) {
                    for (int j = rz * REGION_H - REGION_H; j <= (rz + 2) * REGION_H; j++) {
                        if ((isAvenueColumn(i) || isAvenueRow(j)) && isNodeActive(i, j) && reached.add(key(i, j))) {
                            queue.add(new int[]{i, j});
                        }
                    }
                }
                while (!queue.isEmpty()) {
                    int[] node = queue.poll();
                    for (Facing f : Facing.values()) {
                        int ni = node[0] + f.dx;
                        int nj = node[1] + f.dz;
                        if (Math.abs(ni - rx * REGION_W) > 2 * REGION_W || Math.abs(nj - rz * REGION_H) > 2 * REGION_H) continue;
                        if (hasStreet(node[0], node[1], f) && reached.add(key(ni, nj))) {
                            queue.add(new int[]{ni, nj});
                        }
                    }
                }

                for (int li = 1; li < REGION_W; li++) {
                    for (int lj = 1; lj < REGION_H; lj++) {
                        int i = rx * REGION_W + li;
                        int j = rz * REGION_H + lj;
                        if (isNodeActive(i, j) && streetCount(i, j) > 0) {
                            assertTrue(reached.contains(key(i, j)),
                                    "Crossing " + i + ", " + j + " (region " + rx + ", " + rz + ") cannot be reached from any avenue");
                        }
                    }
                }
            }
        }
        assertTrue(checked > 20, "Too few neighborhoods to check: " + checked);
    }

    private static long key(int i, int j) {
        return ((long) i << 32) ^ (j & 0xFFFFFFFFL);
    }

    @Test
    @DisplayName("STOP signs stand beside the roads, never on them (except barriers)")
    void stopSignsBesideRoads() {
        for (int[] area : AREAS) {
            forEachStopSign(area[0], area[1], area[2], area[3], sign -> {
                boolean barrier = Math.floorMod(sign.x(), GRID) == 0 && Math.floorMod(sign.z(), GRID) == 0
                        && isNodeActive(Math.floorDiv(sign.x(), GRID), Math.floorDiv(sign.z(), GRID));
                if (barrier) return;
                assertTrue(nearestRoadDistance(sign.x(), sign.z()) > CURB_EDGE, "STOP sign on a road: " + sign);
                assertTrue(sign.poles() >= 2, "STOP sign without pole: " + sign);
            });
        }
    }

    @Test
    @DisplayName("The exits are the 6 lonely signs of the Stage, on flat ground beside the main road")
    void exitsAreLonely() {
        List<StopSign> exits = new ArrayList<>();
        forEachStopSign(-3200, -400, 3200, 400, sign -> {
            if (sign.isExit()) exits.add(sign);
        });
        assertEquals(SPECIAL_STOP_X.length, exits.size(), "Wrong number of exits");

        for (StopSign exit : exits) {
            assertEquals(4, zone(exit.x()), "Exit outside the Stage: " + exit);
            assertEquals(BASE_Y + 1, exit.poleBaseY(), "Exit not on flat ground: " + exit);
            double distance = Math.abs(roadDistance(exit.x(), exit.z()));
            assertTrue(distance > CURB_EDGE && distance < FLAT_EDGE, "Exit not beside the main road: " + exit);

            forEachStopSign(exit.x() - 10, exit.z() - 10, exit.x() + 10, exit.z() + 10, other -> {
                if (other != exit && !other.equals(exit)) {
                    fail("Another sign right next to the exit " + exit + ": " + other);
                }
            });
        }
    }

    @Test
    @DisplayName("Zone rules: the Stage only has facades, the Normal zone has no ghost houses or quirks")
    void zoneRules() {
        forEachLot(2100, -300, 2900, 300, house -> assertTrue(house.ghost(), "A full house on the Stage: " + house));
        forEachLot(-250, -250, 250, 250, house -> {
            if (zone(house.doorX()) == 1) {
                assertFalse(house.ghost(), "A ghost house in the Normal zone: " + house);
                assertEquals(Quirk.NONE, house.quirk(), "A quirky house in the Normal zone: " + house);
            }
        });
        for (int cx = Math.floorDiv(2100, TREE_CELL); cx <= Math.floorDiv(2900, TREE_CELL); cx++) {
            for (int cz = Math.floorDiv(-300, TREE_CELL); cz <= Math.floorDiv(300, TREE_CELL); cz++) {
                assertNull(Tree.at(cx, cz), "Trees on the Stage");
            }
        }
    }

    @Test
    @DisplayName("The layout is deterministic")
    void layoutIsDeterministic() {
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        forEachLot(600, -200, 800, 200, house -> first.add(house.toString()));
        forEachLot(600, -200, 800, 200, house -> second.add(house.toString()));
        assertEquals(first, second);
        assertEquals(getTerrainHeight(1234, 567), getTerrainHeight(1234, 567));
    }
}
