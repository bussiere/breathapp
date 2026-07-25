package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class JsonSupport {
    // One shared Gson instance keeps project, preset, help, and atlas JSON behavior aligned.
    // That matters because these files are exchanged between UI flows and batch exports.
    static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    private JsonSupport() {
    }

    static JsonObject parseObject(String json) {
        JsonElement element = JsonParser.parseString(json == null ? "{}" : json);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    static String string(JsonObject object, String key, String fallback) {
        JsonElement element = object == null ? null : object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsString();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    static double number(JsonObject object, String key, double fallback) {
        JsonElement element = object == null ? null : object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsDouble();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    static Double optionalNumber(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsDouble();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement element = object == null ? null : object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    static int color(JsonObject object, String key, int fallback) {
        String value = string(object, key, "").strip();
        if (value.isEmpty()) {
            return fallback;
        }
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() != 6) {
            return fallback;
        }
        try {
            return Integer.parseInt(value, 16) & 0xffffff;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static String colorHex(int rgb) {
        return String.format(java.util.Locale.ROOT, "#%06X", rgb & 0xffffff);
    }
}
