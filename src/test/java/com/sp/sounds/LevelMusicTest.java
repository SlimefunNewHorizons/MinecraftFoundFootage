package com.sp.sounds;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks which version of the Level 94 music is heard along the main road.
 */
public class LevelMusicTest {

    @Test
    @DisplayName("Each zone plays its own version, both ends of the road alike")
    void zones() {
        assertArrayEquals(new float[]{1, 0, 0, 0}, LevelMusic.level94Weights(0));
        assertArrayEquals(new float[]{0, 1, 0, 0}, LevelMusic.level94Weights(600));
        assertArrayEquals(new float[]{0, 0, 1, 0}, LevelMusic.level94Weights(-1500));
        assertArrayEquals(new float[]{0, 0, 0, 1}, LevelMusic.level94Weights(2500));
    }

    @Test
    @DisplayName("Walking towards the next zone fades its version in before the boundary")
    void crossfade() {
        float[] halfway = LevelMusic.level94Weights(400 - LevelMusic.BLEND_BLOCKS / 2);
        assertEquals(0.5f, halfway[0], 0.01f);
        assertEquals(0.5f, halfway[1], 0.01f);

        for (int x = -3000; x <= 3000; x += 37) {
            float sum = 0;
            for (float w : LevelMusic.level94Weights(x)) sum += w;
            assertEquals(1f, sum, 0.001f, "total volume stays the same at x=" + x);
        }
    }
}
