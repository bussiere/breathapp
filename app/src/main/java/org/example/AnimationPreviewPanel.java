package org.example;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.Timer;

public final class AnimationPreviewPanel extends JPanel {
    private final Timer timer;
    private TexturePaint checkerPaint;
    private List<BufferedImage> frames = List.of();
    private int frameIndex;

    public AnimationPreviewPanel() {
        setBackground(new Color(33, 35, 38));
        setPreferredSize(new Dimension(820, 640));
        timer = new Timer(100, event -> advance());
    }

    public void setFrames(List<BufferedImage> frames, int delayMs) {
        this.frames = List.copyOf(new ArrayList<>(frames));
        frameIndex = 0;
        timer.setDelay(Math.max(20, delayMs));
        repaint();
    }

    public void start() {
        if (!frames.isEmpty()) {
            timer.start();
        }
    }

    public void stop() {
        timer.stop();
    }

    int frameIndex() {
        return frameIndex;
    }

    private void advance() {
        if (frames.isEmpty()) {
            return;
        }
        frameIndex = (frameIndex + 1) % frames.size();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            paintChecker(g);
            if (frames.isEmpty()) {
                paintMessage(g, "Preview unavailable");
                return;
            }
            BufferedImage frame = frames.get(frameIndex);
            double scale = Math.min((getWidth() - 80.0) / frame.getWidth(), (getHeight() - 80.0) / frame.getHeight());
            scale = Math.max(0.1, Math.min(scale, 8.0));
            int width = (int) Math.round(frame.getWidth() * scale);
            int height = (int) Math.round(frame.getHeight() * scale);
            int x = (getWidth() - width) / 2;
            int y = (getHeight() - height) / 2;
            g.drawImage(frame, x, y, width, height, null);
            g.setColor(new Color(230, 234, 240));
            g.drawString("Animation preview", 16, 24);
        } finally {
            g.dispose();
        }
    }

    private void paintChecker(Graphics2D g) {
        if (checkerPaint == null) {
            checkerPaint = createCheckerPaint();
        }
        g.setPaint(checkerPaint);
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    private TexturePaint createCheckerPaint() {
        return CheckerPaints.create(new Color(47, 50, 55), new Color(40, 43, 48));
    }

    private void paintMessage(Graphics2D g, String text) {
        FontMetrics metrics = g.getFontMetrics();
        g.setColor(new Color(220, 225, 232));
        g.drawString(text, Math.max(20, (getWidth() - metrics.stringWidth(text)) / 2), getHeight() / 2);
    }
}
