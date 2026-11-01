package com.sp.entity.voice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the device rules and audio conversions of the cloned voice (no native code involved).
 */
public class VoiceClonerTest {
    private static final long GIB = 1024L * 1024 * 1024;

    @Test
    @DisplayName("Only discrete GPUs with GTX 1650 class memory are tried")
    void gpuCandidates() {
        assertTrue(VoiceCloner.isGpuCandidate(1, 4 * GIB), "GTX 1650 / RX 570 (4 GB)");
        assertTrue(VoiceCloner.isGpuCandidate(1, 8 * GIB));
        assertFalse(VoiceCloner.isGpuCandidate(1, 2 * GIB), "2 GB cards are below the minimum");
        assertFalse(VoiceCloner.isGpuCandidate(2, 8 * GIB), "integrated GPUs use the CPU path");
        assertFalse(VoiceCloner.isGpuCandidate(0, 16 * GIB), "the CPU device is not a GPU");
    }

    @Test
    @DisplayName("A GPU must speak faster than real time with margin")
    void gpuSpeed() {
        assertTrue(VoiceCloner.isFastEnough(0.6), "measured GTX 1650");
        assertFalse(VoiceCloner.isFastEnough(1.2));
    }

    @Test
    @DisplayName("Answers follow the victim's speaking rate, bounded when the transcript does not fit the clip")
    void duration() {
        // 26 chars said in 2 s (50 frames) -> 13 chars/s, so a 26 char answer lasts 2 s.
        assertEquals(2.0f, VoiceCloner.durationSeconds("abcdefghijklmnopqrstuvwxyz", "abcdefghijklmnopqrstuvwxyz", 50), 0.01f);
        // A transcript of one letter for a 5.7 s clip is nonsense: the normal rate is used instead of minutes of audio.
        assertEquals(26 / 13.0f, VoiceCloner.durationSeconds("abcdefghijklmnopqrstuvwxyz", "x", 143), 0.01f);
        assertEquals(12.0f, VoiceCloner.durationSeconds("a".repeat(1000), "abcdefghijklm", 25), 0.01f);
        assertEquals(0.6f, VoiceCloner.durationSeconds("si", "abcdefghijklm", 25), 0.01f);
    }

    @Test
    @DisplayName("Text reaches the native side as NUL-terminated UTF-8")
    void utf8() {
        assertArrayEquals(new byte[]{(byte) 0xC3, (byte) 0xB3, 0}, VoiceCloner.utf8("ó"));
    }

    @Test
    @DisplayName("Audio converts between 16 bit and float, clipping out of range samples")
    void audioConversion() {
        short[] pcm = {0, 16384, -16384, Short.MAX_VALUE, Short.MIN_VALUE};
        short[] back = VoiceCloner.toShort(VoiceCloner.toFloat(pcm), pcm.length);
        for (int i = 0; i < pcm.length; i++) {
            assertEquals(pcm[i], back[i], 1, "sample " + i);
        }

        short[] clipped = VoiceCloner.toShort(new float[]{2f, -2f}, 2);
        assertEquals(Short.MAX_VALUE, clipped[0]);
        assertEquals(-Short.MAX_VALUE, clipped[1]);
    }
}
