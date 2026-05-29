/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.ui;

import com.fliphelper.model.TimeseriesPoint;
import net.runelite.client.ui.ColorScheme;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Native Java2D line chart of avg high vs avg low price over time for a single
 * item, drawn from OSRS Wiki /timeseries data. Dark-themed to match RuneLite.
 *
 * <p>Render is pure-Swing: set state via {@link #setLoading()},
 * {@link #setPoints(String, List)} or {@link #setError(String)} on the EDT.
 */
public class PriceChartPanel extends JPanel
{
    private static final Color HIGH_COLOR = new Color(0xE0, 0xA8, 0x2E); // sell / wheat-gold
    private static final Color LOW_COLOR = new Color(0x5F, 0xA8, 0xD4);  // buy / blue
    private static final Color GRID_COLOR = new Color(0x33, 0x33, 0x33);
    private static final Color AXIS_TEXT = new Color(0x9A, 0x9A, 0x9A);
    private static final int PAD_LEFT = 58;
    private static final int PAD_RIGHT = 12;
    private static final int PAD_TOP = 30;
    private static final int PAD_BOTTOM = 28;

    private final SimpleDateFormat axisDateFormat = new SimpleDateFormat("MM/dd HH:mm");

    private String title = "Price History";
    private String message = "No item selected";
    private List<TimeseriesPoint> points = new ArrayList<>();

    public PriceChartPanel()
    {
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setPreferredSize(new Dimension(480, 280));
    }

    /** Show a "loading" placeholder. */
    public void setLoading()
    {
        this.points = new ArrayList<>();
        this.message = "Loading price history...";
        repaint();
    }

    /** Show an error / empty message. */
    public void setError(String msg)
    {
        this.points = new ArrayList<>();
        this.message = msg;
        repaint();
    }

    /** Supply the data to chart. Filters out empty (0/0) points. */
    public void setPoints(String title, List<TimeseriesPoint> data)
    {
        this.title = title != null ? title : "Price History";
        List<TimeseriesPoint> cleaned = new ArrayList<>();
        if (data != null)
        {
            for (TimeseriesPoint p : data)
            {
                if (p.getAvgHighPrice() > 0 || p.getAvgLowPrice() > 0)
                {
                    cleaned.add(p);
                }
            }
        }
        this.points = cleaned;
        this.message = cleaned.isEmpty() ? "No price history available" : null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Title
            g2.setColor(Color.WHITE);
            g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
            g2.drawString(title, PAD_LEFT, 18);

            if (message != null)
            {
                drawCenteredMessage(g2, message, w, h);
                return;
            }

            int plotLeft = PAD_LEFT;
            int plotRight = w - PAD_RIGHT;
            int plotTop = PAD_TOP;
            int plotBottom = h - PAD_BOTTOM;
            int plotW = plotRight - plotLeft;
            int plotH = plotBottom - plotTop;
            if (plotW <= 10 || plotH <= 10)
            {
                return;
            }

            // Value range across both series (ignoring zeros).
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            long minTs = Long.MAX_VALUE;
            long maxTs = Long.MIN_VALUE;
            for (TimeseriesPoint p : points)
            {
                if (p.getAvgHighPrice() > 0)
                {
                    min = Math.min(min, p.getAvgHighPrice());
                    max = Math.max(max, p.getAvgHighPrice());
                }
                if (p.getAvgLowPrice() > 0)
                {
                    min = Math.min(min, p.getAvgLowPrice());
                    max = Math.max(max, p.getAvgLowPrice());
                }
                minTs = Math.min(minTs, p.getTimestamp());
                maxTs = Math.max(maxTs, p.getTimestamp());
            }
            if (min == Long.MAX_VALUE || max == Long.MIN_VALUE)
            {
                drawCenteredMessage(g2, "No price history available", w, h);
                return;
            }
            if (max == min)
            {
                max = min + 1;
            }
            long tsRange = Math.max(1, maxTs - minTs);
            long valRange = max - min;

            // Horizontal grid + Y axis labels (4 divisions).
            g2.setFont(getFont().deriveFont(10f));
            FontMetrics fm = g2.getFontMetrics();
            int divisions = 4;
            for (int i = 0; i <= divisions; i++)
            {
                int y = plotBottom - (plotH * i / divisions);
                g2.setColor(GRID_COLOR);
                g2.drawLine(plotLeft, y, plotRight, y);

                long val = min + (valRange * i / divisions);
                String label = formatGp(val);
                g2.setColor(AXIS_TEXT);
                g2.drawString(label, plotLeft - 6 - fm.stringWidth(label), y + fm.getAscent() / 2 - 1);
            }

            // X axis time labels (start / mid / end).
            drawTimeLabel(g2, fm, minTs, plotLeft, plotBottom);
            drawTimeLabel(g2, fm, minTs + tsRange / 2, plotLeft + plotW / 2, plotBottom);
            drawTimeLabel(g2, fm, maxTs, plotRight, plotBottom);

            // Series.
            Stroke line = new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            g2.setStroke(line);
            drawSeries(g2, true, HIGH_COLOR, plotLeft, plotBottom, plotW, plotH, minTs, tsRange, min, valRange);
            drawSeries(g2, false, LOW_COLOR, plotLeft, plotBottom, plotW, plotH, minTs, tsRange, min, valRange);

            // Legend.
            drawLegend(g2, plotLeft, plotTop - 14);
        }
        finally
        {
            g2.dispose();
        }
    }

    private void drawSeries(Graphics2D g2, boolean high, Color color,
                            int plotLeft, int plotBottom, int plotW, int plotH,
                            long minTs, long tsRange, long min, long valRange)
    {
        g2.setColor(color);
        int prevX = -1;
        int prevY = -1;
        for (TimeseriesPoint p : points)
        {
            long val = high ? p.getAvgHighPrice() : p.getAvgLowPrice();
            if (val <= 0)
            {
                prevX = -1; // break the line across gaps
                continue;
            }
            int x = plotLeft + (int) ((p.getTimestamp() - minTs) * plotW / tsRange);
            int y = plotBottom - (int) ((val - min) * plotH / valRange);
            if (prevX >= 0)
            {
                g2.drawLine(prevX, prevY, x, y);
            }
            prevX = x;
            prevY = y;
        }
    }

    private void drawTimeLabel(Graphics2D g2, FontMetrics fm, long ts, int x, int plotBottom)
    {
        String label = axisDateFormat.format(new Date(ts * 1000L));
        int tw = fm.stringWidth(label);
        int tx = Math.max(PAD_LEFT, Math.min(x - tw / 2, getWidth() - PAD_RIGHT - tw));
        g2.setColor(AXIS_TEXT);
        g2.drawString(label, tx, plotBottom + fm.getAscent() + 3);
    }

    private void drawLegend(Graphics2D g2, int x, int y)
    {
        g2.setFont(getFont().deriveFont(10f));
        FontMetrics fm = g2.getFontMetrics();
        int cx = x + 130;
        g2.setColor(HIGH_COLOR);
        g2.fillRect(cx, y, 10, 10);
        g2.setColor(AXIS_TEXT);
        g2.drawString("Sell (high)", cx + 14, y + fm.getAscent());
        int second = cx + 14 + fm.stringWidth("Sell (high)") + 14;
        g2.setColor(LOW_COLOR);
        g2.fillRect(second, y, 10, 10);
        g2.setColor(AXIS_TEXT);
        g2.drawString("Buy (low)", second + 14, y + fm.getAscent());
    }

    private void drawCenteredMessage(Graphics2D g2, String msg, int w, int h)
    {
        g2.setFont(getFont().deriveFont(12f));
        g2.setColor(AXIS_TEXT);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
    }

    /** Compact gp formatting for axis ticks (e.g. 1.2k, 3.4m). */
    static String formatGp(long v)
    {
        long a = Math.abs(v);
        if (a >= 1_000_000_000L)
        {
            return trim(v / 1_000_000_000.0) + "b";
        }
        if (a >= 1_000_000L)
        {
            return trim(v / 1_000_000.0) + "m";
        }
        if (a >= 1_000L)
        {
            return trim(v / 1_000.0) + "k";
        }
        return Long.toString(v);
    }

    private static String trim(double d)
    {
        if (d == Math.floor(d))
        {
            return Long.toString((long) d);
        }
        return String.format("%.1f", d);
    }
}
