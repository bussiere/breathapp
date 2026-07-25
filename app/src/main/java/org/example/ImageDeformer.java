package org.example;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

public final class ImageDeformer {
    private static final int PARALLEL_PIXEL_THRESHOLD = 64_000;

    public BufferedImage deform(BufferedImage source, List<ControlPoint> points, double phase) {
        return deform(source, points, List.of(), phase, 1.0);
    }

    public BufferedImage deform(BufferedImage source, List<ControlPoint> points, double phase, double breathingStrength) {
        return deform(source, points, List.of(), phase, breathingStrength);
    }

    public BufferedImage deform(
            BufferedImage source,
            List<ControlPoint> points,
            List<ControlStroke> strokes,
            double phase,
            double breathingStrength) {
        return deform(source, points, strokes, phase, breathingStrength, () -> false);
    }

    BufferedImage deform(
            BufferedImage source,
            List<ControlPoint> points,
            List<ControlStroke> strokes,
            double phase,
            double breathingStrength,
            BooleanSupplier shouldCancel) {
        if (source == null) {
            return null;
        }
        checkCancelled(shouldCancel);

        BufferedImage sourceArgb = ensureArgb(source);
        BufferedImage destination = new BufferedImage(sourceArgb.getWidth(), sourceArgb.getHeight(), BufferedImage.TYPE_INT_ARGB);
        boolean hasPoints = points != null && !points.isEmpty();
        boolean hasStrokes = strokes != null && !strokes.isEmpty();
        if (!hasPoints && !hasStrokes) {
            Graphics2D graphics = destination.createGraphics();
            try {
                graphics.drawImage(sourceArgb, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            return destination;
        }

        int width = sourceArgb.getWidth();
        int height = sourceArgb.getHeight();
        int[] sourcePixels = pixelsOf(sourceArgb);
        int[] destinationPixels = pixelsOf(destination);
        // Starting from a source copy lets locked pixels and pixels outside the ROI skip exact no-op resampling.
        System.arraycopy(sourcePixels, 0, destinationPixels, 0, sourcePixels.length);
        FrameContext context = FrameContext.build(points, strokes, phase, breathingStrength);
        checkCancelled(shouldCancel);
        if (!context.hasMovingControls()) {
            return destination;
        }

        int minX = Math.max(0, (int) Math.floor(context.minX()));
        int minY = Math.max(0, (int) Math.floor(context.minY()));
        int maxX = Math.min(width, (int) Math.ceil(context.maxX()) + 1);
        int maxY = Math.min(height, (int) Math.ceil(context.maxY()) + 1);
        warpRows(sourcePixels, destinationPixels, width, height, minX, minY, maxX, maxY, context, shouldCancel);
        return destination;
    }

    private void warpRows(
            int[] sourcePixels,
            int[] destinationPixels,
            int width,
            int height,
            int minX,
            int minY,
            int maxX,
            int maxY,
            FrameContext context,
            BooleanSupplier shouldCancel) {
        checkCancelled(shouldCancel);
        int pixelCount = width * height;
        if (pixelCount >= PARALLEL_PIXEL_THRESHOLD) {
            // Rows are independent, so parallel rendering can speed exports without changing pixel order or math.
            IntStream.range(minY, maxY).parallel().forEach(y -> warpRow(sourcePixels, destinationPixels, width, height, minX, maxX, y, context, shouldCancel));
            return;
        }
        for (int y = minY; y < maxY; y++) {
            warpRow(sourcePixels, destinationPixels, width, height, minX, maxX, y, context, shouldCancel);
        }
    }

    private void warpRow(
            int[] sourcePixels,
            int[] destinationPixels,
            int width,
            int height,
            int minX,
            int maxX,
            int y,
            FrameContext context,
            BooleanSupplier shouldCancel) {
        checkCancelled(shouldCancel);
        int row = y * width;
        for (int x = minX; x < maxX; x++) {
            if ((x & 31) == 0) {
                checkCancelled(shouldCancel);
            }
            if (context.isLocked(x, y)) {
                continue;
            }
            double displacementX = 0.0;
            double displacementY = 0.0;
            double totalInfluence = 0.0;

            for (int i = 0; i < context.pointCount(); i++) {
                double dx = x - context.pointX(i);
                double dy = y - context.pointY(i);
                double radius = context.pointRadius(i);
                if (Math.abs(dx) > radius || Math.abs(dy) > radius) {
                    continue;
                }
                double distanceSquared = dx * dx + dy * dy;
                if (distanceSquared >= context.pointRadiusSquared(i)) {
                    continue;
                }
                double influence = influenceFromDistanceSquared(distanceSquared, radius);
                displacementX += context.pointOffsetX(i) * influence;
                displacementY += context.pointOffsetY(i) * influence;
                totalInfluence += influence;
            }

            for (int i = 0; i < context.strokeCount(); i++) {
                StrokeData stroke = context.stroke(i);
                if (!stroke.boundsMayContain(x, y)) {
                    continue;
                }
                double distanceSquared = stroke.distanceSquaredTo(x, y);
                if (distanceSquared >= stroke.radiusSquared()) {
                    continue;
                }
                double influence = influenceFromDistanceSquared(distanceSquared, stroke.radius());
                displacementX += stroke.offsetX() * influence;
                displacementY += stroke.offsetY() * influence;
                totalInfluence += influence;
            }

            if (totalInfluence <= 0.0) {
                continue;
            }
            double sourceX = x - displacementX / totalInfluence;
            double sourceY = y - displacementY / totalInfluence;
            destinationPixels[row + x] = bilinearSample(sourcePixels, width, height, sourceX, sourceY);
        }
    }

    private void checkCancelled(BooleanSupplier shouldCancel) {
        // SwingWorker.cancel(true) alone cannot stop CPU-bound pixel loops, especially when
        // rows are running in the common pool. Polling an explicit supplier gives live preview
        // renders a cooperative abort path before exports start doing the same work.
        if ((shouldCancel != null && shouldCancel.getAsBoolean()) || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Image deformation cancelled");
        }
    }

    private BufferedImage ensureArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }
        BufferedImage argb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = argb.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return argb;
    }

