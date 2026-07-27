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

import static org.junit.Assert.assertTrue;

/**
 * The advisor badge fetch is issued from PER-CARD rendering, so without a guard one render
 * pass over N cards issues N requests, and overlapping passes re-issue them. Observed live in
 * Railway http logs on 2026-07-27: a single client fired ~35
 * GET /api/intelligence/smart-advisor in 0.8s, and a second ~25 in 3.6s.
 *
 * Two independent defects produced that, and this test pins both:
 *
 *   1. NO IN-FLIGHT GUARD. A cache MISS is not a record that a fetch is already running, so
 *      every render that missed dispatched its own request for the same itemId.
 *   2. FAILURES CACHED NOTHING. `catch (Exception ignored) {}` stored no entry, so a
 *      persistently failing item was re-requested on every repaint forever, unbounded.
 *
 * Scanned as SOURCE rather than exercised as behaviour on purpose: the fetch lives inside
 * Swing card construction, which needs a live RuneLite client and an EDT to drive. A source
 * assertion cannot prove runtime correctness, but it does prove the guard was not deleted —
 * which is the actual regression risk, since the original code shipped with no guard at all
 * and nobody noticed until the server logs were read months later.
 *
 * Sibling of FreeVsPremiumCopyTest, same reasoning: the rule has to outlive the person who
 * knows it.
 */
public class AdvisorFetchDedupeTest
{
    private static String panelSource() throws IOException
    {
        final Path p = Paths.get("src", "main", "java", "com", "fliphelper", "ui",
            "GrandFlipOutPanel.java");
        assertTrue("GrandFlipOutPanel.java not found at " + p.toAbsolutePath(), Files.exists(p));
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    /** The itemId must be CLAIMED before the fetch is dispatched, not after. */
    @Test
    public void fetchIsGuardedByAnInFlightClaim() throws IOException
    {
        final String src = panelSource();

        assertTrue("advisorInFlight set is gone — a render pass will fan out one request per card",
            src.contains("advisorInFlight"));

        final int claim = src.indexOf("advisorInFlight.add(");
        assertTrue("no advisorInFlight.add(...) claim — nothing deduplicates concurrent fetches",
            claim >= 0);

        final int dispatch = src.indexOf("fetchSmartAdvisor");
        assertTrue("fetchSmartAdvisor call vanished from the panel", dispatch >= 0);
        assertTrue("the in-flight claim must precede the fetch dispatch, otherwise the guard "
            + "cannot prevent the duplicate request it exists to prevent",
            claim < dispatch);
    }

    /** The claim must be released in a finally, or one throw strands the itemId forever. */
    @Test
    public void theClaimIsReleasedInAFinally() throws IOException
    {
        final String src = panelSource();
        final int remove = src.indexOf("advisorInFlight.remove(");
        assertTrue("the in-flight claim is never released — the first fetch would block that "
            + "itemId for the rest of the session", remove >= 0);

        // The release must sit inside a finally block, not on the success path. Search a window
        // backwards from the release for the keyword rather than regexing brace structure.
        final String before = src.substring(Math.max(0, remove - 260), remove);
        assertTrue("advisorInFlight.remove(...) is not inside a finally — an unchecked throw "
            + "would strand the claim and permanently starve that itemId of a badge",
            before.contains("finally"));
    }

    /** A failure must be cached, or a failing item re-fires on every repaint with no ceiling. */
    @Test
    public void failuresAreCachedSoTheyBackOff() throws IOException
    {
        final String src = panelSource();

        assertTrue("no negative cache entry — a failing itemId will be re-requested on every "
                + "repaint forever (the `catch (Exception ignored) {}` regression)",
            src.contains("CachedAdvisor.failure()"));

        assertTrue("the negative entry needs its own longer TTL, else a failure expires as fast "
            + "as a success and the backoff buys nothing", src.contains("FAIL_TTL_MS"));

        assertTrue("a cached FAILURE must not render a signal badge — action is null on a "
            + "negative entry and would NPE or print 'null'", src.contains("!cached.failed"));
    }
}
