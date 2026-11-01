package com.sp;

import com.sp.world.generation.chunk_generator.Level94Layout;
import com.sp.world.levels.custom.Level324Backroomslevel;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks that Level 94 and the Level 324 exits change with the world seed and still keep their exits reachable.
 */
public class LevelSeedTest {
    private static final long[] SEEDS = {1L, -42L, 8_675_309L, Long.MAX_VALUE, 0x94_94_94L, -7_777_777_777L};

    @Test
    @DisplayName("Every world winds the Level 94 road differently, always through the spawn")
    void roadVaries() {
        try {
            Level94Layout.useSeed(0);
            double original = Level94Layout.roadCenterZ(600);
            for (long seed : SEEDS) {
                Level94Layout.useSeed(seed);
                assertEquals(0.0, Level94Layout.roadCenterZ(0), 1e-9, "the road passes through the spawn, seed " + seed);
                assertNotEquals(original, Level94Layout.roadCenterZ(600), 1e-6, "seed " + seed);
            }
        } finally {
            Level94Layout.useSeed(0);
        }
    }

    @Test
    @DisplayName("Level 94 exits move per world but stay in the Stage zone, apart, beside the road")
    void exitsStayValid() {
        try {
            for (long seed : SEEDS) {
                Level94Layout.useSeed(seed);
                int[] xs = new int[Level94Layout.SPECIAL_STOP_X.length];
                for (int i = 0; i < xs.length; i++) {
                    BlockPos sign = Level94Layout.getSpecialStopSign(i);
                    xs[i] = sign.getX();
                    assertEquals(4, Level94Layout.zone(sign.getX()), "exit " + i + " in the Stage zone, seed " + seed);
                    double fromRoad = Math.abs(sign.getZ() + 0.5 - Level94Layout.roadCenterZ(sign.getX() + 0.5));
                    assertTrue(fromRoad > 4 && fromRoad < 7, "exit " + i + " on the verge, seed " + seed + ": " + fromRoad);
                }
                for (int i = 0; i < xs.length; i++) {
                    for (int j = i + 1; j < xs.length; j++) {
                        assertTrue(Math.abs(xs[i] - xs[j]) >= 100, "exits " + i + " and " + j + " apart, seed " + seed);
                    }
                }
            }

            Level94Layout.useSeed(SEEDS[2]);
            BlockPos first = Level94Layout.getSpecialStopSign(0);
            Level94Layout.useSeed(SEEDS[3]);
            Level94Layout.useSeed(SEEDS[2]);
            assertEquals(first, Level94Layout.getSpecialStopSign(0), "the same world always gets the same layout");
        } finally {
            Level94Layout.useSeed(0);
        }
    }

    @Test
    @DisplayName("Every existing Level 94 layout and blueprint check also holds in other worlds")
    void existingChecksHoldForOtherSeeds() throws Exception {
        try {
            for (long seed : new long[]{SEEDS[0], SEEDS[2], SEEDS[5]}) {
                Level94Layout.useSeed(seed);
                for (Class<?> testClass : new Class<?>[]{Level94LayoutTest.class, Level94BlueprintTest.class}) {
                    var constructor = testClass.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    Object instance = constructor.newInstance();
                    for (var method : testClass.getDeclaredMethods()) {
                        if (!method.isAnnotationPresent(Test.class)) continue;
                        method.setAccessible(true);
                        try {
                            method.invoke(instance);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            fail(testClass.getSimpleName() + "." + method.getName() + " fails with seed " + seed + ": " + e.getCause(), e.getCause());
                        }
                    }
                }
            }
        } finally {
            Level94Layout.useSeed(0);
        }
    }

    @Test
    @DisplayName("Level 324 exit puddles turn per world and keep their distance to the center")
    void puddles() {
        float[] original = Level324Backroomslevel.puddleCenters(0);
        assertEquals(300f, original[0], 1e-4f);
        assertEquals(0f, original[1], 1e-4f);

        for (long seed : SEEDS) {
            float[] turned = Level324Backroomslevel.puddleCenters(seed);
            assertFalse(Arrays.equals(original, turned), "seed " + seed);
            for (int i = 0; i < turned.length; i += 2) {
                double before = Math.hypot(original[i], original[i + 1]);
                double after = Math.hypot(turned[i], turned[i + 1]);
                assertEquals(before, after, 1e-3, "puddle " + i / 2 + ", seed " + seed);
            }
        }
        assertArrayEquals(Level324Backroomslevel.puddleCenters(77L), Level324Backroomslevel.puddleCenters(77L));
    }
}
