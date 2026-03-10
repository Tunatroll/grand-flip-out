package com.fliphelper.ui;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.tracker.AccountDataManager;
import com.fliphelper.tracker.FlipTracker;
import com.fliphelper.tracker.GEOfferHelper;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemComposition;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.util.List;


public class SlotsPanel extends JPanel
{
    // Colors
    private static final Color BG_DARK = new Color(0x0F, 0x0F, 0x17);
    private static final Color BG_CARD = new Color(0x1A, 0x1A, 0x2E);
    private static final Color BG_CARD_ALT = new Color(0x16, 0x16, 0x25);
    private static final Color GOLD = new Color(0xFF, 0xB8, 0x00);
    private static final Color GREEN = new Color(0x00, 0xD2, 0x6A);
    private static final Color RED = new Color(0xFF, 0x47, 0x57);
    private static final Color BLUE = new Color(0x3B, 0x82, 0xF6);
    private static final Color DIM = new Color(0x60, 0x60, 0x80);
    private static final Color SEPARATOR = new Color(0x2A, 0x2A, 0x45);

    private final GrandFlipOutConfig config;
    private final Client client;
    private final PriceService priceService;
    private final FlipTracker flipTracker;
    private final AccountDataManager accountDataManager;
    private final GEOfferHelper geOfferHelper;

    // UI components
    private JComboBox<String> accountSelector;
    private JLabel accountStatusLabel;
    private JPanel slotsContainer;
    private final JPanel[] slotCards = new JPanel[8];
    private JLabel totalProfitLabel;
    private JLabel hotkeyHintLabel;
    private Timer refreshTimer;

    public SlotsPanel(GrandFlipOutConfig config, Client client, PriceService priceService,
                     FlipTracker flipTracker, AccountDataManager accountDataManager,
                     GEOfferHelper geOfferHelper)
    {
        this.config = config;
        this.client = client;
        this.priceService = priceService;
        this.flipTracker = flipTracker;
        this.accountDataManager = accountDataManager;
        this.geOfferHelper = geOfferHelper;

        setLayout(new BorderLayout());
        setBackground(BG_DARK);

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildSlotsArea(), BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);

