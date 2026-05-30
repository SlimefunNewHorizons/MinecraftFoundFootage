package com.sp.entity.voice;

import org.jetbrains.annotations.Nullable;
import java.util.LinkedList;

public class ResponseQueue {
    private static final int COOLDOWN_TICKS = 100;
    private final LinkedList<PendingResponse> queue = new LinkedList<>();
    private volatile int cooldownTicks = 0;

    public void enqueue(short[] audio, int delayTicks) {
        synchronized (queue) {
            queue.clear();
            queue.add(new PendingResponse(audio, delayTicks));
            cooldownTicks = 0;
        }
    }

    @Nullable
    public short[] poll() {
        synchronized (queue) {
            if (cooldownTicks > 0) {
                cooldownTicks--;
                return null;
            }

            if (queue.isEmpty()) return null;

            PendingResponse response = queue.peek();
            if (response.delayTicks > 0) {
                response.delayTicks--;
                return null;
            }

            queue.poll();
            cooldownTicks = COOLDOWN_TICKS;
            return response.audio;
        }
    }

    public boolean hasPending() {
        synchronized (queue) {
            return !queue.isEmpty();
        }
    }

    public void clear() {
        synchronized (queue) {
            queue.clear();
            cooldownTicks = 0;
        }
    }

    public boolean isOnCooldown() {
        return cooldownTicks > 0;
    }

    public int getCooldownRemaining() {
        return cooldownTicks;
    }

    private static class PendingResponse {
        final short[] audio;
        int delayTicks;

        PendingResponse(short[] audio, int delayTicks) {
            this.audio = audio;
            this.delayTicks = delayTicks;
        }
    }
}
