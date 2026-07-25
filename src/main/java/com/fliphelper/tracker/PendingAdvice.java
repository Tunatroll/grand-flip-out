/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */
package com.fliphelper.tracker;

import java.util.HashMap;
import java.util.Map;

/**
 * Advice waiting to be attached to the buy it produced (#249).
 *
 * Owner: "if someone buys something on a recommendation then you need to remember that —
 * people are buying and then the number they should've sold for isn't shown anymore to them."
 *
 * Keyed on itemId ALONE, deliberately not on price. Players routinely buy at a price other
 * than the advised one — they undercut, or the book moves between the advice and the offer —
 * so keying on price would silently fail to stamp exactly the lots most worth remembering.
 *
 * Claim-once (a second buy must not inherit the first buy's advice) and expiring, so stale
 * advice can never latch onto an unrelated later buy of the same item.
 */
public class PendingAdvice
{
    /** Advice older than this never attaches — long enough to place an offer, short enough not to drift. */
    private static final long TTL_MS = 30 * 60_000L;

    private final Map<Integer, long[]> pending = new HashMap<>();

    /**
     * Remember that {@code sellPrice} was advised for {@code itemId} at {@code nowMs}.
     * A non-positive price is ignored: an older server sends 0, and recording that would stamp
     * "advised 0" onto a lot and render a fabricated target.
     */
    public void record(int itemId, long sellPrice, long nowMs)
    {
        if (sellPrice <= 0)
        {
            return;
        }
        pending.put(itemId, new long[]{sellPrice, nowMs});
    }

    /**
     * Take the advice for this item, if any is still live. Removes it either way — expired
     * advice is dropped rather than left to accumulate.
     *
     * @return {@code {sellPrice, advisedAt}}, or {@code null} when there is none or it expired.
     */
    public long[] claim(int itemId, long nowMs)
    {
        long[] entry = pending.remove(itemId);
        if (entry == null || nowMs - entry[1] > TTL_MS)
        {
            return null;
        }
        return entry;
    }
}
