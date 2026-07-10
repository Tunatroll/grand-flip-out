/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.ui;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.tracker.FlipTracker;
import com.fliphelper.tracker.SessionManager;
import com.fliphelper.util.TradeLogEntry;
import com.fliphelper.util.TradeLogReader;
import com.fliphelper.util.WealthSnapshot;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Main side panel for the Grand Flip Out plugin.
 * Contains tabs for Prices, Flips, History, and Profit Chart.
 */
@Slf4j
public class GrandFlipOutPanel extends PluginPanel
{
    // GFO pastel brand via the GfoPalette SSOT (the granary wheat-gold theme was the
    // WRONG project's identity — owner 2026-07-10). Legacy local names kept this chunk
    // to hold the diff down; the rename sweep rides the chunk-C file split.
    private static final Color BRAND_GOLD = GfoPalette.ACCENT;
    private static final Color PANEL_DEEP = GfoPalette.PANEL;
    private static final Color PANEL_CARD = GfoPalette.CARD;
    private static final Color TEXT_DIM = GfoPalette.TEXT_MUTED;
    private static final Color PROFIT_GREEN = GfoPalette.UP;
    private static final Color LOSS_RED = GfoPalette.DOWN;
    private static final Color PANEL_BORDER = GfoPalette.BORDER;
    private static final Color PANEL_BUTTON = GfoPalette.CARD;
    private static final Color PANEL_BUTTON_ACTIVE = GfoPalette.ELEVATED;

    private static final NumberFormat GP_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private final GrandFlipOutConfig config;
    private final PriceService priceService;
    private final FlipTracker flipTracker;
    private final SessionManager sessionManager;
    private final File dataDir;

    private JTabbedPane tabbedPane;
    private JPanel pricesTab;
    private JPanel flipsTab;
    private JPanel historyTab;
    private JPanel intelPanel;
    private RecipePanel recipesTab;
    private JPanel intelContentPanel;
    private com.fliphelper.api.IntelligenceClient intelligenceClient;
    private com.fliphelper.api.EntitlementService entitlementService;
    private java.util.concurrent.ScheduledExecutorService executor;
    private JTextField searchField;
    private JPanel priceResultsPanel;
    private JPanel activeFlipsPanel;
    private JPanel historyPanel;
    private StatsPanel statsPanel;
    private JLabel upgradeLink;
    private boolean intelAutoRefreshStarted;
    private java.util.concurrent.ScheduledFuture<?> intelRefreshFuture;
    private com.fliphelper.util.WatchlistStore watchlist;
    private com.fliphelper.util.AlertStore alertStore;

    // Session stats labels
    private JLabel sessionProfitLabel;
    private JLabel sessionFlipCountLabel;
    private JLabel avgProfitLabel;
    private JLabel gpPerHourLabel;
    private JLabel wealthDeltaLabel;
    private JLabel lastRefreshLabel;
    private String historyFilter = "all";
    /** Optional callback to trigger a manual GE-history-tab import (wired by the plugin). */
    private Runnable geHistoryImportAction;

    // Category filtering
    private String selectedCategory = "All";
    // Suggestion sort (default: best flips by profit per GE limit)
    private String selectedSort = "margin";
    private JButton[] categoryButtons;
    private boolean tabChangeListenerAttached;

    // Intelligence signal cache (itemId -> advisor result, TTL 60s)
    private final java.util.concurrent.ConcurrentHashMap<Integer, CachedAdvisor> advisorCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static final class CachedAdvisor {
        final String action;
        final int strength;
        final long fetchedAt;
        CachedAdvisor(String action, int strength) {
            this.action = action;
            this.strength = strength;
            this.fetchedAt = System.currentTimeMillis();
        }
        boolean isStale() { return System.currentTimeMillis() - fetchedAt > 60_000; }
    }

    public GrandFlipOutPanel(GrandFlipOutConfig config, PriceService priceService,
                           FlipTracker flipTracker)
    {
        this(config, priceService, flipTracker, null, null);
    }

    public GrandFlipOutPanel(GrandFlipOutConfig config, PriceService priceService,
                           FlipTracker flipTracker, SessionManager sessionManager, File dataDir)
    {
        this(config, priceService, flipTracker, sessionManager, dataDir, null, null);
    }

    public GrandFlipOutPanel(GrandFlipOutConfig config, PriceService priceService,
                           FlipTracker flipTracker, SessionManager sessionManager, File dataDir,
                           com.fliphelper.api.IntelligenceClient intelligenceClient,
                           java.util.concurrent.ScheduledExecutorService executor)
    {
        this(config, priceService, flipTracker, sessionManager, dataDir, intelligenceClient, executor, null);
    }

    public GrandFlipOutPanel(GrandFlipOutConfig config, PriceService priceService,
                           FlipTracker flipTracker, SessionManager sessionManager, File dataDir,
                           com.fliphelper.api.IntelligenceClient intelligenceClient,
                           java.util.concurrent.ScheduledExecutorService executor,
                           com.fliphelper.api.EntitlementService entitlementService)
    {
        super(false);
        this.config = config;
        this.priceService = priceService;
        this.flipTracker = flipTracker;
        this.sessionManager = sessionManager;
        this.dataDir = dataDir;
        this.intelligenceClient = intelligenceClient;
        this.executor = executor;
        this.entitlementService = entitlementService;
        this.watchlist = new com.fliphelper.util.WatchlistStore(dataDir);
        this.alertStore = new com.fliphelper.util.AlertStore(dataDir);

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
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        pricesTab = buildPricesTab();
        flipsTab = buildFlipsTab();
        historyTab = buildHistoryTab();

        recipesTab = new RecipePanel(priceService);
        GuidePanel guideTab = new GuidePanel();

        intelPanel = buildIntelTab();
        // Chunk B (UX overhaul): the 5-tab bar — Advisor (inserted at index 0 by the
        // plugin = the default) · Prices (+Recipes card) · Flips (+History card) ·
        // Intel · Guide. Prices/Intel/Recipes had been BUILT but never added since
        // the Hub-submission slim (91dec5d) — allocated dark on every boot; History
        // was a whole tab for one concern (past flips), now a card inside Flips.
        tabbedPane.addTab("Prices", buildCardPairTab("prices",
            pricesTab, "Prices", recipesTab, "Recipes"));
        tabbedPane.addTab("Flips", buildCardPairTab("flips",
            flipsTab, "Active", historyTab, "History"));
        tabbedPane.addTab("Intel", intelPanel);
        tabbedPane.addTab("Guide", guideTab);
        styleTabbedPane();

        add(tabbedPane, BorderLayout.CENTER);
        add(buildLinksFooter(), BorderLayout.SOUTH);
    }

    /** True when the user has an unlocked Grand Flip Out account (all items + premium). */
    private boolean isUnlocked()
    {
        return entitlementService != null && entitlementService.isUnlocked();
    }

