package com.sp.entity.voice;

import com.sp.SPBRevamped;
import com.sp.compat.modmenu.ConfigStuff;
import com.sp.entity.ai.goals.SpeakGoal;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Speaks new sentences with the victim's own voice (OmniVoice through the native omnivoice.cpp runtime).
 * The model is loaded once and stays resident; references and generated audio only live in memory.
 * Runs on a discrete GPU at least as fast as a GTX 1650 (or the AMD equivalent), otherwise on the CPU.
 * Every native call happens on one worker thread: the omnivoice context is not thread safe.
 */
public class VoiceCloner {
    //*Fewer MaskGIT steps and no CFG (one forward per step instead of two): the fastest setting that still sounds right
    static final int STEPS = 6;
    static final float GUIDANCE = 0.0f;

    //*Below a GTX 1650 / RX 570 class card the CPU path is the better bet
    static final long MIN_GPU_VRAM = (long) (3.5 * 1024 * 1024 * 1024);
    //*A GTX 1650 speaks a sentence in about half its length; slower cards fall back to the CPU
    static final double MAX_GPU_REALTIME_FACTOR = 0.8;
    private static final int GGML_DEVICE_TYPE_GPU = 1;

    private static final int MODEL_RATE = 24000;
    private static final int VOICECHAT_RATE = 48000;
    private static final int MAX_OUTPUT_SAMPLES = MODEL_RATE * 20;
    private static final int MAX_REF_CODES = 8 * 400;

    //*Pinned revision + SHA-256: the files go straight into a native GGUF parser
    private static final String MODEL_URL = "https://huggingface.co/Serveurperso/OmniVoice-GGUF/resolve/017094167b5c9ed565a5076ac9b3b93c5ecf5c73/";
    private static final String MODEL_FILE = "omnivoice-base-Q4_K_M.gguf";
    private static final String MODEL_SHA256 = "4836ba5affcb83c04d08b3e96b6e48ac839947ee3864188739807b68e0e159c6";
    private static final String CODEC_FILE = "omnivoice-tokenizer-Q4_K_M.gguf";
    private static final String CODEC_SHA256 = "988fc32bc699bce361c44e9af8383be9811960441f293eb5ab4ec6bf6386378d";

    //*Load order: the bridge links against ggml and ggml-base; ggml-cpu / ggml-vulkan are loaded by ggml itself
    private static final String NATIVES_RESOURCE = "/natives/windows-x64/";
    private static final String[] NATIVE_FILES = {"ggml-base.dll", "ggml.dll", "ggml-cpu.dll", "ggml-vulkan.dll", "spb-voice.dll"};

    //*Paths and text go in as NUL-terminated UTF-8 bytes (see utf8): JNA's String conversion follows the system code page
    interface Bridge extends Library {
        String spb_last_error();
        int spb_load_backends(byte[] dir);
        int spb_device_count();
        String spb_device_name(int i);
        String spb_device_description(int i);
        int spb_device_type(int i);
        long spb_device_memory_total(int i);
        Pointer spb_init(byte[] modelPath, byte[] codecPath, String device);
        void spb_free(Pointer ctx);
        int spb_encode_ref(Pointer ctx, float[] pcm24k, int samples, int[] codesOut, int maxCodes);
        int spb_synthesize(Pointer ctx, byte[] text, String lang, int[] refCodes, int refT, byte[] refText,
                           int steps, float guidance, float durationSec, long seed, float[] out, int maxOut);
    }

    //*Codec frame rate (24 kHz / hop 960), and the speaking rate used when the reference's own one is implausible
    private static final double FRAMES_PER_SECOND = 25.0;
    private static final double DEFAULT_CHARS_PER_SECOND = 13.0;

    interface Kernel32 extends Library {
        boolean IsProcessorFeaturePresent(int feature);
    }

    private enum State { UNINITIALIZED, READY, UNAVAILABLE }

    private record EncodedRef(short[] source, int[] codes, int frames, String text) {}

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SPB-VoiceClone");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final Map<UUID, EncodedRef> REFS = new ConcurrentHashMap<>();

    private static volatile State state = State.UNINITIALIZED;
    private static Bridge bridge;
    private static Pointer ctx;

