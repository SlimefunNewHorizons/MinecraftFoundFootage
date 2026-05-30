package com.sp.entity.voice;

import java.util.*;

public class TextToPhoneme {
    private static final Map<String, String[]> DICTIONARY = new HashMap<>();
    private static final Map<Character, String> FALLBACK = new HashMap<>();
    private static final Set<String> PUNCTUATION = new HashSet<>();

    static {
        DICTIONARY.put("hola", new String[]{"o", "l", "a"});
        DICTIONARY.put("hello", new String[]{"e", "l", "o"});
        DICTIONARY.put("adiós", new String[]{"a", "d", "i", "o", "s"});
        DICTIONARY.put("bye", new String[]{"b", "a", "i"});
        DICTIONARY.put("si", new String[]{"s", "i"});
        DICTIONARY.put("no", new String[]{"n", "o"});
        DICTIONARY.put("quien", new String[]{"k", "i", "e", "n"});
        DICTIONARY.put("eres", new String[]{"e", "r", "e", "s"});
        DICTIONARY.put("soy", new String[]{"s", "o", "i"});
        DICTIONARY.put("tu", new String[]{"t", "u"});
        DICTIONARY.put("el", new String[]{"e", "l"});
        DICTIONARY.put("la", new String[]{"l", "a"});
        DICTIONARY.put("donde", new String[]{"d", "o", "n", "d", "e"});
        DICTIONARY.put("estas", new String[]{"e", "s", "t", "a", "s"});
        DICTIONARY.put("aqui", new String[]{"a", "k", "i"});
        DICTIONARY.put("alla", new String[]{"a", "y", "a"});
        DICTIONARY.put("detras", new String[]{"d", "e", "t", "r", "a", "s"});
        DICTIONARY.put("frente", new String[]{"f", "r", "e", "n", "t", "e"});
        DICTIONARY.put("amigo", new String[]{"a", "m", "i", "g", "o"});
        DICTIONARY.put("miedo", new String[]{"m", "i", "e", "d", "o"});
        DICTIONARY.put("ayuda", new String[]{"a", "y", "u", "d", "a"});
        DICTIONARY.put("corre", new String[]{"k", "o", "r", "e"});
        DICTIONARY.put("para", new String[]{"p", "a", "r", "a"});
        DICTIONARY.put("mira", new String[]{"m", "i", "r", "a"});
        DICTIONARY.put("veo", new String[]{"v", "e", "o"});
        DICTIONARY.put("puedo", new String[]{"p", "u", "e", "d", "o"});
        DICTIONARY.put("puedes", new String[]{"p", "u", "e", "d", "e", "s"});
        DICTIONARY.put("donde", new String[]{"d", "o", "n", "d", "e"});
        DICTIONARY.put("porque", new String[]{"p", "o", "r", "k", "e"});
        DICTIONARY.put("como", new String[]{"k", "o", "m", "o"});
        DICTIONARY.put("que", new String[]{"k", "e"});
        DICTIONARY.put("esto", new String[]{"e", "s", "t", "o"});
        DICTIONARY.put("eso", new String[]{"e", "s", "o"});
        DICTIONARY.put("jugar", new String[]{"x", "u", "g", "a", "r"});
        DICTIONARY.put("escapar", new String[]{"e", "s", "k", "a", "p", "a", "r"});
        DICTIONARY.put("ayudame", new String[]{"a", "y", "u", "d", "a", "m", "e"});
        DICTIONARY.put("salvame", new String[]{"s", "a", "l", "v", "a", "m", "e"});
        DICTIONARY.put("escucha", new String[]{"e", "s", "k", "u", "ch", "a"});
        DICTIONARY.put("silencio", new String[]{"s", "i", "l", "e", "n", "s", "i", "o"});
        DICTIONARY.put("ten", new String[]{"t", "e", "n"});
        DICTIONARY.put("cuidado", new String[]{"k", "u", "i", "d", "a", "d", "o"});
        DICTIONARY.put("espera", new String[]{"e", "s", "p", "e", "r", "a"});
        DICTIONARY.put("ven", new String[]{"v", "e", "n"});
        DICTIONARY.put("vete", new String[]{"v", "e", "t", "e"});
        DICTIONARY.put("busca", new String[]{"b", "u", "s", "k", "a"});
        DICTIONARY.put("encuentra", new String[]{"e", "n", "k", "u", "e", "n", "t", "r", "a"});
        DICTIONARY.put("detente", new String[]{"d", "e", "t", "e", "n", "t", "e"});
        DICTIONARY.put("sal", new String[]{"s", "a", "l"});

        FALLBACK.put('a', "a");
        FALLBACK.put('b', "b");
        FALLBACK.put('c', "k");
        FALLBACK.put('d', "d");
        FALLBACK.put('e', "e");
        FALLBACK.put('f', "f");
        FALLBACK.put('g', "g");
        FALLBACK.put('h', "");
        FALLBACK.put('i', "i");
        FALLBACK.put('j', "x");
        FALLBACK.put('k', "k");
        FALLBACK.put('l', "l");
        FALLBACK.put('m', "m");
        FALLBACK.put('n', "n");
        FALLBACK.put('o', "o");
        FALLBACK.put('p', "p");
        FALLBACK.put('q', "k");
        FALLBACK.put('r', "r");
        FALLBACK.put('s', "s");
        FALLBACK.put('t', "t");
        FALLBACK.put('u', "u");
        FALLBACK.put('v', "v");
        FALLBACK.put('w', "u");
        FALLBACK.put('x', "x");
        FALLBACK.put('y', "y");
        FALLBACK.put('z', "s");

        PUNCTUATION.addAll(Arrays.asList(".", ",", "!", "?", ";", ":", "-", "\"", "'", "¿", "¡"));
    }

    public List<String> convert(String text) {
        List<String> phonemes = new ArrayList<>();
        String normalized = text.toLowerCase().trim();
        String[] words = normalized.split("\\s+");

        for (String word : words) {
            StringBuilder cleaned = new StringBuilder();
            for (char c : word.toCharArray()) {
                if (!PUNCTUATION.contains(String.valueOf(c))) {
                    cleaned.append(c);
                }
            }
            word = cleaned.toString();
            if (word.isEmpty()) continue;

            if (DICTIONARY.containsKey(word)) {
                Collections.addAll(phonemes, DICTIONARY.get(word));
            } else {
                for (int i = 0; i < word.length(); i++) {
                    char c = word.charAt(i);
                    if (i + 1 < word.length() && (word.substring(i, i + 2).equals("ch") || word.substring(i, i + 2).equals("ll") || word.substring(i, i + 2).equals("rr"))) {
                        phonemes.add(word.substring(i, i + 2));
                        i++;
                    } else {
                        String mapped = FALLBACK.getOrDefault(c, String.valueOf(c));
                        if (!mapped.isEmpty()) {
                            phonemes.add(mapped);
                        }
                    }
                }
            }
            phonemes.add("_pause");
        }

        return phonemes;
    }

    public static Set<String> getDictionaryWords() {
        return DICTIONARY.keySet();
    }
}
