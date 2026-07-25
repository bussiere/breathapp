package org.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;

public final class AnimationExporter {
    public static final int DEFAULT_FRAME_COUNT = 30;

    private AnimationExporter() {
    }

    public static List<ControlPoint> defaultTorsoPoints(BufferedImage image) {
        double w = image.getWidth();
        double h = image.getHeight();
        List<ControlPoint> points = new ArrayList<>();
        points.add(new ControlPoint(w * 0.38, h * 0.31, 0.0, 0.0, h * 0.075, false, true));
        points.add(new ControlPoint(w * 0.62, h * 0.31, 0.0, 0.0, h * 0.075, false, true));
        points.add(new ControlPoint(w * 0.43, h * 0.385, -2.0, -1.4, h * 0.085, true, false));
        points.add(new ControlPoint(w * 0.59, h * 0.355, 2.0, -1.4, h * 0.085, true, false));
        points.add(new ControlPoint(w * 0.50, h * 0.47, 0.0, 1.0, h * 0.085, true, false));
        points.add(new ControlPoint(w * 0.50, h * 0.55, 0.0, 0.0, h * 0.055, false, true));
        return points;
    }



    public static List<BufferedImage> renderFrames(BufferedImage source, List<ControlPoint> points, int frameCount) {
        return renderFrames(source, points, List.of(), frameCount, 1.0);
    }

    public static List<BufferedImage> renderFrames(
            BufferedImage source,
            List<ControlPoint> points,
            int frameCount,
            double breathingStrength) {
        return renderFrames(source, points, List.of(), frameCount, breathingStrength);
    }

