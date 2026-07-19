/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.util;

import com.fliphelper.model.Recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Small, hand-curated catalog of well-known combination/recipe items for set-vs-pieces
 * arbitrage. Every item ID below was verified against the live OSRS Wiki item mapping
 * (prices.runescape.wiki/api/v1/osrs/mapping). The catalog is intentionally kept short
 * and correct: a wrong ID would silently mis-price a recipe, so only relationships whose
 * IDs are certain are included.
 *
 * <p>Two recipe families are covered:
 * <ul>
 *   <li><b>Barrows armour sets</b> — the placeholder "armour set" item (sold as a unit on
 *       the GE) versus its four equipment pieces. Combining/decombining is free at a
 *       banker, so the spread between set and pieces is pure arbitrage.</li>
 *   <li><b>Godswords</b> — a godsword blade + a god-specific hilt combine into the finished
 *       godsword (free attach). Also includes the blade itself = the three godsword shards.</li>
 * </ul>
 */
public final class RecipeCatalog
{
    // ---- Barrows armour set placeholders (sold as a single GE item) ----
    private static final int DHAROK_SET = 12877;
    private static final int AHRIM_SET  = 12881;
    private static final int GUTHAN_SET = 12873;
    private static final int KARIL_SET  = 12883;
    private static final int TORAG_SET  = 12879;
    private static final int VERAC_SET  = 12875;

    // ---- Dharok's pieces ----
    private static final int DHAROK_HELM      = 4716;
    private static final int DHAROK_BODY      = 4720;
    private static final int DHAROK_LEGS      = 4722;
    private static final int DHAROK_GREATAXE  = 4718;

    // ---- Ahrim's pieces ----
    private static final int AHRIM_HOOD     = 4708;
    private static final int AHRIM_TOP      = 4712;
    private static final int AHRIM_SKIRT    = 4714;
    private static final int AHRIM_STAFF    = 4710;

    // ---- Guthan's pieces ----
    private static final int GUTHAN_HELM    = 4724;
    private static final int GUTHAN_BODY    = 4728;
    private static final int GUTHAN_SKIRT   = 4730;
    private static final int GUTHAN_SPEAR   = 4726;

    // ---- Karil's pieces ----
    private static final int KARIL_COIF     = 4732;
    private static final int KARIL_TOP      = 4736;
    private static final int KARIL_SKIRT    = 4738;
    private static final int KARIL_XBOW     = 4734;

    // ---- Torag's pieces ----
    private static final int TORAG_HELM     = 4745;
    private static final int TORAG_BODY     = 4749;
    private static final int TORAG_LEGS     = 4751;
    private static final int TORAG_HAMMERS  = 4747;

    // ---- Verac's pieces ----
    private static final int VERAC_HELM     = 4753;
    private static final int VERAC_BRASSARD = 4757;
    private static final int VERAC_SKIRT    = 4759;
    private static final int VERAC_FLAIL    = 4755;

    // ---- Godsword components ----
    private static final int GODSWORD_BLADE = 11798;
    private static final int ARMADYL_HILT   = 11810;
    private static final int BANDOS_HILT    = 11812;
    private static final int SARADOMIN_HILT = 11814;
    private static final int ZAMORAK_HILT   = 11816;
    private static final int ARMADYL_GS     = 11802;
    private static final int BANDOS_GS      = 11804;
    private static final int SARADOMIN_GS   = 11806;
    private static final int ZAMORAK_GS     = 11808;
    private static final int GS_SHARD_1     = 11818;
    private static final int GS_SHARD_2     = 11820;
    private static final int GS_SHARD_3     = 11822;

    private static final List<Recipe> RECIPES;
    static
    {
        List<Recipe> r = new ArrayList<>();

        // Barrows sets: set placeholder = helm + body + legs + weapon
        r.add(new Recipe("Dharok's armour set", DHAROK_SET,
            Arrays.asList(DHAROK_HELM, DHAROK_BODY, DHAROK_LEGS, DHAROK_GREATAXE),
            "Set vs 4 pieces; combine/split free at a banker."));
        r.add(new Recipe("Ahrim's armour set", AHRIM_SET,
            Arrays.asList(AHRIM_HOOD, AHRIM_TOP, AHRIM_SKIRT, AHRIM_STAFF),
            "Set vs 4 pieces; combine/split free at a banker."));
        r.add(new Recipe("Guthan's armour set", GUTHAN_SET,
            Arrays.asList(GUTHAN_HELM, GUTHAN_BODY, GUTHAN_SKIRT, GUTHAN_SPEAR),
            "Set vs 4 pieces; combine/split free at a banker."));
        r.add(new Recipe("Karil's armour set", KARIL_SET,
            Arrays.asList(KARIL_COIF, KARIL_TOP, KARIL_SKIRT, KARIL_XBOW),
            "Set vs 4 pieces; combine/split free at a banker."));
        r.add(new Recipe("Torag's armour set", TORAG_SET,
            Arrays.asList(TORAG_HELM, TORAG_BODY, TORAG_LEGS, TORAG_HAMMERS),
            "Set vs 4 pieces; combine/split free at a banker."));
        r.add(new Recipe("Verac's armour set", VERAC_SET,
            Arrays.asList(VERAC_HELM, VERAC_BRASSARD, VERAC_SKIRT, VERAC_FLAIL),
            "Set vs 4 pieces; combine/split free at a banker."));

        // Godswords: blade + hilt = finished godsword (free attach)
        r.add(new Recipe("Armadyl godsword", ARMADYL_GS,
            Arrays.asList(GODSWORD_BLADE, ARMADYL_HILT),
            "Godsword blade + Armadyl hilt; attaching is free."));
        r.add(new Recipe("Bandos godsword", BANDOS_GS,
            Arrays.asList(GODSWORD_BLADE, BANDOS_HILT),
            "Godsword blade + Bandos hilt; attaching is free."));
        r.add(new Recipe("Saradomin godsword", SARADOMIN_GS,
            Arrays.asList(GODSWORD_BLADE, SARADOMIN_HILT),
            "Godsword blade + Saradomin hilt; attaching is free."));
        r.add(new Recipe("Zamorak godsword", ZAMORAK_GS,
            Arrays.asList(GODSWORD_BLADE, ZAMORAK_HILT),
            "Godsword blade + Zamorak hilt; attaching is free."));

        // Godsword blade itself = the three shards (smith on anvil, free)
        r.add(new Recipe("Godsword blade", GODSWORD_BLADE,
            Arrays.asList(GS_SHARD_1, GS_SHARD_2, GS_SHARD_3),
            "Three godsword shards smith into the blade for free."));

        RECIPES = Collections.unmodifiableList(r);
    }

    private RecipeCatalog()
    {
    }

    /** Immutable list of all bundled recipes. */
    public static List<Recipe> all()
    {
        return RECIPES;
    }
}
