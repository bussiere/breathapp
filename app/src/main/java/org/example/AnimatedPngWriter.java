package org.example;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

public final class AnimatedPngWriter {
    private static final byte[] PNG_SIGNATURE = new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private AnimatedPngWriter() {
    }

    public static void write(List<BufferedImage> frames, Path target, int delayMs) throws IOException {
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("At least one frame is required");
        }
        int width = frames.get(0).getWidth();
        int height = frames.get(0).getHeight();
        for (BufferedImage frame : frames) {
            if (frame.getWidth() != width || frame.getHeight() != height) {
                throw new IllegalArgumentException("All APNG frames must have the same size");
            }
        }

        List<List<PngChunk>> encodedFrames = new ArrayList<>();
        for (BufferedImage frame : frames) {
            encodedFrames.add(chunksFor(frame));
        }

        List<PngChunk> first = encodedFrames.get(0);
        PngChunk ihdr = chunk(first, "IHDR");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(PNG_SIGNATURE);
        writeChunk(bytes, ihdr.type(), ihdr.data());
        writeChunk(bytes, "acTL", intBytes(frames.size(), 0));

        for (PngChunk chunk : first) {
            if (!chunk.type().equals("IHDR") && !chunk.type().equals("IDAT") && !chunk.type().equals("IEND")) {
                writeChunk(bytes, chunk.type(), chunk.data());
            }
        }

        int sequence = 0;
        for (int i = 0; i < encodedFrames.size(); i++) {
            writeChunk(bytes, "fcTL", frameControl(sequence++, width, height, delayMs));
            for (PngChunk idat : chunks(encodedFrames.get(i), "IDAT")) {
                if (i == 0) {
                    writeChunk(bytes, "IDAT", idat.data());
                } else {
                    byte[] data = new byte[idat.data().length + 4];
                    ByteBuffer.wrap(data).putInt(sequence++).put(idat.data());
                    writeChunk(bytes, "fdAT", data);
                }
            }
        }
        writeChunk(bytes, "IEND", new byte[0]);
        Files.write(target, bytes.toByteArray());
    }

    private static byte[] frameControl(int sequence, int width, int height, int delayMs) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(buffer);
        data.writeInt(sequence);
        data.writeInt(width);
        data.writeInt(height);
        data.writeInt(0);
        data.writeInt(0);
        data.writeShort(Math.max(1, delayMs));
        data.writeShort(1000);
        data.writeByte(0);
        data.writeByte(0);
        return buffer.toByteArray();
    }

    private static byte[] intBytes(int first, int second) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(buffer);
        data.writeInt(first);
        data.writeInt(second);
        return buffer.toByteArray();
    }

    private static List<PngChunk> chunksFor(BufferedImage image) throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        byte[] bytes = png.toByteArray();
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes[i] != PNG_SIGNATURE[i]) {
                throw new IOException("Invalid PNG signature");
            }
        }

        List<PngChunk> chunks = new ArrayList<>();
        int offset = PNG_SIGNATURE.length;
        while (offset < bytes.length) {
            int length = ByteBuffer.wrap(bytes, offset, 4).getInt();
            String type = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
            byte[] data = new byte[length];
            System.arraycopy(bytes, offset + 8, data, 0, length);
            chunks.add(new PngChunk(type, data));
            offset += 12 + length;
            if (type.equals("IEND")) {
                break;
            }
        }
        return chunks;
    }

    private static PngChunk chunk(List<PngChunk> chunks, String type) throws IOException {
        for (PngChunk chunk : chunks) {
            if (chunk.type().equals(type)) {
                return chunk;
            }
        }
        throw new IOException("Missing PNG chunk " + type);
    }

    private static List<PngChunk> chunks(List<PngChunk> chunks, String type) {
        List<PngChunk> matching = new ArrayList<>();
        for (PngChunk chunk : chunks) {
            if (chunk.type().equals(type)) {
                matching.add(chunk);
            }
        }
        return matching;
    }

    private static void writeChunk(ByteArrayOutputStream output, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        DataOutputStream stream = new DataOutputStream(output);
        stream.writeInt(data.length);
        stream.write(typeBytes);
        stream.write(data);

        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        stream.writeInt((int) crc.getValue());
    }

    private record PngChunk(String type, byte[] data) {
    }
}
