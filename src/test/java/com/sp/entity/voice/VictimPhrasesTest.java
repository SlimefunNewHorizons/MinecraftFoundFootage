package com.sp.entity.voice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks how the SkinWalker picks one of the victim's real recordings to answer with.
 */
public class VictimPhrasesTest {

    @Test
    @DisplayName("Accents, case and punctuation are ignored")
    void normalizesWords() {
        assertEquals(VictimPhrases.words("donde estas"), VictimPhrases.words("¿Dónde ESTÁS?"));
    }

    @Test
    @DisplayName("The closest real phrase is chosen, unrelated ones are not")
    void picksBestPhrase() {
        UUID victim = UUID.randomUUID();
        short[] whereAreYou = {1};
        short[] whereAreYouLong = {2};
        short[] no = {3};
        VictimPhrases.add(victim, whereAreYouLong, "oye donde estas tu");
        VictimPhrases.add(victim, whereAreYou, "dónde estás");
        VictimPhrases.add(victim, no, "no");

        assertSame(whereAreYou, VictimPhrases.find(victim, "¿Dónde estás?"));
        assertSame(no, VictimPhrases.find(victim, "no"));
        assertNull(VictimPhrases.find(victim, "tengo mucho miedo"));
        assertNull(VictimPhrases.find(UUID.randomUUID(), "no"));

        VictimPhrases.forget(victim);
        assertNull(VictimPhrases.find(victim, "no"));
    }

    @Test
    @DisplayName("The cloning reference is the longest phrase between 1 and 6 seconds")
    void picksReference() {
        UUID victim = UUID.randomUUID();
        short[] half = new short[24000];
        short[] three = new short[48000 * 3];
        short[] five = new short[48000 * 5];
        short[] seven = new short[48000 * 7];
        VictimPhrases.add(victim, half, "si");
        VictimPhrases.add(victim, five, "donde estas");
        VictimPhrases.add(victim, three, "ven aqui");
        VictimPhrases.add(victim, seven, "no se");

        VictimPhrases.Reference reference = VictimPhrases.reference(victim);
        assertNotNull(reference);
        assertSame(five, reference.audio());
        assertEquals("donde estas", reference.text());

        UUID quiet = UUID.randomUUID();
        VictimPhrases.add(quiet, half, "si");
        assertNull(VictimPhrases.reference(quiet), "half a second is too short to clone");
    }

    @Test
    @DisplayName("Long rambles are not kept as answers")
    void skipsLongPhrases() {
        UUID victim = UUID.randomUUID();
        VictimPhrases.add(victim, new short[]{1}, "uno dos tres cuatro cinco seis siete");
        assertNull(VictimPhrases.find(victim, "uno dos tres"));
    }
}
