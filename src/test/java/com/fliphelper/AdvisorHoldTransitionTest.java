/*
 * Copyright (c) 2026, Tunatroll
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.fliphelper;

import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Review follow-up (R1) on the #225 S4 hold: the (advisorHeldItemId, advisorHoldFromArmAtMs)
 * VOLATILE PAIR was written non-atomically, so a GE offer event landing between the two writes
 * could read a half-set hold (wrong banner, or a just-set hold released — one event of the
 * pre-fix rotation). The hold is now ONE immutable AdvisorHold behind an AtomicReference, and
 * the offer-event decision is the pure transition below, applied via updateAndGet (CAS) — no
 * torn reads, no lost arm-click. These pin the whole transition table.
 */
public class AdvisorHoldTransitionTest
{
	private static final long NOW = 1_000_000L;

	@Test
	public void anArmedHoldSurvivesUnrelatedOfferEventsUntilTheTtl()
	{
		GrandFlipOutPlugin.AdvisorHold armed = new GrandFlipOutPlugin.AdvisorHold(4151, NOW);
		// Another slot's BUYING tick while the player fills — the reported rotation vector.
		GrandFlipOutPlugin.AdvisorHold next = GrandFlipOutPlugin.nextAdvisorHold(
			armed, GrandExchangeOfferState.BUYING, true, 9999, NOW + 1_000);
		assertTrue(next.active());
		assertEquals(4151, next.itemId);
		assertTrue(next.fromArm());
	}

	@Test
	public void anExpiredArmHoldLapsesOnTheNextEvent()
	{
		GrandFlipOutPlugin.AdvisorHold armed = new GrandFlipOutPlugin.AdvisorHold(4151, NOW);
		GrandFlipOutPlugin.AdvisorHold next = GrandFlipOutPlugin.nextAdvisorHold(
			armed, GrandExchangeOfferState.BUYING, true, 9999,
			NOW + GrandFlipOutPlugin.GE_FILL_ARM_TTL_MS + 1);
		assertFalse("an abandoned arm must not strand the advisor", next.active());
	}

	@Test
	public void aTerminalBuyUpgradesToTheUnboundedHeldForSellState()
	{
		GrandFlipOutPlugin.AdvisorHold armed = new GrandFlipOutPlugin.AdvisorHold(4151, NOW);
		GrandFlipOutPlugin.AdvisorHold next = GrandFlipOutPlugin.nextAdvisorHold(
			armed, GrandExchangeOfferState.BOUGHT, true, 4151, NOW + 1_000);
		assertTrue(next.active());
		assertFalse("upgraded hold must never lapse on the arm TTL", next.fromArm());
		// And it survives arbitrarily long — the S1 semantic.
		GrandFlipOutPlugin.AdvisorHold later = GrandFlipOutPlugin.nextAdvisorHold(
			next, GrandExchangeOfferState.BUYING, true, 7777, NOW + 10 * GrandFlipOutPlugin.GE_FILL_ARM_TTL_MS);
		assertTrue(later.active());
	}

	@Test
	public void sellSideStatesReleaseEverything()
	{
		GrandFlipOutPlugin.AdvisorHold held = new GrandFlipOutPlugin.AdvisorHold(4151, -1L);
		assertFalse(GrandFlipOutPlugin.nextAdvisorHold(
			held, GrandExchangeOfferState.SELLING, true, 4151, NOW).active());
		assertFalse(GrandFlipOutPlugin.nextAdvisorHold(
			held, GrandExchangeOfferState.SOLD, true, 4151, NOW).active());
		GrandFlipOutPlugin.AdvisorHold armed = new GrandFlipOutPlugin.AdvisorHold(4151, NOW);
		assertFalse(GrandFlipOutPlugin.nextAdvisorHold(
			armed, GrandExchangeOfferState.CANCELLED_SELL, true, 4151, NOW).active());
	}

	@Test
	public void noHoldStaysNoHoldThroughOrdinaryEvents()
	{
		GrandFlipOutPlugin.AdvisorHold none = GrandFlipOutPlugin.AdvisorHold.NONE;
		assertFalse(GrandFlipOutPlugin.nextAdvisorHold(
			none, GrandExchangeOfferState.BUYING, true, 4151, NOW).active());
		// Config opt-out: a terminal buy must NOT hold when the user disabled it.
		assertFalse(GrandFlipOutPlugin.nextAdvisorHold(
			none, GrandExchangeOfferState.BOUGHT, false, 4151, NOW).active());
	}
}
