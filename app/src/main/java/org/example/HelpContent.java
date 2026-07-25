package org.example;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public record HelpContent(String title, URL baseUrl, List<Page> pages) {
    public HelpContent {
        title = title == null || title.isBlank() ? "Help" : title;
        pages = List.copyOf(pages == null || pages.isEmpty() ? List.of(new Page(title, "")) : pages);
    }

    public static HelpContent load(String resourcePath) throws IOException {
        URL resourceUrl = HelpContent.class.getResource(resourcePath);
        if (resourceUrl == null) {
            throw new IOException("Help resource not found: " + resourcePath);
        }
        String json;
        try (InputStream input = resourceUrl.openStream()) {
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        JsonObject root = JsonSupport.parseObject(json);
        String title = JsonSupport.string(root, "title", "Help");
        // Store the directory URL, not the JSON file URL, because Swing resolves <img src>
        // relative to HTMLDocument.getBase(). This keeps image paths stable in both Gradle
        // resources and packaged JAR URLs.
        URL baseUrl = resourceDirectory(resourceUrl);
        List<Page> pages = new ArrayList<>();
        JsonElement pageElement = root.get("pages");
        if (pageElement != null && pageElement.isJsonArray()) {
            for (JsonElement item : pageElement.getAsJsonArray()) {
                if (item != null && item.isJsonObject()) {
                    JsonObject object = item.getAsJsonObject();
                    pages.add(new Page(
                            JsonSupport.string(object, "title", title),
                            JsonSupport.string(object, "html", "")));
                }
            }
        }
        if (pages.isEmpty()) {
            pages.add(new Page(title, JsonSupport.string(root, "html", "")));
        }
        return new HelpContent(title, baseUrl, pages);
    }

    private static URL resourceDirectory(URL resourceUrl) throws IOException {
        String external = resourceUrl.toExternalForm();
        int slash = external.lastIndexOf('/');
        if (slash < 0) {
            throw new IOException("Unable to resolve help resource directory: " + resourceUrl);
        }
        return java.net.URI.create(external.substring(0, slash + 1)).toURL();
    }

    public record Page(String title, String html) {
        public Page {
            title = title == null || title.isBlank() ? "Help" : title;
            html = html == null ? "" : html;
        }
    }
}
