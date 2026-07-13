/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.api;

import com.fliphelper.model.FlipItem;
import com.fliphelper.model.FlipState;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the flip-outcome telemetry payload to the server contract
 * (server sanitizeOutcome: itemId/buyPrice/sellPrice/qty/placedAt/filledAt/
 * outcome/hitTarget) and the live-fill guard that keeps GE-history imports
 * from replaying stale outcomes into the calibrator.
 */
public class IntelligenceClientTest
{
    private static final Instant BUY = Instant.parse("2026-07-10T12:00:00Z");
    private static final Instant SELL = Instant.parse("2026-07-10T12:08:20Z");

    private FlipItem.FlipItemBuilder baseFlip()
    {
        return FlipItem.builder()
            .itemId(4151)
            .itemName("Abyssal whip")
            .quantity(3)
            .buyPrice(1_650_000L)
            .sellPrice(1_700_000L)
            .buyTime(BUY)
            .sellTime(SELL)
            .state(FlipState.COMPLETE);
    }

    @Test
    public void payloadCarriesTheFullServerContract()
    {
        String json = IntelligenceClient.flipOutcomeJson(baseFlip().frozenSellPrice(1_690_000L).build());
        assertEquals("{\"itemId\":4151,\"buyPrice\":1650000,\"sellPrice\":1700000,\"qty\":3,"
            + "\"outcome\":\"filled\",\"placedAt\":" + BUY.toEpochMilli()
            + ",\"filledAt\":" + SELL.toEpochMilli()
            + ",\"hitTarget\":true}", json);
    }

    @Test
    public void hitTargetIsFalseWhenSoldBelowTheFrozenTarget()
    {
        String json = IntelligenceClient.flipOutcomeJson(baseFlip().frozenSellPrice(1_750_000L).build());
        assertTrue(json.contains("\"hitTarget\":false"));
    }

    @Test
    public void hitTargetIsOmittedWithoutAFrozenTarget()
    {
        String json = IntelligenceClient.flipOutcomeJson(baseFlip().frozenSellPrice(0L).build());
        assertFalse(json.contains("hitTarget"));
    }

    @Test
    public void timingsAreOmittedWhenUnknown()
    {
        String json = IntelligenceClient.flipOutcomeJson(baseFlip().buyTime(null).sellTime(null).build());
        assertFalse(json.contains("placedAt"));
        assertFalse(json.contains("filledAt"));
    }

    @Test
    public void liveFillGuardAcceptsAFreshSellAndRejectsHistoryReplays()
    {
        Instant now = SELL.plusSeconds(30);
        assertTrue(IntelligenceClient.isRecentFill(baseFlip().build(), now));
        // a GE-history import replays fills that completed hours/days ago
        assertFalse(IntelligenceClient.isRecentFill(baseFlip().build(), SELL.plusSeconds(3600)));
        // unknown sell time can never be proven live
        assertFalse(IntelligenceClient.isRecentFill(baseFlip().sellTime(null).build(), now));
        assertFalse(IntelligenceClient.isRecentFill(null, now));
    }

    @Test
    public void outcomeSubmitRequiresALiveWitnessedPair()
    {
        Instant now = SELL.plusSeconds(30);
        // GeHistoryImporter pairs stamp buy==sell at import time (geSlot -1 → liveWitnessed
        // false) and previously sailed through the recency guard — the 0-minute fill factory.
        assertFalse(IntelligenceClient.shouldSubmitOutcome(baseFlip().build(), now));
        assertTrue(IntelligenceClient.shouldSubmitOutcome(baseFlip().liveWitnessed(true).build(), now));
        // the live flag alone is not enough — recency still applies
        assertFalse(IntelligenceClient.shouldSubmitOutcome(baseFlip().liveWitnessed(true).build(), SELL.plusSeconds(3600)));
        assertFalse(IntelligenceClient.shouldSubmitOutcome(null, now));
    }
}
