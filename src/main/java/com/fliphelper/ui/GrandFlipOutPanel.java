package com.fliphelper.ui;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.tracker.FlipSuggestionEngine;
import com.fliphelper.tracker.FlipTracker;
import com.fliphelper.tracker.SmartAdvisor;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Main side panel for the Grand Flip Out plugin.
 * Contains tabs for Prices, Flips, Suggestions, History, and Settings.
 */
public class GrandFlipOutPanel extends PluginPanel
{
    private final GrandFlipOutConfig config;
    private final PriceService priceService;
    private final FlipTracker flipTracker;
    private final FlipSuggestionEngine suggestionEngine;
    private final SmartAdvisor smartAdvisor;

    private JTabbedPane tabbedPane;
    private JPanel pricesTab;
    private JPanel flipsTab;
    private JPanel suggestionsTab;
    private JPanel historyTab;
    private JPanel smartTab;
    private JTextField searchField;
    private JPanel priceResultsPanel;
    private JPanel activeFlipsPanel;
    private JPanel suggestionsPanel;
    private JPanel historyPanel;
    private JPanel smartPanel;

    // Session stats labels
    private JLabel sessionProfitLabel;
    private JLabel sessionFlipCountLabel;
    private JLabel avgProfitLabel;
    private JLabel lastRefreshLabel;

    public GrandFlipOutPanel(GrandFlipOutConfig config, PriceService priceService,
                           FlipTracker flipTracker, FlipSuggestionEngine suggestionEngine)
    {
        this(config, priceService, flipTracker, suggestionEngine, null);
    }

    public GrandFlipOutPanel(GrandFlipOutConfig config, PriceService priceService,
                           FlipTracker flipTracker, FlipSuggestionEngine suggestionEngine,
                           SmartAdvisor smartAdvisor)
    {
        super(false);
        this.config = config;
        this.priceService = priceService;
        this.flipTracker = flipTracker;
        this.suggestionEngine = suggestionEngine;
        this.smartAdvisor = smartAdvisor;

        buildPanel();
    }

