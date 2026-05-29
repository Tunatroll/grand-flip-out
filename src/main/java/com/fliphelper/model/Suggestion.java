/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.model;

import lombok.Value;

import java.util.Collections;
import java.util.List;

/**
 * One next-action recommendation from the server advisor (Phase 1).
 * {@code action} is BUY / SELL / ABORT / WAIT. For WAIT there is no item.
 */
@Value
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

    public List<String> getReasons()
    {
        return reasons != null ? reasons : Collections.emptyList();
    }

    public boolean isWait()
    {
        return action == null || "WAIT".equalsIgnoreCase(action);
    }
}
