/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.model;

import com.fliphelper.util.GeTax;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class FlipItem
{
    private final int itemId;
    private final String itemName;
    private int quantity;
    private long buyPrice;
    private long sellPrice;
    private Instant buyTime;
    private Instant sellTime;
    private FlipState state;
    private int geSlot;
    /**
     * True only when BOTH legs were witnessed as live GE offer events this session
     * (never true for history/FU imports, which pair buy==sell at import time —
     * geSlot -1 — and would fabricate 0-minute fill durations in telemetry).
     * Gson leaves it {@code false} on rows from older history files: fail-closed.
     */
    private boolean liveWitnessed;
    /** Wealth snapshot at sell completion (optional, local-only). */
    /** Market sell price at the time the buy was placed (for frozen sell tracking). */
    private long frozenSellPrice;
    private Long sellCoinGp;
    private Long sellInventoryGp;
    private Long sellBankGp;
    private Long sellTotalWealthGp;
    /**
     * Stable per-account key (RuneScape account hash). Survives RSN changes.
     * Absent in pre-account history files, where Gson leaves it {@code 0}.
     */
    private long accountId;
    /**
     * In-game display name (RSN) of the account that made this flip.
     * Absent in pre-account history files, where Gson leaves it {@code null}.
     */
    private String accountName;

    public long getProfit()
    {
        if (buyPrice <= 0 || sellPrice <= 0)
        {
            return 0;
        }
        long revenue = sellPrice * quantity;
        long cost = buyPrice * quantity;
        long tax = GeTax.tax(itemId, sellPrice, quantity);
        return revenue - cost - tax;
    }

    public double getProfitPercent()
    {
        if (buyPrice <= 0)
        {
            return 0;
        }
        return (double) getProfit() / (buyPrice * quantity) * 100.0;
    }

    public long getFlipDurationSeconds()
    {
        if (buyTime == null || sellTime == null)
        {
            return 0;
        }
        return sellTime.getEpochSecond() - buyTime.getEpochSecond();
    }

    public boolean isComplete()
    {
        return state == FlipState.COMPLETE;
    }

    /**
     * Get the GE tax paid on this flip.
     */
    public long getTax()
    {
        if (sellPrice <= 0 || quantity <= 0)
        {
            return 0;
        }
        return GeTax.tax(itemId, sellPrice, quantity);
    }

    /**
     * Get GP/hr for this flip based on its duration.
     */
    public long getGpPerHour()
    {
        long duration = getFlipDurationSeconds();
        if (duration <= 0)
        {
            return 0;
        }
        return (long) ((double) getProfit() / duration * 3600);
    }
}
