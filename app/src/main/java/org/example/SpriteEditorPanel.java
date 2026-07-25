package org.example;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

public final class SpriteEditorPanel extends JPanel {
    private static final int POINT_SCREEN_RADIUS = 7;

    private final List<ControlPoint> points;
    private final List<ControlStroke> strokes;
    private final Set<Integer> selectedPointIndices = new LinkedHashSet<>();
    private final Set<Integer> selectedStrokeIndices = new LinkedHashSet<>();
    private Consumer<Selection> selectionListener = selection -> { };
    private Runnable controlsChangedListener = () -> { };
    private BufferedImage originalImage;
    private BufferedImage previewImage;
    private TexturePaint checkerPaint;
    private ToolMode toolMode = ToolMode.POINT;
    private SelectionKind selectionKind = SelectionKind.NONE;
    private int selectedIndex = -1;
    private double zoom = 1.0;
    private double panX;
    private double panY;
    private double phase;
    private double breathingStrength = 1.0;
    private Point lastMouse;
    private Point selectionStart;
    private Point selectionEnd;
    // Dragging can generate hundreds of mouse events. These flags let the overlay move
    // immediately while deferring expensive deform work and spinner sync until release.
    private boolean strokeDrawChanged;
    private boolean controlDragChanged;
    private DragMode dragMode = DragMode.NONE;

