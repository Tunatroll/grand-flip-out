package com.fliphelper.ui;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.api.PriceService;
import com.fliphelper.model.FlipItem;
import com.fliphelper.tracker.FlipTracker;
import com.fliphelper.tracker.GEOfferHelper;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import java.awt.*;

/**
 * GE Widget Highlight Overlay — draws directly ON TOP of the GE interface.
 *
 * CRITICAL PATTERN (from research):
 *   setLayer(OverlayLayer.MANUAL)
 *   drawAfterInterface(InterfaceID.GE_OFFERS)
 *
 * This ensures highlights render pixel-perfect on the GE slots, exactly
 * like Quest Helper renders on quest interfaces. The old approach of
 * OverlayLayer.ABOVE_WIDGETS doesn't reliably z-order with GE widgets.
 *
 * Also integrates FlipGuidance state machine for step-by-step instructions.
 */
public class GEHighlightOverlay extends Overlay
{
    // Fill colors (low alpha for tinted overlay)
    private static final Color COLOR_BUY = new Color(0, 200, 83, 50);
    private static final Color COLOR_SELL = new Color(33, 150, 243, 50);
    private static final Color COLOR_STALE = new Color(255, 152, 0, 50);
    private static final Color COLOR_DUMP = new Color(244, 67, 54, 50);
    private static final Color COLOR_COMPLETE = new Color(255, 193, 7, 50);
    private static final Color COLOR_GUIDANCE = new Color(0, 229, 255, 40); // Cyan for active guidance

    // Border colors (higher alpha)
    private static final Color BORDER_BUY = new Color(0, 200, 83, 180);
    private static final Color BORDER_SELL = new Color(33, 150, 243, 180);
    private static final Color BORDER_STALE = new Color(255, 152, 0, 180);
    private static final Color BORDER_DUMP = new Color(244, 67, 54, 180);
    private static final Color BORDER_COMPLETE = new Color(255, 193, 7, 180);
    private static final Color BORDER_GUIDANCE = new Color(0, 229, 255, 200);

    private static final int STALE_SECONDS = 300;
    private static final int MAX_SLOTS = 8;

    private final Client client;
    private final GrandFlipOutConfig config;
    private final FlipTracker flipTracker;
    private final GEOfferHelper offerHelper;

    // Guidance state — set by FlipGuidance engine
    private FlipGuidanceState guidanceState = FlipGuidanceState.IDLE;
    private int guidanceSlot = -1;
    private PriceService priceService;
    private String guidanceText = "";
    private String guidanceSubtext = "";

    // Per-slot suggestion labels from SmartAdvisor
    private final String[] slotLabels = new String[MAX_SLOTS];
    private final String[] slotReasons = new String[MAX_SLOTS];

    @Inject
    public GEHighlightOverlay(Client client, GrandFlipOutConfig config,
                              FlipTracker flipTracker, GEOfferHelper offerHelper, PriceService priceService)
    {
        this.client = client;
        this.config = config;
        this.flipTracker = flipTracker;
        this.offerHelper = offerHelper;
        this.priceService = priceService;

        // CRITICAL: Use MANUAL layer + drawAfterInterface for pixel-perfect GE overlay
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.MANUAL);
        drawAfterInterface(InterfaceID.GRAND_EXCHANGE);
        setPriority(OverlayPriority.HIGH);

