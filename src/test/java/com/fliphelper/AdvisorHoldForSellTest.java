/*
 * Copyright (c) 2026, Tunatroll
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.fliphelper;

import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * crab (#support, 2026-07-22): "When I finish a flip, I get info from advisor. But once I take the
 * offer from GE, this item disappears and the next one appears - now I know I need to remember the
 * numbers that are there but it would be cool if it was shown till I create sell offer. Or
 * alternatively, a button to click 'I finished selling, show me next one'."
 *
 * The advisor refreshed on EVERY GE offer change, so collecting a completed BUY replaced the card
 * carrying the sell price the player had not used yet — forcing them to memorise it before
 * collecting. These pin the hold gate: a terminal BUY holds the card, creating the SELL releases
 * it, and everything else advances exactly as before.
 */
public class AdvisorHoldForSellTest
{
	@Test
	public void collectingACompletedBuyHoldsTheCard()
	{
		// The whole bug: this is the moment the sell numbers were being thrown away.
		assertTrue(GrandFlipOutPlugin.shouldHoldAdvisorForSell(GrandExchangeOfferState.BOUGHT, true));
		// A cancelled buy is still a collect — the player holds items they now need to sell.
		assertTrue(GrandFlipOutPlugin.shouldHoldAdvisorForSell(GrandExchangeOfferState.CANCELLED_BUY, true));
	}

	@Test
	public void sellSideNeverHolds()
	{
		// The flip is finished — advancing to the next suggestion is the correct behaviour.
		assertFalse(GrandFlipOutPlugin.shouldHoldAdvisorForSell(GrandExchangeOfferState.SOLD, true));
		assertFalse(GrandFlipOutPlugin.shouldHoldAdvisorForSell(GrandExchangeOfferState.CANCELLED_SELL, true));
	}

	@Test
	public void inProgressAndEmptyStatesNeverHold()
	{
		// Only a TERMINAL buy is a collect; a partial tick must not freeze the advisor.
		assertFalse(GrandFlipOutPlugin.shouldHoldAdvisorForSell(GrandExchangeOfferState.BUYING, true));
		assertFalse(GrandFlipOutPlugin.shouldHoldAdvisorForSell(GrandExchangeOfferState.SELLING, true));
		assertFalse(GrandFlipOutPlugin.shouldHoldAdvisorForSell(GrandExchangeOfferState.EMPTY, true));
	}

	@Test
	public void theConfigToggleFullyRestoresTheOldBehaviour()
	{
		// Opt-out must be total: with the setting off, nothing ever holds.
		for (GrandExchangeOfferState s : GrandExchangeOfferState.values())
		{
			assertFalse("no state may hold when the setting is off: " + s,
				GrandFlipOutPlugin.shouldHoldAdvisorForSell(s, false));
		}
	}

	@Test
	public void creatingTheSellOfferReleasesTheHold()
	{
		// "shown till I create sell offer" — SELLING for the held item is the release signal.
		assertTrue(GrandFlipOutPlugin.releasesAdvisorHold(GrandExchangeOfferState.SELLING));
		// ...and so is finishing the sale outright, in case the release tick is missed.
		assertTrue(GrandFlipOutPlugin.releasesAdvisorHold(GrandExchangeOfferState.SOLD));
		assertTrue(GrandFlipOutPlugin.releasesAdvisorHold(GrandExchangeOfferState.CANCELLED_SELL));
	}

	@Test
	public void buySideDoesNotReleaseTheHold()
	{
		// A second buy completing must not knock the held sell numbers off the screen.
		assertFalse(GrandFlipOutPlugin.releasesAdvisorHold(GrandExchangeOfferState.BOUGHT));
		assertFalse(GrandFlipOutPlugin.releasesAdvisorHold(GrandExchangeOfferState.BUYING));
		assertFalse(GrandFlipOutPlugin.releasesAdvisorHold(GrandExchangeOfferState.CANCELLED_BUY));
		assertFalse(GrandFlipOutPlugin.releasesAdvisorHold(GrandExchangeOfferState.EMPTY));
	}
}
