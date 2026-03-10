package com.fliphelper.tracker;

import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * FlipGuidanceEngine — Quest Helper-style step-by-step flip coaching.
 *
 * State machine: IDLE → MARGIN_CHECK → BUY → WAIT_BUY → SELL → WAIT_SELL → COMPLETE
 *
 * Each state produces:
 * - A short instruction string for the overlay ("Buy 1 at +5% to check margin")
 * - A highlight slot index (which GE slot to draw attention to)
 * - A suggested price/quantity
 *
 * Each step explains the reasoning so users learn market mechanics
 * at each step: the margin %, ROI, volume, and confidence level.
 */
@Slf4j
@Singleton
public class FlipGuidanceEngine
{
    public enum FlipState
    {
        IDLE("No active guidance"),
        MARGIN_CHECK_BUY("Buy 1 at high price to check instant-buy price"),
        MARGIN_CHECK_SELL("Sell 1 at low price to check instant-sell price"),
        MARGIN_READY("Margin checked — ready to flip"),
        BUY("Place buy offer at margin-check price"),
        WAIT_BUY("Waiting for buy offer to fill..."),
        SELL("Place sell offer at margin-check price"),
        WAIT_SELL("Waiting for sell offer to fill..."),
        COMPLETE("Flip complete! Collect profit.");

        @Getter
        private final String description;
        FlipState(String description) { this.description = description; }
    }

    @Getter
    public static class GuidanceStep
    {
        private final FlipState state;
        private final String instruction;     // Short text for overlay
        private final String reason;          // Why this step (margin%, vol, etc)
        private final int suggestedSlot;      // Which GE slot (-1 = any empty)
        private final int suggestedItemId;
        private final String suggestedItemName;
        private final long suggestedPrice;
        private final int suggestedQuantity;
        private final Instant timestamp;

        public GuidanceStep(FlipState state, String instruction, String reason,
                            int slot, int itemId, String itemName, long price, int qty)
        {
            this.state = state;
            this.instruction = instruction;
            this.reason = reason;
            this.suggestedSlot = slot;
            this.suggestedItemId = itemId;
            this.suggestedItemName = itemName;
            this.suggestedPrice = price;
            this.suggestedQuantity = qty;
            this.timestamp = Instant.now();
        }
    }

    private final Client client;
    private final PriceService priceService;
    private final AccountDataManager accountDataManager;

    // Per-slot tracking: slot → current guidance state
    private final Map<Integer, SlotGuidance> slotStates = new HashMap<>();
    @Getter
    private GuidanceStep currentStep = null;
    @Getter
    private boolean active = false;

    private static class SlotGuidance
    {
        FlipState state = FlipState.IDLE;
        int itemId;
        String itemName;
        long marginCheckBuyPrice;  // instant-buy price discovered
        long marginCheckSellPrice; // instant-sell price discovered
        long buyPrice;
        long sellPrice;
        int quantity;
        Instant stateChanged = Instant.now();
    }

    @Inject
    public FlipGuidanceEngine(Client client, PriceService priceService,
                              AccountDataManager accountDataManager)
    {
        this.client = client;
        this.priceService = priceService;
        this.accountDataManager = accountDataManager;
    }

