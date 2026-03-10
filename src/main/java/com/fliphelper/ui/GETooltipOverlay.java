package com.fliphelper.ui;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import net.runelite.api.Client;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import javax.inject.Inject;
import net.runelite.api.Point;
import java.awt.*;

/**
 * Shows margin/ROI when hovering items in the GE interface.
 *
 * Uses RuneLite's WidgetItemOverlay + TooltipManager pattern (same as ItemStatPlugin).
 * Renders on GRAND_EXCHANGE_INVENTORY to catch items in the GE side panel.
 *
 * Renders margin data as hover tooltips for quick at-a-glance info.
 * FU shows it in a panel. We show it RIGHT WHERE YOU'RE LOOKING.
 */
public class GETooltipOverlay extends WidgetItemOverlay
{
    private final Client client;
    private final GrandFlipOutConfig config;
    private final PriceService priceService;
    private final TooltipManager tooltipManager;

    @Inject
    public GETooltipOverlay(Client client, GrandFlipOutConfig config,
                            PriceService priceService, TooltipManager tooltipManager)
    {
        this.client = client;
        this.config = config;
        this.priceService = priceService;
        this.tooltipManager = tooltipManager;
        showOnInterfaces(InterfaceID.GRAND_EXCHANGE);
    }

    @Override
    public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem widgetItem)
    {
        if (!config.showMarginInTooltip()) return;
        if (client.isMenuOpen()) return;

        // Check if mouse is over this item
        Rectangle bounds = widgetItem.getCanvasBounds();
        if (bounds == null) return;
        Point mouse = client.getMouseCanvasPosition();
        if (mouse == null || !bounds.contains(mouse.getX(), mouse.getY())) return;

        PriceAggregate agg = priceService.getPrice(itemId);
        if (agg == null) return;

        long buy = agg.getBestLowPrice();
        long sell = agg.getBestHighPrice();
        long margin = agg.getConsensusMargin();
        double marginPct = agg.getConsensusMarginPercent();
        long tax = Math.min((long)(sell * 0.02), 5_000_000);
        long profitPerItem = margin - tax;
        int limit = agg.getBuyLimit();

        // Build tooltip using RuneLite's color tag format
        StringBuilder sb = new StringBuilder();
        sb.append("<col=ffb800>Grand Flip Out</col>");
        sb.append("</br>Buy: <col=00ff00>").append(fmtGp(buy)).append("</col>");
        sb.append(" | Sell: <col=ff4444>").append(fmtGp(sell)).append("</col>");
        sb.append("</br>Margin: <col=ffb800>").append(fmtGp(margin));
        sb.append("</col> (").append(String.format("%.1f%%", marginPct)).append(")");
        sb.append("</br>After tax: <col=");
        sb.append(profitPerItem > 0 ? "00ff00" : "ff4444").append(">");
        sb.append(fmtGp(profitPerItem)).append("</col>/item");
        if (limit > 0) sb.append("</br>Limit: ").append(String.format("%,d", limit)).append("/4h");

        tooltipManager.add(new Tooltip(sb.toString()));
    }

    private String fmtGp(long gp)
    {
        if (Math.abs(gp) >= 1_000_000) return String.format("%.1fM", gp / 1e6);
        if (Math.abs(gp) >= 1_000) return String.format("%.0fK", gp / 1e3);
        return gp + "gp";
    }
}
