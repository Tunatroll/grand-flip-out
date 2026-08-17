/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Activation-funnel wiring contracts (2026-08-17 funnel audit; 7d live: 431 CTA-seen
 * sessions -> 21 clicks -> 16 device links). Two defects pinned:
 *
 * 1. DISCARDED verificationUri — the server has always returned the /link page URL with
 *    every minted code, and {@code GuidePanel.onCode} threw it away, so the panel told the
 *    player to HAND-TYPE "grandflipout.com/link" mid-game. {@code linkPageUrl} is the one
 *    place that composes the browse target: the served URI plus a {@code ?code=} prefill
 *    the site accepts as input-only (link.html never auto-submits — approving stays a
 *    human click, which is what keeps this on the right side of the botting bright-line).
 *
 * 2. STRANDED CTA CLICKER — the unlock CTA sent its clicker to bare /signup, and the
 *    device-link flow lived in a different tab the signup page never mentioned. The CTA
 *    URL must carry {@code next=link} (a FIXED server-side allowlist destination,
 *    auth-discord.js DEST_PATHS) so signup hands the new account straight to /link.
 */
public class ActivationLinkWiringTest
{
    @Test
    public void linkUrlCarriesTheServedUriAndCodePrefill()
    {
        assertEquals("https://grandflipout.com/link?code=CPUB-5GUF",
            GuidePanel.linkPageUrl("https://grandflipout.com/link", "CPUB-5GUF"));
    }

    @Test
    public void missingServedUriFallsBackToTheCanonicalLinkPage()
    {
        assertEquals("https://grandflipout.com/link?code=AAAA-BBBB",
            GuidePanel.linkPageUrl(null, "AAAA-BBBB"));
        assertEquals("https://grandflipout.com/link?code=AAAA-BBBB",
            GuidePanel.linkPageUrl("", "AAAA-BBBB"));
    }

    @Test
    public void missingCodeStillOpensTheLinkPageBare()
    {
        assertEquals("https://grandflipout.com/link",
            GuidePanel.linkPageUrl("https://grandflipout.com/link", null));
    }

    @Test
    public void unlockCtaLandsTheClickerOnTheLinkFlowAfterSignup()
    {
        assertTrue("CTA must keep its attribution ref",
            GrandFlipOutPanel.UNLOCK_SIGNUP_URL.contains("ref=plugin"));
        assertTrue("CTA must ride the signup next=link allowlist to /link",
            GrandFlipOutPanel.UNLOCK_SIGNUP_URL.contains("next=link"));
    }
}
