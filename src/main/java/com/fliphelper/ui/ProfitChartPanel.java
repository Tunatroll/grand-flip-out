package com.fliphelper.ui;

import com.fliphelper.model.FlipItem;
import com.fliphelper.tracker.FlipTracker;
import com.fliphelper.tracker.SessionManager;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;


public class ProfitChartPanel extends JPanel
{
    private final FlipTracker flipTracker;
    private final SessionManager sessionManager;

    // Chart view modes
    public enum ChartMode { SESSION, TODAY, WEEK, ALL_TIME }
    private ChartMode currentMode = ChartMode.SESSION;

    // Colors
    private static final Color PROFIT_GREEN   = new Color(0x00, 0xD2, 0x6A);
    private static final Color LOSS_RED       = new Color(0xFF, 0x47, 0x57);
    private static final Color GOLD           = new Color(0xFF, 0xB8, 0x00);
    private static final Color CHART_BG       = new Color(0x0F, 0x0F, 0x17);
    private static final Color GRID_COLOR     = new Color(0x1E, 0x1E, 0x30);
    private static final Color AXIS_COLOR     = new Color(0x35, 0x35, 0x55);
    private static final Color TEXT_DIM       = new Color(0x60, 0x60, 0x80);

    private JPanel statsBar;
    private JLabel totalProfitLabel;
    private JLabel gpHourLabel;
    private JLabel flipCountLabel;
    private JLabel winRateLabel;
    private ChartCanvas chartCanvas;
    private JPanel modeButtons;

    public ProfitChartPanel(FlipTracker flipTracker, SessionManager sessionManager)
    {
        this.flipTracker = flipTracker;
        this.sessionManager = sessionManager;

        setLayout(new BorderLayout(0, 0));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        add(buildStatsBar(),   BorderLayout.NORTH);
        add(buildChart(),      BorderLayout.CENTER);
        add(buildModeBar(),    BorderLayout.SOUTH);
    }

    // --------------------------------------------------------─
    //  STATS BAR
    // --------------------------------------------------------─

    private JPanel buildStatsBar()
    {
        statsBar = new JPanel(new GridLayout(2, 2, 4, 4));
        statsBar.setBackground(new Color(0x12, 0x12, 0x1E));
        statsBar.setBorder(new EmptyBorder(8, 10, 8, 10));

        totalProfitLabel = makeStat("0 gp",      "TOTAL PROFIT", PROFIT_GREEN);
        gpHourLabel      = makeStat("0 gp/hr",   "GP / HOUR",    GOLD);
        flipCountLabel   = makeStat("0",          "FLIPS",        Color.WHITE);
        winRateLabel     = makeStat("0%",         "WIN RATE",     Color.LIGHT_GRAY);

        statsBar.add(wrapStat("TOTAL PROFIT", totalProfitLabel));
        statsBar.add(wrapStat("GP / HOUR",    gpHourLabel));
        statsBar.add(wrapStat("FLIPS",        flipCountLabel));
        statsBar.add(wrapStat("WIN RATE",     winRateLabel));

        return statsBar;
    }

