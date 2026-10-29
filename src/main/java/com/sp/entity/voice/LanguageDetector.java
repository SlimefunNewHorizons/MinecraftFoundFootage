package com.sp.entity.voice;

import java.util.*;

/**
 * Small word-scoring language detector for the languages the SkinWalker can answer in
 * (es, en, de, fr, it, pt).
 * <p>
 * Every language gets a point for each of its common words found in the text, plus a bonus for characters only
 * that language uses. Words shared by several languages (e.g. "no", "si") are left out on purpose, the old
 * regex-based detection matched those first and answered most English or Portuguese messages in Spanish.
 */
public final class LanguageDetector {
    private static final Map<String, Set<String>> WORDS = new LinkedHashMap<>();

    static {
        WORDS.put("es", Set.of("hola", "que", "qué", "como", "cómo", "donde", "dónde", "quien", "quién", "porque", "por",
                "eres", "estas", "estás", "esta", "está", "aqui", "aquí", "gracias", "ayuda", "buenas", "tengo", "miedo",
                "vamos", "ven", "oye", "yo", "el", "los", "las", "una", "muy", "pero", "también", "nada", "sí", "mira",
                "ahí", "dime", "hay", "soy", "estoy", "puedes", "quieres", "corre", "vete", "sal", "de", "en", "lo"));
        WORDS.put("en", Set.of("hello", "hi", "hey", "what", "how", "where", "who", "why", "you", "are", "is", "the",
                "this", "that", "help", "thanks", "please", "yes", "here", "there", "my", "your", "i'm", "i", "it",
                "can", "do", "don't", "get", "go", "run", "stop", "and", "with", "be", "am", "we", "they", "have"));
        WORDS.put("de", Set.of("hallo", "wie", "wo", "wer", "was", "warum", "ich", "du", "bist", "ist", "nicht", "ja",
                "nein", "danke", "bitte", "hilfe", "komm", "hier", "und", "der", "die", "das", "ein", "eine", "mir",
                "dich", "mich", "geh", "weg", "angst", "habe", "hast", "sind", "auch"));
        WORDS.put("fr", Set.of("bonjour", "salut", "comment", "pourquoi", "où", "qui", "je", "es", "est", "vous", "oui",
                "non", "merci", "aide", "ici", "le", "les", "un", "une", "pas", "ça", "moi", "toi", "viens", "suis",
                "avec", "peur", "quoi", "c'est", "et", "mais", "très"));
        WORDS.put("it", Set.of("ciao", "come", "dove", "chi", "cosa", "perché", "perche", "sei", "sono", "io", "grazie",
                "aiuto", "qui", "il", "gli", "non", "sì", "vieni", "andiamo", "bene", "ancora", "paura", "anche", "molto",
                "che", "questo", "quello"));
        WORDS.put("pt", Set.of("olá", "ola", "oi", "como", "onde", "quem", "você", "voce", "eu", "obrigado", "obrigada",
                "ajuda", "aqui", "não", "nao", "sim", "tudo", "bem", "vem", "está", "estou", "isso", "medo", "também",
                "muito", "uma", "um", "que", "é"));
    }

    private LanguageDetector() {}

    /**
     * @return the detected language code, or null if no known word was found.
     */
    public static String detect(String text) {
        if (text == null || text.isBlank()) return null;

        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> tokens = tokenize(lower);
        Map<String, Integer> scores = new LinkedHashMap<>();

        for (Map.Entry<String, Set<String>> entry : WORDS.entrySet()) {
            int score = 0;
            for (String token : tokens) {
                if (entry.getValue().contains(token)) score++;
            }
            scores.put(entry.getKey(), score);
        }

        if (lower.matches(".*[ñ¿¡].*")) scores.merge("es", 2, Integer::sum);
        if (lower.matches(".*[ßäöü].*")) scores.merge("de", 2, Integer::sum);
        if (lower.matches(".*[çœêèëîûù].*")) scores.merge("fr", 1, Integer::sum);
        if (lower.matches(".*[ãõ].*")) scores.merge("pt", 2, Integer::sum);
        if (lower.matches(".*[òì].*")) scores.merge("it", 1, Integer::sum);

        String best = null;
        int bestScore = 0;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > bestScore) {
                best = entry.getKey();
                bestScore = entry.getValue();
            }
        }
        return best;
    }

    /**
     * Splits a text into lower case words. Apostrophes stay inside words ("c'est", "don't").
     */
    public static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}']+")) {
            if (!token.isEmpty()) tokens.add(token);
        }
        return tokens;
    }

    /**
     * Language code of a Vosk model id such as "alphacephei/vosk-model-small-es-0.42", or null if unknown.
     * Vosk models only understand one language, so this is the language of everything they transcribe.
     */
    public static String fromModelId(String modelId) {
        if (modelId == null) return null;
        for (String part : modelId.toLowerCase(Locale.ROOT).split("[-_/]")) {
            if (part.equals("en") || part.equals("us")) return "en";
            if (WORDS.containsKey(part)) return part;
        }
        return null;
    }
}
