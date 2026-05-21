package com.fliphelper.ui;

import com.fliphelper.debug.DebugManager;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Debug panel tab for Grand Flip Out sidebar.
 *
 * Shows live performance stats, API call metrics, memory usage,
 * recent events, and a copyable debug report for bug reporting.
 */
@Slf4j
public class DebugPanel extends JPanel
{
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private static final Color BG_COLOR = ColorScheme.DARK_GRAY_COLOR;
    private static final Color CARD_BG = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color LABEL_COLOR = ColorScheme.MEDIUM_GRAY_COLOR;
    private static final Color VALUE_COLOR = Color.WHITE;
    private static final Color BORDER_COLOR = new Color(58, 58, 58);
    private static final Color SURFACE_COLOR = new Color(22, 22, 32);
    private static final Color GREEN = new Color(0, 200, 83);
    private static final Color RED = new Color(255, 82, 82);
    private static final Color YELLOW = new Color(255, 193, 7);

    private final DebugManager debugManager;

    // Live stats labels
    private JLabel refreshAvgLabel;
    private JLabel refreshP95Label;
    private JLabel refreshCountLabel;
    private JLabel wikiApiLabel;
    private JLabel runeliteApiLabel;
    private JLabel backendApiLabel;
    private JLabel predictionApiLabel;
    private JLabel memHeapLabel;
    private JLabel memItemsLabel;
    private JPanel eventsPanel;

    public DebugPanel(DebugManager debugManager)
    {
        this.debugManager = debugManager;

        setLayout(new BorderLayout());
        setBackground(BG_COLOR);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG_COLOR);
        content.setBorder(new EmptyBorder(8, 8, 8, 8));

        // ── Performance card ──
        content.add(buildPerformanceCard());
        content.add(Box.createVerticalStrut(8));

        // ── API Stats card ──
        content.add(buildApiStatsCard());
        content.add(Box.createVerticalStrut(8));

        // ── Memory card ──
        content.add(buildMemoryCard());
        content.add(Box.createVerticalStrut(8));

        // ── Recent Events ──
        content.add(buildEventsCard());
        content.add(Box.createVerticalStrut(12));

