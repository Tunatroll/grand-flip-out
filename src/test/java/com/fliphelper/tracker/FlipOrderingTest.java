/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */
package com.fliphelper.tracker;

import com.fliphelper.model.FlipItem;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class FlipOrderingTest
{
    /** itemId doubles as the buy-time marker so the asserted order is unambiguous. */
    private static FlipItem at(long epochSec)
    {
        return FlipItem.builder()
            .itemId((int) epochSec)
            .buyTime(epochSec == 0 ? null : Instant.ofEpochSecond(epochSec))
            .build();
    }

    @Test
    public void activeFlipsAreNewestFirstByBuyTime()
    {
        // Supplied in mixed order with one un-stamped (null) buy time; the result must be
        // newest-buy-time first, and the null-buyTime flip must sink to the bottom.
        List<FlipItem> in = Arrays.asList(at(100), at(300), at(200), at(0));
        List<FlipItem> out = FlipTracker.newestFirstByBuyTime(in);
        assertEquals(300, out.get(0).getItemId());
        assertEquals(200, out.get(1).getItemId());
        assertEquals(100, out.get(2).getItemId());
        assertEquals(0, out.get(3).getItemId());
    }

    @Test
    public void completedFlipsReverseToNewestFirst()
    {
        // completedFlips is append-ordered (oldest at index 0); newestFirst reverses it so the
        // most recently completed flip lands on top of the trade log.
        List<FlipItem> appendOrder = Arrays.asList(at(100), at(200), at(300));
        List<FlipItem> out = FlipTracker.newestFirst(appendOrder);
        assertEquals(300, out.get(0).getItemId());
        assertEquals(200, out.get(1).getItemId());
        assertEquals(100, out.get(2).getItemId());
    }
}
