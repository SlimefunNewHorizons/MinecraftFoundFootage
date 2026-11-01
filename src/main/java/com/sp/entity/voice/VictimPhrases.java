package com.sp.entity.voice;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Short phrases a player really said, with their transcription, so the SkinWalker can answer with the victim's own
 * voice instead of the robotic one. Filled from the speech-to-text thread, read from the server thread.
 */
public class VictimPhrases {
    private record Phrase(short[] audio, String text, Set<String> words) {}

    /** A real recording of the player with its transcription, used to clone their voice. */
    public record Reference(short[] audio, String text) {}

    //*48 kHz voice chat audio. Long references slow down every cloned phrase, short ones lose the voice
    private static final int SAMPLE_RATE = 48000;
    private static final int MIN_REFERENCE_SAMPLES = SAMPLE_RATE;
    private static final int MAX_REFERENCE_SAMPLES = SAMPLE_RATE * 6;

    private static final int MAX_PHRASES = 20;
    //*Long rambles make bad answers
    private static final int MAX_WORDS = 6;
    //*Share of the answer's words the phrase must contain
    private static final double MIN_MATCH = 0.6;

    private static final Map<UUID, List<Phrase>> PHRASES = new ConcurrentHashMap<>();

    public static void add(UUID player, short[] audio, String text) {
        Set<String> words = words(text);
        if (words.isEmpty() || words.size() > MAX_WORDS) {
            return;
        }

        List<Phrase> phrases = PHRASES.computeIfAbsent(player, uuid -> new CopyOnWriteArrayList<>());
        if (phrases.size() >= MAX_PHRASES) {
            phrases.remove(0);
        }
        phrases.add(new Phrase(audio, text, words));
    }

    /**
     * @return the longest phrase of the player that is still a good cloning reference (1 to 6 seconds), or null.
     */
    public static Reference reference(UUID player) {
        List<Phrase> phrases = player == null ? null : PHRASES.get(player);
        if (phrases == null) {
            return null;
        }

        Phrase best = null;
        for (Phrase phrase : phrases) {
            int length = phrase.audio().length;
            if (length >= MIN_REFERENCE_SAMPLES && length <= MAX_REFERENCE_SAMPLES
                    && (best == null || length > best.audio().length)) {
                best = phrase;
            }
        }
        return best == null ? null : new Reference(best.audio(), best.text());
    }

    /**
     * @return the recording of the player that best says the given answer, or null if none is close enough.
     */
    public static short[] find(UUID player, String answer) {
        List<Phrase> phrases = player == null ? null : PHRASES.get(player);
        if (phrases == null || phrases.isEmpty()) {
            return null;
        }

        Set<String> answerWords = words(answer);
        Phrase best = null;
        double bestScore = 0;
        for (Phrase phrase : phrases) {
            double score = score(phrase.words(), answerWords);
            //*On a tie the shorter phrase wins: fewer words that have nothing to do with the answer
            if (score > bestScore || (score == bestScore && best != null && phrase.words().size() < best.words().size())) {
                best = phrase;
                bestScore = score;
            }
        }

        return bestScore >= MIN_MATCH ? best.audio() : null;
    }

    public static void forget(UUID player) {
        PHRASES.remove(player);
    }

    /**
     * Share of the answer's words that the phrase contains, from 0 to 1.
     */
    static double score(Set<String> phraseWords, Set<String> answerWords) {
        if (answerWords.isEmpty()) {
            return 0;
        }

        int shared = 0;
        for (String word : answerWords) {
            if (phraseWords.contains(word)) {
                shared++;
            }
        }
        return (double) shared / answerWords.size();
    }

    /**
     * Lower case words without accents or punctuation, so "¿Dónde estás?" and "donde estas" match.
     */
    static Set<String> words(String text) {
        Set<String> words = new HashSet<>();
        if (text == null) {
            return words;
        }

        String plain = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        for (String word : plain.split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return words;
    }
}
