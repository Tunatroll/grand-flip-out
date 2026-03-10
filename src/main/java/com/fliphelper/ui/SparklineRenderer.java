package com.fliphelper.ui;

import java.awt.*;
import java.awt.geom.Path2D;
import javax.swing.JPanel;
import java.util.List;

/**
 * Sparkline — Tiny inline price charts (60×20px) rendered via Graphics2D.
 * 
 * Inline sparkline charts for slot cards showing recent price movement
 * has nothing visual at all. This is a pure UX differentiator.
 *
 * Usage:
 *   SparklineRenderer.paint(g2d, x, y, width, height, priceHistory, color);
 * 
 * Features:
 * - Auto-scales Y axis to data range
 * - Color-coded: green if trending up, red if trending down
 * - Optional fill gradient under the line
 * - Handles sparse data gracefully (needs minimum 3 points)
 * - Renders a horizontal reference line at the mean
 */
public class SparklineRenderer
{
    // Default dimensions
    public static final int DEFAULT_WIDTH = 60;
    public static final int DEFAULT_HEIGHT = 20;

    private static final Color TREND_UP = new Color(0x00, 0xD2, 0x6A);
    private static final Color TREND_DOWN = new Color(0xFF, 0x47, 0x57);
    private static final Color TREND_FLAT = new Color(0xFF, 0xB8, 0x00);
    private static final Color REFERENCE_LINE = new Color(0x60, 0x60, 0x80, 100);
    private static final Color FILL_UP = new Color(0x00, 0xD2, 0x6A, 30);
    private static final Color FILL_DOWN = new Color(0xFF, 0x47, 0x57, 30);

    /**
     * Paint a sparkline chart at the given position.
     *
     * @param g2d    Graphics context
     * @param x      Top-left X
     * @param y      Top-left Y
     * @param width  Chart width in pixels
     * @param height Chart height in pixels
     * @param data   Price data points (oldest → newest)
     * @param forceColor Optional color override (null = auto green/red)
     */
    public static void paint(Graphics2D g2d, int x, int y, int width, int height,
                             List<Long> data, Color forceColor)
    {
        if (data == null || data.size() < 3) return;

        // Save graphics state
        Stroke oldStroke = g2d.getStroke();
        Color oldColor = g2d.getColor();
        Object oldAA = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int n = data.size();
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (long v : data)
        {
            if (v < min) min = v;
            if (v > max) max = v;
        }

        // Prevent division by zero for flat data
        long range = max - min;
        if (range == 0) range = 1;

        // Determine trend color
        long first = data.get(0);
        long last = data.get(n - 1);
        double changePct = first > 0 ? (double)(last - first) / first : 0;
        Color lineColor;
        Color fillColor;
        if (forceColor != null)
        {
            lineColor = forceColor;
            fillColor = new Color(forceColor.getRed(), forceColor.getGreen(), forceColor.getBlue(), 30);
        }
        else if (changePct > 0.005)
        {
            lineColor = TREND_UP;
            fillColor = FILL_UP;
        }
        else if (changePct < -0.005)
        {
            lineColor = TREND_DOWN;
            fillColor = FILL_DOWN;
        }
        else
        {
            lineColor = TREND_FLAT;
            fillColor = new Color(0xFF, 0xB8, 0x00, 20);
        }

        // Build the line path
        Path2D.Float linePath = new Path2D.Float();
        Path2D.Float fillPath = new Path2D.Float();
        float padding = 1f;
        float drawWidth = width - 2 * padding;
        float drawHeight = height - 2 * padding;

        for (int i = 0; i < n; i++)
        {
            float px = x + padding + (drawWidth * i / (n - 1));
            float py = y + padding + drawHeight - (drawHeight * (data.get(i) - min) / range);

            if (i == 0)
            {
                linePath.moveTo(px, py);
                fillPath.moveTo(px, y + height); // Start fill at bottom
                fillPath.lineTo(px, py);
            }
            else
            {
                linePath.lineTo(px, py);
                fillPath.lineTo(px, py);
            }
        }

        // Close fill path along the bottom
        fillPath.lineTo(x + width - padding, y + height);
        fillPath.lineTo(x + padding, y + height);
        fillPath.closePath();

        // Draw fill gradient
        g2d.setColor(fillColor);
        g2d.fill(fillPath);

        // Draw reference line at mean
        long sum = 0;
        for (long v : data) sum += v;
        float meanY = y + padding + drawHeight - (drawHeight * ((sum / n) - min) / range);
        g2d.setColor(REFERENCE_LINE);
        g2d.setStroke(new BasicStroke(0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
            1f, new float[]{2f, 2f}, 0f)); // Dashed line
        g2d.drawLine(x, (int) meanY, x + width, (int) meanY);

        // Draw the price line
        g2d.setColor(lineColor);
        g2d.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.draw(linePath);

        // Draw endpoint dot (current price)
        float lastX = x + width - padding;
        float lastY = y + padding + drawHeight - (drawHeight * (last - min) / range);
        g2d.fillOval((int)(lastX - 2), (int)(lastY - 2), 4, 4);

        // Restore graphics state
        g2d.setStroke(oldStroke);
        g2d.setColor(oldColor);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
    }

    /**
     * Create a JPanel that renders a sparkline. For use inside Swing layouts.
     */
    public static JPanel createPanel(List<Long> data, int width, int height, Color color)
    {
        return new JPanel()
        {

            {
                setOpaque(false);
                setPreferredSize(new Dimension(width, height));
                setMinimumSize(new Dimension(width, height));
                setMaximumSize(new Dimension(width, height));
            }

            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                SparklineRenderer.paint(g2d, 0, 0, getWidth(), getHeight(), data, color);
            }
        };
    }

    /**
     * Create a sparkline icon suitable for use in a JLabel.
     * Returns an ImageIcon that can be set with label.setIcon().
     */
    public static javax.swing.ImageIcon createIcon(List<Long> data, int width, int height, Color color)
    {
        if (data == null || data.size() < 3) return null;

        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
            width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        paint(g2d, 0, 0, width, height, data, color);
        g2d.dispose();

        return new javax.swing.ImageIcon(img);
    }
}
