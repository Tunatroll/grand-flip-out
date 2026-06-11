/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.util;

import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;

/**
 * Read-only estimate of player wealth from RuneLite client state.
 * Uses Wiki midpoint prices for non-coin items (informational only).
 */
@Value
public class WealthSnapshot
{
    private static final int COINS = 995;

    long coinGp;
    long inventoryGp;
    long bankGp;
    long totalWealthGp;

    public static WealthSnapshot capture(Client client, PriceService priceService)
    {
        if (client == null || client.getGameState() != GameState.LOGGED_IN)
        {
            return empty();
        }

        long coins = 0;
        long inventory = 0;
        long bank = 0;

        ItemContainer inventoryContainer = client.getItemContainer(InventoryID.INVENTORY);
        if (inventoryContainer != null)
        {
            for (Item item : inventoryContainer.getItems())
            {
                if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
                {
                    continue;
                }

                if (item.getId() == COINS)
                {
                    coins += item.getQuantity();
                }
                else
                {
                    inventory += (long) item.getQuantity() * estimateItemGp(priceService, item.getId());
                }
            }
        }

        ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
        if (bankContainer != null)
        {
            for (Item item : bankContainer.getItems())
            {
                if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
                {
                    continue;
                }

                if (item.getId() == COINS)
                {
                    coins += item.getQuantity();
                }
                else
                {
                    bank += (long) item.getQuantity() * estimateItemGp(priceService, item.getId());
                }
            }
        }

        long total = coins + inventory + bank;
        return new WealthSnapshot(coins, inventory, bank, total);
    }

    public static WealthSnapshot empty()
    {
        return new WealthSnapshot(0, 0, 0, 0);
    }

    private static long estimateItemGp(PriceService priceService, int itemId)
    {
        if (priceService == null || itemId <= 0)
        {
            return 0;
        }

        PriceAggregate agg = priceService.getPrice(itemId);
        if (agg == null)
        {
            return 0;
        }

        long low = agg.getBestLowPrice();
        long high = agg.getBestHighPrice();
        if (low > 0 && high > 0)
        {
            return (low + high) / 2;
        }
        return Math.max(low, high);
    }
}