    private JLabel makeStat(String value, String title, Color color)
    {
        JLabel lbl = new JLabel(value);
        lbl.setForeground(color);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 13f));
        return lbl;
    }

    private JPanel wrapStat(String title, JLabel valueLabel)
    {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setBackground(new Color(0x1A, 0x1A, 0x2E));
        p.setBorder(new EmptyBorder(5, 8, 5, 8));
        JLabel titleLbl = new JLabel(title);
        titleLbl.setForeground(TEXT_DIM);
        titleLbl.setFont(titleLbl.getFont().deriveFont(Font.BOLD, 8f));
        p.add(titleLbl,   BorderLayout.NORTH);
        p.add(valueLabel, BorderLayout.CENTER);
        return p;
    }

    // --------------------------------------------------------─
    //  CHART CANVAS
    // --------------------------------------------------------─

    private JPanel buildChart()
    {
        chartCanvas = new ChartCanvas();
        chartCanvas.setPreferredSize(new Dimension(200, 180));

        JScrollPane scroll = new JScrollPane(chartCanvas);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(CHART_BG);
        return chartCanvas;
    }

    // --------------------------------------------------------─
    //  MODE SELECTOR BAR
    // --------------------------------------------------------─

    private JPanel buildModeBar()
    {
        modeButtons = new JPanel(new GridLayout(1, 4, 2, 0));
        modeButtons.setBackground(new Color(0x12, 0x12, 0x1E));
        modeButtons.setBorder(new EmptyBorder(4, 6, 6, 6));

        String[] labels = {"Session", "Today", "Week", "All Time"};
        ChartMode[] modes = ChartMode.values();

        for (int i = 0; i < labels.length; i++)
        {
            final ChartMode mode = modes[i];
            JButton btn = new JButton(labels[i]);
            btn.setFont(btn.getFont().deriveFont(9f));
            btn.setFocusPainted(false);
            btn.addActionListener(e -> {
                currentMode = mode;
                refreshModeButtons();
                update();
            });
            modeButtons.add(btn);
        }
        refreshModeButtons();
        return modeButtons;
    }

    private void refreshModeButtons()
    {
        ChartMode[] modes = ChartMode.values();
        for (int i = 0; i < modeButtons.getComponentCount(); i++)
        {
            JButton btn = (JButton) modeButtons.getComponent(i);
            boolean active = modes[i] == currentMode;
            btn.setBackground(active ? new Color(0x2A, 0x2A, 0x55) : new Color(0x1A, 0x1A, 0x2E));
            btn.setForeground(active ? GOLD : TEXT_DIM);
        }
    }

    // --------------------------------------------------------─
    //  DATA + UPDATE
    // --------------------------------------------------------─

    public void update()
    {
        SwingUtilities.invokeLater(() -> {
            List<FlipItem> flips = getFilteredFlips();
            updateStatsBar(flips);
            chartCanvas.setFlips(flips);
            chartCanvas.repaint();
        });
    }

    private List<FlipItem> getFilteredFlips()
    {
        List<FlipItem> all = new ArrayList<>(flipTracker.getCompletedFlips());
        Instant cutoff = getCutoff();
        if (cutoff == null) return all;
        List<FlipItem> filtered = new ArrayList<>();
        for (FlipItem f : all)
        {
            if (f.getSellTime() != null && f.getSellTime().isAfter(cutoff))
                filtered.add(f);
        }
        return filtered;
    }

    private Instant getCutoff()
    {
        switch (currentMode)
        {
            case SESSION: return null; // use session data via tracker
            case TODAY:   return Instant.now().minusSeconds(86400);
            case WEEK:    return Instant.now().minusSeconds(604800);
            case ALL_TIME: return null;
            default:      return null;
        }
    }

    private void updateStatsBar(List<FlipItem> flips)
    {
        long total = 0;
        int wins = 0, losses = 0;
        Instant earliest = null, latest = null;

        for (FlipItem f : flips)
        {
            if (!f.isComplete()) continue;
            long p = f.getProfit();
            total += p;
            if (p >= 0) wins++; else losses++;
            if (f.getSellTime() != null)
            {
                if (earliest == null || f.getSellTime().isBefore(earliest)) earliest = f.getSellTime();
                if (latest   == null || f.getSellTime().isAfter(latest))   latest   = f.getSellTime();
            }
        }

        // For SESSION mode, use live session data
        if (currentMode == ChartMode.SESSION)
        {
            total = flipTracker.getSessionProfit().get();
            wins = flipTracker.getSessionFlipCount().get();
        }

        // GP/hour
        double gpHour = 0;
        if (earliest != null && latest != null)
        {
            double hours = Math.max(0.017, (latest.toEpochMilli() - earliest.toEpochMilli()) / 3600000.0);
            gpHour = total / hours;
        }
        else if (currentMode == ChartMode.SESSION)
        {
            gpHour = sessionManager != null ? sessionManager.getCurrentGpHourRate() : 0;
        }

        int total_flips = wins + losses;
        double winRate = total_flips > 0 ? (wins * 100.0 / total_flips) : 0;

        final long fTotal = total;
        final double fGpHour = gpHour;
        final int fFlips = currentMode == ChartMode.SESSION ? flipTracker.getSessionFlipCount().get() : total_flips;
        final double fWinRate = winRate;

        totalProfitLabel.setText(formatGp(fTotal));
        totalProfitLabel.setForeground(fTotal >= 0 ? PROFIT_GREEN : LOSS_RED);
        gpHourLabel.setText(formatGp((long) fGpHour) + "/hr");
        gpHourLabel.setForeground(fGpHour >= 0 ? GOLD : LOSS_RED);
        flipCountLabel.setText(String.valueOf(fFlips));
        winRateLabel.setText(String.format("%.0f%%", fWinRate));
        winRateLabel.setForeground(fWinRate >= 50 ? PROFIT_GREEN : LOSS_RED);
    }

    // --------------------------------------------------------─
    //  CHART CANVAS (inner class)
    // --------------------------------------------------------─

    private class ChartCanvas extends JPanel
    {
        private List<FlipItem> flips = new ArrayList<>();
        private static final int PAD_L = 42, PAD_R = 10, PAD_T = 12, PAD_B = 28;

        public void setFlips(List<FlipItem> flips)
        {
            this.flips = new ArrayList<>(flips);
            // Sort by sell time
            this.flips.sort(Comparator.comparing(f -> f.getSellTime() != null ? f.getSellTime() : Instant.EPOCH));
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int cw = w - PAD_L - PAD_R;
            int ch = h - PAD_T - PAD_B;

            // Background
            g2.setColor(CHART_BG);
            g2.fillRect(0, 0, w, h);

            if (flips == null || flips.isEmpty())
            {
                drawEmpty(g2, w, h);
                g2.dispose();
                return;
            }

            // Build cumulative profit series
            List<long[]> points = buildCumulativeSeries();
            if (points.size() < 2) { drawEmpty(g2, w, h); g2.dispose(); return; }

            long minT = points.get(0)[0], maxT = points.get(points.size()-1)[0];
            long minP = Long.MAX_VALUE, maxP = Long.MIN_VALUE;
            for (long[] pt : points) { minP = Math.min(minP, pt[1]); maxP = Math.max(maxP, pt[1]); }
            if (minP == maxP) { minP -= 1000; maxP += 1000; }
            long padP = (maxP - minP) / 8;
            minP -= padP; maxP += padP;

            long rangeT = Math.max(1, maxT - minT);
            long rangeP = Math.max(1, maxP - minP);

            // Grid lines
            drawGrid(g2, w, h, cw, ch, minP, maxP, minT, maxT);

            // Zero line
            if (minP < 0 && maxP > 0)
            {
                int zy = PAD_T + (int)((maxP * ch) / rangeP);
                g2.setColor(AXIS_COLOR);
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4,3}, 0));
                g2.drawLine(PAD_L, zy, PAD_L + cw, zy);
                g2.setStroke(new BasicStroke(1f));
            }

            // Build pixel path
            int[] xs = new int[points.size()];
            int[] ys = new int[points.size()];
            for (int i = 0; i < points.size(); i++)
            {
                xs[i] = PAD_L + (int)((points.get(i)[0] - minT) * cw / rangeT);
                ys[i] = PAD_T + (int)((maxP - points.get(i)[1]) * ch / rangeP);
            }

            // Fill under curve
            Path2D fill = new Path2D.Double();
            fill.moveTo(xs[0], PAD_T + ch);
            fill.lineTo(xs[0], ys[0]);
            for (int i = 1; i < xs.length; i++) fill.lineTo(xs[i], ys[i]);
            fill.lineTo(xs[xs.length-1], PAD_T + ch);
            fill.closePath();

            long finalProfit = points.get(points.size()-1)[1];
            Color lineCol = finalProfit >= 0 ? PROFIT_GREEN : LOSS_RED;

            GradientPaint grad = new GradientPaint(0, PAD_T, withAlpha(lineCol, 60), 0, PAD_T + ch, withAlpha(lineCol, 5));
            g2.setPaint(grad);
            g2.fill(fill);

            // Line
            g2.setColor(lineCol);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 1; i < xs.length; i++)
                g2.drawLine(xs[i-1], ys[i-1], xs[i], ys[i]);

            // Flip dots (only if not too many)
            if (points.size() <= 60)
            {
                for (int i = 0; i < xs.length; i++)
                {
                    long profit = i == 0 ? points.get(0)[1] : points.get(i)[1] - points.get(i-1)[1];
                    Color dot = profit >= 0 ? PROFIT_GREEN : LOSS_RED;
                    g2.setColor(dot);
                    g2.fillOval(xs[i]-3, ys[i]-3, 6, 6);
                    g2.setColor(CHART_BG);
                    g2.drawOval(xs[i]-3, ys[i]-3, 6, 6);
                }
            }

            // Current value label at end
            String endLabel = formatGp(finalProfit);
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 10f));
            g2.setColor(lineCol);
            g2.drawString(endLabel, Math.max(PAD_L, xs[xs.length-1] - 20), Math.max(PAD_T + 12, ys[ys.length-1] - 5));

            // Y axis labels
            drawYAxis(g2, ch, minP, maxP);

            // X axis labels
            drawXAxis(g2, w, h, cw, ch, minT, maxT);

            g2.dispose();
        }

        private void drawEmpty(Graphics2D g2, int w, int h)
        {
            g2.setColor(TEXT_DIM);
            g2.setFont(g2.getFont().deriveFont(12f));
            String msg = "Complete flips to see your chart";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
        }

        private void drawGrid(Graphics2D g2, int w, int h, int cw, int ch, long minP, long maxP, long minT, long maxT)
        {
            g2.setColor(GRID_COLOR);
            g2.setStroke(new BasicStroke(1f));
            // Horizontal grid lines (5)
            for (int i = 0; i <= 4; i++)
            {
                int y = PAD_T + (i * ch / 4);
                g2.drawLine(PAD_L, y, PAD_L + cw, y);
            }
            // Vertical grid lines (4)
            for (int i = 0; i <= 3; i++)
            {
                int x = PAD_L + (i * cw / 3);
                g2.drawLine(x, PAD_T, x, PAD_T + ch);
            }
            // Axes
            g2.setColor(AXIS_COLOR);
            g2.drawLine(PAD_L, PAD_T, PAD_L, PAD_T + ch);
            g2.drawLine(PAD_L, PAD_T + ch, PAD_L + cw, PAD_T + ch);
        }

        private void drawYAxis(Graphics2D g2, int ch, long minP, long maxP)
        {
            g2.setFont(g2.getFont().deriveFont(8f));
            g2.setColor(TEXT_DIM);
            for (int i = 0; i <= 4; i++)
            {
                int y = PAD_T + (i * ch / 4);
                long val = maxP - (i * (maxP - minP) / 4);
                String lbl = formatGpShort(val);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(lbl, PAD_L - fm.stringWidth(lbl) - 3, y + 4);
            }
        }

        private void drawXAxis(Graphics2D g2, int w, int h, int cw, int ch, long minT, long maxT)
        {
            g2.setFont(g2.getFont().deriveFont(8f));
            g2.setColor(TEXT_DIM);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
            for (int i = 0; i <= 3; i++)
            {
                int x = PAD_L + (i * cw / 3);
                long t = minT + (i * (maxT - minT) / 3);
                String lbl = fmt.format(Instant.ofEpochMilli(t));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(lbl, x - fm.stringWidth(lbl)/2, PAD_T + ch + 14);
            }
        }

        private List<long[]> buildCumulativeSeries()
        {
            List<long[]> pts = new ArrayList<>();
            long cumulative = 0;
            for (FlipItem f : flips)
            {
                if (!f.isComplete() || f.getSellTime() == null) continue;
                cumulative += f.getProfit();
                pts.add(new long[]{f.getSellTime().toEpochMilli(), cumulative});
            }
            return pts;
        }

        private Color withAlpha(Color c, int alpha)
        {
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
        }
    }

    // --------------------------------------------------------─
    //  HELPERS
    // --------------------------------------------------------─

    private static String formatGp(long value)
    {
        return String.format("%,d", value);
    }

    private static String formatGpShort(long value)
    {
        if (Math.abs(value) >= 1_000_000L)
            return String.format("%.0fM", value / 1_000_000.0);
        if (Math.abs(value) >= 1_000L)
            return String.format("%.0fK", value / 1_000.0);
        return String.valueOf(value);
    }
}
