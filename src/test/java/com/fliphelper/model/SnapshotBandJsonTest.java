/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.model;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * #215: the advisor request JSON carries band/maxFillMin ONLY when set — an old
 * server must see byte-identical bodies from an unfiltered client (the fields are
 * additive, never a contract break), and the hand-built JSON must stay parseable.
 */
public class SnapshotBandJsonTest
{
    private static final Gson GSON = new Gson();

    private static GameStateSnapshot snap()
    {
        // @Value all-args constructor: (gold, freeSlots, activeOffers)
        return new GameStateSnapshot(10_000_000L, 4, Collections.emptyList());
    }

    @Test
    public void filtersRideTheBodyWhenSet()
    {
        String json = snap().toRequestJson(Collections.emptyList(), false, "patient_whale", 120);
        JsonObject o = GSON.fromJson(json, JsonObject.class);
        assertEquals("patient_whale", o.get("band").getAsString());
        assertEquals(120, o.get("maxFillMin").getAsInt());
        assertEquals(10_000_000, o.get("gold").getAsLong());
    }

    @Test
    public void unfilteredBodyOmitsTheFields()
    {
        String json = snap().toRequestJson(Collections.emptyList(), false, null, 0);
        JsonObject o = GSON.fromJson(json, JsonObject.class);
        assertFalse("band must be absent when unset", o.has("band"));
        assertFalse("maxFillMin must be absent when 0", o.has("maxFillMin"));
    }

    @Test
    public void legacyTwoArgOverloadIsUnfiltered()
    {
        assertEquals(snap().toRequestJson(Collections.emptyList(), false, null, 0),
            snap().toRequestJson(Collections.emptyList(), false));
        assertTrue(GSON.fromJson(snap().toRequestJson(Collections.emptyList(), true), JsonObject.class).get("f2pOnly").getAsBoolean());
    }
}
