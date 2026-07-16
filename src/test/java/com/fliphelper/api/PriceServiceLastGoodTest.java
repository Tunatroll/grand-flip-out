/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.api;

import com.fliphelper.model.ItemMapping;
import com.fliphelper.model.PriceData;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * A failed Wiki refresh must NEVER blank the panel (the "every price suddenly
 * empty for a minute" glitch, diagnosed 2026-07-16): refreshAll() used to swallow
 * the fetch failure, build a price map from the EMPTY result and swap it in — and
 * still stamp lastRefresh, so the header claimed fresh data over a blank panel.
 * Pins: last-good prices survive a failed refresh, and lastRefresh only advances
 * on success (the staleness label must tell the truth).
 */
public class PriceServiceLastGoodTest
{
    /** Scripted WikiPriceClient: first fetch returns one item, later fetches fail. */
    private static final class ScriptedWikiClient extends WikiPriceClient
    {
        private int calls = 0;

        ScriptedWikiClient()
        {
            super(null, "test", new com.google.gson.Gson());
        }

        @Override
        public java.util.List<ItemMapping> fetchMapping()
        {
            // mappings come from getMappingById() below
            return Collections.emptyList();
        }

        @Override
        public Map<Integer, ItemMapping> getMappingById()
        {
            Map<Integer, ItemMapping> m = new HashMap<>();
            ItemMapping whip = new ItemMapping();
            whip.setId(4151);
            whip.setName("Abyssal whip");
            m.put(4151, whip);
            return m;
        }

        @Override
        public Map<Integer, PriceData> fetchLatestPrices() throws IOException
        {
            calls++;
            if (calls > 1)
            {
                throw new IOException("wiki down");
            }
            return Collections.singletonMap(4151, price());
        }

        @Override
        public Map<Integer, PriceData> fetch5mPrices()
        {
            return Collections.emptyMap();
        }

        @Override
        public Map<Integer, PriceData> fetch1hPrices()
        {
            return Collections.emptyMap();
        }

        @Override
        public PriceData getCombinedData(int itemId)
        {
            return itemId == 4151 ? price() : null;
        }

        private PriceData price()
        {
            return PriceData.builder()
                .itemId(4151)
                .itemName("Abyssal whip")
                .highPrice(1_700_000L)
                .lowPrice(1_650_000L)
                .build();
        }
    }

    @Test
    public void failedRefreshKeepsLastGoodPricesAndHonestFreshness() throws Exception
    {
        PriceService service = new PriceService(new ScriptedWikiClient(), null, new com.google.gson.Gson());

        service.refreshAll(); // succeeds — one item
        assertEquals(1, service.getAggregatedPrices().size());
        assertNotNull(service.getPrice(4151));
        Instant goodRefresh = service.getLastRefresh();

        service.refreshAll(); // wiki down — must keep last-good, not swap in empty
        assertEquals("a failed refresh must never blank the last-good prices",
            1, service.getAggregatedPrices().size());
        assertNotNull(service.getPrice(4151));
        assertEquals("lastRefresh must not advance on a failed refresh (the header would lie)",
            goodRefresh, service.getLastRefresh());
    }
}
