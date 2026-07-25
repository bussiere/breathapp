package org.example;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import javax.imageio.ImageIO;

final class EditorExportService {
    private static final int SPRITESHEET_COLUMNS = 6;

    private EditorExportService() {
    }

    static void exportPngSequence(ExportRequest request, Path directory) throws IOException {
        AnimationExporter.writePngSequence(renderFrames(request), directory);
    }

    static void exportSpriteSheet(ExportRequest request, Path target) throws IOException {
        AnimationExporter.writeSpriteSheetWithAtlases(renderFrames(request), target, SPRITESHEET_COLUMNS, request.durationSeconds());
    }

    static void exportAnimatedPng(ExportRequest request, Path target) throws IOException {
        AnimationExporter.writeAnimatedPng(renderFrames(request), target, request.durationSeconds());
    }

    static void exportGif(ExportRequest request, Path target) throws IOException {
        AnimationExporter.writeGif(renderFrames(request), target, request.durationSeconds());
    }

    static BatchExportResult runBatch(
            RatioControlPreset preset,
            List<File> imageFiles,
            Path outputDirectory,
            BatchExportFormat format,
            int frameCount,
            IntConsumer progressSink) throws IOException {
        Files.createDirectories(outputDirectory);
        int exported = 0;
        int processed = 0;
        List<String> failures = new ArrayList<>();
        for (File imageFile : imageFiles) {
            try {
                BufferedImage loaded = ImageIO.read(imageFile);
                if (loaded == null) {
                    failures.add(imageFile.getName() + ": unsupported or unreadable image");
                    continue;
                }
                BufferedImage image = EditorImageLoader.toArgb(loaded);
                RatioControlPreset.AppliedControls controls = preset.applyTo(image);
                ExportRequest request = new ExportRequest(
                        image,
                        controls.points(),
                        controls.strokes(),
                        frameCount,
                        preset.breathingStrength(),
                        preset.durationSeconds());
                Path target = outputDirectory.resolve(format.fileNameFor(imageFile.toPath()));
                writeBatchFormat(request, target, format);
                exported++;
            } catch (IOException | RuntimeException ex) {
                failures.add(imageFile.getName() + ": " + ex.getMessage());
            } finally {
                processed++;
                if (progressSink != null) {
                    progressSink.accept(processed);
                }
            }
        }
        return new BatchExportResult(exported, failures);
    }

    private static List<BufferedImage> renderFrames(ExportRequest request) {
        return AnimationExporter.renderFrames(
                request.image(),
                request.points(),
                request.strokes(),
                request.frameCount(),
                request.breathingStrength());
    }

    private static void writeBatchFormat(ExportRequest request, Path target, BatchExportFormat format) throws IOException {
        switch (format) {
            case GIF -> exportGif(request, target);
            case SPRITESHEET -> exportSpriteSheet(request, target);
            case APNG -> exportAnimatedPng(request, target);
        }
    }

    private static String batchBaseName(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base + "_breath";
    }

    record ExportRequest(
            BufferedImage image,
            List<ControlPoint> points,
            List<ControlStroke> strokes,
            int frameCount,
            double breathingStrength,
            double durationSeconds) {
        ExportRequest {
            // Requests cross into SwingWorker threads, so freeze mutable controls at the boundary
            // instead of depending on the editor lists staying stable during rendering.
            points = points == null ? List.of() : points.stream().map(ControlPoint::copy).toList();
            strokes = strokes == null ? List.of() : strokes.stream().map(ControlStroke::copy).toList();
            frameCount = Math.max(1, frameCount);
            breathingStrength = Math.max(0.0, breathingStrength);
            durationSeconds = Math.max(0.25, durationSeconds);
        }
    }

    record BatchExportResult(int exported, List<String> failures) {
        BatchExportResult {
            failures = List.copyOf(failures == null ? List.of() : failures);
        }
    }

    enum BatchExportFormat {
        GIF("GIF", ".gif", ""),
        SPRITESHEET("Spritesheet + JSON + atlas", ".png", "_sheet"),
        APNG("Animated PNG", ".png", "_apng");

        private final String label;
        private final String extension;
        private final String suffix;

        BatchExportFormat(String label, String extension, String suffix) {
            this.label = label;
            this.extension = extension;
            this.suffix = suffix;
        }

        String label() {
            return label;
        }

        String extension() {
            return extension;
        }

        String fileNameFor(Path sourcePath) {
            return batchBaseName(sourcePath) + suffix + extension;
        }

        static BatchExportFormat fromLabel(String label) {
            for (BatchExportFormat format : values()) {
                if (format.label().equals(label)) {
                    return format;
                }
            }
            throw new IllegalArgumentException(String.format(Locale.ROOT, "Unknown batch export format: %s", label));
        }
    }
}
