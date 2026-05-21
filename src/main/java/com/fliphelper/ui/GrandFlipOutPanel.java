package com.fliphelper.ui;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.api.MarketSignalClient;
import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.tracker.FlipSuggestionEngine;
import com.fliphelper.tracker.FlipTracker;
import com.fliphelper.tracker.SessionManager;
import com.fliphelper.tracker.SmartAdvisor;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Main side panel for the Grand Flip Out plugin.
 * Contains tabs for Prices, Flips, Suggestions, History, and Settings.
 */
public class GrandFlipOutPanel extends PluginPanel
{
    private static final Color BRAND_GOLD = new Color(0xFF, 0xA3, 0x1A); // PH Orange
    private static final Color PANEL_DEEP = new Color(0x00, 0x00, 0x00); // Pure black
    private static final Color PANEL_CARD = new Color(0x1A, 0x1A, 0x1A); // Dark gray card
    private static final Color TEXT_DIM = new Color(0x88, 0x88, 0x88);
    private static final Color PROFIT_GREEN = new Color(0x22, 0xC5, 0x5E);
    private static final Color LOSS_RED = new Color(0xEF, 0x44, 0x44);
    private static final Color PANEL_BORDER = new Color(0x33, 0x33, 0x33);
    private static final Color PANEL_BUTTON = new Color(0x11, 0x11, 0x11);
    private static final Color PANEL_BUTTON_ACTIVE = new Color(0x26, 0x26, 0x26);

    private final GrandFlipOutConfig config;
    private final PriceService priceService;
    private final FlipTracker flipTracker;
    private final FlipSuggestionEngine suggestionEngine;
    private final SmartAdvisor smartAdvisor;
    private final MarketSignalClient marketSignalClient;
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
    private JLabel gpPerHourLabel;
    private JLabel lastRefreshLabel;

    // Category filtering
    private String selectedCategory = "All";
    private JButton[] categoryButtons;
    private boolean tabChangeListenerAttached;

    public GrandFlipOutPanel(GrandFlipOutConfig config, PriceService priceService,
                           FlipTracker flipTracker, FlipSuggestionEngine suggestionEngine)
    {
        this(config, priceService, flipTracker, suggestionEngine, null, null, null);
    }

    public GrandFlipOutPanel(GrandFlipOutConfig config, PriceService priceService,
                           FlipTracker flipTracker, FlipSuggestionEngine suggestionEngine,
                           SmartAdvisor smartAdvisor)
    {
        this(config, priceService, flipTracker, suggestionEngine, smartAdvisor, null, null);
    }

    public GrandFlipOutPanel(GrandFlipOutConfig config, PriceService priceService,
                           FlipTracker flipTracker, FlipSuggestionEngine suggestionEngine,
                           SmartAdvisor smartAdvisor, SessionManager sessionManager)
    {
        this(config, priceService, flipTracker, suggestionEngine, smartAdvisor, sessionManager, null);
    }

    public GrandFlipOutPanel(GrandFlipOutConfig config, PriceService priceService,
                           FlipTracker flipTracker, FlipSuggestionEngine suggestionEngine,
                           SmartAdvisor smartAdvisor, SessionManager sessionManager,
                           MarketSignalClient marketSignalClient)
    {
        super(false);
        this.config = config;
        this.priceService = priceService;
        this.flipTracker = flipTracker;
        this.suggestionEngine = suggestionEngine;
        this.smartAdvisor = smartAdvisor;
        this.marketSignalClient = marketSignalClient;
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
        tabbedPane.setForeground(Color.LIGHT_GRAY);
        tabbedPane.setBorder(new EmptyBorder(0, 4, 0, 4));

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
        styleTabbedPane();

        add(tabbedPane, BorderLayout.CENTER);
    }

    /**
     * Add an external panel as a tab (used by ProfilePanel for the Profile tab).
     */
    public void addTab(String title, JPanel tabPanel)
    {
        if (tabbedPane != null)
        {
            tabbedPane.addTab(title, tabPanel);
            styleTabbedPane();
        }
    }

