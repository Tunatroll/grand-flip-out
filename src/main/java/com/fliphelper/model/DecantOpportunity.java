/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.model;

import com.fliphelper.util.GeTax;

/**
 * Per-dose decanting arbitrage for a single potion.
 *
 * <p>A potion trades as four GE items (1, 2, 3 and 4 doses). The same dose of liquid is
 * worth a different amount depending on which variant you trade. This finds the best decant:
 * buy the variant that is cheapest <em>per dose</em> (at its insta-buy / ask price), decant
 * to the variant that sells highest <em>per dose</em> (at its insta-sell / bid price, after
 * GE tax), and report the after-tax profit for each dose of liquid moved.
 *
 * <p>The math is deliberately decoupled from {@link PriceAggregate}: callers pass the raw
 * ask/bid per variant and item IDs so it is trivially unit-testable. Use the
 * {@link #compute(String, int[], long[], long[])} factory.
 */
public final class DecantOpportunity
{
    private final String potionName;

    private final int buyItemId;
    private final int buyDoses;     // dose count of the variant we buy (1..4)
    private final long buyPrice;    // ask (insta-buy) of the bought variant, per item

    private final int sellItemId;
    private final int sellDoses;    // dose count of the variant we sell (1..4)
    private final long sellPrice;   // bid (insta-sell) of the sold variant, per item

    private final double buyPerDose;          // gp per dose to acquire (no tax — buying is untaxed)
    private final double sellPerDoseAfterTax; // gp per dose received after GE sell tax
    private final double profitPerDose;       // sellPerDoseAfterTax - buyPerDose

    private DecantOpportunity(String potionName,
                              int buyItemId, int buyDoses, long buyPrice,
                              int sellItemId, int sellDoses, long sellPrice,
                              double buyPerDose, double sellPerDoseAfterTax, double profitPerDose)
    {
        this.potionName = potionName;
        this.buyItemId = buyItemId;
        this.buyDoses = buyDoses;
        this.buyPrice = buyPrice;
        this.sellItemId = sellItemId;
        this.sellDoses = sellDoses;
        this.sellPrice = sellPrice;
        this.buyPerDose = buyPerDose;
        this.sellPerDoseAfterTax = sellPerDoseAfterTax;
        this.profitPerDose = profitPerDose;
    }

    /**
     * Compute the best per-dose decant for a potion from its four dose variants.
     *
     * @param potionName display name
     * @param doseItemIds item IDs, index 0 = 1-dose ... index 3 = 4-dose (length 4)
     * @param asks insta-buy (ask) price per variant, same indexing; non-positive = unknown
     * @param bids insta-sell (bid) price per variant, same indexing; non-positive = unknown
     * @return the best opportunity, or {@code null} if fewer than two priced variants exist
     *         (need one to buy and one to sell)
     */
    public static DecantOpportunity compute(String potionName, int[] doseItemIds, long[] asks, long[] bids)
    {
        if (doseItemIds == null || asks == null || bids == null
            || doseItemIds.length != 4 || asks.length != 4 || bids.length != 4)
        {
            return null;
        }

        // Cheapest variant to BUY, measured per dose (buying is untaxed).
        int buyIdx = -1;
        double bestBuyPerDose = Double.MAX_VALUE;
        for (int i = 0; i < 4; i++)
        {
            if (asks[i] <= 0)
            {
                continue;
            }
            int doses = i + 1;
            double perDose = (double) asks[i] / doses;
            if (perDose < bestBuyPerDose)
            {
                bestBuyPerDose = perDose;
                buyIdx = i;
            }
        }

        // Best variant to SELL, measured per dose AFTER GE tax on the sold item.
        int sellIdx = -1;
        double bestSellPerDose = -Double.MAX_VALUE;
        for (int i = 0; i < 4; i++)
        {
            if (bids[i] <= 0)
            {
                continue;
            }
            int doses = i + 1;
            long taxPerItem = GeTax.tax(doseItemIds[i], bids[i], 1);
            double netPerItem = bids[i] - taxPerItem;
            double perDose = netPerItem / doses;
            if (perDose > bestSellPerDose)
            {
                bestSellPerDose = perDose;
                sellIdx = i;
            }
        }

        if (buyIdx < 0 || sellIdx < 0)
        {
            return null;
        }

        double profitPerDose = bestSellPerDose - bestBuyPerDose;
        return new DecantOpportunity(
            potionName,
            doseItemIds[buyIdx], buyIdx + 1, asks[buyIdx],
            doseItemIds[sellIdx], sellIdx + 1, bids[sellIdx],
            bestBuyPerDose, bestSellPerDose, profitPerDose);
    }

    public String getPotionName()
    {
        return potionName;
    }

    public int getBuyItemId()
    {
        return buyItemId;
    }

    /** Dose count (1..4) of the variant to buy. */
    public int getBuyDoses()
    {
        return buyDoses;
    }

    /** Ask (insta-buy) price of the bought variant, per item. */
    public long getBuyPrice()
    {
        return buyPrice;
    }

    public int getSellItemId()
    {
        return sellItemId;
    }

    /** Dose count (1..4) of the variant to sell. */
    public int getSellDoses()
    {
        return sellDoses;
    }

    /** Bid (insta-sell) price of the sold variant, per item. */
    public long getSellPrice()
    {
        return sellPrice;
    }

    /** gp per dose to acquire the liquid (buying is untaxed). */
    public double getBuyPerDose()
    {
        return buyPerDose;
    }

    /** gp per dose received after GE sell tax on the sold variant. */
    public double getSellPerDoseAfterTax()
    {
        return sellPerDoseAfterTax;
    }

    /** After-tax profit per dose of liquid moved (may be negative). */
    public double getProfitPerDose()
    {
        return profitPerDose;
    }

    /** True when moving a dose of liquid nets a positive after-tax profit. */
    public boolean isProfitable()
    {
        return profitPerDose > 0;
    }
}
