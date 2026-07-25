/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */
package com.fliphelper.ui;

import com.fliphelper.model.FlipItem;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * #249 — which target the open-position card shows.
 *
 * `buildFlipCard` renders a LIVE `expectedSell` that drifts with the market, and never showed
 * the REMEMBERED target, so the number the player was told disappeared the moment they acted.
 * Resolution order: what the advisor advised, else the market high captured at buy time, else
 * nothing at all — never a fabricated target.
 */
public class TargetLineTest
{
    @Test
    public void advisedTargetWinsOverTheFrozenMarketPrice()
    {
        FlipItem f = FlipItem.builder().frozenSellPrice(1088L).advisedSellPrice(1102L).build();
        assertEquals(1102L, GrandFlipOutPanel.rememberedTarget(f));
    }

    @Test
    public void fallsBackToFrozenWhenTheBuyWasNotAdvised()
    {
        FlipItem f = FlipItem.builder().frozenSellPrice(1088L).build();
        assertEquals(1088L, GrandFlipOutPanel.rememberedTarget(f));
    }

    @Test
    public void omitsTheLineWhenThereIsNoTargetAtAll()
    {
        assertEquals(0L, GrandFlipOutPanel.rememberedTarget(FlipItem.builder().build()));
    }

    @Test
    public void isNullSafeAndNeverNegative()
    {
        assertEquals(0L, GrandFlipOutPanel.rememberedTarget(null));
        assertEquals(0L, GrandFlipOutPanel.rememberedTarget(
            FlipItem.builder().frozenSellPrice(-5L).build()));
    }
}