    private void buildPanel()
    {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header with session stats
        JPanel headerPanel = buildHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // Tabbed pane
        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(ColorScheme.DARK_GRAY_COLOR);

        pricesTab = buildPricesTab();
        flipsTab = buildFlipsTab();
        suggestionsTab = buildSuggestionsTab();
        historyTab = buildHistoryTab();
        smartTab = buildSmartTab();

        tabbedPane.addTab("Smart", smartTab);
        tabbedPane.addTab("Prices", pricesTab);
        tabbedPane.addTab("Flips", flipsTab);
        tabbedPane.addTab("Suggest", suggestionsTab);
        tabbedPane.addTab("History", historyTab);

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel buildHeaderPanel()
    {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(new EmptyBorder(8, 10, 8, 10));

        JLabel titleLabel = new JLabel("Grand Flip Out");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        header.add(titleLabel);
        header.add(Box.createVerticalStrut(6));

        JPanel statsGrid = new JPanel(new GridLayout(2, 2, 10, 4));
        statsGrid.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        sessionProfitLabel = new JLabel("Profit: 0gp");
        sessionProfitLabel.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
        statsGrid.add(sessionProfitLabel);

        sessionFlipCountLabel = new JLabel("Flips: 0");
        sessionFlipCountLabel.setForeground(Color.WHITE);
        statsGrid.add(sessionFlipCountLabel);

        avgProfitLabel = new JLabel("Avg: 0gp");
        avgProfitLabel.setForeground(Color.WHITE);
        statsGrid.add(avgProfitLabel);

        lastRefreshLabel = new JLabel("Last refresh: never");
        lastRefreshLabel.setForeground(Color.GRAY);
        statsGrid.add(lastRefreshLabel);

        header.add(statsGrid);

        return header;
    }

    private JPanel buildPricesTab()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Search bar
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        searchPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        searchField = new JTextField();
        searchField.setToolTipText("Search items by name...");
        searchField.addActionListener(e -> searchItems());
        searchPanel.add(searchField, BorderLayout.CENTER);

        JButton searchBtn = new JButton("Search");
        searchBtn.addActionListener(e -> searchItems());
        searchPanel.add(searchBtn, BorderLayout.EAST);

        panel.add(searchPanel, BorderLayout.NORTH);

        // Results
        priceResultsPanel = new JPanel();
        priceResultsPanel.setLayout(new BoxLayout(priceResultsPanel, BoxLayout.Y_AXIS));
        priceResultsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(priceResultsPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildFlipsTab()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel label = new JLabel("Active Flips");
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        label.setBorder(new EmptyBorder(8, 8, 4, 8));
        panel.add(label, BorderLayout.NORTH);

        activeFlipsPanel = new JPanel();
        activeFlipsPanel.setLayout(new BoxLayout(activeFlipsPanel, BoxLayout.Y_AXIS));
        activeFlipsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(activeFlipsPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JButton resetBtn = new JButton("Reset Session");
        resetBtn.addActionListener(e -> {
            flipTracker.resetSession();
            updateFlipsTab();
            updateHeader();
        });
        buttonPanel.add(resetBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildSuggestionsTab()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerPanel.setBorder(new EmptyBorder(8, 8, 4, 8));

        JLabel label = new JLabel("Flip Suggestions");
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(label, BorderLayout.WEST);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> updateSuggestionsTab());
        headerPanel.add(refreshBtn, BorderLayout.EAST);

        panel.add(headerPanel, BorderLayout.NORTH);

        suggestionsPanel = new JPanel();
        suggestionsPanel.setLayout(new BoxLayout(suggestionsPanel, BoxLayout.Y_AXIS));
        suggestionsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(suggestionsPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildHistoryTab()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerPanel.setBorder(new EmptyBorder(8, 8, 4, 8));

        JLabel label = new JLabel("Flip History");
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(label, BorderLayout.WEST);
        panel.add(headerPanel, BorderLayout.NORTH);

        historyPanel = new JPanel();
        historyPanel.setLayout(new BoxLayout(historyPanel, BoxLayout.Y_AXIS));
        historyPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(historyPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Summary panel at bottom
        JPanel summaryPanel = new JPanel(new GridLayout(3, 1));
        summaryPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        summaryPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        Map<String, Long> topItems = flipTracker.getMostProfitableItems(3);
        if (!topItems.isEmpty())
        {
            JLabel topLabel = new JLabel("Top Items:");
            topLabel.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
            summaryPanel.add(topLabel);
            for (Map.Entry<String, Long> entry : topItems.entrySet())
            {
                JLabel itemLabel = new JLabel(entry.getKey() + ": " + formatGp(entry.getValue()));
                itemLabel.setForeground(entry.getValue() >= 0 ? Color.GREEN : Color.RED);
                summaryPanel.add(itemLabel);
            }
        }
        panel.add(summaryPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildSmartTab()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerPanel.setBorder(new EmptyBorder(8, 8, 4, 8));

        JLabel label = new JLabel("Smart Advisor");
        label.setForeground(new Color(0xD4, 0xAF, 0x37)); // Gold
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(label, BorderLayout.WEST);

        JButton refreshBtn = new JButton("Analyze");
        refreshBtn.addActionListener(e -> updateSmartTab());
        headerPanel.add(refreshBtn, BorderLayout.EAST);

        panel.add(headerPanel, BorderLayout.NORTH);

        smartPanel = new JPanel();
        smartPanel.setLayout(new BoxLayout(smartPanel, BoxLayout.Y_AXIS));
        smartPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(smartPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    public void updateSmartTab()
    {
        smartPanel.removeAll();

        if (smartAdvisor == null)
        {
            JLabel noAdvisor = new JLabel("SmartAdvisor not available.");
            noAdvisor.setForeground(Color.GRAY);
            noAdvisor.setBorder(new EmptyBorder(20, 8, 20, 8));
            smartPanel.add(noAdvisor);
            smartPanel.revalidate();
            smartPanel.repaint();
            return;
        }

        if (!priceService.isReady())
        {
            JLabel loading = new JLabel("Loading price data...");
            loading.setForeground(Color.GRAY);
            loading.setBorder(new EmptyBorder(20, 8, 20, 8));
            smartPanel.add(loading);
            smartPanel.revalidate();
            smartPanel.repaint();
            return;
        }

        // Get market overview
        SmartAdvisor.MarketOverview overview = smartAdvisor.getMarketOverview();
        if (overview != null)
        {
            // Market mood banner
            JPanel moodPanel = new JPanel(new GridLayout(2, 2, 4, 2));
            moodPanel.setBackground(new Color(0x1A, 0x14, 0x10)); // Dark OSRS
            moodPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
            moodPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

            JLabel moodLabel = new JLabel("Market: " + overview.getMarketMood());
            moodLabel.setForeground(getMoodColor(overview.getMarketMood()));
            moodLabel.setFont(moodLabel.getFont().deriveFont(Font.BOLD));
            moodPanel.add(moodLabel);

            JLabel healthLabel = new JLabel("Health: " + overview.getMarketHealthScore() + "/100");
            healthLabel.setForeground(overview.getMarketHealthScore() >= 50 ? Color.GREEN : Color.RED);
            moodPanel.add(healthLabel);

            JLabel botLabel = new JLabel("Bots: " + overview.getBotActivity());
            botLabel.setForeground(Color.ORANGE);
            moodPanel.add(botLabel);

            JLabel countLabel = new JLabel("Analyzed: " + overview.getTotalItemsAnalyzed());
            countLabel.setForeground(Color.LIGHT_GRAY);
            moodPanel.add(countLabel);

            smartPanel.add(moodPanel);
            smartPanel.add(Box.createVerticalStrut(6));

            // Alerts
            if (overview.getAlerts() != null && !overview.getAlerts().isEmpty())
            {
                for (String alert : overview.getAlerts())
                {
                    JLabel alertLabel = new JLabel("  " + alert);
                    alertLabel.setForeground(Color.YELLOW);
                    alertLabel.setBorder(new EmptyBorder(2, 8, 2, 8));
                    alertLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
                    smartPanel.add(alertLabel);
                }
                smartPanel.add(Box.createVerticalStrut(6));
            }

            // Top Smart Picks header
            JLabel picksHeader = new JLabel("Top Smart Picks");
            picksHeader.setForeground(new Color(0xD4, 0xAF, 0x37));
            picksHeader.setFont(picksHeader.getFont().deriveFont(Font.BOLD, 13f));
            picksHeader.setBorder(new EmptyBorder(4, 8, 4, 8));
            smartPanel.add(picksHeader);

            // Top picks
            if (overview.getTopPicks() != null)
            {
                for (int i = 0; i < overview.getTopPicks().size(); i++)
                {
                    SmartAdvisor.SmartPick pick = overview.getTopPicks().get(i);
                    smartPanel.add(buildSmartPickCard(pick, i + 1));
                    smartPanel.add(Box.createVerticalStrut(3));
                }
            }

            // Sell warnings
            if (overview.getTopSells() != null && !overview.getTopSells().isEmpty())
            {
                smartPanel.add(Box.createVerticalStrut(8));
                JLabel sellHeader = new JLabel("Avoid / Sell");
                sellHeader.setForeground(Color.RED);
                sellHeader.setFont(sellHeader.getFont().deriveFont(Font.BOLD, 13f));
                sellHeader.setBorder(new EmptyBorder(4, 8, 4, 8));
                smartPanel.add(sellHeader);

                for (SmartAdvisor.SmartPick pick : overview.getTopSells())
                {
                    smartPanel.add(buildSmartPickCard(pick, 0));
                    smartPanel.add(Box.createVerticalStrut(3));
                }
            }
        }
        else
        {
            JLabel empty = new JLabel("Click 'Analyze' to run Smart Advisor.");
            empty.setForeground(Color.GRAY);
            empty.setBorder(new EmptyBorder(20, 8, 20, 8));
            smartPanel.add(empty);
        }

        smartPanel.revalidate();
        smartPanel.repaint();
    }

    private JPanel buildSmartPickCard(SmartAdvisor.SmartPick pick, int rank)
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(new EmptyBorder(6, 8, 6, 8));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        // Row 1: Name + Action + Score
        JPanel row1 = new JPanel(new BorderLayout());
        row1.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        String prefix = rank > 0 ? "#" + rank + " " : "";
        JLabel nameLabel = new JLabel(prefix + pick.getItemName());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        row1.add(nameLabel, BorderLayout.WEST);

        JLabel scoreLabel = new JLabel(pick.getAction().getLabel() + " (" + pick.getSmartScore() + ")");
        scoreLabel.setForeground(getActionColor(pick.getAction()));
        scoreLabel.setFont(scoreLabel.getFont().deriveFont(Font.BOLD));
        row1.add(scoreLabel, BorderLayout.EAST);
        card.add(row1);

        // Row 2: Price + Risk + Confidence
        JPanel row2 = new JPanel(new GridLayout(1, 3, 4, 0));
        row2.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row2.add(createInfoLabel("Price: " + formatGp(pick.getCurrentPrice())));

        JLabel riskLabel = new JLabel("Risk: " + pick.getRisk().name());
        riskLabel.setForeground(getRiskColor(pick.getRisk()));
        row2.add(riskLabel);
        row2.add(createInfoLabel("Conf: " + pick.getConfidence().name()));
        card.add(row2);

        // Row 3: JTI + RSI + Regime
        JPanel row3 = new JPanel(new GridLayout(1, 3, 4, 0));
        row3.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row3.add(createInfoLabel("JTI: " + pick.getJtiScore()));
        row3.add(createInfoLabel("RSI: " + pick.getRsi()));
        row3.add(createInfoLabel(pick.getRegime()));
        card.add(row3);

        // Row 4: Profit estimate + hold time
        JPanel row4 = new JPanel(new GridLayout(1, 2, 4, 0));
        row4.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel profitLabel = new JLabel("Est: " + formatGp(pick.getEstimatedProfitLow()) + " - " + formatGp(pick.getEstimatedProfitHigh()));
        profitLabel.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
        row4.add(profitLabel);
        row4.add(createInfoLabel("Hold: " + pick.getHoldTime()));
        card.add(row4);

        // Row 5: Reasons (first 2)
        if (pick.getReasons() != null && !pick.getReasons().isEmpty())
        {
            int reasonCount = Math.min(pick.getReasons().size(), 2);
            for (int i = 0; i < reasonCount; i++)
            {
                JLabel reasonLabel = new JLabel("+ " + pick.getReasons().get(i));
                reasonLabel.setForeground(new Color(0x90, 0xCA, 0x77)); // Light green
                reasonLabel.setFont(reasonLabel.getFont().deriveFont(10f));
                card.add(reasonLabel);
            }
        }

        // Row 6: Warnings (first 1)
        if (pick.getWarnings() != null && !pick.getWarnings().isEmpty())
        {
            JLabel warnLabel = new JLabel("! " + pick.getWarnings().get(0));
            warnLabel.setForeground(Color.YELLOW);
            warnLabel.setFont(warnLabel.getFont().deriveFont(10f));
            card.add(warnLabel);
        }

        return card;
    }

    private Color getActionColor(SmartAdvisor.SmartAction action)
    {
        switch (action)
        {
            case STRONG_BUY: return new Color(0x00, 0xE6, 0x76); // Bright green
            case BUY: return Color.GREEN;
            case HOLD: return Color.YELLOW;
            case SELL: return Color.ORANGE;
            case STRONG_SELL: return Color.RED;
            case AVOID: return Color.DARK_GRAY;
            default: return Color.WHITE;
        }
    }

    private Color getRiskColor(SmartAdvisor.RiskLevel risk)
    {
        switch (risk)
        {
            case LOW: return Color.GREEN;
            case MODERATE: return Color.YELLOW;
            case HIGH: return Color.ORANGE;
            case EXTREME: return Color.RED;
            default: return Color.WHITE;
        }
    }

    private Color getMoodColor(String mood)
    {
        if ("Bullish".equals(mood)) return Color.GREEN;
        if ("Bearish".equals(mood)) return Color.RED;
        if ("Volatile".equals(mood)) return Color.ORANGE;
        return Color.YELLOW; // Neutral
    }

    // ==================== UPDATE METHODS ====================

    public void updateAll()
    {
        SwingUtilities.invokeLater(() -> {
            updateHeader();
            updateSuggestionsTab();
            updateFlipsTab();
            updateHistoryTab();
        });
    }

    public void updateHeader()
    {
        long profit = flipTracker.getSessionProfit();
        sessionProfitLabel.setText("Profit: " + formatGp(profit));
        sessionProfitLabel.setForeground(profit >= 0 ? Color.GREEN : Color.RED);

        sessionFlipCountLabel.setText("Flips: " + flipTracker.getSessionFlipCount());
        avgProfitLabel.setText("Avg: " + formatGp((long) flipTracker.getAverageProfitPerFlip()));

        if (priceService.getLastRefresh() != Instant.EPOCH)
        {
            long seconds = Duration.between(priceService.getLastRefresh(), Instant.now()).getSeconds();
            lastRefreshLabel.setText("Updated " + seconds + "s ago");
        }
    }

    public void updateSuggestionsTab()
    {
        suggestionsPanel.removeAll();

        if (!priceService.isReady())
        {
            JLabel loading = new JLabel("Loading price data...");
            loading.setForeground(Color.GRAY);
            loading.setBorder(new EmptyBorder(20, 8, 20, 8));
            suggestionsPanel.add(loading);
            suggestionsPanel.revalidate();
            suggestionsPanel.repaint();
            return;
        }

        List<FlipSuggestionEngine.FlipSuggestion> suggestions = suggestionEngine.generateSuggestions();

        for (int i = 0; i < suggestions.size(); i++)
        {
            FlipSuggestionEngine.FlipSuggestion s = suggestions.get(i);
            JPanel card = buildSuggestionCard(s, i + 1);
            suggestionsPanel.add(card);
            suggestionsPanel.add(Box.createVerticalStrut(4));
        }

        if (suggestions.isEmpty())
        {
            JLabel noResults = new JLabel("No items match your criteria. Adjust filter settings.");
            noResults.setForeground(Color.GRAY);
            noResults.setBorder(new EmptyBorder(20, 8, 20, 8));
            suggestionsPanel.add(noResults);
        }

        suggestionsPanel.revalidate();
        suggestionsPanel.repaint();
    }

    public void updateFlipsTab()
    {
        activeFlipsPanel.removeAll();

        if (flipTracker.getActiveFlips().isEmpty())
        {
            JLabel empty = new JLabel("No active flips. Buy something in the GE!");
            empty.setForeground(Color.GRAY);
            empty.setBorder(new EmptyBorder(20, 8, 20, 8));
            activeFlipsPanel.add(empty);
        }
        else
        {
            flipTracker.getActiveFlips().values().forEach(flip -> {
                JPanel card = buildFlipCard(flip);
                activeFlipsPanel.add(card);
                activeFlipsPanel.add(Box.createVerticalStrut(4));
            });
        }

        activeFlipsPanel.revalidate();
        activeFlipsPanel.repaint();
    }

    public void updateHistoryTab()
    {
        historyPanel.removeAll();

        List<com.fliphelper.model.FlipItem> completed = flipTracker.getCompletedFlips();
        int displayCount = Math.min(completed.size(), 50);

        for (int i = 0; i < displayCount; i++)
        {
            com.fliphelper.model.FlipItem flip = completed.get(i);
            JPanel card = buildHistoryCard(flip);
            historyPanel.add(card);
            historyPanel.add(Box.createVerticalStrut(2));
        }

        if (completed.isEmpty())
        {
            JLabel empty = new JLabel("No completed flips yet.");
            empty.setForeground(Color.GRAY);
            empty.setBorder(new EmptyBorder(20, 8, 20, 8));
            historyPanel.add(empty);
        }

        historyPanel.revalidate();
        historyPanel.repaint();
    }

    // ==================== CARD BUILDERS ====================

    private JPanel buildSuggestionCard(FlipSuggestionEngine.FlipSuggestion s, int rank)
    {
        JPanel card = new JPanel(new GridLayout(5, 2, 4, 2));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(new EmptyBorder(6, 8, 6, 8));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        JLabel nameLabel = new JLabel("#" + rank + " " + s.getItemName());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        card.add(nameLabel);

        JLabel marginLabel = new JLabel("Margin: " + formatGp(s.getMargin()));
        marginLabel.setForeground(Color.GREEN);
        card.add(marginLabel);

        card.add(createInfoLabel("Buy: " + formatGp(s.getBuyPrice())));
        card.add(createInfoLabel("Sell: " + formatGp(s.getSellPrice())));

        card.add(createInfoLabel("Vol/1h: " + QuantityFormatter.formatNumber(s.getVolume1h())));
        card.add(createInfoLabel("Limit: " + QuantityFormatter.formatNumber(s.getBuyLimit())));

        JLabel profitLabel = new JLabel("Profit/Limit: " + formatGp(s.getProfitPerLimit()));
        profitLabel.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
        card.add(profitLabel);

        card.add(createInfoLabel("ROI: " + String.format("%.2f%%", s.getRoi())));

        // Expected profit timeline
        long volume = s.getVolume1h();
        int limit = s.getBuyLimit();
        String timeline = estimateFlipTimeline(volume, limit);
        card.add(createInfoLabel("Est. Time: " + timeline));

        JLabel capitalLabel = new JLabel("Capital: " + formatGp(s.getCapitalRequired()));
        capitalLabel.setForeground(Color.LIGHT_GRAY);
        card.add(capitalLabel);

        return card;
    }

    private JPanel buildFlipCard(com.fliphelper.model.FlipItem flip)
    {
        JPanel card = new JPanel(new GridLayout(3, 2, 4, 2));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(new EmptyBorder(6, 8, 6, 8));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

        JLabel nameLabel = new JLabel(flip.getItemName());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        card.add(nameLabel);

        JLabel stateLabel = new JLabel(flip.getState().getDisplayName());
        stateLabel.setForeground(Color.YELLOW);
        card.add(stateLabel);

        card.add(createInfoLabel("Qty: " + QuantityFormatter.formatNumber(flip.getQuantity())));
        card.add(createInfoLabel("Buy: " + formatGp(flip.getBuyPrice())));

        // Show expected sell price from market data
        PriceAggregate agg = priceService.getPrice(flip.getItemId());
        if (agg != null)
        {
            long expectedSell = agg.getBestHighPrice();
            long expectedProfit = (expectedSell - flip.getBuyPrice()) * flip.getQuantity();
            long tax = Math.min((long) (expectedSell * 0.02), 5_000_000L) * flip.getQuantity();
            expectedProfit -= tax;

            JLabel expectedLabel = new JLabel("Exp. Profit: " + formatGp(expectedProfit));
            expectedLabel.setForeground(expectedProfit >= 0 ? Color.GREEN : Color.RED);
            card.add(expectedLabel);
        }
        else
        {
            card.add(createInfoLabel("Exp. Profit: N/A"));
        }

        card.add(createInfoLabel("Slot: " + flip.getGeSlot()));

        return card;
    }

    private JPanel buildHistoryCard(com.fliphelper.model.FlipItem flip)
    {
        JPanel card = new JPanel(new GridLayout(2, 3, 4, 2));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(new EmptyBorder(4, 8, 4, 8));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

        JLabel nameLabel = new JLabel(flip.getItemName());
        nameLabel.setForeground(Color.WHITE);
        card.add(nameLabel);

        card.add(createInfoLabel(QuantityFormatter.formatNumber(flip.getQuantity()) + "x"));

        long profit = flip.getProfit();
        JLabel profitLabel = new JLabel(formatGp(profit));
        profitLabel.setForeground(profit >= 0 ? Color.GREEN : Color.RED);
        card.add(profitLabel);

        card.add(createInfoLabel("Buy: " + formatGp(flip.getBuyPrice())));
        card.add(createInfoLabel("Sell: " + formatGp(flip.getSellPrice())));

        long duration = flip.getFlipDurationSeconds();
        card.add(createInfoLabel("Time: " + formatDuration(duration)));

        return card;
    }

    // ==================== SEARCH ====================

    private void searchItems()
    {
        String query = searchField.getText().trim();
        if (query.isEmpty())
        {
            return;
        }

        priceResultsPanel.removeAll();

        List<PriceAggregate> results = priceService.searchByName(query);
        int displayCount = Math.min(results.size(), 50);

        for (int i = 0; i < displayCount; i++)
        {
            PriceAggregate agg = results.get(i);
            JPanel card = buildPriceCard(agg);
            priceResultsPanel.add(card);
            priceResultsPanel.add(Box.createVerticalStrut(4));
        }

        if (results.isEmpty())
        {
            JLabel noResults = new JLabel("No items found for '" + query + "'");
            noResults.setForeground(Color.GRAY);
            noResults.setBorder(new EmptyBorder(20, 8, 20, 8));
            priceResultsPanel.add(noResults);
        }
        else
        {
            JLabel countLabel = new JLabel("Showing " + displayCount + " of " + results.size() + " results");
            countLabel.setForeground(Color.GRAY);
            countLabel.setBorder(new EmptyBorder(4, 8, 4, 8));
            priceResultsPanel.add(countLabel);
        }

        priceResultsPanel.revalidate();
        priceResultsPanel.repaint();
    }

    private JPanel buildPriceCard(PriceAggregate agg)
    {
        JPanel card = new JPanel(new GridLayout(4, 2, 4, 2));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(new EmptyBorder(6, 8, 6, 8));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        JLabel nameLabel = new JLabel(agg.getItemName());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        card.add(nameLabel);

        long margin = agg.getConsensusMargin();
        JLabel marginLabel = new JLabel("Margin: " + formatGp(margin));
        marginLabel.setForeground(margin > 0 ? Color.GREEN : Color.RED);
        card.add(marginLabel);

        card.add(createInfoLabel("Insta-Buy: " + formatGp(agg.getBestHighPrice())));
        card.add(createInfoLabel("Insta-Sell: " + formatGp(agg.getBestLowPrice())));

        card.add(createInfoLabel("Vol/1h: " + QuantityFormatter.formatNumber(agg.getTotalVolume1h())));
        card.add(createInfoLabel("Limit: " + QuantityFormatter.formatNumber(agg.getBuyLimit())));

        card.add(createInfoLabel("Profit/Limit: " + formatGp(agg.getProfitPerLimit())));
        card.add(createInfoLabel("Margin %: " + String.format("%.2f%%", agg.getConsensusMarginPercent())));

        return card;
    }

    // ==================== HELPERS ====================

    private JLabel createInfoLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(Color.LIGHT_GRAY);
        return label;
    }

    private String formatGp(long amount)
    {
        if (amount >= 1_000_000_000)
        {
            return String.format("%.1fb gp", amount / 1_000_000_000.0);
        }
        if (amount >= 1_000_000)
        {
            return String.format("%.1fm gp", amount / 1_000_000.0);
        }
        if (amount >= 1_000)
        {
            return String.format("%.1fk gp", amount / 1_000.0);
        }
        return amount + " gp";
    }

    private String formatDuration(long seconds)
    {
        if (seconds <= 0) return "N/A";
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }

    /**
     * Estimate how long a flip cycle will take based on trade volume.
     */
    private String estimateFlipTimeline(long volume1h, int buyLimit)
    {
        if (volume1h <= 0 || buyLimit <= 0)
        {
            return "Unknown";
        }

        // Estimate based on volume: if volume/h >> limit, it's fast
        double hoursToFill = (double) buyLimit / Math.max(volume1h / 2.0, 1);

        if (hoursToFill < 0.1)
        {
            return "<5 min";
        }
        else if (hoursToFill < 0.5)
        {
            return "~" + (int)(hoursToFill * 60) + " min";
        }
        else if (hoursToFill < 4)
        {
            return String.format("~%.1f hrs", hoursToFill);
        }
        else
        {
            return "4h+ (limit cycle)";
        }
    }
}
