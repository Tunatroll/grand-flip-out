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

    @Getter
    private final Map<Integer, Instant> slotLastActivity = new ConcurrentHashMap<>();

    @Getter
    private final Map<Integer, Integer> slotItems = new ConcurrentHashMap<>();

    @Getter
    @Setter
    private int selectedSlot = -1;

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

    
    public void onOfferChanged(int slot, int itemId)
    {
        slotLastActivity.put(slot, Instant.now());
        if (itemId > 0)
        {
            slotItems.put(slot, itemId);
        }
    }

    
    public long getSlotIdleSeconds(int slot)
    {
        Instant lastActivity = slotLastActivity.get(slot);
        if (lastActivity == null)
        {
            return -1;
        }
        return Instant.now().getEpochSecond() - lastActivity.getEpochSecond();
    }

    
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

    
    public boolean isSlotStale(int slot, int thresholdMinutes)
    {
        long seconds = getSlotIdleSeconds(slot);
        return seconds > 0 && seconds > (thresholdMinutes * 60L);
    }

    
    public boolean isGEOpen()
    {
        Widget geWidget = client.getWidget(GE_OFFER_CONTAINER, 0);
        return geWidget != null && !geWidget.isHidden();
    }

    
    public boolean isChatboxInputActive()
    {
        Widget chatboxInput = client.getWidget(WidgetInfo.CHATBOX_FULL_INPUT);
        return chatboxInput != null && !chatboxInput.isHidden();
    }

    // ==================== MARGIN FRESHNESS ====================

    
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

    // ==================== BUY LIMIT TRACKER ====================
    // Tracks the 4-hour GE buy limit cycle per item.
    // When a player first buys an item, the 4-hour window starts.
    // Buy limit countdown with progress tracking
    // and they still haven't built it.

    // itemId → {firstBuyTime, quantityBought, buyLimit}
    private final java.util.Map<Integer, BuyLimitState> buyLimitStates = new java.util.HashMap<>();

    public static class BuyLimitState
    {
        private Instant windowStart;
        private int quantityBought;
        private int buyLimit;

        public BuyLimitState(int buyLimit)
        {
            this.windowStart = Instant.now();
            this.quantityBought = 0;
            this.buyLimit = buyLimit;
        }

        public Instant getWindowStart() { return windowStart; }
        public int getQuantityBought() { return quantityBought; }
        public int getBuyLimit() { return buyLimit; }

        /** Seconds remaining until buy limit resets */
        public long getSecondsUntilReset()
        {
            long elapsed = Instant.now().getEpochSecond() - windowStart.getEpochSecond();
            long remaining = (4 * 3600) - elapsed;
            return Math.max(0, remaining);
        }

        /** Progress as 0.0 to 1.0 (quantity bought / buy limit) */
        public double getProgress()
        {
            if (buyLimit <= 0) return 0;
            return Math.min(1.0, (double) quantityBought / buyLimit);
        }

        /** True if 4-hour window has expired */
        public boolean isExpired()
        {
            return getSecondsUntilReset() <= 0;
        }
    }

    /**
     * Record a buy for buy-limit tracking.
     * Called when a BUYING offer progresses.
     */
    public void recordBuy(int itemId, int quantity, int buyLimit)
    {
        BuyLimitState state = buyLimitStates.get(itemId);
        if (state == null || state.isExpired())
        {
            state = new BuyLimitState(buyLimit);
            buyLimitStates.put(itemId, state);
        }
        state.quantityBought += quantity;
    }

    /**
     * Get buy-limit state for an item.
     */
    public BuyLimitState getBuyLimitState(int itemId)
    {
        BuyLimitState state = buyLimitStates.get(itemId);
        if (state != null && state.isExpired())
        {
            buyLimitStates.remove(itemId);
            return null;
        }
        return state;
    }

    /**
     * Get formatted countdown string: "2h 15m" or "RESET"
     */
    public String getBuyLimitCountdown(int itemId)
    {
        BuyLimitState state = getBuyLimitState(itemId);
        if (state == null) return "No limit data";
        long secs = state.getSecondsUntilReset();
        if (secs <= 0) return "RESET";
        long hours = secs / 3600;
        long mins = (secs % 3600) / 60;
        return String.format("%dh %dm", hours, mins);
    }

    /**
     * Get formatted limit usage: "1,234 / 2,000 (61%)"
     */
    public String getBuyLimitUsage(int itemId)
    {
        BuyLimitState state = getBuyLimitState(itemId);
        if (state == null) return "—";
        return String.format("%,d / %,d (%d%%)",
            state.getQuantityBought(), state.getBuyLimit(),
            (int) (state.getProgress() * 100));
    }
}
