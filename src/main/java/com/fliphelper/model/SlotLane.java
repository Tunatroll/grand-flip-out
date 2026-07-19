/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.model;

import lombok.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * #215 item 4: one lane of the advisor's slot-mix plan — {@code slots} GE slots
 * filtered to a flip-bands {@code band} (null = any) and/or a fill-time cap.
 * Built from the plugin's mix config via {@link #mixPlan} and serialized
 * additively onto the /suggest-basket request (GameStateSnapshot#toRequestJson);
 * the server allocates lanes in order through its one ranker and falls back to
 * best-overall when a lane has no pick.
 */
@Value
public class SlotLane
{
    int slots;
    /** flip-bands key (throughput | patient_whale) or null for any band. */
    String band;
    /** Estimated-fill cap in minutes; 0 = uncapped. */
    int maxFillMin;
    /** Human lane header for the panel's grouped render. */
    String label;

    /**
     * Build the ordered mix plan from the three config knobs, clamped to the free
     * slots (lanes are truncated in order when the mix asks for more than exist —
     * the server 400s an over-plan, so the clamp lives client-side). Zero lanes
     * are omitted; an all-zero mix (or no free slots) returns an empty plan,
     * which callers treat as "mix off".
     */
    public static List<SlotLane> mixPlan(int volumeSlots, int fastSlots, int whaleSlots, int freeSlots)
    {
        List<SlotLane> plan = new ArrayList<>();
        int remaining = Math.max(0, freeSlots);
        remaining = addLane(plan, volumeSlots, "throughput", 0, "High volume", remaining);
        remaining = addLane(plan, fastSlots, null, 120, "Fast fill (≤2h)", remaining);
        addLane(plan, whaleSlots, "patient_whale", 0, "High ticket", remaining);
        return plan;
    }

    private static int addLane(List<SlotLane> plan, int want, String band, int maxFillMin,
                               String label, int remaining)
    {
        int take = Math.min(Math.max(0, want), remaining);
        if (take > 0)
        {
            plan.add(new SlotLane(take, band, maxFillMin, label));
        }
        return remaining - take;
    }
}
