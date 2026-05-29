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
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
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
    private static final NumberFormat GP_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
    private final Client client;
    private final GrandFlipOutConfig config;
    private final PriceService priceService;
    private final FlipTracker flipTracker;
    private final SessionManager sessionManager;
    private final PanelComponent panelComponent = new PanelComponent();

    /** Slot activity timestamps for slot timer display (like Flipping Utilities). */
    private final Instant[] slotLastActive = new Instant[8];

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
        setPriority(Overlay.PRIORITY_MED);
    }

    public void toggleVisibility()
    {
        visible = !visible;
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
            Widget geWidget = client.getWidget(InterfaceID.GeOffers.UNIVERSE);
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
                long totalRevenue = expectedSell * flip.getQuantity();
                long tax = Math.min((long) (totalRevenue * 0.02), 5_000_000L);
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
            }
        }
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
                long totalRevenue = currentPrice * quantitySold;
                long tax = Math.min((long)(totalRevenue * 0.02), 5_000_000L);
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
        }
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
