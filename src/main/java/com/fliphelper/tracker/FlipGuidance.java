package com.fliphelper.tracker;

import com.fliphelper.AwfullyPureConfig;
import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.ui.GEHighlightOverlay;
import com.fliphelper.ui.GEHighlightOverlay.FlipGuidanceState;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * FlipGuidance — Quest Helper-style step-by-step flip walkthrough.
 *
 * Inspired by Quest Helper's ConditionalStep architecture:
 * - Each state checks requirements (gold, slots, GE state)
 * - Transitions happen automatically when conditions are met
 * - The overlay renders guidance text + highlighted slot
 *
 * States: IDLE → MARGIN_CHECK → MARGIN_SELL → BUY → WAIT_BUY → SELL → WAIT_SELL → COLLECT → COMPLETE
 *
 * Step-by-step flip walkthrough with visual GE highlights.
 * We give it away free AND show users the reasoning at each step.
 */
@Slf4j
@Singleton
public class FlipGuidance
{
    private final Client client;
    private final AwfullyPureConfig config;
    private final PriceService priceService;
    private GEHighlightOverlay highlightOverlay;

    // Active guidance state
    private FlipGuidanceState state = FlipGuidanceState.IDLE;
    private int activeItemId = -1;
    private String activeItemName = "";
    private int activeSlot = -1;
    private int marginCheckBuyPrice = 0;   // Price we paid for margin check buy
    private int marginCheckSellPrice = 0;  // Price we got for margin check sell
    private int discoveredMargin = 0;
    private long guidanceStartTime = 0;

    @Inject
    public FlipGuidance(Client client, AwfullyPureConfig config, PriceService priceService)
    {
        this.client = client;
        this.config = config;
        this.priceService = priceService;
    }

    public void setHighlightOverlay(GEHighlightOverlay overlay)
    {
        this.highlightOverlay = overlay;
    }

    /**
     * Start guided flip for a specific item.
     * Called when user clicks "Guide Me" button or hotkey.
     */
    public void startGuidedFlip(int itemId, String itemName)
    {
        this.activeItemId = itemId;
        this.activeItemName = itemName;
        this.guidanceStartTime = System.currentTimeMillis();
        this.marginCheckBuyPrice = 0;
        this.marginCheckSellPrice = 0;
        this.discoveredMargin = 0;

        // Find an empty GE slot
        int emptySlot = findEmptySlot();
        if (emptySlot < 0)
        {
            updateOverlay(FlipGuidanceState.IDLE, -1,
                "No empty GE slots!",
                "Cancel an offer to free a slot");
            return;
        }

        this.activeSlot = emptySlot;

        // Get API price for initial guidance
        PriceAggregate agg = priceService.getPrice(itemId);
        long suggestedBuyCheck = agg != null ? (long)(agg.getBestHighPrice() * 1.05) : 0;

        transitionTo(FlipGuidanceState.MARGIN_CHECK);
        updateOverlay(FlipGuidanceState.MARGIN_CHECK, activeSlot,
            "Step 1: Margin Check — Buy 1 " + itemName,
            suggestedBuyCheck > 0
                ? "Buy 1 at " + formatGp(suggestedBuyCheck) + " (+5%) → Press E to auto-fill"
                : "Buy 1 at +5% above market price → Press E");

        log.info("FlipGuidance started for {} (slot {})", itemName, activeSlot);
    }

