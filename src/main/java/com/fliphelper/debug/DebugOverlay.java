package com.fliphelper.debug;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.api.PriceService;
import com.fliphelper.tracker.FlipTracker;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;


@Slf4j
public class DebugOverlay extends Overlay
{
    private final GrandFlipOutConfig config;
    private final PriceService priceService;
    private final FlipTracker flipTracker;
    private final DebugManager debugManager;
    private final PanelComponent panelComponent = new PanelComponent();

    private long lastRenderDuration = 0;
    private Instant lastApiCallTime = Instant.EPOCH;
    private int apiErrorCount = 0;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    @Inject
    public DebugOverlay(GrandFlipOutConfig config, PriceService priceService,
                        FlipTracker flipTracker, DebugManager debugManager)
    {
        this.config = config;
        this.priceService = priceService;
        this.flipTracker = flipTracker;
        this.debugManager = debugManager;

        setPosition(OverlayPosition.TOP_RIGHT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    @Nullable
    public Dimension render(Graphics2D graphics)
    {
        // Check if debug overlay is enabled in config
        if (!config.enableDebugOverlay())
        {
            return null;
        }

        long startTime = System.currentTimeMillis();

        try
        {
            panelComponent.getChildren().clear();
            panelComponent.setPreferredSize(new Dimension(220, 0));

            // Title
            panelComponent.getChildren().add(TitleComponent.builder()
                .text("Debug Info")
                .color(Color.CYAN)
                .build());

            // API Status
            addApiStatusLine();

            // Memory Usage
            addMemoryUsageLine();

            // Price Data Age
            addPriceDataAgeLine();

            // Active Flips
            addActiveFlipsLine();

            // Items Tracked
            addItemsTrackedLine();

            // FPS Impact
            addFpsImpactLine();

            // Render the panel
            return panelComponent.render(graphics);
        }
        finally
        {
            lastRenderDuration = System.currentTimeMillis() - startTime;
        }
    }

    private void addApiStatusLine()
    {
        // Show last successful API call and error count
        String apiStatus = lastApiCallTime == Instant.EPOCH
            ? "Never"
            : TIME_FORMATTER.format(lastApiCallTime);

        String statusLabel = apiErrorCount > 0 ? "ERR" : "OK";
        Color statusColor = apiErrorCount > 0 ? Color.RED : Color.GREEN;

        panelComponent.getChildren().add(LineComponent.builder()
            .left("API [" + statusLabel + "]:")
            .leftColor(statusColor)
            .right(apiStatus + " (Err: " + apiErrorCount + ")")
            .rightColor(statusColor)
            .build());
    }

    private void addMemoryUsageLine()
    {
        Runtime runtime = Runtime.getRuntime();
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        long heapMax = runtime.maxMemory();

        long usedMb = heapUsed / (1024 * 1024);
        long maxMb = heapMax / (1024 * 1024);
        double usagePercent = (100.0 * heapUsed) / heapMax;

        Color memoryColor;
        if (usagePercent > 80)
        {
            memoryColor = Color.RED;
        }
        else if (usagePercent > 60)
        {
            memoryColor = new Color(0xFF, 0xA5, 0x00); // Orange
        }
        else
        {
            memoryColor = Color.GREEN;
        }

        String memLabel = usagePercent > 80 ? "WARN" : "OK";
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Mem [" + memLabel + "]:")
            .leftColor(memoryColor)
            .right(String.format("%d/%d MB (%.0f%%)", usedMb, maxMb, usagePercent))
            .rightColor(memoryColor)
            .build());
    }

    private void addPriceDataAgeLine()
    {
        Instant lastRefresh = priceService.getLastRefresh();
        long ageSeconds = Instant.now().getEpochSecond() - lastRefresh.getEpochSecond();

        String ageLabel = ageSeconds > 120 ? "STALE" : "OK";
        Color ageColor = ageSeconds > 120 ? Color.RED : Color.GREEN;

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Data [" + ageLabel + "]:")
            .leftColor(ageColor)
            .right(ageSeconds + "s")
            .rightColor(ageColor)
            .build());
    }

    private void addActiveFlipsLine()
    {
        int activeCount = flipTracker.getActiveFlips().size();
        Color flipsColor = activeCount > 0 ? Color.YELLOW : Color.GRAY;

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Active Flips:")
            .right(String.valueOf(activeCount))
            .rightColor(flipsColor)
            .build());
    }

    private void addItemsTrackedLine()
    {
        int itemsCount = priceService.getAggregatedPrices().size();

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Items Tracked:")
            .right(String.valueOf(itemsCount))
            .build());
    }

    private void addFpsImpactLine()
    {
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Render Time:")
            .right(lastRenderDuration + " ms")
            .build());
    }

    
    public void recordAPICall(boolean success)
    {
        this.lastApiCallTime = Instant.now();
        if (!success)
        {
            apiErrorCount++;
        }
    }

    
    public void resetApiErrorCount()
    {
        apiErrorCount = 0;
    }
}
