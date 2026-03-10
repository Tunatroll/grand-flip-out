package com.fliphelper.tracker;

import com.fliphelper.AwfullyPureConfig;
import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.VarClientStr;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GE Offer Helper — handles the "press key to set price" flow that makes
 * Flipping Copilot and Flipping Utilities feel so smooth.
 *
 * <p>When the user is in the GE price-entry chatbox, pressing the configured
 * hotkey fills the price from the last margin check, suggestion, or best
 * known price. This is INFORMATION ASSISTANCE ONLY — the player still
 * confirms and submits the offer manually.</p>
 *
 * <p>Also tracks GE slot timers (time since last offer activity) like
 * Flipping Utilities, so users know when to cancel stale offers.</p>
 */
@Slf4j
public class GEOfferHelper
{
    // GE Widget IDs (standard RuneLite constants)
    private static final int GE_OFFER_CONTAINER = 465;
    private static final int GE_SEARCH_RESULTS = 162;
    // GE chatbox input when setting price/quantity
    private static final int CHATBOX_INPUT_PARENT = 162;
    private static final int CHATBOX_INPUT_TEXT = 5;

    private final Client client;
    private final ClientThread clientThread;
    private final AwfullyPureConfig config;
    private final PriceService priceService;
    private final AccountDataManager accountDataManager;

    /** Slot timers: slot index → Instant of last activity. */
    @Getter
    private final Map<Integer, Instant> slotLastActivity = new ConcurrentHashMap<>();

    /** Slot item tracking: slot index → item ID currently in slot. */
    @Getter
    private final Map<Integer, Integer> slotItems = new ConcurrentHashMap<>();

    /** Currently selected GE slot (0-7), or -1 if none. */
    @Getter
    @Setter
    private int selectedSlot = -1;

    /** What kind of offer the user is currently setting up. */
    @Getter
    @Setter
    private boolean settingUpBuyOffer = false;

