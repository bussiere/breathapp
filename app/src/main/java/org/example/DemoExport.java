package org.example;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

public final class DemoExport {
    private DemoExport() {
    }

    public static void main(String[] args) throws Exception {
        Path output = args.length > 0 ? Path.of(args[0]) : Path.of("build/demo-output");
        run(output);
    }

    public static void run(Path outputDirectory) throws IOException {
        BufferedImage sprite;
        try (InputStream input = DemoExport.class.getClassLoader().getResourceAsStream("test_sprite/chips.png")) {
            if (input == null) {
                throw new IOException("Unable to find test_sprite/chips.png");
            }
            sprite = ImageIO.read(input);
        }
        if (sprite == null) {
            throw new IOException("Unable to decode test_sprite/chips.png");
        }

        List<ControlPoint> points = AnimationExporter.defaultTorsoPoints(sprite);
        List<BufferedImage> frames = AnimationExporter.renderFrames(sprite, points, AnimationExporter.DEFAULT_FRAME_COUNT);
        AnimationExporter.writePngSequence(frames, outputDirectory.resolve("frames"));
        AnimationExporter.writeSpriteSheetWithAtlases(frames, outputDirectory.resolve("chips_breath_spritesheet.png"), 6, 3.5);
        AnimationExporter.writeAnimatedPng(frames, outputDirectory.resolve("chips_breath_apng.png"), 3.5);
        AnimationExporter.writeGif(frames, outputDirectory.resolve("chips_breath.gif"), 3.5);
    }
}
