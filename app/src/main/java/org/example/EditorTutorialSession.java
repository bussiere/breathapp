package org.example;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

final class EditorTutorialSession {
    private static final String CHIPS_PROJECT_RESOURCE = "tutorial/chips_breath_project.json";

    private boolean active;
    private Snapshot snapshot;

    void clear() {
        active = false;
        snapshot = null;
    }

    boolean hasSnapshot() {
        return snapshot != null;
    }

    void captureBeforeTutorial(Snapshot current) {
        if (!active && current != null && current.image() != null) {
            snapshot = current;
        }
    }

    BreathingProject loadChipsProject() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CHIPS_PROJECT_RESOURCE)) {
            if (input == null) {
                throw new IOException("Resource " + CHIPS_PROJECT_RESOURCE + " not found");
            }
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return BreathingProject.parse(json, null);
        }
    }

    void markTutorialLoaded() {
        active = true;
    }

    String loadedStatus() {
        return snapshot == null
                ? "Chips tutorial loaded: play, tune points, then export."
                : "Chips tutorial loaded. Close tutorial restores your saved image and controls.";
    }

    Snapshot consumeSnapshot() {
        Snapshot restored = snapshot;
        clear();
        return restored;
    }

    record Snapshot(
            BufferedImage image,
            Path imagePath,
            double durationSeconds,
            double breathingStrength,
            List<ControlPoint> points,
            List<ControlStroke> strokes,
            SpriteEditorPanel.ToolMode toolMode) {
        Snapshot {
            // Snapshot restoration must not observe later edits made inside the tutorial.
            points = points == null ? List.of() : points.stream().map(ControlPoint::copy).toList();
            strokes = strokes == null ? List.of() : strokes.stream().map(ControlStroke::copy).toList();
        }
    }
}
