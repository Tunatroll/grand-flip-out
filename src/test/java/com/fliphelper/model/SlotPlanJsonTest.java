/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * #215 item 4: the slot-mix plan. {@link SlotLane#mixPlan} maps the three config
 * knobs onto ordered lanes clamped to the free slots (the server 400s over-plans,
 * so the clamp is client-side), and the plan rides the request JSON ADDITIVELY —
 * an absent plan must produce a byte-identical body to the pre-mix overload
 * (sibling of SnapshotBandJsonTest's rule for band/maxFillMin).
 */
public class SlotPlanJsonTest
{
    private static final Gson GSON = new Gson();

    private static GameStateSnapshot snap()
    {
        return new GameStateSnapshot(10_000_000L, 8, Collections.emptyList());
    }

    @Test
    public void mixPlanBuildsOrderedLanes()
    {
        List<SlotLane> plan = SlotLane.mixPlan(2, 3, 3, 8);
        assertEquals(3, plan.size());
        assertEquals(2, plan.get(0).getSlots());
        assertEquals("throughput", plan.get(0).getBand());
        assertEquals(3, plan.get(1).getSlots());
        assertEquals(120, plan.get(1).getMaxFillMin());
        assertEquals(3, plan.get(2).getSlots());
        assertEquals("patient_whale", plan.get(2).getBand());
    }

    @Test
    public void mixPlanClampsToFreeSlots()
    {
        List<SlotLane> plan = SlotLane.mixPlan(2, 3, 3, 4);
        int total = plan.stream().mapToInt(SlotLane::getSlots).sum();
        assertEquals("lanes truncate in order to the free slots", 4, total);
        assertEquals(2, plan.get(0).getSlots());
        assertEquals(2, plan.get(1).getSlots());
        assertEquals("whale lane fully truncated away", 2, plan.size());
    }

    @Test
    public void mixPlanOmitsZeroLanesAndHandlesEmpty()
    {
        List<SlotLane> whalesOnly = SlotLane.mixPlan(0, 0, 3, 8);
        assertEquals(1, whalesOnly.size());
        assertEquals("patient_whale", whalesOnly.get(0).getBand());
        assertTrue("all-zero mix = empty plan", SlotLane.mixPlan(0, 0, 0, 8).isEmpty());
        assertTrue("no free slots = empty plan", SlotLane.mixPlan(2, 3, 3, 0).isEmpty());
    }

    @Test
    public void slotPlanRidesTheBodyAdditively()
    {
        List<SlotLane> plan = SlotLane.mixPlan(2, 3, 1, 8);
        String json = snap().toRequestJson(Collections.emptyList(), false, null, 0, plan);
        JsonObject o = GSON.fromJson(json, JsonObject.class);
        assertTrue("slotPlan array present", o.has("slotPlan"));
        JsonArray lanes = o.getAsJsonArray("slotPlan");
        assertEquals(3, lanes.size());
        JsonObject vol = lanes.get(0).getAsJsonObject();
        assertEquals(2, vol.get("slots").getAsInt());
        assertEquals("throughput", vol.get("band").getAsString());
        assertFalse("uncapped lane omits maxFillMin", vol.has("maxFillMin"));
        JsonObject fast = lanes.get(1).getAsJsonObject();
        assertEquals(120, fast.get("maxFillMin").getAsInt());
        assertFalse("bandless lane omits band", fast.has("band"));
    }

    @Test
    public void absentPlanKeepsBodyByteIdentical()
    {
        String withNull = snap().toRequestJson(Collections.emptyList(), false, null, 0, null);
        String legacy = snap().toRequestJson(Collections.emptyList(), false, null, 0);
        assertEquals("null plan = byte-identical legacy body", legacy, withNull);
        String withEmpty = snap().toRequestJson(Collections.emptyList(), false, null, 0, Collections.emptyList());
        assertEquals("empty plan = byte-identical legacy body", legacy, withEmpty);
    }
}
