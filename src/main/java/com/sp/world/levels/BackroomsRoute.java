package com.sp.world.levels;

import com.sp.init.BackroomsLevels;

import java.util.List;
import java.util.Random;

/**
 * The order the levels are crossed in. Level 0 always comes first; the other levels follow in an order shuffled by
 * the world seed, so every player of a world walks the same route and no level is ever visited twice. Every exit of
 * a level leads to the next one, and the exits of the last one lead back to the overworld.
 */
public final class BackroomsRoute {
    private BackroomsRoute() {}

    private static List<BackroomsLevel> shuffledLevels() {
        return List.of(
                BackroomsLevels.LEVEL1_BACKROOMS_LEVEL,
                BackroomsLevels.LEVEL2_BACKROOMS_LEVEL,
                BackroomsLevels.POOLROOMS_BACKROOMS_LEVEL,
                BackroomsLevels.INFINITE_FIELD_BACKROOMS_LEVEL,
                BackroomsLevels.LEVEL324_BACKROOMS_LEVEL,
                BackroomsLevels.LEVEL94_BACKROOMS_LEVEL
        );
    }

    /**
     * Where the exits of {@code from} lead in the world with this seed, or null if {@code from} is not on the
     * route (the overworld, the nether...).
     */
    public static BackroomsLevel next(BackroomsLevel from, long seed) {
        List<BackroomsLevel> levels = shuffledLevels();
        int[] order = order(seed, levels.size());
        if (from == BackroomsLevels.LEVEL0_BACKROOMS_LEVEL) {
            return levels.get(order[0]);
        }

        for (int i = 0; i < order.length; i++) {
            if (levels.get(order[i]) == from) {
                return i + 1 < order.length ? levels.get(order[i + 1]) : BackroomsLevels.OVERWORLD_REPRESENTING_BACKROOMS_LEVEL;
            }
        }
        return null;
    }

    /**
     * A permutation of 0..count-1 that only depends on the seed.
     */
    static int[] order(long seed, int count) {
        int[] order = new int[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        Random random = new Random(seed ^ 0x5DEECE66DL * 94);
        for (int i = count - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = order[i];
            order[i] = order[j];
            order[j] = swap;
        }
        return order;
    }
}