    private int[] pixelsOf(BufferedImage image) {
        return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    Displacement calculateDisplacement(double pixelX, double pixelY, List<ControlPoint> points, double phase) {
        return calculateDisplacement(pixelX, pixelY, points, List.of(), phase, 1.0);
    }

    Displacement calculateDisplacement(double pixelX, double pixelY, List<ControlPoint> points, double phase, double breathingStrength) {
        return calculateDisplacement(pixelX, pixelY, points, List.of(), phase, breathingStrength);
    }

    Displacement calculateDisplacement(
            double pixelX,
            double pixelY,
            List<ControlPoint> points,
            List<ControlStroke> strokes,
            double phase,
            double breathingStrength) {
        FrameContext context = FrameContext.build(points, strokes, phase, breathingStrength);
        if (context.isLocked(pixelX, pixelY)) {
            return new Displacement(0.0, 0.0);
        }

        double displacementX = 0.0;
        double displacementY = 0.0;
        double totalInfluence = 0.0;

        for (int i = 0; i < context.pointCount(); i++) {
            double dx = pixelX - context.pointX(i);
            double dy = pixelY - context.pointY(i);
            double radius = context.pointRadius(i);
            if (Math.abs(dx) > radius || Math.abs(dy) > radius) {
                continue;
            }
            double distanceSquared = dx * dx + dy * dy;
            if (distanceSquared >= context.pointRadiusSquared(i)) {
                continue;
            }
            double influence = influenceFromDistanceSquared(distanceSquared, radius);
            displacementX += context.pointOffsetX(i) * influence;
            displacementY += context.pointOffsetY(i) * influence;
            totalInfluence += influence;
        }

        for (int i = 0; i < context.strokeCount(); i++) {
            StrokeData stroke = context.stroke(i);
            if (!stroke.boundsMayContain(pixelX, pixelY)) {
                continue;
            }
            double distanceSquared = stroke.distanceSquaredTo(pixelX, pixelY);
            if (distanceSquared >= stroke.radiusSquared()) {
                continue;
            }
            double influence = influenceFromDistanceSquared(distanceSquared, stroke.radius());
            displacementX += stroke.offsetX() * influence;
            displacementY += stroke.offsetY() * influence;
            totalInfluence += influence;
        }

        if (totalInfluence <= 0.0) {
            return new Displacement(0.0, 0.0);
        }
        return new Displacement(displacementX / totalInfluence, displacementY / totalInfluence);
    }

    private double influence(double distance, double radius) {
        double influence = Math.max(0.0, 1.0 - distance / radius);
        return influence * influence;
    }

    private double influenceFromDistanceSquared(double distanceSquared, double radius) {
        return influence(Math.sqrt(distanceSquared), radius);
    }

    double distanceToStroke(double pixelX, double pixelY, ControlStroke stroke) {
        return PolylineGeometry.distanceToPolyline(pixelX, pixelY, stroke.pointsView());
    }

    int bilinearSample(BufferedImage image, double x, double y) {
        int width = image.getWidth();
        int height = image.getHeight();
        return bilinearSample(image.getRGB(0, 0, width, height, null, 0, width), width, height, x, y);
    }

    private int bilinearSample(int[] pixels, int width, int height, double x, double y) {
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

        int a = interpolate(channel(c00, 24), channel(c10, 24), channel(c01, 24), channel(c11, 24), tx, ty);
        int r = interpolate(channel(c00, 16), channel(c10, 16), channel(c01, 16), channel(c11, 16), tx, ty);
        int g = interpolate(channel(c00, 8), channel(c10, 8), channel(c01, 8), channel(c11, 8), tx, ty);
        int b = interpolate(channel(c00, 0), channel(c10, 0), channel(c01, 0), channel(c11, 0), tx, ty);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int channel(int argb, int shift) {
        return (argb >> shift) & 0xff;
    }

    private int interpolate(int c00, int c10, int c01, int c11, double tx, double ty) {
        double top = c00 + (c10 - c00) * tx;
        double bottom = c01 + (c11 - c01) * tx;
        return clamp((int) Math.round(top + (bottom - top) * ty));
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    record Displacement(double x, double y) {
    }

    // The context freezes mutable controls into primitive arrays so the pixel loop stays predictable and cheap.
    private static final class FrameContext {
        private final double[] pointX;
        private final double[] pointY;
        private final double[] pointRadius;
        private final double[] pointRadiusSquared;
        private final double[] pointOffsetX;
        private final double[] pointOffsetY;
        private final double[] lockPointX;
        private final double[] lockPointY;
        private final double[] lockPointRadius;
        private final double[] lockPointRadiusSquared;
        private final StrokeData[] strokes;
        private final StrokeData[] lockStrokes;
        private final int pointCount;
        private final int lockPointCount;
        private final int strokeCount;
        private final int lockStrokeCount;
        private final boolean hasMovingControls;
        private final double minX;
        private final double minY;
        private final double maxX;
        private final double maxY;

        private FrameContext(
                double[] pointX,
                double[] pointY,
                double[] pointRadius,
                double[] pointRadiusSquared,
                double[] pointOffsetX,
                double[] pointOffsetY,
                double[] lockPointX,
                double[] lockPointY,
                double[] lockPointRadius,
                double[] lockPointRadiusSquared,
                StrokeData[] strokes,
                StrokeData[] lockStrokes,
                int pointCount,
                int lockPointCount,
                int strokeCount,
                int lockStrokeCount,
                boolean hasMovingControls,
                double minX,
                double minY,
                double maxX,
                double maxY) {
            this.pointX = pointX;
            this.pointY = pointY;
            this.pointRadius = pointRadius;
            this.pointRadiusSquared = pointRadiusSquared;
            this.pointOffsetX = pointOffsetX;
            this.pointOffsetY = pointOffsetY;
            this.lockPointX = lockPointX;
            this.lockPointY = lockPointY;
            this.lockPointRadius = lockPointRadius;
            this.lockPointRadiusSquared = lockPointRadiusSquared;
            this.strokes = strokes;
            this.lockStrokes = lockStrokes;
            this.pointCount = pointCount;
            this.lockPointCount = lockPointCount;
            this.strokeCount = strokeCount;
            this.lockStrokeCount = lockStrokeCount;
            this.hasMovingControls = hasMovingControls;
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        static FrameContext build(List<ControlPoint> points, List<ControlStroke> strokes, double phase, double breathingStrength) {
            int pointCapacity = points == null ? 0 : points.size();
            int strokeCapacity = strokes == null ? 0 : strokes.size();
            double[] pointX = new double[pointCapacity];
            double[] pointY = new double[pointCapacity];
            double[] pointRadius = new double[pointCapacity];
            double[] pointRadiusSquared = new double[pointCapacity];
            double[] pointOffsetX = new double[pointCapacity];
            double[] pointOffsetY = new double[pointCapacity];
            double[] lockPointX = new double[pointCapacity];
            double[] lockPointY = new double[pointCapacity];
            double[] lockPointRadius = new double[pointCapacity];
            double[] lockPointRadiusSquared = new double[pointCapacity];
            StrokeData[] strokeData = new StrokeData[strokeCapacity];
            StrokeData[] lockStrokeData = new StrokeData[strokeCapacity];

            int pointCount = 0;
            int lockPointCount = 0;
            int strokeCount = 0;
            int lockStrokeCount = 0;
            boolean hasMovingControls = false;
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;

            if (points != null) {
                for (ControlPoint point : points) {
                    double radius = point.radius();
                    if (point.unmovable()) {
                        lockPointX[lockPointCount] = point.x();
                        lockPointY[lockPointCount] = point.y();
                        lockPointRadius[lockPointCount] = radius;
                        lockPointRadiusSquared[lockPointCount] = radius * radius;
                        lockPointCount++;
                        continue;
                    }
                    double offsetX = point.currentOffsetX(phase, breathingStrength);
                    double offsetY = point.currentOffsetY(phase, breathingStrength);
                    pointX[pointCount] = point.x();
                    pointY[pointCount] = point.y();
                    pointRadius[pointCount] = radius;
                    pointRadiusSquared[pointCount] = radius * radius;
                    pointOffsetX[pointCount] = offsetX;
                    pointOffsetY[pointCount] = offsetY;
                    pointCount++;
                    if (offsetX != 0.0 || offsetY != 0.0) {
                        hasMovingControls = true;
                    }
                    minX = Math.min(minX, point.x() - radius);
                    minY = Math.min(minY, point.y() - radius);
                    maxX = Math.max(maxX, point.x() + radius);
                    maxY = Math.max(maxY, point.y() + radius);
                }
            }

            if (strokes != null) {
                for (ControlStroke stroke : strokes) {
                    StrokeData data = StrokeData.from(stroke, phase, breathingStrength);
                    if (data.pointCount() == 0) {
                        continue;
                    }
                    if (stroke.unmovable()) {
                        lockStrokeData[lockStrokeCount++] = data;
                        continue;
                    }
                    strokeData[strokeCount++] = data;
                    if (data.offsetX() != 0.0 || data.offsetY() != 0.0) {
                        hasMovingControls = true;
                    }
                    minX = Math.min(minX, data.minX());
                    minY = Math.min(minY, data.minY());
                    maxX = Math.max(maxX, data.maxX());
                    maxY = Math.max(maxY, data.maxY());
                }
            }

            if (pointCount == 0 && strokeCount == 0) {
                minX = 0.0;
                minY = 0.0;
                maxX = 0.0;
                maxY = 0.0;
            }
            return new FrameContext(
                    pointX,
                    pointY,
                    pointRadius,
                    pointRadiusSquared,
                    pointOffsetX,
                    pointOffsetY,
                    lockPointX,
                    lockPointY,
                    lockPointRadius,
                    lockPointRadiusSquared,
                    strokeData,
                    lockStrokeData,
                    pointCount,
                    lockPointCount,
                    strokeCount,
                    lockStrokeCount,
                    hasMovingControls,
                    minX,
                    minY,
                    maxX,
                    maxY);
        }

        boolean isLocked(double pixelX, double pixelY) {
            for (int i = 0; i < lockPointCount; i++) {
                double dx = pixelX - lockPointX[i];
                double dy = pixelY - lockPointY[i];
                double radius = lockPointRadius[i];
                if (Math.abs(dx) <= radius && Math.abs(dy) <= radius && dx * dx + dy * dy <= lockPointRadiusSquared[i]) {
                    return true;
                }
            }
            for (int i = 0; i < lockStrokeCount; i++) {
                StrokeData stroke = lockStrokes[i];
                if (stroke.boundsMayContain(pixelX, pixelY) && stroke.distanceSquaredTo(pixelX, pixelY) <= stroke.radiusSquared()) {
                    return true;
                }
            }
            return false;
        }

        boolean hasMovingControls() {
            return hasMovingControls;
        }

        double minX() {
            return minX;
        }

        double minY() {
            return minY;
        }

        double maxX() {
            return maxX;
        }

        double maxY() {
            return maxY;
        }

        int pointCount() {
            return pointCount;
        }

        double pointX(int index) {
            return pointX[index];
        }

        double pointY(int index) {
            return pointY[index];
        }

        double pointRadius(int index) {
            return pointRadius[index];
        }

        double pointRadiusSquared(int index) {
            return pointRadiusSquared[index];
        }

        double pointOffsetX(int index) {
            return pointOffsetX[index];
        }

        double pointOffsetY(int index) {
            return pointOffsetY[index];
        }

        int strokeCount() {
            return strokeCount;
        }

        StrokeData stroke(int index) {
            return strokes[index];
        }
    }

    // Freehand strokes are flattened once per frame because walking Point2D objects per pixel scales badly.
    private static final class StrokeData {
        private final double[] pointX;
        private final double[] pointY;
        private final int pointCount;
        private final double radius;
        private final double radiusSquared;
        private final double offsetX;
        private final double offsetY;
        private final double minX;
        private final double minY;
        private final double maxX;
        private final double maxY;

        private StrokeData(
                double[] pointX,
                double[] pointY,
                int pointCount,
                double radius,
                double radiusSquared,
                double offsetX,
                double offsetY,
                double minX,
                double minY,
                double maxX,
                double maxY) {
            this.pointX = pointX;
            this.pointY = pointY;
            this.pointCount = pointCount;
            this.radius = radius;
            this.radiusSquared = radiusSquared;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        static StrokeData from(ControlStroke stroke, double phase, double breathingStrength) {
            List<Point2D.Double> points = stroke.pointsView();
            int count = points.size();
            double[] pointX = new double[count];
            double[] pointY = new double[count];
            for (int i = 0; i < count; i++) {
                Point2D.Double point = points.get(i);
                pointX[i] = point.x;
                pointY[i] = point.y;
            }
            double radius = stroke.radius();
            return new StrokeData(
                    pointX,
                    pointY,
                    count,
                    radius,
                    radius * radius,
                    stroke.currentOffsetX(phase, breathingStrength),
                    stroke.currentOffsetY(phase, breathingStrength),
                    min(pointX, count) - radius,
                    min(pointY, count) - radius,
                    max(pointX, count) + radius,
                    max(pointY, count) + radius);
        }

        boolean boundsMayContain(double x, double y) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }

        double distanceSquaredTo(double pixelX, double pixelY) {
            return PolylineGeometry.distanceSquaredToPolyline(pixelX, pixelY, pointX, pointY, pointCount);
        }

        int pointCount() {
            return pointCount;
        }

        double radius() {
            return radius;
        }

        double radiusSquared() {
            return radiusSquared;
        }

        double offsetX() {
            return offsetX;
        }

        double offsetY() {
            return offsetY;
        }

        double minX() {
            return minX;
        }

        double minY() {
            return minY;
        }

        double maxX() {
            return maxX;
        }

        double maxY() {
            return maxY;
        }

        private static double min(double[] values, int count) {
            double min = Double.POSITIVE_INFINITY;
            for (int i = 0; i < count; i++) {
                min = Math.min(min, values[i]);
            }
            return min;
        }

        private static double max(double[] values, int count) {
            double max = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < count; i++) {
                max = Math.max(max, values[i]);
            }
            return max;
        }
    }
}
