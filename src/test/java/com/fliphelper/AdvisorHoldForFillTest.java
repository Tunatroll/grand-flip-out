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
 * kahubu (#feedback, 2026-07-29): "I'm clicking 'fill offer' but there's no visual feedback. I
 * don't know if it's worked, then when I'm going to buy the item that the Advisor suggested, it
 * changes mid putting all the prices in." (#225 S4)
 *
 * Two defects in one report. (1) Feedback: clicking Fill offer only printed a GAME CHAT line —
 * nothing changed in the panel the player was looking at, so "I don't know if it's worked". The
 * clicked button now relabels to the armed copy when the fill actually armed, and stays put when
 * the auto-fill setting is off (the chat line explains that case). (2) Rotation: the advisor
 * card had NO hold between Fill-offer and offer placement, so any OTHER slot's offer event
 * refetched and swapped the card mid-entry. Arming now starts the SAME hold the sell-side fix
 * uses (AdvisorHoldForSellTest), bounded by the fill-arm TTL so an abandoned arm can never pin
 * the advisor forever; a terminal BUY upgrades it to the unbounded held-for-sell state, and the
 * sell-side release states clear it exactly as before. Advisor Fill-offer button only — the
 * hotkey arms the ACTIVE slot's context, not the advisor card, and takes no hold.
 */
public class AdvisorHoldForFillTest
{
	@Test
	public void anArmSourcedHoldLapsesAfterTheFillArmTtl()
	{
		long armedAt = 1_000_000L;
		// Fresh arm: held.
		assertFalse(GrandFlipOutPlugin.armHoldExpired(armedAt, armedAt + 1_000));
		// One tick short of the TTL: still held.
		assertFalse(GrandFlipOutPlugin.armHoldExpired(armedAt, armedAt + GrandFlipOutPlugin.GE_FILL_ARM_TTL_MS));
		// Beyond the TTL: an abandoned arm must release — a stale hold would strand the advisor.
		assertTrue(GrandFlipOutPlugin.armHoldExpired(armedAt, armedAt + GrandFlipOutPlugin.GE_FILL_ARM_TTL_MS + 1));
	}

	@Test
	public void aSellSourcedHoldNeverLapsesOnTheArmTtl()
	{
		// -1 marks a hold that did NOT come from an arm (the S1 held-for-sell state) — it pins
		// the sell numbers until the sell offer exists, however long that takes.
		assertFalse(GrandFlipOutPlugin.armHoldExpired(-1, Long.MAX_VALUE));
		assertFalse(GrandFlipOutPlugin.armHoldExpired(0, Long.MAX_VALUE));
	}

	@Test
	public void theFlipLifecycleUpgradesAndReleasesTheHoldExactlyAsBefore()
	{
		// Placing the armed offer (BUYING) neither holds nor releases — the entry is done, the
		// TTL winds the arm-hold down naturally, and S1 takes over only at the terminal BUY.
		assertFalse(GrandFlipOutPlugin.shouldHoldAdvisorForSell(GrandExchangeOfferState.BUYING, true));
		assertFalse(GrandFlipOutPlugin.releasesAdvisorHold(GrandExchangeOfferState.BUYING));
		// Terminal BUY upgrades to the unbounded held-for-sell state.
		assertTrue(GrandFlipOutPlugin.shouldHoldAdvisorForSell(GrandExchangeOfferState.BOUGHT, true));
		// Creating the sell releases — unchanged from AdvisorHoldForSellTest.
		assertTrue(GrandFlipOutPlugin.releasesAdvisorHold(GrandExchangeOfferState.SELLING));
	}

	@Test
	public void theClickedButtonSaysArmedOnlyWhenTheFillActuallyArmed()
	{
		// "I don't know if it's worked" — the feedback lives on the button the player clicked.
		assertEquals("Armed ✓ — open the GE offer",
			com.fliphelper.ui.AdvisorPanel.fillButtonFeedback(true));
		// Auto-fill off: nothing armed, the label must not claim otherwise (the chat line
		// names the setting to enable).
		assertEquals("Fill offer", com.fliphelper.ui.AdvisorPanel.fillButtonFeedback(false));
	}
}
