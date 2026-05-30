package com.sp.entity.voice;

public class LatencyCalculator {
    private static final int MIN_LATENCY_MS = 100;
    private static final int MAX_LATENCY_MS = 8000;
    private static final int SIMPLE_COMPLEXITY_THRESHOLD = 25;
    private static final int MEDIUM_COMPLEXITY_THRESHOLD = 60;

    public int calculateLatency(String input, boolean isTrueForm, int repetitionCount) {
        int complexity = scoreComplexity(input);
        int latency = mapComplexityToLatency(complexity);

        if (isTrueForm) {
            latency = (int) (latency * 0.7);
        }

        latency += repetitionCount * 500;

        latency = Math.max(MIN_LATENCY_MS, Math.min(MAX_LATENCY_MS, latency));

        double jitter = 0.85 + Math.random() * 0.3;
        latency = (int) (latency * jitter);

        return Math.max(MIN_LATENCY_MS, Math.min(MAX_LATENCY_MS, latency));
    }

    public int calculateLatencyForClip(boolean isTrueForm) {
        int base = 500 + (int) (Math.random() * 3000);
        if (isTrueForm) {
            base = (int) (base * 0.7);
        }
        double jitter = 0.85 + Math.random() * 0.3;
        int latency = (int) (base * jitter);
        return Math.max(MIN_LATENCY_MS, Math.min(MAX_LATENCY_MS, latency));
    }

    private int scoreComplexity(String input) {
        if (input == null || input.isEmpty()) return 0;

        String lower = input.toLowerCase();

        if (lower.matches(".*\\b(hola|hello|hey|hi|oye)\\b.*")) return 10;
        if (lower.matches(".*\\b(quien|eres|que|qué|como|quién)\\b.*")) return 30;
        if (lower.matches(".*\\b(donde|dónde|esta|está|aqui|aquí)\\b.*")) return 40;
        if (lower.matches(".*\\b(porque|por qué|razon|razón)\\b.*")) return 55;
        if (lower.matches(".*\\b(ayuda|miedo|socorro|temor|salvame|salvame|escape)\\b.*")) return 70;
        if (lower.matches(".*\\b(muerte|morir|muerto|kill|die|matar)\\b.*")) return 85;

        int wordCount = lower.split("\\s+").length;
        if (wordCount <= 2) return 20;
        if (wordCount <= 5) return 40;
        return 60;
    }

    private int mapComplexityToLatency(int complexity) {
        if (complexity <= SIMPLE_COMPLEXITY_THRESHOLD) {
            return 100 + (complexity * 16);
        } else if (complexity <= MEDIUM_COMPLEXITY_THRESHOLD) {
            return 500 + (complexity - SIMPLE_COMPLEXITY_THRESHOLD) * 43;
        } else {
            return 2000 + (complexity - MEDIUM_COMPLEXITY_THRESHOLD) * 150;
        }
    }
}
