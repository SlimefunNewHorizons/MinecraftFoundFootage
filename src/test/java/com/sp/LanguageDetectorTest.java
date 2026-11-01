package com.sp;

import com.sp.entity.voice.LanguageDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the language detection the SkinWalker uses to answer in the player's language.
 */
class LanguageDetectorTest {

    @Test
    @DisplayName("A new player's mod language follows their game language when the mod is fully ready in it")
    void defaultLanguageFromGame() {
        assertEquals("es", com.sp.cca_stuff.PlayerComponent.modLanguageFor("es_mx"));
        assertEquals("es", com.sp.cca_stuff.PlayerComponent.modLanguageFor("es_ES"));
        assertEquals("en", com.sp.cca_stuff.PlayerComponent.modLanguageFor("en_us"));
        assertEquals("en", com.sp.cca_stuff.PlayerComponent.modLanguageFor("ja_jp"), "not fully ready: English");
        assertEquals("en", com.sp.cca_stuff.PlayerComponent.modLanguageFor(null));
    }

    @Test
    @DisplayName("Typical chat messages are detected in the 6 supported languages")
    void detectsSupportedLanguages() {
        Map<String, String> samples = Map.ofEntries(
                Map.entry("hola, ¿dónde estás?", "es"),
                Map.entry("tengo miedo, ayuda por favor", "es"),
                Map.entry("quien eres tu", "es"),
                Map.entry("hello, where are you?", "en"),
                Map.entry("no, stop it", "en"),
                Map.entry("who are you, what do you want", "en"),
                Map.entry("ich habe angst, wo bist du", "de"),
                Map.entry("hallo, wer bist du?", "de"),
                Map.entry("je suis perdu, c'est quoi ça", "fr"),
                Map.entry("bonjour, tu es où ?", "fr"),
                Map.entry("ciao, dove sei?", "it"),
                Map.entry("ho paura, chi sei", "it"),
                Map.entry("olá, onde você está?", "pt"),
                Map.entry("estou com medo, não sei onde estou", "pt")
        );

        samples.forEach((text, language) -> assertEquals(language, LanguageDetector.detect(text), "Wrong language for \"" + text + "\""));
    }

    @Test
    @DisplayName("Text with no known words gives no language")
    void unknownText() {
        assertNull(LanguageDetector.detect(""));
        assertNull(LanguageDetector.detect("   "));
        assertNull(LanguageDetector.detect("..."));
        assertNull(LanguageDetector.detect(null));
        assertNull(LanguageDetector.detect("xyzzy plugh"));
    }

    @Test
    @DisplayName("Tokenizer keeps apostrophes and accents, ignores punctuation and case")
    void tokenizer() {
        assertEquals(Set.of("c'est", "quoi", "ça"), LanguageDetector.tokenize("C'est QUOI, ça?!"));
        assertEquals(Set.of("dónde", "estás"), LanguageDetector.tokenize("¿Dónde estás?"));
    }

    @Test
    @DisplayName("The speech-to-text model id tells its language")
    void modelLanguage() {
        assertEquals("es", LanguageDetector.fromModelId("alphacephei/vosk-model-small-es-0.42"));
        assertEquals("en", LanguageDetector.fromModelId("alphacephei/vosk-model-small-en-us-0.15"));
        assertEquals("de", LanguageDetector.fromModelId("alphacephei/vosk-model-small-de-0.15"));
        assertEquals("fr", LanguageDetector.fromModelId("alphacephei/vosk-model-small-fr-0.22"));
        assertEquals("pt", LanguageDetector.fromModelId("alphacephei/vosk-model-small-pt-0.3"));
        assertEquals("it", LanguageDetector.fromModelId("alphacephei/vosk-model-small-it-0.22"));
        assertNull(LanguageDetector.fromModelId("some-other-model"));
        assertNull(LanguageDetector.fromModelId(null));
    }
}