    /**
     * Called on every GE offer change. Drives state transitions.
     */
    public void onOfferChanged(int slot, GrandExchangeOffer offer)
    {
        if (state == FlipGuidanceState.IDLE || activeItemId < 0) return;
        if (offer == null || offer.getItemId() != activeItemId) return;

        GrandExchangeOfferState offerState = offer.getState();

        switch (state)
        {
            case MARGIN_CHECK:
                // Waiting for margin check buy to complete (1 qty)
                if (offerState == GrandExchangeOfferState.BOUGHT && offer.getTotalQuantity() == 1)
                {
                    marginCheckBuyPrice = offer.getPrice();
                    // Now sell 1 to find buy price
                    transitionTo(FlipGuidanceState.MARGIN_SELL);
                    updateOverlay(FlipGuidanceState.MARGIN_SELL, slot,
                        "Step 2: Sell Check — Sell 1 " + activeItemName,
                        "Sell 1 at 1gp (instant sell) → Press R to auto-fill");
                }
                break;

            case MARGIN_SELL:
                // Waiting for margin check sell to complete
                if (offerState == GrandExchangeOfferState.SOLD && offer.getTotalQuantity() == 1)
                {
                    marginCheckSellPrice = offer.getPrice();
                    discoveredMargin = marginCheckSellPrice - marginCheckBuyPrice;
                    long tax = Math.min((long)(marginCheckSellPrice * 0.02), 5_000_000);
                    long profitPerItem = discoveredMargin - tax;

                    if (profitPerItem <= 0)
                    {
                        updateOverlay(FlipGuidanceState.IDLE, -1,
                            "Margin too thin for " + activeItemName,
                            "Profit after tax: " + formatGp(profitPerItem) + " — try another item");
                        cancelGuidance();
                        return;
                    }

                    // Margin is profitable — suggest the buy
                    PriceAggregate agg = priceService.getPrice(activeItemId);
                    int buyLimit = agg != null ? agg.getBuyLimit() : 100;

                    transitionTo(FlipGuidanceState.BUY);
                    updateOverlay(FlipGuidanceState.BUY, slot,
                        "Step 3: Buy " + activeItemName + " (margin: " + formatGp(discoveredMargin) + ")",
                        "Buy up to " + buyLimit + " at " + formatGp(marginCheckBuyPrice) + " → Press E + Q");
                }
                break;

            case BUY:
                // User should be placing buy offer — watch for BUYING state
                if (offerState == GrandExchangeOfferState.BUYING)
                {
                    transitionTo(FlipGuidanceState.WAIT_BUY);
                    updateOverlay(FlipGuidanceState.WAIT_BUY, slot,
                        "Step 4: Waiting for buy to fill...",
                        offer.getQuantitySold() + "/" + offer.getTotalQuantity() + " filled");
                }
                break;

            case WAIT_BUY:
                // Update fill progress
                if (offerState == GrandExchangeOfferState.BUYING)
                {
                    updateOverlay(FlipGuidanceState.WAIT_BUY, slot,
                        "Step 4: Buying " + activeItemName + "...",
                        offer.getQuantitySold() + "/" + offer.getTotalQuantity() + " filled");
                }
                else if (offerState == GrandExchangeOfferState.BOUGHT)
                {
                    transitionTo(FlipGuidanceState.SELL);
                    updateOverlay(FlipGuidanceState.SELL, slot,
                        "Step 5: Sell " + activeItemName + " at " + formatGp(marginCheckSellPrice),
                        "Sell " + offer.getTotalQuantity() + " items → Press R to auto-fill");
                }
                break;

            case SELL:
                if (offerState == GrandExchangeOfferState.SELLING)
                {
                    transitionTo(FlipGuidanceState.WAIT_SELL);
                    updateOverlay(FlipGuidanceState.WAIT_SELL, slot,
                        "Step 6: Selling " + activeItemName + "...",
                        offer.getQuantitySold() + "/" + offer.getTotalQuantity() + " sold");
                }
                break;

            case WAIT_SELL:
                if (offerState == GrandExchangeOfferState.SELLING)
                {
                    updateOverlay(FlipGuidanceState.WAIT_SELL, slot,
                        "Step 6: Selling " + activeItemName + "...",
                        offer.getQuantitySold() + "/" + offer.getTotalQuantity() + " sold");
                }
                else if (offerState == GrandExchangeOfferState.SOLD)
                {
                    long totalProfit = (long) discoveredMargin * offer.getTotalQuantity();
                    long totalTax = Math.min((long)(marginCheckSellPrice * 0.02), 5_000_000L) * offer.getTotalQuantity();
                    totalProfit -= totalTax;
                    long elapsed = (System.currentTimeMillis() - guidanceStartTime) / 1000;

                    transitionTo(FlipGuidanceState.COLLECT);
                    updateOverlay(FlipGuidanceState.COLLECT, slot,
                        "Step 7: Collect " + formatGp(totalProfit) + " profit!",
                        "Flip took " + formatTime(elapsed) + " — click slot to collect");
                }
                break;

            case COLLECT:
                if (offerState == GrandExchangeOfferState.EMPTY)
                {
                    transitionTo(FlipGuidanceState.COMPLETE);
                    updateOverlay(FlipGuidanceState.COMPLETE, -1,
                        "Flip complete! " + activeItemName,
                        "Ready for next flip");

                    // Auto-clear after 5 seconds
                    new Thread(() -> {
                        try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                        if (state == FlipGuidanceState.COMPLETE) cancelGuidance();
                    }).start();
                }
                break;
        }
    }

    public void cancelGuidance()
    {
        state = FlipGuidanceState.IDLE;
        activeItemId = -1;
        activeItemName = "";
        activeSlot = -1;
        if (highlightOverlay != null) highlightOverlay.clearGuidance();
    }

    public boolean isActive() { return state != FlipGuidanceState.IDLE; }
    public FlipGuidanceState getState() { return state; }
    public int getActiveItemId() { return activeItemId; }
    public String getActiveItemName() { return activeItemName; }

    private void transitionTo(FlipGuidanceState newState)
    {
        log.debug("FlipGuidance: {} → {} (item: {})", state, newState, activeItemName);
        state = newState;
    }

    private void updateOverlay(FlipGuidanceState displayState, int slot, String text, String subtext)
    {
        if (highlightOverlay != null)
        {
            highlightOverlay.setGuidance(displayState, slot, text, subtext);
        }
    }

    private int findEmptySlot()
    {
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        if (offers == null) return -1;
        for (int i = 0; i < Math.min(offers.length, 8); i++)
        {
            if (offers[i].getState() == GrandExchangeOfferState.EMPTY) return i;
        }
        return -1;
    }

    private String formatGp(long amount)
    {
        if (Math.abs(amount) >= 1_000_000) return String.format("%.1fM", amount / 1_000_000.0);
        if (Math.abs(amount) >= 1_000) return String.format("%.1fK", amount / 1_000.0);
        return amount + "gp";
    }

    private String formatTime(long seconds)
    {
        if (seconds >= 3600) return String.format("%dh %dm", seconds / 3600, (seconds % 3600) / 60);
        if (seconds >= 60) return String.format("%dm %ds", seconds / 60, seconds % 60);
        return seconds + "s";
    }
}
