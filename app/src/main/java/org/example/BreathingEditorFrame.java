package org.example;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SwingWorker;
import javax.swing.JToolBar;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

public final class BreathingEditorFrame extends JFrame {
    private static final int TIMER_DELAY_MS = 50;
    private static final int EXPORT_FRAMES = AnimationExporter.DEFAULT_FRAME_COUNT;

    private final List<ControlPoint> points = new ArrayList<>();
    private final List<ControlStroke> strokes = new ArrayList<>();
    private final BreathingAnimator animator = new BreathingAnimator();
    private final SpriteEditorPanel spritePanel = new SpriteEditorPanel(points, strokes);
    private final AnimationPreviewPanel animationPreviewPanel = new AnimationPreviewPanel();
    private final LastPathMemory lastPathMemory = new LastPathMemory();
    private final EditorTutorialSession tutorialSession = new EditorTutorialSession();
    private final EditorLivePreview livePreview = new EditorLivePreview();
    private final EditorAnimationPreviewRenderer animationPreviewRenderer = new EditorAnimationPreviewRenderer();
    private final CardLayout centerLayout = new CardLayout();
    private final JPanel centerPanel = new JPanel(centerLayout);
    private final JPanel sidePanel;
    private final JScrollPane sideScrollPane;
    private final Timer timer;
    private final EditorSelectionController selectionController;

    private final JSpinner durationSpinner = spinner(3.5, 0.25, 30.0, 0.1);
    private final JSpinner breathingStrengthSpinner = spinner(1.0, 0.0, 4.0, 0.1);
    private final JSpinner pointXSpinner = spinner(0.0, 0.0, 100000.0, 0.5);
    private final JSpinner pointYSpinner = spinner(0.0, 0.0, 100000.0, 0.5);
    private final JSpinner offsetXSpinner = spinner(0.0, -80.0, 80.0, 0.5);
    private final JSpinner offsetYSpinner = spinner(-4.0, -80.0, 80.0, 0.5);
    private final JSpinner warpAngleSpinner = spinner(ControlPoint.DEFAULT_WARP_ANGLE_DEGREES, -180.0, 180.0, 1.0);
    private final JSpinner radiusSpinner = spinner(80.0, 1.0, 800.0, 1.0);
    private final JSpinner outlineWidthSpinner = spinner(ControlPoint.DEFAULT_OUTLINE_WIDTH, 0.5, 16.0, 0.5);
    private final JSpinner controlBreathingStrengthSpinner = spinner(1.0, 0.0, 8.0, 0.1);
    private final JCheckBox customBreathingStrengthBox = new JCheckBox("Custom breath");
    private final JCheckBox animatedBox = new JCheckBox("Animated");
    private final JCheckBox unmovableBox = new JCheckBox("Unmovable");
    private final JLabel statusLabel = new JLabel("Load a PNG, then click the sprite to add control points.");
    private final JButton playButton = new JButton("Play");
    private final JButton previewButton = new JButton("Preview");
    private final JButton editPointsButton = new JButton("Edit points");
    private final JButton deletePointButton = new JButton("Delete");
    private final JButton nextPointButton = new JButton("Next");
    private final JButton colorButton = new JButton("Color");
    private final JButton closeTutorialButton = new JButton("Close tutorial");
    private final List<JButton> exportActionButtons = new ArrayList<>();
    private final JToggleButton pointToolButton = new JToggleButton("Point", true);
    private final JToggleButton strokeToolButton = new JToggleButton("Trait");

    private BufferedImage originalImage;
    private BufferedImage deformedImage;
    private Path imagePath;
    private boolean exportInProgress;
    private boolean resumeTimerAfterExport;
    private boolean projectDirty;

