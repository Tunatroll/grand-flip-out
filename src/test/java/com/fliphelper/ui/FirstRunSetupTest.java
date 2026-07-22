/*
 * Copyright (c) 2026, Tunatroll
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.fliphelper.ui;

import com.fliphelper.model.Suggestion;
import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A fresh install ships three consent toggles OFF, and GE offer auto-fill had NO in-plugin
 * enable path at all — you had to find it in the RuneLite config screen. The first-run card
 * is the one place a new user looks, so the flagship auto-fill must be reachable there as an
 * explicit, separately-ticked opt-in. This pins that the checkbox actually drives the callback.
 */
public class FirstRunSetupTest
{
	private static final AdvisorPanel.Listener NOOP = new AdvisorPanel.Listener()
	{
		@Override public void onSkip(int itemId) { }
		@Override public void onBlock(int itemId) { }
		@Override public void onPauseToggled(boolean paused) { }
		@Override public void onFillOffer(int itemId, long price, int quantity) { }
		@Override public void onFiltersChanged() { }
		@Override public void onNextFlip() { }
	};

	private static Suggestion flip()
	{
		return Suggestion.builder().action("BUY").itemId(560).itemName("Death rune")
			.price(205L).quantity(0).marginPer(9L).reasons(Arrays.asList("Buy at 205")).targetSlot(-1).build();
	}

	@Test
	public void tickingGeFillOptInRunsItsCallbackOnEnable() throws Exception
	{
		AtomicBoolean enabled = new AtomicBoolean(false);
		AtomicBoolean geFill = new AtomicBoolean(false);
		SwingUtilities.invokeAndWait(() ->
		{
			AdvisorPanel panel = new AdvisorPanel(NOOP);
			// serverEnabled=true path takes no modal disclosure dialog (safe to click headless).
			panel.showFirstRun(Arrays.asList(flip()), true,
				() -> enabled.set(true), () -> geFill.set(true));

			JCheckBox opt = firstCheckBox(panel);
			assertNotNull("GE auto-fill opt-in checkbox is present in the first-run card", opt);
			opt.setSelected(true);

			JButton enable = firstEnableButton(panel);
			assertNotNull("enable button is present", enable);
			enable.doClick();
		});
		assertTrue("enable callback ran", enabled.get());
		assertTrue("ticking the opt-in enabled GE auto-fill", geFill.get());
	}

	@Test
	public void leavingGeFillUntickedDoesNotEnableIt() throws Exception
	{
		AtomicBoolean geFill = new AtomicBoolean(false);
		SwingUtilities.invokeAndWait(() ->
		{
			AdvisorPanel panel = new AdvisorPanel(NOOP);
			panel.showFirstRun(Arrays.asList(flip()), true, () -> { }, () -> geFill.set(true));
			firstEnableButton(panel).doClick(); // checkbox left unticked
		});
		assertFalse("GE auto-fill stays off unless explicitly ticked", geFill.get());
	}

	private static JCheckBox firstCheckBox(Container c)
	{
		for (Component comp : c.getComponents())
		{
			if (comp instanceof JCheckBox)
			{
				return (JCheckBox) comp;
			}
			if (comp instanceof Container)
			{
				JCheckBox hit = firstCheckBox((Container) comp);
				if (hit != null)
				{
					return hit;
				}
			}
		}
		return null;
	}

	private static JButton firstEnableButton(Container c)
	{
		for (Component comp : c.getComponents())
		{
			if (comp instanceof JButton && ((JButton) comp).getText() != null
				&& ((JButton) comp).getText().toLowerCase().contains("enable"))
			{
				return (JButton) comp;
			}
			if (comp instanceof Container)
			{
				JButton hit = firstEnableButton((Container) comp);
				if (hit != null)
				{
					return hit;
				}
			}
		}
		return null;
	}
}
