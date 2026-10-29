package com.sp;

import com.sp.util.TickScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the scheduler that replaced the threads running game logic.
 */
class TickSchedulerTest {
    private final TickScheduler scheduler = TickScheduler.SERVER;

    @Test
    @DisplayName("Tasks run after exactly their delay in ticks, in order")
    void runsAfterDelay() {
        scheduler.clear();
        List<String> ran = new ArrayList<>();
        scheduler.schedule(0, () -> ran.add("now"));
        scheduler.schedule(2, () -> ran.add("later"));
        scheduler.schedule(-5, () -> ran.add("negative"));

        scheduler.tick();
        assertEquals(List.of("now", "negative"), ran);
        scheduler.tick();
        assertEquals(2, ran.size());
        scheduler.tick();
        assertEquals(List.of("now", "negative", "later"), ran);
        scheduler.tick();
        assertEquals(3, ran.size(), "A task ran twice");
    }

    @Test
    @DisplayName("A task scheduled by another task runs on a later tick, not the same one")
    void schedulingFromATask() {
        scheduler.clear();
        List<Integer> ran = new ArrayList<>();
        int[] tick = {0};
        scheduler.schedule(0, () -> scheduler.schedule(0, () -> ran.add(tick[0])));

        tick[0] = 1;
        scheduler.tick();
        assertTrue(ran.isEmpty(), "The nested task ran during the same tick");
        tick[0] = 2;
        scheduler.tick();
        assertEquals(List.of(2), ran);
    }

    @Test
    @DisplayName("A failing task does not break the scheduler")
    void failingTask() {
        scheduler.clear();
        List<String> ran = new ArrayList<>();
        scheduler.schedule(0, () -> {
            throw new IllegalStateException("boom");
        });
        scheduler.schedule(1, () -> ran.add("after"));

        assertDoesNotThrow(scheduler::tick);
        scheduler.tick();
        assertEquals(List.of("after"), ran);
    }

    @Test
    @DisplayName("clear() drops every pending task")
    void clearDropsTasks() {
        scheduler.clear();
        List<String> ran = new ArrayList<>();
        scheduler.schedule(1, () -> ran.add("x"));
        scheduler.clear();
        scheduler.tick();
        scheduler.tick();
        assertTrue(ran.isEmpty());
    }
}
