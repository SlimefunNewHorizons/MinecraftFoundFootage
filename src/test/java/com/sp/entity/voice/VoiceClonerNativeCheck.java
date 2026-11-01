package com.sp.entity.voice;

import com.sun.jna.Pointer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manual check of the JNA bridge with the real DLLs and model, outside Minecraft. Not part of ModTestRunner:
 * it needs the ~660 MB model on disk.
 * Args: natives-dir model.gguf codec.gguf ref.wav (24 kHz mono s16) "ref text" device out.wav
 */
public class VoiceClonerNativeCheck {
    public static void main(String[] args) throws Exception {
        VoiceCloner.Bridge bridge = VoiceCloner.loadBridge(Path.of(args[0]));
        for (int i = 0; i < bridge.spb_device_count(); i++) {
            System.out.printf("%s | %s | type=%d | candidate=%b%n", bridge.spb_device_name(i), bridge.spb_device_description(i),
                    bridge.spb_device_type(i), VoiceCloner.isGpuCandidate(bridge.spb_device_type(i), bridge.spb_device_memory_total(i)));
        }

        Pointer ctx = bridge.spb_init(VoiceCloner.utf8(args[1]), VoiceCloner.utf8(args[2]), args[5]);
        if (ctx == null) throw new IllegalStateException(bridge.spb_last_error());

        byte[] wav = Files.readAllBytes(Path.of(args[3]));
        int data = new String(wav, java.nio.charset.StandardCharsets.ISO_8859_1).indexOf("data") + 8;
        short[] pcm = new short[(wav.length - data) / 2];
        ByteBuffer.wrap(wav, data, pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
        int[] codes = new int[8 * 400];
        int frames = bridge.spb_encode_ref(ctx, VoiceCloner.toFloat(pcm), pcm.length, codes, codes.length);
        System.out.println("reference frames: " + frames);

        float[] out = new float[24000 * 20];
        long start = System.nanoTime();
        String text = "¿Dónde estás? Ven aquí, no me dejes solo.";
        float duration = VoiceCloner.durationSeconds(text, args[4], frames);
        System.out.printf("planned duration: %.2f s%n", duration);
        int samples = bridge.spb_synthesize(ctx, VoiceCloner.utf8(text), "es", codes, frames, VoiceCloner.utf8(args[4]),
                VoiceCloner.STEPS, VoiceCloner.GUIDANCE, duration, 1234567890123L, out, out.length);
        double seconds = (System.nanoTime() - start) / 1e9;
        if (samples <= 0) throw new IllegalStateException(bridge.spb_last_error());
        System.out.printf("synth %.2f s for %.2f s of audio (%.2fx real time)%n", seconds, samples / 24000.0, seconds / (samples / 24000.0));

        short[] voice = VoiceCloner.toShort(out, samples);
        ByteBuffer file = ByteBuffer.allocate(44 + voice.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        file.put("RIFF".getBytes()).putInt(36 + voice.length * 2).put("WAVEfmt ".getBytes()).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(24000).putInt(48000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(voice.length * 2);
        for (short s : voice) file.putShort(s);
        Files.write(Path.of(args[6]), file.array());
        bridge.spb_free(ctx);
    }
}
