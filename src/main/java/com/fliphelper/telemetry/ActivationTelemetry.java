/*
 * Copyright (c) 2026, Tunatroll
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.fliphelper.telemetry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Anonymous, session-scoped activation counters.
 *
 * The id is generated per instance and NEVER persisted, so nothing correlates two client sessions.
 * Holds no player identity: no display name, no account hash, no item or trade data — only how many
 * times a panel was opened and whether the "create free account" prompt was shown or clicked.
 */
public class ActivationTelemetry
{
	/**
	 * Panel names we will report — the real tab titles, lowercased. An unknown name is DROPPED
	 * rather than forwarded: the label becomes meta.source server-side, so an open field would let
	 * a future tab rename quietly start sending an unreviewed string.
	 */
	private static final Set<String> PANELS =
		new HashSet<>(Arrays.asList("prices", "flips", "intel", "guide"));

	private final String sessionId = UUID.randomUUID().toString();
	private final Map<String, AtomicInteger> panels = new ConcurrentHashMap<>();
	private final AtomicInteger unlockCtaSeen = new AtomicInteger();
	private final AtomicInteger unlockCtaClicked = new AtomicInteger();

	public String sessionId()
	{
		return sessionId;
	}

	public void panelOpened(String panel)
	{
		if (panel == null || !PANELS.contains(panel))
		{
			return;
		}
		panels.computeIfAbsent(panel, k -> new AtomicInteger()).incrementAndGet();
	}

	public void unlockCtaSeen()
	{
		unlockCtaSeen.incrementAndGet();
	}

	public void unlockCtaClicked()
	{
		unlockCtaClicked.incrementAndGet();
	}

	/** Stage counts, resetting them. Zero counters are omitted so an idle session sends nothing. */
	public Map<String, Integer> drain()
	{
		Map<String, Integer> out = new HashMap<>();
		put(out, "plugin_unlock_cta_seen", unlockCtaSeen.getAndSet(0));
		put(out, "plugin_unlock_cta_clicked", unlockCtaClicked.getAndSet(0));
		return out;
	}

	/** Panel counts, resetting them. Kept apart from {@link #drain()} — these are labels, not stages. */
	public Map<String, Integer> drainPanels()
	{
		Map<String, Integer> out = new HashMap<>();
		for (Map.Entry<String, AtomicInteger> e : panels.entrySet())
		{
			put(out, e.getKey(), e.getValue().getAndSet(0));
		}
		return out;
	}

	private static void put(Map<String, Integer> out, String key, int value)
	{
		if (value > 0)
		{
			out.put(key, value);
		}
	}
}
