package com.sp.entity.voice;

import com.sp.sounds.voicechat.BackroomsVoicechatPlugin;
import net.minecraft.util.math.random.Random;

import java.util.*;

public class VoiceGenerator {
    private final PhonemeExtractor extractor;
    private final PhonemeLibrary library;
    private final TextToPhoneme textToPhoneme;
    private final SpeechSynthesizer synthesizer;
    private final VoiceConversionEngine converter;
    private final LatencyCalculator latencyCalculator;
    private final Random random = Random.create();
    private int playerPitchBase = 200;

    public VoiceGenerator() {
        this.extractor = new PhonemeExtractor();
        this.library = new PhonemeLibrary();
        this.textToPhoneme = new TextToPhoneme();
        this.synthesizer = new SpeechSynthesizer(library);
        this.converter = new VoiceConversionEngine();
        this.latencyCalculator = new LatencyCalculator();
    }

    public void learnFromPlayerVoice(UUID playerUUID) {
        Vector<short[]> clips = BackroomsVoicechatPlugin.randomSpeakingList.get(playerUUID);
        if (clips == null || clips.isEmpty()) return;

        int totalPitch = 0;
        int pitchCount = 0;

        for (short[] clip : clips) {
            if (clip == null || clip.length < 200) continue;

            int clipPitch = extractor.estimatePitch(clip);
            totalPitch += clipPitch;
            pitchCount++;

            List<short[]> segments = extractor.extractPhonemes(clip);
            for (short[] segment : segments) {
                if (segment == null || segment.length < 50) continue;

                PhonemeExtractor.PhonemeData data = extractor.classifySegmentDetailed(segment);
                if (data.type.equals("silence")) continue;

                String phonemeKey = mapToLibraryKey(data);
                library.addPhoneme(phonemeKey, segment);
            }
        }

        if (pitchCount > 0) {
            playerPitchBase = totalPitch / pitchCount;
        }

        library.markInitialized();
    }

    private String mapToLibraryKey(PhonemeExtractor.PhonemeData data) {
        String type = data.type;
        int pitch = data.pitch;
        double energy = data.energy;

        if (type.equals("a") || type.equals("e") || type.equals("i") || type.equals("o") || type.equals("u")) {
            int index = (pitch / 30 + (int)(energy * 50)) % 3;
            return type + index;
        }

        int bucket = (pitch / 40 + (int)(energy * 30)) % 3;
        return type + bucket;
    }

    public short[] generateResponse(String text, float pitchBase) {
        if (!library.isInitialized() || library.getTotalVariants() < 5) {
            return new short[0];
        }

        List<String> phonemeSequence = textToPhoneme.convert(text);

        List<short[]> phonemeAudio = new ArrayList<>();
        for (String phoneme : phonemeSequence) {
            if (phoneme.equals("_pause")) {
                phonemeAudio.add(new short[SYNTHESIS_SAMPLE_RATE / 20]);
                continue;
            }

            short[] matchingClip = findBestMatchingClip(phoneme);
            if (matchingClip != null) {
                short[] adjusted = adjustToPlayerPitch(matchingClip);
                phonemeAudio.add(adjusted);
            } else {
                phonemeAudio.add(new short[SYNTHESIS_SAMPLE_RATE / 30]);
            }
        }

        return assembleWithTransitions(phonemeAudio);
    }

    private short[] findBestMatchingClip(String phoneme) {
        String[] variants = {phoneme + "0", phoneme + "1", phoneme + "2", phoneme};

        for (String variant : variants) {
            short[] clip = library.getPhoneme(variant);
            if (clip != null && clip.length > 0) {
                return clip;
            }
        }

        for (String key : library.getAvailablePhonemes()) {
            if (key.startsWith(phoneme.substring(0, Math.min(1, phoneme.length())))) {
                short[] clip = library.getPhoneme(key);
                if (clip != null && clip.length > 0) {
                    return clip;
                }
            }
        }

        return null;
    }

    private short[] adjustToPlayerPitch(short[] audio) {
        if (audio == null || audio.length == 0) return audio;

        int audioPitch = extractor.estimatePitch(audio);
        if (audioPitch <= 0) return audio;

        float semitones = 12.0f * (float)(Math.log((double) playerPitchBase / audioPitch) / Math.log(2));
        semitones = Math.max(-3.0f, Math.min(3.0f, semitones));

        if (Math.abs(semitones) < 0.3f) return audio;

        return converter.pitchShift(audio, semitones);
    }

    private short[] assembleWithTransitions(List<short[]> segments) {
        if (segments.isEmpty()) return new short[0];
        if (segments.size() == 1) return segments.get(0);

        int totalLength = 0;
        for (short[] seg : segments) {
            if (seg != null) totalLength += seg.length;
        }

        short[] result = new short[totalLength];
        int offset = 0;

        for (int i = 0; i < segments.size(); i++) {
            short[] seg = segments.get(i);
            if (seg == null || seg.length == 0) continue;

            if (i > 0 && offset > 0) {
                int fadeSize = Math.min(TRANSITION_SIZE, Math.min(seg.length, 200));
                applyFadeIn(seg, fadeSize);
            }

            System.arraycopy(seg, 0, result, offset, seg.length);
            offset += seg.length;
        }

        return converter.normalizeVolume(result);
    }

    private void applyFadeIn(short[] audio, int size) {
        for (int i = 0; i < size && i < audio.length; i++) {
            float t = (float) i / size;
            audio[i] = (short) (audio[i] * t);
        }
    }

    public short[] processClip(short[] clip, boolean isTrueForm) {
        if (clip == null || clip.length == 0) return clip;
        return clip;
    }

    public boolean isReady() {
        return library.isInitialized() && library.getTotalVariants() >= 5;
    }

    public PhonemeLibrary getLibrary() {
        return library;
    }

    public LatencyCalculator getLatencyCalculator() {
        return latencyCalculator;
    }

    public int getPlayerPitchBase() {
        return playerPitchBase;
    }

    public int estimatePitch(UUID playerUUID) {
        Vector<short[]> clips = BackroomsVoicechatPlugin.randomSpeakingList.get(playerUUID);
        if (clips == null || clips.isEmpty()) return 200;

        int totalPitch = 0;
        int count = 0;
        for (short[] clip : clips) {
            if (clip != null && clip.length > 100) {
                totalPitch += extractor.estimatePitch(clip);
                count++;
            }
        }
        return count > 0 ? totalPitch / count : 200;
    }

    private static final int SYNTHESIS_SAMPLE_RATE = 48000;
    private static final int TRANSITION_SIZE = 200;
}
