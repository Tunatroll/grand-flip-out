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

    /**
     * Adjacent `"a" + "b"` literals are ONE user-facing sentence. Matching per literal
     * misses a violation that happens to straddle a line break — and this file's copy
     * style is exactly that (both strings the first fix corrected were themselves split
     * across `+`). Had the break landed one word earlier, the original violation would
     * have shipped straight past this gate. Join them before matching.
     */
    private static List<String> joinedCopy(String src)
    {
        // Group only literals joined by `+` with nothing but whitespace between them —
        // i.e. ONE user-facing sentence. A whole-file join would make any file that
        // merely MENTIONS a term anywhere match, which is a false positive: this class
        // legitimately contains the section title "Portfolio Optimizer (10M balanced)"
        // far away from any free-account copy. Precision matters or the gate gets muted.
        List<String> out = new ArrayList<>();
        List<int[]> spans = new ArrayList<>();
        List<String> lits = stringLiteralsWithSpans(src, spans);
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < lits.size(); i++)
        {
            run.append(lits.get(i));
            boolean joinsNext = false;
            if (i + 1 < lits.size())
            {
                String between = src.substring(spans.get(i)[1], spans.get(i + 1)[0]);
                joinsNext = between.replaceAll("\\s+", "").equals("+");
            }
            if (!joinsNext)
            {
                out.add(run.toString());
                run.setLength(0);
            }
        }
        if (run.length() > 0)
        {
            out.add(run.toString());
        }
        return out;
    }

    /** As stringLiterals, but also records each literal's [start,end) offsets in src. */
    private static List<String> stringLiteralsWithSpans(String src, List<int[]> spans)
    {
        List<String> out = new ArrayList<>();
        StringBuilder cur = null;
        int start = -1;
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
                if (c == '"') { out.add(cur.toString()); spans.add(new int[]{ start, i + 1 }); cur = null; inStr = false; continue; }
                cur.append(c);
                continue;
            }

            if (c == '/' && n == '/') { inLine = true; i++; continue; }
            if (c == '/' && n == '*') { inBlock = true; i++; continue; }
            if (c == '\'') { inChar = true; continue; }
            if (c == '"') { inStr = true; cur = new StringBuilder(); start = i; continue; }
        }
        return out;
    }

    /**
     * Features that are PRO-gated on the server. Copy may not offer these as something a
     * FREE account unlocks. Verified live 2026-07-26:
     *   /api/intelligence/vpin       200 anonymous   (free)
     *   /api/intelligence/screener   200 anonymous   (free)
     *   /api/intelligence/next-dumps 200 anonymous   (free)
     *   /api/intelligence/optimize   403 PRO required — and require-pro.js:102 gates on
     *                                    getTier()==='PRO', so a FREE key 403s too.
     *
     * The first pass at rule 6 deleted the word "premium" from the Intel-tab CTA and left
     * "Create a free Grand Flip Out account to unlock ... the portfolio optimizer" — new
     * bad info replacing old bad info, on the exact surface the rule exists to protect.
     */
    private static final String[] PRO_ONLY_FEATURES = { "portfolio optimizer" };

    /**
     * True when `feat` appears inside an "unlock ..." ENUMERATION — i.e. is offered as
     * something the free account gets — rather than merely being mentioned in the same
     * sentence. Naming a PRO feature to say it IS Pro ("The portfolio optimizer is Pro.")
     * is correct copy and must not trip the gate; listing it after "unlock" is the
     * violation. The clause ends at the first sentence break after "unlock".
     */
    private static boolean inUnlockClause(String sentence, String feat)
    {
        String s = sentence.toLowerCase();
        if (!s.contains("free"))
        {
            return false;
        }
        int from = 0;
        while (true)
        {
            int u = s.indexOf("unlock", from);
            if (u < 0)
            {
                return false;
            }
            int end = s.indexOf('.', u);
            String clause = end < 0 ? s.substring(u) : s.substring(u, end);
            if (clause.contains(feat))
            {
                return true;
            }
            from = u + 6;
        }
    }

    @Test
    public void freeAccountCopyDoesNotPromiseProOnlyFeatures() throws IOException
    {
        final Path root = Paths.get("src", "main", "java");
        final List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root))
        {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator)
            {
                String fileSrc = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
                for (String sentence : joinedCopy(fileSrc))
                {
                    for (String feat : PRO_ONLY_FEATURES)
                    {
                        if (inUnlockClause(sentence, feat))
                        {
                            offenders.add(root.relativize(f) + "  ->  \"" + sentence.trim() + "\"");
                        }
                    }
                }
            }
        }
        assertTrue(
            "user-facing copy offers a PRO-gated feature as part of what a FREE account unlocks"
                + " — that is the same revenue-leak as calling a free feature premium, inverted:\n  "
                + String.join("\n  ", offenders),
            offenders.isEmpty());
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
