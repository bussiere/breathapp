package org.example;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class App {
    private App() {
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--export-demo")) {
            try {
                DemoExport.run(args.length > 1 ? java.nio.file.Path.of(args[1]) : java.nio.file.Path.of("build/demo-output"));
                return;
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("Unable to export demo animation", ex);
            }
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ignored) {
                // Keep Swing default look and feel if the platform one is unavailable.
            }

            BreathingEditorFrame frame = new BreathingEditorFrame();
            frame.setVisible(true);
        });
    }
}