    private JPanel buildHeaderPanel()
    {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(PANEL_DEEP);
        header.setBorder(new EmptyBorder(10, 12, 10, 12));

        // Title row with brand
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JLabel titleLabel = new JLabel("Grand Flip Out");
        titleLabel.setForeground(BRAND_GOLD);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleRow.add(titleLabel, BorderLayout.WEST);

        lastRefreshLabel = new JLabel("...");
        lastRefreshLabel.setForeground(TEXT_DIM);
        lastRefreshLabel.setFont(lastRefreshLabel.getFont().deriveFont(10f));
        titleRow.add(lastRefreshLabel, BorderLayout.EAST);
        header.add(titleRow);
        header.add(Box.createVerticalStrut(8));

        // Session stats as mini-cards (2x2 grid for compact layout)
        JPanel statsRow = new JPanel(new GridLayout(1, 4, 4, 0));
        statsRow.setOpaque(false);

        // Profit card
        JPanel profitCard = buildStatMiniCard();
        JLabel profitTitle = new JLabel("PROFIT");
        profitTitle.setForeground(TEXT_DIM);
        profitTitle.setFont(profitTitle.getFont().deriveFont(Font.BOLD, 9f));
        profitCard.add(profitTitle, BorderLayout.NORTH);
        sessionProfitLabel = new JLabel("0 gp");
        sessionProfitLabel.setForeground(PROFIT_GREEN);
        sessionProfitLabel.setFont(sessionProfitLabel.getFont().deriveFont(Font.BOLD, 12f));
        profitCard.add(sessionProfitLabel, BorderLayout.CENTER);
        statsRow.add(profitCard);

        // Flip count card
        JPanel countCard = buildStatMiniCard();
        JLabel countTitle = new JLabel("FLIPS");
        countTitle.setForeground(TEXT_DIM);
        countTitle.setFont(countTitle.getFont().deriveFont(Font.BOLD, 9f));
        countCard.add(countTitle, BorderLayout.NORTH);
        sessionFlipCountLabel = new JLabel("0");
        sessionFlipCountLabel.setForeground(new Color(0x3B, 0x82, 0xF6));
        sessionFlipCountLabel.setFont(sessionFlipCountLabel.getFont().deriveFont(Font.BOLD, 12f));
        countCard.add(sessionFlipCountLabel, BorderLayout.CENTER);
        statsRow.add(countCard);

        // Average card
        JPanel avgCard = buildStatMiniCard();
        JLabel avgTitle = new JLabel("AVG");
        avgTitle.setForeground(TEXT_DIM);
        avgTitle.setFont(avgTitle.getFont().deriveFont(Font.BOLD, 9f));
        avgCard.add(avgTitle, BorderLayout.NORTH);
        avgProfitLabel = new JLabel("0 gp");
        avgProfitLabel.setForeground(Color.WHITE);
        avgProfitLabel.setFont(avgProfitLabel.getFont().deriveFont(Font.BOLD, 12f));
        avgCard.add(avgProfitLabel, BorderLayout.CENTER);
        statsRow.add(avgCard);

        // GP/hr card — key metric for flippers (matches Flipping Utilities feature)
        JPanel gpHrCard = buildStatMiniCard();
        JLabel gpHrTitle = new JLabel("GP/HR");
        gpHrTitle.setForeground(TEXT_DIM);
        gpHrTitle.setFont(gpHrTitle.getFont().deriveFont(Font.BOLD, 9f));
        gpHrCard.add(gpHrTitle, BorderLayout.NORTH);
        gpPerHourLabel = new JLabel("-");
        gpPerHourLabel.setForeground(BRAND_GOLD);
        gpPerHourLabel.setFont(gpPerHourLabel.getFont().deriveFont(Font.BOLD, 12f));
        gpHrCard.add(gpPerHourLabel, BorderLayout.CENTER);
        statsRow.add(gpHrCard);

        header.add(statsRow);

        // Bottom separator line
        header.add(Box.createVerticalStrut(6));
        JSeparator sep = new JSeparator();
        sep.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        header.add(sep);

        return header;
    }

