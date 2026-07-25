package org.example;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.TexturePaint;
import java.awt.image.BufferedImage;

final class CheckerPaints {
    private static final int TILE_SIZE = 18;

    private CheckerPaints() {
    }

    static TexturePaint create(Color first, Color second) {
        // Panels repaint frequently during drag, zoom, and playback. A tiny tiled paint avoids
        // rebuilding the checkerboard with nested fill loops on every frame.
        BufferedImage tile = new BufferedImage(TILE_SIZE * 2, TILE_SIZE * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = tile.createGraphics();
        try {
            graphics.setColor(first);
            graphics.fillRect(0, 0, TILE_SIZE, TILE_SIZE);
            graphics.fillRect(TILE_SIZE, TILE_SIZE, TILE_SIZE, TILE_SIZE);
            graphics.setColor(second);
            graphics.fillRect(TILE_SIZE, 0, TILE_SIZE, TILE_SIZE);
            graphics.fillRect(0, TILE_SIZE, TILE_SIZE, TILE_SIZE);
        } finally {
            graphics.dispose();
        }
        return new TexturePaint(tile, new Rectangle(0, 0, tile.getWidth(), tile.getHeight()));
    }
}