    /**
     * Start guided flipping for a specific item.
     * Called when user clicks "Guide Me" on an item suggestion.
     */
    public GuidanceStep startGuidance(int itemId, String itemName, int preferredSlot)
    {
        active = true;

        PriceAggregate agg = priceService.getPrice(itemId);
        long highPrice = agg != null ? agg.getBestHighPrice() : 0;
        long lowPrice = agg != null ? agg.getBestLowPrice() : 0;
        int buyLimit = agg != null ? agg.getBuyLimit() : 0;

        // Check if we already have a fresh margin check
        AccountDataManager.MarginCheckResult mc = accountDataManager != null
            ? accountDataManager.getLastMarginCheck(itemId) : null;

        boolean hasFreshMargin = mc != null && mc.getTimestamp() != null
            && (Instant.now().getEpochSecond() - mc.getTimestamp().getEpochSecond()) < 1800; // <30 min

        int slot = preferredSlot >= 0 ? preferredSlot : findEmptySlot();
        if (slot < 0)
        {
            currentStep = new GuidanceStep(FlipState.IDLE,
                "No empty GE slots available", "Cancel or collect an existing offer first",
                -1, itemId, itemName, 0, 0);
            return currentStep;
        }

        SlotGuidance sg = new SlotGuidance();
        sg.itemId = itemId;
        sg.itemName = itemName;
        slotStates.put(slot, sg);

        if (hasFreshMargin)
        {
            // Skip margin check — go straight to buy
            sg.marginCheckBuyPrice = mc.getBuyCheckPrice();
            sg.marginCheckSellPrice = mc.getSellCheckPrice();
            sg.state = FlipState.MARGIN_READY;

            long margin = sg.marginCheckBuyPrice - sg.marginCheckSellPrice;
            double marginPct = sg.marginCheckSellPrice > 0
                ? (double) margin / sg.marginCheckSellPrice * 100 : 0;

            // Suggest buy at instant-sell price (the low side of the margin)
            long buyAt = sg.marginCheckSellPrice;
            int qty = buyLimit > 0 ? buyLimit : 100;

            sg.buyPrice = buyAt;
            sg.quantity = qty;
            sg.state = FlipState.BUY;

            long estProfit = (margin - Math.min((long)(sg.marginCheckBuyPrice * 0.02), 5_000_000)) * qty;

            currentStep = new GuidanceStep(FlipState.BUY,
                String.format("Buy %,d × %s at %,d gp", qty, itemName, buyAt),
                String.format("Margin: %,d gp (%.1f%%) | Est profit: %,d gp | Press E to auto-fill",
                    margin, marginPct, estProfit),
                slot, itemId, itemName, buyAt, qty);
            return currentStep;
        }

        // Need margin check — Step 1: Buy 1 at +5% to discover instant-buy price
        long checkBuyPrice = highPrice > 0 ? (long)(highPrice * 1.05) : lowPrice + 100;
        sg.state = FlipState.MARGIN_CHECK_BUY;

        currentStep = new GuidanceStep(FlipState.MARGIN_CHECK_BUY,
            String.format("Buy 1 × %s at %,d gp (margin check)", itemName, checkBuyPrice),
            String.format("This checks the instant-buy price. It will fill at the real price, not %,d. Press E to auto-fill.",
                checkBuyPrice),
            slot, itemId, itemName, checkBuyPrice, 1);
        return currentStep;
    }

