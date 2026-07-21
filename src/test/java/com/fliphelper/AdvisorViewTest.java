/*
 * Copyright (c) 2026, Tunatroll
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.fliphelper;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Two users (crabthecrabster, charismata_) asked to see a SINGLE flip instead of the
 * multi-slot basket — "way clearer to me". The basket-vs-single choice was hard-wired to
 * {@code freeSlots > 1} with no override. This pins the decision helper so a player can
 * force the single card regardless of how many GE slots are free.
 */
public class AdvisorViewTest
{
	@Test
	public void autoShowsBasketWithMultipleFreeSlots()
	{
		assertTrue(GrandFlipOutPlugin.shouldShowBasket(3, false));
		assertTrue(GrandFlipOutPlugin.shouldShowBasket(2, false));
	}

	@Test
	public void autoShowsSingleWithOneOrNoFreeSlot()
	{
		assertFalse(GrandFlipOutPlugin.shouldShowBasket(1, false));
		assertFalse(GrandFlipOutPlugin.shouldShowBasket(0, false));
	}

	@Test
	public void forceSingleAlwaysWins()
	{
		// The whole point of the setting: never a basket, however many slots are free.
		assertFalse(GrandFlipOutPlugin.shouldShowBasket(8, true));
		assertFalse(GrandFlipOutPlugin.shouldShowBasket(2, true));
		assertFalse(GrandFlipOutPlugin.shouldShowBasket(0, true));
	}
}
