/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.model;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * #269: the item-page "Est. fill (limit)" label is a plugin-LOCAL estimator
 * (limit / total both-sides 1h volume) sharing the both-sides-linear bias the
 * server measurement refuted — fill is zero-then-lump and a buyer captures only
 * a share of one side, so the number is a measured-optimistic FLOOR. The label
 * must carry the same honest floor marker the server's c1 fix shipped
 * ("~Xm+" / "~X.Xh+"); "&lt;1m" (already a bound) and "—" (unknown) stay as-is.
 */
public class PriceAggregateFillLabelTest
{
    private static PriceAggregate agg(int limit, long highVol1h, long lowVol1h)
    {
        ItemMapping mapping = new ItemMapping();
        mapping.setLimit(limit);
        Map<PriceSource, PriceData> sources = new HashMap<>();
        sources.put(PriceSource.WIKI, PriceData.builder()
            .itemId(4151)
            .highVolume1h(highVol1h)
            .lowVolume1h(lowVol1h)
            .build());
        return PriceAggregate.builder()
            .itemId(4151)
            .itemName("Abyssal whip")
            .mapping(mapping)
            .sourceData(sources)
            .build();
    }

    @Test
    public void minuteLabelCarriesTheHonestFloorMarker()
    {
        // 100 limit at 1,000/h -> 6.0 minutes
        assertEquals("~6m+", agg(100, 600, 400).getFillEstimateLabel());
    }

    @Test
    public void hourLabelCarriesTheHonestFloorMarker()
    {
        // 7,000 limit at 1,000/h -> 420 minutes = 7.0h
        assertEquals("~7.0h+", agg(7000, 600, 400).getFillEstimateLabel());
    }

    @Test
    public void subMinuteBoundStaysAsIs()
    {
        // 100 limit at 12,000/h -> 0.5 minutes: "<1m" is already a bound, not a floor
        assertEquals("<1m", agg(100, 8000, 4000).getFillEstimateLabel());
    }

    @Test
    public void unknownStaysDash()
    {
        assertEquals("—", agg(0, 600, 400).getFillEstimateLabel());
    }
}