    public SpriteEditorPanel(List<ControlPoint> points, List<ControlStroke> strokes) {
        this.points = points;
        this.strokes = strokes;
        setBackground(new Color(42, 44, 48));
        setFocusable(true);
        setPreferredSize(new Dimension(820, 640));

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                lastMouse = event.getPoint();
                if (SwingUtilities.isRightMouseButton(event) || SwingUtilities.isMiddleMouseButton(event)) {
                    dragMode = DragMode.PAN;
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(event) || originalImage == null) {
                    dragMode = DragMode.NONE;
                    return;
                }

                if (event.isControlDown()) {
                    dragMode = DragMode.SELECTION_BOX;
                    selectionStart = event.getPoint();
                    selectionEnd = event.getPoint();
                    repaint();
                    return;
                }

                Point2D imagePoint = screenToImage(event.getPoint());
                if (toolMode == ToolMode.STROKE) {
                    pressStrokeTool(event, imagePoint);
                } else {
                    pressPointTool(event, imagePoint);
                }
                notifySelection();
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (lastMouse == null) {
                    lastMouse = event.getPoint();
                }
                double dx = event.getX() - lastMouse.x;
                double dy = event.getY() - lastMouse.y;

                if (dragMode == DragMode.PAN) {
                    panX += dx;
                    panY += dy;
                } else if (dragMode == DragMode.SELECTION_BOX) {
                    selectionEnd = event.getPoint();
                } else if (dragMode == DragMode.WARP_ANGLE && selectedControl() != null) {
                    updateSelectedWarpAngle(screenToImage(event.getPoint()));
                } else if (dragMode == DragMode.POINT && selectedControlCount() > 0) {
                    // Move controls in model space during the gesture, but avoid notifying the
                    // frame on every pixel; otherwise SwingWorkers and side-panel spinners churn.
                    Point2D previous = screenToImage(lastMouse);
                    Point2D current = screenToImage(event.getPoint());
                    controlDragChanged = translateSelectedControls(current.getX() - previous.getX(), current.getY() - previous.getY()) || controlDragChanged;
                } else if (dragMode == DragMode.STROKE_DRAW && selectedStroke() != null) {
                    Point2D imagePoint = screenToImage(event.getPoint());
                    if (insideImage(imagePoint)) {
                        int before = selectedStroke().pointCount();
                        selectedStroke().addPoint(
                                clamp(imagePoint.getX(), 0.0, originalImage.getWidth() - 1.0),
                                clamp(imagePoint.getY(), 0.0, originalImage.getHeight() - 1.0));
                        strokeDrawChanged = strokeDrawChanged || selectedStroke().pointCount() != before;
                    }
                }
                lastMouse = event.getPoint();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (dragMode == DragMode.SELECTION_BOX && selectionStart != null && selectionEnd != null) {
                    selectControlsInBox(selectionRectangle());
                    selectionStart = null;
                    selectionEnd = null;
                    notifySelection();
                    repaint();
                } else if ((dragMode == DragMode.STROKE_DRAW && strokeDrawChanged)
                        || ((dragMode == DragMode.POINT || dragMode == DragMode.WARP_ANGLE) && controlDragChanged)) {
                    // One notification at the end preserves responsive dragging while still
                    // producing a single fresh deformation and an accurate side-panel state.
                    notifyControlsChanged();
                    notifySelection();
                }
                strokeDrawChanged = false;
                controlDragChanged = false;
                dragMode = DragMode.NONE;
                lastMouse = null;
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                if (originalImage == null) {
                    return;
                }
                event.consume();
                double previousZoom = zoom;
                zoom = clamp(zoom * Math.pow(1.12, -event.getPreciseWheelRotation()), 0.1, 12.0);
                Point2D before = screenToImage(event.getPoint(), previousZoom);
                Point2D after = screenToImage(event.getPoint(), zoom);
                panX += (after.getX() - before.getX()) * zoom;
                panY += (after.getY() - before.getY()) * zoom;
                repaint();
            }
        };

        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        installKeyBindings();
    }

    public void setSelectionListener(Consumer<Selection> selectionListener) {
        this.selectionListener = selectionListener == null ? selection -> { } : selectionListener;
    }

    public void setControlsChangedListener(Runnable controlsChangedListener) {
        this.controlsChangedListener = controlsChangedListener == null ? () -> { } : controlsChangedListener;
    }

    private void installKeyBindings() {
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete-selection");
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "delete-selection");
        getActionMap().put("delete-selection", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                deleteSelectedControl();
            }
        });
    }

    public void setToolMode(ToolMode toolMode) {
        this.toolMode = toolMode == null ? ToolMode.POINT : toolMode;
        dragMode = DragMode.NONE;
        repaint();
    }

    public ToolMode toolMode() {
        return toolMode;
    }

    public void setImage(BufferedImage image) {
        setImage(image, true);
    }

    public void setImage(BufferedImage image, boolean clearControls) {
        originalImage = image;
        previewImage = image;
        if (clearControls) {
            points.clear();
            strokes.clear();
            clearSelection();
        }
        zoom = 1.0;
        panX = 0.0;
        panY = 0.0;
        fitImage();
        notifySelection();
        repaint();
    }

    public void setPreview(BufferedImage previewImage, double phase, double breathingStrength) {
        this.previewImage = previewImage;
        this.phase = phase;
        this.breathingStrength = breathingStrength;
        repaint();
    }

    public void setControls(List<ControlPoint> loadedPoints, List<ControlStroke> loadedStrokes) {
        List<ControlPoint> pointCopies = loadedPoints.stream().map(ControlPoint::copy).toList();
        List<ControlStroke> strokeCopies = loadedStrokes.stream().map(ControlStroke::copy).toList();
        points.clear();
        points.addAll(pointCopies);
        strokes.clear();
        strokes.addAll(strokeCopies);
        if (toolMode == ToolMode.STROKE && !strokes.isEmpty()) {
            selectOnlyStroke(0);
        } else if (!points.isEmpty()) {
            selectOnlyPoint(0);
        } else if (!strokes.isEmpty()) {
            selectOnlyStroke(0);
        } else {
            clearSelection();
        }
        notifySelection();
        repaint();
    }

    public void setPoints(List<ControlPoint> loadedPoints) {
        setControls(loadedPoints, strokes);
    }

    public ControlPoint selectedPoint() {
        if (selectionKind != SelectionKind.POINT || selectedIndex < 0 || selectedIndex >= points.size()) {
            return null;
        }
        return points.get(selectedIndex);
    }

    public ControlStroke selectedStroke() {
        if (selectionKind != SelectionKind.STROKE || selectedIndex < 0 || selectedIndex >= strokes.size()) {
            return null;
        }
        return strokes.get(selectedIndex);
    }

    public Selection selectedControl() {
        return new Selection(selectedPoint(), selectedStroke(), selectedControlCount());
    }

    public int selectedControlCount() {
        return selectedPointIndices.size() + selectedStrokeIndices.size();
    }

    public int selectedPointCount() {
        return selectedPointIndices.size();
    }

    public int selectedStrokeCount() {
        return selectedStrokeIndices.size();
    }

    public void forEachSelectedControl(Consumer<ControlPoint> pointConsumer, Consumer<ControlStroke> strokeConsumer) {
        if (selectedPointIndices.isEmpty() && selectedStrokeIndices.isEmpty()) {
            Selection selection = selectedControl();
            if (selection.point() != null) {
                pointConsumer.accept(selection.point());
            } else if (selection.stroke() != null) {
                strokeConsumer.accept(selection.stroke());
            }
            return;
        }
        for (int index : selectedPointIndices) {
            if (index >= 0 && index < points.size()) {
                pointConsumer.accept(points.get(index));
            }
        }
        for (int index : selectedStrokeIndices) {
            if (index >= 0 && index < strokes.size()) {
                strokeConsumer.accept(strokes.get(index));
            }
        }
    }

    public void repaintControls() {
        repaint();
    }

    public void deleteSelectedControl() {
        boolean changed = false;
        if (!selectedPointIndices.isEmpty() || !selectedStrokeIndices.isEmpty()) {
            for (int index : selectedPointIndices.stream().sorted((a, b) -> Integer.compare(b, a)).toList()) {
                if (index >= 0 && index < points.size()) {
                    points.remove(index);
                    changed = true;
                }
            }
            for (int index : selectedStrokeIndices.stream().sorted((a, b) -> Integer.compare(b, a)).toList()) {
                if (index >= 0 && index < strokes.size()) {
                    strokes.remove(index);
                    changed = true;
                }
            }
            clearSelection();
        } else if (selectionKind == SelectionKind.POINT && selectedIndex >= 0 && selectedIndex < points.size()) {
            points.remove(selectedIndex);
            changed = true;
            clearSelection();
        } else if (selectionKind == SelectionKind.STROKE && selectedIndex >= 0 && selectedIndex < strokes.size()) {
            strokes.remove(selectedIndex);
            changed = true;
            clearSelection();
        }
        if (changed) {
            notifyControlsChanged();
        }
        notifySelection();
        repaint();
    }

    public void selectNextControl() {
        if ((selectionKind == SelectionKind.STROKE || toolMode == ToolMode.STROKE) && !strokes.isEmpty()) {
            selectOnlyStroke((selectedIndex + 1 + strokes.size()) % strokes.size());
        } else if (!points.isEmpty()) {
            selectOnlyPoint((selectedIndex + 1 + points.size()) % points.size());
        } else {
            clearSelection();
        }
        notifySelection();
        repaint();
    }

    public void fitImage() {
        if (originalImage == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        double fitX = (getWidth() - 80.0) / originalImage.getWidth();
        double fitY = (getHeight() - 80.0) / originalImage.getHeight();
        zoom = clamp(Math.min(fitX, fitY), 0.1, 6.0);
        panX = 0.0;
        panY = 0.0;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            paintChecker(g);

            if (previewImage == null) {
                paintEmptyState(g);
                return;
            }

            double left = imageLeft();
            double top = imageTop();
            int drawWidth = (int) Math.round(previewImage.getWidth() * zoom);
            int drawHeight = (int) Math.round(previewImage.getHeight() * zoom);
            g.drawImage(previewImage, (int) Math.round(left), (int) Math.round(top), drawWidth, drawHeight, null);
            paintStrokes(g, left, top);
            paintPoints(g, left, top);
            paintSelectionBox(g);
        } finally {
            g.dispose();
        }
    }

    private void pressPointTool(MouseEvent event, Point2D imagePoint) {
        int hit = findPoint(imagePoint, POINT_SCREEN_RADIUS / zoom + 3.0);
        if (hit >= 0) {
            keepExistingSelectionOrSelectPoint(hit);
            if (event.isShiftDown() && canEditWarpAngle(selectedControl())) {
                dragMode = DragMode.WARP_ANGLE;
                updateSelectedWarpAngle(imagePoint);
            } else {
                dragMode = DragMode.POINT;
            }
        } else if (insideImage(imagePoint)) {
            points.add(new ControlPoint(imagePoint.getX(), imagePoint.getY(), 0.0, -4.0, 80.0, true));
            selectOnlyPoint(points.size() - 1);
            dragMode = DragMode.POINT;
            notifyControlsChanged();
        }
    }

    private void pressStrokeTool(MouseEvent event, Point2D imagePoint) {
        int hit = findStroke(imagePoint, 8.0 / zoom + 3.0);
        if (hit >= 0) {
            keepExistingSelectionOrSelectStroke(hit);
            if (event.isShiftDown() && canEditWarpAngle(selectedControl())) {
                dragMode = DragMode.WARP_ANGLE;
                updateSelectedWarpAngle(imagePoint);
            } else {
                dragMode = DragMode.POINT;
            }
        } else if (insideImage(imagePoint)) {
            strokes.add(new ControlStroke(imagePoint.getX(), imagePoint.getY(), 0.0, -4.0, 24.0, true));
            selectOnlyStroke(strokes.size() - 1);
            dragMode = DragMode.STROKE_DRAW;
            strokeDrawChanged = false;
            notifyControlsChanged();
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
        return CheckerPaints.create(new Color(55, 58, 63), new Color(48, 51, 56));
    }

    private void paintEmptyState(Graphics2D g) {
        String text = "Load a PNG to start";
        FontMetrics metrics = g.getFontMetrics();
        int textWidth = metrics.stringWidth(text);
        g.setColor(new Color(220, 225, 232));
        g.drawString(text, Math.max(20, (getWidth() - textWidth) / 2), getHeight() / 2);
    }

    private void paintStrokes(Graphics2D g, double left, double top) {
        for (int i = 0; i < strokes.size(); i++) {
            ControlStroke stroke = strokes.get(i);
            List<Point2D.Double> strokePoints = stroke.pointsView();
            if (strokePoints.isEmpty()) {
                continue;
            }
            boolean selected = selectedStrokeIndices.contains(i);
            float influenceWidth = (float) Math.max(1.0, stroke.radius() * zoom * 2.0);
            g.setStroke(new BasicStroke(influenceWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(colorWithAlpha(stroke.colorRgb(), selected ? 85 : 50));
            drawStrokePath(g, strokePoints, left, top, 0.0, 0.0);

            double dx = stroke.currentOffsetX(phase, breathingStrength) * zoom;
            double dy = stroke.currentOffsetY(phase, breathingStrength) * zoom;
            g.setStroke(new BasicStroke(selected ? 3.0f : 2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(stroke.colorRgb()));
            drawStrokePath(g, strokePoints, left, top, dx, dy);

            double centerX = left + stroke.centerX() * zoom + dx;
            double centerY = top + stroke.centerY() * zoom + dy;
            g.setColor(selected ? new Color(255, 196, 87) : new Color(30, 32, 35));
            g.draw(new Ellipse2D.Double(centerX - POINT_SCREEN_RADIUS, centerY - POINT_SCREEN_RADIUS, POINT_SCREEN_RADIUS * 2.0, POINT_SCREEN_RADIUS * 2.0));
            if (stroke.animated() && !stroke.unmovable()) {
                paintWarpArrow(g, centerX, centerY, stroke.warpAngleDegrees());
            }
        }
    }

    private void drawStrokePath(Graphics2D g, List<Point2D.Double> strokePoints, double left, double top, double offsetX, double offsetY) {
        if (strokePoints.size() == 1) {
            Point2D.Double point = strokePoints.get(0);
            double x = left + point.x * zoom + offsetX;
            double y = top + point.y * zoom + offsetY;
            g.draw(new Line2D.Double(x, y, x + 0.1, y + 0.1));
            return;
        }
        for (int i = 1; i < strokePoints.size(); i++) {
            Point2D.Double a = strokePoints.get(i - 1);
            Point2D.Double b = strokePoints.get(i);
            g.draw(new Line2D.Double(
                    left + a.x * zoom + offsetX,
                    top + a.y * zoom + offsetY,
                    left + b.x * zoom + offsetX,
                    top + b.y * zoom + offsetY));
        }
    }

    private void paintPoints(Graphics2D g, double left, double top) {
        for (int i = 0; i < points.size(); i++) {
            ControlPoint point = points.get(i);
            boolean selected = selectedPointIndices.contains(i);
            double baseX = left + point.x() * zoom;
            double baseY = top + point.y() * zoom;
            double currentX = left + (point.x() + point.currentOffsetX(phase, breathingStrength)) * zoom;
            double currentY = top + (point.y() + point.currentOffsetY(phase, breathingStrength)) * zoom;
            double radius = point.radius() * zoom;

            double outlineWidth = Math.max(0.5, point.outlineWidth());
            double innerRadius = Math.max(0.5, radius - outlineWidth / 2.0);
            g.setStroke(new BasicStroke((float) outlineWidth));
            g.setColor(selected ? colorWithAlpha(point.colorRgb(), 130) : colorWithAlpha(point.colorRgb(), 85));
            g.draw(new Ellipse2D.Double(baseX - innerRadius, baseY - innerRadius, innerRadius * 2.0, innerRadius * 2.0));

            g.setColor(new Color(255, 255, 255, 160));
            g.drawLine((int) Math.round(baseX), (int) Math.round(baseY), (int) Math.round(currentX), (int) Math.round(currentY));

            g.setColor(new Color(point.colorRgb()));
            g.fill(new Ellipse2D.Double(currentX - POINT_SCREEN_RADIUS, currentY - POINT_SCREEN_RADIUS, POINT_SCREEN_RADIUS * 2.0, POINT_SCREEN_RADIUS * 2.0));
            g.setColor(selected ? new Color(255, 196, 87) : new Color(30, 32, 35));
            g.setStroke(new BasicStroke(1.0f));
            g.draw(new Ellipse2D.Double(currentX - POINT_SCREEN_RADIUS, currentY - POINT_SCREEN_RADIUS, POINT_SCREEN_RADIUS * 2.0, POINT_SCREEN_RADIUS * 2.0));
            if (point.animated() && !point.unmovable()) {
                paintWarpArrow(g, currentX, currentY, point.warpAngleDegrees());
            }
        }
    }


    boolean translateSelectedControls(double dx, double dy) {
        if (originalImage == null || selectedControlCount() == 0) {
            return false;
        }
        double[] clamped = clampedSelectionDelta(dx, dy);
        double safeDx = clamped[0];
        double safeDy = clamped[1];
        if (safeDx == 0.0 && safeDy == 0.0) {
            return false;
        }
        for (int index : selectedPointIndices) {
            if (index >= 0 && index < points.size()) {
                ControlPoint point = points.get(index);
                point.setX(point.x() + safeDx);
                point.setY(point.y() + safeDy);
            }
        }
        for (int index : selectedStrokeIndices) {
            if (index >= 0 && index < strokes.size()) {
                strokes.get(index).translateBy(safeDx, safeDy);
            }
        }
        return true;
    }

    private double[] clampedSelectionDelta(double dx, double dy) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int index : selectedPointIndices) {
            if (index >= 0 && index < points.size()) {
                ControlPoint point = points.get(index);
                minX = Math.min(minX, point.x());
                minY = Math.min(minY, point.y());
                maxX = Math.max(maxX, point.x());
                maxY = Math.max(maxY, point.y());
            }
        }
        for (int index : selectedStrokeIndices) {
            if (index >= 0 && index < strokes.size()) {
                for (Point2D.Double point : strokes.get(index).pointsView()) {
                    minX = Math.min(minX, point.x);
                    minY = Math.min(minY, point.y);
                    maxX = Math.max(maxX, point.x);
                    maxY = Math.max(maxY, point.y);
                }
            }
        }
        if (!Double.isFinite(minX)) {
            return new double[] {0.0, 0.0};
        }
        // Clamp the whole selection as a group so multi-drag preserves its shape at image edges.
        double safeDx = clamp(dx, -minX, originalImage.getWidth() - 1.0 - maxX);
        double safeDy = clamp(dy, -minY, originalImage.getHeight() - 1.0 - maxY);
        return new double[] {safeDx, safeDy};
    }

    private Color colorWithAlpha(int rgb, int alpha) {
        return new Color((alpha << 24) | (rgb & 0xffffff), true);
    }

    private void paintWarpArrow(Graphics2D g, double centerX, double centerY, double angleDegrees) {
        double radians = Math.toRadians(angleDegrees);
        double ux = Math.cos(radians);
        double uy = Math.sin(radians);
        double tailX = centerX - ux * 3.0;
        double tailY = centerY - uy * 3.0;
        double headX = centerX + ux * 5.0;
        double headY = centerY + uy * 5.0;
        double leftHeadX = headX - ux * 3.0 - uy * 2.0;
        double leftHeadY = headY - uy * 3.0 + ux * 2.0;
        double rightHeadX = headX - ux * 3.0 + uy * 2.0;
        double rightHeadY = headY - uy * 3.0 - ux * 2.0;

        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(20, 24, 28));
        g.drawLine((int) Math.round(tailX), (int) Math.round(tailY), (int) Math.round(headX), (int) Math.round(headY));
        g.drawLine((int) Math.round(headX), (int) Math.round(headY), (int) Math.round(leftHeadX), (int) Math.round(leftHeadY));
        g.drawLine((int) Math.round(headX), (int) Math.round(headY), (int) Math.round(rightHeadX), (int) Math.round(rightHeadY));
    }

    private int findPoint(Point2D imagePoint, double radius) {
        for (int i = points.size() - 1; i >= 0; i--) {
            ControlPoint point = points.get(i);
            double displayX = point.x() + point.currentOffsetX(phase, breathingStrength);
            double displayY = point.y() + point.currentOffsetY(phase, breathingStrength);
            if (imagePoint.distance(displayX, displayY) <= radius) {
                return i;
            }
        }
        return -1;
    }

    private int findStroke(Point2D imagePoint, double padding) {
        for (int i = strokes.size() - 1; i >= 0; i--) {
            ControlStroke stroke = strokes.get(i);
            double offsetX = stroke.currentOffsetX(phase, breathingStrength);
            double offsetY = stroke.currentOffsetY(phase, breathingStrength);
            if (PolylineGeometry.distanceToPolyline(imagePoint, stroke.pointsView(), offsetX, offsetY) <= stroke.radius() + padding) {
                return i;
            }
        }
        return -1;
    }

    private boolean insideImage(Point2D imagePoint) {
        return originalImage != null
                && imagePoint.getX() >= 0.0
                && imagePoint.getY() >= 0.0
                && imagePoint.getX() < originalImage.getWidth()
                && imagePoint.getY() < originalImage.getHeight();
    }

    private Point2D screenToImage(Point point) {
        return screenToImage(point, zoom);
    }

    private Point2D screenToImage(Point point, double zoomValue) {
        return new Point2D.Double((point.x - imageLeft(zoomValue)) / zoomValue, (point.y - imageTop(zoomValue)) / zoomValue);
    }

    private double imageLeft() {
        return imageLeft(zoom);
    }

    private double imageTop() {
        return imageTop(zoom);
    }

    private double imageLeft(double zoomValue) {
        if (originalImage == null) {
            return 0.0;
        }
        return (getWidth() - originalImage.getWidth() * zoomValue) / 2.0 + panX;
    }

    private double imageTop(double zoomValue) {
        if (originalImage == null) {
            return 0.0;
        }
        return (getHeight() - originalImage.getHeight() * zoomValue) / 2.0 + panY;
    }

    private boolean canEditWarpAngle(Selection selection) {
        return selection != null && selection.animated() && !selection.unmovable();
    }

    private void updateSelectedWarpAngle(Point2D imagePoint) {
        Selection selection = selectedControl();
        if (!canEditWarpAngle(selection)) {
            return;
        }
        double dx = imagePoint.getX() - selection.centerX();
        double dy = imagePoint.getY() - selection.centerY();
        if (Math.hypot(dx, dy) <= 0.001) {
            return;
        }
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        // Multi-select uses one absolute angle so a gesture has the same meaning as typing
        // the value into the shared Warp angle spinner.
        forEachSelectedControl(
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
        controlDragChanged = true;
    }

    private void selectControlsInBox(Rectangle2D box) {
        clearSelection();
        if (box == null || box.getWidth() < 3.0 || box.getHeight() < 3.0) {
            return;
        }
        for (int i = 0; i < points.size(); i++) {
            ControlPoint point = points.get(i);
            double x = imageLeft() + (point.x() + point.currentOffsetX(phase, breathingStrength)) * zoom;
            double y = imageTop() + (point.y() + point.currentOffsetY(phase, breathingStrength)) * zoom;
            if (box.contains(x, y)) {
                selectedPointIndices.add(i);
            }
        }
        for (int i = 0; i < strokes.size(); i++) {
            Rectangle2D bounds = strokeScreenBounds(strokes.get(i));
            if (bounds != null && box.intersects(bounds)) {
                selectedStrokeIndices.add(i);
            }
        }
        if (!selectedPointIndices.isEmpty()) {
            selectionKind = SelectionKind.POINT;
            selectedIndex = selectedPointIndices.iterator().next();
        } else if (!selectedStrokeIndices.isEmpty()) {
            selectionKind = SelectionKind.STROKE;
            selectedIndex = selectedStrokeIndices.iterator().next();
        }
    }

    private Rectangle2D selectionRectangle() {
        if (selectionStart == null || selectionEnd == null) {
            return null;
        }
        double x = Math.min(selectionStart.x, selectionEnd.x);
        double y = Math.min(selectionStart.y, selectionEnd.y);
        double width = Math.abs(selectionStart.x - selectionEnd.x);
        double height = Math.abs(selectionStart.y - selectionEnd.y);
        return new Rectangle2D.Double(x, y, width, height);
    }

    private Rectangle2D strokeScreenBounds(ControlStroke stroke) {
        List<Point2D.Double> strokePoints = stroke.pointsView();
        if (strokePoints.isEmpty()) {
            return null;
        }
        double dx = stroke.currentOffsetX(phase, breathingStrength) * zoom;
        double dy = stroke.currentOffsetY(phase, breathingStrength) * zoom;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Point2D.Double point : strokePoints) {
            double x = imageLeft() + point.x * zoom + dx;
            double y = imageTop() + point.y * zoom + dy;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        double padding = Math.max(4.0, stroke.radius() * zoom);
        return new Rectangle2D.Double(minX - padding, minY - padding, maxX - minX + padding * 2.0, maxY - minY + padding * 2.0);
    }

    private void paintSelectionBox(Graphics2D g) {
        Rectangle2D box = selectionRectangle();
        if (box == null || dragMode != DragMode.SELECTION_BOX) {
            return;
        }
        g.setColor(new Color(255, 196, 87, 45));
        g.fill(box);
        g.setStroke(new BasicStroke(1.0f));
        g.setColor(new Color(255, 196, 87, 210));
        g.draw(box);
    }

    private void selectOnlyPoint(int index) {
        clearSelection();
        selectionKind = SelectionKind.POINT;
        selectedIndex = index;
        selectedPointIndices.add(index);
    }

    private void selectOnlyStroke(int index) {
        clearSelection();
        selectionKind = SelectionKind.STROKE;
        selectedIndex = index;
        selectedStrokeIndices.add(index);
    }


    private void keepExistingSelectionOrSelectPoint(int index) {
        if (selectedPointIndices.contains(index)) {
            selectionKind = SelectionKind.POINT;
            selectedIndex = index;
            return;
        }
        selectOnlyPoint(index);
    }

    private void keepExistingSelectionOrSelectStroke(int index) {
        if (selectedStrokeIndices.contains(index)) {
            selectionKind = SelectionKind.STROKE;
            selectedIndex = index;
            return;
        }
        selectOnlyStroke(index);
    }

    private void notifySelection() {
        selectionListener.accept(selectedControl());
    }

    private void notifyControlsChanged() {
        controlsChangedListener.run();
    }

    private void clearSelection() {
        selectedPointIndices.clear();
        selectedStrokeIndices.clear();
        selectionKind = SelectionKind.NONE;
        selectedIndex = -1;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum ToolMode {
        POINT,
        STROKE
    }

    public record Selection(ControlPoint point, ControlStroke stroke, int count) {
        public boolean present() {
            return point != null || stroke != null;
        }

        public boolean multiple() {
            return count > 1;
        }

        public boolean animated() {
            return point != null ? point.animated() : stroke != null && stroke.animated();
        }

        public boolean unmovable() {
            return point != null ? point.unmovable() : stroke != null && stroke.unmovable();
        }

        public double offsetX() {
            return point != null ? point.offsetX() : stroke == null ? 0.0 : stroke.offsetX();
        }

        public double offsetY() {
            return point != null ? point.offsetY() : stroke == null ? 0.0 : stroke.offsetY();
        }

        public double radius() {
            return point != null ? point.radius() : stroke == null ? 1.0 : stroke.radius();
        }

        public int colorRgb() {
            return point != null ? point.colorRgb() : stroke == null ? ControlPoint.DEFAULT_COLOR_RGB : stroke.colorRgb();
        }

        public double outlineWidth() {
            return point == null ? ControlPoint.DEFAULT_OUTLINE_WIDTH : point.outlineWidth();
        }

        public boolean hasCustomBreathingStrength() {
            return point != null ? point.hasCustomBreathingStrength() : stroke != null && stroke.hasCustomBreathingStrength();
        }

        public double effectiveBreathingStrength(double globalBreathingStrength) {
            if (point != null) {
                return point.effectiveBreathingStrength(globalBreathingStrength);
            }
            return stroke == null ? Math.max(0.0, globalBreathingStrength) : stroke.effectiveBreathingStrength(globalBreathingStrength);
        }

        public double warpAngleDegrees() {
            return point != null ? point.warpAngleDegrees() : stroke == null ? ControlPoint.DEFAULT_WARP_ANGLE_DEGREES : stroke.warpAngleDegrees();
        }

        public double centerX() {
            return point != null ? point.x() : stroke == null ? 0.0 : stroke.centerX();
        }

        public double centerY() {
            return point != null ? point.y() : stroke == null ? 0.0 : stroke.centerY();
        }
    }

    private enum SelectionKind {
        NONE,
        POINT,
        STROKE
    }

    private enum DragMode {
        NONE,
        POINT,
        STROKE_DRAW,
        PAN,
        WARP_ANGLE,
        SELECTION_BOX
    }
}
