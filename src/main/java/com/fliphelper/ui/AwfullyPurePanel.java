package com.fliphelper.ui;

import com.fliphelper.AwfullyPureConfig;
import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.tracker.FlipSuggestionEngine;
import com.fliphelper.tracker.FlipTracker;
import com.fliphelper.tracker.SessionManager;
import com.fliphelper.tracker.SmartAdvisor;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;


public class AwfullyPurePanel extends PluginPanel
{
    private final AwfullyPureConfig config;
    private final PriceService priceService;
    private final FlipTracker flipTracker;
    private final FlipSuggestionEngine suggestionEngine;
    private final SmartAdvisor smartAdvisor;
    private ProfitChartPanel profitChartPanel;

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
    private JLabel gpPerHourLabel;
    private JLabel sessionDurationLabel;
    private JLabel winRateLabel;

    // Category filtering
    private String selectedCategory = "All";
    private JButton[] categoryButtons;

    public AwfullyPurePanel(AwfullyPureConfig config, PriceService priceService,
                           FlipTracker flipTracker, FlipSuggestionEngine suggestionEngine)
    {
        this(config, priceService, flipTracker, suggestionEngine, null, null);
    }

    public AwfullyPurePanel(AwfullyPureConfig config, PriceService priceService,
                           FlipTracker flipTracker, FlipSuggestionEngine suggestionEngine,
                           SmartAdvisor smartAdvisor)
    {
        this(config, priceService, flipTracker, suggestionEngine, smartAdvisor, null);
    }

    public AwfullyPurePanel(AwfullyPureConfig config, PriceService priceService,
                           FlipTracker flipTracker, FlipSuggestionEngine suggestionEngine,
                           SmartAdvisor smartAdvisor, SessionManager sessionManager)
    {
        super(false);
        this.config = config;
        this.priceService = priceService;
        this.flipTracker = flipTracker;
        this.suggestionEngine = suggestionEngine;
        this.smartAdvisor = smartAdvisor;
        this.profitChartPanel = new ProfitChartPanel(flipTracker, sessionManager);

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
        tabbedPane.addTab("Chart", profitChartPanel);

        add(tabbedPane, BorderLayout.CENTER);
    }

    
    public void addTab(String title, JPanel tabPanel)
    {
        if (tabbedPane != null)
        {
            tabbedPane.addTab(title, tabPanel);
        }
    }

    
    public void insertTab(String title, JPanel tabPanel, int index)
    {
        if (tabbedPane != null)
        {
            tabbedPane.insertTab(title, null, tabPanel, null, index);
        }
    }

    
    public void setSelectedTabIndex(int index)
    {
        if (tabbedPane != null && index >= 0 && index < tabbedPane.getTabCount())
        {
            tabbedPane.setSelectedIndex(index);
        }
    }

    private JPanel buildHeaderPanel()
    {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(new Color(0x16, 0x16, 0x25));
        header.setBorder(new EmptyBorder(10, 12, 10, 12));

        // Title row with brand
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JLabel titleLabel = new JLabel("Awfully Pure");
        titleLabel.setForeground(new Color(0xFF, 0xB8, 0x00)); // Gold
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleRow.add(titleLabel, BorderLayout.WEST);

        lastRefreshLabel = new JLabel("...");
        lastRefreshLabel.setForeground(new Color(0x60, 0x60, 0x80));
        lastRefreshLabel.setFont(lastRefreshLabel.getFont().deriveFont(10f));
        titleRow.add(lastRefreshLabel, BorderLayout.EAST);
        header.add(titleRow);
        header.add(Box.createVerticalStrut(8));

        // Session stats as mini-cards — Row 1: Profit | GP/hr | Flips
        JPanel statsRow = new JPanel(new GridLayout(1, 3, 6, 0));
        statsRow.setOpaque(false);

        // Profit card
        JPanel profitCard = buildStatMiniCard();
        JLabel profitTitle = new JLabel("SESSION PROFIT");
        profitTitle.setForeground(new Color(0x60, 0x60, 0x80));
        profitTitle.setFont(profitTitle.getFont().deriveFont(Font.BOLD, 8f));
        profitCard.add(profitTitle, BorderLayout.NORTH);
        sessionProfitLabel = new JLabel("0 gp");
        sessionProfitLabel.setForeground(new Color(0x00, 0xD2, 0x6A));
        sessionProfitLabel.setFont(sessionProfitLabel.getFont().deriveFont(Font.BOLD, 13f));
        profitCard.add(sessionProfitLabel, BorderLayout.CENTER);
        statsRow.add(profitCard);

        // GP/hr card — the key metric
        JPanel gphrCard = buildStatMiniCard();
        JLabel gphrTitle = new JLabel("GP/HOUR");
        gphrTitle.setForeground(new Color(0x60, 0x60, 0x80));
        gphrTitle.setFont(gphrTitle.getFont().deriveFont(Font.BOLD, 8f));
        gphrCard.add(gphrTitle, BorderLayout.NORTH);
        gpPerHourLabel = new JLabel("0 gp/hr");
        gpPerHourLabel.setForeground(new Color(0xFF, 0xB8, 0x00));
        gpPerHourLabel.setFont(gpPerHourLabel.getFont().deriveFont(Font.BOLD, 13f));
        gphrCard.add(gpPerHourLabel, BorderLayout.CENTER);
        statsRow.add(gphrCard);

        // Flip count card
        JPanel countCard = buildStatMiniCard();
        JLabel countTitle = new JLabel("FLIPS");
        countTitle.setForeground(new Color(0x60, 0x60, 0x80));
        countTitle.setFont(countTitle.getFont().deriveFont(Font.BOLD, 8f));
        countCard.add(countTitle, BorderLayout.NORTH);
        sessionFlipCountLabel = new JLabel("0");
        sessionFlipCountLabel.setForeground(new Color(0x3B, 0x82, 0xF6));
        sessionFlipCountLabel.setFont(sessionFlipCountLabel.getFont().deriveFont(Font.BOLD, 13f));
        countCard.add(sessionFlipCountLabel, BorderLayout.CENTER);
        statsRow.add(countCard);

        header.add(statsRow);
        header.add(Box.createVerticalStrut(4));

        // Row 2: Avg profit | Win Rate | Duration
        JPanel statsRow2 = new JPanel(new GridLayout(1, 3, 6, 0));
        statsRow2.setOpaque(false);

        // Average card
        JPanel avgCard = buildStatMiniCard();
        JLabel avgTitle = new JLabel("AVG PROFIT");
        avgTitle.setForeground(new Color(0x60, 0x60, 0x80));
        avgTitle.setFont(avgTitle.getFont().deriveFont(Font.BOLD, 8f));
        avgCard.add(avgTitle, BorderLayout.NORTH);
        avgProfitLabel = new JLabel("0 gp");
        avgProfitLabel.setForeground(Color.WHITE);
        avgProfitLabel.setFont(avgProfitLabel.getFont().deriveFont(Font.BOLD, 12f));
        avgCard.add(avgProfitLabel, BorderLayout.CENTER);
        statsRow2.add(avgCard);

        // Win rate card
        JPanel winCard = buildStatMiniCard();
        JLabel winTitle = new JLabel("WIN RATE");
        winTitle.setForeground(new Color(0x60, 0x60, 0x80));
        winTitle.setFont(winTitle.getFont().deriveFont(Font.BOLD, 8f));
        winCard.add(winTitle, BorderLayout.NORTH);
        winRateLabel = new JLabel("—");
        winRateLabel.setForeground(new Color(0x00, 0xD2, 0x6A));
        winRateLabel.setFont(winRateLabel.getFont().deriveFont(Font.BOLD, 12f));
        winCard.add(winRateLabel, BorderLayout.CENTER);
        statsRow2.add(winCard);

        // Session duration card
        JPanel durationCard = buildStatMiniCard();
        JLabel durTitle = new JLabel("SESSION");
        durTitle.setForeground(new Color(0x60, 0x60, 0x80));
        durTitle.setFont(durTitle.getFont().deriveFont(Font.BOLD, 8f));
        durationCard.add(durTitle, BorderLayout.NORTH);
        sessionDurationLabel = new JLabel("0m");
        sessionDurationLabel.setForeground(new Color(0x80, 0x80, 0xA0));
        sessionDurationLabel.setFont(sessionDurationLabel.getFont().deriveFont(Font.BOLD, 12f));
        durationCard.add(sessionDurationLabel, BorderLayout.CENTER);
        statsRow2.add(durationCard);

        header.add(statsRow2);

        // Bottom separator line
        header.add(Box.createVerticalStrut(6));
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(0x2A, 0x2A, 0x45));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        header.add(sep);

