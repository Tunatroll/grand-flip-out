/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

/**
 * Represents a completed trade recorded from the GE history.
 */
@Data
@Builder(toBuilder = true)
public class TradeRecord
{
    private final int itemId;
    private final String itemName;
    private final int quantity;
    private final long price;
    private final boolean bought;
    private final Instant timestamp;
    private final int geSlot;
    /** Coin pouch + inventory coins at transaction time (gp). */
    private final Long coinGp;
    /** Non-coin inventory value at transaction time (Wiki midpoint, gp). */
    private final Long inventoryGp;
    /** Bank value at transaction time (Wiki midpoint, gp). */
    private final Long bankGp;
    /** coin + inventory + bank at transaction time (gp). */
    private final Long totalWealthGp;
    /** Stable per-account key (RuneScape account hash) of the trading account. */
    private final long accountId;
    /** In-game display name (RSN) of the trading account. */
    private final String accountName;
}