    public static List<BufferedImage> renderFrames(
            BufferedImage source,
            List<ControlPoint> points,
            List<ControlStroke> strokes,
            int frameCount,
            double breathingStrength) {
        ImageDeformer deformer = new ImageDeformer();
        List<BufferedImage> frames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            double phase = Math.sin(i / (double) frameCount * Math.PI * 2.0);
            frames.add(deformer.deform(source, points, strokes, phase, breathingStrength));
        }
        return frames;
    }

    public static void writePngSequence(List<BufferedImage> frames, Path directory) throws IOException {
        Files.createDirectories(directory);
        for (int i = 0; i < frames.size(); i++) {
            ImageIO.write(frames.get(i), "png", directory.resolve(String.format("breath_%03d.png", i)).toFile());
        }
    }

    public static void writeSpriteSheet(List<BufferedImage> frames, Path target, int columns) throws IOException {
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("At least one frame is required");
        }
        int frameWidth = frames.get(0).getWidth();
        int frameHeight = frames.get(0).getHeight();
        int safeColumns = Math.max(1, columns);
        int rows = (int) Math.ceil(frames.size() / (double) safeColumns);
        BufferedImage sheet = new BufferedImage(frameWidth * safeColumns, frameHeight * rows, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = sheet.createGraphics();
        try {
            for (int i = 0; i < frames.size(); i++) {
                int x = i % safeColumns * frameWidth;
                int y = i / safeColumns * frameHeight;
                graphics.drawImage(frames.get(i), x, y, null);
            }
        } finally {
            graphics.dispose();
        }
        ImageIO.write(sheet, "png", target.toFile());
    }

    public static void writeSpriteSheetWithAtlases(List<BufferedImage> frames, Path target, int columns) throws IOException {
        writeSpriteSheetWithAtlases(frames, target, columns, 1.0);
    }

    public static void writeSpriteSheetWithAtlases(List<BufferedImage> frames, Path target, int columns, double durationSeconds) throws IOException {
        writeSpriteSheet(frames, target, columns);
        writeTextureAtlasJson(frames, target, columns, atlasPath(target, ".json"), durationSeconds);
        writeTextureAtlas(frames, target, columns, atlasPath(target, ".atlas"));
    }

    public static Path atlasPath(Path spriteSheetPath, String extension) {
        String fileName = spriteSheetPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
        return spriteSheetPath.resolveSibling(baseName + extension);
    }

    private static void writeTextureAtlasJson(List<BufferedImage> frames, Path spriteSheetPath, int columns, Path target, double durationSeconds) throws IOException {
        int frameWidth = frames.get(0).getWidth();
        int frameHeight = frames.get(0).getHeight();
        int safeColumns = Math.max(1, columns);
        int rows = (int) Math.ceil(frames.size() / (double) safeColumns);
        int sheetWidth = frameWidth * safeColumns;
        int sheetHeight = frameHeight * rows;
        String imageName = spriteSheetPath.getFileName().toString();

        JsonObject root = new JsonObject();
        JsonObject frameMap = new JsonObject();
        int delayMs = frameDelayMs(frames.size(), durationSeconds);
        for (int i = 0; i < frames.size(); i++) {
            int x = i % safeColumns * frameWidth;
            int y = i / safeColumns * frameHeight;
            JsonObject frame = new JsonObject();
            frame.add("frame", rectangle(x, y, frameWidth, frameHeight));
            frame.addProperty("rotated", false);
            frame.addProperty("trimmed", false);
            frame.add("spriteSourceSize", rectangle(0, 0, frameWidth, frameHeight));
            frame.add("sourceSize", size(frameWidth, frameHeight));
            frame.addProperty("duration", delayMs);
            frameMap.add(String.format(java.util.Locale.ROOT, "breath_%03d", i), frame);
        }
        root.add("frames", frameMap);

        JsonObject meta = new JsonObject();
        meta.addProperty("app", "breath");
        meta.addProperty("version", "1.0");
        meta.addProperty("image", imageName);
        meta.addProperty("format", "RGBA8888");
        meta.add("size", size(sheetWidth, sheetHeight));
        meta.addProperty("scale", "1");
        // Aseprite readers use frameTags to recover animation intent from a flat atlas;
        // keeping it here avoids requiring sidecar timing metadata for batch exports.
        JsonObject tag = new JsonObject();
        tag.addProperty("name", "breath");
        tag.addProperty("from", 0);
        tag.addProperty("to", frames.size() - 1);
        tag.addProperty("direction", "forward");
        JsonArray frameTags = new JsonArray();
        frameTags.add(tag);
        meta.add("frameTags", frameTags);
        meta.add("layers", new JsonArray());
        root.add("meta", meta);

        Files.writeString(target, JsonSupport.GSON.toJson(root) + System.lineSeparator());
    }

    private static JsonObject rectangle(int x, int y, int width, int height) {
        JsonObject object = new JsonObject();
        object.addProperty("x", x);
        object.addProperty("y", y);
        object.addProperty("w", width);
        object.addProperty("h", height);
        return object;
    }

    private static JsonObject size(int width, int height) {
        JsonObject object = new JsonObject();
        object.addProperty("w", width);
        object.addProperty("h", height);
        return object;
    }

    private static void writeTextureAtlas(List<BufferedImage> frames, Path spriteSheetPath, int columns, Path target) throws IOException {
        int frameWidth = frames.get(0).getWidth();
        int frameHeight = frames.get(0).getHeight();
        int safeColumns = Math.max(1, columns);
        int rows = (int) Math.ceil(frames.size() / (double) safeColumns);
        final String newline = "\n";
        StringBuilder atlas = new StringBuilder();
        atlas.append(spriteSheetPath.getFileName()).append(newline);
        atlas.append(String.format(java.util.Locale.ROOT, "size: %d,%d", frameWidth * safeColumns, frameHeight * rows)).append(newline);
        atlas.append("format: RGBA8888").append(newline);
        atlas.append("filter: Linear,Linear").append(newline);
        atlas.append("repeat: none").append(newline);
        for (int i = 0; i < frames.size(); i++) {
            int x = i % safeColumns * frameWidth;
            int y = i / safeColumns * frameHeight;
            atlas.append(String.format(java.util.Locale.ROOT, "breath_%03d", i)).append(newline);
            atlas.append("  rotate: false").append(newline);
            atlas.append(String.format(java.util.Locale.ROOT, "  xy: %d, %d", x, y)).append(newline);
            atlas.append(String.format(java.util.Locale.ROOT, "  size: %d, %d", frameWidth, frameHeight)).append(newline);
            atlas.append(String.format(java.util.Locale.ROOT, "  orig: %d, %d", frameWidth, frameHeight)).append(newline);
            atlas.append("  offset: 0, 0").append(newline);
            atlas.append(String.format(java.util.Locale.ROOT, "  index: %d", i)).append(newline);
        }
        Files.writeString(target, atlas.toString());
    }


    public static void writeGif(List<BufferedImage> frames, Path target, double durationSeconds) throws IOException {
        int delayMs = frameDelayMs(frames.size(), durationSeconds);
        try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile());
             GifSequenceWriter writer = new GifSequenceWriter(output, BufferedImage.TYPE_INT_ARGB, delayMs, true)) {
            for (BufferedImage frame : frames) {
                writer.write(frame);
            }
        }
    }

    public static void writeAnimatedPng(List<BufferedImage> frames, Path target, double durationSeconds) throws IOException {
        AnimatedPngWriter.write(frames, target, frameDelayMs(frames.size(), durationSeconds));
    }

    public static int frameDelayMs(int frameCount, double durationSeconds) {
        return Math.max(20, (int) Math.round(Math.max(0.25, durationSeconds) * 1000.0 / Math.max(1, frameCount)));
    }
}
