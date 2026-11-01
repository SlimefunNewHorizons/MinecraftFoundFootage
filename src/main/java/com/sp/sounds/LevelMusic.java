package com.sp.sounds;

import com.sp.init.BackroomsLevels;
import com.sp.init.ModSounds;
import com.sp.world.generation.chunk_generator.Level94Layout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;

/**
 * The levels' own music (vanilla music is off in the Backrooms, see MusicTrackerMixin).
 * <ul>
 *     <li>Level 94: one calm piece in four versions of the same length, started together so they stay in sync.
 *     Only the version of the zone you are in is heard; walking towards the next zone fades it in, so the song
 *     keeps playing while it falls apart.</li>
 *     <li>Infinite field: a lonely loop.</li>
 * </ul>
 */
public final class LevelMusic {
    private static final float LEVEL94_VOLUME = 0.55F;
    private static final float INFINITE_FIELD_VOLUME = 0.6F;
    //*Blocks before a zone boundary over which the next version fades in
    static final int BLEND_BLOCKS = 80;
    //*Volume change per tick (about 3 seconds for a full fade)
    private static final float FADE_STEP = 1F / 60;

    private static final LoopingMusic[] level94 = new LoopingMusic[4];
    private static LoopingMusic infiniteField;

    private LevelMusic() {}

    public static void tick(MinecraftClient client) {
        RegistryKey<World> world = client.world == null || client.player == null ? null : client.world.getRegistryKey();
        SoundManager sounds = client.getSoundManager();

        if (world == BackroomsLevels.LEVEL94_WORLD_KEY) {
            //*isPlaying, not isDone: leaving a world stops every sound without marking it done
            if (level94[0] == null || !sounds.isPlaying(level94[0])) {
                //*All four in the same tick so they share one timeline
                for (int i = 0; i < level94.length; i++) {
                    if (level94[i] != null) {
                        sounds.stop(level94[i]);
                    }
                    level94[i] = new LoopingMusic(ModSounds.LEVEL94_MUSIC[i], BackroomsLevels.LEVEL94_WORLD_KEY, 0F);
                    sounds.play(level94[i]);
                }
            }
            float[] weights = level94Weights(client.player.getBlockX());
            for (int i = 0; i < level94.length; i++) {
                level94[i].fadeTo(weights[i] * LEVEL94_VOLUME);
            }
        }

        if (world == BackroomsLevels.INFINITE_FIELD_WORLD_KEY && (infiniteField == null || !sounds.isPlaying(infiniteField))) {
            infiniteField = new LoopingMusic(ModSounds.INFINITE_FIELD_MUSIC, BackroomsLevels.INFINITE_FIELD_WORLD_KEY, 0F);
            sounds.play(infiniteField);
        }
        if (world == BackroomsLevels.INFINITE_FIELD_WORLD_KEY) {
            infiniteField.fadeTo(INFINITE_FIELD_VOLUME);
        }
    }

    /**
     * How much of each Level 94 version to hear at this x: one zone at a time, crossfading over the
     * {@link #BLEND_BLOCKS} before each zone boundary.
     */
    static float[] level94Weights(int x) {
        int distance = Math.abs(x);
        float position = 0;
        for (int k = 1; k < Level94Layout.ZONE_START.length; k++) {
            float s = (distance - (Level94Layout.ZONE_START[k] - BLEND_BLOCKS)) / (float) BLEND_BLOCKS;
            position += Math.max(0, Math.min(1, s));
        }

        float[] weights = new float[4];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = Math.max(0, 1 - Math.abs(position - i));
        }
        return weights;
    }

    /**
     * A music loop heard everywhere at once. It fades to its target volume and stops itself when the player
     * leaves its level.
     */
    private static final class LoopingMusic extends MovingSoundInstance {
        private final RegistryKey<World> level;
        private float target;

        LoopingMusic(SoundEvent sound, RegistryKey<World> level, float volume) {
            super(sound, SoundCategory.MUSIC, SoundInstance.createRandom());
            this.level = level;
            this.repeat = true;
            this.repeatDelay = 0;
            this.relative = true;
            this.attenuationType = AttenuationType.NONE;
            this.volume = volume;
            this.target = volume;
        }

        void fadeTo(float volume) {
            this.target = volume;
        }

        @Override
        public void tick() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.world.getRegistryKey() != this.level) {
                this.setDone();
                return;
            }
            this.volume += Math.max(-FADE_STEP, Math.min(FADE_STEP, this.target - this.volume));
        }

        //*Silent layers must still run, or they would fall out of sync with the others
        @Override
        public boolean shouldAlwaysPlay() {
            return true;
        }
    }
}
