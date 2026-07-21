/*
 * Copyright (c) 2026, Tunatroll
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.fliphelper;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * Pressing the Price-Fill hotkey while "GE offer auto-fill" is off used to do NOTHING: the
 * dispatch read {@code config.enableGePriceFill() && priceFillHotkey().matches(e)}, so a bound
 * key fell through the else-if chain silently — no fill, no message, no reason. A player who
 * bound the key concluded the plugin was broken.
 *
 * armOfferFill already handles this state correctly (armChatMessage(..., false) names the
 * setting to enable). This pins the sibling so both surfaces say the same true thing.
 */
public class GeFillHotkeyDisabledTest
{
	@Test
	public void namesTheExactSettingAPlayerMustEnable()
	{
		String msg = GrandFlipOutPlugin.priceFillDisabledMessage();
		// Must match the @ConfigItem name on enableGePriceFill verbatim, or it sends the
		// player hunting for a setting that does not exist under that label.
		assertTrue(msg, msg.contains("GE offer auto-fill"));
	}

	@Test
	public void doesNotClaimAnythingWasFilled()
	{
		String msg = GrandFlipOutPlugin.priceFillDisabledMessage().toLowerCase();
		assertTrue(msg, !msg.contains("filled"));
	}

	@Test
	public void agreesWithTheArmOfferFillSibling()
	{
		// Both off-state surfaces must point at the same setting.
		String armed = GrandFlipOutPlugin.armChatMessage("1,000 gp", 5, false);
		String hotkey = GrandFlipOutPlugin.priceFillDisabledMessage();
		assertTrue(armed, armed.contains("GE offer auto-fill"));
		assertTrue(hotkey, hotkey.contains("GE offer auto-fill"));
	}
}