    private JPanel buildStatMiniCard()
    {
        JPanel card = new JPanel(new BorderLayout(0, 2));
        card.setBackground(PANEL_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PANEL_BORDER),
            new EmptyBorder(6, 8, 6, 8)
        ));
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
        stylePrimaryButton(searchBtn);
        searchBtn.addActionListener(e -> searchItems());
        searchPanel.add(searchBtn, BorderLayout.EAST);

        JButton webChartBtn = new JButton("Web Chart");
        styleSecondaryButton(webChartBtn);
        webChartBtn.setToolTipText("Open live web dashboard chart for current search");
        webChartBtn.addActionListener(e -> openWebChartForSearch());
        searchPanel.add(webChartBtn, BorderLayout.WEST);

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
            catBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
        styleSearchField(searchField);

        topPanel.add(filterPanel, BorderLayout.SOUTH);
        panel.add(topPanel, BorderLayout.NORTH);

        // Results
        priceResultsPanel = new JPanel();
        priceResultsPanel.setLayout(new BoxLayout(priceResultsPanel, BoxLayout.Y_AXIS));
        priceResultsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(priceResultsPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        styleScrollPane(scrollPane);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void openWebChartForSearch()
    {
        try
        {
            String query = searchField != null ? searchField.getText().trim() : "";
            String base = "https://grandflipout.com/";
            String url = query.isEmpty()
                ? base
                : base + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

            if (!Desktop.isDesktopSupported())
            {
                JOptionPane.showMessageDialog(
                    this,
                    "Desktop browser is not supported in this environment.",
                    "Open Web Chart",
                    JOptionPane.INFORMATION_MESSAGE
                );
                return;
            }

            Desktop.getDesktop().browse(new URI(url));
        }
        catch (Exception ex)
        {
            JOptionPane.showMessageDialog(
                this,
                "Failed to open web chart: " + ex.getMessage(),
                "Open Web Chart",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private JPanel buildFlipsTab()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel headerRow = buildSectionHeader("Active Flips");
        panel.add(headerRow, BorderLayout.NORTH);

        activeFlipsPanel = new JPanel();
        activeFlipsPanel.setLayout(new BoxLayout(activeFlipsPanel, BoxLayout.Y_AXIS));
        activeFlipsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        activeFlipsPanel.add(buildHotkeyAssistCard());
        activeFlipsPanel.add(Box.createVerticalStrut(6));

        JScrollPane scrollPane = new JScrollPane(activeFlipsPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        styleScrollPane(scrollPane);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JButton resetBtn = new JButton("Reset Session");
        styleSecondaryButton(resetBtn);
        resetBtn.setToolTipText("Clear all session profit/loss data and start fresh");
        resetBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                GrandFlipOutPanel.this,
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

        JPanel headerPanel = buildSectionHeader("Flip Suggestions");

        JButton refreshBtn = new JButton("Refresh");
        stylePrimaryButton(refreshBtn);
        refreshBtn.addActionListener(e -> updateSuggestionsTab());
        headerPanel.add(refreshBtn, BorderLayout.EAST);

        panel.add(headerPanel, BorderLayout.NORTH);

        suggestionsPanel = new JPanel();
        suggestionsPanel.setLayout(new BoxLayout(suggestionsPanel, BoxLayout.Y_AXIS));
        suggestionsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(suggestionsPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        styleScrollPane(scrollPane);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }
    private JPanel buildHotkeyAssistCard()
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(0x15, 0x15, 0x1F));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PANEL_BORDER),
            new EmptyBorder(8, 10, 8, 10)
        ));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        JLabel title = new JLabel("Hotkey Assist (manual entry)");
        title.setForeground(BRAND_GOLD);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        card.add(title);
        card.add(Box.createVerticalStrut(4));

        card.add(createCompactLabel("Ctrl+Shift+P  Fill GE chatbox price"));
        card.add(createCompactLabel("Ctrl+Shift+B  Copy buy +/- margin assist"));
        card.add(createCompactLabel("Ctrl+Shift+S  Copy sell +/- margin assist"));
        card.add(createCompactLabel("Ctrl+Shift+G  Copy full slot assist block"));

        JLabel note = createCompactLabel("No auto-submit; you still confirm everything manually.");
        note.setForeground(TEXT_DIM);
        note.setBorder(new EmptyBorder(4, 0, 0, 0));
        card.add(note);
        return card;
    }
    private JPanel buildHistoryTab()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel headerPanel = buildSectionHeader("Flip History");

        JPanel historyActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        historyActions.setOpaque(false);

        JButton exportCsvBtn = new JButton("Export CSV");
        stylePrimaryButton(exportCsvBtn);
        exportCsvBtn.setToolTipText("Export completed flips to CSV for advanced trade-log analysis");
        exportCsvBtn.addActionListener(e -> exportHistoryCsv());
        historyActions.add(exportCsvBtn);

        headerPanel.add(historyActions, BorderLayout.EAST);
        panel.add(headerPanel, BorderLayout.NORTH);

        historyPanel = new JPanel();
        historyPanel.setLayout(new BoxLayout(historyPanel, BoxLayout.Y_AXIS));
        historyPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(historyPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        styleScrollPane(scrollPane);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Summary panel at bottom — populated dynamically by updateHistoryTab()
        JPanel summaryPanel = new JPanel();
        summaryPanel.setName("historySummary");
        summaryPanel.setLayout(new BoxLayout(summaryPanel, BoxLayout.Y_AXIS));
        summaryPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        summaryPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.add(summaryPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildSmartTab()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel headerPanel = buildSectionHeader("Smart Advisor");

        JButton refreshBtn = new JButton("Analyze");
        stylePrimaryButton(refreshBtn);
        refreshBtn.addActionListener(e -> updateSmartTab());
        headerPanel.add(refreshBtn, BorderLayout.EAST);

        panel.add(headerPanel, BorderLayout.NORTH);

        smartPanel = new JPanel();
        smartPanel.setLayout(new BoxLayout(smartPanel, BoxLayout.Y_AXIS));
        smartPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(smartPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        styleScrollPane(scrollPane);
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

            // ── Market Analysis status (if enabled & alive) ──
            if (marketSignalClient != null && marketSignalClient.isAlive())
            {
                JPanel nnPanel = new JPanel();
                nnPanel.setLayout(new BoxLayout(nnPanel, BoxLayout.Y_AXIS));
                nnPanel.setBackground(new Color(0x0D, 0x17, 0x22));
                nnPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0x58, 0xA6, 0xFF), 1),
                    new EmptyBorder(8, 10, 8, 10)
                ));
                nnPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

                // Header row
                JPanel nnHeaderRow = new JPanel(new BorderLayout());
                nnHeaderRow.setOpaque(false);
                JLabel nnTitle = new JLabel("Market Analysis");
                nnTitle.setForeground(new Color(0x58, 0xA6, 0xFF));
                nnTitle.setFont(nnTitle.getFont().deriveFont(Font.BOLD, 11f));
                nnHeaderRow.add(nnTitle, BorderLayout.WEST);

                JLabel nnAlive = new JLabel("ONLINE");
                nnAlive.setForeground(new Color(0x3F, 0xB9, 0x50));
                nnAlive.setFont(nnAlive.getFont().deriveFont(Font.BOLD, 9f));
                nnHeaderRow.add(nnAlive, BorderLayout.EAST);
                nnPanel.add(nnHeaderRow);

                // Status details
                JsonObject nnStatus = marketSignalClient.getStatus();
                if (nnStatus != null)
                {
                    JPanel nnStats = new JPanel(new GridLayout(1, 3, 4, 0));
                    nnStats.setOpaque(false);

                    String step = nnStatus.has("step") ? nnStatus.get("step").getAsString() : "?";
                    JLabel stepLabel = new JLabel("Step: " + step);
                    stepLabel.setForeground(Color.LIGHT_GRAY);
                    stepLabel.setFont(stepLabel.getFont().deriveFont(10f));
                    nnStats.add(stepLabel);

                    String loss = nnStatus.has("loss_ema") ? String.format("%.6f", nnStatus.get("loss_ema").getAsDouble()) : "?";
                    JLabel lossLabel = new JLabel("Loss: " + loss);
                    lossLabel.setForeground(Color.LIGHT_GRAY);
                    lossLabel.setFont(lossLabel.getFont().deriveFont(10f));
                    nnStats.add(lossLabel);

                    // Mood from bio system
                    JsonObject bio = marketSignalClient.getBioStatus();
                    String mood = "?";
                    if (bio != null && bio.has("emotions"))
                    {
                        JsonObject emo = bio.getAsJsonObject("emotions");
                        if (emo != null && emo.has("mood"))
                        {
                            mood = emo.get("mood").getAsString();
                        }
                    }
                    JLabel moodNnLabel = new JLabel("Mood: " + mood);
                    moodNnLabel.setForeground(new Color(0xD2, 0x99, 0x22));
                    moodNnLabel.setFont(moodNnLabel.getFont().deriveFont(10f));
                    nnStats.add(moodNnLabel);

                    nnPanel.add(nnStats);
                }

                // Insights summary
                JsonObject insights = marketSignalClient.getInsights();
                if (insights != null && insights.has("summary"))
                {
                    JLabel insightLabel = new JLabel(insights.get("summary").getAsString());
                    insightLabel.setForeground(new Color(0x8B, 0x94, 0x9E));
                    insightLabel.setFont(insightLabel.getFont().deriveFont(Font.ITALIC, 10f));
                    nnPanel.add(insightLabel);
                }

                smartPanel.add(nnPanel);
                smartPanel.add(Box.createVerticalStrut(6));
            }

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
        });
    }

    public void updateHeader()
    {
        long profit = flipTracker.getSessionProfit().get();
        sessionProfitLabel.setText(formatGp(profit) + " gp");
        sessionProfitLabel.setForeground(profit >= 0 ? PROFIT_GREEN : LOSS_RED);

        long flipCount = flipTracker.getSessionFlipCount().get();
        sessionFlipCountLabel.setText(String.valueOf(flipCount));

        long avgProfit = (long) flipTracker.getAverageProfitPerFlip();
        avgProfitLabel.setText(formatGp(avgProfit));
        avgProfitLabel.setForeground(avgProfit >= 0 ? Color.WHITE : new Color(0xFF, 0x47, 0x57));

        long gpPerHour = flipTracker.getGpPerHour();
        gpPerHourLabel.setText(gpPerHour > 0 ? formatGp(gpPerHour) : "-");
        gpPerHourLabel.setForeground(gpPerHour > 0 ? BRAND_GOLD : TEXT_DIM);

        if (priceService.getLastRefresh() != Instant.EPOCH)
        {
            long seconds = Duration.between(priceService.getLastRefresh(), Instant.now()).getSeconds();
            if (seconds < 60)
            {
                lastRefreshLabel.setText(seconds + "s ago");
                lastRefreshLabel.setForeground(PROFIT_GREEN);
            }
            else
            {
                lastRefreshLabel.setText((seconds / 60) + "m ago");
                lastRefreshLabel.setForeground(BRAND_GOLD);
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
        activeFlipsPanel.add(buildHotkeyAssistCard());
        activeFlipsPanel.add(Box.createVerticalStrut(6));

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

        // Refresh the summary panel (top items) — find it by name
        if (historyTab != null)
        {
            for (Component comp : historyTab.getComponents())
            {
                if (comp instanceof JPanel && "historySummary".equals(comp.getName()))
                {
                    JPanel summaryPanel = (JPanel) comp;
                    summaryPanel.removeAll();
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
                    catch (Exception ex)
                    {
                        JLabel errorLabel = new JLabel("Unable to load top items");
                        errorLabel.setForeground(Color.GRAY);
                        summaryPanel.add(errorLabel);
                    }
                    summaryPanel.revalidate();
                    summaryPanel.repaint();
                    break;
                }
            }
        }
    }

    // ==================== CARD BUILDERS ====================

    private JPanel buildSuggestionCard(FlipSuggestionEngine.FlipSuggestion s, int rank)
    {
        Color cardBg = new Color(0x1A, 0x1A, 0x2E);
        Color cardBgAlt = new Color(0x16, 0x16, 0x25);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(cardBg);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PANEL_BORDER),
            new EmptyBorder(8, 10, 8, 10)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        // ── Top banner for #1 ──
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

        // ── Row 1: Rank + Name (left) | Grade badge (right) ──
        JPanel row1 = new JPanel(new BorderLayout());
        row1.setOpaque(false);
        JLabel rankLabel = new JLabel("#" + rank);
        rankLabel.setForeground(new Color(0x60, 0x60, 0x80));
        rankLabel.setFont(rankLabel.getFont().deriveFont(Font.BOLD, 11f));
        JLabel nameLabel = new JLabel(" " + s.getItemName());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 13f));
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

        // ── Row 2: JTI Progress Bar ──
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

        // ── Row 3: Buy / Sell / Margin in mini-cards ──
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

        // ── Row 4: Volume | Limit | ROI | Fill time ──
        JPanel metaRow = new JPanel(new GridLayout(1, 4, 4, 0));
        metaRow.setOpaque(false);
        metaRow.add(createMetaLabel("Vol/1h", QuantityFormatter.formatNumber(s.getVolume1h())));
        metaRow.add(createMetaLabel("Limit", QuantityFormatter.formatNumber(s.getBuyLimit())));
        metaRow.add(createMetaLabel("ROI", String.format("%.1f%%", s.getRoi())));
        String timeline = estimateFlipTimeline(s.getVolume1h(), s.getBuyLimit());
        metaRow.add(createMetaLabel("Fill", timeline));
        card.add(metaRow);
        card.add(Box.createVerticalStrut(4));

        // ── Row 5: Profit/Limit + Capital ──
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

        // ── Row 6: Action buttons ──
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

    /**
     * Style a small action button with consistent look.
     */
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

    private void stylePrimaryButton(JButton btn)
    {
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 11f));
        btn.setBackground(BRAND_GOLD);
        btn.setForeground(new Color(0x10, 0x10, 0x18));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleSecondaryButton(JButton btn)
    {
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 11f));
        btn.setBackground(PANEL_BUTTON);
        btn.setForeground(Color.LIGHT_GRAY);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PANEL_BORDER),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleSearchField(JTextField field)
    {
        field.setBackground(new Color(0x12, 0x12, 0x20));
        field.setForeground(Color.WHITE);
        field.setCaretColor(BRAND_GOLD);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PANEL_BORDER),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
    }

    private void styleScrollPane(JScrollPane scrollPane)
    {
        scrollPane.setBorder(BorderFactory.createLineBorder(PANEL_BORDER));
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
    }

    private JPanel buildSectionHeader(String title)
    {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PANEL_DEEP);
        header.setBorder(new EmptyBorder(8, 10, 6, 10));
        JLabel label = new JLabel(title);
        label.setForeground(BRAND_GOLD);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        header.add(label, BorderLayout.WEST);
        return header;
    }

    private void styleTabbedPane()
    {
        tabbedPane.setUI(new BasicTabbedPaneUI()
        {
            @Override
            protected void installDefaults()
            {
                super.installDefaults();
                highlight = PANEL_BUTTON_ACTIVE;
                lightHighlight = PANEL_BUTTON_ACTIVE;
                shadow = PANEL_BORDER;
                darkShadow = PANEL_BORDER;
                focus = PANEL_BORDER;
            }

            @Override
            protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects,
                                               int tabIndex, Rectangle iconRect, Rectangle textRect,
                                               boolean isSelected)
            {
                // Intentionally disabled for cleaner tab visuals.
            }
        });

        for (int i = 0; i < tabbedPane.getTabCount(); i++)
        {
            boolean selected = i == tabbedPane.getSelectedIndex();
            tabbedPane.setBackgroundAt(i, selected ? PANEL_BUTTON_ACTIVE : PANEL_BUTTON);
            tabbedPane.setForegroundAt(i, selected ? BRAND_GOLD : Color.LIGHT_GRAY);
        }

        if (!tabChangeListenerAttached)
        {
            tabbedPane.addChangeListener(e -> {
                for (int i = 0; i < tabbedPane.getTabCount(); i++)
                {
                    boolean selected = i == tabbedPane.getSelectedIndex();
                    tabbedPane.setBackgroundAt(i, selected ? PANEL_BUTTON_ACTIVE : PANEL_BUTTON);
                    tabbedPane.setForegroundAt(i, selected ? BRAND_GOLD : Color.LIGHT_GRAY);
                }
            });
            tabChangeListenerAttached = true;
        }
    }

    /**
     * Build a compact meta-label with title on top and value below.
     */
    private JPanel createMetaLabel(String title, String value)
    {
        JPanel p = new JPanel(new BorderLayout(0, 1));
        p.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setForeground(TEXT_DIM);
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
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PANEL_BORDER),
            new EmptyBorder(8, 10, 8, 10)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));

        // ── Row 1: Name + State badge ──
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

        // ── Row 2: Qty | Buy Price | Slot ──
        JPanel row2 = new JPanel(new GridLayout(1, 3, 4, 0));
        row2.setOpaque(false);
        row2.add(createMetaLabel("Qty", QuantityFormatter.formatNumber(flip.getQuantity())));
        row2.add(createMetaLabel("Buy", formatGp(flip.getBuyPrice())));
        row2.add(createMetaLabel("Slot", String.valueOf(flip.getGeSlot())));
        card.add(row2);
        card.add(Box.createVerticalStrut(4));

        // ── Row 3: Expected profit ──
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
        Color cardBg = new Color(0x1A, 0x1A, 0x2E);
        long profit = flip.getProfit();

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(cardBg);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PANEL_BORDER),
            new EmptyBorder(6, 10, 6, 10)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

        // ── Row 1: Name + Profit (colored) ──
        JPanel row1 = new JPanel(new BorderLayout());
        row1.setOpaque(false);
        JLabel nameLabel = new JLabel(flip.getItemName());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 11f));
        row1.add(nameLabel, BorderLayout.WEST);

        String profitText = (profit >= 0 ? "+" : "") + formatGp(profit);
        JLabel profitLabel = new JLabel(profitText);
        profitLabel.setForeground(profit >= 0 ? new Color(0x00, 0xD2, 0x6A) : new Color(0xFF, 0x47, 0x57));
        profitLabel.setFont(profitLabel.getFont().deriveFont(Font.BOLD, 11f));
        row1.add(profitLabel, BorderLayout.EAST);
        card.add(row1);
        card.add(Box.createVerticalStrut(2));

        // ── Row 2: Qty | Buy | Sell | Duration ──
        JPanel row2 = new JPanel(new GridLayout(1, 4, 4, 0));
        row2.setOpaque(false);
        row2.add(createCompactLabel(QuantityFormatter.formatNumber(flip.getQuantity()) + "x"));
        row2.add(createCompactLabel("B:" + formatGp(flip.getBuyPrice())));
        row2.add(createCompactLabel("S:" + formatGp(flip.getSellPrice())));
        long duration = flip.getFlipDurationSeconds();
        row2.add(createCompactLabel("\u23F1 " + formatDuration(duration)));
        card.add(row2);
        card.add(Box.createVerticalStrut(2));

        // ── Row 3: ROI% | Tax | GP/hr ──
        JPanel row3 = new JPanel(new GridLayout(1, 3, 4, 0));
        row3.setOpaque(false);
        double roi = flip.getProfitPercent();
        JLabel roiLabel = createCompactLabel(String.format("ROI: %.1f%%", roi));
        roiLabel.setForeground(roi >= 0 ? new Color(0x00, 0xB0, 0x5A) : new Color(0xCC, 0x40, 0x40));
        row3.add(roiLabel);
        row3.add(createCompactLabel("Tax: " + formatGp(flip.getTax())));
        long gpPerHour = flip.getGpPerHour();
        JLabel gpHrLabel = createCompactLabel(gpPerHour > 0 ? formatGp(gpPerHour) + "/hr" : "-/hr");
        gpHrLabel.setForeground(gpPerHour > 0 ? new Color(0xFF, 0xB8, 0x00) : new Color(0x60, 0x60, 0x80));
        row3.add(gpHrLabel);
        card.add(row3);

        return card;
    }

    /**
     * Compact info label for tight rows.
     */
    private JLabel createCompactLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(0x80, 0x80, 0xA0));
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 9f));
        return label;
    }

    // ==================== CATEGORY FILTERING ====================

    /**
     * Determines if an item name belongs to a given category based on keyword matching.
     */
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

    /**
     * Update the visual styling of category buttons to highlight the selected one.
     */
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
                btn.setBackground(BRAND_GOLD);
                btn.setForeground(new Color(0x0F, 0x0F, 0x17));
                btn.setOpaque(true);
                btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BRAND_GOLD),
                    BorderFactory.createEmptyBorder(4, 8, 4, 8)
                ));
                btn.setFont(btn.getFont().deriveFont(Font.BOLD, 10f));
            }
            else
            {
                btn.setBackground(PANEL_CARD);
                btn.setForeground(new Color(0x80, 0x80, 0xA0));
                btn.setOpaque(true);
                btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(PANEL_BORDER),
                    BorderFactory.createEmptyBorder(4, 8, 4, 8)
                ));
                btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 10f));
            }
            btn.setFocusPainted(false);
        }
    }

    /**
     * Display all items (or items matching search) filtered by selected category.
     */
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

    /**
     * Open the prices tab and focus the search box for quick lookup hotkey flow.
     */
    public void openQuickLookup(String prefill)
    {
        if (tabbedPane != null && pricesTab != null)
        {
            tabbedPane.setSelectedComponent(pricesTab);
        }

        if (searchField != null)
        {
            if (prefill != null && !prefill.trim().isEmpty())
            {
                searchField.setText(prefill.trim());
                searchField.selectAll();
            }
            searchField.requestFocusInWindow();
            if (prefill != null && !prefill.trim().isEmpty())
            {
                searchItems();
            }
        }
    }

    private void exportHistoryCsv()
    {
        try
        {
            String path = flipTracker.exportTradeLogCsv();
            JOptionPane.showMessageDialog(
                this,
                "Trade log exported successfully:\n" + path,
                "Export Complete",
                JOptionPane.INFORMATION_MESSAGE
            );
        }
        catch (Exception ex)
        {
            JOptionPane.showMessageDialog(
                this,
                "Failed to export trade log:\n" + ex.getMessage(),
                "Export Failed",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private JPanel buildPriceCard(PriceAggregate agg)
    {
        Color cardBg = new Color(0x1A, 0x1A, 0x2E);
        long margin = agg.getConsensusMargin();

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(cardBg);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PANEL_BORDER),
            new EmptyBorder(8, 10, 8, 10)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        // ── Row 1: Name + Margin % badge ──
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

        // ── Row 2: Buy / Sell / Margin ──
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

        // ── Row 3: Vol | Limit | Profit/Limit ──
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
        if (amount >= 1_000_000_000)
        {
            return String.format("%.1fB", amount / 1_000_000_000.0);
        }
        if (amount >= 1_000_000)
        {
            return String.format("%.1fM", amount / 1_000_000.0);
        }
        if (amount >= 1_000)
        {
            return String.format("%.1fK", amount / 1_000.0);
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
