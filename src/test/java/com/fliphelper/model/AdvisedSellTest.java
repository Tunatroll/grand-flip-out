/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */
package com.fliphelper.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * #249 — the advisor's sell target must survive as data.
 *
 * Owner: "people are buying and then the number they should've sold for isn't shown anymore to
 * them." The advised sell existed only in the advisor card's UI state, so it died on card
 * advance, a second position, or a relog.
 *
 * `Suggestion.sellPrice` carries it in from the server (added to /api/intelligence/suggest);
 * `FlipItem.advisedSellPrice` / `advisedAt` remember it against the lot the buy produced.
 */
public class AdvisedSellTest
{
    @Test
    public void suggestionCarriesTheAdvisedSellPrice()
    {
        Suggestion s = Suggestion.builder().itemId(4151).price(1024L).sellPrice(1102L).build();
        assertEquals(1024L, s.getPrice());
        assertEquals(1102L, s.getSellPrice());
    }

    @Test
    public void suggestionSellPriceDefaultsToZeroOnAnOlderServer()
    {
        // Gson leaves an absent field at 0 — the correct fail-closed default. A 0 means
        // "the server did not tell us", never "sell for nothing".
        assertEquals(0L, Suggestion.builder().itemId(4151).build().getSellPrice());
    }

    @Test
    public void advisedFieldsDefaultToZeroOnLegacyHistoryRows()
    {
        FlipItem f = FlipItem.builder().itemId(4151).build();
        assertEquals(0L, f.getAdvisedSellPrice());
        assertEquals(0L, f.getAdvisedAt());
    }

    @Test
    public void advisedSellIsIndependentOfFrozenSell()
    {
        // frozenSellPrice = raw MARKET high at buy time and feeds the server's hitTarget metric;
        // advisedSellPrice = what the advisor actually advised. They differ, and BOTH must
        // survive — conflating them would redefine hitTarget underneath itself.
        FlipItem f = FlipItem.builder()
            .itemId(4151)
            .frozenSellPrice(1088L)
            .advisedSellPrice(1102L)
            .advisedAt(1_784_000_000_000L)
            .build();
        assertEquals(1088L, f.getFrozenSellPrice());
        assertEquals(1102L, f.getAdvisedSellPrice());
        assertEquals(1_784_000_000_000L, f.getAdvisedAt());
    }
}