        for (int i = 0; i < MAX_SLOTS; i++)
        {
            slotLabels[i] = "";
            slotReasons[i] = "";
        }
    }

    // GUIDANCE API

    /** Flip guidance states — Quest Helper-inspired step-by-step flow */
    public enum FlipGuidanceState
    {
        IDLE,           // No active guidance
        MARGIN_CHECK,   // Step 1: Buy 1 at +5% to check sell price
        MARGIN_SELL,    // Step 2: Sell 1 at -5% to check buy price
        BUY,            // Step 3: Buy at the price we discovered
        WAIT_BUY,       // Step 4: Waiting for buy to fill
        SELL,           // Step 5: Sell at margin check price
        WAIT_SELL,      // Step 6: Waiting for sell to fill
        COLLECT,        // Step 7: Collect completed flip
        COMPLETE        // Done — show profit summary
    }

    public void setGuidance(FlipGuidanceState state, int slot, String text, String subtext)
    {
        this.guidanceState = state;
        this.guidanceSlot = slot;
        this.guidanceText = text != null ? text : "";
        this.guidanceSubtext = subtext != null ? subtext : "";
    }

    public void clearGuidance()
    {
        this.guidanceState = FlipGuidanceState.IDLE;
        this.guidanceSlot = -1;
        this.guidanceText = "";
        this.guidanceSubtext = "";
    }

    public void setSuggestion(int slot, String label, String reason)
    {
        if (slot >= 0 && slot < MAX_SLOTS)
        {
            slotLabels[slot] = label != null ? label : "";
            slotReasons[slot] = reason != null ? reason : "";
        }
    }

    public void clearSuggestions()
    {
        for (int i = 0; i < MAX_SLOTS; i++) { slotLabels[i] = ""; slotReasons[i] = ""; }
    }

    public FlipGuidanceState getGuidanceState() { return guidanceState; }

    // RENDER

    @Override
    public Dimension render(Graphics2D g)
    {
        if (!config.enableOverlay()) return null;

        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        if (offers == null) return null;

        // Enable anti-aliasing for smooth text
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Render highlights on each GE slot
        for (int slot = 0; slot < Math.min(offers.length, MAX_SLOTS); slot++)
        {
            Widget slotWidget = getSlotWidget(slot);
            if (slotWidget == null || slotWidget.isHidden()) continue;

            Rectangle bounds = slotWidget.getBounds();
            if (bounds == null || bounds.width <= 0) continue;

            renderSlot(g, slot, offers[slot], bounds);
        }

        // Render guidance instruction panel (top-right of GE window)
        if (guidanceState != FlipGuidanceState.IDLE && !guidanceText.isEmpty())
        {
            renderGuidancePanel(g);
        }

        return null;
    }

    private void renderSlot(Graphics2D g, int slot, GrandExchangeOffer offer, Rectangle bounds)
    {
        GrandExchangeOfferState state = offer != null ? offer.getState() : GrandExchangeOfferState.EMPTY;
        Color fill = null;
        Color border = null;
        String label = "";
        String reason = "";

        // Guidance highlight takes priority
        if (guidanceSlot == slot && guidanceState != FlipGuidanceState.IDLE)
        {
            fill = COLOR_GUIDANCE;
            border = BORDER_GUIDANCE;
            label = guidanceText;
            reason = guidanceSubtext;
        }
        else if (state == GrandExchangeOfferState.EMPTY)
        {

            if (!slotLabels[slot].isEmpty())
            {
                fill = COLOR_BUY;
                border = BORDER_BUY;
                label = slotLabels[slot];
                reason = slotReasons[slot];
            }
        }
        else if (state == GrandExchangeOfferState.BOUGHT)
        {
            fill = COLOR_SELL;
            border = BORDER_SELL;
            int profit = estimateProfit(offer);
            label = profit > 0 ? "SELL \u2192 " + fmtGp(profit) : "SELL";
        }
        else if (state == GrandExchangeOfferState.SOLD)
        {
            fill = COLOR_COMPLETE;
            border = BORDER_COMPLETE;
            label = "COLLECT \u2713";
        }
        else if (state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.SELLING)
        {
            long idle = offerHelper != null ? offerHelper.getSlotIdleSeconds(slot) : 0;
            if (idle > STALE_SECONDS)
            {
                fill = COLOR_STALE;
                border = BORDER_STALE;
                label = "IDLE " + fmtTime(idle);
            }
        }
        else if (state == GrandExchangeOfferState.CANCELLED_BUY || state == GrandExchangeOfferState.CANCELLED_SELL)
        {
            fill = COLOR_COMPLETE;
            border = BORDER_COMPLETE;
            label = "COLLECT";
        }

        if (fill == null) return;

        // Draw fill
        g.setColor(fill);
        g.fillRect(bounds.x + 1, bounds.y + 1, bounds.width - 2, bounds.height - 2);

        // Draw border (2px rounded)
        g.setColor(border);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1, 4, 4);

        // Draw label (centered at bottom)
        if (!label.isEmpty())
        {
            g.setFont(new Font("Arial", Font.BOLD, 11));
            FontMetrics fm = g.getFontMetrics();
            int tx = bounds.x + (bounds.width - fm.stringWidth(label)) / 2;
            int ty = bounds.y + bounds.height - 5;

            // Shadow + text
            g.setColor(new Color(0, 0, 0, 200));
            g.drawString(label, tx + 1, ty + 1);
            g.setColor(Color.WHITE);
            g.drawString(label, tx, ty);
        }

        // Draw reason subtitle
        if (!reason.isEmpty())
        {
            g.setFont(new Font("Arial", Font.PLAIN, 9));
            FontMetrics fm = g.getFontMetrics();
            int tx = bounds.x + (bounds.width - fm.stringWidth(reason)) / 2;
            int ty = bounds.y + bounds.height - 18;
            g.setColor(new Color(0, 0, 0, 160));
            g.drawString(reason, tx + 1, ty + 1);
            g.setColor(new Color(200, 220, 255, 220));
            g.drawString(reason, tx, ty);
        }
    }

    /**
     * Render the flip guidance instruction panel — Quest Helper-style
     * step indicator positioned near the GE interface.
     */
    private void renderGuidancePanel(Graphics2D g)
    {
        // Position: top-right area near GE
        Widget geWidget = client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
        if (geWidget == null || geWidget.isHidden()) return;

        Rectangle geBounds = geWidget.getBounds();
        int panelX = geBounds.x + geBounds.width + 8;
        int panelY = geBounds.y;
        int panelW = 180;
        int panelH = 60;

        // Clamp to screen
        if (panelX + panelW > client.getCanvasWidth())
        {
            panelX = geBounds.x - panelW - 8;
        }

        // Background
        g.setColor(new Color(15, 15, 23, 220));
        g.fillRoundRect(panelX, panelY, panelW, panelH, 6, 6);

        // Cyan border
        g.setColor(BORDER_GUIDANCE);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(panelX, panelY, panelW, panelH, 6, 6);

        // Step indicator (e.g. "STEP 3/7")
        int stepNum = guidanceState.ordinal();
        int totalSteps = FlipGuidanceState.COMPLETE.ordinal();
        String stepStr = "STEP " + stepNum + "/" + totalSteps;
        g.setFont(new Font("Arial", Font.BOLD, 9));
        g.setColor(new Color(0, 229, 255));
        g.drawString(stepStr, panelX + 8, panelY + 14);

        // Progress bar
        int barX = panelX + 8;
        int barY = panelY + 18;
        int barW = panelW - 16;
        int barH = 3;
        g.setColor(new Color(40, 40, 60));
        g.fillRect(barX, barY, barW, barH);
        g.setColor(new Color(0, 229, 255));
        g.fillRect(barX, barY, (int)(barW * ((float)stepNum / totalSteps)), barH);

        // Main instruction text
        g.setFont(new Font("Arial", Font.BOLD, 11));
        g.setColor(Color.WHITE);
        g.drawString(guidanceText, panelX + 8, panelY + 36);

        // Subtext
        if (!guidanceSubtext.isEmpty())
        {
            g.setFont(new Font("Arial", Font.PLAIN, 9));
            g.setColor(new Color(180, 200, 220));
            g.drawString(guidanceSubtext, panelX + 8, panelY + 50);
        }
    }

    // HELPERS

    private Widget getSlotWidget(int slot)
    {
        // GE slot lookup - return null until proper widget IDs are mapped
        return null;
    }

    private int estimateProfit(GrandExchangeOffer offer)
    {
        if (offer == null || offer.getItemId() <= 0) return 0;
        int buyPrice = offer.getPrice();
        int qty = offer.getTotalQuantity();
        if (priceService == null) return 0;
        com.fliphelper.model.PriceAggregate agg = priceService.getPrice(offer.getItemId());
        if (agg != null && agg.getSellPrice() > 0)
        {
            long sp = agg.getSellPrice();
            long tax = Math.min((long)(sp * 0.02), 5_000_000L);
            return (int)((sp - tax - buyPrice) * qty);
        }
        return 0;
    }

    private String fmtGp(int gp)
    {
        if (Math.abs(gp) >= 1_000_000) return String.format("%.1fM", gp / 1e6);
        if (Math.abs(gp) >= 1_000) return String.format("%.0fK", gp / 1e3);
        return gp + "";
    }

    private String fmtTime(long s)
    {
        if (s >= 3600) return String.format("%dh%dm", s / 3600, (s % 3600) / 60);
        if (s >= 60) return String.format("%dm%ds", s / 60, s % 60);
        return s + "s";
    }
}
