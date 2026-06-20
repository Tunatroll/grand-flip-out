/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper;

import com.fliphelper.api.PriceService;
import com.fliphelper.model.FlipItem;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.tracker.FlipTracker;
import com.fliphelper.tracker.SessionManager;
import com.fliphelper.util.WealthSnapshot;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.QuantityFormatter;

import javax.inject.Inject;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * In-game overlay displayed when the Grand Exchange interface is open.
 * Shows current item margins, flip status, and session profit.
 */
public class GrandFlipOutOverlay extends Overlay
{
    private static final Color ACCENT_GOLD = new Color(0xFF, 0xB8, 0x00);
    private static final Color PROFIT_GREEN = new Color(0x00, 0xD2, 0x6A);
    private static final Color LOSS_RED = new Color(0xFF, 0x47, 0x57);
    private static final Color WARNING_AMBER = new Color(0xFF, 0xA5, 0x00);
    private static final Color META_NEUTRAL = new Color(0xD4, 0xAF, 0x37);
    private static final Color SLOT_PROFIT = new Color(0x00, 0xB0, 0x5A, 180);
    private static final Color SLOT_LOSS = new Color(0xCC, 0x40, 0x40, 180);
    private static final Color SLOT_FLAT = new Color(0xC8, 0x96, 0x00, 180);
    private static final Color ACT_FILL = new Color(0xFF, 0xB8, 0x00, 60);
    private static final Color ACT_OUTLINE = new Color(0xFF, 0xB8, 0x00, 220);
    private static final NumberFormat GP_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
    private final Client client;
    private final GrandFlipOutConfig config;
    private final PriceService priceService;
    private final FlipTracker flipTracker;
    private final SessionManager sessionManager;
    private final PanelComponent panelComponent = new PanelComponent();

    /** Slot activity timestamps for slot timer display (like Flipping Utilities). */
    private final Instant[] slotLastActive = new Instant[8];

    /** Buy limit reset tracking: itemId -> timestamp of first buy in current 4h window. */
    private final java.util.Map<Integer, Instant> buyLimitStartTimes = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Duration BUY_LIMIT_WINDOW = Duration.ofHours(4);

    /** GE slot the current Advisor suggestion targets (abort/sell), or -1 = none. */
    private volatile int actSlot = -1;

    private boolean visible = true;

