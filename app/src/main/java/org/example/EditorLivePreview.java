package org.example;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

final class EditorLivePreview {
    private final ImageDeformer deformer = new ImageDeformer();
    private boolean dirty = true;
    private boolean queued;
    private long generation;
    private SwingWorker<RenderResult, Void> worker;

    void markDirty() {
        dirty = true;
        generation++;
    }

    void cancelActive() {
        queued = false;
        if (worker != null && !worker.isDone()) {
            // Cancelling plus bumping the generation prevents a stale worker from painting
            // over the editor after a heavier render has claimed the CPU.
            worker.cancel(true);
            generation++;
        }
    }

    void refresh(
            RenderRequest request,
            boolean exportInProgress,
            boolean animatorRunning,
            boolean hasRenderedImage,
            Consumer<RenderResult> renderedConsumer,
            Consumer<Throwable> errorConsumer,
            Runnable rerender) {
        if (request.image() == null || exportInProgress) {
            return;
        }
        if (!animatorRunning && !dirty && hasRenderedImage) {
            return;
        }
        if (worker != null && !worker.isDone()) {
            queued = true;
            return;
        }

        long renderGeneration = generation;
        dirty = false;
        queued = false;
        worker = new SwingWorker<>() {
            @Override
            protected RenderResult doInBackground() {
                BufferedImage rendered = deformer.deform(
                        request.image(),
                        request.points(),
                        request.strokes(),
                        request.phase(),
                        request.breathingStrength(),
                        this::isCancelled);
                return new RenderResult(rendered, request.phase(), request.breathingStrength());
            }

            @Override
            protected void done() {
                try {
                    RenderResult result = get();
                    if (renderGeneration == generation) {
                        renderedConsumer.accept(result);
                    }
                } catch (CancellationException ignored) {
                    // Heavy jobs intentionally cancel live previews; a later timer tick will reschedule if needed.
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    if (!isCancellation(cause)) {
                        errorConsumer.accept(cause);
                    }
                }
                if (queued || dirty) {
                    queued = false;
                    rerender.run();
                }
            }
        };
        worker.execute();
    }

    boolean dirty() {
        return dirty;
    }

    boolean queued() {
        return queued;
    }

    static boolean isCancellation(Throwable throwable) {
        return throwable instanceof CancellationException;
    }

    record RenderRequest(
            BufferedImage image,
            List<ControlPoint> points,
            List<ControlStroke> strokes,
            double phase,
            double breathingStrength) {
        RenderRequest {
            // Rendering runs off the EDT, so copy mutable controls at the request boundary.
            points = points == null ? List.of() : points.stream().map(ControlPoint::copy).toList();
            strokes = strokes == null ? List.of() : strokes.stream().map(ControlStroke::copy).toList();
            breathingStrength = Math.max(0.0, breathingStrength);
        }
    }

    record RenderResult(BufferedImage image, double phase, double breathingStrength) {
    }
}