    /**
     * Called on every GE offer state change. Advances the guidance state machine.
     */
    public GuidanceStep onOfferChanged(int slot, GrandExchangeOffer offer)
    {
        if (!active) return null;

        SlotGuidance sg = slotStates.get(slot);
        if (sg == null) return null;

        GrandExchangeOfferState state = offer.getState();
        int itemId = offer.getItemId();
        if (itemId != sg.itemId) return null; // Different item in this slot

        switch (sg.state)
        {
            case MARGIN_CHECK_BUY:
                if (state == GrandExchangeOfferState.BOUGHT)
                {
                    // Margin check buy completed — record actual buy price
                    long actualBuyPrice = offer.getSpent() / Math.max(offer.getQuantitySold(), 1);
                    sg.marginCheckBuyPrice = actualBuyPrice;
                    sg.state = FlipState.MARGIN_CHECK_SELL;

                    currentStep = new GuidanceStep(FlipState.MARGIN_CHECK_SELL,
                        String.format("Sell 1 × %s at 1 gp (margin check)", sg.itemName),
                        String.format("Instant-buy is %,d gp. Now check instant-sell. Press R to auto-fill.",
                            actualBuyPrice),
                        slot, sg.itemId, sg.itemName, 1, 1);
                    return currentStep;
                }
                break;

            case MARGIN_CHECK_SELL:
                if (state == GrandExchangeOfferState.SOLD)
                {
                    long actualSellPrice = offer.getSpent() / Math.max(offer.getQuantitySold(), 1);
                    sg.marginCheckSellPrice = actualSellPrice;

                    // Store margin check in account data
                    if (accountDataManager != null)
                    {
                        accountDataManager.recordMarginCheck(sg.itemId, sg.marginCheckBuyPrice, actualSellPrice);
                    }

                    long margin = sg.marginCheckBuyPrice - actualSellPrice;
                    double marginPct = actualSellPrice > 0 ? (double) margin / actualSellPrice * 100 : 0;

                    PriceAggregate agg = priceService.getPrice(sg.itemId);
                    int buyLimit = agg != null ? agg.getBuyLimit() : 100;
                    int qty = buyLimit > 0 ? buyLimit - 1 : 99; // -1 for the margin check item
                    long tax = Math.min((long)(sg.marginCheckBuyPrice * 0.02), 5_000_000);
                    long estProfit = (margin - tax) * qty;

                    if (margin <= 0 || marginPct < 0.5)
                    {
                        // Not profitable
                        sg.state = FlipState.IDLE;
                        currentStep = new GuidanceStep(FlipState.IDLE,
                            String.format("Skip %s — margin too thin", sg.itemName),
                            String.format("Margin: %,d gp (%.1f%%) — below 0.5%% threshold. Try another item.", margin, marginPct),
                            -1, sg.itemId, sg.itemName, 0, 0);
                        return currentStep;
                    }

                    sg.buyPrice = actualSellPrice; // Buy at the instant-sell price
                    sg.sellPrice = sg.marginCheckBuyPrice; // Sell at the instant-buy price
                    sg.quantity = qty;
                    sg.state = FlipState.BUY;

                    currentStep = new GuidanceStep(FlipState.BUY,
                        String.format("Buy %,d × %s at %,d gp", qty, sg.itemName, sg.buyPrice),
                        String.format("Margin: %,d gp (%.1f%%) | Tax: %,d/item | Est profit: %,d gp | Press E",
                            margin, marginPct, tax, estProfit),
                        slot, sg.itemId, sg.itemName, sg.buyPrice, qty);
                    return currentStep;
                }
                break;

            case BUY:
            case WAIT_BUY:
                if (state == GrandExchangeOfferState.BUYING)
                {
                    sg.state = FlipState.WAIT_BUY;
                    int filled = offer.getQuantitySold();
                    int total = offer.getTotalQuantity();
                    int pct = total > 0 ? (filled * 100 / total) : 0;

                    currentStep = new GuidanceStep(FlipState.WAIT_BUY,
                        String.format("Buying %s... %d/%d (%d%%)", sg.itemName, filled, total, pct),
                        "Waiting for offer to fill. Be patient or adjust price if stale >5 min.",
                        slot, sg.itemId, sg.itemName, sg.buyPrice, sg.quantity);
                    return currentStep;
                }
                else if (state == GrandExchangeOfferState.BOUGHT)
                {

                    // Buy complete — now sell
                    sg.state = FlipState.SELL;
                    currentStep = new GuidanceStep(FlipState.SELL,
                        String.format("Sell %,d × %s at %,d gp", sg.quantity, sg.itemName, sg.sellPrice),
                        String.format("Buy filled! Now sell at instant-buy price. Press R to auto-fill."),
                        slot, sg.itemId, sg.itemName, sg.sellPrice, sg.quantity);
                    return currentStep;
                }
                break;

            case SELL:
            case WAIT_SELL:
                if (state == GrandExchangeOfferState.SELLING)
                {
                    sg.state = FlipState.WAIT_SELL;
                    int filled = offer.getQuantitySold();
                    int total = offer.getTotalQuantity();
                    int pct = total > 0 ? (filled * 100 / total) : 0;

                    currentStep = new GuidanceStep(FlipState.WAIT_SELL,
                        String.format("Selling %s... %d/%d (%d%%)", sg.itemName, filled, total, pct),
                        "Waiting for sell to complete.",
                        slot, sg.itemId, sg.itemName, sg.sellPrice, sg.quantity);
                    return currentStep;
                }
                else if (state == GrandExchangeOfferState.SOLD)
                {
                    sg.state = FlipState.COMPLETE;
                    long revenue = offer.getSpent();
                    long cost = sg.buyPrice * sg.quantity;
                    long tax = Math.min((long)(sg.sellPrice * 0.02), 5_000_000) * sg.quantity;
                    long profit = revenue - cost - tax;

                    currentStep = new GuidanceStep(FlipState.COMPLETE,
                        String.format("Flip complete! Profit: %,d gp", profit),
                        String.format("Collect from GE slot %d. Revenue: %,d | Cost: %,d | Tax: %,d",
                            slot + 1, revenue, cost, tax),
                        slot, sg.itemId, sg.itemName, 0, 0);

                    // Clean up
                    slotStates.remove(slot);
                    if (slotStates.isEmpty()) active = false;
                    return currentStep;
                }
                break;
        }
        return null;
    }

    /**
     * Cancel guidance for a slot or all slots.
     */
    public void cancel(int slot)
    {
        if (slot < 0)
        {
            slotStates.clear();
            active = false;
            currentStep = null;
        }
        else
        {
            slotStates.remove(slot);
            if (slotStates.isEmpty())
            {
                active = false;
                currentStep = null;
            }
        }
    }

    private int findEmptySlot()
    {
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        if (offers == null) return -1;
        for (int i = 0; i < offers.length; i++)
        {
            if (offers[i].getState() == GrandExchangeOfferState.EMPTY) return i;
        }
        return -1;
    }
}
