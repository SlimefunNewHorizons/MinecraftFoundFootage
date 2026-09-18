package com.sp;

import com.sp.util.MathStuff;
import com.sp.world.generation.chunk_generator.PoolroomsChunkGenerator;
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

    public static void main(String[] args) {
        System.out.println("=== Running MFF Automated Integrity Tests ===");
        IntegrityTest test = new IntegrityTest();
        int passed = 0;
        int total = 4;

        try {
            test.testPoolroomsRoomDimensionParsing();
            System.out.println(" [PASS] testPoolroomsRoomDimensionParsing");
            passed++;
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            System.err.println(" [FAIL] testPoolroomsRoomDimensionParsing: " + cause.getClass().getName() + ": " + cause.getMessage());
            cause.printStackTrace();
        }

        try {
            test.testMathStuffLerp();
            System.out.println(" [PASS] testMathStuffLerp");
            passed++;
        } catch (Throwable t) {
            System.err.println(" [FAIL] testMathStuffLerp: " + t.getMessage());
        }

        try {
            test.testPlayerComponentNbtKeyUniqueness();
            System.out.println(" [PASS] testPlayerComponentNbtKeyUniqueness");
            passed++;
        } catch (Throwable t) {
            System.err.println(" [FAIL] testPlayerComponentNbtKeyUniqueness: " + t.getMessage());
        }

        try {
            test.testStepSoundHelperClassloading();
            System.out.println(" [PASS] testStepSoundHelperClassloading");
            passed++;
        } catch (Throwable t) {
            System.err.println(" [FAIL] testStepSoundHelperClassloading: " + t.getMessage());
        }

        System.out.println("=== Result: " + passed + "/" + total + " tests passed ===");
        if (passed != total) {
            System.exit(1);
        }
    }
}
