package org.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import org.junit.Test;

public class AppTest {
    @Test
    public void deformerMovesPixelsOnChipsSprite() throws Exception {
        BufferedImage sprite = loadChips();
        List<ControlPoint> points = AnimationExporter.defaultTorsoPoints(sprite);

        BufferedImage frame = new ImageDeformer().deform(sprite, points, 1.0);

        assertEquals(sprite.getWidth(), frame.getWidth());
        assertEquals(sprite.getHeight(), frame.getHeight());
        assertTrue("The deformation should change visible pixels", countPixelDifferences(sprite, frame) > 100);
    }

    @Test
    public void optimizedDeformerMatchesLegacyReferenceWithPointsStrokesAndLocks() {
        BufferedImage sprite = gradientImage(28, 22);
        List<ControlPoint> points = List.of(
                new ControlPoint(8.0, 8.0, 3.25, -1.5, 7.0, true, false, 0x3366cc, 1.0, 1.4),
                new ControlPoint(14.0, 10.0, 0.0, 0.0, 5.0, false, false),
                new ControlPoint(9.0, 8.0, 0.0, 0.0, 2.0, false, true));
        List<ControlStroke> strokes = List.of(
                new ControlStroke(
                        List.of(new Point2D.Double(3.0, 15.0), new Point2D.Double(12.0, 17.0), new Point2D.Double(22.0, 14.0)),
                        -1.75,
                        2.5,
                        4.5,
                        true,
                        false,
                        0x22aa66,
                        0.8),
                new ControlStroke(
                        List.of(new Point2D.Double(18.0, 6.0), new Point2D.Double(23.0, 8.0)),
                        0.0,
                        0.0,
                        3.0,
                        false,
                        true));

        BufferedImage optimized = new ImageDeformer().deform(sprite, points, strokes, 0.75, 1.25);
        BufferedImage reference = legacyDeform(sprite, points, strokes, 0.75, 1.25);

        assertImagesEqual(reference, optimized);
    }

    @Test
    public void deformerStopsWhenCancellationIsRequested() {
        BufferedImage sprite = gradientImage(32, 32);
        List<ControlPoint> points = List.of(new ControlPoint(16.0, 16.0, 4.0, 0.0, 20.0, true));

        try {
            new ImageDeformer().deform(sprite, points, List.of(), 1.0, 1.0, () -> true);
        } catch (java.util.concurrent.CancellationException ex) {
            assertTrue(ex.getMessage().contains("cancelled"));
            return;
        }
        throw new AssertionError("Cancelled deformation should stop before rendering");
    }