    @Inject
    public GrandFlipOutOverlay(Client client, GrandFlipOutConfig config,
                              PriceService priceService, FlipTracker flipTracker,
                              SessionManager sessionManager)
    {
        this.client = client;
        this.config = config;
        this.priceService = priceService;
        this.flipTracker = flipTracker;
        this.sessionManager = sessionManager;

        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.MED);
    }

    public void toggleVisibility()
    {
        visible = !visible;
    }

    /**
     * Set the GE slot the current Advisor suggestion wants the player to act on
     * (e.g. the offer to abort or the filled buy to re-list). Pass -1 to clear.
     * The overlay tints/outlines that slot widget so "Act here" is unambiguous.
     */
    public void setActSlot(int slot)
    {
        this.actSlot = (slot >= 0 && slot < 8) ? slot : -1;
    }

    /**
     * Update slot activity timestamp (called from plugin on GE offer changes).
     */
    public void updateSlotActivity(int slot)
    {
        if (slot >= 0 && slot < 8)
        {
            slotLastActive[slot] = Instant.now();
        }
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!visible)
        {
            return null;
        }

        // Only show when GE is open
        if (!config.showGEOverlay() && !config.showProfitOverlay())
        {
            return null;
        }

        long startTime = System.currentTimeMillis();

        try
        {
            Widget geWidget = client.getWidget(ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER);
            boolean geOpen = geWidget != null && !geWidget.isHidden();

            panelComponent.getChildren().clear();
            panelComponent.setPreferredSize(new Dimension(235, 0));

            if (config.showProfitOverlay())
            {
                renderSessionStats();
            }

            if (geOpen && config.showGEOverlay())
            {
                renderGEInfo();
                renderRecommendedPrices();
            }

            if (geOpen)
            {
                renderActSlotHighlight(graphics);
            }

            if (panelComponent.getChildren().isEmpty())
            {
                return null;
            }

            return panelComponent.render(graphics);
        }
        finally
        {
            long duration = System.currentTimeMillis() - startTime;
            // Log overlay render time for debug purposes (optional)
            if (duration > 10)
            {
                // Log only if render took significant time
            }
        }
    }

    private void renderSessionStats()
    {
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Grand Flip Out")
            .color(ACCENT_GOLD)
            .build());

        long profit = flipTracker.getSessionProfit().get();
        // Color coding: green for profit, orange for break-even, red for loss
        Color profitColor;
        if (profit > 0)
        {
            profitColor = PROFIT_GREEN;
        }
        else if (profit == 0)
        {
            profitColor = WARNING_AMBER;
        }
        else
        {
            profitColor = LOSS_RED;
        }

        String profitPrefix = profit > 0 ? "\u25B2 " : profit < 0 ? "\u25BC " : "";
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Session Profit:")
            .right(profitPrefix + formatGp(profit))
            .rightColor(profitColor)
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Flips:")
            .right(String.valueOf(flipTracker.getSessionFlipCount().get()))
            .build());

        double avg = flipTracker.getAverageProfitPerFlip();
        Color avgColor;
        if (avg > 0)
        {
            avgColor = PROFIT_GREEN;
        }
        else if (avg == 0)
        {
            avgColor = WARNING_AMBER;
        }
        else
        {
            avgColor = LOSS_RED;
        }

        String avgPrefix = avg > 0 ? "\u25B2 " : avg < 0 ? "\u25BC " : "";
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Avg Profit/Flip:")
            .right(avgPrefix + formatGp((long) avg))
            .rightColor(avgColor)
            .build());

        // GP/hr — the key metric flippers care about
        long gpPerHour = flipTracker.getGpPerHour();
        if (gpPerHour > 0)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("GP/hr:")
                .right(formatGp(gpPerHour))
                .rightColor(ACCENT_GOLD)
                .build());
        }

        // Show active flips count
        int activeCount = flipTracker.getActiveFlips().size();
        if (activeCount > 0)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Active Flips:")
                .right(String.valueOf(activeCount))
                .rightColor(META_NEUTRAL)
                .build());
        }

        if (priceService.getLastRefresh() != null && priceService.getLastRefresh() != Instant.EPOCH)
        {
            long ageSeconds = Duration.between(priceService.getLastRefresh(), Instant.now()).getSeconds();
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Price Feed Age:")
                .right(ageSeconds < 60 ? ageSeconds + "s" : (ageSeconds / 60) + "m")
                .rightColor(ageSeconds <= 120 ? PROFIT_GREEN : WARNING_AMBER)
                .build());
        }

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Price-Fill Hotkey:")
            .right(config.priceFillHotkey().toString())
            .rightColor(META_NEUTRAL)
            .build());

        if (config.showWealthInOverlay())
        {
            WealthSnapshot wealth = WealthSnapshot.capture(client, priceService);
            if (wealth.getTotalWealthGp() > 0)
            {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Est. Wealth:")
                    .right(formatGp(wealth.getTotalWealthGp()))
                    .rightColor(META_NEUTRAL)
                    .build());
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("  Coins / Bank:")
                    .right(formatGp(wealth.getCoinGp()) + " / " + formatGp(wealth.getBankGp()))
                    .rightColor(META_NEUTRAL)
                    .build());

                if (sessionManager != null && sessionManager.getActiveSession() != null)
                {
                    long start = sessionManager.getActiveSession().getStartTotalWealthGp();
                    long delta = wealth.getTotalWealthGp() - start;
                    Color deltaColor = delta > 0 ? PROFIT_GREEN : delta < 0 ? LOSS_RED : WARNING_AMBER;
                    String deltaPrefix = delta > 0 ? "+" : "";
                    panelComponent.getChildren().add(LineComponent.builder()
                        .left("  Session Δ Wealth:")
                        .right(deltaPrefix + formatGp(delta))
                        .rightColor(deltaColor)
                        .build());
                }
            }
        }
    }

    private void renderGEInfo()
    {
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("GE Slots")
            .color(ACCENT_GOLD)
            .build());

        // Show slot profit colorizer if enabled
        if (config.showSlotColorizer())
        {
            renderGESlotColorizer();
        }
        else
        {
            // Show info for active flip items (legacy view)
            for (FlipItem flip : flipTracker.getActiveFlips().values())
            {
                if (flip == null)
                {
                    continue;
                }

                PriceAggregate agg = priceService.getPrice(flip.getItemId());
                if (agg == null)
                {
                    continue;
                }

                String itemName = flip.getItemName() != null ? flip.getItemName() : "Unknown";
                String stateName = flip.getState() != null ? flip.getState().getDisplayName() : "Unknown";

                panelComponent.getChildren().add(LineComponent.builder()
                    .left(itemName)
                    .right(stateName)
                    .rightColor(META_NEUTRAL)
                    .build());

                long margin = agg.getConsensusMargin();
                Color marginColor;
                if (margin > 0)
                {
                    marginColor = PROFIT_GREEN;
                }
                else if (margin == 0)
                {
                    marginColor = WARNING_AMBER;
                }
                else
                {
                    marginColor = LOSS_RED;
                }

                panelComponent.getChildren().add(LineComponent.builder()
                    .left("  Margin:")
                    .right(formatGp(margin))
                    .rightColor(marginColor)
                    .build());

                if (config.overlayShowVolume())
                {
                    panelComponent.getChildren().add(LineComponent.builder()
                        .left("  Vol/1h:")
                        .right(QuantityFormatter.formatNumber(agg.getTotalVolume1h()))
                        .build());
                }

                // Expected profit for this flip with color coding
                long expectedSell = agg.getBestHighPrice();
                long tax = com.fliphelper.util.GeTax.tax(flip.getItemId(), expectedSell, flip.getQuantity());
                long expectedProfit = (expectedSell - flip.getBuyPrice()) * flip.getQuantity() - tax;

                Color profitColor;
                if (expectedProfit > 0)
                {
                    profitColor = PROFIT_GREEN;
                }
                else if (expectedProfit == 0)
                {
                    profitColor = WARNING_AMBER;
                }
                else
                {
                    profitColor = LOSS_RED;
                }

                String expPrefix = expectedProfit > 0 ? "\u25B2 " : expectedProfit < 0 ? "\u25BC " : "";
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("  Exp. Profit:")
                    .right(expPrefix + formatGp(expectedProfit))
                    .rightColor(profitColor)
                    .build());

                if (flip.getFrozenSellPrice() > 0)
                {
                    long drift = expectedSell - flip.getFrozenSellPrice();
                    String frozenLabel = drift >= 0
                        ? "  Sell target: " + formatGp(flip.getFrozenSellPrice()) + " (+" + formatGp(drift) + ")"
                        : "  Sell target: " + formatGp(flip.getFrozenSellPrice()) + " (" + formatGp(drift) + ")";
                    panelComponent.getChildren().add(LineComponent.builder()
                        .left(frozenLabel)
                        .right("")
                        .leftColor(drift >= 0 ? PROFIT_GREEN : WARNING_AMBER)
                        .build());
                }
            }
        }
    }

    private void renderRecommendedPrices()
    {
        int currentItemId = client.getVarpValue(net.runelite.api.VarPlayer.CURRENT_GE_ITEM);
        if (currentItemId <= 0)
        {
            return;
        }

        PriceAggregate agg = priceService.getPrice(currentItemId);
        if (agg == null)
        {
            return;
        }

        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Rec. Prices")
            .color(ACCENT_GOLD)
            .build());

        String name = agg.getItemName();
        if (name != null && name.length() > 20) name = name.substring(0, 18) + "..";
        panelComponent.getChildren().add(LineComponent.builder()
            .left(name != null ? name : "Item " + currentItemId)
            .right("")
            .build());

        long buyPrice = agg.getBestLowPrice();
        long sellPrice = agg.getBestHighPrice();
        long tax = com.fliphelper.util.GeTax.tax(currentItemId, sellPrice, 1);
        long netProfit = sellPrice - buyPrice - tax;
        int geLimit = agg.getBuyLimit();

        panelComponent.getChildren().add(LineComponent.builder()
            .left("  Buy at:")
            .right(formatGp(buyPrice))
            .rightColor(PROFIT_GREEN)
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("  Sell at:")
            .right(formatGp(sellPrice))
            .rightColor(PROFIT_GREEN)
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("  Net/item:")
            .right(formatGp(netProfit))
            .rightColor(netProfit > 0 ? PROFIT_GREEN : LOSS_RED)
            .build());

        if (geLimit > 0)
        {
            long profitAtLimit = netProfit * geLimit;
            panelComponent.getChildren().add(LineComponent.builder()
                .left("  P/Limit:")
                .right(formatGp(profitAtLimit))
                .rightColor(profitAtLimit > 0 ? PROFIT_GREEN : LOSS_RED)
                .build());
        }

        // Alch floor
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
            boolean nearFloor = alchFloor > 0 && buyPrice > 0 && buyPrice <= alchFloor * 1.05;
            if (alchFloor > 0)
            {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("  Alch floor:")
                    .right(formatGp(alchFloor) + (nearFloor ? " NEAR" : ""))
                    .rightColor(nearFloor ? LOSS_RED : META_NEUTRAL)
                    .build());
            }
        }
    }

    /**
     * Tint + outline the GE slot the Advisor is pointing at, so the "Act here"
     * suggestion (abort a stale buy, re-list a filled buy to sell) maps onto a
     * physical slot. Read-only — draws over the slot widget bounds, never clicks.
     */
    private void renderActSlotHighlight(Graphics2D graphics)
    {
        int slot = actSlot;
        if (slot < 0 || slot >= 8)
        {
            return;
        }
        Widget geWindow = client.getWidget(ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER);
        if (geWindow == null || geWindow.isHidden())
        {
            return;
        }
        // Children 7..14 of the GE window container are the eight offer slots.
        Widget slotWidget = geWindow.getChild(7 + slot);
        if (slotWidget == null || slotWidget.isHidden())
        {
            return;
        }
        java.awt.Rectangle b = slotWidget.getBounds();
        if (b == null || b.width <= 0 || b.height <= 0)
        {
            return;
        }
        graphics.setColor(ACT_FILL);
        graphics.fillRect(b.x, b.y, b.width, b.height);
        graphics.setColor(ACT_OUTLINE);
        graphics.setStroke(new java.awt.BasicStroke(2f));
        graphics.drawRect(b.x, b.y, b.width, b.height);
    }

    private void renderGESlotColorizer()
    {
        // Iterate all 8 GE slots and color-code based on profit
        for (int slot = 0; slot < 8; slot++)
        {
            GrandExchangeOffer offer = client.getGrandExchangeOffers()[slot];
            if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
            {
                continue;
            }

            int itemId = offer.getItemId();
            long spent = offer.getSpent();
            int quantitySold = offer.getQuantitySold();
            int totalQuantity = offer.getTotalQuantity();
            long price = offer.getPrice();

            PriceAggregate agg = priceService.getPrice(itemId);
            if (agg == null)
            {
                continue;
            }

            boolean isBuying = (offer.getState() == GrandExchangeOfferState.BUYING
                            || offer.getState() == GrandExchangeOfferState.BOUGHT);

            long currentPrice = isBuying ? agg.getBestHighPrice() : agg.getBestLowPrice();
            long paidPerItem = quantitySold > 0 ? Math.round((double) spent / quantitySold) : price;

            long estimatedProfit;
            Color slotColor;
            String profitText;

            if (isBuying)
            {
                // For buys: estimate profit if we sold at current sell price
                long tax = com.fliphelper.util.GeTax.tax(itemId, currentPrice, quantitySold);
                estimatedProfit = (currentPrice - paidPerItem) * quantitySold - tax;
            }
            else
            {
                // For sells: calculate actual revenue vs what we'd pay now
                estimatedProfit = (currentPrice - paidPerItem) * quantitySold;
            }

            if (estimatedProfit > 0)
            {
                slotColor = SLOT_PROFIT;
                profitText = "+" + formatGp(estimatedProfit);
            }
            else if (estimatedProfit < 0)
            {
                slotColor = SLOT_LOSS;
                profitText = formatGp(estimatedProfit);
            }
            else
            {
                slotColor = SLOT_FLAT;
                profitText = "0 gp";
            }

            String itemName = agg.getItemName() != null ? agg.getItemName() : "Slot " + (slot + 1);
            String stateStr = isBuying ? "BUY" : "SELL";
            int pctFilled = totalQuantity > 0 ? (quantitySold * 100 / totalQuantity) : 0;

            // Slot timer: time since last activity (like Flipping Utilities)
            String timerStr = "";
            if (slotLastActive[slot] != null)
            {
                long elapsed = Duration.between(slotLastActive[slot], Instant.now()).getSeconds();
                timerStr = formatSlotTimer(elapsed) + " ";
            }

            panelComponent.getChildren().add(LineComponent.builder()
                .left(String.format("[%d] %s", slot + 1, truncate(itemName, 12)))
                .leftColor(slotColor)
                .right(String.format("%s%s %d%% %s", timerStr, stateStr, pctFilled, profitText))
                .rightColor(slotColor)
                .build());

            // Buy limit reset countdown
            if (isBuying)
            {
                recordBuyStart(itemId);
            }
            Duration limitRemaining = getBuyLimitRemaining(itemId);
            if (limitRemaining != null)
            {
                long mins = limitRemaining.toMinutes();
                String limitStr = mins >= 60
                    ? String.format("%dh%02dm", mins / 60, mins % 60)
                    : mins + "m";
                int geLimit = agg.getBuyLimit();
                int remaining = geLimit > 0 ? Math.max(0, geLimit - quantitySold) : 0;
                String limitLine = remaining > 0
                    ? String.format("  Limit: %d left · resets %s", remaining, limitStr)
                    : String.format("  Limit resets in %s", limitStr);
                panelComponent.getChildren().add(LineComponent.builder()
                    .left(limitLine)
                    .right("")
                    .leftColor(mins < 30 ? WARNING_AMBER : META_NEUTRAL)
                    .build());
            }

            // Drift alert: warn when market price has moved away from offer price
            long driftGp;
            String driftLabel;
            if (isBuying)
            {
                long marketBuy = agg.getBestLowPrice();
                driftGp = price - marketBuy;
                driftLabel = driftGp > 0 ? "  Overpaying by " + formatGp(driftGp)
                    : driftGp < 0 ? "  Underpriced by " + formatGp(-driftGp) : null;
            }
            else
            {
                long marketSell = agg.getBestHighPrice();
                driftGp = marketSell - price;
                driftLabel = driftGp > 0 ? "  Underselling by " + formatGp(driftGp)
                    : driftGp < 0 ? "  Above market by " + formatGp(-driftGp) : null;
            }
            if (driftLabel != null && Math.abs(driftGp) > Math.max(50, price * 0.01))
            {
                // driftGp is defined per side so a positive value is always the
                // money-losing case (overpaying when buying, underselling when selling).
                boolean bad = driftGp > 0;
                panelComponent.getChildren().add(LineComponent.builder()
                    .left(driftLabel)
                    .right("")
                    .leftColor(bad ? LOSS_RED : PROFIT_GREEN)
                    .build());
            }
        }
    }

    /**
     * Record that a buy offer was placed for an item. If no existing window is
     * active, starts the 4-hour countdown.
     */
    public void recordBuyStart(int itemId)
    {
        buyLimitStartTimes.computeIfAbsent(itemId, k -> Instant.now());
        buyLimitStartTimes.entrySet().removeIf(e ->
            Duration.between(e.getValue(), Instant.now()).compareTo(BUY_LIMIT_WINDOW) > 0);
    }

    /**
     * Get remaining time until buy limit resets for an item, or null if no active window.
     */
    public Duration getBuyLimitRemaining(int itemId)
    {
        Instant start = buyLimitStartTimes.get(itemId);
        if (start == null) return null;
        Duration elapsed = Duration.between(start, Instant.now());
        if (elapsed.compareTo(BUY_LIMIT_WINDOW) >= 0)
        {
            buyLimitStartTimes.remove(itemId);
            return null;
        }
        return BUY_LIMIT_WINDOW.minus(elapsed);
    }

    private String truncate(String s, int maxLen)
    {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + ".." : s;
    }

    /**
     * Format slot timer elapsed seconds into compact display (Nm, Nh format).
     */
    private String formatSlotTimer(long seconds)
    {
        if (seconds < 60)
        {
            return seconds + "s";
        }
        if (seconds < 3600)
        {
            return (seconds / 60) + "m";
        }
        return (seconds / 3600) + "h" + ((seconds % 3600) / 60) + "m";
    }

    private String formatGp(long amount)
    {
        return GP_FORMAT.format(amount) + " gp";
    }
}
