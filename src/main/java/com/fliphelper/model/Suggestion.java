/*
 * Copyright (c) 2026, Tunatroll
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
    /**
     * The advised SELL target for this buy, straight from the server (#249). 0 means the
     * server did not supply one — an older build, or a WAIT with no item — never "sell for
     * nothing". Gson leaves an absent field at 0, which is the correct fail-closed default.
     * Before this existed the target reached us only inside the prose `reasons` line, so it
     * could not be remembered against the position the buy produced.
     */
    long sellPrice;
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

    /**
     * Server price-honesty tier for the quoted prices themselves: "EXECUTABLE"
     * (fresh two-sided book), "INDICATIVE" (stale book — context, not quotes),
     * "NO_ESTIMATE"/"NO_DATA" (no defensible live price). Display-only label,
     * graded server-side (price-quality SSOT) — never re-derived here.
     */
    String priceTier;

    /** flip-bands taxonomy from the server (#215): throughput | patient_whale | standard; null on old servers. */
    String band;
    String bandLabel;
    /** Rough minutes to acquire the sized quantity at current volume; 0 when the server didn't say. */
    int estFillMin;

    /**
     * #269 c3: measured fill-window probability — the percentage of comparable advised
     * flips that FULLY filled within 2h, measured server-side per band. Null when the
     * server sent no measurement (older build / unmeasured band); a measured 0 is
     * meaningful and must stay distinct from absent, hence Integer not int.
     */
    Integer fillH2Pct;
    /** Same as {@link #fillH2Pct} for the 4h window. */
    Integer fillH4Pct;

    public List<String> getReasons()
    {
        return reasons != null ? reasons : Collections.emptyList();
    }

    public boolean isWait()
    {
        return action == null || "WAIT".equalsIgnoreCase(action);
    }
}
