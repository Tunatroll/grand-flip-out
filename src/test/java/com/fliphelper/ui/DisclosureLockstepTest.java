/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.ui;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/**
 * GuidePanel.SERVER_DISCLOSURE's own contract comment says "KEEP IN LOCKSTEP with
 * the enableServerFunctionality @ConfigItem warning" — because three in-panel
 * enable surfaces flip the warned switch programmatically (bypassing ConfigPanel's
 * warning dialog) and present ONLY this string. Consent must be equivalent
 * whichever surface the player uses.
 *
 * It drifted anyway: the telemetry sentence ("anonymous usage counts once per
 * session ... no character name, no account hash") was added to the @ConfigItem
 * warning and never reached SERVER_DISCLOSURE, so a player consenting in-panel
 * never saw the telemetry disclosure while telemetry sent once opted in (found
 * 2026-08-23, #289 L8 — live on the Hub at pin a07015c, i.e. shipped drift).
 *
 * A doc comment is not an enforcer (the FreeVsPremiumCopyTest lesson). This test
 * is: every load-bearing disclosure clause of the config warning must appear in
 * SERVER_DISCLOSURE too. Clause-level (not byte-equality) so whitespace/prompt
 * framing can differ without weakening consent.
 */
public class DisclosureLockstepTest
{
    private static final String[] REQUIRED_CLAUSES = {
        "submits your IP address",
        "Grand Exchange offer and trade data (item, price, quantity, flip timings, and approximate coins)",
        "starred watchlist items sync",
        "anonymous usage counts once per session",
        "No character name, no account hash, no other players' data",
    };

    private static String normalize(String region)
    {
        // Join "..." + "..." concatenations, unescape quotes, collapse whitespace so
        // clauses that span literal boundaries compare cleanly.
        return region
            .replaceAll("\"\\s*\\+\\s*\"", "")
            .replace("\\\"", "\"")
            .replace("\\n", " ")
            .replaceAll("\\s+", " ");
    }

    private static String region(String src, String startMarker, String endMarker, String what)
    {
        int start = src.indexOf(startMarker);
        assertTrue(what + " start marker present", start >= 0);
        int end = src.indexOf(endMarker, start);
        assertTrue(what + " end marker present", end > start);
        return src.substring(start, end);
    }

    @Test
    public void serverDisclosureCarriesEveryWarningClause() throws IOException
    {
        String config = new String(Files.readAllBytes(Paths.get(
            "src", "main", "java", "com", "fliphelper", "GrandFlipOutConfig.java")), StandardCharsets.UTF_8);
        String panel = new String(Files.readAllBytes(Paths.get(
            "src", "main", "java", "com", "fliphelper", "ui", "GuidePanel.java")), StandardCharsets.UTF_8);

        String warning = normalize(region(config, "warning = ", "default boolean enableServerFunctionality", "config warning"));
        String disclosure = normalize(region(panel, "SERVER_DISCLOSURE =", ";", "SERVER_DISCLOSURE"));

        for (String clause : REQUIRED_CLAUSES)
        {
            assertTrue("config warning must state: " + clause, warning.contains(clause));
            assertTrue("SERVER_DISCLOSURE must state (lockstep): " + clause, disclosure.contains(clause));
        }
    }
}