        // ── Action buttons ──
        content.add(buildButtonBar());

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(BG_COLOR);
        add(scrollPane, BorderLayout.CENTER);
    }

    // ─── Card builders ──────────────────────────────────────

    private JPanel buildPerformanceCard()
    {
        JPanel card = createCard("Performance — refreshPrices()");

        refreshAvgLabel = addStatRow(card, "Avg cycle time:");
        refreshP95Label = addStatRow(card, "P95 cycle time:");
        refreshCountLabel = addStatRow(card, "Total refreshes:");

        return card;
    }

    private JPanel buildApiStatsCard()
    {
        JPanel card = createCard("API Calls");

        wikiApiLabel = addStatRow(card, "Wiki API:");
        runeliteApiLabel = addStatRow(card, "RuneLite API:");
        backendApiLabel = addStatRow(card, "Backend:");
        predictionApiLabel = addStatRow(card, "Prediction:");

        return card;
    }

    private JPanel buildMemoryCard()
    {
        JPanel card = createCard("Memory");

        memHeapLabel = addStatRow(card, "Heap usage:");
        memItemsLabel = addStatRow(card, "Tracked items:");

        return card;
    }

    private JPanel buildEventsCard()
    {
        JPanel card = createCard("Recent Events");

        eventsPanel = new JPanel();
        eventsPanel.setLayout(new BoxLayout(eventsPanel, BoxLayout.Y_AXIS));
        eventsPanel.setBackground(SURFACE_COLOR);
        eventsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            new EmptyBorder(6, 6, 6, 6)
        ));
        card.add(eventsPanel);

        return card;
    }

    private JPanel buildButtonBar()
    {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bar.setBackground(BG_COLOR);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JButton copyBtn = new JButton("Copy Debug Report");
        styleActionButton(copyBtn);
        copyBtn.setToolTipText("Copy full debug report to clipboard for bug reporting");
        copyBtn.addActionListener(e -> {
            String report = debugManager.exportDebugReport();
            StringSelection sel = new StringSelection(report);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            copyBtn.setText("\u2713 Copied!");
            Timer timer = new Timer(2000, evt -> copyBtn.setText("Copy Debug Report"));
            timer.setRepeats(false);
            timer.start();
        });
        bar.add(copyBtn);

        JButton clearBtn = new JButton("Clear");
        styleActionButton(clearBtn);
        clearBtn.setToolTipText("Clear all debug data");
        clearBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                this,
                "Clear all debug data?",
                "Confirm Clear",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (confirm == JOptionPane.YES_OPTION)
            {
                debugManager.clearAll();
                refresh();
            }
        });
        bar.add(clearBtn);

        return bar;
    }

    // ─── Refresh ────────────────────────────────────────────

    /**
     * Refresh all stats from the debug manager.
     * Called periodically by the panel's update cycle.
     */
    public void refresh()
    {
        SwingUtilities.invokeLater(() -> {
            updatePerformance();
            updateApiStats();
            updateMemory();
            updateEvents();
        });
    }

    private void updatePerformance()
    {
        DebugManager.OperationStats stats = debugManager.getOperationStats("refreshPrices");
        if (stats != null)
        {
            refreshAvgLabel.setText(String.format("%.1f ms", stats.getAvgMs()));
            refreshAvgLabel.setForeground(stats.getAvgMs() > 2000 ? RED : stats.getAvgMs() > 500 ? YELLOW : GREEN);

            refreshP95Label.setText(String.format("%d ms", stats.getP95Ms()));
            refreshP95Label.setForeground(stats.getP95Ms() > 3000 ? RED : stats.getP95Ms() > 1000 ? YELLOW : GREEN);

            refreshCountLabel.setText(String.valueOf(stats.getCallCount()));
            refreshCountLabel.setForeground(VALUE_COLOR);
        }
        else
        {
            refreshAvgLabel.setText("—");
            refreshP95Label.setText("—");
            refreshCountLabel.setText("0");
        }
    }

    private void updateApiStats()
    {
        Map<String, DebugManager.APICallStats> allStats = debugManager.getAPICallStats();

        updateApiLabel(wikiApiLabel, allStats.get("wiki/latest+5m+1h"));
        updateApiLabel(runeliteApiLabel, allStats.get("runelite/prices"));

        // Combine backend + p2p stats
        DebugManager.APICallStats backend = allStats.get("backend/contribute");
        DebugManager.APICallStats p2p = allStats.get("p2p/contribute");
        if (backend != null || p2p != null)
        {
            long total = (backend != null ? backend.getTotalCalls().get() : 0)
                + (p2p != null ? p2p.getTotalCalls().get() : 0);
            long failed = (backend != null ? backend.getFailedCalls().get() : 0)
                + (p2p != null ? p2p.getFailedCalls().get() : 0);
            backendApiLabel.setText(String.format("%d calls (%d failed)", total, failed));
            backendApiLabel.setForeground(failed > 0 ? YELLOW : GREEN);
        }
        else
        {
            backendApiLabel.setText("—");
            backendApiLabel.setForeground(LABEL_COLOR);
        }

        updateApiLabel(predictionApiLabel, allStats.get("backend/predict/stats"));
    }

    private void updateApiLabel(JLabel label, DebugManager.APICallStats stats)
    {
        if (stats != null)
        {
            long total = stats.getTotalCalls().get();
            long failed = stats.getFailedCalls().get();
            double avgMs = stats.getAverageDurationMs();
            label.setText(String.format("%d calls (%.0fms avg, %d err)", total, avgMs, failed));
            label.setForeground(failed > 0 ? YELLOW : GREEN);
        }
        else
        {
            label.setText("—");
            label.setForeground(LABEL_COLOR);
        }
    }

    private void updateMemory()
    {
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        double pct = (double) usedMb / maxMb * 100;

        memHeapLabel.setText(String.format("%d / %d MB (%.0f%%)", usedMb, maxMb, pct));
        memHeapLabel.setForeground(pct > 85 ? RED : pct > 70 ? YELLOW : VALUE_COLOR);

        // Item count from last memory snapshot
        memItemsLabel.setText("—");
        memItemsLabel.setForeground(VALUE_COLOR);
    }

    private void updateEvents()
    {
        eventsPanel.removeAll();

        // Show last 10 events
        List<DebugManager.EventLogEntry> events = getRecentEvents(10);
        if (events.isEmpty())
        {
            JLabel empty = new JLabel("No events recorded yet");
            empty.setForeground(LABEL_COLOR);
            empty.setFont(empty.getFont().deriveFont(11f));
            eventsPanel.add(empty);
        }
        else
        {
            for (DebugManager.EventLogEntry event : events)
            {
                String time = TIME_FMT.format(event.getTimestamp());
                String text = String.format("[%s] %s", time, event.getEventType());
                if (event.getItemName() != null)
                {
                    text += " — " + event.getItemName();
                }

                JLabel label = new JLabel(text);
                label.setFont(label.getFont().deriveFont(10.5f));
                label.setForeground(getEventColor(event.getEventType()));
                label.setAlignmentX(Component.LEFT_ALIGNMENT);
                label.setBorder(new EmptyBorder(1, 0, 1, 0));
                eventsPanel.add(label);
            }
        }

        eventsPanel.revalidate();
        eventsPanel.repaint();
    }

    private Color getEventColor(String eventType)
    {
        if (eventType == null) return LABEL_COLOR;
        if (eventType.contains("ERROR") || eventType.contains("FAIL")) return RED;
        if (eventType.contains("DUMP")) return YELLOW;
        if (eventType.contains("FLIP")) return GREEN;
        return VALUE_COLOR;
    }

    /**
     * Get recent events from the debug manager's event log.
     */
    private List<DebugManager.EventLogEntry> getRecentEvents(int count)
    {
        try
        {
            return debugManager.getRecentEvents(count);
        }
        catch (Exception e)
        {
            return java.util.Collections.emptyList();
        }
    }

    // ─── UI helpers ─────────────────────────────────────────

    private JPanel createCard(String title)
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            new EmptyBorder(8, 10, 8, 10)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        titleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setBorder(new EmptyBorder(0, 0, 6, 0));
        card.add(titleLabel);

        return card;
    }

    private JLabel addStatRow(JPanel card, String labelText)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(CARD_BG);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel left = new JLabel(labelText);
        left.setForeground(LABEL_COLOR);
        left.setFont(left.getFont().deriveFont(11f));
        row.add(left, BorderLayout.WEST);

        JLabel right = new JLabel("—");
        right.setForeground(VALUE_COLOR);
        right.setFont(right.getFont().deriveFont(11f));
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(right, BorderLayout.EAST);

        card.add(row);
        return right;
    }

    private void styleActionButton(JButton button)
    {
        button.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
        button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
}
