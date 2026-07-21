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
 * Single flip is the DEFAULT — one clear next action at a time (owner directive: "think single
 * slot, focus on that, not filling the 8 slots"; two users also asked for it). The multi-slot
 * basket only appears when the player opts in via "Plan all my free GE slots" AND has more than
 * one GE slot free. This pins that decision helper.
 */
public class AdvisorViewTest
{
	@Test
	public void singleFlipIsTheDefaultEvenWithManyFreeSlots()
	{
		// multiSlot=false (the default) -> never a basket, however many slots are free.
		assertFalse(GrandFlipOutPlugin.shouldShowBasket(8, false));
		assertFalse(GrandFlipOutPlugin.shouldShowBasket(3, false));
		assertFalse(GrandFlipOutPlugin.shouldShowBasket(2, false));
		assertFalse(GrandFlipOutPlugin.shouldShowBasket(0, false));
	}

	@Test
	public void basketShowsOnlyWhenOptedInAndMultipleSlotsFree()
	{
		assertTrue(GrandFlipOutPlugin.shouldShowBasket(3, true));
		assertTrue(GrandFlipOutPlugin.shouldShowBasket(2, true));
	}

	@Test
	public void optedInButOneOrNoFreeSlotStaysSingle()
	{
		// A basket needs more than one slot to coordinate across; one free slot is a single action.
		assertFalse(GrandFlipOutPlugin.shouldShowBasket(1, true));
		assertFalse(GrandFlipOutPlugin.shouldShowBasket(0, true));
	}
}
