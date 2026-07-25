package org.example;

import java.awt.Color;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import javax.swing.JColorChooser;
import javax.swing.JSpinner;

final class EditorSelectionController {
    private final Component parent;
    private final SpriteEditorPanel spritePanel;
    private final EditorSidePanelFactory.Components components;
    private final Supplier<BufferedImage> imageSupplier;
    private final DoubleSupplier globalBreathingStrengthSupplier;
    private final Runnable controlsChanged;
    private final Consumer<String> statusConsumer;
    // Spinner updates can trigger each other through Swing listeners; this guard keeps
    // model-to-UI synchronization from being mistaken for a user edit.
    private boolean updatingControls;

    EditorSelectionController(
            Component parent,
            SpriteEditorPanel spritePanel,
            EditorSidePanelFactory.Components components,
            Supplier<BufferedImage> imageSupplier,
            DoubleSupplier globalBreathingStrengthSupplier,
            Runnable controlsChanged,
            Consumer<String> statusConsumer) {
        this.parent = parent;
        this.spritePanel = spritePanel;
        this.components = components;
        this.imageSupplier = imageSupplier;
        this.globalBreathingStrengthSupplier = globalBreathingStrengthSupplier;
        this.controlsChanged = controlsChanged;
        this.statusConsumer = statusConsumer;
    }

    void wireSelectionSettings() {
        components.pointXSpinner().addChangeListener(event -> updateSelectedPointPosition());
        components.pointYSpinner().addChangeListener(event -> updateSelectedPointPosition());

        Runnable updateSelection = () -> {
            if (updatingControls) {
                return;
            }
            spritePanel.forEachSelectedControl(point -> {
                point.setOffsetX(number(components.offsetXSpinner()));
                point.setOffsetY(number(components.offsetYSpinner()));
                point.setRadius(number(components.radiusSpinner()));
                point.setAnimated(components.animatedBox().isSelected());
                point.setUnmovable(components.unmovableBox().isSelected());
            }, stroke -> {
                stroke.setOffsetX(number(components.offsetXSpinner()));
                stroke.setOffsetY(number(components.offsetYSpinner()));
                stroke.setRadius(number(components.radiusSpinner()));
                stroke.setAnimated(components.animatedBox().isSelected());
                stroke.setUnmovable(components.unmovableBox().isSelected());
            });
            controlsChanged.run();
        };
        components.offsetXSpinner().addChangeListener(event -> { updateSelection.run(); syncWarpAngleSpinner(); });
        components.offsetYSpinner().addChangeListener(event -> { updateSelection.run(); syncWarpAngleSpinner(); });
        components.warpAngleSpinner().addChangeListener(event -> updateSelectedControlAngle());
        components.radiusSpinner().addChangeListener(event -> updateSelection.run());
        components.outlineWidthSpinner().addChangeListener(event -> updateSelectedPointOutlineWidth());
        components.customBreathingStrengthBox().addActionListener(event -> updateSelectedCustomBreathingStrength());
        components.controlBreathingStrengthSpinner().addChangeListener(event -> updateSelectedCustomBreathingStrength());
        // Reflect the mutual exclusion immediately in the panel, otherwise the UI
        // can briefly show a combination the model will normalize away.
        components.animatedBox().addActionListener(event -> {
            if (updatingControls) {
                return;
            }
            if (components.animatedBox().isSelected()) {
                updatingControls = true;
                try {
                    components.unmovableBox().setSelected(false);
                } finally {
                    updatingControls = false;
                }
            }
            updateSelection.run();
        });
        components.unmovableBox().addActionListener(event -> {
            if (updatingControls) {
                return;
            }
            if (components.unmovableBox().isSelected()) {
                updatingControls = true;
                try {
                    components.animatedBox().setSelected(false);
                } finally {
                    updatingControls = false;
                }
            }
            updateSelection.run();
        });
    }

    void syncGlobalBreathingStrengthChanged() {
        SpriteEditorPanel.Selection selection = spritePanel.selectedControl();
        if (selection.present() && !selection.hasCustomBreathingStrength()) {
            updatingControls = true;
            try {
                components.controlBreathingStrengthSpinner().setValue(globalBreathingStrengthSupplier.getAsDouble());
            } finally {
                updatingControls = false;
            }
        }
    }

    void showSelectedControl(SpriteEditorPanel.Selection selection) {
        updatingControls = true;
        try {
            setControlsEnabled(selection.present());
            if (selection.present()) {
                components.pointXSpinner().setValue(selection.centerX());
                components.pointYSpinner().setValue(selection.centerY());
                components.offsetXSpinner().setValue(selection.offsetX());
                components.offsetYSpinner().setValue(selection.offsetY());
                components.radiusSpinner().setValue(selection.radius());
                components.outlineWidthSpinner().setValue(selection.outlineWidth());
                components.customBreathingStrengthBox().setSelected(selection.hasCustomBreathingStrength());
                components.controlBreathingStrengthSpinner().setValue(
                        selection.effectiveBreathingStrength(globalBreathingStrengthSupplier.getAsDouble()));
                components.controlBreathingStrengthSpinner().setEnabled(selection.hasCustomBreathingStrength());
                components.warpAngleSpinner().setValue(selection.warpAngleDegrees());
                components.animatedBox().setSelected(selection.animated());
                components.unmovableBox().setSelected(selection.unmovable());
                updateColorButton(selection.colorRgb());
                updateStatus(selection);
            }
        } finally {
            updatingControls = false;
        }
    }

