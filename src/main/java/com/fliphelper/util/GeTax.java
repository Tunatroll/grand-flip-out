/*
 * Copyright (c) 2026, Tunatroll
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
        // OSRS Wiki "Exempt from tax" (verified 2026-06). Only certain IDs are
        // listed; watering-can fill variants and Civitas illa fortis teleport
        // are omitted (IDs not individually verified). Keep in sync with
        // server/ge-tax.js and intelligence config.GE_TAX_EXEMPT_ITEMS.
        Set<Integer> exempt = new HashSet<>();
        exempt.add(OLD_SCHOOL_BOND);
        // Tools (exempt since 9 Dec 2021)
        exempt.add(1755);   // Chisel
        exempt.add(5325);   // Gardening trowel
        exempt.add(1785);   // Glassblowing pipe
        exempt.add(2347);   // Hammer
        exempt.add(1733);   // Needle
        exempt.add(233);    // Pestle and mortar
        exempt.add(5341);   // Rake
        exempt.add(8794);   // Saw
        exempt.add(5329);   // Secateurs
        exempt.add(5343);   // Seed dibber
        exempt.add(1735);   // Shears
        exempt.add(952);    // Spade
        // Energy potion (added 29 May 2025), all doses
        exempt.add(3008);   // Energy potion(4)
        exempt.add(3010);   // Energy potion(3)
        exempt.add(3012);   // Energy potion(2)
        exempt.add(3014);   // Energy potion(1)
        // Low-tier ammo / runes
        exempt.add(882);    // Bronze arrow
        exempt.add(884);    // Iron arrow
        exempt.add(886);    // Steel arrow
        exempt.add(806);    // Bronze dart
        exempt.add(807);    // Iron dart
        exempt.add(808);    // Steel dart
        exempt.add(558);    // Mind rune
        // Basic food
        exempt.add(365);    // Bass
        exempt.add(2309);   // Bread
        exempt.add(1891);   // Cake
        exempt.add(2140);   // Cooked chicken
        exempt.add(2142);   // Cooked meat
        exempt.add(347);    // Herring
        exempt.add(379);    // Lobster
        exempt.add(355);    // Mackerel
        exempt.add(2327);   // Meat pie
        exempt.add(351);    // Pike
        exempt.add(329);    // Salmon
        exempt.add(315);    // Shrimps
        exempt.add(361);    // Tuna
        // Teleports
        exempt.add(8011);   // Ardougne teleport
        exempt.add(8010);   // Camelot teleport
        exempt.add(8009);   // Falador teleport
        exempt.add(19651);  // Kourend castle teleport
        exempt.add(8008);   // Lumbridge teleport
        exempt.add(8007);   // Varrock teleport
        exempt.add(8013);   // Teleport to house
        exempt.add(3853);   // Games necklace(8)
        exempt.add(2552);   // Ring of dueling(8)
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
