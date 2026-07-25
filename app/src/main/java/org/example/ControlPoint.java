package org.example;

public final class ControlPoint {
    public static final double DEFAULT_WARP_ANGLE_DEGREES = -90.0;
    public static final double DEFAULT_WARP_DISTANCE = 4.0;
    public static final int DEFAULT_COLOR_RGB = 0x53beff;
    public static final double DEFAULT_OUTLINE_WIDTH = 1.0;

    private double x;
    private double y;
    private double offsetX;
    private double offsetY;
    private double radius;
    private boolean animated;
    private boolean unmovable;
    private int colorRgb;
    private double outlineWidth;
    private Double customBreathingStrength;

    public ControlPoint(double x, double y, double offsetX, double offsetY, double radius, boolean animated) {
        this(x, y, offsetX, offsetY, radius, animated, false, DEFAULT_COLOR_RGB);
    }

    public ControlPoint(double x, double y, double offsetX, double offsetY, double radius, boolean animated, boolean unmovable) {
        this(x, y, offsetX, offsetY, radius, animated, unmovable, DEFAULT_COLOR_RGB);
    }

    public ControlPoint(double x, double y, double offsetX, double offsetY, double radius, boolean animated, boolean unmovable, int colorRgb) {
        this(x, y, offsetX, offsetY, radius, animated, unmovable, colorRgb, DEFAULT_OUTLINE_WIDTH);
    }

    public ControlPoint(double x, double y, double offsetX, double offsetY, double radius, boolean animated, boolean unmovable, int colorRgb, double outlineWidth) {
        this(x, y, offsetX, offsetY, radius, animated, unmovable, colorRgb, outlineWidth, null);
    }

    public ControlPoint(
            double x,
            double y,
            double offsetX,
            double offsetY,
            double radius,
            boolean animated,
            boolean unmovable,
            int colorRgb,
            double outlineWidth,
            Double customBreathingStrength) {
        this.x = x;
        this.y = y;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.radius = Math.max(1.0, radius);
        // A control cannot both deform pixels and lock them in place. Legacy or
        // hand-edited project files may contain both flags, so preserve pixels by
        // letting the lock win during normalization.
        this.animated = animated && !unmovable;
        this.unmovable = unmovable;
        this.colorRgb = colorRgb & 0xffffff;
        this.outlineWidth = clampOutlineWidth(outlineWidth);
        this.customBreathingStrength = sanitizeBreathingStrength(customBreathingStrength);
    }

    public ControlPoint copy() {
        return new ControlPoint(x, y, offsetX, offsetY, radius, animated, unmovable, colorRgb, outlineWidth, customBreathingStrength);
    }

    public double x() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double y() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
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

    public double outlineWidth() {
        return outlineWidth;
    }

    public void setOutlineWidth(double outlineWidth) {
        this.outlineWidth = clampOutlineWidth(outlineWidth);
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
            return DEFAULT_WARP_ANGLE_DEGREES;
        }
        return Math.toDegrees(Math.atan2(offsetY, offsetX));
    }

    public void setWarpAngleDegrees(double angleDegrees) {
        double distance = warpDistance();
        if (distance <= 0.0001) {
            distance = DEFAULT_WARP_DISTANCE;
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

    private double clampOutlineWidth(double value) {
        return Math.max(0.5, Math.min(16.0, value));
    }

    private Double sanitizeBreathingStrength(Double value) {
        return value == null ? null : Math.max(0.0, value);
    }
}
