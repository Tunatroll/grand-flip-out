/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Small, hand-curated catalog of common decantable potions for per-dose price arbitrage.
 *
 * <p>Many OSRS potions trade as four separate GE items — one per dose count (1, 2, 3 and
 * 4 doses). The same liquid is worth different amounts per dose depending on which variant
 * you trade, so a decanting service (Bob Barter at the GE, or a player decanter) can buy the
 * cheapest variant <em>per dose</em>, decant, and sell the priciest variant <em>per dose</em>.
 * This catalog holds the four dose item IDs so {@code ui.RecipePanel} can compute that spread
 * from live prices, after GE tax.
 *
 * <p>Every ID below was verified against the live OSRS Wiki item mapping
 * (prices.runescape.wiki/api/v1/osrs/mapping). The list is intentionally short and certain:
 * a wrong ID would silently mis-price a variant and over- or under-report profit, so only
 * potions whose four dose IDs are known are included.
 */
public final class DecantCatalog
{
    /**
     * One decantable potion: its display name and the four dose-variant item IDs, indexed by
     * (dose - 1) so {@code doseItemIds[0]} is the 1-dose item and {@code doseItemIds[3]} the
     * 4-dose item.
     */
    public static final class Potion
    {
        private final String name;
        private final int[] doseItemIds; // index 0 = 1-dose ... index 3 = 4-dose

        Potion(String name, int oneDose, int twoDose, int threeDose, int fourDose)
        {
            this.name = name;
            this.doseItemIds = new int[]{oneDose, twoDose, threeDose, fourDose};
        }

        /** Display name, e.g. "Prayer potion". */
        public String getName()
        {
            return name;
        }

        /**
         * Number of doses in the {@code index}-th variant (1-based dose count): index 0 holds a
         * 1-dose potion, index 3 a 4-dose potion.
         */
        public static int dosesForIndex(int index)
        {
            return index + 1;
        }

        /** The four dose-variant item IDs, index 0 = 1-dose ... index 3 = 4-dose. */
        public int[] getDoseItemIds()
        {
            // Defensive copy so callers cannot mutate the shared catalog entry.
            return doseItemIds.clone();
        }
    }

    // ---- Prayer potion (1..4) ----
    private static final int PRAYER_1 = 143;
    private static final int PRAYER_2 = 141;
    private static final int PRAYER_3 = 139;
    private static final int PRAYER_4 = 2434;

    // ---- Super restore (1..4) ----
    private static final int SUPER_RESTORE_1 = 3030;
    private static final int SUPER_RESTORE_2 = 3028;
    private static final int SUPER_RESTORE_3 = 3026;
    private static final int SUPER_RESTORE_4 = 3024;

    // ---- Saradomin brew (1..4) ----
    private static final int SARA_BREW_1 = 6693;
    private static final int SARA_BREW_2 = 6691;
    private static final int SARA_BREW_3 = 6689;
    private static final int SARA_BREW_4 = 6685;

    // ---- Super combat potion (1..4) ----
    private static final int SUPER_COMBAT_1 = 12701;
    private static final int SUPER_COMBAT_2 = 12699;
    private static final int SUPER_COMBAT_3 = 12697;
    private static final int SUPER_COMBAT_4 = 12695;

    // ---- Ranging potion (1..4) ----
    private static final int RANGING_1 = 173;
    private static final int RANGING_2 = 171;
    private static final int RANGING_3 = 169;
    private static final int RANGING_4 = 2444;

    // ---- Magic potion (1..4) ----
    private static final int MAGIC_1 = 3046;
    private static final int MAGIC_2 = 3044;
    private static final int MAGIC_3 = 3042;
    private static final int MAGIC_4 = 3040;

    // ---- Stamina potion (1..4) ----
    private static final int STAMINA_1 = 12631;
    private static final int STAMINA_2 = 12629;
    private static final int STAMINA_3 = 12627;
    private static final int STAMINA_4 = 12625;

    private static final List<Potion> POTIONS;
    static
    {
        List<Potion> p = new ArrayList<>();
        p.add(new Potion("Prayer potion", PRAYER_1, PRAYER_2, PRAYER_3, PRAYER_4));
        p.add(new Potion("Super restore", SUPER_RESTORE_1, SUPER_RESTORE_2, SUPER_RESTORE_3, SUPER_RESTORE_4));
        p.add(new Potion("Saradomin brew", SARA_BREW_1, SARA_BREW_2, SARA_BREW_3, SARA_BREW_4));
        p.add(new Potion("Super combat potion", SUPER_COMBAT_1, SUPER_COMBAT_2, SUPER_COMBAT_3, SUPER_COMBAT_4));
        p.add(new Potion("Ranging potion", RANGING_1, RANGING_2, RANGING_3, RANGING_4));
        p.add(new Potion("Magic potion", MAGIC_1, MAGIC_2, MAGIC_3, MAGIC_4));
        p.add(new Potion("Stamina potion", STAMINA_1, STAMINA_2, STAMINA_3, STAMINA_4));
        POTIONS = Collections.unmodifiableList(p);
    }

    private DecantCatalog()
    {
    }

    /** Immutable list of all bundled decantable potions. */
    public static List<Potion> all()
    {
        return POTIONS;
    }
}
