package com.sp.world.levels;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the order the levels are crossed in.
 */
public class BackroomsRouteTest {

    @Test
    @DisplayName("Every world visits each level exactly once")
    void permutation() {
        for (long seed = -500; seed < 500; seed++) {
            int[] order = BackroomsRoute.order(seed * 7919, 6);
            int[] sorted = order.clone();
            Arrays.sort(sorted);
            assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5}, sorted, "seed " + seed);
        }
    }

    @Test
    @DisplayName("Same world same route, and different worlds get different routes")
    void seeded() {
        assertArrayEquals(BackroomsRoute.order(12345L, 6), BackroomsRoute.order(12345L, 6));

        Set<String> routes = new HashSet<>();
        Set<Integer> firstLevels = new HashSet<>();
        for (long seed = 0; seed < 200; seed++) {
            int[] order = BackroomsRoute.order(seed, 6);
            routes.add(Arrays.toString(order));
            firstLevels.add(order[0]);
        }
        assertTrue(routes.size() > 150, "routes should vary between worlds, got " + routes.size());
        assertEquals(6, firstLevels.size(), "any level can come right after Level 0");
    }
}
