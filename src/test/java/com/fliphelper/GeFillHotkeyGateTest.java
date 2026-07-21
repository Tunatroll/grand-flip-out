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
 * Copilot ships a bare "E" as the fill key. A bare key is dangerous here because RuneLite's
 * KeyManager has NO chatbox-typing guard (verified against client 1.12.33 bytecode) — a bare
 * key fires while the player types in chat OR in the GE item-SEARCH box. So a bare fill key may
 * only be consumed when a GE numeric price/quantity input is open; anywhere else it must pass
 * through. A modifier combo (a user's own choice) is safe to act on anywhere. These two pure
 * helpers carry that safety and are the only headlessly-verifiable part of the bare-E change.
 */
public class GeFillHotkeyGateTest
{
	@Test
	public void numericPromptsAreRecognised()
	{
		assertTrue(GrandFlipOutPlugin.isGeNumericPrompt("how many do you wish to buy?"));
		assertTrue(GrandFlipOutPlugin.isGeNumericPrompt("set a price for each item:"));
	}

	@Test
	public void theItemSearchBoxIsNotANumericPrompt()
	{
		// The search box is where a player types letters like "E" in "rune platE" — never
		// treat it as a fill target, or the bare key eats item-search typing.
		assertFalse(GrandFlipOutPlugin.isGeNumericPrompt("search for an item"));
		assertFalse(GrandFlipOutPlugin.isGeNumericPrompt(""));
		assertFalse(GrandFlipOutPlugin.isGeNumericPrompt(null));
	}

	@Test
	public void bareKeyOnlyConsumedInsideANumericInput()
	{
		assertFalse("bare key outside a numeric input must pass through",
			GrandFlipOutPlugin.shouldConsumeFillHotkey(true, false));
		assertTrue("bare key inside a numeric input fills + consumes",
			GrandFlipOutPlugin.shouldConsumeFillHotkey(true, true));
	}

	@Test
	public void modifierComboActsAnywhere()
	{
		// A user-chosen Ctrl/Alt combo can't collide with chat typing, so keep arm-from-anywhere.
		assertTrue(GrandFlipOutPlugin.shouldConsumeFillHotkey(false, false));
		assertTrue(GrandFlipOutPlugin.shouldConsumeFillHotkey(false, true));
	}
}
