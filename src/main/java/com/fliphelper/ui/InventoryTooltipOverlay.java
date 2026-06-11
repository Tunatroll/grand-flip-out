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
import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Overlay that shows flip-relevant price tooltips when hovering inventory
 * items while the Grand Exchange interface is open.
 *
 * <p>Displays: Wiki buy/sell prices, cost basis (average buy from active flips),
 * recommended sell price (high minus 2% GE tax, capped 5M), and live ROI %
 * when cost basis is known.</p>
 */
public class InventoryTooltipOverlay extends Overlay
{
    private static final NumberFormat GP_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private final Client client;
    private final GrandFlipOutConfig config;
    private final PriceService priceService;
    private final FlipTracker flipTracker;
    private final TooltipManager tooltipManager;

    @Inject
    public InventoryTooltipOverlay(Client client, GrandFlipOutConfig config,
                                   PriceService priceService, FlipTracker flipTracker,
                                   TooltipManager tooltipManager)
    {
        this.client = client;
        this.config = config;
        this.priceService = priceService;
        this.flipTracker = flipTracker;
        this.tooltipManager = tooltipManager;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showInventoryTooltips())
        {
            return null;
        }

        // Only show when GE is open
        Widget geWidget = client.getWidget(ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER);
        if (geWidget == null || geWidget.isHidden())
        {
            return null;
        }

        // Find the inventory container widget while GE is open
        // GE inventory items container: group 467, child 0
        Widget inventoryWidget = client.getWidget(467, 0);
        if (inventoryWidget == null || inventoryWidget.isHidden())
        {
            return null;
        }

        Widget[] children = inventoryWidget.getDynamicChildren();
        if (children == null)
        {
            return null;
        }

        net.runelite.api.Point mousePos = client.getMouseCanvasPosition();
        if (mousePos == null)
        {
            return null;
        }

        for (Widget child : children)
        {
            if (child == null || child.isHidden() || child.getItemId() < 0)
            {
                continue;
            }

            Rectangle bounds = child.getBounds();
            if (bounds == null || !bounds.contains(mousePos.getX(), mousePos.getY()))
            {
                continue;
            }

            int itemId = child.getItemId();
            if (itemId <= 0)
            {
                continue;
            }

            PriceAggregate agg = priceService.getPrice(itemId);
            if (agg == null)
            {
                continue;
            }

            String tooltip = buildTooltip(itemId, agg);
            if (tooltip != null)
            {
                tooltipManager.add(new Tooltip(tooltip));
            }

            // Only one item can be hovered at a time
            break;
        }

        return null;
    }

    private String buildTooltip(int itemId, PriceAggregate agg)
    {
        long buyPrice = agg.getBestLowPrice();
        long sellPrice = agg.getBestHighPrice();

        if (buyPrice <= 0 && sellPrice <= 0)
        {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<col=ffb800>").append(agg.getItemName()).append("</col>");

        if (buyPrice > 0)
        {
            sb.append("</br>Buy: ").append(formatGp(buyPrice));
        }
        if (sellPrice > 0)
        {
            sb.append("</br>Sell: ").append(formatGp(sellPrice));
        }

        // Recommended sell = high price minus 2% GE tax (capped 5M)
        if (sellPrice > 0)
        {
            long tax = Math.min((long) (sellPrice * 0.02), 5_000_000L);
            long recSell = sellPrice - tax;
            sb.append("</br>Net sell: ").append(formatGp(recSell));
        }

        // Cost basis from active flips
        long costBasis = flipTracker.getAverageBuyPrice(itemId);
        if (costBasis > 0)
        {
            sb.append("</br><col=d4af37>Cost basis: ").append(formatGp(costBasis)).append("</col>");

            // Live ROI
            if (sellPrice > 0)
            {
                long tax = Math.min((long) (sellPrice * 0.02), 5_000_000L);
                long netSell = sellPrice - tax;
                double roi = (double) (netSell - costBasis) / costBasis * 100.0;
                String roiColor = roi >= 0 ? "00d26a" : "ff4757";
                String roiPrefix = roi >= 0 ? "+" : "";
                sb.append("</br><col=").append(roiColor).append(">ROI: ")
                    .append(roiPrefix).append(String.format("%.1f%%", roi))
                    .append("</col>");
            }
        }

        // Frozen sell price if available
        for (com.fliphelper.model.FlipItem flip : flipTracker.getActiveFlips().values())
        {
            if (flip.getItemId() == itemId && flip.getFrozenSellPrice() > 0)
            {
                sb.append("</br><col=d4af37>Frozen sell: ").append(formatGp(flip.getFrozenSellPrice())).append("</col>");
                break;
            }
        }

        return sb.toString();
    }

    private String formatGp(long amount)
    {
        return GP_FORMAT.format(amount) + " gp";
    }
}
