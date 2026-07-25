/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */
package com.fliphelper.tracker;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

/**
 * #249 — advice waiting to be attached to the buy it produced.
 *
 * Keyed on itemId ALONE, deliberately not on price: players routinely buy at a price other
 * than the advised one (undercutting, or the book moves between advice and offer), and
 * price-keying would silently fail to stamp exactly the lots most worth remembering.
 */
public class PendingAdviceTest
{
    private static final long T0 = 1_784_000_000_000L;

    @Test
    public void aClaimedAdviceReturnsItsPriceAndTimestamp()
    {
        PendingAdvice p = new PendingAdvice();
        p.record(4151, 1102L, T0);
        assertArrayEquals(new long[]{1102L, T0}, p.claim(4151, T0 + 5_000));
    }

    @Test
    public void adviceIsClaimedOnceOnly()
    {
        PendingAdvice p = new PendingAdvice();
        p.record(4151, 1102L, T0);
        p.claim(4151, T0 + 1_000);
        assertNull("a second buy must not inherit the first buy's advice", p.claim(4151, T0 + 2_000));
    }

    @Test
    public void adviceOlderThanThirtyMinutesNeverAttaches()
    {
        // Stale advice must not latch onto an unrelated later buy of the same item.
        PendingAdvice p = new PendingAdvice();
        p.record(4151, 1102L, T0);
        assertNull(p.claim(4151, T0 + 30 * 60_000 + 1));
    }

    @Test
    public void mostRecentAdviceForAnItemWins()
    {
        PendingAdvice p = new PendingAdvice();
        p.record(4151, 1102L, T0);
        p.record(4151, 1150L, T0 + 60_000);
        assertArrayEquals(new long[]{1150L, T0 + 60_000}, p.claim(4151, T0 + 61_000));
    }

    @Test
    public void anUnadvisedItemClaimsNothing()
    {
        assertNull(new PendingAdvice().claim(4151, T0));
    }

    @Test
    public void aZeroOrNegativeAdvisedPriceIsNeverRecorded()
    {
        // An older server sends sellPrice 0. Recording it would stamp "advised 0" onto a lot
        // and the card would render a fabricated target.
        PendingAdvice p = new PendingAdvice();
        p.record(4151, 0L, T0);
        p.record(4152, -5L, T0);
        assertNull(p.claim(4151, T0));
        assertNull(p.claim(4152, T0));
    }
}
