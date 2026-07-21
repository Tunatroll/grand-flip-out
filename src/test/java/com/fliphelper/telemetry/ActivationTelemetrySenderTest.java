/*
 * Copyright (c) 2026, Tunatroll
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.fliphelper.telemetry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActivationTelemetrySenderTest
{
	private static Map<String, Integer> counts()
	{
		Map<String, Integer> m = new HashMap<>();
		m.put("plugin_unlock_cta_seen", 2);
		return m;
	}

	private static Map<String, Integer> panels()
	{
		Map<String, Integer> m = new HashMap<>();
		m.put("prices", 3);
		return m;
	}

	@Test
	public void neverSendsWithoutConsent()
	{
		// THE compliance assertion: no grandflipout.com call may be constructed pre-consent.
		assertFalse(ActivationTelemetrySender.shouldSend(false, counts(), panels()));
	}

	@Test
	public void sendsWithConsentAndData()
	{
		assertTrue(ActivationTelemetrySender.shouldSend(true, counts(), Collections.emptyMap()));
		assertTrue("panels alone are worth sending",
			ActivationTelemetrySender.shouldSend(true, Collections.emptyMap(), panels()));
	}

	@Test
	public void sendsNothingWhenThereIsNothingToReport()
	{
		assertFalse(ActivationTelemetrySender.shouldSend(true, Collections.emptyMap(), Collections.emptyMap()));
		assertFalse(ActivationTelemetrySender.shouldSend(true, null, null));
	}

	@Test
	public void bodyCarriesOnlySessionIdCountsAndPanels()
	{
		String body = ActivationTelemetrySender.buildBody("abc-123", counts(), panels());
		assertTrue(body.contains("\"sid\":\"abc-123\""));
		assertTrue(body.contains("\"plugin_unlock_cta_seen\":2"));
		assertTrue(body.contains("\"prices\":3"));
		// Nothing that could identify the player may ever appear in the payload.
		assertEquals("body must not carry account data", -1, body.indexOf("account"));
		assertEquals("body must not carry a display name", -1, body.indexOf("rsn"));
	}
}
