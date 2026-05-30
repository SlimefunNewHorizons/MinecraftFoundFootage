package com.sp.entity.voice;

public class VoiceConversionEngine {
    private static final int SAMPLE_RATE = 48000;

    public short[] pitchShift(short[] data, float semitones) {
        if (data == null || data.length == 0) return data;
        float factor = (float) Math.pow(2.0, semitones / 12.0);
        int newLength = (int) (data.length / factor);
        short[] result = new short[newLength];

        for (int i = 0; i < newLength; i++) {
            float srcPos = i * factor;
            int srcIndex = (int) srcPos;
            float frac = srcPos - srcIndex;

            if (srcIndex + 1 < data.length) {
                result[i] = (short) (data[srcIndex] * (1 - frac) + data[srcIndex + 1] * frac);
            } else if (srcIndex < data.length) {
                result[i] = data[srcIndex];
            }
        }

        return result;
    }

    public short[] timeStretch(short[] data, float factor) {
        if (data == null || data.length == 0) return data;
        int newLength = (int) (data.length * factor);
        short[] result = new short[newLength];

        for (int i = 0; i < newLength; i++) {
            float srcPos = i / factor;
            int srcIndex = (int) srcPos;
            float frac = srcPos - srcIndex;

            if (srcIndex + 1 < data.length) {
                result[i] = (short) (data[srcIndex] * (1 - frac) + data[srcIndex + 1] * frac);
            } else if (srcIndex < data.length) {
                result[i] = data[srcIndex];
            }
        }

        return result;
    }

    public short[] reverseSegment(short[] data, float startPercent, float endPercent) {
        if (data == null || data.length == 0) return data;
        short[] result = new short[data.length];
        System.arraycopy(data, 0, result, 0, data.length);

        int segStart = (int) (data.length * startPercent);
        int segEnd = (int) (data.length * endPercent);
        segStart = Math.max(0, Math.min(segStart, data.length));
        segEnd = Math.max(segStart, Math.min(segEnd, data.length));

        for (int i = 0; i < (segEnd - segStart) / 2; i++) {
            short temp = result[segStart + i];
            result[segStart + i] = result[segEnd - 1 - i];
            result[segEnd - 1 - i] = temp;
        }

        return result;
    }

    public short[] stutter(short[] data, int stutterSize, int stutterInterval) {
        if (data == null || data.length == 0) return data;
        short[] result = new short[data.length];
        System.arraycopy(data, 0, result, 0, data.length);

        for (int i = 0; i < data.length; i += stutterInterval) {
            for (int j = 0; j < stutterSize && (i + j) < data.length; j++) {
                result[i + j] = data[i];
            }
        }

        return result;
    }

    public short[] addStaticNoise(short[] data, float intensity) {
        if (data == null || data.length == 0 || intensity <= 0) return data;
        short[] result = new short[data.length];
        System.arraycopy(data, 0, result, 0, data.length);

        java.util.Random rand = new java.util.Random();
        int noiseLevel = (int) (intensity * 3000);
        if (noiseLevel <= 0) return result;

        for (int i = 0; i < result.length; i++) {
            if (rand.nextDouble() < intensity * 0.1) {
                result[i] = (short) Math.max(-32768, Math.min(32767, result[i] + rand.nextInt(noiseLevel * 2) - noiseLevel));
            }
        }

        return result;
    }

    public short[] normalizeVolume(short[] data) {
        if (data == null || data.length == 0) return data;
        double max = 0;
        for (short s : data) {
            double abs = Math.abs(s / 32768.0);
            if (abs > max) max = abs;
        }
        if (max < 0.001) return data;

        double gain = 0.8 / max;
        short[] result = new short[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (short) Math.max(-32768, Math.min(32767, (int) (data[i] * gain)));
        }
        return result;
    }

    public short[] crossfade(short[] a, short[] b, int fadeSize) {
        if (a == null || a.length == 0) return b;
        if (b == null || b.length == 0) return a;

        fadeSize = Math.min(fadeSize, Math.min(a.length, b.length));
        int totalLength = a.length + b.length - fadeSize;
        short[] result = new short[totalLength];

        System.arraycopy(a, 0, result, 0, a.length);

        for (int i = 0; i < fadeSize && i < b.length && (a.length - fadeSize + i) < result.length; i++) {
            float t = (float) i / fadeSize;
            int aIdx = a.length - fadeSize + i;
            if (aIdx >= 0 && aIdx < a.length) {
                result[a.length - fadeSize + i] = (short) (a[aIdx] * (1 - t) + b[i] * t);
            }
        }

        int copyStart = a.length - fadeSize + Math.min(fadeSize, b.length);
        if (fadeSize < b.length) {
            System.arraycopy(b, fadeSize, result, a.length, b.length - fadeSize);
        }

        return result;
    }
}
