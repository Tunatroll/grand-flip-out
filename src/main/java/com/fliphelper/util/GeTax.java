/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Single source of truth for Grand Exchange sell tax.
 *
 * <p>Rules (current as of 2026): 2% of the sale price is taken from the seller,
 * rounded down, capped at 5,000,000 gp <em>per item</em> (so items selling above
 * 250,000,000 gp each are taxed under 2%). Items selling under 50 gp pay no tax
 * (2% of 49 = 0.98, which floors to 0). A small set of items are fully exempt.
 *
 * <p>The exempt set is intentionally conservative: only items whose IDs are
 * certain are listed, because a wrong ID would silently zero-tax a normal item
 * and over-report profit. Extend it with verified IDs from the OSRS Wiki
 * (Update:Grand_Exchange_Tax_&amp;_Item_Sink) as needed — Jagex updates the list.
 */
public final class GeTax
{
    public static final double RATE = 0.02;
    public static final long CAP_PER_ITEM = 5_000_000L;

    /** Old School Bond — the main tradeable, tax-exempt item flippers care about. */
    public static final int OLD_SCHOOL_BOND = 13190;

    private static final Set<Integer> EXEMPT_ITEMS;
    static
    {
        Set<Integer> exempt = new HashSet<>();
        exempt.add(OLD_SCHOOL_BOND);
        EXEMPT_ITEMS = Collections.unmodifiableSet(exempt);
    }

    private GeTax()
    {
    }

    public static boolean isExempt(int itemId)
    {
        return EXEMPT_ITEMS.contains(itemId);
    }

    /**
     * GE sell tax for selling {@code quantity} of {@code itemId} at
     * {@code pricePerItem} gp each. Returns 0 for exempt items, non-positive
     * inputs, or per-item prices below the taxable threshold.
     */
    public static long tax(int itemId, long pricePerItem, int quantity)
    {
        if (quantity <= 0 || pricePerItem <= 0 || isExempt(itemId))
        {
            return 0;
        }
        long perItem = Math.min((long) (pricePerItem * RATE), CAP_PER_ITEM);
        return perItem * quantity;
    }
}
