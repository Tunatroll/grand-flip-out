/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The anonymous unlock CTA quotes the CONCRETE value being hidden — the top-ranked
 * members item and its tax-net PER-ITEM margin — instead of only a count. Funnel
 * ground (30d, read 2026-08-05): plugin_unlock_cta_seen 614 → clicked 55; the prompt
 * is the dominant acquisition surface and "83 members items hidden" names a quantity
 * but no value.
 *
 * Honesty rails pinned here:
 *  - the value line renders ONLY with a real name AND a positive tax-net margin —
 *    omitted entirely otherwise (no placeholder, no zero dressed up);
 *  - the number is PER-ITEM net, never ×buy-limit (the fantasy-profit class);
 *  - formatGp already carries the unit for sub-1K amounts, so the copy adds no
 *    "gp" of its own (the copy gate's 'gp gp' composition class).
 */
public class UnlockCtaValueTest
{
	@Test
	public void valueLineRendersWithTopItemAndNetMargin()
	{
		String html = GrandFlipOutPanel.unlockCtaHtml(83, "Dragon bones", 412L);
		assertTrue(html.contains("83 members items hidden"));
		assertTrue("names the top hidden item", html.contains("Dragon bones"));
		assertTrue("quotes the tax-net per-item margin", html.contains("412 gp/item net"));
		assertFalse("no gp-gp doubling", html.contains("gp gp"));
	}

	@Test
	public void millionRangeUsesCompactUnitOnce()
	{
		String html = GrandFlipOutPanel.unlockCtaHtml(5, "Twisted bow", 1_200_000L);
		assertTrue(html.contains("1.2M/item net"));
		assertFalse(html.contains("gp gp"));
	}

	@Test
	public void omittedWithoutPositiveMarginOrName()
	{
		assertFalse("zero margin omits the line",
			GrandFlipOutPanel.unlockCtaHtml(9, "Dragon bones", 0L).contains("Dragon bones"));
		assertFalse("negative margin omits the line",
			GrandFlipOutPanel.unlockCtaHtml(9, "Dragon bones", -5L).contains("Dragon bones"));
		assertFalse("missing name omits the line",
			GrandFlipOutPanel.unlockCtaHtml(9, null, 412L).contains("/item net"));
		assertTrue("the count line always renders",
			GrandFlipOutPanel.unlockCtaHtml(9, null, 412L).contains("9 members items hidden"));
	}
}
