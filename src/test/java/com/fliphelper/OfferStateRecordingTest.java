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
 * crab (#support): "the flips I'm making are not stored locally. I have 1 flip even though I did
 * multiple within a session - usually I don't wait till the last item is sold before I grab the
 * cash." The GE handler recorded a fill ONLY on BOUGHT/SOLD, so a partially-sold offer that the
 * player collected + cancelled (SELLING -> CANCELLED_SELL, never SOLD) dropped that sale entirely.
 * These pin the recording gate: a terminal offer state — completed OR cancelled — records whatever
 * quantity actually filled, and the buy/sell side is read from the state, not from completion.
 */
public class OfferStateRecordingTest
{
	@Test
	public void cancelledOffersAreTerminalForRecording()
	{
		// The whole bug: a cancelled offer must be a chance to record its filled quantity.
		assertTrue(GrandFlipOutPlugin.isTerminalOfferState(GrandExchangeOfferState.CANCELLED_SELL));
		assertTrue(GrandFlipOutPlugin.isTerminalOfferState(GrandExchangeOfferState.CANCELLED_BUY));
		assertTrue(GrandFlipOutPlugin.isTerminalOfferState(GrandExchangeOfferState.SOLD));
		assertTrue(GrandFlipOutPlugin.isTerminalOfferState(GrandExchangeOfferState.BOUGHT));
	}

	@Test
	public void activeAndEmptyStatesDoNotTriggerARecord()
	{
		// Recording on every partial tick would spam records; the delta is captured at the terminal.
		assertFalse(GrandFlipOutPlugin.isTerminalOfferState(GrandExchangeOfferState.BUYING));
		assertFalse(GrandFlipOutPlugin.isTerminalOfferState(GrandExchangeOfferState.SELLING));
		assertFalse(GrandFlipOutPlugin.isTerminalOfferState(GrandExchangeOfferState.EMPTY));
	}

	@Test
	public void buySideIsReadFromTheStateNotFromCompletion()
	{
		assertTrue(GrandFlipOutPlugin.isBuySideOfferState(GrandExchangeOfferState.BOUGHT));
		assertTrue(GrandFlipOutPlugin.isBuySideOfferState(GrandExchangeOfferState.CANCELLED_BUY));
		assertFalse(GrandFlipOutPlugin.isBuySideOfferState(GrandExchangeOfferState.SOLD));
		assertFalse(GrandFlipOutPlugin.isBuySideOfferState(GrandExchangeOfferState.CANCELLED_SELL));
	}
}
