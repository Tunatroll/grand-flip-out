/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * #269 c3: fillWindow parse contract. The server serves measured per-band
 * fill-window probabilities as 0..1 fractions in an OPTIONAL {@code fillWindow}
 * object on each advisor card ({@code {"h2":0.165,"h4":0.282,"n":443,...}});
 * the plugin stores whole percentages, and ABSENT must map to null — never 0 —
 * so a measured 0% stays distinct from "the server sent no measurement"
 * (unmeasured bands are served null, never fabricated).
 */
public class FillWindowParseTest
{
    private static final Gson GSON = new Gson();

    private static JsonObject card(String json)
    {
        return GSON.fromJson(json, JsonObject.class);
    }

    @Test
    public void fractionsRoundToWholePercents()
    {
        JsonObject o = card("{\"fillWindow\":{\"h2\":0.165,\"h4\":0.282,\"n\":443}}");
        assertEquals(Integer.valueOf(17), IntelligenceClient.fillWindowPct(o, "h2"));
        assertEquals(Integer.valueOf(28), IntelligenceClient.fillWindowPct(o, "h4"));
    }

    @Test
    public void measuredZeroIsZeroNotNull()
    {
        JsonObject o = card("{\"fillWindow\":{\"h2\":0.0}}");
        assertEquals(Integer.valueOf(0), IntelligenceClient.fillWindowPct(o, "h2"));
    }

    @Test
    public void absentObjectIsNull()
    {
        assertNull(IntelligenceClient.fillWindowPct(card("{\"estFillMin\":35}"), "h2"));
    }

    @Test
    public void nonObjectWindowIsNull()
    {
        assertNull(IntelligenceClient.fillWindowPct(card("{\"fillWindow\":7}"), "h2"));
    }

    @Test
    public void missingLegIsNull()
    {
        assertNull(IntelligenceClient.fillWindowPct(card("{\"fillWindow\":{\"h4\":0.5}}"), "h2"));
    }

    @Test
    public void nonNumericLegIsNull()
    {
        assertNull(IntelligenceClient.fillWindowPct(card("{\"fillWindow\":{\"h2\":\"x\"}}"), "h2"));
    }

    @Test
    public void nullLegIsNull()
    {
        assertNull(IntelligenceClient.fillWindowPct(card("{\"fillWindow\":{\"h2\":null}}"), "h2"));
    }
}