    void setControlsEnabled(boolean enabled) {
        boolean singlePoint = spritePanel.selectedPointCount() == 1 && spritePanel.selectedControlCount() == 1;
        components.pointXSpinner().setEnabled(enabled && singlePoint);
        components.pointYSpinner().setEnabled(enabled && singlePoint);
        components.offsetXSpinner().setEnabled(enabled);
        components.offsetYSpinner().setEnabled(enabled);
        components.radiusSpinner().setEnabled(enabled);
        components.outlineWidthSpinner().setEnabled(enabled && selectedControlsAreOnlyPoints());
        components.customBreathingStrengthBox().setEnabled(enabled);
        components.controlBreathingStrengthSpinner().setEnabled(enabled && components.customBreathingStrengthBox().isSelected());
        components.warpAngleSpinner().setEnabled(enabled);
        components.animatedBox().setEnabled(enabled);
        components.unmovableBox().setEnabled(enabled);
        components.colorButton().setEnabled(enabled);
        components.deletePointButton().setEnabled(enabled);
        components.nextPointButton().setEnabled(enabled);
    }

    void chooseSelectedColor() {
        SpriteEditorPanel.Selection selection = spritePanel.selectedControl();
        if (!selection.present()) {
            return;
        }
        Color selected = JColorChooser.showDialog(parent, "Control color", new Color(selection.colorRgb()));
        if (selected == null) {
            return;
        }
        int rgb = selected.getRGB() & 0xffffff;
        spritePanel.forEachSelectedControl(
                point -> point.setColorRgb(rgb),
                stroke -> stroke.setColorRgb(rgb));
        updateColorButton(rgb);
        spritePanel.repaintControls();
    }

    private void updateSelectedPointPosition() {
        if (updatingControls) {
            return;
        }
        ControlPoint point = spritePanel.selectedPoint();
        BufferedImage image = imageSupplier.get();
        if (point == null || image == null || spritePanel.selectedControlCount() != 1) {
            return;
        }
        point.setX(clamp(number(components.pointXSpinner()), 0.0, image.getWidth() - 1.0));
        point.setY(clamp(number(components.pointYSpinner()), 0.0, image.getHeight() - 1.0));
        controlsChanged.run();
        spritePanel.repaintControls();
    }

    private void updateSelectedControlAngle() {
        if (updatingControls) {
            return;
        }
        SpriteEditorPanel.Selection selection = spritePanel.selectedControl();
        if (!selection.present()) {
            return;
        }
        double angle = number(components.warpAngleSpinner());
        spritePanel.forEachSelectedControl(
                point -> {
                    if (point.animated() && !point.unmovable()) {
                        point.setWarpAngleDegrees(angle);
                    }
                },
                stroke -> {
                    if (stroke.animated() && !stroke.unmovable()) {
                        stroke.setWarpAngleDegrees(angle);
                    }
                });
        updatingControls = true;
        try {
            components.offsetXSpinner().setValue(selection.offsetX());
            components.offsetYSpinner().setValue(selection.offsetY());
        } finally {
            updatingControls = false;
        }
        controlsChanged.run();
    }

    private void syncWarpAngleSpinner() {
        if (updatingControls) {
            return;
        }
        SpriteEditorPanel.Selection selection = spritePanel.selectedControl();
        if (!selection.present()) {
            return;
        }
        updatingControls = true;
        try {
            components.warpAngleSpinner().setValue(selection.warpAngleDegrees());
        } finally {
            updatingControls = false;
        }
    }

    private boolean selectedControlsAreOnlyPoints() {
        int selectedPoints = spritePanel.selectedPointCount();
        return selectedPoints > 0 && selectedPoints == spritePanel.selectedControlCount() && spritePanel.selectedStrokeCount() == 0;
    }

    private void updateSelectedPointOutlineWidth() {
        if (updatingControls || !selectedControlsAreOnlyPoints()) {
            return;
        }
        double outlineWidth = number(components.outlineWidthSpinner());
        // Circle outline width is intentionally visual-only: it changes editor readability
        // without changing the deformation radius or exported motion.
        spritePanel.forEachSelectedControl(
                point -> point.setOutlineWidth(outlineWidth),
                stroke -> { });
        spritePanel.repaintControls();
    }

    private void updateSelectedCustomBreathingStrength() {
        if (updatingControls) {
            return;
        }
        SpriteEditorPanel.Selection selection = spritePanel.selectedControl();
        if (!selection.present()) {
            return;
        }
        boolean custom = components.customBreathingStrengthBox().isSelected();
        Double value = custom ? number(components.controlBreathingStrengthSpinner()) : null;
        components.controlBreathingStrengthSpinner().setEnabled(custom);
        spritePanel.forEachSelectedControl(
                point -> point.setCustomBreathingStrength(value),
                stroke -> stroke.setCustomBreathingStrength(value));
        controlsChanged.run();
    }

    private void updateStatus(SpriteEditorPanel.Selection selection) {
        if (selection.multiple()) {
            statusConsumer.accept(String.format("Selection: %d controls", selection.count()));
        } else if (selection.point() != null) {
            statusConsumer.accept(String.format("Point: x %.1f, y %.1f", selection.centerX(), selection.centerY()));
        } else {
            statusConsumer.accept(String.format(
                    "Trait: %d points, center %.1f %.1f",
                    selection.stroke().pointCount(),
                    selection.centerX(),
                    selection.centerY()));
        }
    }

    private void updateColorButton(int rgb) {
        components.colorButton().setBackground(new Color(rgb));
        components.colorButton().setForeground(contrastColor(rgb));
        components.colorButton().setOpaque(true);
    }

    private Color contrastColor(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return r * 299 + g * 587 + b * 114 > 128000 ? Color.BLACK : Color.WHITE;
    }

    private double number(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
