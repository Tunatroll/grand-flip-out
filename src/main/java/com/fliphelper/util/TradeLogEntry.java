/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.util;

import lombok.Data;

/**
 * Parsed row from trade_log.ndjson (local-only flip completion events).
 */
@Data
public class TradeLogEntry
{
    private String event;
    private String source;
    private String timestamp;
    private int itemId;
    private String itemName;
    private int quantity;
    private long buyPrice;
    private long sellPrice;
    private long tax;
    private long profit;
    private double roiPercent;
    private int geSlot;
    private Long coinGp;
    private Long inventoryGp;
    private Long bankGp;
    private Long totalWealthGp;
    /** Account display name (RSN); null for pre-account log lines. */
    private String accountName;
    /** Stable account hash; 0 for pre-account log lines. */
    private long accountId;
}
