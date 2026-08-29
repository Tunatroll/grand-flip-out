/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * #290 phase 3: the wiki prices API docs moved v1 -> v2 (2026-07-24); v1 serves
 * but is undocumented (silent-sunset risk). /mapping, /latest, /5m and /1h are
 * payload-identical on v2 (live-diffed on the issue), so the base swap is safe.
 * /timeseries CHANGED contract: v1 {@code timestep=5m/1h/6h/24h} is replaced by
 * v2 {@code lookback} with fixed span/step pairs (6h/24h -> 5m step, 7d -> 1h,
 * 30d -> 6h, 6m/1y -> 1d); sending {@code timestep=} to v2 is a 400. These pins
 * hold the plugin on the documented contract.
 */
public class WikiV2ContractTest
{
    @Test
    public void baseUrlIsTheDocumentedV2()
    {
        assertEquals("https://prices.runescape.wiki/api/v2/osrs", WikiPriceClient.BASE_URL);
    }

    @Test
    public void timeseriesUsesTheV2LookbackContract()
    {
        String url = WikiPriceClient.timeseriesUrl(4151, "7d");
        assertTrue("v2 base rides the timeseries call: " + url,
            url.startsWith("https://prices.runescape.wiki/api/v2/osrs/timeseries?"));
        assertTrue("v2 lookback param, never v1 timestep: " + url,
            url.contains("id=4151") && url.contains("lookback=7d") && !url.contains("timestep"));
    }
}
