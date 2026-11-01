package com.sp.util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Writes Motion JPEG video into an AVI file: every frame is a JPEG image. Plain Java, no codec needed, and the
 * result opens in VLC, Windows' players and video editors. Frames must all have the size given to the constructor.
 */
public class AviMjpegWriter implements AutoCloseable {
    private static final int FLAG_KEYFRAME = 0x10;

    private final RandomAccessFile file;
    private final int fps;
    private ByteBuffer growingIndex = ByteBuffer.allocate(16 * 1024).order(ByteOrder.LITTLE_ENDIAN);
    private final long moviListStart;
    private int frames;
    private int largestFrame;

    // Offsets of the fields patched in close()
    private static final int AVIH_TOTAL_FRAMES = 48;
    private static final int AVIH_BUFFER_SIZE = 60;
    private static final int STRH_LENGTH = 140;
    private static final int STRH_BUFFER_SIZE = 144;

    public AviMjpegWriter(Path path, int width, int height, int fps) throws IOException {
        this.file = new RandomAccessFile(path.toFile(), "rw");
        this.file.setLength(0);
        this.fps = fps;

        ByteBuffer header = ByteBuffer.allocate(224).order(ByteOrder.LITTLE_ENDIAN);
        fourcc(header, "RIFF").putInt(0);
        fourcc(header, "AVI ");
        fourcc(header, "LIST").putInt(192);
        fourcc(header, "hdrl");
        fourcc(header, "avih").putInt(56);
        header.putInt(1_000_000 / fps)   // microseconds per frame
                .putInt(0).putInt(0)
                .putInt(FLAG_KEYFRAME)        // AVIF_HASINDEX
                .putInt(0)                    // total frames, patched
                .putInt(0).putInt(1)          // initial frames, streams
                .putInt(0)                    // suggested buffer size, patched
                .putInt(width).putInt(height)
                .putInt(0).putInt(0).putInt(0).putInt(0);
        fourcc(header, "LIST").putInt(116);
        fourcc(header, "strl");
        fourcc(header, "strh").putInt(56);
        fourcc(header, "vids");
        fourcc(header, "MJPG");
        header.putInt(0).putShort((short) 0).putShort((short) 0).putInt(0)
                .putInt(1).putInt(fps)        // scale, rate: fps frames per second
                .putInt(0)
                .putInt(0)                    // length in frames, patched
                .putInt(0)                    // suggested buffer size, patched
                .putInt(-1).putInt(0)
                .putShort((short) 0).putShort((short) 0).putShort((short) width).putShort((short) height);
        fourcc(header, "strf").putInt(40);
        header.putInt(40).putInt(width).putInt(height).putShort((short) 1).putShort((short) 24);
        fourcc(header, "MJPG");
        header.putInt(width * height * 3).putInt(0).putInt(0).putInt(0).putInt(0);
        this.file.write(header.array(), 0, header.position());

        this.moviListStart = this.file.getFilePointer();
        ByteBuffer movi = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        fourcc(movi, "LIST").putInt(0);
        fourcc(movi, "movi");
        this.file.write(movi.array());
    }

    /**
     * Appends one frame, already encoded as a JPEG file.
     */
    public void writeFrame(byte[] jpeg) throws IOException {
        long offset = this.file.getFilePointer() - (this.moviListStart + 8); // relative to the "movi" fourcc
        ByteBuffer chunk = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        fourcc(chunk, "00dc").putInt(jpeg.length);
        this.file.write(chunk.array());
        this.file.write(jpeg);
        if ((jpeg.length & 1) == 1) {
            this.file.write(0); // chunks are word aligned
        }

        if (this.growingIndex.remaining() < 16) {
            ByteBuffer bigger = ByteBuffer.allocate(this.growingIndex.capacity() * 2).order(ByteOrder.LITTLE_ENDIAN);
            this.growingIndex.flip();
            bigger.put(this.growingIndex);
            this.growingIndex = bigger;
        }
        fourcc(this.growingIndex, "00dc").putInt(FLAG_KEYFRAME).putInt((int) offset).putInt(jpeg.length);
        this.frames++;
        this.largestFrame = Math.max(this.largestFrame, jpeg.length);
    }

    public int frames() {
        return this.frames;
    }

    public double seconds() {
        return (double) this.frames / this.fps;
    }

    @Override
    public void close() throws IOException {
        long moviEnd = this.file.getFilePointer();

        ByteBuffer idx = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        fourcc(idx, "idx1").putInt(this.growingIndex.position());
        this.file.write(idx.array());
        this.file.write(this.growingIndex.array(), 0, this.growingIndex.position());
        long end = this.file.getFilePointer();

        patch(4, (int) (end - 8));
        patch(this.moviListStart + 4, (int) (moviEnd - this.moviListStart - 8));
        patch(AVIH_TOTAL_FRAMES, this.frames);
        patch(AVIH_BUFFER_SIZE, this.largestFrame + 8);
        patch(STRH_LENGTH, this.frames);
        patch(STRH_BUFFER_SIZE, this.largestFrame + 8);
        this.file.close();
    }

    private void patch(long position, int value) throws IOException {
        this.file.seek(position);
        this.file.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
    }

    private static ByteBuffer fourcc(ByteBuffer buffer, String code) {
        return buffer.put(code.getBytes(StandardCharsets.US_ASCII));
    }
}
