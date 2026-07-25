package org.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;

public record BreathingProject(
        Path imagePath,
        String imageName,
        String imageBase64,
        double durationSeconds,
        double breathingStrength,
        List<ControlPoint> points,
        List<ControlStroke> strokes) {
    public BreathingProject(Path imagePath, double durationSeconds, List<ControlPoint> points) {
        this(imagePath, imagePath == null ? "" : imagePath.getFileName().toString(), readImageBase64(imagePath), durationSeconds, 1.0, points, List.of());
    }

    public BreathingProject(
            Path imagePath,
            String imageName,
            String imageBase64,
            double durationSeconds,
            double breathingStrength,
            List<ControlPoint> points) {
        this(imagePath, imageName, imageBase64, durationSeconds, breathingStrength, points, List.of());
    }

    public BreathingProject {
        imageName = imageName == null ? "" : imageName;
        imageBase64 = imageBase64 == null ? "" : imageBase64;
        breathingStrength = Math.max(0.0, breathingStrength);
        points = List.copyOf(points == null ? List.of() : points);
        strokes = List.copyOf(strokes == null ? List.of() : strokes);
    }

    public List<ControlPoint> copiedPoints() {
        return points.stream().map(ControlPoint::copy).toList();
    }

    public List<ControlStroke> copiedStrokes() {
        return strokes.stream().map(ControlStroke::copy).toList();
    }

    public static BreathingProject fromEditorState(
            Path imagePath,
            BufferedImage image,
            double durationSeconds,
            double breathingStrength,
            List<ControlPoint> points,
            List<ControlStroke> strokes) {
        String imageName = imagePath == null ? "" : imagePath.getFileName().toString();
        return new BreathingProject(
                imagePath,
                imageName,
                imageBase64ForSave(image, imagePath),
                durationSeconds,
                breathingStrength,
                points,
                strokes);
    }

    public boolean hasEmbeddedImage() {
        return !imageBase64.isBlank();
    }

    public byte[] embeddedImageBytes() {
        return Base64.getDecoder().decode(imageBase64);
    }

    public void save(Path projectFile) throws IOException {
        Files.writeString(projectFile, toJson(projectFile.getParent()), StandardCharsets.UTF_8);
    }

    public String toJson() {
        return toJson(null);
    }

    private String toJson(Path projectDirectory) {
        Path storedImage = imagePath;
        if (projectDirectory != null && imagePath != null) {
            try {
                storedImage = projectDirectory.toAbsolutePath().normalize().relativize(imagePath.toAbsolutePath().normalize());
            } catch (IllegalArgumentException ignored) {
                storedImage = imagePath;
            }
        }

        JsonObject root = new JsonObject();
        root.addProperty("image", storedImage == null ? "" : storedImage.toString());
        root.addProperty("imageName", imageName);
        root.addProperty("imageBase64", imageBase64);
        root.addProperty("duration", durationSeconds);
        root.addProperty("breathingStrength", breathingStrength);

        JsonArray pointArray = new JsonArray();
        for (ControlPoint point : points) {
            JsonObject object = new JsonObject();
            object.addProperty("x", point.x());
            object.addProperty("y", point.y());
            object.addProperty("offsetX", point.offsetX());
            object.addProperty("offsetY", point.offsetY());
            object.addProperty("radius", point.radius());
            object.addProperty("animated", point.animated());
            object.addProperty("unmovable", point.unmovable());
            object.addProperty("color", JsonSupport.colorHex(point.colorRgb()));
            object.addProperty("outlineWidth", point.outlineWidth());
            addOptionalNumber(object, "customBreathingStrength", point.customBreathingStrength());
            pointArray.add(object);
        }
        root.add("points", pointArray);

        JsonArray strokeArray = new JsonArray();
        for (ControlStroke stroke : strokes) {
            JsonObject object = new JsonObject();
            object.addProperty("offsetX", stroke.offsetX());
            object.addProperty("offsetY", stroke.offsetY());
            object.addProperty("radius", stroke.radius());
            object.addProperty("animated", stroke.animated());
            object.addProperty("unmovable", stroke.unmovable());
            object.addProperty("color", JsonSupport.colorHex(stroke.colorRgb()));
            addOptionalNumber(object, "customBreathingStrength", stroke.customBreathingStrength());
            JsonArray strokePoints = new JsonArray();
            for (Point2D.Double point : stroke.points()) {
                JsonObject coordinate = new JsonObject();
                coordinate.addProperty("x", point.x);
                coordinate.addProperty("y", point.y);
                strokePoints.add(coordinate);
            }
            object.add("points", strokePoints);
            strokeArray.add(object);
        }
        root.add("strokes", strokeArray);
        return JsonSupport.GSON.toJson(root) + System.lineSeparator();
    }

    public static BreathingProject load(Path projectFile) throws IOException {
        return parse(Files.readString(projectFile, StandardCharsets.UTF_8), projectFile.getParent());
    }

    public static BreathingProject parse(String json, Path baseDirectory) {
        JsonObject root = JsonSupport.parseObject(json);
        String image = JsonSupport.string(root, "image", "");
        String imageName = JsonSupport.string(root, "imageName", "");
        String imageBase64 = JsonSupport.string(root, "imageBase64", "");
        double duration = JsonSupport.number(root, "duration", 3.5);
        double breathingStrength = JsonSupport.number(root, "breathingStrength", 1.0);

        Path loadedImagePath = image.isBlank() ? null : Path.of(image);
        if (loadedImagePath != null && !loadedImagePath.isAbsolute() && baseDirectory != null) {
            loadedImagePath = baseDirectory.resolve(loadedImagePath).normalize();
        }

        List<ControlPoint> loadedPoints = new ArrayList<>();
        for (JsonObject object : objects(root, "points")) {
            if (!has(object, "x") || !has(object, "y")) {
                continue;
            }
            loadedPoints.add(new ControlPoint(
                    JsonSupport.number(object, "x", 0.0),
                    JsonSupport.number(object, "y", 0.0),
                    JsonSupport.number(object, "offsetX", 0.0),
                    JsonSupport.number(object, "offsetY", 0.0),
                    JsonSupport.number(object, "radius", 1.0),
                    JsonSupport.bool(object, "animated", true),
                    JsonSupport.bool(object, "unmovable", JsonSupport.bool(object, "shoulder", false)),
                    JsonSupport.color(object, "color", ControlPoint.DEFAULT_COLOR_RGB),
                    JsonSupport.number(object, "outlineWidth", ControlPoint.DEFAULT_OUTLINE_WIDTH),
                    JsonSupport.optionalNumber(object, "customBreathingStrength")));
        }

        List<ControlStroke> loadedStrokes = new ArrayList<>();
        for (JsonObject object : objects(root, "strokes")) {
            List<Point2D.Double> strokePoints = new ArrayList<>();
            for (JsonObject pointObject : objects(object, "points")) {
                strokePoints.add(new Point2D.Double(
                        JsonSupport.number(pointObject, "x", 0.0),
                        JsonSupport.number(pointObject, "y", 0.0)));
            }
            if (!strokePoints.isEmpty()) {
                loadedStrokes.add(new ControlStroke(
                        strokePoints,
                        JsonSupport.number(object, "offsetX", 0.0),
                        JsonSupport.number(object, "offsetY", 0.0),
                        JsonSupport.number(object, "radius", 1.0),
                        JsonSupport.bool(object, "animated", true),
                        JsonSupport.bool(object, "unmovable", false),
                        JsonSupport.color(object, "color", ControlStroke.DEFAULT_COLOR_RGB),
                        JsonSupport.optionalNumber(object, "customBreathingStrength")));
            }
        }
        return new BreathingProject(loadedImagePath, imageName, imageBase64, duration, breathingStrength, loadedPoints, loadedStrokes);
    }

    private static List<JsonObject> objects(JsonObject root, String key) {
        JsonElement element = root.get(key);
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

    private static String readImageBase64(Path imagePath) {
        return imageBase64ForSave(null, imagePath);
    }

    private static String imageBase64ForSave(BufferedImage image, Path imagePath) {
        if (image != null) {
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(image, "png", output);
                return Base64.getEncoder().encodeToString(output.toByteArray());
            } catch (IOException ignored) {
                // A project should still be savable if ImageIO refuses the in-memory image; the
                // original file is the next best source for preserving a portable project JSON.
            }
        }
        if (imagePath == null) {
            return "";
        }
        try {
            return Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
        } catch (IOException ignored) {
            return "";
        }
    }
}
