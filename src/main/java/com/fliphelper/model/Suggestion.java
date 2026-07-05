/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.model;

import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.List;

/**
 * One next-action recommendation from the server advisor (Phase 1).
 * {@code action} is BUY / SELL / ABORT / WAIT. For WAIT there is no item.
 */
@Value
@Builder
public class Suggestion
{
    String action;
    int itemId;
    String itemName;
    long price;
    int quantity;
    long expectedProfit;
    double confidence;
    List<String> reasons;
    /** GE slot the action targets (e.g. the offer to abort), or -1 when not slot-specific. */
    int targetSlot;

    // Enrichment fields (server-provided; default to 0 on older responses).
    /** After-tax profit per item — negative when the flip is currently a loss. */
    long marginPer;
    /** GE 4-hour buy limit for this item (0 = unknown). */
    int geLimit;
    /** Profit if you bought the FULL GE limit (per-cycle ceiling) — negative = loss. */
    long profitPerLimit;
    /** ~1h traded volume (buy + sell) — a liquidity gauge. */
    long volume;
    /**
     * Server margin grade for the quoted round-trip: null/"executable" = a fresh
     * two-sided book; "estimate" = one leg is stale (the margin is not executable
     * as quoted); "no_estimate" = no second price exists. Display-only label —
     * the server already refuses profit numbers on graded margins.
     */
    String marginQuality;

    public List<String> getReasons()
    {
        return reasons != null ? reasons : Collections.emptyList();
    }

    public boolean isWait()
    {
        return action == null || "WAIT".equalsIgnoreCase(action);
    }
}
