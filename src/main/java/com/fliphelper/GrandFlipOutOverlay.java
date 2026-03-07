package com.fliphelper;

import com.fliphelper.api.PriceService;
import com.fliphelper.model.FlipItem;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.tracker.FlipTracker;
import net.runelite.api.Client;
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
import java.awt.*;

/**
 * In-game overlay displayed when the Grand Exchange interface is open.
 * Shows current item margins, flip status, and session profit.
 */
public class GrandFlipOutOverlay extends Overlay
{
    private final Client client;
    private final GrandFlipOutConfig config;
    private final PriceService priceService;
    private final FlipTracker flipTracker;
    private final PanelComponent panelComponent = new PanelComponent();

    private boolean visible = true;

    @Inject
    public GrandFlipOutOverlay(Client client, GrandFlipOutConfig config,
                              PriceService priceService, FlipTracker flipTracker)
    {
        this.client = client;
        this.config = config;
        this.priceService = priceService;
        this.flipTracker = flipTracker;

        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.MED);
    }

    public void toggleVisibility()
    {
        visible = !visible;
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

        Widget geWidget = client.getWidget(ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER);
        boolean geOpen = geWidget != null && !geWidget.isHidden();

        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(200, 0));

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

    private void renderSessionStats()
    {
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Grand Flip Out")
            .color(Color.CYAN)
            .build());

        long profit = flipTracker.getSessionProfit().get();
        // Color coding: green for profit, orange for break-even, red for loss
        Color profitColor;
        if (profit > 0)
        {
            profitColor = Color.GREEN;
        }
        else if (profit == 0)
        {
            profitColor = new Color(0xFF, 0xA5, 0x00); // Orange
        }
        else
        {
            profitColor = Color.RED;
        }

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Session Profit:")
            .right(formatGp(profit))
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
            avgColor = Color.GREEN;
        }
        else if (avg == 0)
        {
            avgColor = new Color(0xFF, 0xA5, 0x00); // Orange
        }
        else
        {
            avgColor = Color.RED;
        }

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Avg/Flip:")
            .right(formatGp((long) avg))
            .rightColor(avgColor)
            .build());

        // Show current margin clearly
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Margin ROI:")
            .right(String.format("%.1f%%", flipTracker.getAverageProfitPerFlip() > 0 ?
                (flipTracker.getAverageProfitPerFlip() / Math.max(1, flipTracker.getSessionFlipCount().get())) : 0))
            .build());

        // Show active flips count
        int activeCount = flipTracker.getActiveFlips().size();
        if (activeCount > 0)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Active Flips:")
                .right(String.valueOf(activeCount))
                .rightColor(Color.YELLOW)
                .build());
        }
    }

    private void renderGEInfo()
    {
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("GE Slots")
            .color(Color.ORANGE)
            .build());

        // Show info for active flip items
        for (FlipItem flip : flipTracker.getActiveFlips().values())
        {
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
                .rightColor(Color.YELLOW)
                .build());

            long margin = agg.getConsensusMargin();
            Color marginColor;
            if (margin > 0)
            {
                marginColor = Color.GREEN;
            }
            else if (margin == 0)
            {
                marginColor = new Color(0xFF, 0xA5, 0x00); // Orange for break-even
            }
            else
            {
                marginColor = Color.RED;
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
            long expectedProfit = (expectedSell - flip.getBuyPrice()) * flip.getQuantity();
            long tax = Math.min((long) (expectedSell * 0.02), 5_000_000L) * flip.getQuantity();
            expectedProfit -= tax;

            Color profitColor;
            if (expectedProfit > 0)
            {
                profitColor = Color.GREEN;
            }
            else if (expectedProfit == 0)
            {
                profitColor = new Color(0xFF, 0xA5, 0x00); // Orange
            }
            else
            {
                profitColor = Color.RED;
            }

            panelComponent.getChildren().add(LineComponent.builder()
                .left("  Exp. Profit:")
                .right(formatGp(expectedProfit))
                .rightColor(profitColor)
                .build());
        }
    }

    private String formatGp(long amount)
    {
        if (Math.abs(amount) >= 1_000_000)
        {
            return String.format("%.1fm", amount / 1_000_000.0);
        }
        if (Math.abs(amount) >= 1_000)
        {
            return String.format("%.1fk", amount / 1_000.0);
        }
        return amount + " gp";
    }
}
