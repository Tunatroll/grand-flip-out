/*
 * Copyright (c) 2026, Tunatroll
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.fliphelper.ui;

import com.fliphelper.model.Suggestion;
import java.awt.Component;
import java.awt.Container;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * kahubu (#support, 2026-08-03): "I filled the order but now you need a way to go to the next
 * suggestion, it's stuck on this order until you click the 'All' or 'Volume' etc." The
 * "Next flip" escape existed ONLY on the collected-buy (held-for-sell) banner — the armed
 * state (Fill Order pressed, offer working) pinned the card with no affordance at all. The
 * armed state now shows its own banner with the same "Next flip" button, and a later
 * held-for-sell upgrade REPLACES the armed copy (the two banners must never stack or the
 * stale hint lies about the offer's state).
 */
public class AdvisorArmBannerTest
{
	private static final class StubListener implements AdvisorPanel.Listener
	{
		int nextFlips;

		@Override public void onSkip(int itemId) { }
		@Override public void onBlock(int itemId) { }
		@Override public void onPauseToggled(boolean paused) { }
		@Override public boolean onFillOffer(int itemId, long price, int quantity) { return true; }
		@Override public void onFiltersChanged() { }
		@Override public void onNextFlip() { nextFlips++; }
	}

	private static Suggestion buy()
	{
		return Suggestion.builder().action("BUY").itemId(4151).itemName("Abyssal whip")
			.price(1_800_000).quantity(1).build();
	}

	private static int count(Container c, Class<?> type, String text)
	{
		int n = 0;
		for (Component comp : c.getComponents())
		{
			if (type.isInstance(comp))
			{
				String t = comp instanceof AbstractButton ? ((AbstractButton) comp).getText()
					: ((JLabel) comp).getText();
				if (t != null && t.contains(text))
				{
					n++;
				}
			}
			if (comp instanceof Container)
			{
				n += count((Container) comp, type, text);
			}
		}
		return n;
	}

	@Test
	public void armedStateCarriesTheNextFlipEscape() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			StubListener listener = new StubListener();
			AdvisorPanel panel = new AdvisorPanel(listener);
			panel.showSuggestion(buy());
			panel.showArmedHold();
			assertEquals("armed banner renders the Next flip escape",
				1, count(panel, AbstractButton.class, "Next flip"));
			assertTrue("armed copy present",
				count(panel, JLabel.class, "stays put") >= 1);
			// Idempotent across offer ticks — never stacks.
			panel.showArmedHold();
			assertEquals(1, count(panel, AbstractButton.class, "Next flip"));
		});
	}

	@Test
	public void heldForSellUpgradeReplacesTheArmedCopy() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			AdvisorPanel panel = new AdvisorPanel(new StubListener());
			panel.showSuggestion(buy());
			panel.showArmedHold();
			panel.showHeldForSell();
			assertEquals("exactly one banner after the upgrade",
				1, count(panel, AbstractButton.class, "Next flip"));
			assertEquals("the armed hint is gone", 0, count(panel, JLabel.class, "stays put"));
			assertTrue("the sell hint replaced it",
				count(panel, JLabel.class, "place your sell") >= 1);
		});
	}

	private static AbstractButton findButton(Container c, String text)
	{
		for (Component comp : c.getComponents())
		{
			if (comp instanceof AbstractButton && text.equals(((AbstractButton) comp).getText()))
			{
				return (AbstractButton) comp;
			}
			if (comp instanceof Container)
			{
				AbstractButton b = findButton((Container) comp, text);
				if (b != null)
				{
					return b;
				}
			}
		}
		return null;
	}

	/**
	 * The escape's click WIRING: "Next flip" on the armed banner must reach
	 * Listener.onNextFlip (the stub's counter existed unasserted — review nit on the
	 * 5af290e..a07015c batch).
	 */
	@Test
	public void armedNextFlipClickReachesTheListener() throws Exception
	{
		StubListener listener = new StubListener();
		SwingUtilities.invokeAndWait(() ->
		{
			AdvisorPanel panel = new AdvisorPanel(listener);
			panel.showSuggestion(buy());
			panel.showArmedHold();
			AbstractButton next = findButton(panel, "Next flip");
			assertNotNull("armed banner carries the Next flip escape", next);
			next.doClick();
		});
		assertEquals("click reaches onNextFlip exactly once", 1, listener.nextFlips);
	}
}
