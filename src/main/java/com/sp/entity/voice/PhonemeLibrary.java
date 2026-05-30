package com.sp.entity.voice;

import net.minecraft.util.math.random.Random;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PhonemeLibrary {
    private final Map<String, List<short[]>> phonemes = new ConcurrentHashMap<>();
    private final Map<String, int[]> phonemeStats = new ConcurrentHashMap<>();
    private final Random random = Random.create();
    private volatile boolean initialized = false;

    public void addPhoneme(String phoneme, short[] audio) {
        phonemes.computeIfAbsent(phoneme, k -> Collections.synchronizedList(new ArrayList<>())).add(audio);
    }

    public short[] getPhoneme(String phoneme) {
        List<short[]> variants = phonemes.get(phoneme);
        if (variants == null || variants.isEmpty()) return null;
        return variants.get(random.nextBetween(0, variants.size() - 1));
    }

    public short[] getPhonemeVariant(String phoneme, int index) {
        List<short[]> variants = phonemes.get(phoneme);
        if (variants == null || variants.isEmpty()) return null;
        return variants.get(Math.min(index, variants.size() - 1));
    }

    public boolean hasPhoneme(String phoneme) {
        return phonemes.containsKey(phoneme) && !phonemes.get(phoneme).isEmpty();
    }

    public int getVariantCount(String phoneme) {
        List<short[]> variants = phonemes.get(phoneme);
        return variants != null ? variants.size() : 0;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void markInitialized() {
        this.initialized = true;
    }

    public int getTotalPhonemes() {
        return phonemes.size();
    }

    public int getTotalVariants() {
        return phonemes.values().stream().mapToInt(List::size).sum();
    }

    public Set<String> getAvailablePhonemes() {
        return phonemes.keySet();
    }

    public void clear() {
        phonemes.clear();
        phonemeStats.clear();
        initialized = false;
    }
}