        // Auto-refresh slot timers every 1 second
        refreshTimer = new Timer(1000, e -> refreshSlotTimers());
        refreshTimer.start();
    }

    public void stopTimer()
    {
        if (refreshTimer != null)
        {
            refreshTimer.stop();
        }
    }

    // ==================== TOP BAR (Account Switcher) ====================

    private JPanel buildTopBar()
    {
        JPanel topBar = new JPanel();
        topBar.setLayout(new BoxLayout(topBar, BoxLayout.Y_AXIS));
        topBar.setBackground(BG_CARD);
        topBar.setBorder(new EmptyBorder(8, 10, 8, 10));

        // Row 1: "GE Slots" title + active account indicator
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JLabel title = new JLabel("GE Slots");
        title.setForeground(GOLD);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        titleRow.add(title, BorderLayout.WEST);

        accountStatusLabel = new JLabel("No account");
        accountStatusLabel.setForeground(DIM);
        accountStatusLabel.setFont(accountStatusLabel.getFont().deriveFont(10f));
        titleRow.add(accountStatusLabel, BorderLayout.EAST);
        topBar.add(titleRow);
        topBar.add(Box.createVerticalStrut(6));

        // Row 2: Account dropdown selector
        JPanel selectorRow = new JPanel(new BorderLayout(6, 0));
        selectorRow.setOpaque(false);

        JLabel acctLabel = new JLabel("Account:");
        acctLabel.setForeground(DIM);
        acctLabel.setFont(acctLabel.getFont().deriveFont(Font.BOLD, 10f));
        selectorRow.add(acctLabel, BorderLayout.WEST);

        accountSelector = new JComboBox<>();
        accountSelector.setBackground(BG_DARK);
        accountSelector.setForeground(Color.WHITE);
        accountSelector.setFont(accountSelector.getFont().deriveFont(11f));
        accountSelector.addActionListener(e -> onAccountSelected());
        selectorRow.add(accountSelector, BorderLayout.CENTER);

        topBar.add(selectorRow);
        topBar.add(Box.createVerticalStrut(4));

        // Separator
        JSeparator sep = new JSeparator();
        sep.setForeground(SEPARATOR);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        topBar.add(sep);

        return topBar;
    }

    private void onAccountSelected()
    {
        String selected = (String) accountSelector.getSelectedItem();
        if (selected != null && accountDataManager != null)
        {
            // Update status label
            AccountDataManager.AccountData data = accountDataManager.getAccountData(selected);
            if (data != null)
            {
                accountStatusLabel.setText(data.getRsn() + " — " + data.getFlipCount() + " flips");
                accountStatusLabel.setForeground(GREEN);
            }
        }
    }

    // ==================== SLOTS AREA ====================

    private JScrollPane buildSlotsArea()
    {
        slotsContainer = new JPanel();
        slotsContainer.setLayout(new BoxLayout(slotsContainer, BoxLayout.Y_AXIS));
        slotsContainer.setBackground(BG_DARK);
        slotsContainer.setBorder(new EmptyBorder(4, 0, 4, 0));

        // Build 8 GE slot cards
        for (int i = 0; i < 8; i++)
        {
            slotCards[i] = buildSlotCard(i);
            slotsContainer.add(slotCards[i]);
            if (i < 7)
            {
                slotsContainer.add(Box.createVerticalStrut(2));
            }
        }

        JScrollPane scrollPane = new JScrollPane(slotsContainer);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(null);
        return scrollPane;
    }

    private JPanel buildSlotCard(int slot)
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(slot % 2 == 0 ? BG_CARD : BG_CARD_ALT);
        card.setBorder(new EmptyBorder(6, 10, 6, 10));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        card.setName("slot_" + slot); // For lookup during refresh

        // Row 1: Slot # + Item name + State badge
        JPanel row1 = new JPanel(new BorderLayout());
        row1.setOpaque(false);

        JLabel slotLabel = new JLabel("Slot " + (slot + 1));
        slotLabel.setForeground(DIM);
        slotLabel.setFont(slotLabel.getFont().deriveFont(Font.BOLD, 10f));

        JLabel nameLabel = new JLabel(" — Empty");
        nameLabel.setForeground(Color.LIGHT_GRAY);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 11f));
        nameLabel.setName("name_" + slot);

        JPanel nameGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        nameGroup.setOpaque(false);
        nameGroup.add(slotLabel);
        nameGroup.add(nameLabel);
        row1.add(nameGroup, BorderLayout.WEST);

        JLabel stateLabel = new JLabel(" EMPTY ");
        stateLabel.setName("state_" + slot);
        stateLabel.setForeground(Color.WHITE);
        stateLabel.setOpaque(true);
        stateLabel.setBackground(DIM.darker());
        stateLabel.setFont(stateLabel.getFont().deriveFont(Font.BOLD, 9f));
        row1.add(stateLabel, BorderLayout.EAST);
        card.add(row1);
        card.add(Box.createVerticalStrut(3));

        // Row 2: Price | Qty | Timer | Freshness
        JPanel row2 = new JPanel(new GridLayout(1, 4, 4, 0));
        row2.setOpaque(false);

        JLabel priceLabel = new JLabel("—");
        priceLabel.setName("price_" + slot);
        priceLabel.setForeground(Color.LIGHT_GRAY);
        priceLabel.setFont(priceLabel.getFont().deriveFont(10f));
        row2.add(wrapMeta("Price", priceLabel));

        JLabel qtyLabel = new JLabel("—");
        qtyLabel.setName("qty_" + slot);
        qtyLabel.setForeground(Color.LIGHT_GRAY);
        qtyLabel.setFont(qtyLabel.getFont().deriveFont(10f));
        row2.add(wrapMeta("Qty", qtyLabel));

        JLabel timerLabel = new JLabel("—");
        timerLabel.setName("timer_" + slot);
        timerLabel.setForeground(GREEN);
        timerLabel.setFont(timerLabel.getFont().deriveFont(Font.BOLD, 11f));
        row2.add(wrapMeta("Idle", timerLabel));

        // Margin freshness indicator
        JLabel freshnessLabel = new JLabel("—");
        freshnessLabel.setName("fresh_" + slot);
        freshnessLabel.setForeground(DIM);
        freshnessLabel.setFont(freshnessLabel.getFont().deriveFont(9f));
        row2.add(wrapMeta("Check", freshnessLabel));

        card.add(row2);
        card.add(Box.createVerticalStrut(2));

        // Row 3: Margin info + estimated profit (only visible when we have data)
        JPanel row3 = new JPanel(new GridLayout(1, 2, 4, 0));
        row3.setOpaque(false);

        JLabel marginLabel = new JLabel("—");
        marginLabel.setName("margin_" + slot);
        marginLabel.setForeground(GOLD);
        marginLabel.setFont(marginLabel.getFont().deriveFont(10f));
        row3.add(wrapMeta("Margin", marginLabel));

        JLabel estProfitLabel = new JLabel("—");
        estProfitLabel.setName("estprofit_" + slot);
        estProfitLabel.setForeground(GREEN);
        estProfitLabel.setFont(estProfitLabel.getFont().deriveFont(Font.BOLD, 10f));
        row3.add(wrapMeta("Est. Profit", estProfitLabel));

        card.add(row3);

        // Row 4: Buy limit progress bar + countdown 
        JPanel row4 = new JPanel(new GridLayout(1, 2, 4, 0));
        row4.setOpaque(false);

        JLabel limitLabel = new JLabel("—");
        limitLabel.setName("limit_" + slot);
        limitLabel.setForeground(DIM);
        limitLabel.setFont(limitLabel.getFont().deriveFont(9f));
        row4.add(wrapMeta("Buy Limit", limitLabel));

        JLabel countdownLabel = new JLabel("—");
        countdownLabel.setName("countdown_" + slot);
        countdownLabel.setForeground(DIM);
        countdownLabel.setFont(countdownLabel.getFont().deriveFont(9f));
        row4.add(wrapMeta("Limit Reset", countdownLabel));

        card.add(row4);

        // Row 5: Sparkline mini-chart (60×20px) for price trend at a glance
        JPanel sparklinePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        sparklinePanel.setOpaque(false);
        sparklinePanel.setName("sparkline_" + slot);
        sparklinePanel.setPreferredSize(new Dimension(Integer.MAX_VALUE, 22));
        sparklinePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        card.add(sparklinePanel);

        return card;
    }

    private JPanel wrapMeta(String title, JLabel valueLabel)
    {
        JPanel p = new JPanel(new BorderLayout(0, 1));
        p.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setForeground(DIM);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 8f));
        p.add(t, BorderLayout.NORTH);
        p.add(valueLabel, BorderLayout.CENTER);
        return p;
    }

    // ==================== BOTTOM BAR ====================

    private JPanel buildBottomBar()
    {
        JPanel bottomBar = new JPanel();
        bottomBar.setLayout(new BoxLayout(bottomBar, BoxLayout.Y_AXIS));
        bottomBar.setBackground(BG_CARD);
        bottomBar.setBorder(new EmptyBorder(6, 10, 8, 10));

        // Separator
        JSeparator sep = new JSeparator();
        sep.setForeground(SEPARATOR);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        bottomBar.add(sep);
        bottomBar.add(Box.createVerticalStrut(6));

        // Session profit for active account
        JPanel profitRow = new JPanel(new GridLayout(1, 2, 8, 0));
        profitRow.setOpaque(false);

        JPanel sessionCard = new JPanel(new BorderLayout(0, 2));
        sessionCard.setOpaque(false);
        JLabel profitTitle = new JLabel("SESSION");
        profitTitle.setForeground(DIM);
        profitTitle.setFont(profitTitle.getFont().deriveFont(Font.BOLD, 8f));
        sessionCard.add(profitTitle, BorderLayout.NORTH);
        totalProfitLabel = new JLabel("0 gp");
        totalProfitLabel.setForeground(GREEN);
        totalProfitLabel.setFont(totalProfitLabel.getFont().deriveFont(Font.BOLD, 13f));
        sessionCard.add(totalProfitLabel, BorderLayout.CENTER);
        profitRow.add(sessionCard);

        JPanel lifetimeCard = new JPanel(new BorderLayout(0, 2));
        lifetimeCard.setOpaque(false);
        JLabel lifetimeTitle = new JLabel("LIFETIME");
        lifetimeTitle.setForeground(DIM);
        lifetimeTitle.setFont(lifetimeTitle.getFont().deriveFont(Font.BOLD, 8f));
        lifetimeCard.add(lifetimeTitle, BorderLayout.NORTH);
        JLabel lifetimeProfitLabel = new JLabel("0 gp");
        lifetimeProfitLabel.setName("lifetimeProfit");
        lifetimeProfitLabel.setForeground(GOLD);
        lifetimeProfitLabel.setFont(lifetimeProfitLabel.getFont().deriveFont(Font.BOLD, 13f));
        lifetimeCard.add(lifetimeProfitLabel, BorderLayout.CENTER);
        profitRow.add(lifetimeCard);

        bottomBar.add(profitRow);
        bottomBar.add(Box.createVerticalStrut(6));

        // Hotkey hints
        hotkeyHintLabel = new JLabel(buildHotkeyHintText());
        hotkeyHintLabel.setForeground(DIM);
        hotkeyHintLabel.setFont(hotkeyHintLabel.getFont().deriveFont(9f));
        hotkeyHintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomBar.add(hotkeyHintLabel);

        return bottomBar;
    }

    private String buildHotkeyHintText()
    {
        return "<html>" +
            "<b style='color:#FFB800'>E</b> Set buy price &nbsp; " +
            "<b style='color:#FFB800'>R</b> Set sell price &nbsp; " +
            "<b style='color:#FFB800'>Q</b> Max qty<br>" +
            "<b style='color:#FFB800'>Ctrl+F</b> Favorite &nbsp; " +
            "<b style='color:#FFB800'>Ctrl+Shift+G</b> Top suggestion" +
            "</html>";
    }

    // ==================== REFRESH LOGIC ====================

    
    private void refreshSlotTimers()
    {
        if (client == null)
        {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try
            {
                GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
                if (offers == null)
                {
                    return;
                }

                for (int slot = 0; slot < 8; slot++)
                {
                    updateSlotCard(slot, offers.length > slot ? offers[slot] : null);
                }

                // Update session profit
                long profit = flipTracker.getSessionProfit().get();
                totalProfitLabel.setText(formatGp(profit) + " gp");
                totalProfitLabel.setForeground(profit >= 0 ? GREEN : RED);

                // Update lifetime profit from account data
                if (accountDataManager != null)
                {
                    long lifetime = accountDataManager.getLifetimeProfit();
                    JLabel lifetimeLabel = findLabel(SlotsPanel.this, "lifetimeProfit");
                    if (lifetimeLabel != null)
                    {
                        lifetimeLabel.setText(formatGp(lifetime) + " gp");
                        lifetimeLabel.setForeground(lifetime >= 0 ? GOLD : RED);
                    }
                }
            }
            catch (Exception e)
            {
                // Silently ignore — we're on a timer, don't spam logs
            }
        });
    }

    private void updateSlotCard(int slot, GrandExchangeOffer offer)
    {
        JPanel card = slotCards[slot];
        if (card == null)
        {
            return;
        }

        // Find child labels by name
        JLabel nameLabel = findLabel(card, "name_" + slot);
        JLabel stateLabel = findLabel(card, "state_" + slot);
        JLabel priceLabel = findLabel(card, "price_" + slot);
        JLabel qtyLabel = findLabel(card, "qty_" + slot);
        JLabel timerLabel = findLabel(card, "timer_" + slot);

        if (offer == null || offer.getItemId() == 0)
        {
            // Empty slot
            if (nameLabel != null) nameLabel.setText(" — Empty");
            if (stateLabel != null)
            {
                stateLabel.setText(" EMPTY ");
                stateLabel.setBackground(DIM.darker());
            }
            if (priceLabel != null) priceLabel.setText("—");
            if (qtyLabel != null) qtyLabel.setText("—");
            if (timerLabel != null)
            {
                timerLabel.setText("—");
                timerLabel.setForeground(DIM);
            }
            JLabel marginL = findLabel(card, "margin_" + slot);
            JLabel estL = findLabel(card, "estprofit_" + slot);
            JLabel freshL = findLabel(card, "fresh_" + slot);
            if (marginL != null) { marginL.setText("—"); marginL.setForeground(DIM); }
            if (estL != null) { estL.setText("—"); estL.setForeground(DIM); }
            if (freshL != null) { freshL.setText("—"); freshL.setForeground(DIM); }
            card.setBackground(slot % 2 == 0 ? BG_CARD : BG_CARD_ALT);
            return;
        }

        // Active offer
        GrandExchangeOfferState state = offer.getState();
        int itemId = offer.getItemId();
        String itemName;
        try
        {
            ItemComposition def = client.getItemDefinition(itemId);
            itemName = def != null ? def.getName() : "Item #" + itemId;
        }
        catch (Exception e)
        {
            itemName = "Item #" + itemId;
        }

        if (nameLabel != null) nameLabel.setText(" " + itemName);

        // State badge with color
        if (stateLabel != null)
        {
            String stateText = getStateText(state);
            Color stateBg = getStateColor(state);
            stateLabel.setText(" " + stateText + " ");
            stateLabel.setBackground(stateBg.darker());
        }

        // Price
        if (priceLabel != null)
        {
            long price = offer.getQuantitySold() > 0
                ? offer.getSpent() / offer.getQuantitySold()
                : offer.getPrice();
            priceLabel.setText(formatGp(price));
        }

        // Quantity (filled/total)
        if (qtyLabel != null)
        {
            qtyLabel.setText(offer.getQuantitySold() + "/" + offer.getTotalQuantity());
        }

        // Timer — color coded by staleness
        if (timerLabel != null && geOfferHelper != null)
        {
            String idleStr = geOfferHelper.getSlotIdleString(slot);
            long idleSec = geOfferHelper.getSlotIdleSeconds(slot);

            timerLabel.setText(idleStr);

            // Color: green <2m, yellow <15m, orange <1h, red >1h
            if (idleSec < 0)
            {
                timerLabel.setForeground(DIM);
            }
            else if (idleSec < 120)
            {
                timerLabel.setForeground(GREEN);
            }
            else if (idleSec < 900)
            {
                timerLabel.setForeground(GOLD);
            }
            else if (idleSec < 3600)
            {
                timerLabel.setForeground(new Color(0xFF, 0x8C, 0x00)); // Orange
            }
            else
            {
                timerLabel.setForeground(RED);
            }
        }

        // Margin + Estimated profit from price data
        JLabel marginLabel = findLabel(card, "margin_" + slot);
        JLabel estProfitLabel = findLabel(card, "estprofit_" + slot);
        JLabel freshnessLabel = findLabel(card, "fresh_" + slot);

        // Update margin freshness indicator
        if (freshnessLabel != null && geOfferHelper != null)
        {
            int freshness = geOfferHelper.getMarginFreshness(itemId);
            freshnessLabel.setText(geOfferHelper.getFreshnessLabel(freshness));
            freshnessLabel.setForeground(geOfferHelper.getFreshnessColor(freshness));
        }

        if (priceService != null)
        {
            PriceAggregate agg = priceService.getPrice(itemId);
            if (agg != null)
            {
                long margin = agg.getConsensusMargin();
                if (marginLabel != null)
                {
                    marginLabel.setText(formatGp(margin) + " (" + String.format("%.1f%%", agg.getConsensusMarginPercent()) + ")");
                    marginLabel.setForeground(margin > 0 ? GOLD : RED);
                }

                // Calculate estimated profit for this slot
                if (estProfitLabel != null)
                {
                    long offerPrice = offer.getQuantitySold() > 0
                        ? offer.getSpent() / offer.getQuantitySold()
                        : offer.getPrice();
                    int totalQty = offer.getTotalQuantity();

                    if (state == GrandExchangeOfferState.BUYING)
                    {
                        // Buying: est profit = (sell price - buy price - tax) * qty
                        long estSell = agg.getBestHighPrice();
                        long estProfit = (estSell - offerPrice) * totalQty;
                        long tax = Math.min((long)(estSell * 0.02), 5_000_000L) * totalQty;
                        estProfit -= tax;
                        estProfitLabel.setText(formatGp(estProfit));
                        estProfitLabel.setForeground(estProfit >= 0 ? GREEN : RED);
                    }
                    else if (state == GrandExchangeOfferState.SELLING)
                    {
                        // Selling: show what we should get when it fills
                        estProfitLabel.setText("Pending sell");
                        estProfitLabel.setForeground(GOLD);
                    }
                    else if (state == GrandExchangeOfferState.BOUGHT)
                    {
                        // Bought: ready to sell
                        long estSell = agg.getBestHighPrice();
                        long estProfit = (estSell - offerPrice) * totalQty;
                        long tax = Math.min((long)(estSell * 0.02), 5_000_000L) * totalQty;
                        estProfit -= tax;
                        estProfitLabel.setText(formatGp(estProfit) + " (sell now)");
                        estProfitLabel.setForeground(estProfit >= 0 ? GREEN : RED);
                    }
                    else
                    {
                        estProfitLabel.setText("—");
                        estProfitLabel.setForeground(DIM);
                    }
                }
            }
            else
            {
                if (marginLabel != null) { marginLabel.setText("—"); marginLabel.setForeground(DIM); }
                if (estProfitLabel != null) { estProfitLabel.setText("—"); estProfitLabel.setForeground(DIM); }
            }
        }

        // Update buy-limit progress (Row 4)
        JLabel limitLabel = findLabel(card, "limit_" + slot);
        JLabel countdownLabel = findLabel(card, "countdown_" + slot);
        if (geOfferHelper != null && itemId > 0)
        {
            GEOfferHelper.BuyLimitState limitState = geOfferHelper.getBuyLimitState(itemId);
            if (limitState != null)
            {
                if (limitLabel != null)
                {
                    String usage = geOfferHelper.getBuyLimitUsage(itemId);
                    limitLabel.setText(usage);
                    double prog = limitState.getProgress();
                    if (prog >= 0.95) limitLabel.setForeground(RED);        // Nearly full
                    else if (prog >= 0.7) limitLabel.setForeground(GOLD);   // Getting close
                    else limitLabel.setForeground(GREEN);                    // Plenty left
                }
                if (countdownLabel != null)
                {
                    String countdown = geOfferHelper.getBuyLimitCountdown(itemId);
                    countdownLabel.setText(countdown);
                    long secs = limitState.getSecondsUntilReset();
                    if (secs < 600) countdownLabel.setForeground(GREEN);       // <10m — almost reset
                    else if (secs < 3600) countdownLabel.setForeground(GOLD);  // <1h
                    else countdownLabel.setForeground(DIM);                     // Normal
                }
            }
            else
            {
                if (limitLabel != null) { limitLabel.setText("—"); limitLabel.setForeground(DIM); }
                if (countdownLabel != null) { countdownLabel.setText("—"); countdownLabel.setForeground(DIM); }
            }
        }

        // Update sparkline mini-chart
        JPanel sparklinePanel = findPanel(card, "sparkline_" + slot);
        if (sparklinePanel != null && priceService != null && itemId > 0)
        {
            sparklinePanel.removeAll();
            PriceAggregate agg = priceService.getPrice(itemId);
            if (agg != null)
            {
                // Get recent price history for sparkline (last 12 data points)
                java.util.List<Long> priceHistory = priceService.getRecentPrices(itemId, 12);
                if (priceHistory != null && priceHistory.size() >= 3)
                {
                    JPanel chart = SparklineRenderer.createPanel(priceHistory, 60, 18, null);
                    sparklinePanel.add(chart);

                    // Add tiny price change label next to sparkline
                    long first = priceHistory.get(0);
                    long last = priceHistory.get(priceHistory.size() - 1);
                    double changePct = first > 0 ? ((double)(last - first) / first) * 100 : 0;
                    JLabel changeLabel = new JLabel(String.format(" %+.1f%%", changePct));
                    changeLabel.setFont(changeLabel.getFont().deriveFont(9f));
                    changeLabel.setForeground(changePct > 0.5 ? GREEN : changePct < -0.5 ? RED : DIM);
                    sparklinePanel.add(changeLabel);
                }
            }
            sparklinePanel.revalidate();
        }

        // Highlight completed offers with a subtle border
        if (state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.SOLD)
        {
            card.setBackground(new Color(0x0A, 0x2A, 0x0A)); // Dark green tint
        }
        else if (state == GrandExchangeOfferState.CANCELLED_BUY || state == GrandExchangeOfferState.CANCELLED_SELL)
        {
            card.setBackground(new Color(0x2A, 0x0A, 0x0A)); // Dark red tint
        }
        else
        {
            card.setBackground(slot % 2 == 0 ? BG_CARD : BG_CARD_ALT);
        }
    }

    // ==================== PUBLIC METHODS ====================

    
    public void refreshAccountList()
    {
        if (accountDataManager == null || accountSelector == null)
        {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            String previousSelection = (String) accountSelector.getSelectedItem();
            accountSelector.removeAllItems();

            List<String> accounts = accountDataManager.getAllAccountNames();
            for (String name : accounts)
            {
                accountSelector.addItem(name);
            }

            // Auto-select the active account
            String active = accountDataManager.getActiveAccount();
            if (active != null)
            {
                accountSelector.setSelectedItem(active);
                accountStatusLabel.setText(active);
                accountStatusLabel.setForeground(GREEN);
            }
            else if (previousSelection != null && accounts.contains(previousSelection))
            {
                accountSelector.setSelectedItem(previousSelection);
            }
        });
    }

    
    public void refresh()
    {
        refreshAccountList();
        refreshSlotTimers();
    }

    // ==================== HELPERS ====================

    private String getStateText(GrandExchangeOfferState state)
    {
        if (state == null) return "EMPTY";
        switch (state)
        {
            case BUYING: return "BUYING";
            case BOUGHT: return "BOUGHT";
            case SELLING: return "SELLING";
            case SOLD: return "SOLD";
            case CANCELLED_BUY: return "CANCEL";
            case CANCELLED_SELL: return "CANCEL";
            case EMPTY: return "EMPTY";
            default: return state.name();
        }
    }

    private Color getStateColor(GrandExchangeOfferState state)
    {
        if (state == null) return DIM;
        switch (state)
        {
            case BUYING: return BLUE;
            case BOUGHT: return GREEN;
            case SELLING: return GOLD;
            case SOLD: return GREEN;
            case CANCELLED_BUY:
            case CANCELLED_SELL: return RED;
            default: return DIM;
        }
    }

    
    private JLabel findLabel(Container container, String name)
    {
        for (Component c : container.getComponents())
        {
            if (c instanceof JLabel && name.equals(c.getName()))
            {
                return (JLabel) c;
            }
            if (c instanceof Container)
            {
                JLabel found = findLabel((Container) c, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private JPanel findPanel(Container parent, String name)
    {
        for (Component c : parent.getComponents())
        {
            if (c instanceof JPanel && name.equals(c.getName()))
            {
                return (JPanel) c;
            }
            if (c instanceof Container)
            {
                JPanel found = findPanel((Container) c, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String formatGp(long amount)
    {
       
        // Never abbreviate GP values in user-facing output
        return String.format("%,d", amount);
    }
}
