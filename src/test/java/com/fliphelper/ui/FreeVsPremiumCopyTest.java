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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

/**
 * src/CLAUDE.md rule 6: "Never call a FREE feature 'premium' in user-facing copy."
 * FREE = all items + flip suggestions + the intel; PRO ($/mo) = the paid
 * analytics/export layer. The doc records this conflation as the funnel
 * revenue-leak — free users felt they already had everything — and declares it
 * "Fixed 2026-06-30".
 *
 * It was not fixed. On 2026-07-26, 26 days later, TWO user-facing sites still
 * conflated them:
 *
 *   GrandFlipOutPanel.buildUnlockCta   "Create a free account to unlock all
 *                                       members flips and premium features."
 *   GrandFlipOutPanel Intel-tab CTA    "Server intelligence is a premium feature.
 *                                       Create a free Grand Flip Out account to
 *                                       unlock VPIN alerts, screener signals..."
 *
 * The @ConfigItem half of that fix did land; the panel-CTA half did not, and
 * nothing noticed because only a DOC enforced the rule. Live funnel over the
 * preceding 7 days: plugin_unlock_cta_seen 108, and the plugin is the dominant
 * acquisition channel (79 of 91 signup views carry ref=plugin).
 *
 * A doc is not an enforcer. This test is.
 *
 * Scope: user-facing STRING LITERALS only. Comments and javadoc may say "premium"
 * freely — EntitlementService's javadoc does, correctly, and describing the tier
 * in code is not selling it to a user.
 */
public class FreeVsPremiumCopyTest
{
    /**
     * Literals are extracted by a CHARACTER SCANNER, not a regex. Two reasons, both
     * hit on the first attempt at this test:
     *
     *   1. `"(?:[^"\\]|\\.)*"` recurses per character in java.util.regex and threw
     *      StackOverflowError on this file's long HTML copy strings.
     *   2. Stripping `//...` with a regex EATS the rest of any line containing a URL
     *      literal ("https://grandflipout.com/..."), which would silently hide
     *      violations — a gate that under-reports is worse than no gate.
     *
     * A scanner that tracks string/comment state handles both correctly.
     */
    private static List<String> stringLiterals(String src)
    {
        List<String> out = new ArrayList<>();
        StringBuilder cur = null;
        boolean inStr = false, inChar = false, inLine = false, inBlock = false;
        for (int i = 0; i < src.length(); i++)
        {
            char c = src.charAt(i);
            char n = i + 1 < src.length() ? src.charAt(i + 1) : '\0';

            if (inLine) { if (c == '\n') inLine = false; continue; }
            if (inBlock) { if (c == '*' && n == '/') { inBlock = false; i++; } continue; }
            if (inChar) { if (c == '\\') i++; else if (c == '\'') inChar = false; continue; }

            if (inStr)
            {
                if (c == '\\') { if (n != '\0') { cur.append(n); i++; } continue; }
                if (c == '"') { out.add(cur.toString()); cur = null; inStr = false; continue; }
                cur.append(c);
                continue;
            }

            if (c == '/' && n == '/') { inLine = true; i++; continue; }
            if (c == '/' && n == '*') { inBlock = true; i++; continue; }
            if (c == '\'') { inChar = true; continue; }
            if (c == '"') { inStr = true; cur = new StringBuilder(); continue; }
        }
        return out;
    }

    @Test
    public void noUserFacingCopyCallsAFreeFeaturePremium() throws IOException
    {
        final Path root = Paths.get("src", "main", "java");
        assertTrue("expected to find " + root.toAbsolutePath(), Files.isDirectory(root));

        final List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root))
        {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator)
            {
                String src = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
                for (String lit : stringLiterals(src))
                {
                    if (lit.toLowerCase().contains("premium"))
                    {
                        offenders.add(root.relativize(f) + "  ->  \"" + lit + "\"");
                    }
                }
            }
        }

        assertTrue(
            "user-facing copy calls a FREE feature \"premium\" (src/CLAUDE.md rule 6 — this is the"
                + " funnel revenue-leak; a free user who believes they already have the premium"
                + " layer never reaches /upgrade):\n  " + String.join("\n  ", offenders),
            offenders.isEmpty());
    }
}
