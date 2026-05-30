package com.sp.entity.voice;

import java.util.ArrayList;
import java.util.List;

public class PhonemeExtractor {
    private static final int SAMPLE_RATE = 48000;
    private static final int WINDOW_SIZE = 512;
    private static final double ENERGY_THRESHOLD = 0.005;
    private static final int MIN_SEGMENT_SIZE = 100;
    private static final int MAX_SEGMENT_SIZE = 8000;

    private static final String[] VOWEL_KEYS = {"a", "e", "i", "o", "u", "ae", "ao", "ah"};
    private static final String[] FRICATIVE_KEYS = {"f", "v", "s", "z", "sh", "zh", "h"};
    private static final String[] PLOSIVE_KEYS = {"p", "b", "t", "d", "k", "g"};
    private static final String[] NASAL_KEYS = {"m", "n", "ng"};
    private static final String[] LIQUID_KEYS = {"l", "r"};
    private static final String[] GLIDE_KEYS = {"w", "y"};

    public List<short[]> extractPhonemes(short[] audio) {
        List<short[]> segments = new ArrayList<>();
        if (audio == null || audio.length < MIN_SEGMENT_SIZE) return segments;

        double[] energy = calculateEnergy(audio);
        List<Integer> onsets = detectOnsets(energy);
        List<Integer> offsets = detectOffsets(energy, onsets);

        for (int i = 0; i < onsets.size() && i < offsets.size(); i++) {
            int start = onsets.get(i) * WINDOW_SIZE;
            int end = Math.min(offsets.get(i) * WINDOW_SIZE, audio.length);
            int segmentSize = end - start;

            if (segmentSize >= MIN_SEGMENT_SIZE && segmentSize <= MAX_SEGMENT_SIZE) {
                short[] segment = new short[segmentSize];
                System.arraycopy(audio, start, segment, 0, segmentSize);
                segments.add(segment);
            }
        }

        return segments;
    }

    public PhonemeData classifySegmentDetailed(short[] segment) {
        if (segment == null || segment.length < 10) {
            return new PhonemeData("silence", 200, 0, 0);
        }

        double rms = calculateRMS(segment);
        if (rms < 0.01) {
            return new PhonemeData("silence", 200, 0, rms);
        }

        int pitch = estimatePitch(segment);
        double zcr = calculateZeroCrossingRate(segment);
        double spectralCentroid = calculateSpectralCentroid(segment);
        double energy = rms;

        String type;
        if (isVocalic(zcr, rms, spectralCentroid)) {
            if (spectralCentroid < 1500) {
                type = selectFromList(VOWEL_KEYS, pitch, energy);
            } else {
                type = selectFromList(GLIDE_KEYS, pitch, energy);
            }
        } else if (isNasal(zcr, rms, spectralCentroid)) {
            type = selectFromList(NASAL_KEYS, pitch, energy);
        } else if (isFricative(zcr, spectralCentroid)) {
            type = selectFromList(FRICATIVE_KEYS, pitch, energy);
        } else if (isPlosive(zcr, rms, segment.length)) {
            type = selectFromList(PLOSIVE_KEYS, pitch, energy);
        } else {
            type = selectFromList(LIQUID_KEYS, pitch, energy);
        }

        return new PhonemeData(type, pitch, energy, rms);
    }

    public String classifySegment(short[] segment) {
        return classifySegmentDetailed(segment).type;
    }

    private double calculateRMS(short[] segment) {
        double sum = 0;
        for (short s : segment) {
            double sample = s / 32768.0;
            sum += sample * sample;
        }
        return Math.sqrt(sum / segment.length);
    }

    private double calculateZeroCrossingRate(short[] segment) {
        int crossings = 0;
        for (int i = 1; i < segment.length; i++) {
            if ((segment[i - 1] >= 0 && segment[i] < 0) || (segment[i - 1] < 0 && segment[i] >= 0)) {
                crossings++;
            }
        }
        return (double) crossings / segment.length;
    }

