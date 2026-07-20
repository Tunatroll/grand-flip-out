/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.ui;

import com.fliphelper.model.PriceAggregate;
import com.fliphelper.model.PriceData;
import com.fliphelper.model.PriceSource;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Watchlist / price-row "fill" affordance (#190 follow-on): the advisor rows could arm a GE
 * fill, starred rows could not — the churn user's "add items from the website to the plugin,
 * then add them to the search when GE is open" ends at a row you cannot act on.
 *
 * The contract pinned here is the honest-price one: a row arms the BUY side, and a row with no
 * real buy price arms NOTHING. Arming 0 would pre-fill a GE offer with a fabricated price —
 * the one thing an advisory tool must never do.
 */
public class WatchlistFillTest
{
    private static PriceAggregate agg(long low)
    {
        PriceData pd = PriceData.builder()
            .itemId(4151).itemName("Abyssal whip")
            .highPrice(low > 0 ? low + 100 : 0).lowPrice(low)
            .highTime(1000L).lowTime(1000L)
            .source(PriceSource.WIKI)
            .build();
        Map<PriceSource, PriceData> sources = new HashMap<>();
        sources.put(PriceSource.WIKI, pd);
        return PriceAggregate.builder().itemId(4151).itemName("Abyssal whip").sourceData(sources).build();
    }

    @Test
    public void armsTheBuySide()
    {
        assertEquals("a price row arms what you would place a BUY offer at",
            1_500_000L, GrandFlipOutPanel.fillArmPrice(agg(1_500_000L)));
    }

    @Test
    public void refusesWhenThereIsNoHonestBuyPrice()
    {
        assertEquals("no real buy price must arm nothing, never 0 gp into a GE input",
            0L, GrandFlipOutPanel.fillArmPrice(agg(0L)));
    }

    @Test
    public void refusesNullAggregate()
    {
        assertEquals(0L, GrandFlipOutPanel.fillArmPrice(null));
    }
}
