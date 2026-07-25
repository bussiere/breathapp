package org.example;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

final class EditorAnimationPreviewRenderer {
    void render(RenderRequest request, Consumer<RenderResult> renderedConsumer, Consumer<Throwable> errorConsumer) {
        new SwingWorker<RenderResult, Void>() {
            @Override
            protected RenderResult doInBackground() {
                List<BufferedImage> frames = AnimationExporter.renderFrames(
                        request.image(),
                        request.points(),
                        request.strokes(),
                        request.frameCount(),
                        request.breathingStrength());
                int delayMs = AnimationExporter.frameDelayMs(frames.size(), request.durationSeconds());
                return new RenderResult(frames, delayMs);
            }

            @Override
            protected void done() {
                try {
                    renderedConsumer.accept(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    errorConsumer.accept(ex);
                } catch (ExecutionException ex) {
                    errorConsumer.accept(ex.getCause() == null ? ex : ex.getCause());
                }
            }
        }.execute();
    }

    record RenderRequest(
            BufferedImage image,
            List<ControlPoint> points,
            List<ControlStroke> strokes,
            int frameCount,
            double breathingStrength,
            double durationSeconds) {
        RenderRequest {
            // Animation preview runs off the EDT, so it must not observe half-applied edits
            // while the user keeps dragging points or traits.
            points = points == null ? List.of() : points.stream().map(ControlPoint::copy).toList();
            strokes = strokes == null ? List.of() : strokes.stream().map(ControlStroke::copy).toList();
            frameCount = Math.max(1, frameCount);
            breathingStrength = Math.max(0.0, breathingStrength);
            durationSeconds = Math.max(0.25, durationSeconds);
        }
    }

    record RenderResult(List<BufferedImage> frames, int delayMs) {
        RenderResult {
            frames = List.copyOf(frames);
        }
    }
}
