/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.api;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pins the device-code link flow to the server contract
 * (server routes/device-link.js + device-link.js):
 *   start  -> { ok, userCode, deviceCode, verificationUri, expiresAt, interval }
 *   poll   -> { status: pending|slow_down|approved|expired|unknown, apiKey?, displayName? }
 * The apiKey arrives EXACTLY ONCE on approved — the service must surface it and
 * never poll again; slow_down backs off; garbage never crashes the client.
 */
public class DeviceLinkServiceTest
{
    private final Gson gson = new Gson();

    @Test
    public void parseStart_happyPath()
    {
        DeviceLinkService.StartResponse r = DeviceLinkService.parseStart(
            "{\"ok\":true,\"userCode\":\"K4PX-7MQD\",\"deviceCode\":\"abc123\","
                + "\"verificationUri\":\"https://grandflipout.com/link\","
                + "\"expiresAt\":1783800900000,\"interval\":5}", gson);
        assertTrue(r.ok);
        assertEquals("K4PX-7MQD", r.userCode);
        assertEquals("abc123", r.deviceCode);
        assertEquals("https://grandflipout.com/link", r.verificationUri);
        assertEquals(5, r.intervalSec);
    }

    @Test
    public void parseStart_rateLimitedAndGarbageAreSafe()
    {
        DeviceLinkService.StartResponse limited = DeviceLinkService.parseStart(
            "{\"ok\":false,\"reason\":\"rate_limited\"}", gson);
        assertTrue(!limited.ok);
        assertEquals("rate_limited", limited.reason);

        DeviceLinkService.StartResponse garbage = DeviceLinkService.parseStart("not json", gson);
        assertTrue(!garbage.ok);
    }

    @Test
    public void parsePoll_allStatuses()
    {
        assertEquals(DeviceLinkService.PollStatus.PENDING,
            DeviceLinkService.parsePoll("{\"status\":\"pending\"}", gson).status);
        assertEquals(DeviceLinkService.PollStatus.SLOW_DOWN,
            DeviceLinkService.parsePoll("{\"status\":\"slow_down\",\"interval\":5}", gson).status);
        assertEquals(DeviceLinkService.PollStatus.EXPIRED,
            DeviceLinkService.parsePoll("{\"status\":\"expired\"}", gson).status);
        assertEquals(DeviceLinkService.PollStatus.UNKNOWN,
            DeviceLinkService.parsePoll("{\"status\":\"unknown\"}", gson).status);

        DeviceLinkService.PollResult ok = DeviceLinkService.parsePoll(
            "{\"status\":\"approved\",\"apiKey\":\"gfo_secret\",\"displayName\":\"Tuna\"}", gson);
        assertEquals(DeviceLinkService.PollStatus.APPROVED, ok.status);
        assertEquals("gfo_secret", ok.apiKey);
        assertEquals("Tuna", ok.displayName);
    }

    @Test
    public void parsePoll_garbageNeverCrashesAndCarriesNoKey()
    {
        DeviceLinkService.PollResult r = DeviceLinkService.parsePoll("<html>502</html>", gson);
        assertEquals(DeviceLinkService.PollStatus.ERROR, r.status);
        assertNull(r.apiKey);
    }

    @Test
    public void pollPacing_respectsIntervalAndBacksOffOnSlowDown()
    {
        // Normal cadence: the advertised interval.
        assertEquals(5_000, DeviceLinkService.nextPollDelayMs(DeviceLinkService.PollStatus.PENDING, 5));
        // slow_down: interval + 2s per RFC 8628 semantics.
        assertEquals(7_000, DeviceLinkService.nextPollDelayMs(DeviceLinkService.PollStatus.SLOW_DOWN, 5));
        // Transient errors back off harder so a 502 storm never hammers the API.
        assertEquals(10_000, DeviceLinkService.nextPollDelayMs(DeviceLinkService.PollStatus.ERROR, 5));
        // Terminal states never reschedule.
        assertEquals(-1, DeviceLinkService.nextPollDelayMs(DeviceLinkService.PollStatus.APPROVED, 5));
        assertEquals(-1, DeviceLinkService.nextPollDelayMs(DeviceLinkService.PollStatus.EXPIRED, 5));
        assertEquals(-1, DeviceLinkService.nextPollDelayMs(DeviceLinkService.PollStatus.UNKNOWN, 5));
    }
}