    /**
     * Footer row of external links: site, Discord, and an account-aware Upgrade/Account CTA.
     * Opens in the system browser via {@link #openDashboardUrl} (no in-client payment).
     */
    private JPanel buildLinksFooter()
    {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        footer.setBackground(PANEL_DEEP);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, PANEL_BORDER));

        footer.add(buildFooterLink("Website", "https://grandflipout.com/?ref=plugin"));
        footer.add(buildFooterDot());
        footer.add(buildFooterLink("Discord", "https://discord.gg/yUhfTJEuMr"));
        footer.add(buildFooterDot());

        // Upgrade CTA changes label once the account is unlocked. "Upgrade" only opens the
        // web — payment/account management is server-side per Jagex rules (no in-client pay).
        // URL resolved at CLICK time: the entitlement refresh only re-labels this link
        // (setText below), so a construction-time URL could say "Account" but open /upgrade.
        upgradeLink = buildFooterLink(isUnlocked() ? "Account" : "Upgrade",
            () -> isUnlocked() ? "https://grandflipout.com/account?ref=plugin" : "https://grandflipout.com/upgrade?ref=plugin");
        upgradeLink.setForeground(BRAND_GOLD);
        footer.add(upgradeLink);

        return footer;
    }

    private JLabel buildFooterDot()
    {
        JLabel dot = new JLabel("·");
        dot.setForeground(TEXT_DIM);
        return dot;
    }

    private JLabel buildFooterLink(String text, String url)
    {
        return buildFooterLink(text, () -> url);
    }

    private JLabel buildFooterLink(String text, java.util.function.Supplier<String> url)
    {
        JLabel link = new JLabel(text);
        link.setForeground(TEXT_DIM);
        link.setFont(link.getFont().deriveFont(11f));
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                openDashboardUrl(url.get());
            }
        });
        return link;
    }

    /**
     * "Create free account -> unlock members flips" call-to-action shown to anonymous users
     * above the F2P-filtered suggestion list. Opens the web signup (no in-client payment).
     */
    private JPanel buildUnlockCta(int membersHidden)
    {
        JPanel cta = new JPanel(new BorderLayout(0, 6));
        cta.setBackground(PANEL_CARD);
        cta.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BRAND_GOLD),
            new EmptyBorder(8, 10, 8, 10)));
        cta.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));

        JLabel msg = new JLabel("<html><div style='width:170px'><b>" + membersHidden
            + " members items hidden.</b><br>Create a free account to unlock all "
            + "members flips and premium features.</div></html>");
        msg.setForeground(TEXT_DIM);
        msg.setFont(msg.getFont().deriveFont(11f));
        cta.add(msg, BorderLayout.CENTER);

        cta.add(buildUnlockButton("Create free account"), BorderLayout.SOUTH);
        return cta;
    }

    /** Shared gold "create account" button that opens the web signup (no in-client payment). */
    private JButton buildUnlockButton(String label)
    {
        JButton btn = new JButton(label);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 11f));
        btn.setForeground(PANEL_DEEP);
        btn.setBackground(BRAND_GOLD);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(6, 10, 6, 10));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.addActionListener(e -> openDashboardUrl("https://grandflipout.com/signup?ref=plugin"));
        return btn;
    }

    /**
     * Re-render account-gated surfaces after the entitlement is (re)resolved. Must run on the EDT.
     */
    public void onEntitlementChanged()
    {
        if (upgradeLink != null)
        {
            upgradeLink.setText(isUnlocked() ? "Account" : "Upgrade");
        }
        // Re-render the gated browse list and the premium Intel tab.
        displayAllItemsInCategory();
        rebuildIntelGate();
    }

    /**
     * The shared per-character price-alert targets. The plugin checks these on each price
     * refresh; the panel lets the user set them. Same instance so both see the same data.
     */
    public com.fliphelper.util.AlertStore getAlertStore()
    {
        return alertStore;
    }

    /** The shared per-character starred-item list, used for the GE-search quick-look. */
    public com.fliphelper.util.WatchlistStore getWatchlist()
    {
        return watchlist;
    }

    /**
     * Add an external panel as a tab (used by ProfilePanel for the Profile tab).
     */
    public void addTab(String title, JPanel tabPanel)
    {
        SwingUtilities.invokeLater(() -> {
            tabbedPane.addTab(title, tabPanel);
        });
    }

    public void insertTab(String title, JPanel tabPanel, int index)
    {
        SwingUtilities.invokeLater(() -> {
            tabbedPane.insertTab(title, null, tabPanel, null, index);
            tabbedPane.setSelectedIndex(index);
        });
    }

    /**
     * Wire the "Import GE history" button to a plugin-supplied action that reads
     * the in-game GE History tab and back-fills any new trades.
     */
    public void setGeHistoryImportAction(Runnable action)
    {
        this.geHistoryImportAction = action;
    }

    /**
     * Chunk B: two related surfaces share ONE tab — a segmented toggle swaps
     * CardLayout cards (Prices|Recipes, Active|History). The existing panels are
     * reused untouched: refresh paths keep mutating the same component instances
     * regardless of parent, so behavior is identical card-for-card.
     */
    private JPanel buildCardPairTab(String key, JPanel first, String firstLabel,
        JPanel second, String secondLabel)
    {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);

        CardLayout cards = new CardLayout();
        JPanel cardHost = new JPanel(cards);
        cardHost.setOpaque(false);
        cardHost.add(first, key + ".first");
        cardHost.add(second, key + ".second");

        JToggleButton firstBtn = new JToggleButton(firstLabel, true);
        JToggleButton secondBtn = new JToggleButton(secondLabel);
        ButtonGroup group = new ButtonGroup();
        group.add(firstBtn);
        group.add(secondBtn);

        JPanel toggleRow = new JPanel(new GridLayout(1, 2, 4, 0));
        toggleRow.setOpaque(false);
        toggleRow.setBorder(new EmptyBorder(6, 8, 2, 8));
        for (JToggleButton b : new JToggleButton[] { firstBtn, secondBtn })
        {
            styleSecondaryButton(b);
            // kill the LaF's selected fill (painted light-blue over our dark scheme —
            // caught on the screenshot harness render) so OUR colors are what paints
            b.setContentAreaFilled(false);
            b.setOpaque(true);
            // selected segment: elevated dark + gold text; unselected: flat + gray
            java.awt.event.ItemListener restyle = e -> {
                b.setForeground(b.isSelected() ? BRAND_GOLD : Color.LIGHT_GRAY);
                b.setBackground(b.isSelected() ? PANEL_DEEP : PANEL_BUTTON);
            };
            b.addItemListener(restyle);
            toggleRow.add(b);
        }
        firstBtn.setForeground(BRAND_GOLD);
        firstBtn.setBackground(PANEL_DEEP);
        firstBtn.addActionListener(e -> cards.show(cardHost, key + ".first"));
        secondBtn.addActionListener(e -> cards.show(cardHost, key + ".second"));

        wrapper.add(toggleRow, BorderLayout.NORTH);
        wrapper.add(cardHost, BorderLayout.CENTER);
        return wrapper;
    }


    /**
     * Chunk B (#2): the ONE empty-state label — four hand-rolled copies had drifted
     * on color/insets; the restored surfaces make empty states visible, so they
     * should read identically everywhere.
     */
    private JLabel emptyStateLabel(String text)
    {
        JLabel empty = new JLabel(text);
        empty.setForeground(Color.GRAY);
        empty.setBorder(new EmptyBorder(20, 8, 20, 8));
        empty.setAlignmentX(Component.LEFT_ALIGNMENT);
        return empty;
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
        header.add(Box.createVerticalStrut(4));

        // (Nature-rune barometer removed — nat price is now just an alch-cost
        //  input to high-alch margins via the production graph, not a special branch.)



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
        sessionFlipCountLabel.setForeground(GfoPalette.ACCENT_2);
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

        if (config.showWealthInOverlay())
        {
            JPanel wealthRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            wealthRow.setOpaque(false);
            wealthRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel wealthTitle = new JLabel("WEALTH Δ");
            wealthTitle.setForeground(TEXT_DIM);
            wealthTitle.setFont(wealthTitle.getFont().deriveFont(Font.BOLD, 9f));
            wealthDeltaLabel = new JLabel("—");
            wealthDeltaLabel.setForeground(BRAND_GOLD);
            wealthDeltaLabel.setFont(wealthDeltaLabel.getFont().deriveFont(Font.BOLD, 11f));
            wealthRow.add(wealthTitle);
            wealthRow.add(wealthDeltaLabel);
            header.add(Box.createVerticalStrut(4));
            header.add(wealthRow);
        }

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

        JButton chartBtn = new JButton("Chart");
        styleSecondaryButton(chartBtn);
        chartBtn.setToolTipText("Show a native price-history chart for the current search");
        chartBtn.addActionListener(e -> openNativeChartForSearch());
        searchPanel.add(chartBtn, BorderLayout.WEST);

        topPanel.add(searchPanel, BorderLayout.CENTER);

        // Category filter buttons
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        filterPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        String[] categories = {"All", CAT_WATCH, "Weapons", "Armor", "Consumables", "Resources"};
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

        // Sort row — turns the browse list into ranked flip suggestions.
        JPanel sortRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 2));
        sortRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JLabel sortLbl = new JLabel("Sort:");
        sortLbl.setForeground(TEXT_DIM);
        sortLbl.setFont(sortLbl.getFont().deriveFont(10f));
        sortRow.add(sortLbl);

        final String[] sortLabels = {"Best (profit/limit)", "Margin (gp)", "Margin %", "Name (A-Z)"};
        final String[] sortKeys = {SORT_BEST, SORT_MARGIN, SORT_MARGIN_PCT, SORT_NAME};
        JComboBox<String> sortCombo = new JComboBox<>(sortLabels);
        sortCombo.setFont(sortCombo.getFont().deriveFont(10f));
        sortCombo.setForeground(Color.LIGHT_GRAY);
        sortCombo.setBackground(PANEL_BUTTON);
        sortCombo.setToolTipText("Rank suggestions by flip metric (volume-floored) or browse by name");
        sortCombo.addActionListener(e ->
        {
            int idx = sortCombo.getSelectedIndex();
            if (idx >= 0 && idx < sortKeys.length)
            {
                selectedSort = sortKeys[idx];
                searchField.setText("");
                displayAllItemsInCategory();
            }
        });
        sortRow.add(sortCombo);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
        controls.add(filterPanel);
        controls.add(sortRow);

        topPanel.add(controls, BorderLayout.SOUTH);
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
        String query = searchField != null ? searchField.getText().trim() : "";
        String base = "https://grandflipout.com/dashboard.html";
        String url = query.isEmpty()
            ? base
            : base + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        openDashboardUrl(url);
    }

    private void openDashboardUrl(String url)
    {
        try
        {
            net.runelite.client.util.LinkBrowser.browse(url);
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

    /**
     * Resolve the current search box to an item and show a native, in-plugin
     * price-history chart for it in a popup dialog. The Wiki timeseries fetch
     * runs off the EDT; rendering happens back on the EDT.
     */
    private void openNativeChartForSearch()
    {
        String query = searchField != null ? searchField.getText().trim() : "";
        if (query.isEmpty())
        {
            JOptionPane.showMessageDialog(this,
                "Type an item name in the search box first, then click Chart.",
                "Price Chart", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (!priceService.isReady())
        {
            JOptionPane.showMessageDialog(this,
                "Price data is still loading. Please wait a moment and try again.",
                "Price Chart", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        List<PriceAggregate> matches = priceService.searchByName(query);
        PriceAggregate target = null;
        for (PriceAggregate agg : matches)
        {
            if (agg.getItemName() != null && agg.getItemName().equalsIgnoreCase(query))
            {
                target = agg;
                break;
            }
        }
        if (target == null && !matches.isEmpty())
        {
            target = matches.get(0);
        }
        if (target == null)
        {
            JOptionPane.showMessageDialog(this,
                "No item found for '" + query + "'.",
                "Price Chart", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        openNativeChart(target.getItemId(), target.getItemName());
    }

    /** Build the chart dialog for a specific item and kick off the data fetch. */
    private void openNativeChart(int itemId, String itemName)
    {
        final String name = itemName != null ? itemName : ("Item " + itemId);
        final String timestep = "1h"; // ~15 days of hourly data — a useful default window

        PriceChartPanel chart = new PriceChartPanel();
        chart.setLoading();

        java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
        final javax.swing.JDialog dialog = new javax.swing.JDialog(owner, "Price History — " + name);
        dialog.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        dialog.getContentPane().setBackground(ColorScheme.DARK_GRAY_COLOR);
        chart.setPreferredSize(new Dimension(560, 320));
        dialog.getContentPane().add(chart);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);

        Runnable fetch = () ->
        {
            try
            {
                List<com.fliphelper.model.TimeseriesPoint> points =
                    priceService.getWikiClient().fetchTimeseries(itemId, timestep);
                SwingUtilities.invokeLater(() -> chart.setPoints(name + " (" + timestep + ")", points));
            }
            catch (Exception ex)
            {
                log.warn("Failed to load timeseries for item {}", itemId, ex);
                SwingUtilities.invokeLater(() -> chart.setError("Failed to load price history"));
            }
        };

        executor.execute(fetch);
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

        JPanel historyActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        historyActions.setOpaque(false);

        JButton filterAllBtn = new JButton("All");
        JButton filterProfitBtn = new JButton("Profit");
        JButton filterLossBtn = new JButton("Loss");
        for (JButton b : new JButton[] { filterAllBtn, filterProfitBtn, filterLossBtn })
        {
            styleSecondaryButton(b);
            b.setFont(b.getFont().deriveFont(10f));
        }
        filterAllBtn.addActionListener(e -> { historyFilter = "all"; updateHistoryTab(); });
        filterProfitBtn.addActionListener(e -> { historyFilter = "profit"; updateHistoryTab(); });
        filterLossBtn.addActionListener(e -> { historyFilter = "loss"; updateHistoryTab(); });
        historyActions.add(filterAllBtn);
        historyActions.add(filterProfitBtn);
        historyActions.add(filterLossBtn);

        JButton refreshLogBtn = new JButton("Refresh Log");
        styleSecondaryButton(refreshLogBtn);
        refreshLogBtn.addActionListener(e -> updateHistoryTab());
        historyActions.add(refreshLogBtn);

        JButton importGeHistoryBtn = new JButton("Import GE history");
        styleSecondaryButton(importGeHistoryBtn);
        importGeHistoryBtn.setToolTipText("Open the in-game Grand Exchange History tab, then click "
            + "to back-fill any mobile/untracked trades it shows (deduplicated).");
        importGeHistoryBtn.addActionListener(e ->
        {
            if (geHistoryImportAction != null)
            {
                geHistoryImportAction.run();
            }
        });
        historyActions.add(importGeHistoryBtn);

        JButton exportCsvBtn = new JButton("Export CSV");
        stylePrimaryButton(exportCsvBtn);
        exportCsvBtn.setToolTipText("Export completed flips to CSV for advanced trade-log analysis");
        exportCsvBtn.addActionListener(e -> exportHistoryCsv());
        historyActions.add(exportCsvBtn);

        headerPanel.add(historyActions, BorderLayout.EAST);

        // Stack the section header above the numbers-only Stats block (both NORTH).
        JPanel northStack = new JPanel();
        northStack.setLayout(new BoxLayout(northStack, BoxLayout.Y_AXIS));
        northStack.setOpaque(false);
        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        northStack.add(headerPanel);
        statsPanel = new StatsPanel(flipTracker, sessionManager);
        statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Re-render the history list whenever the account/interval selection changes.
        statsPanel.setSelectionListener(this::updateHistoryTab);
        northStack.add(statsPanel);
        JSeparator statsSep = new JSeparator();
        statsSep.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        statsSep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        northStack.add(statsSep);
        panel.add(northStack, BorderLayout.NORTH);

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
        });
    }

    public void updateHeader()
    {
        long profit = flipTracker.getSessionProfit().get();
        sessionProfitLabel.setText(formatGpFull(profit) + " gp");
        sessionProfitLabel.setForeground(profit >= 0 ? PROFIT_GREEN : LOSS_RED);

        long flipCount = flipTracker.getSessionFlipCount().get();
        sessionFlipCountLabel.setText(String.valueOf(flipCount));

        long avgProfit = (long) flipTracker.getAverageProfitPerFlip();
        avgProfitLabel.setText(formatGpFull(avgProfit) + " gp");
        avgProfitLabel.setForeground(avgProfit >= 0 ? GfoPalette.TEXT : GfoPalette.DOWN);

        long gpPerHour = flipTracker.getGpPerHour();
        gpPerHourLabel.setText(gpPerHour > 0 ? formatGpFull(gpPerHour) + " gp/hr" : "—");
        gpPerHourLabel.setForeground(gpPerHour > 0 ? BRAND_GOLD : TEXT_DIM);

        if (wealthDeltaLabel != null && sessionManager != null && sessionManager.getActiveSession() != null)
        {
            long start = sessionManager.getActiveSession().getStartTotalWealthGp();
            long current = sessionManager.getActiveSession().getEndTotalWealthGp();
            if (current <= 0 && start <= 0)
            {
                wealthDeltaLabel.setText("—");
                wealthDeltaLabel.setForeground(TEXT_DIM);
            }
            else if (current <= 0)
            {
                wealthDeltaLabel.setText("Start: " + formatGpFull(start) + " gp");
                wealthDeltaLabel.setForeground(TEXT_DIM);
            }
            else
            {
                long delta = current - start;
                String prefix = delta > 0 ? "+" : "";
                wealthDeltaLabel.setText(prefix + formatGpFull(delta) + " gp");
                wealthDeltaLabel.setForeground(delta > 0 ? PROFIT_GREEN : delta < 0 ? LOSS_RED : TEXT_DIM);
            }
        }

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

        // (Nature-rune barometer update removed — see note in buildPanel.)
    }
    public void updateFlipsTab()
    {
        activeFlipsPanel.removeAll();

        if (flipTracker.getActiveFlips().isEmpty())
        {
            activeFlipsPanel.add(emptyStateLabel("No active flips. Buy something in the GE!"));
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
                    sep.setForeground(GfoPalette.BORDER);
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

        List<TradeLogEntry> logEntries = dataDir != null
            ? TradeLogReader.readRecent(dataDir, 40, priceService.getGson())
            : new ArrayList<>();

        String selectedAccount = statsPanel != null
            ? statsPanel.getSelectedAccount() : StatsPanel.ALL_ACCOUNTS;

        List<TradeLogEntry> filteredLog = new ArrayList<>();
        for (TradeLogEntry entry : logEntries)
        {
            if ("profit".equals(historyFilter) && entry.getProfit() <= 0)
            {
                continue;
            }
            if ("loss".equals(historyFilter) && entry.getProfit() >= 0)
            {
                continue;
            }
            if (!matchesAccount(entry.getAccountName(), selectedAccount))
            {
                continue;
            }
            filteredLog.add(entry);
            if (filteredLog.size() >= 25)
            {
                break;
            }
        }

        for (int i = 0; i < filteredLog.size(); i++)
        {
            if (i > 0)
            {
                JSeparator sep = new JSeparator();
                sep.setForeground(GfoPalette.BORDER);
                sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                historyPanel.add(sep);
            }
            historyPanel.add(buildTradeLogCard(filteredLog.get(i)));
        }

        int shown = filteredLog.size();

        if (shown == 0)
        {
            List<com.fliphelper.model.FlipItem> completed = flipTracker.getCompletedFlips();
            int displayCount = Math.min(completed.size(), 25);
            for (int i = 0; i < displayCount; i++)
            {
                com.fliphelper.model.FlipItem flip = completed.get(i);
                if ("profit".equals(historyFilter) && flip.getProfit() <= 0)
                {
                    continue;
                }
                if ("loss".equals(historyFilter) && flip.getProfit() >= 0)
                {
                    continue;
                }
                if (!StatsPanel.ALL_ACCOUNTS.equals(selectedAccount)
                    && !StatsPanel.accountLabel(flip).equals(selectedAccount))
                {
                    continue;
                }
                JPanel card = buildHistoryCard(flip);
                historyPanel.add(card);
            }
            if (displayCount == 0 && logEntries.isEmpty())
            {
                historyPanel.add(emptyStateLabel("No completed flips yet. trade_log.ndjson fills on GE sells."));
            }
        }

        historyPanel.revalidate();
        historyPanel.repaint();

        // Refresh the numbers-only Stats block for the selected interval.
        if (statsPanel != null)
        {
            statsPanel.recompute();
        }

        // Refresh the summary panel (top items + session log totals) — find it by name
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
                        long logProfit = TradeLogReader.sumProfit(logEntries);
                        JLabel sessionLogLabel = new JLabel(
                            "Trade log (session): " + logEntries.size() + " flips, "
                                + formatGpFull(logProfit) + " gp");
                        sessionLogLabel.setForeground(logProfit >= 0 ? PROFIT_GREEN : LOSS_RED);
                        sessionLogLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                        summaryPanel.add(sessionLogLabel);
                        summaryPanel.add(Box.createVerticalStrut(6));

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
                                    "  " + entry.getKey() + ": " + formatGpFull(entry.getValue()) + " gp");
                                itemLabel.setForeground(entry.getValue() >= 0 ? GfoPalette.UP : GfoPalette.DOWN);
                                itemLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                                summaryPanel.add(itemLabel);
                            }
                        }
                        else
                        {
                            summaryPanel.add(emptyStateLabel("Complete flips to see top items"));
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

    private void styleSecondaryButton(AbstractButton btn)
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
        field.setBackground(GfoPalette.PANEL);
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
        Color cardBg = GfoPalette.CARD;

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
        Color stateColor = stateName.contains("Buy") ? GfoPalette.UP
            : stateName.contains("Sell") ? GfoPalette.ACCENT_2
            : GfoPalette.ACCENT_2;
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
            long tax = com.fliphelper.util.GeTax.tax(flip.getItemId(), expectedSell, flip.getQuantity());
            expectedProfit -= tax;

            JPanel profitRow = new JPanel(new BorderLayout());
            profitRow.setOpaque(false);
            JLabel expectedLabel = new JLabel("Expected Profit: " + formatGpFull(expectedProfit) + " gp");
            expectedLabel.setForeground(expectedProfit >= 0
                ? GfoPalette.UP : GfoPalette.DOWN);
            expectedLabel.setFont(expectedLabel.getFont().deriveFont(Font.BOLD, 11f));
            profitRow.add(expectedLabel, BorderLayout.WEST);
            card.add(profitRow);
        }
        else
        {
            JLabel naLabel = new JLabel("Expected Profit: N/A");
            naLabel.setForeground(GfoPalette.TEXT_DIM);
            card.add(naLabel);
        }

        return card;
    }

    private JPanel buildHistoryCard(com.fliphelper.model.FlipItem flip)
    {
        Color cardBg = GfoPalette.CARD;
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

        String profitText = (profit >= 0 ? "+" : "") + formatGpFull(profit) + " gp";
        JLabel profitLabel = new JLabel(profitText);
        profitLabel.setForeground(profit >= 0 ? GfoPalette.UP : GfoPalette.DOWN);
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
        roiLabel.setForeground(roi >= 0 ? GfoPalette.UP : GfoPalette.DOWN);
        row3.add(roiLabel);
        row3.add(createCompactLabel("Tax: " + formatGp(flip.getTax())));
        long gpPerHour = flip.getGpPerHour();
        JLabel gpHrLabel = createCompactLabel(gpPerHour > 0 ? formatGp(gpPerHour) + "/hr" : "-/hr");
        gpHrLabel.setForeground(gpPerHour > 0 ? GfoPalette.ACCENT_2 : GfoPalette.TEXT_DIM);
        row3.add(gpHrLabel);
        card.add(row3);

        return card;
    }

    private JPanel buildTradeLogCard(TradeLogEntry entry)
    {
        long profit = entry.getProfit();
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(GfoPalette.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PANEL_BORDER),
            new EmptyBorder(6, 10, 6, 10)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        JPanel row1 = new JPanel(new BorderLayout());
        row1.setOpaque(false);
        JLabel nameLabel = new JLabel(entry.getItemName() != null ? entry.getItemName() : ("Item " + entry.getItemId()));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 11f));
        row1.add(nameLabel, BorderLayout.WEST);

        String profitText = (profit >= 0 ? "+" : "") + formatGpFull(profit) + " gp";
        JLabel profitLabel = new JLabel(profitText);
        profitLabel.setForeground(profit >= 0 ? PROFIT_GREEN : LOSS_RED);
        profitLabel.setFont(profitLabel.getFont().deriveFont(Font.BOLD, 11f));
        row1.add(profitLabel, BorderLayout.EAST);
        card.add(row1);
        card.add(Box.createVerticalStrut(2));

        if (entry.getSource() != null && !entry.getSource().isEmpty())
        {
            JLabel sourceLabel = createCompactLabel(
                "ge_event".equals(entry.getSource()) ? "GE sell" : entry.getSource());
            sourceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(sourceLabel);
            card.add(Box.createVerticalStrut(2));
        }

        JPanel row2 = new JPanel(new GridLayout(1, 4, 4, 0));
        row2.setOpaque(false);
        row2.add(createCompactLabel(entry.getQuantity() + "x"));
        row2.add(createCompactLabel("B:" + formatGpFull(entry.getBuyPrice())));
        row2.add(createCompactLabel("S:" + formatGpFull(entry.getSellPrice())));
        row2.add(createCompactLabel("Slot " + (entry.getGeSlot() + 1)));
        card.add(row2);

        if (entry.getTotalWealthGp() != null && entry.getTotalWealthGp() > 0)
        {
            card.add(Box.createVerticalStrut(2));
            JLabel wealthLabel = createCompactLabel("Wealth @ sell: " + formatGpFull(entry.getTotalWealthGp()) + " gp");
            wealthLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(wealthLabel);
        }

        return card;
    }

    /**
     * Compact info label for tight rows.
     */
    private JLabel createCompactLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(GfoPalette.TEXT_MUTED);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 9f));
        return label;
    }

    /**
     * Whether a trade-log entry's account matches the selected account filter.
     * Null/blank account names bucket as {@link StatsPanel#UNKNOWN_ACCOUNT}.
     *
     * @param accountName the entry's recorded RSN (may be null on legacy lines)
     * @param selected the account selected in the stats panel
     * @return true if the entry should be shown
     */
    private boolean matchesAccount(String accountName, String selected)
    {
        if (StatsPanel.ALL_ACCOUNTS.equals(selected))
        {
            return true;
        }
        String bucket = (accountName == null || accountName.trim().isEmpty())
            ? StatsPanel.UNKNOWN_ACCOUNT : accountName;
        return bucket.equals(selected);
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

        String[] categories = {"All", CAT_WATCH, "Weapons", "Armor", "Consumables", "Resources"};
        for (int i = 0; i < categoryButtons.length; i++)
        {
            JButton btn = categoryButtons[i];
            if (categories[i].equals(selectedCategory))
            {
                btn.setBackground(BRAND_GOLD);
                btn.setForeground(GfoPalette.PANEL);
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
                btn.setForeground(GfoPalette.TEXT_MUTED);
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
            loading.setForeground(GfoPalette.ACCENT_2);
            loading.setBorder(new EmptyBorder(20, 8, 20, 8));
            priceResultsPanel.add(loading);
            priceResultsPanel.revalidate();
            priceResultsPanel.repaint();
            return;
        }

        priceResultsPanel.removeAll();

        boolean watchMode = CAT_WATCH.equals(selectedCategory);
        // Anonymous (no unlocked account) sees F2P-item suggestions only. Explicit search
        // and price lookup stay free for ALL items — only this browse list is gated.
        boolean unlocked = isUnlocked();
        int membersHidden = 0;
        List<PriceAggregate> allItems = new ArrayList<>();

        if (watchMode)
        {
            // The player's starred items — their own picks, shown in full (no F2P gate,
            // no volume floor), ordered by the active sort.
            for (Integer id : watchlist.getAll())
            {
                PriceAggregate agg = priceService.getPrice(id);
                if (agg != null)
                {
                    allItems.add(agg);
                }
            }
            allItems.sort(comparatorFor(selectedSort));
        }
        else
        {
            // Candidates come pre-ranked (and volume-floored) from the price engine for the
            // metric sorts; "name" falls back to the full alphabetical browse. We then apply
            // the category + F2P gate while PRESERVING the engine's rank order.
            for (PriceAggregate agg : getRankedCandidates())
            {
                if (agg == null || !matchesCategory(agg.getItemName(), selectedCategory))
                {
                    continue;
                }
                if (!unlocked && agg.getMapping() != null && agg.getMapping().isMembers())
                {
                    membersHidden++;
                    continue;
                }
                allItems.add(agg);
            }

            if (!unlocked && membersHidden > 0)
            {
                priceResultsPanel.add(buildUnlockCta(membersHidden));
            }
        }

        int displayCount = Math.min(allItems.size(), 50);
        for (int i = 0; i < displayCount; i++)
        {
            PriceAggregate agg = allItems.get(i);
            JPanel card = buildPriceCard(agg);
            priceResultsPanel.add(card);
            if (i < displayCount - 1)
            {
                JSeparator sep = new JSeparator();
                sep.setForeground(GfoPalette.BORDER);
                sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                priceResultsPanel.add(sep);
            }
        }

        if (allItems.isEmpty())
        {
            JLabel noResults = new JLabel(watchMode
                ? "<html>Nothing starred yet.<br>Tap the ☆ on any item to watch it here.</html>"
                : "No items in '" + selectedCategory + "' category.");
            noResults.setForeground(Color.GRAY);
            noResults.setBorder(new EmptyBorder(20, 8, 20, 8));
            priceResultsPanel.add(noResults);
        }
        else
        {
            JLabel countLabel = new JLabel("Showing " + displayCount + " of " + allItems.size()
                + " · sorted by " + sortLabel(selectedSort));
            countLabel.setForeground(Color.GRAY);
            countLabel.setBorder(new EmptyBorder(4, 8, 4, 8));
            priceResultsPanel.add(countLabel);
        }

        priceResultsPanel.revalidate();
        priceResultsPanel.repaint();
    }

    // Category label for the player's starred items.
    private static final String CAT_WATCH = "★ Watch";
    // Sort modes for the Prices suggestion list.
    private static final String SORT_BEST = "best";   // profit per GE limit
    private static final String SORT_MARGIN = "margin"; // net margin after tax (default)
    private static final String SORT_MARGIN_PCT = "marginPct"; // margin %
    private static final String SORT_NAME = "name";   // alphabetical browse
    // Min 1h volume for an item to qualify as a suggestion (must be flippable).
    private static final int MIN_SUGGESTION_VOLUME = 10;

    /**
     * Candidate items for the suggestion list, pre-ranked by the selected sort. The metric
     * sorts route through the PriceService ranking engine (volume-floored, descending); the
     * "name" sort returns the full catalogue alphabetically for plain browsing.
     */
    private List<PriceAggregate> getRankedCandidates()
    {
        switch (selectedSort)
        {
            case SORT_MARGIN:
                return priceService.getTopByMargin(500, MIN_SUGGESTION_VOLUME);
            case SORT_MARGIN_PCT:
                return priceService.getHighMarginItems(0.0, MIN_SUGGESTION_VOLUME);
            case SORT_NAME:
            {
                List<PriceAggregate> all = new ArrayList<>();
                for (Integer id : priceService.getAllItemIds())
                {
                    PriceAggregate agg = priceService.getPrice(id);
                    if (agg != null)
                    {
                        all.add(agg);
                    }
                }
                all.sort(Comparator.comparing(PriceAggregate::getItemName));
                return all;
            }
            case SORT_BEST:
            default:
                return priceService.getTopByProfitPerLimit(500, MIN_SUGGESTION_VOLUME);
        }
    }

    private String sortLabel(String sort)
    {
        switch (sort)
        {
            case SORT_MARGIN: return "margin";
            case SORT_MARGIN_PCT: return "margin %";
            case SORT_NAME: return "name";
            case SORT_BEST: return "profit/limit";
            default: return "margin";
        }
    }

    /** Comparator matching the selected sort — used to order the watchlist (no volume floor). */
    private Comparator<PriceAggregate> comparatorFor(String sort)
    {
        switch (sort)
        {
            case SORT_MARGIN:
                return Comparator.comparingLong(PriceAggregate::getNetMarginAfterTax).reversed();
            case SORT_MARGIN_PCT:
                return Comparator.comparingDouble(PriceAggregate::getConsensusMarginPercent).reversed();
            case SORT_NAME:
                return Comparator.comparing(PriceAggregate::getItemName);
            case SORT_BEST:
                return Comparator.comparingLong(PriceAggregate::getProfitPerLimit).reversed();
            default:
                return Comparator.comparingLong(PriceAggregate::getNetMarginAfterTax).reversed();
        }
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
            loading.setForeground(GfoPalette.ACCENT_2);
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

    /**
     * Small "set target" dialog for an item's price alert. Lets the user enter a target buy
     * price (alert when the price drops to it) and/or a target sell price (alert when it
     * rises to it); blank or 0 clears that side. Persisted via {@link com.fliphelper.util.AlertStore};
     * the plugin checks targets on each price refresh and notifies on a crossing. Read-only —
     * setting a target never touches the GE.
     */
    private void editAlertTarget(PriceAggregate agg)
    {
        int itemId = agg.getItemId();
        long curBuy = alertStore.getBuyTarget(itemId);
        long curSell = alertStore.getSellTarget(itemId);

        JTextField buyField = new JTextField(curBuy > 0 ? String.valueOf(curBuy) : "", 10);
        JTextField sellField = new JTextField(curSell > 0 ? String.valueOf(curSell) : "", 10);

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        form.add(new JLabel(agg.getItemName()));
        form.add(new JLabel("Alert when insta-buy drops to (gp):"));
        form.add(buyField);
        form.add(new JLabel("Now: " + formatGpFull(agg.getBestLowPrice()) + " gp"));
        form.add(new JLabel("Alert when insta-sell rises to (gp):"));
        form.add(sellField);
        form.add(new JLabel("Now: " + formatGpFull(agg.getBestHighPrice()) + " gp"));
        form.add(new JLabel("Leave blank or 0 to clear a target."));

        int result = JOptionPane.showConfirmDialog(this, form,
            "Set price alert", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION)
        {
            return;
        }

        long buy = parseGpField(buyField.getText());
        long sell = parseGpField(sellField.getText());
        alertStore.setTargets(itemId, buy, sell);
        // Re-render so the bell reflects the new state.
        displayAllItemsInCategory();
    }

    /** Parse a user-entered gp value (commas allowed); returns 0 for blank/invalid. */
    private static long parseGpField(String text)
    {
        if (text == null)
        {
            return 0;
        }
        String cleaned = text.replaceAll("[,\\s]", "");
        if (cleaned.isEmpty())
        {
            return 0;
        }
        try
        {
            long v = Long.parseLong(cleaned);
            return Math.max(0, v);
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    private JPanel buildPriceCard(PriceAggregate agg)
    {
        Color cardBg = GfoPalette.CARD;
        long margin = agg.getConsensusMargin();

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(cardBg);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PANEL_BORDER),
            new EmptyBorder(8, 10, 8, 10)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        // ── Row 1: ☆ star + Name + Margin % badge + Signal badge ──
        JPanel row1 = new JPanel(new BorderLayout());
        row1.setOpaque(false);

        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        nameRow.setOpaque(false);

        final int watchItemId = agg.getItemId();
        JLabel star = new JLabel(watchlist.contains(watchItemId) ? "★" : "☆");
        star.setForeground(watchlist.contains(watchItemId) ? BRAND_GOLD : TEXT_DIM);
        star.setFont(star.getFont().deriveFont(14f));
        star.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        star.setToolTipText("Star this item to add it to your ★ Watch list");
        star.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                boolean nowWatched = watchlist.toggle(watchItemId);
                star.setText(nowWatched ? "★" : "☆");
                star.setForeground(nowWatched ? BRAND_GOLD : TEXT_DIM);
                // When viewing the watch list, un-starring should drop the card immediately.
                if (CAT_WATCH.equals(selectedCategory))
                {
                    displayAllItemsInCategory();
                }
            }
        });
        nameRow.add(star);

        // Bell: set a buy/sell price target. Lit gold when a target is active. Tapping opens
        // a tiny dialog; the plugin fires a RuneLite notification when the target is crossed
        // (only while "Price / Offer Alerts" is enabled in config).
        final boolean hasTarget = alertStore.get(watchItemId) != null;
        // Plain-text affordance (BMP emoji bells render as tofu in Swing). Reads "alert ●"
        // when a target is set, dim "alert" otherwise.
        JLabel bell = new JLabel(hasTarget ? "alert ●" : "alert");
        bell.setForeground(hasTarget ? BRAND_GOLD : TEXT_DIM);
        bell.setFont(bell.getFont().deriveFont(Font.BOLD, 9f));
        bell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        bell.setToolTipText(hasTarget
            ? "Price alert set — click to edit or clear"
            : "Set a buy/sell price target for an alert");
        bell.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                editAlertTarget(agg);
            }
        });
        nameRow.add(bell);

        JLabel nameLabel = new JLabel(agg.getItemName());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        nameRow.add(nameLabel);
        row1.add(nameRow, BorderLayout.WEST);

        JPanel badges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        badges.setOpaque(false);

        // Signal badge from server intelligence (cached)
        if (config.enableServerFunctionality() && config.enableServerIntelligence())
        {
            int itemId = agg.getItemId();
            CachedAdvisor cached = advisorCache.get(itemId);
            if (cached != null && !cached.isStale())
            {
                JLabel sigBadge = new JLabel(" " + cached.action + " ");
                sigBadge.setForeground(Color.WHITE);
                sigBadge.setOpaque(true);
                sigBadge.setBackground(cached.action.equals("BUY") ? GfoPalette.UP
                    : cached.action.equals("SELL") ? GfoPalette.DOWN : GfoPalette.TEXT_DIM);
                sigBadge.setFont(sigBadge.getFont().deriveFont(Font.BOLD, 9f));
                badges.add(sigBadge);
            }
            else if (executor != null && intelligenceClient != null)
            {
                executor.execute(() -> {
                    try {
                        var result = intelligenceClient.fetchSmartAdvisor(itemId);
                        advisorCache.put(itemId, new CachedAdvisor(result.getAction(), result.getSignalStrength()));
                    } catch (Exception ignored) {}
                });
            }
        }

        double marginPct = agg.getConsensusMarginPercent();
        Color marginColor = marginPct >= 5 ? GfoPalette.UP
            : marginPct >= 2 ? GfoPalette.ACCENT_2
            : GfoPalette.DOWN;
        JLabel marginBadge = new JLabel(" " + String.format("%.1f%%", marginPct) + " ");
        marginBadge.setForeground(Color.WHITE);
        marginBadge.setOpaque(true);
        marginBadge.setBackground(marginColor.darker());
        marginBadge.setFont(marginBadge.getFont().deriveFont(Font.BOLD, 10f));
        // N2: explainable confidence — surface the locally-computed score + the inputs
        // behind it (margin / volume / freshness) so the suggestion isn't a black box.
        marginBadge.setToolTipText("Confidence: " + agg.getLocalConfidenceLabel()
            + " — margin " + String.format("%.1f%%", marginPct)
            + ", volume " + formatGp(agg.getTotalVolume1h()) + "/h"
            + ", data " + agg.getFreshnessLabel());
        badges.add(marginBadge);

        // N1: data-freshness badge — flag items whose last real trade is old, so a
        // stale (possibly unreliable) price is visible at a glance.
        long ageSec = agg.getDataAgeSeconds();
        Color freshColor = ageSec < 300 ? GfoPalette.UP
            : ageSec < 3600 ? GfoPalette.ACCENT_2
            : GfoPalette.DOWN;
        JLabel freshBadge = new JLabel(" " + agg.getFreshnessLabel() + " ");
        freshBadge.setForeground(Color.WHITE);
        freshBadge.setOpaque(true);
        freshBadge.setBackground(freshColor.darker());
        freshBadge.setFont(freshBadge.getFont().deriveFont(Font.BOLD, 9f));
        freshBadge.setToolTipText("Last real trade: " + agg.getFreshnessLabel()
            + " ago. Older data may mean a thin, unreliable price.");
        badges.add(freshBadge);

        row1.add(badges, BorderLayout.EAST);
        card.add(row1);
        card.add(Box.createVerticalStrut(4));

        // ── Row 2: Buy / Sell / Margin ──
        JPanel row2 = new JPanel(new GridLayout(1, 3, 4, 0));
        row2.setOpaque(false);
        row2.add(createMetaLabel("Insta-Buy", formatGp(agg.getBestHighPrice())));
        row2.add(createMetaLabel("Insta-Sell", formatGp(agg.getBestLowPrice())));
        // Lead with the AFTER-TAX net margin — the honest profit per item. Showing the
        // gross margin overstates profit (the #1 flipping mistake post-2% tax).
        long netMargin = agg.getNetMarginAfterTax();
        JPanel marginMeta = createMetaLabel("Net margin", formatGp(netMargin));
        marginMeta.setToolTipText("Margin after the 2% GE sell tax (gross "
            + formatGp(margin) + ")");
        Component[] mcs = marginMeta.getComponents();
        if (mcs.length > 1 && mcs[1] instanceof JLabel)
        {
            ((JLabel) mcs[1]).setForeground(netMargin > 0 ? GfoPalette.UP : GfoPalette.DOWN);
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
            ((JLabel) pcs[1]).setForeground(GfoPalette.ACCENT_2);
        }
        row3.add(profitMeta);
        card.add(row3);

        // ── Est. fill — liquidity / capital-lockup guide (no competitor surfaces this) ──
        double fillMin = agg.getEstFillMinutesForLimit();
        if (fillMin >= 0)
        {
            card.add(Box.createVerticalStrut(2));
            JPanel fillMeta = createMetaLabel("Est. fill (limit)", agg.getFillEstimateLabel());
            fillMeta.setToolTipText("Rough time to buy the full buy limit at current volume — "
                + "a liquidity guide, not exact. Longer = capital may sit unfilled.");
            Component[] fcs = fillMeta.getComponents();
            if (fcs.length > 1 && fcs[1] instanceof JLabel)
            {
                Color fillColor = fillMin < 15 ? GfoPalette.UP
                    : fillMin < 60 ? GfoPalette.ACCENT_2
                    : GfoPalette.DOWN;
                ((JLabel) fcs[1]).setForeground(fillColor);
            }
            card.add(fillMeta);
        }

        // ── Row 4: Alch floor (if item has highalch value) ──
        if (agg.getMapping() != null && agg.getMapping().getHighalch() > 0)
        {
            int highalch = agg.getMapping().getHighalch();
            long natPrice = 150;
            PriceAggregate natAgg = priceService.getPrice(561);
            if (natAgg != null && natAgg.getBestLowPrice() > 0)
            {
                natPrice = natAgg.getBestLowPrice();
            }
            long alchFloor = highalch - natPrice;
            long currentBuy = agg.getBestLowPrice();
            boolean nearFloor = alchFloor > 0 && currentBuy > 0 && currentBuy <= alchFloor * 1.05;

            if (alchFloor > 0)
            {
                card.add(Box.createVerticalStrut(2));
                JPanel alchRow = new JPanel(new GridLayout(1, 2, 4, 0));
                alchRow.setOpaque(false);
                JPanel alchMeta = createMetaLabel("Alch Floor", formatGp(alchFloor));
                if (nearFloor)
                {
                    Component[] acs = alchMeta.getComponents();
                    if (acs.length > 1 && acs[1] instanceof JLabel)
                    {
                        ((JLabel) acs[1]).setForeground(GfoPalette.DOWN);
                        ((JLabel) acs[1]).setText(formatGp(alchFloor) + " (NEAR)");
                    }
                }
                alchRow.add(alchMeta);
                long alchProfit = highalch - currentBuy - natPrice;
                JPanel alchProfitMeta = createMetaLabel("Alch Profit", formatGp(alchProfit));
                Component[] apcs = alchProfitMeta.getComponents();
                if (apcs.length > 1 && apcs[1] instanceof JLabel)
                {
                    ((JLabel) apcs[1]).setForeground(alchProfit > 0
                        ? GfoPalette.UP : GfoPalette.DOWN);
                }
                alchRow.add(alchProfitMeta);
                card.add(alchRow);
            }
        }

        return card;
    }

    // ==================== INTEL TAB ====================

    /**
     * Cancel background tasks (the Intel auto-refresh scheduled on RuneLite's shared
     * executor). Called from the plugin's shutDown so the task does not keep firing
     * network calls after the plugin is disabled.
     */
    public void shutdown()
    {
        if (intelRefreshFuture != null)
        {
            intelRefreshFuture.cancel(false);
            intelRefreshFuture = null;
        }
        intelAutoRefreshStarted = false;
    }

    private JPanel buildIntelTab()
    {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(PANEL_DEEP);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(8, 8, 4, 8));
        JLabel title = new JLabel("Server Intelligence (opt-in)");
        title.setForeground(BRAND_GOLD);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        top.add(title, BorderLayout.WEST);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setFont(refreshBtn.getFont().deriveFont(10f));
        refreshBtn.setForeground(BRAND_GOLD);
        refreshBtn.setBackground(PANEL_BUTTON);
        refreshBtn.setFocusPainted(false);
        refreshBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PANEL_BORDER),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        refreshBtn.addActionListener(e -> refreshIntel());
        top.add(refreshBtn, BorderLayout.EAST);
        wrapper.add(top, BorderLayout.NORTH);

        intelContentPanel = new JPanel();
        intelContentPanel.setLayout(new BoxLayout(intelContentPanel, BoxLayout.Y_AXIS));
        intelContentPanel.setBackground(PANEL_DEEP);
        intelContentPanel.setBorder(new EmptyBorder(4, 8, 8, 8));

        rebuildIntelGate();

        JScrollPane scroll = new JScrollPane(intelContentPanel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(PANEL_DEEP);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        wrapper.add(scroll, BorderLayout.CENTER);

        return wrapper;
    }

    /**
     * Decide what the Intel tab shows: a premium unlock CTA for anonymous users, the
     * "server intelligence is off" hint when the account is unlocked but the toggle is off,
     * or the live intelligence (with a 60s auto-refresh started once).
     */
    private void rebuildIntelGate()
    {
        if (intelContentPanel == null)
        {
            return;
        }
        intelContentPanel.removeAll();

        if (!isUnlocked())
        {
            JLabel msg = new JLabel("<html><div style='width:170px'><b>Server intelligence is a "
                + "premium feature.</b><br>Create a free Grand Flip Out account to unlock VPIN "
                + "alerts, screener signals, dump predictions, and the portfolio optimizer.</div></html>");
            msg.setForeground(TEXT_DIM);
            msg.setBorder(new EmptyBorder(16, 8, 12, 8));
            msg.setAlignmentX(Component.LEFT_ALIGNMENT);
            intelContentPanel.add(msg);

            intelContentPanel.add(buildUnlockButton("Create free account"));
        }
        else if (!config.enableServerIntelligence())
        {
            JLabel offLabel = new JLabel("<html>Server intelligence is OFF.<br>Enable in plugin config to see VPIN alerts,<br>screener signals, dump predictions, and more.</html>");
            offLabel.setForeground(TEXT_DIM);
            offLabel.setBorder(new EmptyBorder(20, 8, 20, 8));
            intelContentPanel.add(offLabel);
        }
        else
        {
            JLabel loading = new JLabel("Loading intelligence data...");
            loading.setForeground(TEXT_DIM);
            loading.setBorder(new EmptyBorder(20, 8, 20, 8));
            intelContentPanel.add(loading);

            // Auto-refresh the Intel tab every 60s — register the schedule only once.
            if (executor != null && !intelAutoRefreshStarted)
            {
                intelAutoRefreshStarted = true;
                intelRefreshFuture = executor.scheduleAtFixedRate(this::refreshIntel, 2, 60, java.util.concurrent.TimeUnit.SECONDS);
            }
            else
            {
                refreshIntel();
            }
        }

        intelContentPanel.revalidate();
        intelContentPanel.repaint();
    }

    private void refreshIntel()
    {
        // Premium feature — gated behind an unlocked account and the opt-in toggle.
        if (!isUnlocked() || !config.enableServerFunctionality() || !config.enableServerIntelligence() || intelligenceClient == null)
        {
            return;
        }

        executor.execute(() ->
        {
            try
            {
                com.google.gson.JsonObject screener = intelligenceClient.fetchScreener("buy_signal", 10);
                com.google.gson.JsonObject vpin = intelligenceClient.fetchVPIN();
                com.google.gson.JsonObject nextDumps = intelligenceClient.fetchNextDumps(10);
                com.google.gson.JsonObject alerts = intelligenceClient.fetchAlerts(5);
                com.google.gson.JsonObject barometer = null;
                com.google.gson.JsonObject banWave = null;
                com.google.gson.JsonObject portfolio = null;
                try { barometer = intelligenceClient.fetchBarometer(); } catch (Exception ignored) {}
                try { banWave = intelligenceClient.fetchBanWave(); } catch (Exception ignored) {}

                try { portfolio = intelligenceClient.fetchOptimize(10_000_000, 4, "balanced"); } catch (Exception ignored) {}

                final com.google.gson.JsonObject fBarometer = barometer;
                final com.google.gson.JsonObject fBanWave = banWave;
                final com.google.gson.JsonObject fPortfolio = portfolio;

                javax.swing.SwingUtilities.invokeLater(() ->
                {
                    intelContentPanel.removeAll();

                    // Barometer (nature rune macro)
                    if (fBarometer != null && fBarometer.has("price"))
                    {
                        int natPrice = fBarometer.get("price").getAsInt();
                        String change = fBarometer.has("change5mPct") && !fBarometer.get("change5mPct").isJsonNull()
                            ? String.format("%+.1f%%", fBarometer.get("change5mPct").getAsDouble()) : "—";
                        String signal = fBarometer.has("signal") ? fBarometer.get("signal").getAsString() : "neutral";
                        Color sigColor = "alch_opportunity".equals(signal) ? PROFIT_GREEN
                            : "alch_squeeze".equals(signal) ? LOSS_RED : TEXT_DIM;

                        JLabel baroHeader = new JLabel("Nature Rune Barometer");
                        baroHeader.setForeground(BRAND_GOLD);
                        baroHeader.setFont(baroHeader.getFont().deriveFont(Font.BOLD, 11f));
                        baroHeader.setBorder(new EmptyBorder(4, 0, 2, 0));
                        baroHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
                        intelContentPanel.add(baroHeader);

                        JLabel baroVal = new JLabel("  " + formatGpFull(natPrice) + " gp (" + change + ") — " + signal.replace('_', ' '));
                        baroVal.setForeground(sigColor);
                        baroVal.setFont(baroVal.getFont().deriveFont(10f));
                        baroVal.setBorder(new EmptyBorder(1, 8, 4, 0));
                        baroVal.setAlignmentX(Component.LEFT_ALIGNMENT);
                        intelContentPanel.add(baroVal);
                    }

                    // Ban wave alert
                    if (fBanWave != null && fBanWave.has("banWaveDetected") && fBanWave.get("banWaveDetected").getAsBoolean())
                    {
                        JLabel bwHeader = new JLabel("⚠ BOT BAN WAVE DETECTED");
                        bwHeader.setForeground(LOSS_RED);
                        bwHeader.setFont(bwHeader.getFont().deriveFont(Font.BOLD, 11f));
                        bwHeader.setBorder(new EmptyBorder(4, 0, 2, 0));
                        bwHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
                        intelContentPanel.add(bwHeader);

                        int affected = fBanWave.has("affectedCount") ? fBanWave.get("affectedCount").getAsInt() : 0;
                        JLabel bwDetail = new JLabel("  " + affected + " bot-farmed items with supply drops");
                        bwDetail.setForeground(Color.LIGHT_GRAY);
                        bwDetail.setFont(bwDetail.getFont().deriveFont(10f));
                        bwDetail.setBorder(new EmptyBorder(1, 8, 4, 0));
                        bwDetail.setAlignmentX(Component.LEFT_ALIGNMENT);
                        intelContentPanel.add(bwDetail);
                    }

                    // VPIN section
                    addIntelSection("VPIN Order Flow", vpin, "elevated",
                        e -> e.getAsJsonObject().has("itemName")
                            ? e.getAsJsonObject().get("itemName").getAsString()
                              + " — VPIN " + e.getAsJsonObject().get("vpin").getAsString()
                              + " (" + e.getAsJsonObject().get("signal").getAsString() + ")"
                            : "?");

                    // Buy signals
                    addIntelSection("Buy Signals", screener, "results",
                        e -> {
                            com.google.gson.JsonObject r = e.getAsJsonObject();
                            String name = r.has("itemName") ? r.get("itemName").getAsString() : "?";
                            int score = r.has("score") ? r.get("score").getAsInt() : 0;
                            String regime = r.has("regime") ? r.get("regime").getAsString() : "";
                            return name + " — score " + (score > 0 ? "+" : "") + score + " (" + regime + ")";
                        });

                    // Next dumps
                    addIntelSection("Next Dump Predictions", nextDumps, "predictions",
                        e -> {
                            com.google.gson.JsonObject p = e.getAsJsonObject();
                            String name = p.has("name") ? p.get("name").getAsString() : "Item " + p.get("item_id");
                            int hazard = (int) (p.get("hazard").getAsDouble() * 100);
                            return name + " — " + hazard + "% hazard";
                        });

                    // HP alerts
                    addIntelSection("Dump Alerts", alerts, "alerts",
                        e -> {
                            com.google.gson.JsonObject a = e.getAsJsonObject();
                            String name = a.has("name") ? a.get("name").getAsString() : "?";
                            int score = a.has("score") ? a.get("score").getAsInt() : 0;
                            String sev = a.has("severity") ? a.get("severity").getAsString() : "";
                            return name + " — score " + score + " [" + sev + "]";
                        });

                    // Portfolio optimizer
                    if (fPortfolio != null && fPortfolio.has("allocations"))
                    {
                        addIntelSection("Portfolio Optimizer (10M balanced)", fPortfolio, "allocations",
                            e -> {
                                com.google.gson.JsonObject a = e.getAsJsonObject();
                                String name = a.has("itemName") ? a.get("itemName").getAsString() : "?";
                                int qty = a.has("quantity") ? a.get("quantity").getAsInt() : 0;
                                long exp = a.has("expectedProfit") ? a.get("expectedProfit").getAsLong() : 0;
                                return name + " x" + qty + " — exp " + formatGpFull(exp) + " gp";
                            });
                    }

                    if (intelContentPanel.getComponentCount() == 0)
                    {
                        intelContentPanel.add(emptyStateLabel("No active intelligence signals."));
                    }

                    intelContentPanel.revalidate();
                    intelContentPanel.repaint();
                });
            }
            catch (Exception e)
            {
                log.debug("Intel refresh failed: {}", e.getMessage());
            }
        });
    }

    private void addIntelSection(String title, com.google.gson.JsonObject data, String arrayKey,
                                  java.util.function.Function<com.google.gson.JsonElement, String> formatter)
    {
        if (data == null || !data.has(arrayKey)) return;
        com.google.gson.JsonArray arr = data.getAsJsonArray(arrayKey);
        if (arr == null || arr.size() == 0) return;

        JLabel header = new JLabel(title + " (" + arr.size() + ")");
        header.setForeground(BRAND_GOLD);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 11f));
        header.setBorder(new EmptyBorder(8, 0, 4, 0));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        intelContentPanel.add(header);

        int max = Math.min(arr.size(), 8);
        for (int i = 0; i < max; i++)
        {
            try
            {
                String text = formatter.apply(arr.get(i));
                JLabel item = new JLabel(text);
                item.setForeground(Color.LIGHT_GRAY);
                item.setFont(item.getFont().deriveFont(10f));
                item.setBorder(new EmptyBorder(1, 8, 1, 0));
                item.setAlignmentX(Component.LEFT_ALIGNMENT);
                intelContentPanel.add(item);
            }
            catch (Exception ignored) {}
        }
    }

    // ==================== HELPERS ====================

    private JLabel createInfoLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(Color.LIGHT_GRAY);
        return label;
    }

    private String formatGpFull(long amount)
    {
        return GP_FORMAT.format(amount);
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