    private double calculateSpectralCentroid(short[] segment) {
        int n = Math.min(segment.length, WINDOW_SIZE * 2);
        if (n < WINDOW_SIZE) return 1000;

        double[] windowed = new double[n];
        for (int i = 0; i < n; i++) {
            double w = 0.54 - 0.46 * Math.cos(2.0 * Math.PI * i / (n - 1));
            windowed[i] = (segment[i] / 32768.0) * w;
        }

        double weightedSum = 0;
        double magnitudeSum = 0;

        for (int k = 1; k < n / 2; k++) {
            double real = 0, imag = 0;
            for (int i = 0; i < n; i++) {
                double angle = 2.0 * Math.PI * k * i / n;
                real += windowed[i] * Math.cos(angle);
                imag -= windowed[i] * Math.sin(angle);
            }
            double magnitude = Math.sqrt(real * real + imag * imag);
            double freq = (double) k * SAMPLE_RATE / n;
            weightedSum += freq * magnitude;
            magnitudeSum += magnitude;
        }

        return magnitudeSum > 0 ? weightedSum / magnitudeSum : 1000;
    }

    private boolean isVocalic(double zcr, double rms, double centroid) {
        return zcr < 0.08 && rms > 0.02 && centroid < 2000;
    }

    private boolean isNasal(double zcr, double rms, double centroid) {
        return zcr >= 0.04 && zcr < 0.12 && rms > 0.015 && centroid < 1800;
    }

    private boolean isFricative(double zcr, double centroid) {
        return zcr >= 0.12 || centroid > 3000;
    }

    private boolean isPlosive(double zcr, double rms, int length) {
        return zcr < 0.06 && rms > 0.01 && length < 2000;
    }

    private String selectFromList(String[] list, int pitch, double energy) {
        int index = (pitch / 20 + (int)(energy * 100)) % list.length;
        return list[Math.abs(index)];
    }

    public int estimatePitch(short[] audio) {
        if (audio == null || audio.length < 100) return 200;

        int minLag = SAMPLE_RATE / 600;
        int maxLag = SAMPLE_RATE / 50;
        int bestLag = minLag;
        double bestCorrelation = 0;

        for (int lag = minLag; lag <= maxLag; lag++) {
            double correlation = 0;
            int count = 0;
            for (int i = 0; i + lag < audio.length; i += 2) {
                correlation += (audio[i] / 32768.0) * (audio[i + lag] / 32768.0);
                count++;
            }
            if (count > 0) {
                correlation /= count;
                if (correlation > bestCorrelation) {
                    bestCorrelation = correlation;
                    bestLag = lag;
                }
            }
        }

        return bestLag > 0 ? SAMPLE_RATE / bestLag : 200;
    }

    private double[] calculateEnergy(short[] audio) {
        int numWindows = audio.length / WINDOW_SIZE;
        double[] energy = new double[numWindows];

        for (int i = 0; i < numWindows; i++) {
            double sum = 0;
            for (int j = 0; j < WINDOW_SIZE; j++) {
                int idx = i * WINDOW_SIZE + j;
                if (idx < audio.length) {
                    double sample = audio[idx] / 32768.0;
                    sum += sample * sample;
                }
            }
            energy[i] = sum / WINDOW_SIZE;
        }

        return energy;
    }

    private List<Integer> detectOnsets(double[] energy) {
        List<Integer> onsets = new ArrayList<>();
        boolean inSound = false;

        for (int i = 1; i < energy.length; i++) {
            if (energy[i] > ENERGY_THRESHOLD && !inSound) {
                onsets.add(Math.max(0, i - 2));
                inSound = true;
            } else if (energy[i] < ENERGY_THRESHOLD * 0.5 && inSound) {
                inSound = false;
            }
        }

        return onsets;
    }

    private List<Integer> detectOffsets(double[] energy, List<Integer> onsets) {
        List<Integer> offsets = new ArrayList<>();
        boolean inSound = false;

        for (int i = 1; i < energy.length; i++) {
            if (energy[i] > ENERGY_THRESHOLD && !inSound) {
                inSound = true;
            } else if (energy[i] < ENERGY_THRESHOLD * 0.3 && inSound) {
                offsets.add(i + 2);
                inSound = false;
            }
        }

        if (inSound) {
            offsets.add(energy.length - 1);
        }

        while (offsets.size() < onsets.size()) {
            offsets.add(energy.length - 1);
        }

        return offsets;
    }

    public static class PhonemeData {
        public final String type;
        public final int pitch;
        public final double energy;
        public final double rms;

        public PhonemeData(String type, int pitch, double energy, double rms) {
            this.type = type;
            this.pitch = pitch;
            this.energy = energy;
            this.rms = rms;
        }
    }
}
