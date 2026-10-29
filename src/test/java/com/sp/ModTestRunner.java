package com.sp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Runs every {@link Test} method of the mod's test classes and exits with code 1 if any fails.
 * <p>
 * Gradle's JUnit runner is disabled for this project ({@code test} only runs {@code runIntegrityCheck}), so this
 * runner finds the test methods itself: new test methods in these classes are picked up automatically.
 */
public final class ModTestRunner {
    private static final List<Class<?>> TEST_CLASSES = List.of(
            IntegrityTest.class,
            Level94LayoutTest.class,
            Level94BlueprintTest.class,
            LanguageDetectorTest.class,
            TickSchedulerTest.class,
            ResourcesTest.class
    );

    private ModTestRunner() {}

    public static void main(String[] args) {
        System.out.println("=== Running MFF Automated Tests ===");
        int passed = 0;
        int total = 0;

        for (Class<?> testClass : TEST_CLASSES) {
            System.out.println("--- " + testClass.getSimpleName() + " ---");
            Method[] methods = testClass.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::getName));

            for (Method method : methods) {
                if (!method.isAnnotationPresent(Test.class)) continue;
                total++;

                String name = method.getName();
                DisplayName displayName = method.getAnnotation(DisplayName.class);
                long start = System.nanoTime();
                try {
                    Constructor<?> constructor = testClass.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    Object instance = constructor.newInstance();
                    method.setAccessible(true);
                    method.invoke(instance);
                    passed++;
                    System.out.printf(" [PASS] %s (%d ms)%n", name, (System.nanoTime() - start) / 1_000_000);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    System.err.println(" [FAIL] " + name + (displayName != null ? " - " + displayName.value() : ""));
                    System.err.println("        " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                    if (!(cause instanceof AssertionError)) {
                        cause.printStackTrace();
                    }
                } catch (ReflectiveOperationException e) {
                    System.err.println(" [FAIL] " + name + ": could not run the test: " + e);
                }
            }
        }

        System.out.println("=== Result: " + passed + "/" + total + " tests passed ===");
        if (passed != total) {
            System.exit(1);
        }
    }
}