    /**
     * Starts cloning the answer in the victim's voice.
     *
     * @return the future 48 kHz audio (null inside when generation failed), or null when cloning can't run right
     *         now (disabled, unsupported machine, no usable recording of the victim yet, still loading, or busy).
     */
    public static CompletableFuture<short[]> cloneAsync(UUID victim, String text, String language) {
        if (!ConfigStuff.voiceCloneEnabled || !Platform.isWindows() || !Platform.is64Bit()) {
            return null;
        }
        VictimPhrases.Reference reference = VictimPhrases.reference(victim);
        if (reference == null) {
            return null;
        }
        //*The first use only starts the download and device test in the background: the SkinWalker keeps talking
        //*with its other voices meanwhile instead of going silent for minutes
        if (state != State.READY) {
            if (state == State.UNINITIALIZED && STARTED.compareAndSet(false, true)) {
                WORKER.execute(VoiceCloner::ensureReady);
            }
            return null;
        }
        if (!BUSY.compareAndSet(false, true)) {
            return null;
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return synthesize(victim, reference, text, language);
            } catch (Throwable t) {
                SPBRevamped.LOGGER.warn("[SPB-Clone] Generation failed: {}", String.valueOf(t));
                return null;
            } finally {
                BUSY.set(false);
            }
        }, WORKER);
    }

    public static void forget(UUID player) {
        REFS.remove(player);
    }

    private static short[] synthesize(UUID victim, VictimPhrases.Reference reference, String text, String language) {
        EncodedRef ref = REFS.get(victim);
        if (ref == null || ref.source() != reference.audio()) {
            float[] pcm = toFloat(SpeakGoal.resample(reference.audio(), VOICECHAT_RATE, MODEL_RATE));
            int[] codes = new int[MAX_REF_CODES];
            int frames = bridge.spb_encode_ref(ctx, pcm, pcm.length, codes, codes.length);
            if (frames <= 0) {
                SPBRevamped.LOGGER.warn("[SPB-Clone] Could not encode the reference: {}", bridge.spb_last_error());
                return null;
            }
            ref = new EncodedRef(reference.audio(), codes, frames, reference.text());
            REFS.put(victim, ref);
        }

        float[] out = new float[MAX_OUTPUT_SAMPLES];
        int samples = bridge.spb_synthesize(ctx, utf8(text), language, ref.codes(), ref.frames(), utf8(ref.text()),
                STEPS, GUIDANCE, durationSeconds(text, ref.text(), ref.frames()),
                ThreadLocalRandom.current().nextLong(Long.MAX_VALUE), out, out.length);
        if (samples <= 0) {
            SPBRevamped.LOGGER.warn("[SPB-Clone] Synthesis failed: {}", bridge.spb_last_error());
            return null;
        }
        return SpeakGoal.resample(toShort(out, samples), MODEL_RATE, VOICECHAT_RATE);
    }

    /**
     * Downloads the model, loads the native runtime and picks the device, once. Any failure (no AVX2, a blocked
     * or missing DLL, no network...) turns cloning off for the session and the SkinWalker keeps its other voices.
     */
    private static boolean ensureReady() {
        if (state != State.UNINITIALIZED) {
            return state == State.READY;
        }

        try {
            //*The CPU backend is built for AVX2: loading it on an older CPU would crash the game
            if (!Native.load("kernel32", Kernel32.class).IsProcessorFeaturePresent(40)) {
                throw new IllegalStateException("the CPU has no AVX2");
            }

            Path root = FabricLoader.getInstance().getConfigDir().resolve("spb-revamped").resolve("voiceclone");
            Path model = downloadVerified(root, MODEL_FILE, MODEL_SHA256);
            Path codec = downloadVerified(root, CODEC_FILE, CODEC_SHA256);
            bridge = loadBridge(extractNatives(root.resolve("natives")));

            ctx = pickDevice(utf8(model.toString()), utf8(codec.toString()));
            if (ctx == null) {
                throw new IllegalStateException("no device could load the model: " + bridge.spb_last_error());
            }
            state = State.READY;
            return true;
        } catch (Throwable t) {
            SPBRevamped.LOGGER.warn("[SPB-Clone] Voice cloning disabled: {}", String.valueOf(t));
            state = State.UNAVAILABLE;
            return false;
        }
    }

    /**
     * Loads the bridge from the extracted natives. Its own dependencies go first so Windows finds them, and the
     * ggml backends are registered from that folder (ggml alone would only look next to java.exe).
     */
    static Bridge loadBridge(Path natives) {
        NativeLibrary.getInstance(natives.resolve("ggml-base.dll").toString());
        NativeLibrary.getInstance(natives.resolve("ggml.dll").toString());
        Bridge loaded = Native.load(natives.resolve("spb-voice.dll").toString(), Bridge.class,
                Map.of(Library.OPTION_STRING_ENCODING, "UTF-8"));
        if (loaded.spb_load_backends(utf8(natives.toString())) != 0) {
            throw new IllegalStateException(loaded.spb_last_error());
        }
        return loaded;
    }

    static byte[] utf8(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return Arrays.copyOf(bytes, bytes.length + 1);
    }

    /**
     * Length to give the answer: the victim's own speaking rate (from the reference clip and its transcript), or a
     * normal one when that rate makes no sense. Speech-to-text can miss words, and an unbounded length would make
     * the model plan minutes of audio for one sentence.
     */
    static float durationSeconds(String text, String refText, int refFrames) {
        double refSeconds = refFrames / FRAMES_PER_SECOND;
        double rate = refSeconds > 0 ? refText.length() / refSeconds : 0;
        if (rate < 6 || rate > 25) {
            rate = DEFAULT_CHARS_PER_SECOND;
        }
        return (float) Math.max(0.6, Math.min(12.0, text.length() / rate));
    }

    private static Pointer pickDevice(byte[] model, byte[] codec) {
        for (int i = 0; i < bridge.spb_device_count(); i++) {
            String name = bridge.spb_device_name(i);
            String description = bridge.spb_device_description(i);
            long vram = bridge.spb_device_memory_total(i);
            SPBRevamped.LOGGER.info("[SPB-Clone] Device {}: {} ({}, {} MiB)", name, description, bridge.spb_device_type(i), vram >> 20);
            if (!isGpuCandidate(bridge.spb_device_type(i), vram)) {
                continue;
            }

            Pointer gpu = bridge.spb_init(model, codec, name);
            if (gpu == null) {
                continue;
            }
            double factor = benchmark(gpu);
            if (isFastEnough(factor)) {
                SPBRevamped.LOGGER.info("[SPB-Clone] Using {} ({}x real time)", description, String.format("%.2f", factor));
                return gpu;
            }
            SPBRevamped.LOGGER.info("[SPB-Clone] {} is too slow ({}x real time), using the CPU", description, String.format("%.2f", factor));
            bridge.spb_free(gpu);
        }

        SPBRevamped.LOGGER.info("[SPB-Clone] Using the CPU");
        return bridge.spb_init(model, codec, "CPU");
    }

    /**
     * Seconds of work per second of speech. The first run is a warm-up: Vulkan compiles its pipelines there.
     */
    private static double benchmark(Pointer device) {
        float[] out = new float[MAX_OUTPUT_SAMPLES];
        double factor = Double.MAX_VALUE;
        for (int run = 0; run < 2; run++) {
            long start = System.nanoTime();
            int samples = bridge.spb_synthesize(device, utf8("Hola, esto es una prueba de voz."), "es", null, 0, null,
                    STEPS, GUIDANCE, 0f, 42, out, out.length);
            if (samples <= 0) {
                return Double.MAX_VALUE;
            }
            factor = (System.nanoTime() - start) / 1e9 / ((double) samples / MODEL_RATE);
        }
        return factor;
    }

    static boolean isGpuCandidate(int deviceType, long vramBytes) {
        return deviceType == GGML_DEVICE_TYPE_GPU && vramBytes >= MIN_GPU_VRAM;
    }

    static boolean isFastEnough(double realtimeFactor) {
        return realtimeFactor <= MAX_GPU_REALTIME_FACTOR;
    }

    private static Path downloadVerified(Path root, String file, String sha256) throws IOException {
        Path target = root.resolve(file);
        if (Files.isRegularFile(target) && sha256(target).equals(sha256)) {
            return target;
        }

        SPBRevamped.LOGGER.info("[SPB-Clone] Downloading {}", file);
        Path partial = root.resolve(file + ".part");
        if (!VoiceTranscriber.download(MODEL_URL + file, partial)) {
            throw new IOException("download failed for " + file);
        }
        if (!sha256(partial).equals(sha256)) {
            Files.deleteIfExists(partial);
            throw new IOException("checksum mismatch for " + file);
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static Path extractNatives(Path dir) throws IOException {
        Files.createDirectories(dir);
        for (String file : NATIVE_FILES) {
            try (InputStream in = VoiceCloner.class.getResourceAsStream(NATIVES_RESOURCE + file)) {
                if (in == null) {
                    throw new IOException("missing bundled native " + file);
                }
                byte[] bytes = in.readAllBytes();
                Path target = dir.resolve(file);
                //*Rewrite only when it changed: a DLL loaded by an earlier world in this session can't be replaced
                if (!Files.isRegularFile(target) || !Arrays.equals(Files.readAllBytes(target), bytes)) {
                    Files.write(target, bytes);
                }
            }
        }
        return dir;
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    static float[] toFloat(short[] pcm) {
        float[] out = new float[pcm.length];
        for (int i = 0; i < pcm.length; i++) {
            out[i] = pcm[i] / 32768f;
        }
        return out;
    }

    static short[] toShort(float[] pcm, int samples) {
        short[] out = new short[samples];
        for (int i = 0; i < samples; i++) {
            out[i] = (short) Math.round(Math.max(-1f, Math.min(1f, pcm[i])) * 32767f);
        }
        return out;
    }
}