        return header;
    }

    private JPanel buildStatMiniCard()
    {
        JPanel card = new JPanel(new BorderLayout(0, 2));
        card.setBackground(new Color(0x1A, 0x1A, 0x2E));
        card.setBorder(new EmptyBorder(6, 8, 6, 8));
        return card;
    }

    private JPanel buildPricesTab()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Top panel: search and filters
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(new EmptyBorder(8, 8, 4, 8));
        topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Search bar
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        searchField = new JTextField();
        searchField.setToolTipText("Search items by name...");
        searchField.addActionListener(e -> searchItems());
        searchPanel.add(searchField, BorderLayout.CENTER);

        JButton searchBtn = new JButton("Search");
        searchBtn.addActionListener(e -> searchItems());
        searchPanel.add(searchBtn, BorderLayout.EAST);

        topPanel.add(searchPanel, BorderLayout.CENTER);

        // Category filter buttons
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        filterPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        String[] categories = {"All", "Weapons", "Armor", "Consumables", "Resources"};
        categoryButtons = new JButton[categories.length];
        for (int i = 0; i < categories.length; i++)
        {
            final String cat = categories[i];
            final int catIndex = i;
            JButton catBtn = new JButton(cat);
            catBtn.setFont(catBtn.getFont().deriveFont(10f));
            categoryButtons[catIndex] = catBtn;
            catBtn.addActionListener(e -> {
                selectedCategory = cat;
                searchField.setText("");
                updateCategoryButtonStyles();
                displayAllItemsInCategory();
            });
            filterPanel.add(catBtn);
        }
        // Highlight "All" by default
        updateCategoryButtonStyles();

        topPanel.add(filterPanel, BorderLayout.SOUTH);
        panel.add(topPanel, BorderLayout.NORTH);

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

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setBackground(new Color(0x16, 0x16, 0x25));
        headerRow.setBorder(new EmptyBorder(10, 12, 8, 12));
        JLabel label = new JLabel("Active Flips");
        label.setForeground(new Color(0xFF, 0xB8, 0x00));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        headerRow.add(label, BorderLayout.WEST);
        panel.add(headerRow, BorderLayout.NORTH);

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
        resetBtn.setToolTipText("Clear all session profit/loss data and start fresh");
        resetBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                AwfullyPurePanel.this,
                "Reset session data?\n\nThis will clear all profit/loss tracking for this session.\nYour flip history will NOT be deleted.",
                "Confirm Reset",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (confirm == JOptionPane.YES_OPTION)
            {
                flipTracker.resetSession();
                updateFlipsTab();
                updateHeader();
            }
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
        JPanel summaryPanel = new JPanel();
        summaryPanel.setLayout(new BoxLayout(summaryPanel, BoxLayout.Y_AXIS));
        summaryPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        summaryPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        try
        {
            Map<String, Long> topItems = flipTracker.getMostProfitableItems(3);
            if (!topItems.isEmpty())
            {
                JLabel topLabel = new JLabel("Top Items:");
                topLabel.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
                topLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                summaryPanel.add(topLabel);
                summaryPanel.add(Box.createVerticalStrut(4));
                for (Map.Entry<String, Long> entry : topItems.entrySet())
                {
                    JLabel itemLabel = new JLabel("  " + entry.getKey() + ": " + formatGp(entry.getValue()));
                    itemLabel.setForeground(entry.getValue() >= 0 ? Color.GREEN : Color.RED);
                    itemLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    summaryPanel.add(itemLabel);
                }
            }
            else
            {
                JLabel emptyLabel = new JLabel("Complete flips to see top items");
                emptyLabel.setForeground(Color.GRAY);
                emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                summaryPanel.add(emptyLabel);
            }
        }
        catch (Exception e)
        {
            JLabel errorLabel = new JLabel("Unable to load top items");
            errorLabel.setForeground(Color.GRAY);
            summaryPanel.add(errorLabel);
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
            if (profitChartPanel != null) profitChartPanel.update();
            // StatsPanel auto-refreshes via its own timer / button,
            // but also refresh on major events
            refreshExternalPanels();
        });
    }

    
    private void refreshExternalPanels()
    {
        // Walk tabs looking for StatsPanel instances
        for (int i = 0; i < tabbedPane.getTabCount(); i++)
        {
            Component comp = tabbedPane.getComponentAt(i);
            if (comp instanceof StatsPanel)
            {
                ((StatsPanel) comp).refresh();
            }
        }
    }

    public void updateHeader()
    {
        long profit = flipTracker.getSessionProfit().get();
        sessionProfitLabel.setText(formatGp(profit) + " gp");
        sessionProfitLabel.setForeground(profit >= 0 ? new Color(0x00, 0xD2, 0x6A) : new Color(0xFF, 0x47, 0x57));

        long flipCount = flipTracker.getSessionFlipCount().get();
        sessionFlipCountLabel.setText(String.valueOf(flipCount));

        long avgProfit = (long) flipTracker.getAverageProfitPerFlip();
        avgProfitLabel.setText(formatGp(avgProfit) + " gp");
        avgProfitLabel.setForeground(avgProfit >= 0 ? Color.WHITE : new Color(0xFF, 0x47, 0x57));

        // GP/hr — the metric flippers care about most
        double gphr = flipTracker.getGpPerHour();
        gpPerHourLabel.setText(formatGp((long) gphr) + "/hr");
        gpPerHourLabel.setForeground(gphr >= 0 ? new Color(0xFF, 0xB8, 0x00) : new Color(0xFF, 0x47, 0x57));

        // Win rate
        if (winRateLabel != null)
        {
            double winRate = flipTracker.getWinRate();
            if (flipCount > 0)
            {
                winRateLabel.setText(String.format("%.0f%%", winRate));
                winRateLabel.setForeground(winRate >= 60 ? new Color(0x00, 0xD2, 0x6A)
                    : winRate >= 40 ? new Color(0xFF, 0xB8, 0x00)
                    : new Color(0xFF, 0x47, 0x57));
            }
            else
            {
                winRateLabel.setText("—");
                winRateLabel.setForeground(new Color(0x60, 0x60, 0x80));
            }
        }

        // Session duration
        sessionDurationLabel.setText(flipTracker.getSessionDuration());

        if (priceService.getLastRefresh() != Instant.EPOCH)
        {
            long seconds = Duration.between(priceService.getLastRefresh(), Instant.now()).getSeconds();
            if (seconds < 60)
            {
                lastRefreshLabel.setText(seconds + "s ago");
                lastRefreshLabel.setForeground(new Color(0x00, 0xD2, 0x6A)); // Green = fresh
            }
            else
            {
                lastRefreshLabel.setText((seconds / 60) + "m ago");
                lastRefreshLabel.setForeground(new Color(0xFF, 0xB8, 0x00)); // Gold = aging
            }
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
            // Thin separator between cards
            if (i < suggestions.size() - 1)
            {
                JSeparator sep = new JSeparator();
                sep.setForeground(new Color(0x2A, 0x2A, 0x45));
                sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                suggestionsPanel.add(sep);
            }
        }

        if (suggestions.isEmpty())
        {
            JLabel noResults = new JLabel("No items match criteria. Adjust filter settings.");
            noResults.setForeground(new Color(0x60, 0x60, 0x80));
            noResults.setBorder(new EmptyBorder(20, 12, 20, 12));
            noResults.setHorizontalAlignment(SwingConstants.CENTER);
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
            java.util.concurrent.atomic.AtomicInteger idx = new java.util.concurrent.atomic.AtomicInteger(0);
            flipTracker.getActiveFlips().values().forEach(flip -> {
                JPanel card = buildFlipCard(flip);
                activeFlipsPanel.add(card);
                if (idx.incrementAndGet() < flipTracker.getActiveFlips().size())
                {
                    JSeparator sep = new JSeparator();
                    sep.setForeground(new Color(0x2A, 0x2A, 0x45));
                    sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                    activeFlipsPanel.add(sep);
                }
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

        if (!completed.isEmpty())
        {
            historyPanel.add(buildSessionSummaryPanel(completed));
            historyPanel.add(Box.createVerticalStrut(8));
        }

        for (int i = 0; i < displayCount; i++)
        {
            com.fliphelper.model.FlipItem flip = completed.get(i);
            JPanel card = buildHistoryCard(flip);
            historyPanel.add(card);
            if (i < displayCount - 1)
            {
                JSeparator sep = new JSeparator();
                sep.setForeground(new Color(0x2A, 0x2A, 0x45));
                sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                historyPanel.add(sep);
            }
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

    private JPanel buildSessionSummaryPanel(List<com.fliphelper.model.FlipItem> flips)
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(0x12, 0x12, 0x1E));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

        JLabel headerLabel = new JLabel("Session Summary");
        headerLabel.setForeground(new Color(0xFF, 0xB8, 0x00));
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 12f));
        panel.add(headerLabel);
        panel.add(Box.createVerticalStrut(6));

        long totalProfit = flips.stream().mapToLong(com.fliphelper.model.FlipItem::getProfit).sum();
        int totalFlips = flips.size();
        long wins = flips.stream().filter(f -> f.getProfit() >= 0).count();
        long losses = totalFlips - wins;
        long bestFlip = flips.stream().mapToLong(com.fliphelper.model.FlipItem::getProfit).max().orElse(0);
        long worstFlip = flips.stream().mapToLong(com.fliphelper.model.FlipItem::getProfit).min().orElse(0);
        double avgRoi = flips.stream().mapToDouble(com.fliphelper.model.FlipItem::getRoi).average().orElse(0);

        JPanel statsRow1 = new JPanel(new GridLayout(1, 3, 8, 0));
        statsRow1.setOpaque(false);
        statsRow1.add(buildSummaryStat("TOTAL PROFIT", formatGp(totalProfit), totalProfit >= 0 ? new Color(0x00, 0xD2, 0x6A) : new Color(0xFF, 0x47, 0x57)));
        statsRow1.add(buildSummaryStat("TOTAL FLIPS", String.valueOf(totalFlips), Color.WHITE));
        statsRow1.add(buildSummaryStat("WIN RATE", wins + "W / " + losses + "L", wins > losses ? new Color(0x00, 0xD2, 0x6A) : new Color(0xFF, 0x47, 0x57)));
        panel.add(statsRow1);
        panel.add(Box.createVerticalStrut(6));

        JPanel statsRow2 = new JPanel(new GridLayout(1, 3, 8, 0));
        statsRow2.setOpaque(false);
        statsRow2.add(buildSummaryStat("BEST FLIP", formatGp(bestFlip), new Color(0xFF, 0xB8, 0x00)));
        statsRow2.add(buildSummaryStat("WORST FLIP", formatGp(worstFlip), worstFlip >= 0 ? Color.LIGHT_GRAY : new Color(0xFF, 0x66, 0x66)));
        statsRow2.add(buildSummaryStat("AVG ROI", String.format("%.1f%%", avgRoi), avgRoi >= 5 ? new Color(0x00, 0xD2, 0x6A) : Color.YELLOW));
        panel.add(statsRow2);

        return panel;
    }

    private JPanel buildSummaryStat(String title, String value, Color valueColor)
    {
        JPanel card = new JPanel(new BorderLayout(0, 3));
        card.setBackground(new Color(0x1A, 0x1A, 0x2E));
        card.setBorder(new EmptyBorder(6, 8, 6, 8));
        JLabel t = new JLabel(title);
        t.setForeground(new Color(0x60, 0x60, 0x80));
        t.setFont(t.getFont().deriveFont(Font.BOLD, 8f));
        card.add(t, BorderLayout.NORTH);
        JLabel v = new JLabel(value);
        v.setForeground(valueColor);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 11f));
        card.add(v, BorderLayout.CENTER);
        return card;
    }

    // ==================== CARD BUILDERS ====================

    private JPanel buildSuggestionCard(FlipSuggestionEngine.FlipSuggestion s, int rank)
    {
        Color cardBg = new Color(0x1A, 0x1A, 0x2E);
        Color cardBgAlt = new Color(0x16, 0x16, 0x25);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(cardBg);
        card.setBorder(new EmptyBorder(8, 10, 8, 10));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        // -- Top banner for #1 --
        if (rank == 1)
        {
            JPanel bannerPanel = new JPanel(new BorderLayout());
            bannerPanel.setBackground(new Color(0x0A, 0x3D, 0x0A));
            bannerPanel.setBorder(new EmptyBorder(3, 8, 3, 8));
            bannerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            JLabel bannerLabel = new JLabel("\u2B50 TOP PICK");
            bannerLabel.setForeground(new Color(0xFF, 0xB8, 0x00));
            bannerLabel.setFont(bannerLabel.getFont().deriveFont(Font.BOLD, 10f));
            bannerPanel.add(bannerLabel, BorderLayout.WEST);
            card.add(bannerPanel);
            card.add(Box.createVerticalStrut(4));
        }

        // -- Row 1: Rank + Name (left) | Grade badge (right) --
        JPanel row1 = new JPanel(new BorderLayout());
        row1.setOpaque(false);
        JLabel rankLabel = new JLabel("#" + rank);
        rankLabel.setForeground(new Color(0x60, 0x60, 0x80));
        rankLabel.setFont(rankLabel.getFont().deriveFont(Font.BOLD, 11f));
        JLabel nameLabel = new JLabel(" " + s.getItemName());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 13f));
        nameLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        nameLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(s.getItemName());
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            }
        });
        JPanel nameGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        nameGroup.setOpaque(false);
        nameGroup.add(rankLabel);
        nameGroup.add(nameLabel);
        row1.add(nameGroup, BorderLayout.WEST);

        // Grade as a colored badge
        Color gradeColor = getQfGradeColor(s.getQfGrade());
        JLabel gradeLabel = new JLabel(" " + s.getQfGrade() + " " + s.getQfScore() + " ");
        gradeLabel.setForeground(Color.WHITE);
        gradeLabel.setOpaque(true);
        gradeLabel.setBackground(gradeColor.darker());
        gradeLabel.setFont(gradeLabel.getFont().deriveFont(Font.BOLD, 10f));
        row1.add(gradeLabel, BorderLayout.EAST);
        card.add(row1);
        card.add(Box.createVerticalStrut(6));

        // -- Row 2: JTI Progress Bar --
        int jtiScore = s.getQfScore();
        JPanel jtiRow = new JPanel(new BorderLayout(6, 0));
        jtiRow.setOpaque(false);
        jtiRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        JLabel jtiLabel = new JLabel("JTI");
        jtiLabel.setForeground(new Color(0x60, 0x60, 0x80));
        jtiLabel.setFont(jtiLabel.getFont().deriveFont(Font.BOLD, 9f));
        jtiRow.add(jtiLabel, BorderLayout.WEST);
        JProgressBar jtiBar = new JProgressBar(0, 100);
        jtiBar.setValue(Math.min(jtiScore, 100));
        jtiBar.setStringPainted(true);
        jtiBar.setString(String.valueOf(jtiScore));
        jtiBar.setFont(jtiBar.getFont().deriveFont(Font.BOLD, 9f));
        jtiBar.setBackground(new Color(0x0F, 0x0F, 0x17));
        jtiBar.setForeground(jtiScore >= 70 ? new Color(0x00, 0xD2, 0x6A) : jtiScore >= 40 ? new Color(0xFF, 0xB8, 0x00) : new Color(0xFF, 0x47, 0x57));
        jtiBar.setBorderPainted(false);
        jtiRow.add(jtiBar, BorderLayout.CENTER);
        card.add(jtiRow);
        card.add(Box.createVerticalStrut(6));

        // -- Row 3: Buy / Sell / Margin in mini-cards --
        JPanel priceRow = new JPanel(new GridLayout(1, 3, 4, 0));
        priceRow.setOpaque(false);

        JPanel buyCard = buildStatMiniCard();
        JLabel buyTitle = new JLabel("BUY");
        buyTitle.setForeground(new Color(0x60, 0x60, 0x80));
        buyTitle.setFont(buyTitle.getFont().deriveFont(Font.BOLD, 8f));
        buyCard.add(buyTitle, BorderLayout.NORTH);
        JLabel buyVal = new JLabel(formatGp(s.getBuyPrice()));
        buyVal.setForeground(new Color(0x00, 0xD2, 0x6A));
        buyVal.setFont(buyVal.getFont().deriveFont(Font.BOLD, 11f));
        buyCard.add(buyVal, BorderLayout.CENTER);
        priceRow.add(buyCard);

        JPanel sellCard = buildStatMiniCard();
        JLabel sellTitle = new JLabel("SELL");
        sellTitle.setForeground(new Color(0x60, 0x60, 0x80));
        sellTitle.setFont(sellTitle.getFont().deriveFont(Font.BOLD, 8f));
        sellCard.add(sellTitle, BorderLayout.NORTH);
        JLabel sellVal = new JLabel(formatGp(s.getSellPrice()));
        sellVal.setForeground(new Color(0xFF, 0x47, 0x57));
        sellVal.setFont(sellVal.getFont().deriveFont(Font.BOLD, 11f));
        sellCard.add(sellVal, BorderLayout.CENTER);
        priceRow.add(sellCard);

        JPanel marginCard = buildStatMiniCard();
        JLabel marginTitle = new JLabel("MARGIN");
        marginTitle.setForeground(new Color(0x60, 0x60, 0x80));
        marginTitle.setFont(marginTitle.getFont().deriveFont(Font.BOLD, 8f));
        marginCard.add(marginTitle, BorderLayout.NORTH);
        JLabel marginVal = new JLabel(formatGp(s.getMargin()));
        marginVal.setForeground(new Color(0xFF, 0xB8, 0x00));
        marginVal.setFont(marginVal.getFont().deriveFont(Font.BOLD, 11f));
        marginCard.add(marginVal, BorderLayout.CENTER);
        priceRow.add(marginCard);

        card.add(priceRow);
        card.add(Box.createVerticalStrut(4));

        // -- Row 4: Volume | Limit | ROI | Fill time --
        JPanel metaRow = new JPanel(new GridLayout(1, 4, 4, 0));
        metaRow.setOpaque(false);
        metaRow.add(createMetaLabel("Vol/1h", QuantityFormatter.formatNumber(s.getVolume1h())));
        metaRow.add(createMetaLabel("Limit", QuantityFormatter.formatNumber(s.getBuyLimit())));
        metaRow.add(createMetaLabel("ROI", String.format("%.1f%%", s.getRoi())));
        String timeline = estimateFlipTimeline(s.getVolume1h(), s.getBuyLimit());
        metaRow.add(createMetaLabel("Fill", timeline));
        card.add(metaRow);
        card.add(Box.createVerticalStrut(4));

        // -- Row 5: Profit/Limit + Capital --
        JPanel profitRow = new JPanel(new GridLayout(1, 2, 4, 0));
        profitRow.setOpaque(false);
        JLabel pLabel = new JLabel("Profit/Limit: " + formatGp(s.getProfitPerLimit()));
        pLabel.setForeground(new Color(0xFF, 0xB8, 0x00));
        pLabel.setFont(pLabel.getFont().deriveFont(Font.BOLD, 11f));
        profitRow.add(pLabel);
        JLabel capLabel = new JLabel("Capital: " + formatGp(s.getCapitalRequired()));
        capLabel.setForeground(Color.LIGHT_GRAY);
        profitRow.add(capLabel);
        card.add(profitRow);
        card.add(Box.createVerticalStrut(6));

        // -- Row 6: Action buttons --
        JPanel actionPanel = new JPanel(new GridLayout(1, 2, 6, 0));
        actionPanel.setOpaque(false);
        actionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JButton copyBuyBtn = new JButton("Copy Buy");
        styleActionButton(copyBuyBtn, new Color(0x0A, 0x5C, 0x2E));
        copyBuyBtn.addActionListener(e -> {
            java.awt.datatransfer.StringSelection sel =
                new java.awt.datatransfer.StringSelection(String.valueOf(s.getBuyPrice()));
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            copyBuyBtn.setText("\u2713 Copied");
            Timer timer = new Timer(1200, ev -> copyBuyBtn.setText("Copy Buy"));
            timer.setRepeats(false);
            timer.start();
        });
        actionPanel.add(copyBuyBtn);

        JButton copySellBtn = new JButton("Copy Sell");
        styleActionButton(copySellBtn, new Color(0x5C, 0x0A, 0x0A));
        copySellBtn.addActionListener(e -> {
            java.awt.datatransfer.StringSelection sel =
                new java.awt.datatransfer.StringSelection(String.valueOf(s.getSellPrice()));
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            copySellBtn.setText("\u2713 Copied");
            Timer timer = new Timer(1200, ev -> copySellBtn.setText("Copy Sell"));
            timer.setRepeats(false);
            timer.start();
        });
        actionPanel.add(copySellBtn);

        card.add(actionPanel);

        return card;
    }

    
    private void styleActionButton(JButton btn, Color bgColor)
    {
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 10f));
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    
    private JPanel createMetaLabel(String title, String value)
    {
        JPanel p = new JPanel(new BorderLayout(0, 1));
        p.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setForeground(new Color(0x60, 0x60, 0x80));
        t.setFont(t.getFont().deriveFont(Font.PLAIN, 8f));
        p.add(t, BorderLayout.NORTH);
        JLabel v = new JLabel(value);
        v.setForeground(Color.LIGHT_GRAY);
        v.setFont(v.getFont().deriveFont(Font.PLAIN, 10f));
        p.add(v, BorderLayout.CENTER);
        return p;
    }

    private Color getQfGradeColor(String grade)
    {
        if (grade == null) return Color.GRAY;
        switch (grade)
        {
            case "S": return new Color(0x00, 0xFF, 0xFF); // Cyan — legendary
            case "A": return new Color(0x00, 0xE6, 0x76); // Bright green
            case "B": return Color.GREEN;
            case "C": return Color.YELLOW;
            case "F": return Color.RED;
            default: return Color.GRAY;
        }
    }

    private JPanel buildFlipCard(com.fliphelper.model.FlipItem flip)
    {
        Color cardBg = new Color(0x1A, 0x1A, 0x2E);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(cardBg);
        card.setBorder(new EmptyBorder(8, 10, 8, 10));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));

        // -- Row 1: Name + State badge --
        JPanel row1 = new JPanel(new BorderLayout());
        row1.setOpaque(false);
        JLabel nameLabel = new JLabel(flip.getItemName());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        row1.add(nameLabel, BorderLayout.WEST);

        String stateName = flip.getState().getDisplayName();
        Color stateColor = stateName.contains("Buy") ? new Color(0x00, 0xD2, 0x6A)
            : stateName.contains("Sell") ? new Color(0xFF, 0xB8, 0x00)
            : new Color(0x3B, 0x82, 0xF6);
        JLabel stateLabel = new JLabel(" " + stateName + " ");
        stateLabel.setForeground(Color.WHITE);
        stateLabel.setOpaque(true);
        stateLabel.setBackground(stateColor.darker().darker());
        stateLabel.setFont(stateLabel.getFont().deriveFont(Font.BOLD, 9f));
        row1.add(stateLabel, BorderLayout.EAST);
        card.add(row1);
        card.add(Box.createVerticalStrut(4));

        // -- Row 2: Qty | Buy Price | Slot --
        JPanel row2 = new JPanel(new GridLayout(1, 3, 4, 0));
        row2.setOpaque(false);
        row2.add(createMetaLabel("Qty", QuantityFormatter.formatNumber(flip.getQuantity())));
        row2.add(createMetaLabel("Buy", formatGp(flip.getBuyPrice())));
        row2.add(createMetaLabel("Slot", String.valueOf(flip.getGeSlot())));
        card.add(row2);
        card.add(Box.createVerticalStrut(4));

        // -- Row 3: Expected profit --
        PriceAggregate agg = priceService.getPrice(flip.getItemId());
        if (agg != null)
        {
            long expectedSell = agg.getBestHighPrice();
            long expectedProfit = (expectedSell - flip.getBuyPrice()) * flip.getQuantity();
            long tax = Math.min((long) (expectedSell * 0.02), 5_000_000L) * flip.getQuantity();
            expectedProfit -= tax;

            JPanel profitRow = new JPanel(new BorderLayout());
            profitRow.setOpaque(false);
            JLabel expectedLabel = new JLabel("Expected Profit: " + formatGp(expectedProfit));
            expectedLabel.setForeground(expectedProfit >= 0 ? new Color(0x00, 0xD2, 0x6A) : new Color(0xFF, 0x47, 0x57));
            expectedLabel.setFont(expectedLabel.getFont().deriveFont(Font.BOLD, 11f));
            profitRow.add(expectedLabel, BorderLayout.WEST);
            card.add(profitRow);
        }
        else
        {
            JLabel naLabel = new JLabel("Expected Profit: N/A");
            naLabel.setForeground(new Color(0x60, 0x60, 0x80));
            card.add(naLabel);
        }

        return card;
    }

    private JPanel buildHistoryCard(com.fliphelper.model.FlipItem flip)
    {
        long profit = flip.getProfit();
        Color cardBg = profit >= 0 ? new Color(0x0A, 0x2A, 0x0A) : new Color(0x2A, 0x0A, 0x0A);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(cardBg);
        card.setBorder(new EmptyBorder(8, 10, 8, 10));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));

        // -- Row 1: Name + Profit (colored) + ROI --
        JPanel row1 = new JPanel(new BorderLayout());
        row1.setOpaque(false);
        JLabel nameLabel = new JLabel(flip.getItemName());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        row1.add(nameLabel, BorderLayout.WEST);

        // Profit + ROI badge
        String profitText = (profit >= 0 ? "+" : "") + formatGp(profit);
        String roiText = String.format("%.1f%%", flip.getRoi());
        JPanel profitGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        profitGroup.setOpaque(false);
        JLabel profitLabel = new JLabel(profitText);
        profitLabel.setForeground(profit >= 0 ? new Color(0x00, 0xE6, 0x76) : new Color(0xFF, 0x66, 0x66));
        profitLabel.setFont(profitLabel.getFont().deriveFont(Font.BOLD, 12f));
        profitGroup.add(profitLabel);
        JLabel roiLabel = new JLabel(" " + roiText + " ");
        roiLabel.setForeground(Color.WHITE);
        roiLabel.setOpaque(true);
        roiLabel.setBackground(profit >= 0 ? new Color(0x00, 0x5A, 0x2F) : new Color(0x5A, 0x00, 0x00));
        roiLabel.setFont(roiLabel.getFont().deriveFont(Font.BOLD, 9f));
        profitGroup.add(roiLabel);
        row1.add(profitGroup, BorderLayout.EAST);
        card.add(row1);
        card.add(Box.createVerticalStrut(4));

        // -- Row 2: Buy Price | Sell Price | Margin --
        JPanel row2 = new JPanel(new GridLayout(1, 3, 6, 0));
        row2.setOpaque(false);
        row2.add(buildHistoryStatCell("BUY", formatGp(flip.getBuyPrice()), new Color(0x00, 0xD2, 0x6A)));
        row2.add(buildHistoryStatCell("SELL", formatGp(flip.getSellPrice()), new Color(0xFF, 0x47, 0x57)));
        row2.add(buildHistoryStatCell("MARGIN", formatGp(flip.getSellPrice() - flip.getBuyPrice()), new Color(0xFF, 0xB8, 0x00)));
        card.add(row2);
        card.add(Box.createVerticalStrut(3));

        // -- Row 3: Qty | Tax | Duration | Timestamp --
        JPanel row3 = new JPanel(new GridLayout(1, 4, 6, 0));
        row3.setOpaque(false);
        row3.add(buildHistoryStatCell("QTY", QuantityFormatter.formatNumber(flip.getQuantity()) + "x", Color.LIGHT_GRAY));
        row3.add(buildHistoryStatCell("TAX", formatGp(flip.getTax()), new Color(0xFF, 0x8C, 0x00)));
        long duration = flip.getFlipDurationSeconds();
        row3.add(buildHistoryStatCell("DURATION", formatDuration(duration), Color.LIGHT_GRAY));
        String timeStr = flip.getSellTime() != null ? formatTimestamp(flip.getSellTime()) : "—";
        row3.add(buildHistoryStatCell("SOLD", timeStr, new Color(0x80, 0x80, 0xA0)));
        card.add(row3);

        return card;
    }

    private JPanel buildHistoryStatCell(String title, String value, Color valueColor)
    {
        JPanel cell = new JPanel(new BorderLayout(0, 2));
        cell.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setForeground(new Color(0x60, 0x60, 0x80));
        t.setFont(t.getFont().deriveFont(Font.BOLD, 7f));
        cell.add(t, BorderLayout.NORTH);
        JLabel v = new JLabel(value);
        v.setForeground(valueColor);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 10f));
        cell.add(v, BorderLayout.CENTER);
        return cell;
    }

    private String formatTimestamp(Instant instant)
    {
        if (instant == null) return "—";
        java.time.LocalTime time = instant.atZone(java.time.ZoneId.systemDefault()).toLocalTime();
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    
    private JLabel createCompactLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(0x80, 0x80, 0xA0));
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 9f));
        return label;
    }

    // ==================== CATEGORY FILTERING ====================

    
    private boolean matchesCategory(String itemName, String category)
    {
        if ("All".equals(category))
        {
            return true;
        }

        String name = itemName.toLowerCase();

        switch (category)
        {
            case "Weapons":
                return name.matches(".*(sword|scimitar|bow|crossbow|arrow|bolt|dart|javelin|knife|mace|warhammer|battleaxe|halberd|spear|hasta|staff|wand|blowpipe|whip|dagger|claws|godsword|rapier|tentacle|trident).*");

            case "Armor":
                return name.matches(".*(helm|helmet|platebody|chainbody|platelegs|plateskirt|shield|defender|boots|gloves|gauntlets|cape|ring|amulet|necklace|bracelet|coif|chaps).*");

            case "Consumables":
                return name.matches(".*(potion|brew|restore|prayer|food|shark|lobster|swordfish|tuna|monkfish|karambwan|anglerfish|manta|pie|cake|stew|combo|antifire|antivenom|super|ranging|sara|zamor|guthix).*");

            case "Resources":
                return name.matches(".*(ore|bar|log|plank|herb|seed|rune|essence|bone|ash|hide|leather|gem|diamond|ruby|emerald|sapphire|onyx|dragonstone|scale|feather|coal|clay|flax).*");

            default:
                return false;
        }
    }

    
    private void updateCategoryButtonStyles()
    {
        if (categoryButtons == null)
        {
            return;
        }

        String[] categories = {"All", "Weapons", "Armor", "Consumables", "Resources"};
        for (int i = 0; i < categoryButtons.length; i++)
        {
            JButton btn = categoryButtons[i];
            if (categories[i].equals(selectedCategory))
            {
                btn.setBackground(new Color(0xFF, 0xB8, 0x00));
                btn.setForeground(new Color(0x0F, 0x0F, 0x17));
                btn.setOpaque(true);
                btn.setBorderPainted(false);
                btn.setFont(btn.getFont().deriveFont(Font.BOLD, 10f));
            }
            else
            {
                btn.setBackground(new Color(0x1A, 0x1A, 0x2E));
                btn.setForeground(new Color(0x80, 0x80, 0xA0));
                btn.setOpaque(true);
                btn.setBorderPainted(false);
                btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 10f));
            }
        }
    }

    
    private void displayAllItemsInCategory()
    {
        if (!priceService.isReady())
        {
            priceResultsPanel.removeAll();
            JLabel loading = new JLabel("Price data is still loading. Please wait...");
            loading.setForeground(Color.YELLOW);
            loading.setBorder(new EmptyBorder(20, 8, 20, 8));
            priceResultsPanel.add(loading);
            priceResultsPanel.revalidate();
            priceResultsPanel.repaint();
            return;
        }

        priceResultsPanel.removeAll();

        // Get all items and filter by category
        List<PriceAggregate> allItems = new ArrayList<>();
        for (Integer itemId : priceService.getAllItemIds())
        {
            PriceAggregate agg = priceService.getPrice(itemId);
            if (agg != null && matchesCategory(agg.getItemName(), selectedCategory))
            {
                allItems.add(agg);
            }
        }

        // Sort by name
        allItems.sort(Comparator.comparing(PriceAggregate::getItemName));

        int displayCount = Math.min(allItems.size(), 50);
        for (int i = 0; i < displayCount; i++)
        {
            PriceAggregate agg = allItems.get(i);
            JPanel card = buildPriceCard(agg);
            priceResultsPanel.add(card);
            if (i < displayCount - 1)
            {
                JSeparator sep = new JSeparator();
                sep.setForeground(new Color(0x2A, 0x2A, 0x45));
                sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                priceResultsPanel.add(sep);
            }
        }

        if (allItems.isEmpty())
        {
            JLabel noResults = new JLabel("No items in '" + selectedCategory + "' category.");
            noResults.setForeground(Color.GRAY);
            noResults.setBorder(new EmptyBorder(20, 8, 20, 8));
            priceResultsPanel.add(noResults);
        }
        else
        {
            JLabel countLabel = new JLabel("Showing " + displayCount + " of " + allItems.size() + " results");
            countLabel.setForeground(Color.GRAY);
            countLabel.setBorder(new EmptyBorder(4, 8, 4, 8));
            priceResultsPanel.add(countLabel);
        }

        priceResultsPanel.revalidate();
        priceResultsPanel.repaint();
    }

    // ==================== SEARCH ====================

    private void searchItems()
    {
        String query = searchField.getText().trim();
        if (query.isEmpty())
        {
            return;
        }

        if (!priceService.isReady())
        {
            priceResultsPanel.removeAll();
            JLabel loading = new JLabel("Price data is still loading. Please wait...");
            loading.setForeground(Color.YELLOW);
            loading.setBorder(new EmptyBorder(20, 8, 20, 8));
            priceResultsPanel.add(loading);
            priceResultsPanel.revalidate();
            priceResultsPanel.repaint();
            return;
        }

        priceResultsPanel.removeAll();

        // Search by name and apply category filter
        List<PriceAggregate> results = priceService.searchByName(query);
        List<PriceAggregate> filteredResults = new ArrayList<>();
        for (PriceAggregate agg : results)
        {
            if (matchesCategory(agg.getItemName(), selectedCategory))
            {
                filteredResults.add(agg);
            }
        }

        int displayCount = Math.min(filteredResults.size(), 50);

        for (int i = 0; i < displayCount; i++)
        {
            PriceAggregate agg = filteredResults.get(i);
            JPanel card = buildPriceCard(agg);
            // Alternating row colors for better readability
            if (i % 2 == 0)
            {
                card.setBackground(new Color(50, 50, 50));
            }
            priceResultsPanel.add(card);
            priceResultsPanel.add(Box.createVerticalStrut(4));
        }

        if (filteredResults.isEmpty())
        {
            String filterNote = selectedCategory.equals("All") ? "" : " in '" + selectedCategory + "'";
            JLabel noResults = new JLabel("No items found for '" + query + "'" + filterNote);
            noResults.setForeground(Color.GRAY);
            noResults.setBorder(new EmptyBorder(20, 8, 20, 8));
            priceResultsPanel.add(noResults);
        }
        else
        {
            JLabel countLabel = new JLabel("Showing " + displayCount + " of " + filteredResults.size() + " results");
            countLabel.setForeground(Color.GRAY);
            countLabel.setBorder(new EmptyBorder(4, 8, 4, 8));
            priceResultsPanel.add(countLabel);
        }

        priceResultsPanel.revalidate();
        priceResultsPanel.repaint();
    }

    private JPanel buildPriceCard(PriceAggregate agg)
    {
        Color cardBg = new Color(0x1A, 0x1A, 0x2E);
        long margin = agg.getConsensusMargin();

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(cardBg);
        card.setBorder(new EmptyBorder(8, 10, 8, 10));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        // -- Row 1: Name + Margin % badge --
        JPanel row1 = new JPanel(new BorderLayout());
        row1.setOpaque(false);
        JLabel nameLabel = new JLabel(agg.getItemName());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        row1.add(nameLabel, BorderLayout.WEST);

        double marginPct = agg.getConsensusMarginPercent();
        Color marginColor = marginPct >= 5 ? new Color(0x00, 0xD2, 0x6A)
            : marginPct >= 2 ? new Color(0xFF, 0xB8, 0x00)
            : new Color(0xFF, 0x47, 0x57);
        JLabel marginBadge = new JLabel(" " + String.format("%.1f%%", marginPct) + " ");
        marginBadge.setForeground(Color.WHITE);
        marginBadge.setOpaque(true);
        marginBadge.setBackground(marginColor.darker());
        marginBadge.setFont(marginBadge.getFont().deriveFont(Font.BOLD, 10f));
        row1.add(marginBadge, BorderLayout.EAST);
        card.add(row1);
        card.add(Box.createVerticalStrut(4));

        // -- Row 2: Buy / Sell / Margin --
        JPanel row2 = new JPanel(new GridLayout(1, 3, 4, 0));
        row2.setOpaque(false);
        row2.add(createMetaLabel("Insta-Buy", formatGp(agg.getBestHighPrice())));
        row2.add(createMetaLabel("Insta-Sell", formatGp(agg.getBestLowPrice())));
        JPanel marginMeta = createMetaLabel("Margin", formatGp(margin));
        // Color the margin value
        Component[] mcs = marginMeta.getComponents();
        if (mcs.length > 1 && mcs[1] instanceof JLabel)
        {
            ((JLabel) mcs[1]).setForeground(margin > 0 ? new Color(0x00, 0xD2, 0x6A) : new Color(0xFF, 0x47, 0x57));
        }
        row2.add(marginMeta);
        card.add(row2);
        card.add(Box.createVerticalStrut(2));

        // -- Row 3: Vol | Limit | Profit/Limit --
        JPanel row3 = new JPanel(new GridLayout(1, 3, 4, 0));
        row3.setOpaque(false);
        row3.add(createMetaLabel("Vol/1h", QuantityFormatter.formatNumber(agg.getTotalVolume1h())));
        row3.add(createMetaLabel("Limit", QuantityFormatter.formatNumber(agg.getBuyLimit())));
        JPanel profitMeta = createMetaLabel("P/Limit", formatGp(agg.getProfitPerLimit()));
        Component[] pcs = profitMeta.getComponents();
        if (pcs.length > 1 && pcs[1] instanceof JLabel)
        {
            ((JLabel) pcs[1]).setForeground(new Color(0xFF, 0xB8, 0x00));
        }
        row3.add(profitMeta);
        card.add(row3);

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
        // Format with full comma-separated numbers per CLAUDE.md requirement
        // Never abbreviate GP values in user-facing output
        return String.format("%,d", amount);
    }

    private String formatDuration(long seconds)
    {
        if (seconds <= 0) return "N/A";
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }

    
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
