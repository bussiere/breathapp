package org.example;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ControlStroke {
    public static final int DEFAULT_COLOR_RGB = 0x53beff;

    private final List<Point2D.Double> points = new ArrayList<>();
    private double offsetX;
    private double offsetY;
    private double radius;
    private boolean animated;
    private boolean unmovable;
    private int colorRgb;
    private Double customBreathingStrength;
    private double minX = Double.POSITIVE_INFINITY;
    private double minY = Double.POSITIVE_INFINITY;
    private double maxX = Double.NEGATIVE_INFINITY;
    private double maxY = Double.NEGATIVE_INFINITY;

    public ControlStroke(List<Point2D.Double> points, double offsetX, double offsetY, double radius, boolean animated, boolean unmovable) {
        this(points, offsetX, offsetY, radius, animated, unmovable, DEFAULT_COLOR_RGB);
    }

    public ControlStroke(List<Point2D.Double> points, double offsetX, double offsetY, double radius, boolean animated, boolean unmovable, int colorRgb) {
        this(points, offsetX, offsetY, radius, animated, unmovable, colorRgb, null);
    }

    public ControlStroke(
            List<Point2D.Double> points,
            double offsetX,
            double offsetY,
            double radius,
            boolean animated,
            boolean unmovable,
            int colorRgb,
            Double customBreathingStrength) {
        for (Point2D.Double point : points) {
            addRawPoint(point.x, point.y);
        }
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.radius = Math.max(1.0, radius);
        // A stroke cannot both deform pixels and lock them in place. Legacy or
        // hand-edited project files may contain both flags, so preserve pixels by
        // letting the lock win during normalization.
        this.animated = animated && !unmovable;
        this.unmovable = unmovable;
        this.colorRgb = colorRgb & 0xffffff;
        this.customBreathingStrength = sanitizeBreathingStrength(customBreathingStrength);
    }

    public ControlStroke(double x, double y, double offsetX, double offsetY, double radius, boolean animated) {
        this(List.of(new Point2D.Double(x, y)), offsetX, offsetY, radius, animated, false, DEFAULT_COLOR_RGB);
    }

    public ControlStroke copy() {
        return new ControlStroke(points, offsetX, offsetY, radius, animated, unmovable, colorRgb, customBreathingStrength);
    }

    public List<Point2D.Double> points() {
        return points.stream().map(point -> new Point2D.Double(point.x, point.y)).toList();
    }

    List<Point2D.Double> pointsView() {
        return Collections.unmodifiableList(points);
    }

    public int pointCount() {
        return points.size();
    }

    public void addPoint(double x, double y) {
        if (!points.isEmpty()) {
            Point2D.Double last = points.get(points.size() - 1);
            if (last.distance(x, y) < 2.0) {
                return;
            }
        }
        addRawPoint(x, y);
    }

    private void addRawPoint(double x, double y) {
        points.add(new Point2D.Double(x, y));
        includeInBounds(x, y);
    }

    public void translateBy(double dx, double dy) {
        if (points.isEmpty() || (dx == 0.0 && dy == 0.0)) {
            return;
        }
        for (Point2D.Double point : points) {
            point.x += dx;
            point.y += dy;
        }
        recomputeBounds();
    }

    public void clampPointsToImage(int width, int height) {
        if (points.isEmpty()) {
            return;
        }
        double maxX = Math.max(0.0, width - 1.0);
        double maxY = Math.max(0.0, height - 1.0);
        for (Point2D.Double point : points) {
            point.x = Math.max(0.0, Math.min(maxX, point.x));
            point.y = Math.max(0.0, Math.min(maxY, point.y));
        }
        // Bounds drive hit-testing and deformation ROI; recomputing here keeps a clamped
        // stroke from carrying stale off-image extents into the next preview/export.
        recomputeBounds();
    }

    private void includeInBounds(double x, double y) {
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
    }

    private void recomputeBounds() {
        minX = Double.POSITIVE_INFINITY;
        minY = Double.POSITIVE_INFINITY;
        maxX = Double.NEGATIVE_INFINITY;
        maxY = Double.NEGATIVE_INFINITY;
        for (Point2D.Double point : points) {
            includeInBounds(point.x, point.y);
        }
    }

    public boolean boundsMayContain(double x, double y, double padding) {
        if (points.isEmpty()) {
            return false;
        }
        return x >= minX - padding && x <= maxX + padding && y >= minY - padding && y <= maxY + padding;
    }

    public double centerX() {
        if (points.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (Point2D.Double point : points) {
            sum += point.x;
        }
        return sum / points.size();
    }

    public double centerY() {
        if (points.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (Point2D.Double point : points) {
            sum += point.y;
        }
        return sum / points.size();
    }

    public double offsetX() {
        return offsetX;
    }

    public void setOffsetX(double offsetX) {
        this.offsetX = offsetX;
    }

    public double offsetY() {
        return offsetY;
    }

    public void setOffsetY(double offsetY) {
        this.offsetY = offsetY;
    }

    public double radius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = Math.max(1.0, radius);
    }

    public boolean animated() {
        return animated;
    }

    public void setAnimated(boolean animated) {
        this.animated = animated;
        // Keep the invariant outside the Swing UI as project loading, tests, and
        // future tools can mutate controls directly.
        if (animated) {
            unmovable = false;
        }
    }

    public boolean unmovable() {
        return unmovable;
    }

    public void setUnmovable(boolean unmovable) {
        this.unmovable = unmovable;
        // Locked controls must remove their animation role immediately, otherwise
        // deformation code would receive contradictory instructions.
        if (unmovable) {
            animated = false;
        }
    }

    public int colorRgb() {
        return colorRgb;
    }

    public void setColorRgb(int colorRgb) {
        this.colorRgb = colorRgb & 0xffffff;
    }

    public boolean hasCustomBreathingStrength() {
        return customBreathingStrength != null;
    }

    public Double customBreathingStrength() {
        return customBreathingStrength;
    }

    public double effectiveBreathingStrength(double globalBreathingStrength) {
        return customBreathingStrength == null ? Math.max(0.0, globalBreathingStrength) : customBreathingStrength;
    }

    public void setCustomBreathingStrength(Double customBreathingStrength) {
        this.customBreathingStrength = sanitizeBreathingStrength(customBreathingStrength);
    }

    public double warpDistance() {
        return Math.hypot(offsetX, offsetY);
    }

    public double warpAngleDegrees() {
        if (warpDistance() <= 0.0001) {
            return ControlPoint.DEFAULT_WARP_ANGLE_DEGREES;
        }
        return Math.toDegrees(Math.atan2(offsetY, offsetX));
    }

    public void setWarpAngleDegrees(double angleDegrees) {
        double distance = warpDistance();
        if (distance <= 0.0001) {
            distance = ControlPoint.DEFAULT_WARP_DISTANCE;
        }
        double radians = Math.toRadians(angleDegrees);
        offsetX = Math.cos(radians) * distance;
        offsetY = Math.sin(radians) * distance;
    }

    public double currentOffsetX(double phase, double breathingStrength) {
        return animated && !unmovable ? offsetX * phase * effectiveBreathingStrength(breathingStrength) : 0.0;
    }

    public double currentOffsetY(double phase, double breathingStrength) {
        return animated && !unmovable ? offsetY * phase * effectiveBreathingStrength(breathingStrength) : 0.0;
    }

    private Double sanitizeBreathingStrength(Double value) {
        return value == null ? null : Math.max(0.0, value);
    }
}
