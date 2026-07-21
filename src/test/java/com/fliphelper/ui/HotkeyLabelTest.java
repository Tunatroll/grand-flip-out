/*
 * Copyright (c) 2026, Tunatroll
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.fliphelper.ui;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * The overlay hard-coded "Advisor (Ctrl+Space):" while every hotkey ships Keybind.NOT_SET, so it
 * taught players a shortcut that does nothing — the fastest possible way to look broken.
 * The label must reflect the ACTUAL binding, and say nothing about keys when none is bound.
 */
public class HotkeyLabelTest
{
	@Test
	public void unboundHotkeyAdvertisesNoShortcut()
	{
		assertEquals("Advisor:", UiText.hotkeyLabel("Advisor", "Not set"));
	}

	@Test
	public void boundHotkeyShowsTheRealBinding()
	{
		assertEquals("Advisor (ctrl+F1):", UiText.hotkeyLabel("Advisor", "ctrl+F1"));
	}

	@Test
	public void nullOrBlankBindingIsTreatedAsUnbound()
	{
		assertEquals("Advisor:", UiText.hotkeyLabel("Advisor", null));
		assertEquals("Advisor:", UiText.hotkeyLabel("Advisor", "  "));
	}
}
