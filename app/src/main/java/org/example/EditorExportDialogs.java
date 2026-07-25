package org.example;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

final class EditorExportDialogs {
    private EditorExportDialogs() {
    }

    static Path chooseRatioPresetTarget(Component parent, LastPathMemory lastPathMemory) {
        JFileChooser chooser = new JFileChooser();
        lastPathMemory.configure(chooser);
        chooser.setFileFilter(new FileNameExtensionFilter("Breath ratio preset JSON", "json"));
        chooser.setSelectedFile(new File("breath_ratio_preset.json"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        Path target = EditorFilePaths.withExtension(chooser.getSelectedFile().toPath(), ".json");
        if (!confirmOverwrite(parent, target)) {
            return null;
        }
        lastPathMemory.rememberSelection(target.toFile());
        return target;
    }

    static BatchSelection chooseBatchSelection(Component parent, LastPathMemory lastPathMemory) throws IOException {
        JFileChooser presetChooser = new JFileChooser();
        lastPathMemory.configure(presetChooser);
        presetChooser.setFileFilter(new FileNameExtensionFilter("Breath ratio preset JSON", "json"));
        if (presetChooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        RatioControlPreset preset = RatioControlPreset.load(presetChooser.getSelectedFile().toPath());
        lastPathMemory.rememberSelection(presetChooser.getSelectedFile());

        JFileChooser imageChooser = new JFileChooser();
        lastPathMemory.configure(imageChooser);
        imageChooser.setFileFilter(new FileNameExtensionFilter("PNG images", "png"));
        imageChooser.setMultiSelectionEnabled(true);
        if (imageChooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File[] selectedFiles = imageChooser.getSelectedFiles();
        if (selectedFiles.length == 0) {
            return null;
        }
        lastPathMemory.rememberSelection(selectedFiles[0]);

        EditorExportService.BatchExportFormat format = chooseBatchFormat(parent);
        if (format == null) {
            return null;
        }

        JFileChooser outputChooser = new JFileChooser();
        lastPathMemory.configure(outputChooser);
        outputChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (outputChooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        Path outputDirectory = outputChooser.getSelectedFile().toPath();
        if (!confirmBatchOverwrite(parent, List.of(selectedFiles), outputDirectory, format)) {
            return null;
        }
        lastPathMemory.rememberSelection(outputChooser.getSelectedFile());
        return new BatchSelection(preset, List.of(selectedFiles), outputDirectory, format);
    }

    static Path choosePngSequenceDirectory(Component parent, LastPathMemory lastPathMemory) {
        JFileChooser chooser = new JFileChooser();
        lastPathMemory.configure(chooser);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        lastPathMemory.rememberSelection(chooser.getSelectedFile());
        return chooser.getSelectedFile().toPath();
    }

    static Path chooseSpriteSheetTarget(Component parent, LastPathMemory lastPathMemory) {
        return chooseImageTarget(parent, lastPathMemory, "PNG sprite sheet", "breathing_spritesheet.png", ".png");
    }

    static Path chooseAnimatedPngTarget(Component parent, LastPathMemory lastPathMemory) {
        return chooseImageTarget(parent, lastPathMemory, "Animated PNG APNG", "breathing_apng.png", ".png");
    }

    static Path chooseGifTarget(Component parent, LastPathMemory lastPathMemory) {
        return chooseImageTarget(parent, lastPathMemory, "Animated GIF", "breathing.gif", ".gif");
    }

    private static Path chooseImageTarget(
            Component parent,
            LastPathMemory lastPathMemory,
            String filterLabel,
            String defaultName,
            String extension) {
        JFileChooser chooser = new JFileChooser();
        lastPathMemory.configure(chooser);
        chooser.setFileFilter(new FileNameExtensionFilter(filterLabel, extension.substring(1)));
        chooser.setSelectedFile(new File(defaultName));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        Path target = EditorFilePaths.withExtension(chooser.getSelectedFile().toPath(), extension);
        if (!confirmOverwrite(parent, target)) {
            return null;
        }
        lastPathMemory.rememberSelection(target.toFile());
        return target;
    }

    static boolean confirmOverwrite(Component parent, Path target) {
        if (target == null || !Files.exists(target)) {
            return true;
        }
        int choice = JOptionPane.showConfirmDialog(
                parent,
                "Replace existing file?\n" + target.getFileName(),
                "Overwrite file",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    private static boolean confirmBatchOverwrite(
            Component parent,
            List<File> imageFiles,
            Path outputDirectory,
            EditorExportService.BatchExportFormat format) {
        List<Path> existingTargets = imageFiles.stream()
                .map(file -> outputDirectory.resolve(format.fileNameFor(file.toPath())))
                .filter(Files::exists)
                .toList();
        if (existingTargets.isEmpty()) {
            return true;
        }
        StringBuilder message = new StringBuilder("Batch export will replace existing files:");
        int limit = Math.min(5, existingTargets.size());
        for (int i = 0; i < limit; i++) {
            message.append(System.lineSeparator()).append("- ").append(existingTargets.get(i).getFileName());
        }
        if (existingTargets.size() > limit) {
            message.append(System.lineSeparator()).append("...");
        }
        int choice = JOptionPane.showConfirmDialog(
                parent,
                message.toString(),
                "Overwrite batch files",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    private static EditorExportService.BatchExportFormat chooseBatchFormat(Component parent) {
        EditorExportService.BatchExportFormat[] formats = EditorExportService.BatchExportFormat.values();
        String[] labels = new String[formats.length];
        for (int i = 0; i < formats.length; i++) {
            labels[i] = formats[i].label();
        }
        String selected = (String) JOptionPane.showInputDialog(
                parent,
                "Choose batch export format",
                "Batch export",
                JOptionPane.PLAIN_MESSAGE,
                null,
                labels,
                labels[0]);
        if (selected == null) {
            return null;
        }
        return EditorExportService.BatchExportFormat.fromLabel(selected);
    }

    record BatchSelection(
            RatioControlPreset preset,
            List<File> imageFiles,
            Path outputDirectory,
            EditorExportService.BatchExportFormat format) {
        BatchSelection {
            imageFiles = List.copyOf(imageFiles == null ? List.of() : imageFiles);
        }
    }
}
