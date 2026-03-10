package com.fliphelper.tracker;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.game.ItemManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.menus.WidgetMenuOption;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Right-Click Item Lookup — adds "AP: Lookup" to any item's right-click menu.
 * 
 * Right-click any item to quickly check its current margin and volume.
 * When clicked, shows a tooltip/chat message with:
 * - Current margin (buy/sell spread)
 * - Margin % and ROI after tax
 * - Volume per hour
 * - Buy limit
 * - Margin freshness
 * 
 * This works in inventory, bank, and GE interfaces.
 */
@Slf4j
@Singleton
public class ItemLookupMenu
{
    private static final String LOOKUP_OPTION = "AP: Lookup";

    private final Client client;
    private final GrandFlipOutConfig config;
    private final PriceService priceService;
    private final ItemManager itemManager;

    @Inject
    public ItemLookupMenu(Client client, GrandFlipOutConfig config,
                          PriceService priceService, ItemManager itemManager)
    {
        this.client = client;
        this.config = config;
        this.priceService = priceService;
        this.itemManager = itemManager;
    }

    /**
     * Called from the plugin's onMenuOpened event.
     * Injects "AP: Lookup" into the right-click menu for tradeable items.
     */
    public void onMenuOpened(MenuOpened event)
    {
        if (!config.enableLookupMenu()) return;

        MenuEntry[] entries = event.getMenuEntries();
        if (entries == null || entries.length == 0) return;

        // Find the first menu entry that references an item
        for (MenuEntry entry : entries)
        {
            // Check for standard item interactions (inventory, bank, GE)
            MenuAction action = entry.getType();
            if (action == MenuAction.CC_OP || action == MenuAction.CC_OP_LOW_PRIORITY
                || action == MenuAction.ITEM_USE || action == MenuAction.ITEM_FIRST_OPTION
                || action == MenuAction.ITEM_SECOND_OPTION || action == MenuAction.ITEM_THIRD_OPTION
                || action == MenuAction.ITEM_FOURTH_OPTION || action == MenuAction.ITEM_FIFTH_OPTION
                || action == MenuAction.GROUND_ITEM_FIRST_OPTION || action == MenuAction.EXAMINE_ITEM
                || action == MenuAction.EXAMINE_ITEM_GROUND)
            {
                int itemId = entry.getIdentifier();
                if (itemId <= 0) continue;

                // Get canonical item name
                ItemComposition comp = itemManager.getItemComposition(itemId);
                if (comp == null || !comp.isTradeable()) continue;

                // Add our lookup option
                client.getMenu().createMenuEntry(-1)
                    .setOption(LOOKUP_OPTION)
                    .setTarget(entry.getTarget())
                    .setIdentifier(itemId)
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> showItemLookup(e.getIdentifier(), comp.getName()));

                return; // Only add once per menu
            }
        }
    }

    /**
     * Display item price/margin data in the game chat.
     * Shows: buy/sell/margin/margin%/volume/buy limit/ROI
     */
    private void showItemLookup(int itemId, String itemName)
    {
        PriceAggregate agg = priceService.getPrice(itemId);
        if (agg == null)
        {
            sendChatMessage("[AP] No price data for " + itemName);
            return;
        }

        long buyPrice = agg.getBestLowPrice();
        long sellPrice = agg.getBestHighPrice();
        long margin = agg.getConsensusMargin();
        double marginPct = agg.getConsensusMarginPercent();
        long volume = agg.getVolume();
        int buyLimit = agg.getBuyLimit();

        // Tax calculation
        long taxPerItem = Math.min((long)(sellPrice * 0.02), 5_000_000);
        long profitPerItem = margin - taxPerItem;
        double roi = buyPrice > 0 ? ((double) profitPerItem / buyPrice) * 100 : 0;

        // Build message
        StringBuilder sb = new StringBuilder();
        sb.append("[AP] ").append(itemName);
        sb.append(" | Buy: ").append(formatGp(buyPrice));
        sb.append(" | Sell: ").append(formatGp(sellPrice));
        sb.append(" | Margin: ").append(formatGp(margin));
        sb.append(" (").append(String.format("%.1f%%", marginPct)).append(")");
        sb.append(" | After tax: ").append(formatGp(profitPerItem));
        sb.append(" | ROI: ").append(String.format("%.1f%%", roi));
        sb.append(" | Vol: ").append(formatVolume(volume));
        if (buyLimit > 0) sb.append(" | Limit: ").append(String.format("%,d", buyLimit));

        sendChatMessage(sb.toString());
    }

    private void sendChatMessage(String message)
    {
        client.addChatMessage(
            net.runelite.api.ChatMessageType.GAMEMESSAGE,
            "",
            message,
            null
        );
    }

    private String formatGp(long amount)
    {
        if (amount >= 1_000_000) return String.format("%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000) return String.format("%.1fK", amount / 1_000.0);
        return amount + "gp";
    }

    private String formatVolume(long vol)
    {
        if (vol >= 1_000_000) return String.format("%.1fM", vol / 1_000_000.0);
        if (vol >= 1_000) return String.format("%.1fK", vol / 1_000.0);
        return String.valueOf(vol);
    }
}
