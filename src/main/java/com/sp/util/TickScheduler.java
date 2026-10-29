package com.sp.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Runs delayed tasks on the game thread, counted in game ticks.
 * <p>
 * Replaces the {@code ScheduledExecutorService}s that used to run game logic (component syncs, packets,
 * player abilities) on a separate thread with a wall-clock delay. Those caused race conditions, kept counting
 * while the game was paused and created a new thread per call.
 * <p>
 * {@link #SERVER} is ticked by the server, {@link #CLIENT} by the client. Only schedule from the matching thread.
 */
public class TickScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger("spb-revamped");

    public static final TickScheduler SERVER = new TickScheduler();
    public static final TickScheduler CLIENT = new TickScheduler();

    private final List<Task> tasks = new ArrayList<>();
    private final List<Task> pending = new ArrayList<>();
    private boolean ticking = false;

    private TickScheduler() {}

    public void schedule(int delayTicks, Runnable runnable) {
        Task task = new Task(Math.max(0, delayTicks), runnable);
        if (this.ticking) {
            this.pending.add(task);
        } else {
            this.tasks.add(task);
        }
    }

    public void tick() {
        this.ticking = true;
        try {
            Iterator<Task> iterator = this.tasks.iterator();
            while (iterator.hasNext()) {
                Task task = iterator.next();
                if (task.remaining-- <= 0) {
                    iterator.remove();
                    try {
                        task.runnable.run();
                    } catch (RuntimeException e) {
                        // One broken task must not take the whole game tick (and every other task) down with it.
                        LOGGER.error("Scheduled task failed", e);
                    }
                }
            }
        } finally {
            this.ticking = false;
            this.tasks.addAll(this.pending);
            this.pending.clear();
        }
    }

    public void clear() {
        this.tasks.clear();
        this.pending.clear();
    }

    private static final class Task {
        private int remaining;
        private final Runnable runnable;

        private Task(int remaining, Runnable runnable) {
            this.remaining = remaining;
            this.runnable = runnable;
        }
    }
}