    public GEOfferHelper(Client client, ClientThread clientThread,
                         AwfullyPureConfig config, PriceService priceService,
                         AccountDataManager accountDataManager)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.config = config;
        this.priceService = priceService;
        this.accountDataManager = accountDataManager;
    }

    // ==================== PRICE SET HOTKEYS ====================

    /**
     * Set the buy price in the GE chatbox input.
     * Priority: 1) Last margin check sell price (instant buy) → 2) Wiki insta-buy → 3) Best high price
     *
     * Called when user presses the "Set Buy Price" hotkey while in the GE price input.
     */
    public void setBuyPrice()
    {
        clientThread.invokeLater(() -> {
            int itemId = getCurrentOfferItemId();
            if (itemId <= 0)
            {
                log.debug("setBuyPrice: No active offer item");
                return;
            }

            long price = resolveBuyPrice(itemId);
            if (price <= 0)
            {
                log.debug("setBuyPrice: No price available for item {}", itemId);
                return;
            }

            setGEPriceInput(price);
            log.info("Set buy price for item {} to {} gp", itemId, price);
        });
    }

    /**
     * Set the sell price in the GE chatbox input.
     * Priority: 1) Last margin check buy price (instant sell) → 2) Wiki insta-sell → 3) Best low price
     */
    public void setSellPrice()
    {
        clientThread.invokeLater(() -> {
            int itemId = getCurrentOfferItemId();
            if (itemId <= 0)
            {
                log.debug("setSellPrice: No active offer item");
                return;
            }

            long price = resolveSellPrice(itemId);
            if (price <= 0)
            {
                log.debug("setSellPrice: No price available for item {}", itemId);
                return;
            }

            setGEPriceInput(price);
            log.info("Set sell price for item {} to {} gp", itemId, price);
        });
    }

    /**
     * Set the quantity to the item's GE buy limit.
     */
    public void setMaxQuantity()
    {
        clientThread.invokeLater(() -> {
            int itemId = getCurrentOfferItemId();
            if (itemId <= 0)
            {
                return;
            }

            PriceAggregate agg = priceService.getPrice(itemId);
            if (agg != null && agg.getBuyLimit() > 0)
            {
                setGEQuantityInput(agg.getBuyLimit());
                log.info("Set quantity for item {} to buy limit: {}", itemId, agg.getBuyLimit());
            }
        });
    }

    // ==================== PRICE RESOLUTION ====================

    /**
     * Resolve the best buy price for an item.
     * Uses margin check data if fresh, falls back to API prices.
     */
    private long resolveBuyPrice(int itemId)
    {
        // Priority 1: Fresh margin check — use the sell check price (= what someone will insta-sell to you)
        if (accountDataManager != null)
        {
            AccountDataManager.MarginCheckResult mc = accountDataManager.getLastMarginCheck(itemId);
            if (mc != null && mc.isFresh() && mc.getSellCheckPrice() > 0)
            {
                return mc.getSellCheckPrice();
            }
        }

        // Priority 2: API consensus price
        PriceAggregate agg = priceService.getPrice(itemId);
        if (agg != null)
        {
            // For buying: use the high price (what sellers are asking)
            if (agg.getBestHighPrice() > 0)
            {
                return agg.getBestHighPrice();
            }
            if (agg.getCurrentPrice() > 0)
            {
                return agg.getCurrentPrice();
            }
        }

        return 0;
    }

    /**
     * Resolve the best sell price for an item.
     * Uses margin check data if fresh, falls back to API prices.
     */
    private long resolveSellPrice(int itemId)
    {
        // Priority 1: Fresh margin check — use the buy check price (= what someone will insta-buy from you)
        if (accountDataManager != null)
        {
            AccountDataManager.MarginCheckResult mc = accountDataManager.getLastMarginCheck(itemId);
            if (mc != null && mc.isFresh() && mc.getBuyCheckPrice() > 0)
            {
                return mc.getBuyCheckPrice();
            }
        }

        // Priority 2: API consensus price
        PriceAggregate agg = priceService.getPrice(itemId);
        if (agg != null)
        {
            if (agg.getBestLowPrice() > 0)
            {
                return agg.getBestLowPrice();
            }
            if (agg.getCurrentPrice() > 0)
            {
                return agg.getCurrentPrice();
            }
        }

        return 0;
    }

    // ==================== GE WIDGET MANIPULATION ====================

    /**
     * Get the item ID of the item currently being offered in the GE.
     * Reads from the GE offer setup widget.
     */
    private int getCurrentOfferItemId()
    {
        // Check each GE slot for an active offer being set up
        for (int slot = 0; slot < 8; slot++)
        {
            GrandExchangeOffer offer = client.getGrandExchangeOffers()[slot];
            if (offer != null && offer.getItemId() > 0)
            {
                GrandExchangeOfferState state = offer.getState();
                if (state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.SELLING)
                {
                    // This slot has an active offer, could be the one being edited
                    if (selectedSlot == slot)
                    {
                        return offer.getItemId();
                    }
                }
            }
        }

        // Fallback: check if the GE offer setup screen is open
        // The GE item name widget contains the item being set up
        Widget geOfferContainer = client.getWidget(GE_OFFER_CONTAINER, 24);
        if (geOfferContainer != null && !geOfferContainer.isHidden())
        {
            // Try to get item from the selected slot
            if (selectedSlot >= 0 && selectedSlot < 8)
            {
                GrandExchangeOffer offer = client.getGrandExchangeOffers()[selectedSlot];
                if (offer != null)
                {
                    return offer.getItemId();
                }
            }
        }

        return -1;
    }

    /**
     * Set the price input in the GE chatbox.
     * Uses RuneLite's VarClientStr to write to the chatbox input field.
     * The player must still press Enter to confirm — we don't auto-submit.
     */
    private void setGEPriceInput(long price)
    {
        try
        {
            // Set the chatbox input text to the price
            client.setVarcStrValue(VarClientStr.INPUT_TEXT, String.valueOf(price));

            // Update the visible chatbox widget text
            Widget chatboxInput = client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT);
            if (chatboxInput != null)
            {
                chatboxInput.setText(price + "*");
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to set GE price input: {}", e.getMessage());
        }
    }

    /**
     * Set the quantity input in the GE chatbox.
     */
    private void setGEQuantityInput(int quantity)
    {
        try
        {
            client.setVarcStrValue(VarClientStr.INPUT_TEXT, String.valueOf(quantity));

            Widget chatboxInput = client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT);
            if (chatboxInput != null)
            {
                chatboxInput.setText(quantity + "*");
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to set GE quantity input: {}", e.getMessage());
        }
    }

    // ==================== SLOT TIMERS ====================

    /**
     * Update slot activity timestamp. Called on every GrandExchangeOfferChanged.
     */
    public void onOfferChanged(int slot, int itemId)
    {
        slotLastActivity.put(slot, Instant.now());
        if (itemId > 0)
        {
            slotItems.put(slot, itemId);
        }
    }

    /**
     * Get time since last activity for a slot, in seconds.
     * Returns -1 if no activity recorded.
     */
    public long getSlotIdleSeconds(int slot)
    {
        Instant lastActivity = slotLastActivity.get(slot);
        if (lastActivity == null)
        {
            return -1;
        }
        return Instant.now().getEpochSecond() - lastActivity.getEpochSecond();
    }

    /**
     * Get a human-readable idle time string for a slot.
     * e.g. "2m 30s", "1h 15m", "3d 2h"
     */
    public String getSlotIdleString(int slot)
    {
        long seconds = getSlotIdleSeconds(slot);
        if (seconds < 0)
        {
            return "—";
        }
        if (seconds < 60)
        {
            return seconds + "s";
        }
        if (seconds < 3600)
        {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        }
        if (seconds < 86400)
        {
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        }
        return (seconds / 86400) + "d " + ((seconds % 86400) / 3600) + "h";
    }

    /**
     * Check if a slot has been idle long enough to suggest cancelling.
     * Default threshold: 15 minutes for active flipping sessions.
     */
    public boolean isSlotStale(int slot, int thresholdMinutes)
    {
        long seconds = getSlotIdleSeconds(slot);
        return seconds > 0 && seconds > (thresholdMinutes * 60L);
    }

    /**
     * Check if the GE interface is currently open.
     */
    public boolean isGEOpen()
    {
        Widget geWidget = client.getWidget(GE_OFFER_CONTAINER, 0);
        return geWidget != null && !geWidget.isHidden();
    }

    /**
     * Check if the chatbox is in "enter price" or "enter quantity" input mode.
     */
    public boolean isChatboxInputActive()
    {
        Widget chatboxInput = client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT);
        return chatboxInput != null && !chatboxInput.isHidden();
    }

    // ==================== MARGIN FRESHNESS ====================

    /**
     * Get margin freshness level for an item (0-4).
     * 4 = Fresh (<5 min), 3 = Recent (<30 min), 2 = Aging (<2 hr), 1 = Stale (<4 hr), 0 = Expired
     * Used by the UI to show color-coded freshness dots.
     */
    public int getMarginFreshness(int itemId)
    {
        if (accountDataManager == null)
        {
            return 0;
        }
        AccountDataManager.MarginCheckResult mc = accountDataManager.getLastMarginCheck(itemId);
        if (mc == null || mc.getTimestamp() == null)
        {
            return 0;
        }

        long ageSeconds = Instant.now().getEpochSecond() - mc.getTimestamp().getEpochSecond();
        if (ageSeconds < 300) return 4;    // <5 min = fresh
        if (ageSeconds < 1800) return 3;   // <30 min = recent
        if (ageSeconds < 7200) return 2;   // <2 hr = aging
        if (ageSeconds < 14400) return 1;  // <4 hr = stale
        return 0;                          // expired
    }

    /**
     * Get freshness display string for UI.
     */
    public String getFreshnessLabel(int freshness)
    {
        switch (freshness)
        {
            case 4: return "\u25CF Fresh";   // ● green
            case 3: return "\u25CF Recent";  // ● yellow-green
            case 2: return "\u25CF Aging";   // ● yellow
            case 1: return "\u25CF Stale";   // ● orange
            default: return "\u25CB No data"; // ○ gray
        }
    }

    /**
     * Get freshness color for UI rendering.
     * Returns an AWT Color appropriate for the freshness level.
     */
    public java.awt.Color getFreshnessColor(int freshness)
    {
        switch (freshness)
        {
            case 4: return new java.awt.Color(0x00, 0xD2, 0x6A); // Bright green
            case 3: return new java.awt.Color(0x7C, 0xD2, 0x00); // Yellow-green
            case 2: return new java.awt.Color(0xFF, 0xB8, 0x00); // Gold/yellow
            case 1: return new java.awt.Color(0xFF, 0x8C, 0x00); // Orange
            default: return new java.awt.Color(0x60, 0x60, 0x80); // Gray
        }
    }
}
