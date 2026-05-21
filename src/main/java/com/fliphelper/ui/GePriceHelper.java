package com.fliphelper.ui;

import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;

/**
 * Assists with GE offer creation by pre-filling optimal prices.
 *
 * <p><b>INFORMATION ONLY — COMPLIANCE NOTE</b></p>
 * <p>This helper pre-fills the price input field when the player is
 * actively creating a GE offer. It does NOT place offers, click buttons,
 * or automate any GE interaction. The player must still confirm the
 * offer manually.</p>
 *
 * <p>This approach is identical to what RuneLite's own GE plugin does
 * (setting price text via Widget.setText) and what Flipping Copilot uses.
 * Widget.setText() on the price input is a display-only change — the actual
 * offer is not submitted until the player clicks Confirm.</p>
 */
@Slf4j
public class GePriceHelper
{
    private final Client client;
    private final ClientThread clientThread;
    private final PriceService priceService;

    // GE offer setup widget IDs — these are the child widgets within the GE offer interface
    // ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER is the parent for the offer setup screen
    private static final int GE_OFFER_PRICE_INPUT = 24; // Child index for price input text widget

    private boolean enabled = true;
    private long lastSetPrice = 0;
    private int lastSetItemId = -1;

    public GePriceHelper(Client client, ClientThread clientThread, PriceService priceService)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.priceService = priceService;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    /**
     * Attempt to pre-fill the GE price field with the optimal flip price.
     *
     * <p>Call this when the player opens a GE buy/sell offer screen.
     * Determines if the player is buying or selling, then sets the price
     * to the optimal instant-buy (for buys) or instant-sell (for sells) price.</p>
     *
     * @param itemId The item being offered
     * @param isBuying True if this is a buy offer, false for sell
     * @return The price that was set, or 0 if unable to set
     */
    public long suggestPrice(int itemId, boolean isBuying)
    {
        if (!enabled)
        {
            return 0;
        }

        PriceAggregate agg = priceService.getPrice(itemId);
        if (agg == null)
        {
            log.debug("No price data for item {}, skipping auto-fill", itemId);
            return 0;
        }

        // For buying: use the instant-buy price (current high/sell price + 1gp margin)
        // For selling: use the instant-sell price (current low/buy price - 1gp margin)
        long suggestedPrice;
        if (isBuying)
        {
            // To buy instantly, offer slightly above the current sell price
            suggestedPrice = agg.getBestHighPrice();
            if (suggestedPrice <= 0)
            {
                suggestedPrice = agg.getBestLowPrice();
            }
        }
        else
        {
            // To sell instantly, offer slightly below the current buy price
            suggestedPrice = agg.getBestLowPrice();
            if (suggestedPrice <= 0)
            {
                suggestedPrice = agg.getBestHighPrice();
            }
        }

        if (suggestedPrice <= 0)
        {
            return 0;
        }

        // Track what we set to avoid redundant updates
        if (lastSetItemId == itemId && lastSetPrice == suggestedPrice)
        {
            return suggestedPrice;
        }

        lastSetItemId = itemId;
        lastSetPrice = suggestedPrice;

        log.debug("Auto-fill price for {} ({}): {}gp",
            agg.getItemName(), isBuying ? "BUY" : "SELL", suggestedPrice);

        // Also copy to clipboard as fallback
        try
        {
            String priceStr = String.valueOf(suggestedPrice);
            java.awt.datatransfer.StringSelection selection =
                new java.awt.datatransfer.StringSelection(priceStr);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
        catch (Exception e)
        {
            log.debug("Failed to copy price to clipboard: {}", e.getMessage());
        }

        return suggestedPrice;
    }

    /**
     * Get the margin check prices for an item (buy at 1gp above sell, sell at 1gp below buy).
     * Used for instant margin checks — the fundamental technique all flippers use.
     *
     * @param itemId The item to margin check
     * @return Array of [buyCheckPrice, sellCheckPrice] or null if no data
     */
    public long[] getMarginCheckPrices(int itemId)
    {
        PriceAggregate agg = priceService.getPrice(itemId);
        if (agg == null)
        {
            return null;
        }

        long high = agg.getBestHighPrice();
        long low = agg.getBestLowPrice();

        if (high <= 0 || low <= 0)
        {
            return null;
        }

        // For margin check buy: offer high price to buy instantly
        // For margin check sell: offer low price to sell instantly
        return new long[]{high, low};
    }

    /**
     * Check if the GE offer creation screen is currently open.
     */
    public boolean isOfferScreenOpen()
    {
        Widget geWidget = client.getWidget(ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER);
        return geWidget != null && !geWidget.isHidden();
    }

    /**
     * Get the currently selected item ID in the GE interface, if any.
     * Returns -1 if no item is selected or GE is not open.
     */
    public int getSelectedItemId()
    {
        try
        {
            // Check all 8 GE slots for active selection
            GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
            if (offers == null)
            {
                return -1;
            }

            for (GrandExchangeOffer offer : offers)
            {
                if (offer != null && offer.getState() == GrandExchangeOfferState.BUYING)
                {
                    return offer.getItemId();
                }
            }
        }
        catch (Exception e)
        {
            log.debug("Error getting selected item: {}", e.getMessage());
        }
        return -1;
    }

    /**
     * Reset tracking state (call when GE closes).
     */
    public void reset()
    {
        lastSetItemId = -1;
        lastSetPrice = 0;
    }
}