    public BreathingEditorFrame() {
        super("2D Breathing Sprite");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (confirmDiscardUnsavedChanges()) {
                    shutdownApplication();
                }
            }
        });
        setMinimumSize(new Dimension(960, 680));
        setLayout(new BorderLayout());

        timer = new Timer(TIMER_DELAY_MS, event -> refreshPreview());
        timer.start();

        EditorSidePanelFactory.Components sideComponents = new EditorSidePanelFactory.Components(
                durationSpinner,
                breathingStrengthSpinner,
                pointXSpinner,
                pointYSpinner,
                offsetXSpinner,
                offsetYSpinner,
                warpAngleSpinner,
                radiusSpinner,
                outlineWidthSpinner,
                controlBreathingStrengthSpinner,
                customBreathingStrengthBox,
                animatedBox,
                unmovableBox,
                colorButton,
                nextPointButton,
                deletePointButton,
                pointToolButton,
                strokeToolButton);
        selectionController = new EditorSelectionController(
                this,
                spritePanel,
                sideComponents,
                () -> originalImage,
                () -> number(breathingStrengthSpinner),
                () -> {
                    markProjectDirty();
                    markPreviewDirty();
                    refreshPreview();
                },
                statusLabel::setText);
        spritePanel.setSelectionListener(selectionController::showSelectedControl);
        spritePanel.setControlsChangedListener(() -> {
            markProjectDirty();
            markPreviewDirty();
            refreshPreview();
        });
        sidePanel = EditorSidePanelFactory.create(
                sideComponents,
                new EditorSidePanelFactory.Actions(
                        () -> setToolMode(SpriteEditorPanel.ToolMode.POINT),
                        () -> setToolMode(SpriteEditorPanel.ToolMode.STROKE),
                        selectionController::chooseSelectedColor,
                        spritePanel::selectNextControl,
                        spritePanel::deleteSelectedControl));
        // The control list grows as editing features are added, so scrolling prevents small windows
        // from forcing Swing to squeeze rows until labels and spinners overlap.
        sideScrollPane = new JScrollPane(sidePanel);
        sideScrollPane.setBorder(null);
        sideScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sideScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        sideScrollPane.setPreferredSize(new Dimension(360, 640));
        centerPanel.add(spritePanel, "points");
        centerPanel.add(animationPreviewPanel, "preview");
        add(createToolbar(), BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(sideScrollPane, BorderLayout.EAST);
        add(statusLabel, BorderLayout.SOUTH);

        wireSettings();
        showPointEditor();
        selectionController.setControlsEnabled(false);
        pack();
        setLocationRelativeTo(null);
    }

    private JToolBar createToolbar() {
        return EditorToolbarFactory.create(
                new EditorToolbarFactory.Actions(
                        this::loadPng,
                        this::openProject,
                        this::saveProject,
                        this::exportRatioPreset,
                        this::batchApplyRatioPreset,
                        this::togglePlayback,
                        this::stopPlayback,
                        this::showAnimationPreview,
                        this::showPointEditor,
                        this::addDefaultTorsoPoints,
                        this::loadChipsTutorial,
                        this::restoreBeforeTutorial,
                        this::exportPngSequence,
                        this::exportSpriteSheet,
                        this::exportAnimatedPng,
                        this::exportGif,
                        () -> showHelpResource("/help/tutorial.json"),
                        () -> showHelpResource("/help/about.json")),
                playButton,
                previewButton,
                editPointsButton,
                closeTutorialButton,
                exportActionButtons);
    }

    private void showHelpResource(String resourcePath) {
        try {
            new HtmlHelpDialog(this, HelpContent.load(resourcePath)).setVisible(true);
        } catch (IOException | RuntimeException ex) {
            showError("Could not open help", ex instanceof Exception exception ? exception : new RuntimeException(ex));
        }
    }

    private void wireSettings() {
        durationSpinner.addChangeListener(event -> {
            animator.setDurationSeconds(number(durationSpinner));
            markProjectDirty();
            markPreviewDirty();
        });
        breathingStrengthSpinner.addChangeListener(event -> {
            markProjectDirty();
            markPreviewDirty();
            selectionController.syncGlobalBreathingStrengthChanged();
        });
        selectionController.wireSelectionSettings();
    }

    private void setToolMode(SpriteEditorPanel.ToolMode toolMode) {
        spritePanel.setToolMode(toolMode);
        statusLabel.setText(toolMode == SpriteEditorPanel.ToolMode.STROKE
                ? "Trait tool: drag on the sprite to draw a warp line."
                : "Point tool: click on the sprite to add or select a control point.");
    }

    private void loadPng() {
        JFileChooser chooser = new JFileChooser();
        lastPathMemory.configure(chooser);
        chooser.setFileFilter(new FileNameExtensionFilter("PNG images", "png"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        if (!confirmDiscardUnsavedChanges()) {
            return;
        }
        try {
            EditorImageLoader.LoadedImage loaded = EditorImageLoader.loadPng(chooser.getSelectedFile().toPath());
            ImageControlPolicy controlPolicy = chooseImageControlPolicy(loaded.image());
            if (controlPolicy == ImageControlPolicy.CANCEL) {
                return;
            }
            lastPathMemory.rememberSelection(chooser.getSelectedFile());
            boolean hadControls = !points.isEmpty() || !strokes.isEmpty();
            clearTutorialSession();
            applyLoadedImage(loaded, controlPolicy);
            projectDirty = hadControls && controlPolicy != ImageControlPolicy.RESET;
            markPreviewDirty();
            refreshPreview();
            statusLabel.setText("PNG loaded: " + loaded.label() + statusSuffix(controlPolicy));
        } catch (IOException ex) {
            showError("Could not load PNG", ex);
        }
    }

    private ImageControlPolicy chooseImageControlPolicy(BufferedImage nextImage) {
        if (originalImage == null || (points.isEmpty() && strokes.isEmpty())) {
            return ImageControlPolicy.KEEP;
        }
        boolean controlsFit = EditorImageControlBounds.controlsFitImage(points, strokes, nextImage);
        Object[] options = controlsFit
                ? new Object[] {"Keep controls", "Reset controls", "Cancel"}
                : new Object[] {"Keep as-is", "Clamp into image", "Reset controls", "Cancel"};
        String message = controlsFit
                ? "A new image may not match the current points and traits. What do you want to do?"
                : "Some current points or trait vertices are outside the new image. What do you want to do?";
        int choice = JOptionPane.showOptionDialog(
                this,
                message,
                "Load PNG",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);
        if (controlsFit) {
            return switch (choice) {
                case 0 -> ImageControlPolicy.KEEP;
                case 1 -> ImageControlPolicy.RESET;
                default -> ImageControlPolicy.CANCEL;
            };
        }
        return switch (choice) {
            case 0 -> ImageControlPolicy.KEEP;
            case 1 -> ImageControlPolicy.CLAMP;
            case 2 -> ImageControlPolicy.RESET;
            default -> ImageControlPolicy.CANCEL;
        };
    }

    private void applyLoadedImage(EditorImageLoader.LoadedImage loaded, ImageControlPolicy controlPolicy) {
        setLoadedImage(loaded, controlPolicy == ImageControlPolicy.RESET);
        if (controlPolicy == ImageControlPolicy.CLAMP) {
            EditorImageControlBounds.clampControlsToImage(points, strokes, loaded.image());
            spritePanel.setControls(points, strokes);
        } else if (controlPolicy == ImageControlPolicy.RESET) {
            selectionController.setControlsEnabled(false);
        }
    }

    private void setLoadedImage(EditorImageLoader.LoadedImage loaded, boolean clearControls) {
        originalImage = loaded.image();
        deformedImage = originalImage;
        imagePath = loaded.imagePath();
        spritePanel.setImage(originalImage, clearControls);
    }

    private String statusSuffix(ImageControlPolicy controlPolicy) {
        return switch (controlPolicy) {
            case CLAMP -> " (controls clamped)";
            case RESET -> " (controls reset)";
            default -> "";
        };
    }

    private void openProject() {
        JFileChooser chooser = new JFileChooser();
        lastPathMemory.configure(chooser);
        chooser.setFileFilter(new FileNameExtensionFilter("Breathing project JSON", "json"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        if (!confirmDiscardUnsavedChanges()) {
            return;
        }

        try {
            lastPathMemory.rememberSelection(chooser.getSelectedFile());
            clearTutorialSession();
            BreathingProject project = BreathingProject.load(chooser.getSelectedFile().toPath());
            applyProject(project);
            projectDirty = false;
            statusLabel.setText("Project opened: " + chooser.getSelectedFile().getName());
        } catch (IOException | RuntimeException ex) {
            showError("Could not open project", ex);
        }
    }

    private void applyProject(BreathingProject project) throws IOException {
        setLoadedImage(EditorImageLoader.loadProjectImage(project), true);
        animator.setDurationSeconds(project.durationSeconds());
        durationSpinner.setValue(project.durationSeconds());
        breathingStrengthSpinner.setValue(project.breathingStrength());
        spritePanel.setControls(project.copiedPoints(), project.copiedStrokes());
        markPreviewDirty();
        refreshPreview();
    }

    private void saveProject() {
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this, "Load a PNG first.", "Project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        lastPathMemory.configure(chooser);
        chooser.setFileFilter(new FileNameExtensionFilter("Breathing project JSON", "json"));
        chooser.setSelectedFile(new File("breathing.json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path target = EditorFilePaths.withExtension(chooser.getSelectedFile().toPath(), ".json");
        if (!EditorExportDialogs.confirmOverwrite(this, target)) {
            return;
        }
        try {
            BreathingProject.fromEditorState(imagePath, originalImage, animator.durationSeconds(), number(breathingStrengthSpinner), points, strokes).save(target);
            projectDirty = false;
            lastPathMemory.rememberSelection(target.toFile());
            statusLabel.setText("Project saved: " + target.getFileName());
        } catch (IOException ex) {
            showError("Could not save project", ex);
        }
    }

    private void exportRatioPreset() {
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this, "Load a PNG first.", "Ratio preset", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (points.isEmpty() && strokes.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Add at least one point or trait.", "Ratio preset", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Path target = EditorExportDialogs.chooseRatioPresetTarget(this, lastPathMemory);
        if (target == null) {
            return;
        }
        try {
            RatioControlPreset.fromControls(originalImage, animator.durationSeconds(), number(breathingStrengthSpinner), points, strokes).save(target);
            statusLabel.setText("Ratio preset exported: " + target.getFileName());
        } catch (IOException | RuntimeException ex) {
            showError("Could not export ratio preset", ex instanceof Exception exception ? exception : new RuntimeException(ex));
        }
    }

    private void batchApplyRatioPreset() {
        EditorExportDialogs.BatchSelection selection;
        try {
            selection = EditorExportDialogs.chooseBatchSelection(this, lastPathMemory);
        } catch (IOException | RuntimeException ex) {
            showError("Could not prepare batch export", ex instanceof Exception exception ? exception : new RuntimeException(ex));
            return;
        }
        if (selection == null) {
            return;
        }
        runBatchExport(selection.preset(), selection.imageFiles(), selection.outputDirectory(), selection.format());
    }

    private void runBatchExport(RatioControlPreset preset, List<File> imageFiles, Path outputDirectory, EditorExportService.BatchExportFormat format) {
        if (exportInProgress) {
            statusLabel.setText("An export is already running.");
            return;
        }
        setExportInProgress(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        statusLabel.setText("Batch export: 0/" + imageFiles.size());
        new SwingWorker<EditorExportService.BatchExportResult, Integer>() {
            @Override
            protected EditorExportService.BatchExportResult doInBackground() throws Exception {
                // The service owns filesystem/rendering work; the worker only bridges progress
                // back to Swing's publish/process contract.
                return EditorExportService.runBatch(preset, imageFiles, outputDirectory, format, EXPORT_FRAMES, this::publish);
            }

            @Override
            protected void process(List<Integer> chunks) {
                int processed = chunks.get(chunks.size() - 1);
                statusLabel.setText("Batch export: " + processed + "/" + imageFiles.size());
            }

            @Override
            protected void done() {
                setExportInProgress(false);
                setCursor(Cursor.getDefaultCursor());
                try {
                    EditorExportService.BatchExportResult result = get();
                    String suffix = result.failures().isEmpty() ? "" : ", " + result.failures().size() + " failed";
                    statusLabel.setText("Batch export done: " + result.exported() + "/" + imageFiles.size() + " images" + suffix);
                    if (!result.failures().isEmpty()) {
                        showBatchFailures(result.failures());
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError("Could not run batch export", ex);
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    showError("Could not run batch export", cause instanceof Exception exception ? exception : new RuntimeException(cause));
                }
            }
        }.execute();
    }

    private void togglePlayback() {
        long now = System.nanoTime();
        if (animator.running()) {
            animator.pause(now);
            playButton.setText("Play");
        } else {
            animator.play(now);
            playButton.setText("Pause");
        }
        markPreviewDirty();
        refreshPreview();
    }

    private void stopPlayback() {
        animator.stop();
        playButton.setText("Play");
        markPreviewDirty();
        refreshPreview();
    }

    private void clearTutorialSession() {
        tutorialSession.clear();
        closeTutorialButton.setEnabled(false);
    }

    private void loadChipsTutorial() {
        if (!confirmDiscardUnsavedChanges()) {
            return;
        }
        tutorialSession.captureBeforeTutorial(captureSnapshot());
        try {
            BreathingProject project = tutorialSession.loadChipsProject();
            applyProject(project);
            projectDirty = false;
            tutorialSession.markTutorialLoaded();
            closeTutorialButton.setEnabled(tutorialSession.hasSnapshot());
            statusLabel.setText(tutorialSession.loadedStatus());
        } catch (IOException | RuntimeException ex) {
            showError("Could not load Chips tutorial", ex);
        }
    }

    private EditorTutorialSession.Snapshot captureSnapshot() {
        return new EditorTutorialSession.Snapshot(
                originalImage,
                imagePath,
                animator.durationSeconds(),
                number(breathingStrengthSpinner),
                points,
                strokes,
                spritePanel.toolMode());
    }

    private void restoreBeforeTutorial() {
        EditorTutorialSession.Snapshot snapshot = tutorialSession.consumeSnapshot();
        if (snapshot == null) {
            return;
        }
        originalImage = snapshot.image();
        deformedImage = originalImage;
        imagePath = snapshot.imagePath();
        animator.setDurationSeconds(snapshot.durationSeconds());
        durationSpinner.setValue(snapshot.durationSeconds());
        breathingStrengthSpinner.setValue(snapshot.breathingStrength());
        spritePanel.setToolMode(snapshot.toolMode());
        pointToolButton.setSelected(snapshot.toolMode() == SpriteEditorPanel.ToolMode.POINT);
        strokeToolButton.setSelected(snapshot.toolMode() == SpriteEditorPanel.ToolMode.STROKE);
        spritePanel.setImage(originalImage);
        spritePanel.setControls(snapshot.points(), snapshot.strokes());
        closeTutorialButton.setEnabled(false);
        showPointEditor();
        projectDirty = true;
        markPreviewDirty();
        refreshPreview();
        statusLabel.setText("Tutorial closed: restored saved image, points and traits.");
    }

    private void showAnimationPreview() {
        if (!canExport()) {
            return;
        }
        setExportInProgress(true);
        statusLabel.setText("Rendering animation preview...");
        animationPreviewRenderer.render(
                new EditorAnimationPreviewRenderer.RenderRequest(
                        originalImage,
                        points,
                        strokes,
                        EXPORT_FRAMES,
                        number(breathingStrengthSpinner),
                        animator.durationSeconds()),
                result -> {
                    animationPreviewPanel.setFrames(result.frames(), result.delayMs());
                    animationPreviewPanel.start();
                    timer.stop();
                    // Successful animation preview switches to pre-rendered playback, so the live
                    // editor timer must stay paused even though the heavy render lock is released.
                    resumeTimerAfterExport = false;
                    setExportInProgress(false);
                    sideScrollPane.setVisible(false);
                    centerLayout.show(centerPanel, "preview");
                    previewButton.setEnabled(false);
                    editPointsButton.setEnabled(true);
                    statusLabel.setText("Animation preview. Click Edit points to adjust control points.");
                },
                cause -> {
                    setExportInProgress(false);
                    showError("Could not render preview", cause instanceof Exception exception ? exception : new RuntimeException(cause));
                });
    }

    private void showPointEditor() {
        animationPreviewPanel.stop();
        if (!timer.isRunning() && !exportInProgress) {
            timer.start();
        }
        sideScrollPane.setVisible(true);
        centerLayout.show(centerPanel, "points");
        previewButton.setEnabled(!exportInProgress);
        editPointsButton.setEnabled(false);
        refreshPreview();
    }

    private void addDefaultTorsoPoints() {
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this, "Load a PNG first.", "Points", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if ((!points.isEmpty() || !strokes.isEmpty()) && !confirmReplaceControls("Auto points will replace all current points and traits.")) {
            return;
        }
        projectDirty = true;
        points.clear();
        points.addAll(AnimationExporter.defaultTorsoPoints(originalImage));
        strokes.clear();
        spritePanel.setControls(points, strokes);
        markPreviewDirty();
        refreshPreview();
        statusLabel.setText("Breathing points added. Adjust as needed.");
    }

    private void exportPngSequence() {
        if (!canExport()) {
            return;
        }
        Path directory = EditorExportDialogs.choosePngSequenceDirectory(this, lastPathMemory);
        if (directory == null) {
            return;
        }
        runExport("Rendering PNG sequence...", "PNG sequence exported: " + directory, "Could not export PNG sequence",
                request -> EditorExportService.exportPngSequence(request, directory));
    }

    private void exportSpriteSheet() {
        if (!canExport()) {
            return;
        }
        Path target = EditorExportDialogs.chooseSpriteSheetTarget(this, lastPathMemory);
        if (target == null) {
            return;
        }
        runExport("Rendering spritesheet...", "Spritesheet and atlases exported: " + target.getFileName(), "Could not export spritesheet",
                request -> EditorExportService.exportSpriteSheet(request, target));
    }

    private void exportAnimatedPng() {
        if (!canExport()) {
            return;
        }
        Path target = EditorExportDialogs.chooseAnimatedPngTarget(this, lastPathMemory);
        if (target == null) {
            return;
        }
        runExport("Rendering animated PNG...", "Animated PNG exported: " + target.getFileName(), "Could not export animated PNG",
                request -> EditorExportService.exportAnimatedPng(request, target));
    }

    private void exportGif() {
        if (!canExport()) {
            return;
        }
        Path target = EditorExportDialogs.chooseGifTarget(this, lastPathMemory);
        if (target == null) {
            return;
        }
        runExport("Rendering GIF...", "GIF exported: " + target.getFileName(), "Could not export GIF",
                request -> EditorExportService.exportGif(request, target));
    }

    private void runExport(String runningMessage, String doneMessage, String errorMessage, ExportJob exportJob) {
        if (exportInProgress) {
            statusLabel.setText("An export is already running.");
            return;
        }
        EditorExportService.ExportRequest request = exportRequest();
        setExportInProgress(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        statusLabel.setText(runningMessage);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                exportJob.run(request);
                return null;
            }

            @Override
            protected void done() {
                setExportInProgress(false);
                setCursor(Cursor.getDefaultCursor());
                try {
                    get();
                    statusLabel.setText(doneMessage);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError(errorMessage, ex);
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    showError(errorMessage, cause instanceof Exception exception ? exception : new RuntimeException(cause));
                }
            }
        }.execute();
    }

    private EditorExportService.ExportRequest exportRequest() {
        return new EditorExportService.ExportRequest(
                originalImage,
                points,
                strokes,
                EXPORT_FRAMES,
                number(breathingStrengthSpinner),
                animator.durationSeconds());
    }

    private void setExportInProgress(boolean inProgress) {
        // Export, batch, and animation preview all render many frames. A single gate keeps
        // those jobs from competing with the live preview timer for CPU.
        exportInProgress = inProgress;
        if (inProgress) {
            resumeTimerAfterExport = timer.isRunning();
            timer.stop();
            livePreview.cancelActive();
        } else if (resumeTimerAfterExport && sideScrollPane.isVisible()) {
            timer.start();
        }
        for (JButton button : exportActionButtons) {
            button.setEnabled(!inProgress);
        }
        previewButton.setEnabled(!inProgress);
    }

    private void showBatchFailures(List<String> failures) {
        int limit = Math.min(10, failures.size());
        StringBuilder message = new StringBuilder("Some images could not be exported:");
        for (int i = 0; i < limit; i++) {
            message.append(System.lineSeparator()).append("- ").append(failures.get(i));
        }
        if (failures.size() > limit) {
            message.append(System.lineSeparator()).append("...");
        }
        JOptionPane.showMessageDialog(this, message.toString(), "Batch export", JOptionPane.WARNING_MESSAGE);
    }

    private boolean canExport() {
        if (exportInProgress) {
            JOptionPane.showMessageDialog(this, "A render or export is already running.", "Export", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this, "Load a PNG first.", "Export", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        if (points.isEmpty() && strokes.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Add at least one point or trait.", "Export", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        return true;
    }

    private void shutdownApplication() {
        timer.stop();
        animationPreviewPanel.stop();
        livePreview.cancelActive();
        dispose();
        System.exit(0);
    }

    private boolean confirmReplaceControls(String message) {
        int choice = JOptionPane.showConfirmDialog(
                this,
                message,
                "Replace controls",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    private boolean confirmDiscardUnsavedChanges() {
        if (!projectDirty) {
            return true;
        }
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Discard unsaved changes?",
                "Unsaved changes",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    private void markProjectDirty() {
        if (originalImage != null) {
            projectDirty = true;
        }
    }

    private void markPreviewDirty() {
        livePreview.markDirty();
    }

    private void refreshPreview() {
        livePreview.refresh(
                new EditorLivePreview.RenderRequest(
                        originalImage,
                        points,
                        strokes,
                        animator.phase(System.nanoTime()),
                        number(breathingStrengthSpinner)),
                exportInProgress,
                animator.running(),
                deformedImage != null,
                result -> {
                    deformedImage = result.image();
                    spritePanel.setPreview(deformedImage, result.phase(), result.breathingStrength());
                },
                cause -> statusLabel.setText("Preview error: " + cause.getMessage()),
                this::refreshPreview);
    }

    private static JSpinner spinner(double value, double min, double max, double step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        spinner.setPreferredSize(new Dimension(118, 28));
        spinner.setMinimumSize(new Dimension(96, 28));
        spinner.setMaximumSize(new Dimension(118, 28));
        JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) spinner.getEditor();
        editor.getTextField().setColumns(7);
        return spinner;
    }

    private double number(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    private void showError(String message, Exception ex) {
        JOptionPane.showMessageDialog(this, message + "\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }




    private enum ImageControlPolicy {
        KEEP,
        CLAMP,
        RESET,
        CANCEL
    }

    @FunctionalInterface
    private interface ExportJob {
        void run(EditorExportService.ExportRequest request) throws IOException;
    }

}
