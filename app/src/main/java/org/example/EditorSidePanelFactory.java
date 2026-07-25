package org.example;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.BoxLayout;

final class EditorSidePanelFactory {
    private EditorSidePanelFactory() {
    }

    static JPanel create(Components components, Actions actions) {
        JPanel side = new JPanel();
        side.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(340, 640));

        side.add(sectionTitle("Animation"));
        side.add(row("Duration s", components.durationSpinner()));
        side.add(row("Breath strength", components.breathingStrengthSpinner()));
        side.add(gap());
        side.add(sectionTitle("Tool"));
        side.add(toolButtons(components, actions));
        side.add(gap());
        side.add(sectionTitle("Selected control"));
        side.add(pairRow("Point X", components.pointXSpinner(), "Point Y", components.pointYSpinner()));
        side.add(pairRow("Offset X", components.offsetXSpinner(), "Offset Y", components.offsetYSpinner()));
        side.add(row("Warp angle", components.warpAngleSpinner()));
        side.add(row("Action radius", components.radiusSpinner()));
        components.customBreathingStrengthBox().setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(components.customBreathingStrengthBox());
        side.add(row("Breath value", components.controlBreathingStrengthSpinner()));
        components.animatedBox().setAlignmentX(Component.LEFT_ALIGNMENT);
        components.unmovableBox().setAlignmentX(Component.LEFT_ALIGNMENT);
        components.colorButton().addActionListener(event -> actions.chooseSelectedColor().run());
        side.add(components.animatedBox());
        side.add(components.unmovableBox());
        side.add(cosmeticRow(components.colorButton(), components.outlineWidthSpinner()));
        side.add(gap());
        side.add(selectionButtons(components, actions));

        side.add(gap());
        side.add(sectionTitle("Mouse"));
        side.add(help("Point: click to add/select"));
        side.add(help("Trait: drag to draw"));
        side.add(help("Shift drag selection: warp angle"));
        side.add(help("Ctrl drag: select points/traits"));
        side.add(help("Mouse wheel: zoom"));
        side.add(help("Right/middle click: pan"));
        return side;
    }

    private static JPanel toolButtons(Components components, Actions actions) {
        JPanel toolButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        toolButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        ButtonGroup toolGroup = new ButtonGroup();
        toolGroup.add(components.pointToolButton());
        toolGroup.add(components.strokeToolButton());
        components.pointToolButton().addActionListener(event -> actions.selectPointTool().run());
        components.strokeToolButton().addActionListener(event -> actions.selectStrokeTool().run());
        toolButtons.add(components.pointToolButton());
        toolButtons.add(components.strokeToolButton());
        return toolButtons;
    }

    private static JPanel selectionButtons(Components components, Actions actions) {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        components.nextPointButton().addActionListener(event -> actions.selectNextControl().run());
        components.deletePointButton().addActionListener(event -> actions.deleteSelectedControl().run());
        buttons.add(components.nextPointButton());
        buttons.add(components.deletePointButton());
        return buttons;
    }

    private static JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(8, 0, 6, 0));
        return label;
    }

    private static JPanel row(String label, JSpinner spinner) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(326, 34));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 0, 2, 8);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        JLabel rowLabel = new JLabel(label);
        rowLabel.setPreferredSize(new Dimension(150, 26));
        row.add(rowLabel, c);
        c.gridx = 1;
        c.weightx = 0.0;
        c.fill = GridBagConstraints.NONE;
        row.add(spinner, c);
        return row;
    }

    private static JPanel pairRow(String leftLabel, JSpinner leftSpinner, String rightLabel, JSpinner rightSpinner) {
        // Paired X/Y-style values reduce vertical pressure while preserving their shared meaning.
        JPanel row = new JPanel(new GridLayout(1, 2, 8, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(326, 52));
        row.add(stackedField(leftLabel, leftSpinner));
        row.add(stackedField(rightLabel, rightSpinner));
        return row;
    }

    private static JPanel stackedField(String label, JSpinner spinner) {
        JPanel field = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        field.add(new JLabel(label), c);
        c.gridy = 1;
        c.insets = new Insets(2, 0, 0, 0);
        field.add(spinner, c);
        return field;
    }

    private static JPanel cosmeticRow(JButton colorButton, JSpinner outlineWidthSpinner) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(326, 34));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 0, 2, 8);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        row.add(colorButton, c);
        c.gridx = 1;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        row.add(new JLabel("Circle line"), c);
        c.gridx = 2;
        c.weightx = 0.0;
        c.fill = GridBagConstraints.NONE;
        row.add(outlineWidthSpinner, c);
        return row;
    }

    private static JPanel gap() {
        JPanel gap = new JPanel();
        gap.setMaximumSize(new Dimension(1, 12));
        return gap;
    }

    private static JLabel help(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        return label;
    }

    record Components(
            JSpinner durationSpinner,
            JSpinner breathingStrengthSpinner,
            JSpinner pointXSpinner,
            JSpinner pointYSpinner,
            JSpinner offsetXSpinner,
            JSpinner offsetYSpinner,
            JSpinner warpAngleSpinner,
            JSpinner radiusSpinner,
            JSpinner outlineWidthSpinner,
            JSpinner controlBreathingStrengthSpinner,
            JCheckBox customBreathingStrengthBox,
            JCheckBox animatedBox,
            JCheckBox unmovableBox,
            JButton colorButton,
            JButton nextPointButton,
            JButton deletePointButton,
            JToggleButton pointToolButton,
            JToggleButton strokeToolButton) {
    }

    record Actions(
            Runnable selectPointTool,
            Runnable selectStrokeTool,
            Runnable chooseSelectedColor,
            Runnable selectNextControl,
            Runnable deleteSelectedControl) {
    }
}
