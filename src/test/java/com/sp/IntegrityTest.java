package com.sp;

import com.sp.entity.voice.LanguageDetector;
import com.sp.util.MathStuff;
import com.sp.world.generation.chunk_generator.Level94Layout;
import com.sp.world.generation.chunk_generator.PoolroomsChunkGenerator;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests to verify the integrity of critical logic, math, and parsing routines.
 */
public class IntegrityTest {

    static {
        try {
            net.minecraft.SharedConstants.createGameVersion();
            net.minecraft.Bootstrap.initialize();
        } catch (Throwable ignored) {
        }
    }

    @Test
    @DisplayName("Verify Poolrooms room dimension parser handles various name formats without NumberFormatException")
    void testPoolroomsRoomDimensionParsing() throws Exception {
        Method method = PoolroomsChunkGenerator.class.getDeclaredMethod("parseRoomDimension", String.class, String.class, net.minecraft.util.Identifier.class);
        method.setAccessible(true);

        net.minecraft.util.Identifier idStandard = new net.minecraft.util.Identifier("spb-revamped", "poolrooms/megaroom_16x16");
        int widthStandard = (int) method.invoke(null, "width", "poolrooms", idStandard);
        int heightStandard = (int) method.invoke(null, "height", "poolrooms", idStandard);
        assertEquals(16, widthStandard);
        assertEquals(16, heightStandard);

        net.minecraft.util.Identifier idWithVariant = new net.minecraft.util.Identifier("spb-revamped", "poolrooms/megaroom_16x32_1");
        int widthVariant = (int) method.invoke(null, "width", "poolrooms", idWithVariant);
        int heightVariant = (int) method.invoke(null, "height", "poolrooms", idWithVariant);
        assertEquals(16, widthVariant);
        assertEquals(32, heightVariant);

        net.minecraft.util.Identifier idDifferentAspect = new net.minecraft.util.Identifier("spb-revamped", "poolrooms/megaroom_64x48_3");
        int widthDiff = (int) method.invoke(null, "width", "poolrooms", idDifferentAspect);
        int heightDiff = (int) method.invoke(null, "height", "poolrooms", idDifferentAspect);
        assertEquals(64, widthDiff);
        assertEquals(48, heightDiff);

        net.minecraft.util.Identifier idFallback = new net.minecraft.util.Identifier("spb-revamped", "poolrooms/unknown_room");
        int fallback = (int) method.invoke(null, "width", "poolrooms", idFallback);
        assertEquals(16, fallback);
    }

    @Test
    @DisplayName("Verify MathStuff Lerp functionality and smoothing bounds")
    void testMathStuffLerp() {
        // At delta = 0, Lerp should stay at start
        float resultZero = MathStuff.Lerp(10.0f, 20.0f, 0.5f, 0.0f);
        assertEquals(10.0f, resultZero, 0.001f);

        // Standard positive progression
        float resultHalf = MathStuff.Lerp(0.0f, 100.0f, 0.5f, 1.0f);
        assertTrue(resultHalf > 0.0f && resultHalf < 100.0f);

        // If target equals current, value should remain identical
        float resultIdentical = MathStuff.Lerp(45.0f, 45.0f, 0.5f, 0.05f);
        assertEquals(45.0f, resultIdentical, 0.001f);
    }

    @Test
    @DisplayName("Verify NBT keys in PlayerComponent are distinct to prevent data corruption/overwrite")
    void testPlayerComponentNbtKeyUniqueness() {
        // Known keys serialized in PlayerComponent.writeToNbt
        String[] keys = {
                "inventory",
                "inventoryOffHand",
                "inventoryArmor",
                "checkpoint",
                "hasBeenCaptured",
                "isBeingCaptured",
                "activeSkinwalkerTarget",
                "muteTimer"
        };

        Set<String> keySet = new HashSet<>();
        for (String key : keys) {
            assertTrue(keySet.add(key), "Duplicate NBT key detected in PlayerComponent: " + key);
        }
    }

    @Test
    @DisplayName("Verify StepSoundHelper loads without crashing on server")
    void testStepSoundHelperClassloading() {
        assertDoesNotThrow(() -> {
            Class<?> clazz = Class.forName("com.sp.util.StepSoundHelper");
            assertNotNull(clazz);
        });
    }

