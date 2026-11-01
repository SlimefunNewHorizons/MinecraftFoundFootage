package com.sp;

import com.sp.util.AviMjpegWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks that the camera's videos are well formed AVI files.
 */
public class AviMjpegWriterTest {

    @Test
    @DisplayName("Three frames make a valid MJPEG AVI with matching header, chunks and index")
    void writesValidAvi() throws Exception {
        Path file = Files.createTempFile("spb-test", ".avi");
        byte[][] jpegs = new byte[3][];
        try (AviMjpegWriter writer = new AviMjpegWriter(file, 64, 48, 20)) {
            for (int i = 0; i < 3; i++) {
                BufferedImage image = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
                image.getGraphics().setColor(new Color(i * 80, 40, 200));
                image.getGraphics().fillRect(0, 0, 64, 48);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "jpg", out);
                jpegs[i] = out.toByteArray();
                writer.writeFrame(jpegs[i]);
            }
            assertEquals(3, writer.frames());
        }

        ByteBuffer avi = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
        Files.delete(file);

        assertEquals("RIFF", fourcc(avi, 0));
        assertEquals("AVI ", fourcc(avi, 8));
        assertEquals(avi.capacity() - 8, avi.getInt(4), "RIFF size covers the whole file");
        assertEquals(50_000, avi.getInt(32), "microseconds per frame at 20 fps");
        assertEquals(3, avi.getInt(48), "avih total frames");
        assertEquals(3, avi.getInt(140), "strh length");
        assertEquals("MJPG", fourcc(avi, 112));

        assertEquals("LIST", fourcc(avi, 212));
        assertEquals("movi", fourcc(avi, 220));
        int moviEnd = 220 + avi.getInt(216);
        assertEquals("idx1", fourcc(avi, moviEnd));
        assertEquals(3 * 16, avi.getInt(moviEnd + 4));

        for (int i = 0; i < 3; i++) {
            int entry = moviEnd + 8 + i * 16;
            int chunk = 220 + avi.getInt(entry + 8);
            assertEquals("00dc", fourcc(avi, chunk), "index entry " + i + " points at its chunk");
            assertEquals(jpegs[i].length, avi.getInt(chunk + 4));
            assertEquals((byte) 0xFF, avi.get(chunk + 8), "chunk holds a JPEG");
            assertEquals((byte) 0xD8, avi.get(chunk + 9));
        }
    }

    private static String fourcc(ByteBuffer buffer, int at) {
        byte[] bytes = new byte[4];
        buffer.get(at, bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }
}
