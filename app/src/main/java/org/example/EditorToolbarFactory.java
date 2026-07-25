package org.example;

import java.util.List;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;

final class EditorToolbarFactory {
    private EditorToolbarFactory() {
    }

    static JToolBar create(
            Actions actions,
            JButton playButton,
            JButton previewButton,
            JButton editPointsButton,
            JButton closeTutorialButton,
            List<JButton> exportActionButtons) {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        toolbar.add(button("Load PNG", actions.loadPng()));
        toolbar.add(button("Open JSON", actions.openProject()));
        toolbar.add(button("Save JSON", actions.saveProject()));

        JButton exportRatioPreset = button("Export ratio preset", actions.exportRatioPreset());
        exportActionButtons.add(exportRatioPreset);
        toolbar.add(exportRatioPreset);

        JButton batchApply = button("Batch apply", actions.batchApplyRatioPreset());
        exportActionButtons.add(batchApply);
        toolbar.add(batchApply);

        toolbar.addSeparator();

        playButton.addActionListener(event -> actions.togglePlayback().run());
        toolbar.add(playButton);
        toolbar.add(button("Stop", actions.stopPlayback()));

        previewButton.addActionListener(event -> actions.showAnimationPreview().run());
        toolbar.add(previewButton);

        editPointsButton.addActionListener(event -> actions.showPointEditor().run());
        toolbar.add(editPointsButton);

        toolbar.add(button("Auto points", actions.addDefaultTorsoPoints()));
        toolbar.add(button("Chips tutorial", actions.loadChipsTutorial()));

        closeTutorialButton.setEnabled(false);
        closeTutorialButton.addActionListener(event -> actions.restoreBeforeTutorial().run());
        toolbar.add(closeTutorialButton);

        toolbar.addSeparator();

        JButton exportPng = button("PNG sequence", actions.exportPngSequence());
        exportActionButtons.add(exportPng);
        toolbar.add(exportPng);

        JButton exportSheet = button("PNG spritesheet", actions.exportSpriteSheet());
        exportActionButtons.add(exportSheet);
        toolbar.add(exportSheet);

        JButton exportApng = button("Animated PNG", actions.exportAnimatedPng());
        exportActionButtons.add(exportApng);
        toolbar.add(exportApng);

        JButton exportGif = button("Animated GIF", actions.exportGif());
        exportActionButtons.add(exportGif);
        toolbar.add(exportGif);

        toolbar.addSeparator();
        toolbar.add(helpButton(actions.showTutorialHelp(), actions.showAboutHelp()));

        return toolbar;
    }

    private static JButton button(String label, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(event -> action.run());
        return button;
    }

    private static JButton helpButton(Runnable showTutorial, Runnable showAbout) {
        // A toolbar popup keeps help discoverable without consuming permanent horizontal
        // space for separate Tutorial and About buttons.
        JButton helpButton = new JButton("Help");
        JPopupMenu helpMenu = new JPopupMenu();
        JMenuItem tutorialItem = new JMenuItem("Tutorial");
        JMenuItem aboutItem = new JMenuItem("About");
        tutorialItem.addActionListener(event -> showTutorial.run());
        aboutItem.addActionListener(event -> showAbout.run());
        helpMenu.add(tutorialItem);
        helpMenu.add(aboutItem);
        helpButton.addActionListener(event -> helpMenu.show(helpButton, 0, helpButton.getHeight()));
        return helpButton;
    }

    record Actions(
            Runnable loadPng,
            Runnable openProject,
            Runnable saveProject,
            Runnable exportRatioPreset,
            Runnable batchApplyRatioPreset,
            Runnable togglePlayback,
            Runnable stopPlayback,
            Runnable showAnimationPreview,
            Runnable showPointEditor,
            Runnable addDefaultTorsoPoints,
            Runnable loadChipsTutorial,
            Runnable restoreBeforeTutorial,
            Runnable exportPngSequence,
            Runnable exportSpriteSheet,
            Runnable exportAnimatedPng,
            Runnable exportGif,
            Runnable showTutorialHelp,
            Runnable showAboutHelp) {
    }
}
