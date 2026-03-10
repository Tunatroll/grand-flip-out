package com.fliphelper.ui;

import com.fliphelper.AwfullyPureConfig;
import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import net.runelite.api.Client;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import javax.inject.Inject;
import net.runelite.api.Point;
import java.awt.*;

/**
 * GE Item Tooltip — shows margin, ROI, volume, and buy limit when hovering
 * items in the GE inventory panel.
 *
 * Uses RuneLite's WidgetItemOverlay + TooltipManager pattern, exactly like
 * the ItemStatPlugin shows food healing values on hover.
 *
 * Shows margin, ROI, and volume as hover tooltips directly in the GE interface.
 */
public class GEItemTooltipOverlay extends WidgetItemOverlay
{
    private final Client client;
    private final AwfullyPureConfig config;
    private final PriceService priceService;
    private final TooltipManager tooltipManager;
    private final ItemManager itemManager;

    @Inject
    public GEItemTooltipOverlay(Client client, AwfullyPureConfig config,
                                PriceService priceService, TooltipManager tooltipManager,
                                ItemManager itemManager)
    {
        this.client = client;
        this.config = config;
        this.priceService = priceService;
        this.tooltipManager = tooltipManager;
        this.itemManager = itemManager;

        // Register for GE inventory panel AND bank
        showOnInterfaces(
            InterfaceID.GRAND_EXCHANGE_INVENTORY,
            InterfaceID.BANK,
            InterfaceID.BANK_INVENTORY
        );
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
    {
        if (!config.showMarginInTooltip()) return;
        if (client.isMenuOpen()) return; // Don't show tooltip when right-click menu is open

        // Check if mouse is over this item
        Rectangle bounds = widgetItem.getCanvasBounds();
        if (bounds == null) return;
        Point mouse = client.getMouseCanvasPosition();
        if (mouse == null || !bounds.contains(mouse.getX(), mouse.getY())) return;

        // Look up real item ID (handle noted items)
        int realItemId = itemManager.canonicalize(itemId);
        PriceAggregate agg = priceService.getPrice(realItemId);
        if (agg == null) return;

        // Build tooltip text
        long buyPrice = agg.getBestLowPrice();
        long sellPrice = agg.getBestHighPrice();
        long margin = agg.getConsensusMargin();
        double marginPct = agg.getConsensusMarginPercent();
        long tax = Math.min((long)(sellPrice * 0.02), 5_000_000);
        long profitAfterTax = margin - tax;
        double roi = buyPrice > 0 ? ((double) profitAfterTax / buyPrice) * 100 : 0;
        int buyLimit = agg.getBuyLimit();
        long volume = agg.getVolume();

        StringBuilder sb = new StringBuilder();
        sb.append("</br>");
        sb.append("<col=ffb800>AP Flip Data</col></br>");
        sb.append("Buy: <col=ffffff>").append(formatGp(buyPrice)).append("</col> ");
        sb.append("Sell: <col=ffffff>").append(formatGp(sellPrice)).append("</col></br>");

        // Color margin green/red
        String marginColor = profitAfterTax > 0 ? "00d26a" : "ff4757";
        sb.append("Margin: <col=").append(marginColor).append(">")
          .append(formatGp(margin)).append(" (").append(String.format("%.1f%%", marginPct)).append(")")
          .append("</col></br>");

        sb.append("After tax: <col=").append(marginColor).append(">")
          .append(formatGp(profitAfterTax)).append("</col>");
        sb.append(" ROI: <col=").append(marginColor).append(">")
          .append(String.format("%.1f%%", roi)).append("</col></br>");

        sb.append("Vol: <col=ffffff>").append(formatVol(volume)).append("</col> ");
        if (buyLimit > 0) sb.append("Limit: <col=ffffff>").append(String.format("%,d", buyLimit)).append("/4h</col>");

        tooltipManager.add(new Tooltip(sb.toString()));
    }

    private String formatGp(long amount)
    {
        if (Math.abs(amount) >= 1_000_000) return String.format("%.1fM", amount / 1_000_000.0);
        if (Math.abs(amount) >= 1_000) return String.format("%.0fK", amount / 1_000.0);
        return String.valueOf(amount);
    }

    private String formatVol(long vol)
    {
        if (vol >= 1_000_000) return String.format("%.1fM", vol / 1_000_000.0);
        if (vol >= 1_000) return String.format("%.0fK", vol / 1_000.0);
        return String.valueOf(vol);
    }
}
