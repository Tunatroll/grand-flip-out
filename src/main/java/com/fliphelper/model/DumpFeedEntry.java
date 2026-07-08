/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.model;

import lombok.Value;

/**
 * One entry in the free F2P dump feed (a recent crash-buy). Read-only and
 * informational — anonymous users see F2P items only; members items are gated
 * server-side behind an account/token.
 */
@Value
public class DumpFeedEntry
{
    int itemId;
    String itemName;
    boolean members;
    long buyPrice;
    Long sellTarget;
    Long netMargin;
    Double recoveryProb;
    double percentChange;
    String tier;
}
