package com.fliphelper.ui;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.tracker.FlipTracker;
import com.fliphelper.tracker.SessionManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Main side panel for the Grand Flip Out plugin.
 * Contains tabs for Prices, Flips, History, and Profit Chart.
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
    private ProfitChartPanel profitChartPanel;

    private JTabbedPane tabbedPane;
    private JPanel pricesTab;
    private JPanel flipsTab;
    private JPanel historyTab;
    private JTextField searchField;
    private JPanel priceResultsPanel;
    private JPanel activeFlipsPanel;
    private JPanel historyPanel;

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
                           FlipTracker flipTracker)
    {
        this(config, priceService, flipTracker, null);
    }

    public GrandFlipOutPanel(GrandFlipOutConfig config, PriceService priceService,
                           FlipTracker flipTracker, SessionManager sessionManager)
    {
        super(false);
        this.config = config;
        this.priceService = priceService;
        this.flipTracker = flipTracker;
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
        historyTab = buildHistoryTab();

        tabbedPane.addTab("Prices", pricesTab);
        tabbedPane.addTab("Flips", flipsTab);
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

    private JPanel buildFlipsTab()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel headerRow = buildSectionHeader("Active Flips");
        panel.add(headerRow, BorderLayout.NORTH);

        activeFlipsPanel = new JPanel();
        activeFlipsPanel.setLayout(new BoxLayout(activeFlipsPanel, BoxLayout.Y_AXIS));
        activeFlipsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

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
                "Reset session data?\n\n"
                    + "This will clear all profit/loss tracking for this session.\n"
                    + "Your flip history will NOT be deleted.",
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

    public void updateAll()
    {
        SwingUtilities.invokeLater(() -> {
            updateHeader();
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
                                JLabel itemLabel = new JLabel(
                                    "  " + entry.getKey() + ": " + formatGp(entry.getValue()));
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
            expectedLabel.setForeground(expectedProfit >= 0
                ? new Color(0x00, 0xD2, 0x6A) : new Color(0xFF, 0x47, 0x57));
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
                return name.matches(".*(sword|scimitar|bow|crossbow|arrow|bolt|dart|javelin|"
                    + "knife|mace|warhammer|battleaxe|halberd|spear|hasta|staff|wand|"
                    + "blowpipe|whip|dagger|claws|godsword|rapier|tentacle|trident).*");

            case "Armor":
                return name.matches(".*(helm|helmet|platebody|chainbody|platelegs|plateskirt|"
                    + "shield|defender|boots|gloves|gauntlets|cape|ring|amulet|necklace|"
                    + "bracelet|coif|chaps).*");

            case "Consumables":
                return name.matches(".*(potion|brew|restore|prayer|food|shark|lobster|swordfish|"
                    + "tuna|monkfish|karambwan|anglerfish|manta|pie|cake|stew|combo|antifire|"
                    + "antivenom|super|ranging|sara|zamor|guthix).*");

            case "Resources":
                return name.matches(".*(ore|bar|log|plank|herb|seed|rune|essence|bone|ash|"
                    + "hide|leather|gem|diamond|ruby|emerald|sapphire|onyx|dragonstone|"
                    + "scale|feather|coal|clay|flax).*");

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

}
