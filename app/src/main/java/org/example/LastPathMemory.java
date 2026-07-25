package org.example;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;
import javax.swing.JFileChooser;

public final class LastPathMemory {
    private static final String LAST_DIRECTORY_KEY = "lastDirectory";
    private final Preferences preferences;
    private final Path fallbackDirectory;

    public LastPathMemory() {
        this(Preferences.userNodeForPackage(LastPathMemory.class), Path.of("export"));
    }

    LastPathMemory(Preferences preferences, Path fallbackDirectory) {
        this.preferences = preferences;
        this.fallbackDirectory = fallbackDirectory;
    }

    public void configure(JFileChooser chooser) {
        Path directory = lastDirectory();
        if (directory != null) {
            chooser.setCurrentDirectory(directory.toFile());
        }
    }

    public void rememberSelection(File selectedFile) {
        if (selectedFile == null) {
            return;
        }
        File directory = selectedFile.isDirectory() ? selectedFile : selectedFile.getParentFile();
        if (directory != null) {
            rememberDirectory(directory.toPath());
        }
    }

    public void rememberDirectory(Path directory) {
        if (directory != null) {
            preferences.put(LAST_DIRECTORY_KEY, directory.toAbsolutePath().normalize().toString());
        }
    }

    public Path lastDirectory() {
        String stored = preferences.get(LAST_DIRECTORY_KEY, "");
        if (!stored.isBlank()) {
            Path path = Path.of(stored);
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        if (Files.isDirectory(fallbackDirectory)) {
            return fallbackDirectory.toAbsolutePath().normalize();
        }
        return null;
    }
}