    @Test
    public void deformerKeepsMediumSpriteWithinBroadPerformanceBudget() {
        BufferedImage sprite = gradientImage(96, 96);
        List<ControlPoint> points = List.of(
                new ControlPoint(24.0, 24.0, 4.0, -2.0, 28.0, true),
                new ControlPoint(72.0, 26.0, -3.0, 2.5, 24.0, true),
                new ControlPoint(48.0, 70.0, 0.0, 0.0, 16.0, false, true));
        List<ControlStroke> strokes = List.of(new ControlStroke(
                List.of(new Point2D.Double(18.0, 62.0), new Point2D.Double(48.0, 50.0), new Point2D.Double(78.0, 64.0)),
                0.0,
                4.0,
                18.0,
                true,
                false));
        ImageDeformer deformer = new ImageDeformer();

        deformer.deform(sprite, points, strokes, 0.0, 1.0);
        long start = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            deformer.deform(sprite, points, strokes, i / 5.0, 1.0);
        }
        long elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // This is intentionally a broad regression budget, not a benchmark scoreboard. It
        // catches accidental O(width * height * controls * extra work) changes while staying
        // tolerant of shared CI machines and JVM warmup variance.
        assertTrue("ImageDeformer got unexpectedly slow: " + elapsedMs + " ms", elapsedMs < 1500);
    }

    @Test
    public void projectJsonRoundTripsPoints() throws Exception {
        Path directory = Files.createTempDirectory("breath-project-test");
        Path image = directory.resolve("chips.png");
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("test_sprite/chips.png")) {
            assertNotNull("Chips test sprite resource should exist", input);
            Files.copy(input, image);
        }
        Path projectFile = directory.resolve("project.json");
        List<ControlPoint> points = List.of(
                new ControlPoint(142.0, 118.0, -3.0, -2.0, 80.0, true),
                new ControlPoint(198.0, 118.0, 3.0, -2.0, 80.0, false));

        new BreathingProject(image, 3.5, points).save(projectFile);
        BreathingProject loaded = BreathingProject.load(projectFile);

        assertEquals(image.normalize(), loaded.imagePath());
        assertEquals(3.5, loaded.durationSeconds(), 0.0001);
        assertEquals(2, loaded.points().size());
        assertEquals(-3.0, loaded.points().get(0).offsetX(), 0.0001);
        assertFalse(loaded.points().get(1).animated());
        assertEquals(1.0, loaded.breathingStrength(), 0.0001);
        assertEquals("chips.png", loaded.imageName());
        assertTrue("Saved project should embed imageBase64", loaded.hasEmbeddedImage());
        assertTrue(Files.readString(projectFile).contains("\"imageBase64\""));
        assertTrue(Files.readString(projectFile).contains("\"unmovable\""));
    }

    @Test
    public void exporterWritesAllRequestedFormats() throws Exception {
        BufferedImage sprite = loadChips();
        List<BufferedImage> frames = AnimationExporter.renderFrames(
                sprite,
                AnimationExporter.defaultTorsoPoints(sprite),
                8);
        Path directory = Files.createTempDirectory("breath-export-test");
        Path sequence = directory.resolve("sequence");
        Path sheetPath = directory.resolve("spritesheet.png");
        Path atlasJsonPath = AnimationExporter.atlasPath(sheetPath, ".json");
        Path atlasTextPath = AnimationExporter.atlasPath(sheetPath, ".atlas");
        Path apngPath = directory.resolve("animated.png");
        Path gifPath = directory.resolve("animated.gif");

        AnimationExporter.writePngSequence(frames, sequence);
        AnimationExporter.writeSpriteSheetWithAtlases(frames, sheetPath, 4, 2.0);
        AnimationExporter.writeAnimatedPng(frames, apngPath, 2.0);
        AnimationExporter.writeGif(frames, gifPath, 2.0);

        assertEquals(8, Files.list(sequence).filter(path -> path.toString().endsWith(".png")).count());
        BufferedImage sheet = ImageIO.read(sheetPath.toFile());
        assertEquals(sprite.getWidth() * 4, sheet.getWidth());
        assertEquals(sprite.getHeight() * 2, sheet.getHeight());
        assertTrue(Files.exists(atlasJsonPath));
        assertTrue(Files.exists(atlasTextPath));
        String atlasJson = Files.readString(atlasJsonPath);
        String atlasText = Files.readString(atlasTextPath);
        JsonObject atlasRoot = JsonParser.parseString(atlasJson).getAsJsonObject();
        JsonObject atlasMeta = atlasRoot.getAsJsonObject("meta");
        JsonObject frameSeven = atlasRoot.getAsJsonObject("frames").getAsJsonObject("breath_007");
        JsonObject frameSevenRect = frameSeven.getAsJsonObject("frame");
        assertEquals("spritesheet.png", atlasMeta.get("image").getAsString());
        assertEquals(sheet.getWidth(), atlasMeta.getAsJsonObject("size").get("w").getAsInt());
        assertEquals(sheet.getHeight(), atlasMeta.getAsJsonObject("size").get("h").getAsInt());
        assertEquals(sprite.getWidth() * 3, frameSevenRect.get("x").getAsInt());
        assertEquals(sprite.getHeight(), frameSevenRect.get("y").getAsInt());
        assertEquals(sprite.getWidth(), frameSevenRect.get("w").getAsInt());
        assertEquals(sprite.getHeight(), frameSevenRect.get("h").getAsInt());
        assertFalse(frameSeven.get("rotated").getAsBoolean());
        assertFalse(frameSeven.get("trimmed").getAsBoolean());
        assertTrue(atlasText.contains("spritesheet.png\n"));
        assertTrue(atlasText.contains("size: " + sheet.getWidth() + "," + sheet.getHeight()));
        assertTrue(atlasText.contains("breath_007\n  rotate: false\n  xy: " + (sprite.getWidth() * 3) + ", " + sprite.getHeight()));
        assertTrue(Files.size(gifPath) > 100);
        assertApngHasAnimationChunks(apngPath, 8);
    }

    @Test
    public void editorExportServiceBatchWritesSpritesheetAndReportsFailures() throws Exception {
        Path directory = Files.createTempDirectory("breath-export-service-test");
        Path imagePath = directory.resolve("source.png");
        Path badPath = directory.resolve("bad.png");
        Path outputDirectory = directory.resolve("out");
        BufferedImage sprite = gradientImage(12, 12);
        ImageIO.write(sprite, "png", imagePath.toFile());
        Files.writeString(badPath, "not a png");
        RatioControlPreset preset = RatioControlPreset.fromControls(
                sprite,
                1.0,
                1.0,
                List.of(new ControlPoint(6.0, 6.0, 1.0, 0.0, 4.0, true)),
                List.of());
        List<Integer> progress = new java.util.ArrayList<>();

        EditorExportService.BatchExportResult result = EditorExportService.runBatch(
                preset,
                List.of(imagePath.toFile(), badPath.toFile()),
                outputDirectory,
                EditorExportService.BatchExportFormat.SPRITESHEET,
                3,
                progress::add);

        assertEquals(1, result.exported());
        assertEquals(1, result.failures().size());
        assertEquals(List.of(1, 2), progress);
        assertTrue(Files.exists(outputDirectory.resolve("source_breath_sheet.png")));
        assertTrue(Files.exists(outputDirectory.resolve("source_breath_sheet.json")));
        assertTrue(Files.exists(outputDirectory.resolve("source_breath_sheet.atlas")));
        assertFalse(Files.exists(outputDirectory.resolve("source_breath.png")));
        assertTrue(result.failures().get(0).contains("bad.png"));
    }

    @Test
    public void editorExportServiceBatchUsesDistinctPngNamesForSpritesheetAndApng() throws Exception {
        Path directory = Files.createTempDirectory("breath-export-name-test");
        Path imagePath = directory.resolve("source.png");
        Path outputDirectory = directory.resolve("out");
        BufferedImage sprite = gradientImage(12, 12);
        ImageIO.write(sprite, "png", imagePath.toFile());
        RatioControlPreset preset = RatioControlPreset.fromControls(
                sprite,
                1.0,
                1.0,
                List.of(new ControlPoint(6.0, 6.0, 1.0, 0.0, 4.0, true)),
                List.of());

        EditorExportService.runBatch(
                preset,
                List.of(imagePath.toFile()),
                outputDirectory,
                EditorExportService.BatchExportFormat.SPRITESHEET,
                3,
                null);
        EditorExportService.runBatch(
                preset,
                List.of(imagePath.toFile()),
                outputDirectory,
                EditorExportService.BatchExportFormat.APNG,
                3,
                null);

        assertTrue(Files.exists(outputDirectory.resolve("source_breath_sheet.png")));
        assertTrue(Files.exists(outputDirectory.resolve("source_breath_sheet.json")));
        assertTrue(Files.exists(outputDirectory.resolve("source_breath_sheet.atlas")));
        assertTrue(Files.exists(outputDirectory.resolve("source_breath_apng.png")));
        assertFalse(Files.exists(outputDirectory.resolve("source_breath.png")));
    }

    @Test
    public void helpJsonResourcesLoadHtmlPagesWithImages() throws Exception {
        HelpContent tutorial = HelpContent.load("/help/tutorial.json");
        HelpContent about = HelpContent.load("/help/about.json");

        assertEquals("Breath Tutorial", tutorial.title());
        assertEquals(3, tutorial.pages().size());
        assertTrue(tutorial.pages().get(0).html().contains("<img src='../test_sprite/chips.png'"));
        assertTrue("Image paths need a resource base URL to render from JEditorPane", tutorial.baseUrl().toString().contains("/help/"));
        assertEquals("About Breath", about.title());
        assertEquals(1, about.pages().size());
        assertTrue(about.pages().get(0).html().contains("Breath is a Java/Swing tool"));
    }

    @Test
    public void tutorialSessionCopiesAndConsumesSnapshot() {
        EditorTutorialSession session = new EditorTutorialSession();
        List<ControlPoint> points = new java.util.ArrayList<>();
        points.add(new ControlPoint(1.0, 2.0, 3.0, 4.0, 5.0, true));
        List<ControlStroke> strokes = new java.util.ArrayList<>();
        strokes.add(new ControlStroke(List.of(new Point2D.Double(2.0, 3.0)), 0.0, 0.0, 4.0, true, false));
        EditorTutorialSession.Snapshot snapshot = new EditorTutorialSession.Snapshot(
                new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB),
                Path.of("sprite.png"),
                3.5,
                1.0,
                points,
                strokes,
                SpriteEditorPanel.ToolMode.STROKE);

        session.captureBeforeTutorial(snapshot);
        session.markTutorialLoaded();
        points.get(0).setX(99.0);
        strokes.get(0).translateBy(10.0, 10.0);
        EditorTutorialSession.Snapshot restored = session.consumeSnapshot();

        assertNotNull(restored);
        assertEquals(1.0, restored.points().get(0).x(), 0.0001);
        assertEquals(2.0, restored.strokes().get(0).points().get(0).x, 0.0001);
        assertFalse(session.hasSnapshot());
        assertEquals(SpriteEditorPanel.ToolMode.STROKE, restored.toolMode());
    }

    @Test
    public void editorFilePathAddsMissingExtensionOnly() {
        assertEquals(Path.of("breathing.json"), EditorFilePaths.withExtension(Path.of("breathing"), ".json"));
        assertEquals(Path.of("breathing.JSON"), EditorFilePaths.withExtension(Path.of("breathing.JSON"), ".json"));
    }

    @Test
    public void projectFromEditorStateEmbedsCurrentImageForPortableSave() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xff112233);

        BreathingProject project = BreathingProject.fromEditorState(
                null,
                image,
                2.5,
                1.25,
                List.of(new ControlPoint(0.0, 0.0, 1.0, 2.0, 3.0, true)),
                List.of(new ControlStroke(List.of(new Point2D.Double(0.0, 0.0)), 1.0, 2.0, 3.0, true, false)));

        BufferedImage embedded = ImageIO.read(new ByteArrayInputStream(project.embeddedImageBytes()));

        assertEquals("", project.imageName());
        assertTrue(project.hasEmbeddedImage());
        assertEquals(0xff112233, embedded.getRGB(0, 0));
        assertEquals(1, project.points().size());
        assertEquals(1, project.strokes().size());
    }

    @Test
    public void projectImageLoaderPrefersEmbeddedImageOverExistingDiskPath() throws Exception {
        Path directory = Files.createTempDirectory("breath-project-image-priority-test");
        Path diskPath = directory.resolve("sprite.png");
        BufferedImage diskImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        diskImage.setRGB(0, 0, 0xffff0000);
        ImageIO.write(diskImage, "png", diskPath.toFile());

        BufferedImage embeddedImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        embeddedImage.setRGB(0, 0, 0xff0000ff);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(embeddedImage, "png", output);
        String embeddedBase64 = Base64.getEncoder().encodeToString(output.toByteArray());

        BreathingProject project = new BreathingProject(diskPath, "sprite.png", embeddedBase64, 2.0, 1.0, List.of(), List.of());
        EditorImageLoader.LoadedImage loaded = EditorImageLoader.loadProjectImage(project);

        assertEquals(0xff0000ff, loaded.image().getRGB(0, 0));
        assertEquals(diskPath, loaded.imagePath());
        assertEquals("sprite.png", loaded.label());
    }

    @Test
    public void imageControlBoundsDetectsPointsAndStrokeVerticesOutsideImage() {
        BufferedImage image = new BufferedImage(10, 8, BufferedImage.TYPE_INT_ARGB);
        List<ControlPoint> points = List.of(new ControlPoint(9.0, 7.0, 0.0, 0.0, 2.0, true));
        List<ControlStroke> strokes = List.of(new ControlStroke(
                List.of(new Point2D.Double(2.0, 3.0), new Point2D.Double(10.0, 4.0)),
                0.0,
                0.0,
                2.0,
                true,
                false));

        assertFalse(EditorImageControlBounds.controlsFitImage(points, strokes, image));
    }

    @Test
    public void imageControlBoundsClampsPointsAndStrokeVerticesIntoImage() {
        BufferedImage image = new BufferedImage(10, 8, BufferedImage.TYPE_INT_ARGB);
        List<ControlPoint> points = new java.util.ArrayList<>();
        points.add(new ControlPoint(-5.0, 20.0, 0.0, 0.0, 2.0, true));
        List<ControlStroke> strokes = new java.util.ArrayList<>();
        strokes.add(new ControlStroke(
                List.of(new Point2D.Double(-2.0, 3.0), new Point2D.Double(20.0, 12.0)),
                0.0,
                0.0,
                2.0,
                true,
                false));

        EditorImageControlBounds.clampControlsToImage(points, strokes, image);

        assertEquals(0.0, points.get(0).x(), 0.0001);
        assertEquals(7.0, points.get(0).y(), 0.0001);
        assertEquals(0.0, strokes.get(0).points().get(0).x, 0.0001);
        assertEquals(9.0, strokes.get(0).points().get(1).x, 0.0001);
        assertTrue(EditorImageControlBounds.controlsFitImage(points, strokes, image));
    }

    @Test
    public void livePreviewRequestCopiesMutableControlsForBackgroundRendering() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        List<ControlPoint> points = new java.util.ArrayList<>();
        points.add(new ControlPoint(1.0, 2.0, 3.0, 4.0, 5.0, true, false, 0x3366cc, 2.0, 1.5));
        List<ControlStroke> strokes = new java.util.ArrayList<>();
        strokes.add(new ControlStroke(
                List.of(new Point2D.Double(2.0, 3.0), new Point2D.Double(4.0, 5.0)),
                6.0,
                7.0,
                8.0,
                true,
                false,
                0xcc6633,
                0.75));

        EditorLivePreview.RenderRequest request = new EditorLivePreview.RenderRequest(image, points, strokes, 0.5, -3.0);
        points.get(0).setX(99.0);
        points.get(0).setColorRgb(0xffffff);
        strokes.get(0).translateBy(20.0, 30.0);
        strokes.get(0).setColorRgb(0xffffff);

        assertEquals(1.0, request.points().get(0).x(), 0.0001);
        assertEquals(0x3366cc, request.points().get(0).colorRgb());
        assertEquals(2.0, request.strokes().get(0).points().get(0).x, 0.0001);
        assertEquals(0xcc6633, request.strokes().get(0).colorRgb());
        assertEquals(0.0, request.breathingStrength(), 0.0001);
    }

    @Test
    public void livePreviewTreatsCooperativeCancellationAsNonError() {
        assertTrue(EditorLivePreview.isCancellation(new java.util.concurrent.CancellationException("cancelled")));
        assertFalse(EditorLivePreview.isCancellation(new IOException("real error")));
    }

    @Test
    public void animationPreviewRequestCopiesMutableControlsForBackgroundRendering() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        List<ControlPoint> points = new java.util.ArrayList<>();
        points.add(new ControlPoint(3.0, 4.0, 1.0, 2.0, 6.0, true, false, 0x112233, 2.0, null));
        List<ControlStroke> strokes = new java.util.ArrayList<>();
        strokes.add(new ControlStroke(
                List.of(new Point2D.Double(5.0, 6.0), new Point2D.Double(7.0, 8.0)),
                2.0,
                3.0,
                4.0,
                true,
                false,
                0x445566,
                1.25));

        EditorAnimationPreviewRenderer.RenderRequest request = new EditorAnimationPreviewRenderer.RenderRequest(
                image, points, strokes, -4, -2.0, 0.0);
        points.get(0).setY(99.0);
        strokes.get(0).translateBy(40.0, 50.0);

        assertEquals(4.0, request.points().get(0).y(), 0.0001);
        assertEquals(5.0, request.strokes().get(0).points().get(0).x, 0.0001);
        assertEquals(1, request.frameCount());
        assertEquals(0.0, request.breathingStrength(), 0.0001);
        assertEquals(0.25, request.durationSeconds(), 0.0001);
    }

    @Test
    public void tutorialProjectEmbedsChipsImageAndPoints() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("tutorial/chips_breath_project.json")) {
            assertNotNull("Chips tutorial JSON should be bundled", input);
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            BreathingProject project = BreathingProject.parse(json, null);

            assertEquals("chips.png", project.imageName());
            assertTrue("Tutorial project should embed the PNG as base64", project.hasEmbeddedImage());
            assertEquals(3, project.points().size());
            assertEquals(409.1901785714286, project.points().get(0).x(), 0.01);
            assertEquals(539.9571428571428, project.points().get(0).y(), 0.01);
            assertTrue(project.points().get(0).animated());
            assertFalse(project.points().get(0).unmovable());
            assertTrue(project.points().get(2).animated());
            assertEquals(0.5, project.breathingStrength(), 0.0001);
            assertTrue(project.embeddedImageBytes().length > 1000);
        }
    }

    @Test
    public void unmovablePointsLockPixelsInsideTheirRadius() {
        ImageDeformer deformer = new ImageDeformer();
        List<ControlPoint> points = List.of(
                new ControlPoint(10.0, 10.0, 8.0, 0.0, 20.0, true),
                new ControlPoint(10.0, 10.0, 0.0, 0.0, 4.0, false, true));

        ImageDeformer.Displacement locked = deformer.calculateDisplacement(10.0, 10.0, points, 1.0);
        ImageDeformer.Displacement moving = deformer.calculateDisplacement(18.0, 10.0, points, 1.0);

        assertEquals(0.0, locked.x(), 0.0001);
        assertEquals(0.0, locked.y(), 0.0001);
        assertTrue("Pixels outside the unmovable radius should still follow animated points", moving.x() > 0.0);
    }


    @Test
    public void animatedStrokesContributeToDisplacementWithPoints() {
        ImageDeformer deformer = new ImageDeformer();
        List<ControlPoint> points = List.of(new ControlPoint(10.0, 10.0, 2.0, 0.0, 10.0, true));
        List<ControlStroke> strokes = List.of(new ControlStroke(
                List.of(new Point2D.Double(0.0, 10.0), new Point2D.Double(20.0, 10.0)),
                0.0,
                4.0,
                6.0,
                true,
                false));

        ImageDeformer.Displacement displacement = deformer.calculateDisplacement(10.0, 10.0, points, strokes, 1.0, 1.0);

        assertTrue("Point should push pixels horizontally", displacement.x() > 0.0);
        assertTrue("Stroke should push pixels vertically", displacement.y() > 0.0);
    }

    @Test
    public void unmovableStrokeOverridesAnimatedPointsAndStrokes() {
        ImageDeformer deformer = new ImageDeformer();
        List<ControlPoint> points = List.of(new ControlPoint(10.0, 10.0, 8.0, 0.0, 20.0, true));
        List<ControlStroke> strokes = List.of(
                new ControlStroke(List.of(new Point2D.Double(0.0, 10.0), new Point2D.Double(20.0, 10.0)), 0.0, 8.0, 20.0, true, false),
                new ControlStroke(List.of(new Point2D.Double(8.0, 8.0), new Point2D.Double(12.0, 12.0)), 0.0, 0.0, 4.0, false, true));

        ImageDeformer.Displacement locked = deformer.calculateDisplacement(10.0, 10.0, points, strokes, 1.0, 1.0);
        ImageDeformer.Displacement moving = deformer.calculateDisplacement(18.0, 10.0, points, strokes, 1.0, 1.0);

        assertEquals(0.0, locked.x(), 0.0001);
        assertEquals(0.0, locked.y(), 0.0001);
        assertTrue("Pixels outside the unmovable stroke should still be animated", Math.hypot(moving.x(), moving.y()) > 0.0);
    }

    @Test
    public void unmovablePointOverridesAnimatedStroke() {
        ImageDeformer deformer = new ImageDeformer();
        List<ControlPoint> points = List.of(new ControlPoint(10.0, 10.0, 0.0, 0.0, 4.0, false, true));
        List<ControlStroke> strokes = List.of(new ControlStroke(
                List.of(new Point2D.Double(0.0, 10.0), new Point2D.Double(20.0, 10.0)),
                0.0,
                8.0,
                20.0,
                true,
                false));

        ImageDeformer.Displacement locked = deformer.calculateDisplacement(10.0, 10.0, points, strokes, 1.0, 1.0);
        ImageDeformer.Displacement moving = deformer.calculateDisplacement(18.0, 10.0, points, strokes, 1.0, 1.0);

        assertEquals(0.0, locked.x(), 0.0001);
        assertEquals(0.0, locked.y(), 0.0001);
        assertTrue("Pixels outside the unmovable point should still follow the stroke", moving.y() > 0.0);
    }


    @Test
    public void unmovablePointOverridesAnimatedPointsAndAnimatedStrokesTogether() {
        ImageDeformer deformer = new ImageDeformer();
        List<ControlPoint> points = List.of(
                new ControlPoint(10.0, 10.0, 8.0, 0.0, 20.0, true),
                new ControlPoint(10.0, 10.0, 0.0, 0.0, 4.0, false, true));
        List<ControlStroke> strokes = List.of(new ControlStroke(
                List.of(new Point2D.Double(0.0, 10.0), new Point2D.Double(20.0, 10.0)),
                0.0,
                8.0,
                20.0,
                true,
                false));

        ImageDeformer.Displacement locked = deformer.calculateDisplacement(10.0, 10.0, points, strokes, 1.0, 1.0);
        ImageDeformer.Displacement moving = deformer.calculateDisplacement(18.0, 10.0, points, strokes, 1.0, 1.0);

        assertEquals(0.0, locked.x(), 0.0001);
        assertEquals(0.0, locked.y(), 0.0001);
        assertTrue("Outside the unmovable point, animated points and strokes should still contribute", moving.x() > 0.0 && moving.y() > 0.0);
    }

    @Test
    public void ratioPresetRoundTripsAndAppliesToDifferentImageSize() throws Exception {
        BufferedImage source = new BufferedImage(100, 200, BufferedImage.TYPE_INT_ARGB);
        BufferedImage target = new BufferedImage(200, 400, BufferedImage.TYPE_INT_ARGB);
        List<ControlPoint> points = List.of(new ControlPoint(90.0, 20.0, 10.0, -20.0, 30.0, true, false, 0x3366cc, 4.0, 2.0));
        List<ControlStroke> strokes = List.of(new ControlStroke(
                List.of(new Point2D.Double(10.0, 40.0), new Point2D.Double(80.0, 160.0)),
                5.0,
                -10.0,
                20.0,
                false,
                true,
                0xcc6633,
                0.5));
        Path presetFile = Files.createTempDirectory("breath-ratio-test").resolve("preset.json");

        RatioControlPreset.fromControls(source, 4.0, 1.5, points, strokes).save(presetFile);
        RatioControlPreset loaded = RatioControlPreset.load(presetFile);
        RatioControlPreset.AppliedControls applied = loaded.applyTo(target);
        String json = Files.readString(presetFile);

        JsonObject ratioRoot = JsonParser.parseString(json).getAsJsonObject();
        JsonObject firstRatioPoint = ratioRoot.getAsJsonArray("points").get(0).getAsJsonObject();
        assertEquals("breath-control-ratio-preset", ratioRoot.get("format").getAsString());
        assertEquals(0.9, firstRatioPoint.get("xRatio").getAsDouble(), 0.0001);
        assertEquals(0.1, firstRatioPoint.get("yRatio").getAsDouble(), 0.0001);
        assertEquals(2.0, firstRatioPoint.get("customBreathingStrength").getAsDouble(), 0.0001);
        assertEquals(4.0, loaded.durationSeconds(), 0.0001);
        assertEquals(1.5, loaded.breathingStrength(), 0.0001);
        assertEquals(1, applied.points().size());
        assertEquals(180.0, applied.points().get(0).x(), 0.0001);
        assertEquals(40.0, applied.points().get(0).y(), 0.0001);
        assertEquals(20.0, applied.points().get(0).offsetX(), 0.0001);
        assertEquals(-40.0, applied.points().get(0).offsetY(), 0.0001);
        assertEquals(60.0, applied.points().get(0).radius(), 0.0001);
        assertEquals(0x3366cc, applied.points().get(0).colorRgb());
        assertEquals(2.0, applied.points().get(0).customBreathingStrength(), 0.0001);
        assertEquals(1, applied.strokes().size());
        assertEquals(10.0, applied.strokes().get(0).offsetX(), 0.0001);
        assertEquals(-20.0, applied.strokes().get(0).offsetY(), 0.0001);
        assertEquals(40.0, applied.strokes().get(0).radius(), 0.0001);
        assertTrue(applied.strokes().get(0).unmovable());
        assertEquals(20.0, applied.strokes().get(0).points().get(0).x, 0.0001);
        assertEquals(80.0, applied.strokes().get(0).points().get(0).y, 0.0001);
        assertEquals(160.0, applied.strokes().get(0).points().get(1).x, 0.0001);
        assertEquals(320.0, applied.strokes().get(0).points().get(1).y, 0.0001);
    }

    @Test
    public void ratioPresetRejectsWrongFormat() {
        try {
            RatioControlPreset.parse("""
                    {
                      \"format\": \"breath-project\",
                      \"version\": 1,
                      \"points\": [],
                      \"strokes\": []
                    }
                    """);
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("breath-control-ratio-preset"));
            return;
        }
        throw new AssertionError("Wrong ratio preset format should be rejected");
    }

    @Test
    public void controlStrokeTranslateMovesPointsAndBounds() {
        ControlStroke stroke = new ControlStroke(
                List.of(new Point2D.Double(1.0, 2.0), new Point2D.Double(5.0, 6.0)),
                0.0,
                0.0,
                2.0,
                true,
                false);

        stroke.translateBy(3.0, -1.5);

        assertEquals(4.0, stroke.points().get(0).x, 0.0001);
        assertEquals(0.5, stroke.points().get(0).y, 0.0001);
        assertEquals(8.0, stroke.points().get(1).x, 0.0001);
        assertEquals(4.5, stroke.points().get(1).y, 0.0001);
        assertTrue(stroke.boundsMayContain(4.0, 0.5, 0.0));
        assertFalse(stroke.boundsMayContain(1.0, 2.0, 0.0));
    }

    @Test
    public void spriteEditorPanelTranslatesSelectedStrokeWithinImage() {
        List<ControlPoint> points = new java.util.ArrayList<>();
        List<ControlStroke> strokes = new java.util.ArrayList<>();
        List<ControlStroke> loadedStrokes = List.of(new ControlStroke(
                List.of(new Point2D.Double(2.0, 3.0), new Point2D.Double(5.0, 7.0)),
                0.0,
                0.0,
                2.0,
                true,
                false));
        SpriteEditorPanel panel = new SpriteEditorPanel(points, strokes);
        panel.setToolMode(SpriteEditorPanel.ToolMode.STROKE);
        panel.setImage(new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB));
        panel.setControls(List.of(), loadedStrokes);

        boolean moved = panel.translateSelectedControls(20.0, 20.0);
        boolean clampedNoop = panel.translateSelectedControls(20.0, 20.0);

        assertTrue(moved);
        assertFalse(clampedNoop);
        assertEquals(8.0, strokes.get(0).points().get(0).x, 0.0001);
        assertEquals(7.0, strokes.get(0).points().get(0).y, 0.0001);
        assertEquals(11.0, strokes.get(0).points().get(1).x, 0.0001);
        assertEquals(11.0, strokes.get(0).points().get(1).y, 0.0001);
    }

    @Test
    public void spriteEditorPanelCanLoadImageWithoutClearingControls() {
        List<ControlPoint> points = new java.util.ArrayList<>();
        List<ControlStroke> strokes = new java.util.ArrayList<>();
        SpriteEditorPanel panel = new SpriteEditorPanel(points, strokes);
        panel.setImage(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));
        panel.setControls(
                List.of(new ControlPoint(4.0, 5.0, 0.0, 0.0, 3.0, true)),
                List.of(new ControlStroke(List.of(new Point2D.Double(2.0, 2.0), new Point2D.Double(8.0, 8.0)), 0.0, 0.0, 2.0, true, false)));

        panel.setImage(new BufferedImage(32, 24, BufferedImage.TYPE_INT_ARGB), false);

        assertEquals(1, points.size());
        assertEquals(1, strokes.size());
        assertEquals(4.0, points.get(0).x(), 0.0001);
        assertEquals(8.0, strokes.get(0).points().get(1).x, 0.0001);
    }

    @Test
    public void controlStrokeClampPointsToImageUpdatesVerticesAndBounds() {
        ControlStroke stroke = new ControlStroke(
                List.of(new Point2D.Double(-5.0, 3.0), new Point2D.Double(20.0, 30.0)),
                0.0,
                0.0,
                2.0,
                true,
                false);

        stroke.clampPointsToImage(10, 12);

        assertEquals(0.0, stroke.points().get(0).x, 0.0001);
        assertEquals(3.0, stroke.points().get(0).y, 0.0001);
        assertEquals(9.0, stroke.points().get(1).x, 0.0001);
        assertEquals(11.0, stroke.points().get(1).y, 0.0001);
        assertTrue(stroke.boundsMayContain(9.0, 11.0, 0.0));
        assertFalse(stroke.boundsMayContain(20.0, 30.0, 0.0));
    }

    @Test
    public void gifExportRestoresBackgroundBetweenFrames() throws Exception {
        BufferedImage first = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        BufferedImage second = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        first.setRGB(1, 1, 0xffff0000);
        second.setRGB(2, 2, 0xff00ff00);
        Path gif = Files.createTempDirectory("breath-gif-metadata-test").resolve("anim.gif");

        AnimationExporter.writeGif(List.of(first, second), gif, 1.0);

        try (ImageInputStream input = ImageIO.createImageInputStream(gif.toFile())) {
            ImageReader reader = ImageIO.getImageReadersBySuffix("gif").next();
            try {
                reader.setInput(input);
                IIOMetadata metadata = reader.getImageMetadata(0);
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());
                IIOMetadataNode graphicsControl = metadataNode(root, "GraphicControlExtension");

                assertEquals("restoreToBackgroundColor", graphicsControl.getAttribute("disposalMethod"));
            } finally {
                reader.dispose();
            }
        }
    }

    @Test
    public void projectJsonRoundTripsFreeStrokes() throws Exception {
        Path directory = Files.createTempDirectory("breath-stroke-project-test");
        Path image = directory.resolve("chips.png");
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("test_sprite/chips.png")) {
            assertNotNull("Chips test sprite resource should exist", input);
            Files.copy(input, image);
        }
        Path projectFile = directory.resolve("project.json");
        List<ControlStroke> strokes = List.of(new ControlStroke(
                List.of(new Point2D.Double(1.0, 2.0), new Point2D.Double(3.0, 4.0)),
                5.0,
                -6.0,
                7.0,
                true,
                true));

        new BreathingProject(image, image.getFileName().toString(), "", 3.5, 1.0, List.of(), strokes).save(projectFile);
        BreathingProject loaded = BreathingProject.load(projectFile);

        assertEquals(0, loaded.points().size());
        assertEquals(1, loaded.strokes().size());
        assertEquals(2, loaded.strokes().get(0).pointCount());
        assertEquals(5.0, loaded.strokes().get(0).offsetX(), 0.0001);
        assertEquals(-6.0, loaded.strokes().get(0).offsetY(), 0.0001);
        assertEquals(7.0, loaded.strokes().get(0).radius(), 0.0001);
        assertFalse(loaded.strokes().get(0).animated());
        assertTrue(loaded.strokes().get(0).unmovable());
        assertTrue(Files.readString(projectFile).contains("\"strokes\""));
    }


    @Test
    public void projectJsonSaveContainsCompletePointAndStrokeState() throws Exception {
        Path directory = Files.createTempDirectory("breath-complete-project-test");
        Path image = directory.resolve("source.png");
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("test_sprite/chips.png")) {
            assertNotNull("Chips test sprite resource should exist", input);
            Files.copy(input, image);
        }
        Path projectFile = directory.resolve("full-project.json");
        List<ControlPoint> points = List.of(
                new ControlPoint(11.25, 22.5, -3.5, 4.25, 12.75, true, false, 0x3366cc, 3.5, 2.25),
                new ControlPoint(33.0, 44.0, 0.0, 0.0, 9.5, false, true, 0xcc6633, 6.0, null));
        List<ControlStroke> strokes = List.of(
                new ControlStroke(
                        List.of(
                                new Point2D.Double(1.0, 2.0),
                                new Point2D.Double(3.5, 4.5),
                                new Point2D.Double(6.0, 8.0)),
                        7.25,
                        -8.5,
                        18.25,
                        true,
                        false,
                        0x22aa66,
                        0.5),
                new ControlStroke(
                        List.of(
                                new Point2D.Double(50.0, 51.0),
                                new Point2D.Double(52.0, 53.0)),
                        0.0,
                        0.0,
                        6.0,
                        false,
                        true,
                        0xaa2266));

        String embeddedImage = Base64.getEncoder().encodeToString(Files.readAllBytes(image));
        new BreathingProject(image, "source.png", embeddedImage, 4.25, 1.75, points, strokes).save(projectFile);
        String json = Files.readString(projectFile);
        BreathingProject loaded = BreathingProject.load(projectFile);

        assertTrue(json.contains("\"image\": \"source.png\""));
        assertTrue(json.contains("\"imageName\": \"source.png\""));
        assertTrue(json.contains("\"imageBase64\": \""));
        assertFalse(json.contains("\"imageBase64\": \"\""));
        JsonObject projectRoot = JsonParser.parseString(json).getAsJsonObject();
        JsonObject firstPoint = projectRoot.getAsJsonArray("points").get(0).getAsJsonObject();
        JsonObject secondPoint = projectRoot.getAsJsonArray("points").get(1).getAsJsonObject();
        JsonObject firstStroke = projectRoot.getAsJsonArray("strokes").get(0).getAsJsonObject();
        JsonObject secondStroke = projectRoot.getAsJsonArray("strokes").get(1).getAsJsonObject();
        assertEquals(4.25, projectRoot.get("duration").getAsDouble(), 0.0001);
        assertEquals(1.75, projectRoot.get("breathingStrength").getAsDouble(), 0.0001);
        assertEquals(11.25, firstPoint.get("x").getAsDouble(), 0.0001);
        assertEquals(22.5, firstPoint.get("y").getAsDouble(), 0.0001);
        assertEquals(-3.5, firstPoint.get("offsetX").getAsDouble(), 0.0001);
        assertEquals(4.25, firstPoint.get("offsetY").getAsDouble(), 0.0001);
        assertEquals(12.75, firstPoint.get("radius").getAsDouble(), 0.0001);
        assertTrue(firstPoint.get("animated").getAsBoolean());
        assertTrue(secondPoint.get("unmovable").getAsBoolean());
        assertEquals("#3366CC", firstPoint.get("color").getAsString());
        assertEquals("#CC6633", secondPoint.get("color").getAsString());
        assertEquals(3.5, firstPoint.get("outlineWidth").getAsDouble(), 0.0001);
        assertEquals(6.0, secondPoint.get("outlineWidth").getAsDouble(), 0.0001);
        assertEquals(2.25, firstPoint.get("customBreathingStrength").getAsDouble(), 0.0001);
        assertTrue(secondPoint.get("customBreathingStrength").isJsonNull());
        assertEquals("#22AA66", firstStroke.get("color").getAsString());
        assertEquals("#AA2266", secondStroke.get("color").getAsString());
        assertEquals(0.5, firstStroke.get("customBreathingStrength").getAsDouble(), 0.0001);
        assertEquals(3.5, firstStroke.getAsJsonArray("points").get(1).getAsJsonObject().get("x").getAsDouble(), 0.0001);
        assertEquals(53.0, secondStroke.getAsJsonArray("points").get(1).getAsJsonObject().get("y").getAsDouble(), 0.0001);

        assertEquals(image.normalize(), loaded.imagePath());
        assertEquals("source.png", loaded.imageName());
        assertTrue("Saved project should embed the source PNG", loaded.hasEmbeddedImage());
        assertTrue(loaded.embeddedImageBytes().length > 1000);
        assertEquals(4.25, loaded.durationSeconds(), 0.0001);
        assertEquals(1.75, loaded.breathingStrength(), 0.0001);
        assertEquals(2, loaded.points().size());
        assertControlPoint(points.get(0), loaded.points().get(0));
        assertControlPoint(points.get(1), loaded.points().get(1));
        assertEquals(2, loaded.strokes().size());
        assertControlStroke(strokes.get(0), loaded.strokes().get(0));
        assertControlStroke(strokes.get(1), loaded.strokes().get(1));
    }

    @Test
    public void projectJsonWithoutStrokesStillLoadsAsEmptyStrokeList() {
        String oldProjectJson = """
                {
                  \"image\": \"legacy.png\",
                  \"imageName\": \"legacy.png\",
                  \"imageBase64\": \"\",
                  \"duration\": 2.500,
                  \"breathingStrength\": 0.750,
                  \"points\": [
                    {
                      \"x\": 10.000,
                      \"y\": 20.000,
                      \"offsetX\": 1.000,
                      \"offsetY\": -2.000,
                      \"radius\": 30.000,
                      \"animated\": true,
                      \"unmovable\": false
                    }
                  ]
                }
                """;

        BreathingProject loaded = BreathingProject.parse(oldProjectJson, Path.of("/tmp"));

        assertEquals(Path.of("/tmp/legacy.png"), loaded.imagePath());
        assertEquals("legacy.png", loaded.imageName());
        assertEquals(2.5, loaded.durationSeconds(), 0.0001);
        assertEquals(0.75, loaded.breathingStrength(), 0.0001);
        assertEquals(1, loaded.points().size());
        assertEquals(ControlPoint.DEFAULT_COLOR_RGB, loaded.points().get(0).colorRgb());
        assertEquals(ControlPoint.DEFAULT_OUTLINE_WIDTH, loaded.points().get(0).outlineWidth(), 0.0001);
        assertFalse(loaded.points().get(0).hasCustomBreathingStrength());
        assertEquals(0, loaded.strokes().size());
    }

    @Test
    public void customBreathingStrengthOverridesGlobalPerControl() {
        ImageDeformer deformer = new ImageDeformer();
        List<ControlPoint> points = List.of(new ControlPoint(10.0, 10.0, 10.0, 0.0, 10.0, true, false, 0x3366cc, 1.0, 0.5));
        List<ControlStroke> strokes = List.of(new ControlStroke(
                List.of(new Point2D.Double(0.0, 10.0), new Point2D.Double(20.0, 10.0)),
                0.0,
                10.0,
                10.0,
                true,
                false,
                0x22aa66,
                0.25));

        ImageDeformer.Displacement displacement = deformer.calculateDisplacement(10.0, 10.0, points, strokes, 1.0, 4.0);

        assertEquals(2.5, displacement.x(), 0.0001);
        assertEquals(1.25, displacement.y(), 0.0001);
    }

    @Test
    public void warpAngleRotatesOffsetAndKeepsDistance() {
        ControlPoint point = new ControlPoint(0.0, 0.0, 0.0, -4.0, 10.0, true);

        assertEquals(ControlPoint.DEFAULT_WARP_ANGLE_DEGREES, point.warpAngleDegrees(), 0.0001);
        assertEquals(4.0, point.warpDistance(), 0.0001);

        point.setWarpAngleDegrees(0.0);

        assertEquals(4.0, point.offsetX(), 0.0001);
        assertEquals(0.0, point.offsetY(), 0.0001);
        assertEquals(4.0, point.warpDistance(), 0.0001);
    }

    @Test
    public void animatedAndUnmovableAreMutuallyExclusiveOnControls() {
        ControlPoint point = new ControlPoint(1.0, 2.0, 3.0, 4.0, 5.0, true, true);
        assertFalse(point.animated());
        assertTrue(point.unmovable());

        point.setAnimated(true);
        assertTrue(point.animated());
        assertFalse(point.unmovable());

        point.setUnmovable(true);
        assertFalse(point.animated());
        assertTrue(point.unmovable());

        ControlStroke stroke = new ControlStroke(
                List.of(new Point2D.Double(1.0, 2.0)),
                3.0,
                4.0,
                5.0,
                true,
                true);
        assertFalse(stroke.animated());
        assertTrue(stroke.unmovable());

        stroke.setAnimated(true);
        assertTrue(stroke.animated());
        assertFalse(stroke.unmovable());

        stroke.setUnmovable(true);
        assertFalse(stroke.animated());
        assertTrue(stroke.unmovable());
    }

    @Test
    public void animatorUsesSinusoidalPhase() {
        BreathingAnimator animator = new BreathingAnimator();
        animator.setDurationSeconds(4.0);
        long start = 1_000_000_000L;
        animator.play(start);

        assertEquals(0.0, animator.phase(start), 0.0001);
        assertEquals(1.0, animator.phase(start + 1_000_000_000L), 0.0001);
        assertEquals(0.0, animator.phase(start + 2_000_000_000L), 0.0001);
        assertEquals(-1.0, animator.phase(start + 3_000_000_000L), 0.0001);
    }


    private void assertControlPoint(ControlPoint expected, ControlPoint actual) {
        assertEquals(expected.x(), actual.x(), 0.0001);
        assertEquals(expected.y(), actual.y(), 0.0001);
        assertEquals(expected.offsetX(), actual.offsetX(), 0.0001);
        assertEquals(expected.offsetY(), actual.offsetY(), 0.0001);
        assertEquals(expected.radius(), actual.radius(), 0.0001);
        assertEquals(expected.animated(), actual.animated());
        assertEquals(expected.unmovable(), actual.unmovable());
        assertEquals(expected.colorRgb(), actual.colorRgb());
        assertEquals(expected.outlineWidth(), actual.outlineWidth(), 0.0001);
        assertEquals(expected.customBreathingStrength(), actual.customBreathingStrength());
    }

    private void assertControlStroke(ControlStroke expected, ControlStroke actual) {
        assertEquals(expected.offsetX(), actual.offsetX(), 0.0001);
        assertEquals(expected.offsetY(), actual.offsetY(), 0.0001);
        assertEquals(expected.radius(), actual.radius(), 0.0001);
        assertEquals(expected.animated(), actual.animated());
        assertEquals(expected.unmovable(), actual.unmovable());
        assertEquals(expected.colorRgb(), actual.colorRgb());
        assertEquals(expected.customBreathingStrength(), actual.customBreathingStrength());
        List<Point2D.Double> expectedPoints = expected.points();
        List<Point2D.Double> actualPoints = actual.points();
        assertEquals(expectedPoints.size(), actualPoints.size());
        for (int i = 0; i < expectedPoints.size(); i++) {
            assertEquals(expectedPoints.get(i).x, actualPoints.get(i).x, 0.0001);
            assertEquals(expectedPoints.get(i).y, actualPoints.get(i).y, 0.0001);
        }
    }

    private IIOMetadataNode metadataNode(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equals(name)) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        throw new AssertionError("Missing metadata node: " + name);
    }

    private BufferedImage gradientImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = 0xff;
                int red = (x * 17 + y * 3) & 0xff;
                int green = (x * 5 + y * 19) & 0xff;
                int blue = (x * 11 + y * 7) & 0xff;
                image.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        return image;
    }

    private BufferedImage legacyDeform(
            BufferedImage source,
            List<ControlPoint> points,
            List<ControlStroke> strokes,
            double phase,
            double breathingStrength) {
        BufferedImage destination = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int width = source.getWidth();
        int height = source.getHeight();
        int[] sourcePixels = source.getRGB(0, 0, width, height, null, 0, width);
        int[] destinationPixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                double[] displacement = legacyCalculateDisplacement(x, y, points, strokes, phase, breathingStrength);
                destinationPixels[row + x] = legacyBilinearSample(
                        sourcePixels,
                        width,
                        height,
                        x - displacement[0],
                        y - displacement[1]);
            }
        }
        destination.setRGB(0, 0, width, height, destinationPixels, 0, width);
        return destination;
    }

    private double[] legacyCalculateDisplacement(
            double pixelX,
            double pixelY,
            List<ControlPoint> points,
            List<ControlStroke> strokes,
            double phase,
            double breathingStrength) {
        if (legacyIsLockedByUnmovablePoint(pixelX, pixelY, points) || legacyIsLockedByUnmovableStroke(pixelX, pixelY, strokes)) {
            return new double[] {0.0, 0.0};
        }

        double displacementX = 0.0;
        double displacementY = 0.0;
        double totalInfluence = 0.0;

        if (points != null) {
            for (ControlPoint point : points) {
                double dx = pixelX - point.x();
                double dy = pixelY - point.y();
                double radius = point.radius();
                if (Math.abs(dx) > radius || Math.abs(dy) > radius) {
                    continue;
                }
                double influence = legacyInfluence(Math.sqrt(dx * dx + dy * dy), radius);
                if (influence <= 0.0) {
                    continue;
                }
                displacementX += point.currentOffsetX(phase, breathingStrength) * influence;
                displacementY += point.currentOffsetY(phase, breathingStrength) * influence;
                totalInfluence += influence;
            }
        }

        if (strokes != null) {
            for (ControlStroke stroke : strokes) {
                if (!stroke.boundsMayContain(pixelX, pixelY, stroke.radius())) {
                    continue;
                }
                double influence = legacyInfluence(legacyDistanceToStroke(pixelX, pixelY, stroke), stroke.radius());
                if (influence <= 0.0) {
                    continue;
                }
                displacementX += stroke.currentOffsetX(phase, breathingStrength) * influence;
                displacementY += stroke.currentOffsetY(phase, breathingStrength) * influence;
                totalInfluence += influence;
            }
        }

        if (totalInfluence <= 0.0) {
            return new double[] {0.0, 0.0};
        }
        return new double[] {displacementX / totalInfluence, displacementY / totalInfluence};
    }

    private boolean legacyIsLockedByUnmovablePoint(double pixelX, double pixelY, List<ControlPoint> points) {
        if (points == null) {
            return false;
        }
        for (ControlPoint point : points) {
            if (!point.unmovable()) {
                continue;
            }
            double dx = pixelX - point.x();
            double dy = pixelY - point.y();
            double radius = point.radius();
            if (Math.abs(dx) <= radius && Math.abs(dy) <= radius && dx * dx + dy * dy <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    private boolean legacyIsLockedByUnmovableStroke(double pixelX, double pixelY, List<ControlStroke> strokes) {
        if (strokes == null) {
            return false;
        }
        for (ControlStroke stroke : strokes) {
            if (stroke.unmovable()
                    && stroke.boundsMayContain(pixelX, pixelY, stroke.radius())
                    && legacyDistanceToStroke(pixelX, pixelY, stroke) <= stroke.radius()) {
                return true;
            }
        }
        return false;
    }

    private double legacyInfluence(double distance, double radius) {
        double influence = Math.max(0.0, 1.0 - distance / radius);
        return influence * influence;
    }

    private double legacyDistanceToStroke(double pixelX, double pixelY, ControlStroke stroke) {
        List<Point2D.Double> strokePoints = stroke.pointsView();
        if (strokePoints.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        if (strokePoints.size() == 1) {
            return strokePoints.get(0).distance(pixelX, pixelY);
        }

        double best = Double.POSITIVE_INFINITY;
        for (int i = 1; i < strokePoints.size(); i++) {
            Point2D.Double a = strokePoints.get(i - 1);
            Point2D.Double b = strokePoints.get(i);
            best = Math.min(best, legacyDistanceToSegment(pixelX, pixelY, a.x, a.y, b.x, b.y));
        }
        return best;
    }

    private double legacyDistanceToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double vx = bx - ax;
        double vy = by - ay;
        double lengthSquared = vx * vx + vy * vy;
        if (lengthSquared <= 0.0001) {
            return Math.hypot(px - ax, py - ay);
        }
        double t = ((px - ax) * vx + (py - ay) * vy) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double closestX = ax + t * vx;
        double closestY = ay + t * vy;
        return Math.hypot(px - closestX, py - closestY);
    }

    private int legacyBilinearSample(int[] pixels, int width, int height, double x, double y) {
        if (x < 0.0 || y < 0.0 || x > width - 1.0 || y > height - 1.0) {
            return 0;
        }

        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int x1 = Math.min(x0 + 1, width - 1);
        int y1 = Math.min(y0 + 1, height - 1);
        double tx = x - x0;
        double ty = y - y0;

        int c00 = pixels[y0 * width + x0];
        int c10 = pixels[y0 * width + x1];
        int c01 = pixels[y1 * width + x0];
        int c11 = pixels[y1 * width + x1];

        int a = legacyInterpolate(legacyChannel(c00, 24), legacyChannel(c10, 24), legacyChannel(c01, 24), legacyChannel(c11, 24), tx, ty);
        int r = legacyInterpolate(legacyChannel(c00, 16), legacyChannel(c10, 16), legacyChannel(c01, 16), legacyChannel(c11, 16), tx, ty);
        int g = legacyInterpolate(legacyChannel(c00, 8), legacyChannel(c10, 8), legacyChannel(c01, 8), legacyChannel(c11, 8), tx, ty);
        int b = legacyInterpolate(legacyChannel(c00, 0), legacyChannel(c10, 0), legacyChannel(c01, 0), legacyChannel(c11, 0), tx, ty);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int legacyChannel(int argb, int shift) {
        return (argb >> shift) & 0xff;
    }

    private int legacyInterpolate(int c00, int c10, int c01, int c11, double tx, double ty) {
        double top = c00 + (c10 - c00) * tx;
        double bottom = c01 + (c11 - c01) * tx;
        return Math.max(0, Math.min(255, (int) Math.round(top + (bottom - top) * ty)));
    }

    private void assertImagesEqual(BufferedImage expected, BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals("Pixel mismatch at " + x + "," + y, expected.getRGB(x, y), actual.getRGB(x, y));
            }
        }
    }

    private BufferedImage loadChips() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("test_sprite/chips.png")) {
            assertNotNull("Chips test sprite resource should exist", input);
            BufferedImage sprite = ImageIO.read(input);
            assertNotNull("Chips test sprite should be readable", sprite);
            return sprite;
        }
    }

    private int countPixelDifferences(BufferedImage a, BufferedImage b) {
        int count = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countPixelDifferences(BufferedImage a, BufferedImage b, int minY, int maxY) {
        int count = 0;
        int safeMinY = Math.max(0, minY);
        int safeMaxY = Math.min(a.getHeight(), maxY);
        for (int y = safeMinY; y < safeMaxY; y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    private void assertApngHasAnimationChunks(Path path, int expectedFrames) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        assertNotEquals(-1, indexOf(bytes, "acTL"));
        assertNotEquals(-1, indexOf(bytes, "fcTL"));
        assertNotEquals(-1, indexOf(bytes, "fdAT"));

        int acTL = indexOf(bytes, "acTL");
        int frameCount = ByteBuffer.wrap(bytes, acTL + 4, 4).getInt();
        assertEquals(expectedFrames, frameCount);
    }

    private int indexOf(byte[] bytes, String needle) {
        byte[] search = needle.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i <= bytes.length - search.length; i++) {
            boolean found = true;
            for (int j = 0; j < search.length; j++) {
                if (bytes[i + j] != search[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }
}
