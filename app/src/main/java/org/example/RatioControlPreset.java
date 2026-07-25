package org.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record RatioControlPreset(
        double durationSeconds,
        double breathingStrength,
        List<RatioPoint> points,
        List<RatioStroke> strokes) {
    private static final String FORMAT = "breath-control-ratio-preset";
    private static final int VERSION = 1;
    private static final double DEFAULT_SOURCE_SIZE = 1.0;

    public RatioControlPreset {
        durationSeconds = Math.max(0.25, durationSeconds);
        breathingStrength = Math.max(0.0, breathingStrength);
        points = List.copyOf(points == null ? List.of() : points);
        strokes = List.copyOf(strokes == null ? List.of() : strokes);
    }

    public static RatioControlPreset fromControls(
            BufferedImage image,
            double durationSeconds,
            double breathingStrength,
            List<ControlPoint> points,
            List<ControlStroke> strokes) {
        if (image == null) {
            throw new IllegalArgumentException("Image is required");
        }
        double width = Math.max(1.0, image.getWidth());
        double height = Math.max(1.0, image.getHeight());
        // Radius is normalized against the smaller axis so circular influence zones keep
        // roughly the same visual footprint when the preset is applied to a different size.
        double scale = Math.max(1.0, Math.min(width, height));
        List<RatioPoint> ratioPoints = new ArrayList<>();
        for (ControlPoint point : points == null ? List.<ControlPoint>of() : points) {
            ratioPoints.add(new RatioPoint(
                    point.x() / width,
                    point.y() / height,
                    point.offsetX() / width,
                    point.offsetY() / height,
                    point.radius() / scale,
                    point.animated(),
                    point.unmovable(),
                    point.colorRgb(),
                    point.outlineWidth(),
                    point.customBreathingStrength()));
        }
        List<RatioStroke> ratioStrokes = new ArrayList<>();
        for (ControlStroke stroke : strokes == null ? List.<ControlStroke>of() : strokes) {
            List<RatioCoordinate> coordinates = new ArrayList<>();
            for (Point2D.Double point : stroke.points()) {
                coordinates.add(new RatioCoordinate(point.x / width, point.y / height));
            }
            ratioStrokes.add(new RatioStroke(
                    stroke.offsetX() / width,
                    stroke.offsetY() / height,
                    stroke.radius() / scale,
                    stroke.animated(),
                    stroke.unmovable(),
                    stroke.colorRgb(),
                    stroke.customBreathingStrength(),
                    coordinates));
        }
        return new RatioControlPreset(durationSeconds, breathingStrength, ratioPoints, ratioStrokes);
    }

    public void save(Path path) throws IOException {
        Files.writeString(path, toJson(), StandardCharsets.UTF_8);
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("version", VERSION);
        root.addProperty("duration", durationSeconds);
        root.addProperty("breathingStrength", breathingStrength);

        JsonArray pointArray = new JsonArray();
        for (RatioPoint point : points) {
            JsonObject object = new JsonObject();
            object.addProperty("xRatio", point.xRatio());
            object.addProperty("yRatio", point.yRatio());
            object.addProperty("offsetXRatio", point.offsetXRatio());
            object.addProperty("offsetYRatio", point.offsetYRatio());
            object.addProperty("radiusRatio", point.radiusRatio());
            object.addProperty("animated", point.animated());
            object.addProperty("unmovable", point.unmovable());
            object.addProperty("color", JsonSupport.colorHex(point.colorRgb()));
            object.addProperty("outlineWidth", point.outlineWidth());
            addOptionalNumber(object, "customBreathingStrength", point.customBreathingStrength());
            pointArray.add(object);
        }
        root.add("points", pointArray);

        JsonArray strokeArray = new JsonArray();
        for (RatioStroke stroke : strokes) {
            JsonObject object = new JsonObject();
            object.addProperty("offsetXRatio", stroke.offsetXRatio());
            object.addProperty("offsetYRatio", stroke.offsetYRatio());
            object.addProperty("radiusRatio", stroke.radiusRatio());
            object.addProperty("animated", stroke.animated());
            object.addProperty("unmovable", stroke.unmovable());
            object.addProperty("color", JsonSupport.colorHex(stroke.colorRgb()));
            addOptionalNumber(object, "customBreathingStrength", stroke.customBreathingStrength());
            JsonArray strokePoints = new JsonArray();
            for (RatioCoordinate point : stroke.points()) {
                JsonObject coordinate = new JsonObject();
                coordinate.addProperty("xRatio", point.xRatio());
                coordinate.addProperty("yRatio", point.yRatio());
                strokePoints.add(coordinate);
            }
            object.add("points", strokePoints);
            strokeArray.add(object);
        }
        root.add("strokes", strokeArray);
        return JsonSupport.GSON.toJson(root) + System.lineSeparator();
    }

    public static RatioControlPreset load(Path path) throws IOException {
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    public static RatioControlPreset parse(String json) {
        JsonObject root = JsonSupport.parseObject(json);
        validateHeader(root);
        double duration = JsonSupport.number(root, "duration", 3.5);
        double breathingStrength = JsonSupport.number(root, "breathingStrength", 1.0);
        List<RatioPoint> points = new ArrayList<>();
        for (JsonObject object : objects(root, "points")) {
            if (!has(object, "xRatio") || !has(object, "yRatio")) {
                continue;
            }
            points.add(new RatioPoint(
                    JsonSupport.number(object, "xRatio", 0.0),
                    JsonSupport.number(object, "yRatio", 0.0),
                    JsonSupport.number(object, "offsetXRatio", 0.0),
                    JsonSupport.number(object, "offsetYRatio", 0.0),
                    JsonSupport.number(object, "radiusRatio", 1.0 / DEFAULT_SOURCE_SIZE),
                    JsonSupport.bool(object, "animated", true),
                    JsonSupport.bool(object, "unmovable", false),
                    JsonSupport.color(object, "color", ControlPoint.DEFAULT_COLOR_RGB),
                    JsonSupport.number(object, "outlineWidth", ControlPoint.DEFAULT_OUTLINE_WIDTH),
                    JsonSupport.optionalNumber(object, "customBreathingStrength")));
        }
        List<RatioStroke> strokes = new ArrayList<>();
        for (JsonObject object : objects(root, "strokes")) {
            List<RatioCoordinate> coordinates = new ArrayList<>();
            for (JsonObject pointObject : objects(object, "points")) {
                coordinates.add(new RatioCoordinate(
                        JsonSupport.number(pointObject, "xRatio", 0.0),
                        JsonSupport.number(pointObject, "yRatio", 0.0)));
            }
            if (!coordinates.isEmpty()) {
                strokes.add(new RatioStroke(
                        JsonSupport.number(object, "offsetXRatio", 0.0),
                        JsonSupport.number(object, "offsetYRatio", 0.0),
                        JsonSupport.number(object, "radiusRatio", 1.0 / DEFAULT_SOURCE_SIZE),
                        JsonSupport.bool(object, "animated", true),
                        JsonSupport.bool(object, "unmovable", false),
                        JsonSupport.color(object, "color", ControlStroke.DEFAULT_COLOR_RGB),
                        JsonSupport.optionalNumber(object, "customBreathingStrength"),
                        coordinates));
            }
        }
        return new RatioControlPreset(duration, breathingStrength, points, strokes);
    }

    private static void validateHeader(JsonObject root) {
        // Batch apply should fail fast on project JSON or arbitrary files; silently falling
        // back to empty controls would make users export many unchanged images by mistake.
        String format = JsonSupport.string(root, "format", "");
        if (!FORMAT.equals(format)) {
            throw new IllegalArgumentException("Ratio preset format must be " + FORMAT);
        }
        int version = (int) JsonSupport.number(root, "version", -1.0);
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported ratio preset version: " + version);
        }
    }

    public AppliedControls applyTo(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("Image is required");
        }
        double width = Math.max(1.0, image.getWidth());
        double height = Math.max(1.0, image.getHeight());
        // Apply the same scale rule used at export time, otherwise batch output would
        // silently change the apparent reach of locks and animated controls.
        double scale = Math.max(1.0, Math.min(width, height));
        List<ControlPoint> appliedPoints = new ArrayList<>();
        for (RatioPoint point : points) {
            appliedPoints.add(new ControlPoint(
                    point.xRatio() * width,
                    point.yRatio() * height,
                    point.offsetXRatio() * width,
                    point.offsetYRatio() * height,
                    point.radiusRatio() * scale,
                    point.animated(),
                    point.unmovable(),
                    point.colorRgb(),
                    point.outlineWidth(),
                    point.customBreathingStrength()));
        }
        List<ControlStroke> appliedStrokes = new ArrayList<>();
        for (RatioStroke stroke : strokes) {
            List<Point2D.Double> strokePoints = new ArrayList<>();
            for (RatioCoordinate point : stroke.points()) {
                strokePoints.add(new Point2D.Double(point.xRatio() * width, point.yRatio() * height));
            }
            appliedStrokes.add(new ControlStroke(
                    strokePoints,
                    stroke.offsetXRatio() * width,
                    stroke.offsetYRatio() * height,
                    stroke.radiusRatio() * scale,
                    stroke.animated(),
                    stroke.unmovable(),
                    stroke.colorRgb(),
                    stroke.customBreathingStrength()));
        }
        return new AppliedControls(appliedPoints, appliedStrokes);
    }

    private static List<JsonObject> objects(JsonObject root, String key) {
        JsonElement element = root == null ? null : root.get(key);
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        List<JsonObject> objects = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (item != null && item.isJsonObject()) {
                objects.add(item.getAsJsonObject());
            }
        }
        return objects;
    }

    private static boolean has(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull();
    }

    private static void addOptionalNumber(JsonObject object, String key, Double value) {
        if (value == null) {
            object.add(key, JsonNull.INSTANCE);
        } else {
            object.addProperty(key, value);
        }
    }

    public record RatioPoint(
            double xRatio,
            double yRatio,
            double offsetXRatio,
            double offsetYRatio,
            double radiusRatio,
            boolean animated,
            boolean unmovable,
            int colorRgb,
            double outlineWidth,
            Double customBreathingStrength) {
    }

    public record RatioStroke(
            double offsetXRatio,
            double offsetYRatio,
            double radiusRatio,
            boolean animated,
            boolean unmovable,
            int colorRgb,
            Double customBreathingStrength,
            List<RatioCoordinate> points) {
        public RatioStroke {
            points = List.copyOf(points == null ? List.of() : points);
        }
    }

    public record RatioCoordinate(double xRatio, double yRatio) {
    }

    public record AppliedControls(List<ControlPoint> points, List<ControlStroke> strokes) {
        public AppliedControls {
            points = List.copyOf(points == null ? List.of() : points);
            strokes = List.copyOf(strokes == null ? List.of() : strokes);
        }
    }
}
