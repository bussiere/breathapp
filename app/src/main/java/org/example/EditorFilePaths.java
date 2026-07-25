package org.example;

import java.nio.file.Path;
import java.util.Locale;

final class EditorFilePaths {
    private EditorFilePaths() {
    }

    static Path withExtension(Path path, String extension) {
        String name = path.getFileName().toString();
        if (name.toLowerCase(Locale.ROOT).endsWith(extension)) {
            return path;
        }
        return path.resolveSibling(name + extension);
    }
}
