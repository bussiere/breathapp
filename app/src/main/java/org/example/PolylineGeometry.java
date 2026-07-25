package org.example;

import java.awt.geom.Point2D;
import java.util.List;

final class PolylineGeometry {
    private static final double DEGENERATE_SEGMENT_EPSILON = 0.0001;

    private PolylineGeometry() {
    }

    static double distanceToPolyline(Point2D point, List<Point2D.Double> points, double offsetX, double offsetY) {
        return Math.sqrt(distanceSquaredToPolyline(point.getX(), point.getY(), points, offsetX, offsetY));
    }

    static double distanceToPolyline(double pixelX, double pixelY, List<Point2D.Double> points) {
        return Math.sqrt(distanceSquaredToPolyline(pixelX, pixelY, points, 0.0, 0.0));
    }

    static double distanceSquaredToPolyline(double pixelX, double pixelY, List<Point2D.Double> points, double offsetX, double offsetY) {
        if (points == null || points.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        if (points.size() == 1) {
            Point2D.Double point = points.get(0);
            double dx = pixelX - (point.x + offsetX);
            double dy = pixelY - (point.y + offsetY);
            return dx * dx + dy * dy;
        }
        double best = Double.POSITIVE_INFINITY;
        for (int i = 1; i < points.size(); i++) {
            Point2D.Double a = points.get(i - 1);
            Point2D.Double b = points.get(i);
            best = Math.min(best, distanceSquaredToSegment(
                    pixelX,
                    pixelY,
                    a.x + offsetX,
                    a.y + offsetY,
                    b.x + offsetX,
                    b.y + offsetY));
        }
        return best;
    }

    static double distanceSquaredToPolyline(double pixelX, double pixelY, double[] pointX, double[] pointY, int pointCount) {
        if (pointCount <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        if (pointCount == 1) {
            double dx = pixelX - pointX[0];
            double dy = pixelY - pointY[0];
            return dx * dx + dy * dy;
        }
        double best = Double.POSITIVE_INFINITY;
        for (int i = 1; i < pointCount; i++) {
            best = Math.min(best, distanceSquaredToSegment(
                    pixelX,
                    pixelY,
                    pointX[i - 1],
                    pointY[i - 1],
                    pointX[i],
                    pointY[i]));
        }
        return best;
    }

    static double distanceSquaredToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double vx = bx - ax;
        double vy = by - ay;
        double lengthSquared = vx * vx + vy * vy;
        if (lengthSquared <= DEGENERATE_SEGMENT_EPSILON) {
            double dx = px - ax;
            double dy = py - ay;
            return dx * dx + dy * dy;
        }
        double t = ((px - ax) * vx + (py - ay) * vy) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double closestX = ax + t * vx;
        double closestY = ay + t * vy;
        double dx = px - closestX;
        double dy = py - closestY;
        return dx * dx + dy * dy;
    }
}