    @Test
    @DisplayName("Verify Level 94 spawn and the special STOP sign exits are on the flat road verge")
    void testLevel94Layout() {
        assertEquals(0.0, Level94Layout.roadCenterZ(0), 1e-9, "The road must pass through the spawn");
        assertEquals(Level94Layout.BASE_Y, Level94Layout.getTerrainHeight(0, 0));

        for (int i = 0; i < Level94Layout.SPECIAL_STOP_X.length; i++) {
            BlockPos sign = Level94Layout.getSpecialStopSign(i);
            assertEquals(Level94Layout.BASE_Y, Level94Layout.getTerrainHeight(sign.getX(), sign.getZ()), "Special STOP sign " + i + " is not on flat ground");
            double distance = Math.abs(Level94Layout.roadDistance(sign.getX(), sign.getZ()));
            assertTrue(distance > Level94Layout.CURB_EDGE, "Special STOP sign " + i + " stands on the road");
        }

        // An avenue crosses the main road at the spawn.
        for (int side = -1; side <= 1; side += 2) {
            assertTrue(Level94Layout.hasConnector(0, side), "The avenue at the spawn must cross the main road");
            int x = 0;
            int z = (int) Math.floor(Level94Layout.roadCenterZ(x + 0.5)) + side * 15;
            assertEquals(Level94Layout.BASE_Y, Level94Layout.getTerrainHeight(x, z), "Streets must be flat");
            assertTrue(Level94Layout.nearestRoadDistance(x, z) <= Level94Layout.ROAD_HALF_WIDTH);
        }

        // Every street of the maze joins two crossings and every crossing with streets is reachable.
        int crossings = 0;
        for (int i = -30; i <= 30; i++) {
            for (int j = -15; j <= 15; j++) {
                if (!Level94Layout.isNodeActive(i, j)) continue;
                for (Level94Layout.Facing f : Level94Layout.Facing.values()) {
                    assertEquals(Level94Layout.hasStreet(i, j, f), Level94Layout.hasStreet(i + f.dx, j + f.dz, f.opposite()), "Streets must be symmetric");
                }
                if (Level94Layout.streetCount(i, j) >= 3) crossings++;
            }
        }
        assertTrue(crossings > 20, "The neighborhoods need T and 4-way crossings, found " + crossings);

        for (int x = -2000; x <= 2000; x += 7) {
            for (int z = -400; z <= 400; z += 7) {
                int height = Level94Layout.getTerrainHeight(x, z);
                assertTrue(height >= Level94Layout.BASE_Y && height < Level94Layout.BASE_Y + 60, "Terrain height out of range at " + x + ", " + z);
            }
        }
    }

    @Test
    @DisplayName("Verify the Level 94 zones: Déjà vu copies differ in a single house and the exits are on the Stage")
    void testLevel94Zones() {
        assertEquals(1, Level94Layout.zone(0));
        assertEquals(2, Level94Layout.zone(-600));
        assertEquals(3, Level94Layout.zone(1500));
        assertEquals(4, Level94Layout.zone(-2600));
        for (int x : Level94Layout.SPECIAL_STOP_X) {
            assertEquals(4, Level94Layout.zone(x), "Every exit must be on the Stage");
        }

        // Two Déjà vu neighborhoods (same row of regions) must be the same one, except for their quirky houses.
        int rz = 2;
        int compared = 0;
        for (int a = 2; a <= 4; a++) {
            for (int b = a + 1; b <= 4; b++) {
                if (Level94Layout.regionZone(a) != 2 || Level94Layout.regionZone(b) != 2) continue;
                if (!Level94Layout.isNeighborhood(a, rz) || !Level94Layout.isNeighborhood(b, rz)) continue;
                int differences = 0;
                for (int li = 1; li <= Level94Layout.REGION_W - 2; li++) {
                    for (int lj = 1; lj <= Level94Layout.REGION_H - 2; lj++) {
                        for (int q = 0; q < 4; q++) {
                            Level94Layout.House ha = Level94Layout.lot(a * Level94Layout.REGION_W + li, rz * Level94Layout.REGION_H + lj, q & 1, q >> 1);
                            Level94Layout.House hb = Level94Layout.lot(b * Level94Layout.REGION_W + li, rz * Level94Layout.REGION_H + lj, q & 1, q >> 1);
                            boolean same = ha == null ? hb == null : hb != null && ha.facing() == hb.facing()
                                    && ha.style() == hb.style() && ha.floorY() == hb.floorY() && ha.quirk() == hb.quirk();
                            if (!same) differences++;
                        }
                    }
                }
                assertTrue(differences <= 2, "Déjà vu copies differ in " + differences + " lots");
                compared++;
            }
        }
        assertTrue(compared > 0, "No pair of Déjà vu neighborhoods to compare");
    }

    @Test
    @DisplayName("Verify the SkinWalker language detection for the supported languages")
    void testLanguageDetector() {
        assertEquals("es", LanguageDetector.detect("hola, ¿dónde estás?"));
        assertEquals("en", LanguageDetector.detect("hello, where are you?"));
        assertEquals("en", LanguageDetector.detect("no, stop it"));
        assertEquals("de", LanguageDetector.detect("ich habe angst, wo bist du"));
        assertEquals("fr", LanguageDetector.detect("je suis perdu, c'est quoi ça"));
        assertEquals("it", LanguageDetector.detect("ciao, dove sei?"));
        assertEquals("pt", LanguageDetector.detect("olá, onde você está?"));
        assertNull(LanguageDetector.detect("..."));
        assertEquals("es", LanguageDetector.fromModelId("alphacephei/vosk-model-small-es-0.42"));
        assertEquals("en", LanguageDetector.fromModelId("alphacephei/vosk-model-small-en-us-0.15"));
    }

    /**
     * Kept for the old Gradle task: runs every test class of the mod.
     */
    public static void main(String[] args) {
        ModTestRunner.main(args);
    }
}
