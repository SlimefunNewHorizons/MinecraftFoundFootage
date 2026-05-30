package com.sp.entity.voice;

import net.minecraft.util.math.random.Random;

import java.util.List;

public class SpeechSynthesizer {
    private static final int SAMPLE_RATE = 48000;
    private static final int PAUSE_SAMPLES = SAMPLE_RATE / 15;
    private static final int CROSSFADE_SIZE = 300;
    private final PhonemeLibrary library;
    private final VoiceConversionEngine converter;
    private final PhonemeExtractor extractor;
    private final Random random = Random.create();

    public SpeechSynthesizer(PhonemeLibrary library) {
        this.library = library;
        this.converter = new VoiceConversionEngine();
        this.extractor = new PhonemeExtractor();
    }

    public short[] synthesize(List<String> phonemes, float pitchBase) {
        if (phonemes == null || phonemes.isEmpty()) {
            return new short[0];
        }

        short[][] segments = new short[phonemes.size()][];

        for (int i = 0; i < phonemes.size(); i++) {
            String phoneme = phonemes.get(i);
            if (phoneme.equals("_pause")) {
                segments[i] = generateSmoothPause();
                continue;
            }

            short[] variant = findBestVariant(phoneme, pitchBase);
            if (variant != null && variant.length > 0) {
                segments[i] = normalizeSegment(variant, pitchBase);
            } else {
                segments[i] = generateSmoothPause();
            }
        }

        return assembleSegments(segments);
    }

    private short[] findBestVariant(String phoneme, float pitchBase) {
        String[] suffixes = {"0", "1", "2", ""};
        short[] best = null;
        float bestScore = Float.MAX_VALUE;

        for (String suffix : suffixes) {
            short[] candidate = library.getPhoneme(phoneme + suffix);
            if (candidate == null || candidate.length == 0) continue;

            float candidatePitch = extractor.estimatePitch(candidate);
            float pitchDiff = Math.abs(candidatePitch - pitchBase * 440);
            float score = pitchDiff + random.nextFloat() * 20;

            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }

    private short[] normalizeSegment(short[] audio, float pitchBase) {
        if (audio == null || audio.length == 0) return audio;

        short[] result = converter.normalizeVolume(audio);

        float audioPitch = extractor.estimatePitch(result);
        if (audioPitch > 0 && pitchBase > 0) {
            float targetPitch = pitchBase * 440;
            float semitones = 12.0f * (float)(Math.log(targetPitch / audioPitch) / Math.log(2));
            semitones = Math.max(-2.0f, Math.min(2.0f, semitones));

            if (Math.abs(semitones) > 0.2f) {
                result = converter.pitchShift(result, semitones);
            }
        }

        float timeVar = 0.92f + random.nextFloat() * 0.16f;
        if (Math.abs(timeVar - 1.0f) > 0.02f) {
            result = converter.timeStretch(result, timeVar);
        }

        return result;
    }

    private short[] generateSmoothPause() {
        int length = PAUSE_SAMPLES / 2 + random.nextInt(PAUSE_SAMPLES);
        short[] pause = new short[length];
        return pause;
    }

    private short[] assembleSegments(short[][] segments) {
        int totalLength = 0;
        for (short[] seg : segments) {
            if (seg != null) totalLength += seg.length;
        }

        if (totalLength == 0) return new short[0];

        short[] result = new short[totalLength];
        int offset = 0;

        for (int i = 0; i < segments.length; i++) {
            short[] seg = segments[i];
            if (seg == null || seg.length == 0) continue;

            if (i > 0 && offset > 0) {
                int fadeSize = Math.min(CROSSFADE_SIZE, Math.min(seg.length, offset));
                if (fadeSize > 0) {
                    for (int j = 0; j < fadeSize; j++) {
                        float t = (float) j / fadeSize;
                        int resultIdx = offset - fadeSize + j;
                        if (resultIdx >= 0 && resultIdx < result.length && j < seg.length) {
                            result[resultIdx] = (short)(result[resultIdx] * (1 - t) + seg[j] * t);
                        }
                    }
                    int remaining = seg.length - fadeSize;
                    if (remaining > 0) {
                        System.arraycopy(seg, fadeSize, result, offset, remaining);
                        offset += remaining;
                    }
                } else {
                    System.arraycopy(seg, 0, result, offset, seg.length);
                    offset += seg.length;
                }
            } else {
                System.arraycopy(seg, 0, result, offset, seg.length);
                offset += seg.length;
            }
        }

        return result;
    }
}
