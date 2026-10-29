package com.sp.entity.voice;

import com.sp.SPBRevamped;
import com.sp.compat.modmenu.ConfigStuff;
import com.sp.sounds.voicechat.BackroomsVoicechatPlugin;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class VoiceTranscriber {
    private static final String DEFAULT_MODEL_ID = "alphacephei/vosk-model-small-es-0.42";
    private static final int TARGET_SAMPLE_RATE = 16000;
    private static final int MAX_CLIP_SECONDS = 8;
    private static final int MAX_PENDING = 4;

    private static final Pattern TEXT_PATTERN = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]*)\"");

    // Recreated on demand: the voice chat server stops (and shuts this down) every time a singleplayer world is closed.
    private static ThreadPoolExecutor executor;

    private static volatile Model MODEL;

    private static synchronized ThreadPoolExecutor getExecutor() {
        if (executor == null || executor.isShutdown()) {
            executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), r -> {
                Thread thread = new Thread(r, "SPB-STT");
                thread.setDaemon(true);
                return thread;
            });
        }
        return executor;
    }

    public static void transcribeAsync(PlayerEntity player, short[] pcm, int sampleRate) {
        if (!ConfigStuff.sttEnabled) {
            BackroomsVoicechatPlugin.pushVoiceInput(player);
            return;
        }
        if (pcm == null || pcm.length == 0) {
            BackroomsVoicechatPlugin.pushVoiceInput(player);
            return;
        }

        ThreadPoolExecutor pool = getExecutor();
        if (pool.getQueue().size() >= MAX_PENDING) {
            BackroomsVoicechatPlugin.pushVoiceInput(player);
            return;
        }

        // Limit by duration of the source audio (it is usually 48 kHz, not the 16 kHz Vosk works with).
        short[] clip = new short[Math.min(pcm.length, sampleRate * MAX_CLIP_SECONDS)];
        System.arraycopy(pcm, 0, clip, 0, clip.length);

        pool.submit(() -> transcribeBlocking(player, clip, sampleRate));
    }

    /**
     * Language of the configured speech-to-text model; transcriptions are always in this language.
     */
    public static String modelLanguage() {
        String language = LanguageDetector.fromModelId(modelId());
        return language != null ? language : "es";
    }

    private static void transcribeBlocking(PlayerEntity player, short[] clip, int sampleRate) {
        try {
            if (!ensureModel()) {
                BackroomsVoicechatPlugin.pushVoiceInput(player);
                return;
            }

            short[] mono16 = resampleToTarget(clip, sampleRate);

            if (mono16.length == 0) {
                BackroomsVoicechatPlugin.pushVoiceInput(player);
                return;
            }

            byte[] audioBytes = toBytes(mono16);
            String text = "";

            try (Recognizer recognizer = new Recognizer(MODEL, TARGET_SAMPLE_RATE)) {
                recognizer.acceptWaveForm(audioBytes, audioBytes.length);
                String result = recognizer.getFinalResult();
                Matcher matcher = TEXT_PATTERN.matcher(result);
                if (matcher.find()) {
                    text = matcher.group(1);
                }
            }

            if (text == null || text.trim().isEmpty()) {
                BackroomsVoicechatPlugin.pushVoiceInput(player);
                return;
            }

            // Vosk models understand a single language, so that is the language of the text.
            BackroomsVoicechatPlugin.receiveChatInput(player, text.trim(), modelLanguage());
        } catch (Exception e) {
            SPBRevamped.LOGGER.error("[SPB-STT] Transcription failed: {}", String.valueOf(e));
            BackroomsVoicechatPlugin.pushVoiceInput(player);
        }
    }

    private static boolean ensureModel() {
        if (MODEL != null) {
            return true;
        }

        String modelId = modelId();
        Path workingRoot = modelRoot(modelId);

        try {
            synchronized (VoiceTranscriber.class) {
                if (MODEL != null) {
                    return true;
                }

                Path modelDir = modelsDir();
                if (!Files.isDirectory(modelDir)) {
                    Files.createDirectories(modelDir);
                }

                if (findModelPath(workingRoot) == null) {
                    downloadModel(modelId, workingRoot);
                }

                Path modelPath = findModelPath(workingRoot);
                if (modelPath == null) {
                    SPBRevamped.LOGGER.warn("[SPB-STT] No valid vosk model found under {}", workingRoot);
                    return false;
                }

                SPBRevamped.LOGGER.info("[SPB-STT] Loading vosk model from {}", modelPath);
                MODEL = new Model(modelPath.toString());
                return true;
            }
        } catch (Exception e) {
            SPBRevamped.LOGGER.error("[SPB-STT] Failed to load model: {}", String.valueOf(e));
            return false;
        }
    }

    private static void downloadModel(String modelId, Path workingRoot) throws IOException {
        String lastSegment = modelId.substring(modelId.lastIndexOf('/') + 1);
        String[] urls = {
                "https://huggingface.co/" + modelId + "/resolve/main/model.zip",
                "https://alphacephei.com/vosk/models/" + lastSegment + ".zip"
        };

        for (String url : urls) {
            try {
                SPBRevamped.LOGGER.info("[SPB-STT] Downloading speech model from {}", url);
                Path zipPath = workingRoot.resolve(lastSegment + ".zip");
                if (download(url, zipPath)) {
                    extractZip(zipPath, workingRoot);
                    Files.deleteIfExists(zipPath);
                    return;
                }
            } catch (IOException e) {
                SPBRevamped.LOGGER.warn("[SPB-STT] Download failed from {}: {}", url, String.valueOf(e));
            }
        }

        throw new IOException("No mirror available for model " + modelId);
    }

    private static boolean download(String url, Path target) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(120000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        int code = connection.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            return false;
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (InputStream inputStream = connection.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target.toFile().length() > 0;
    }

    private static void extractZip(Path zipPath, Path root) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                Path out = root.resolve(entry.getName()).normalize();
                if (!out.startsWith(root)) {
                    throw new IOException("Zip entry escapes target directory: " + entry.getName());
                }
                Files.createDirectories(out.getParent());
                Files.copy(zipInputStream, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path findModelPath(Path root) {
        if (!Files.isDirectory(root)) {
            return null;
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(root, 3)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(dir -> Files.isDirectory(dir.resolve("am")))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static short[] resampleToTarget(short[] pcm, int sampleRate) {
        if (sampleRate == TARGET_SAMPLE_RATE) {
            return pcm.clone();
        }
        int targetLength = (int) Math.ceil(pcm.length * (double) TARGET_SAMPLE_RATE / sampleRate);
        short[] out = new short[targetLength];
        for (int i = 0; i < targetLength; i++) {
            int index = (int) ((long) i * sampleRate / TARGET_SAMPLE_RATE);
            if (index >= pcm.length) {
                index = pcm.length - 1;
            }
            out[i] = pcm[index];
        }
        return out;
    }

    private static byte[] toBytes(short[] samples) {
        byte[] bytes = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            bytes[i * 2] = (byte) (samples[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
        }
        return bytes;
    }

    private static String modelId() {
        String id = ConfigStuff.sttModelId;
        return (id == null || id.trim().isEmpty()) ? DEFAULT_MODEL_ID : id.trim();
    }

    private static Path modelsDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("spb-revamped").resolve("stt");
    }

    private static Path modelRoot(String modelId) {
        return modelsDir().resolve(modelId.replaceAll("[^A-Za-z0-9._-]", "_"));
    }

    public static void shutdown() {
        synchronized (VoiceTranscriber.class) {
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
        }
        synchronized (VoiceTranscriber.class) {
            if (MODEL != null) {
                try {
                    MODEL.close();
                } catch (Exception ignored) {
                }
                MODEL = null;
            }
        }
    }
}