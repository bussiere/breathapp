package org.example;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

final class EditorImageLoader {
    // Image decoding is shared by PNG load, project load, and batch export; keeping it
    // outside the frame prevents UI flow changes from duplicating path/base64/ARGB rules.
    private EditorImageLoader() {
    }

    static LoadedImage loadPng(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        return new LoadedImage(readImage(normalized), normalized, normalized.getFileName().toString());
    }

    static LoadedImage loadProjectImage(BreathingProject project) throws IOException {
        if (project.hasEmbeddedImage()) {
            BufferedImage loaded = ImageIO.read(new ByteArrayInputStream(project.embeddedImageBytes()));
            if (loaded == null) {
                throw new IOException("Embedded image is not readable");
            }
            String imageName = project.imageName().isBlank() ? "imageBase64" : project.imageName();
            return new LoadedImage(toArgb(loaded), project.imagePath(), imageName);
        }

        if (project.imagePath() != null && Files.exists(project.imagePath())) {
            return loadPng(project.imagePath());
        }
        throw new IOException("Project image not found and no embedded image is available");
    }

    static BufferedImage toArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }
        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return converted;
    }

    private static BufferedImage readImage(Path path) throws IOException {
        BufferedImage loaded = ImageIO.read(path.toFile());
        if (loaded == null) {
            throw new IOException("Unsupported image format");
        }
        return toArgb(loaded);
    }

    record LoadedImage(BufferedImage image, Path imagePath, String label) {
    }
}
