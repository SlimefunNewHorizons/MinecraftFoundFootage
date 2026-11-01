package com.sp.render.camera;

import com.sp.ModKeyBinds;
import com.sp.SPBRevamped;
import com.sp.init.ModItems;
import com.sp.init.ModSounds;
import com.sp.util.AviMjpegWriter;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The camcorder: turned on (right click with it in hand) the player sees through it, an old VHS tape, and can take
 * photos and record videos into the screenshots folder. Turned off, or not in hand, the view is the normal one.
 */
public final class CameraView implements HudRenderCallback {
    //*Videos are smaller than the screen: JPEG frames at full resolution would be slow to encode every tick
    private static final int VIDEO_MAX_WIDTH = 854;
    private static final int VIDEO_FPS = 20;
    private static final int MAX_PENDING_FRAMES = 8;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");
    // Camcorder style "NOV. 01 2026" / "PM 5:33", always in English like the tapes
    private static final DateTimeFormatter DATE_STAMP = DateTimeFormatter.ofPattern("MMM. dd yyyy", java.util.Locale.ENGLISH);
    private static final DateTimeFormatter TIME_STAMP = DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.ENGLISH);

    private static boolean on;
    private static int flashTicks;
    private static Recording recording;

    private CameraView() {}

    public static final CameraView HUD = new CameraView();

    /**
     * Whether the player is looking through the camera right now.
     */
    public static boolean isViewActive() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return on && player != null && (player.getMainHandStack().isOf(ModItems.CAMERA) || player.getOffHandStack().isOf(ModItems.CAMERA));
    }

    public static void toggle() {
        on = !on;
        play(on ? ModSounds.CAMERA_ON : ModSounds.CAMERA_OFF, 1.0F);
        if (!on) {
            stopRecording();
        }
    }

    public static void tick(MinecraftClient client) {
        boolean active = isViewActive();
        while (ModKeyBinds.takePhoto.wasPressed()) {
            if (active) takePhoto(client);
        }
        while (ModKeyBinds.recordVideo.wasPressed()) {
            if (active) {
                if (recording == null) startRecording(client);
                else stopRecording();
            }
        }

        if (!active || client.world == null) {
            stopRecording();
        } else if (recording != null) {
            recording.capture(client);
        }
        if (flashTicks > 0) flashTicks--;
    }

    private static void takePhoto(MinecraftClient client) {
        String name = "spb_photo_" + LocalDateTime.now().format(STAMP) + ".png";
        // The last finished frame, VHS effect and overlay included.
        ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> client.execute(() -> {
            if (client.player != null) client.player.sendMessage(message, false);
        }));
        flashTicks = 4;
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.6F);
    }

    private static void startRecording(MinecraftClient client) {
        try {
            recording = new Recording(client);
            if (client.player != null) {
                client.player.sendMessage(Text.translatable("spb-revamped.camera.recording").formatted(Formatting.RED), true);
            }
        } catch (IOException e) {
            SPBRevamped.LOGGER.warn("[SPB-Camera] Could not start recording: {}", String.valueOf(e));
            recording = null;
        }
    }

    private static void stopRecording() {
        if (recording != null) {
            recording.finish();
            recording = null;
        }
    }

    private static void play(SoundEvent sound, float pitch) {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(sound, pitch));
    }

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        if (!isViewActive()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        long time = client.world == null ? 0 : client.world.getTime();

        if (recording != null) {
            if ((time / 10) % 2 == 0) {
                context.fill(12, 12, 18, 18, 0xFFE0201C);
            }
            context.drawText(client.textRenderer, "REC", 22, 11, 0xFFFFFFFF, true);
        } else {
            context.drawText(client.textRenderer, "STBY", 12, 11, 0xFFFFFFFF, true);
        }

        long seconds = recording != null ? (long) recording.seconds() : (time / 20) % 360000;
        String counter = String.format("%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
        context.drawText(client.textRenderer, counter, width - client.textRenderer.getWidth(counter) - 12, height - 20, 0xFFFFFFFF, true);

        // Tape speed and a battery that is never quite full
        int battery = width - 32;
        context.drawText(client.textRenderer, "SP", battery - client.textRenderer.getWidth("SP") - 6, 11, 0xFFFFFFFF, true);
        context.drawBorder(battery, 11, 18, 9, 0xFFFFFFFF);
        context.fill(battery + 18, 13, battery + 20, 18, 0xFFFFFFFF);
        for (int bar = 0; bar < 3; bar++) {
            if (bar < 2 || (time / 15) % 2 == 0) context.fill(battery + 2 + bar * 5, 13, battery + 6 + bar * 5, 18, 0xFFFFFFFF);
        }

        // Date stamp, like every home video
        LocalDateTime now = LocalDateTime.now();
        context.drawText(client.textRenderer, now.format(DATE_STAMP).toUpperCase(java.util.Locale.ROOT), 12, height - 30, 0xFFFFFFFF, true);
        context.drawText(client.textRenderer, now.format(TIME_STAMP).toUpperCase(java.util.Locale.ROOT), 12, height - 20, 0xFFFFFFFF, true);

        // Viewfinder corners
        int mx = width / 10;
        int my = height / 8;
        int arm = Math.max(8, width / 40);
        for (int[] corner : new int[][]{{mx, my, 1, 1}, {width - mx, my, -1, 1}, {mx, height - my, 1, -1}, {width - mx, height - my, -1, -1}}) {
            int x = corner[0];
            int y = corner[1];
            context.fill(Math.min(x, x + corner[2] * arm), y, Math.max(x, x + corner[2] * arm), y + corner[3], 0xCCFFFFFF);
            context.fill(x, Math.min(y, y + corner[3] * arm), x + corner[2], Math.max(y, y + corner[3] * arm), 0xCCFFFFFF);
        }

        if (flashTicks > 0) {
            context.fill(0, 0, width, height, (flashTicks * 50) << 24 | 0xFFFFFF);
        }
    }

    /**
     * One video being recorded: frames are read on the render thread once per tick and encoded on a worker.
     */
    private static final class Recording {
        private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "SPB-CameraRecorder");
            thread.setDaemon(true);
            return thread;
        });
        private final AtomicInteger pending = new AtomicInteger();
        private final AviMjpegWriter writer;
        private final Path path;
        private final int width;
        private final int height;

        Recording(MinecraftClient client) throws IOException {
            int sourceWidth = client.getFramebuffer().textureWidth;
            int sourceHeight = client.getFramebuffer().textureHeight;
            double scale = Math.min(1.0, (double) VIDEO_MAX_WIDTH / sourceWidth);
            this.width = (int) (sourceWidth * scale) & ~1;
            this.height = (int) (sourceHeight * scale) & ~1;
            Path folder = client.runDirectory.toPath().resolve("screenshots");
            Files.createDirectories(folder);
            this.path = folder.resolve("spb_video_" + LocalDateTime.now().format(STAMP) + ".avi");
            this.writer = new AviMjpegWriter(this.path, this.width, this.height, VIDEO_FPS);
        }

        double seconds() {
            return this.writer.seconds();
        }

        void capture(MinecraftClient client) {
            //*If encoding falls behind, drop frames instead of filling the memory
            if (this.pending.get() >= MAX_PENDING_FRAMES) {
                return;
            }
            NativeImage frame = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
            this.pending.incrementAndGet();
            this.worker.execute(() -> {
                try (frame) {
                    this.writer.writeFrame(encode(frame));
                } catch (IOException e) {
                    SPBRevamped.LOGGER.warn("[SPB-Camera] Frame lost: {}", String.valueOf(e));
                } finally {
                    this.pending.decrementAndGet();
                }
            });
        }

        private byte[] encode(NativeImage frame) throws IOException {
            BufferedImage image = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_RGB);
            int sourceWidth = frame.getWidth();
            int sourceHeight = frame.getHeight();
            for (int y = 0; y < this.height; y++) {
                int sy = y * sourceHeight / this.height;
                for (int x = 0; x < this.width; x++) {
                    int abgr = frame.getColor(x * sourceWidth / this.width, sy);
                    image.setRGB(x, y, (abgr & 0xFF) << 16 | (abgr & 0xFF00) | (abgr >> 16 & 0xFF));
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        }

        void finish() {
            MinecraftClient client = MinecraftClient.getInstance();
            this.worker.execute(() -> {
                try {
                    this.writer.close();
                    String seconds = String.format("%.1f", this.writer.seconds());
                    client.execute(() -> {
                        if (client.player != null) {
                            client.player.sendMessage(Text.translatable("spb-revamped.camera.saved_video", this.path.getFileName().toString(), seconds), false);
                        }
                    });
                } catch (IOException e) {
                    SPBRevamped.LOGGER.warn("[SPB-Camera] Could not finish the video: {}", String.valueOf(e));
                }
            });
            this.worker.shutdown();
        }
    }
}
