package org.example;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;

final class EditorImageControlBounds {
    private EditorImageControlBounds() {
    }

    static boolean controlsFitImage(List<ControlPoint> points, List<ControlStroke> strokes, BufferedImage image) {
        if (image == null) {
            return true;
        }
        for (ControlPoint point : points) {
            if (!insideImage(point.x(), point.y(), image)) {
                return false;
            }
        }
        for (ControlStroke stroke : strokes) {
            for (Point2D.Double point : stroke.pointsView()) {
                if (!insideImage(point.x, point.y, image)) {
                    return false;
                }
            }
        }
        return true;
    }

    static void clampControlsToImage(List<ControlPoint> points, List<ControlStroke> strokes, BufferedImage image) {
        double maxX = Math.max(0.0, image.getWidth() - 1.0);
        double maxY = Math.max(0.0, image.getHeight() - 1.0);
        for (ControlPoint point : points) {
            point.setX(clamp(point.x(), 0.0, maxX));
            point.setY(clamp(point.y(), 0.0, maxY));
        }
        for (ControlStroke stroke : strokes) {
            // Stroke bounds are cached for hit-testing and deformation ROI, so the stroke owns
            // its own clamp to keep coordinates and cached extents synchronized.
            stroke.clampPointsToImage(image.getWidth(), image.getHeight());
        }
    }

    private static boolean insideImage(double x, double y, BufferedImage image) {
        return x >= 0.0 && y >= 0.0 && x < image.getWidth() && y < image.getHeight();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
