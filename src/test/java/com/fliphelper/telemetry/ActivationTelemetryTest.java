/*
 * Copyright (c) 2026, Tunatroll
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.fliphelper.telemetry;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ActivationTelemetryTest
{
	@Test
	public void sessionIdDiffersPerInstance()
	{
		// Session-scoped, never persisted: two client sessions must not be correlatable.
		assertNotEquals(new ActivationTelemetry().sessionId(), new ActivationTelemetry().sessionId());
	}

	@Test
	public void drainReturnsCountsAndResets()
	{
		ActivationTelemetry t = new ActivationTelemetry();
		t.unlockCtaSeen();
		Map<String, Integer> first = t.drain();
		assertEquals(Integer.valueOf(1), first.get("plugin_unlock_cta_seen"));
		assertTrue("counters must reset after a drain", t.drain().isEmpty());
	}

	@Test
	public void drainOmitsZeroCounters()
	{
		ActivationTelemetry t = new ActivationTelemetry();
		t.unlockCtaClicked();
		Map<String, Integer> out = t.drain();
		assertEquals(1, out.size());
		assertEquals(Integer.valueOf(1), out.get("plugin_unlock_cta_clicked"));
	}

	@Test
	public void panelsAreCountedByNameAndDrainSeparately()
	{
		// Panel names become meta.source on ONE stage, so they must not leak into drain().
		ActivationTelemetry t = new ActivationTelemetry();
		t.panelOpened("prices");
		t.panelOpened("prices");
		t.panelOpened("flips");
		assertTrue("panel names must not appear as stage counts", t.drain().isEmpty());
		Map<String, Integer> panels = t.drainPanels();
		assertEquals(Integer.valueOf(2), panels.get("prices"));
		assertEquals(Integer.valueOf(1), panels.get("flips"));
		assertTrue("panels must reset after a drain", t.drainPanels().isEmpty());
	}

	@Test
	public void unknownPanelNamesAreDropped()
	{
		// A future tab rename must not silently start sending an unreviewed string.
		ActivationTelemetry t = new ActivationTelemetry();
		t.panelOpened("evil");
		t.panelOpened(null);
		assertTrue(t.drainPanels().isEmpty());
	}
}
