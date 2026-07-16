/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.api;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.model.ItemMapping;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.model.PriceData;
import com.fliphelper.model.PriceSource;
import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central service that aggregates price data from the OSRS Wiki API.
 */
@Slf4j
public class PriceService
{
    private final GrandFlipOutConfig config;
    private final WikiPriceClient wikiClient;
    private final Gson gson;

    @Getter
    private volatile Map<Integer, PriceAggregate> aggregatedPrices = new ConcurrentHashMap<>();

    @Getter
    private Instant lastRefresh = Instant.EPOCH;

    private boolean mappingsLoaded = false;

    public PriceService(OkHttpClient httpClient, GrandFlipOutConfig config, Gson gson)
    {
        this(new WikiPriceClient(httpClient, config.userAgent(), gson), config, gson);
    }

    /** Test seam: inject a scripted WikiPriceClient (PriceServiceLastGoodTest). */
    PriceService(WikiPriceClient wikiClient, GrandFlipOutConfig config, Gson gson)
    {
        this.config = config;
        this.gson = gson;
        this.wikiClient = wikiClient;
    }

    /** The shared injected Gson, exposed so static helpers (e.g. TradeLogReader) reuse it, never new Gson(). */
    public Gson getGson()
    {
        return gson;
    }

    /**
     * Initialize by loading item mappings.
     */
    public void initialize() throws IOException
    {
        if (!mappingsLoaded)
        {
            wikiClient.fetchMapping();
            mappingsLoaded = true;
            log.info("Price service initialized with {} item mappings", wikiClient.getItemMappings().size());
        }
    }

    /**
     * Refresh all price data from the OSRS Wiki API.
     */
    public void refreshAll() throws IOException
    {
        long startTime = System.currentTimeMillis();

        if (!mappingsLoaded)
        {
            initialize();
        }

        Map<Integer, PriceData> wikiLatest = new HashMap<>();

        long wikiStart = System.currentTimeMillis();
        try
        {
            wikiLatest = wikiClient.fetchLatestPrices();
            wikiClient.fetch5mPrices();
            wikiClient.fetch1hPrices();
            long wikiDuration = System.currentTimeMillis() - wikiStart;
            log.debug("Wiki price fetch took {} ms", wikiDuration);
        }
        catch (IOException e)
        {
            long wikiDuration = System.currentTimeMillis() - wikiStart;
            log.warn("Failed to fetch Wiki prices: {}", e.getMessage());
        }

        // A failed/empty fetch must never blank the panel: keep the last-good snapshot
        // and leave lastRefresh alone so the header's staleness label tells the truth
        // (the old behavior swapped in an EMPTY map and stamped "fresh" over it).
        if (wikiLatest.isEmpty() && !aggregatedPrices.isEmpty())
        {
            log.warn("Wiki refresh returned no items — keeping last-good prices ({} items)",
                aggregatedPrices.size());
            return;
        }

        // Build new map atomically, then swap — avoids readers seeing partial data
        Map<Integer, PriceAggregate> newPrices = new ConcurrentHashMap<>();
        for (int itemId : wikiLatest.keySet())
        {
            Map<PriceSource, PriceData> sourceData = new HashMap<>();
            PriceData combined = wikiClient.getCombinedData(itemId);
            if (combined != null)
            {
                sourceData.put(PriceSource.WIKI, combined);
            }

            if (!sourceData.isEmpty())
            {
                ItemMapping mapping = wikiClient.getMappingById().get(itemId);
                String name = mapping != null ? mapping.getName() :
                    sourceData.values().iterator().next().getItemName();

                PriceAggregate aggregate = PriceAggregate.builder()
                    .itemId(itemId)
                    .itemName(name)
                    .sourceData(sourceData)
                    .mapping(mapping)
                    .build();

                newPrices.put(itemId, aggregate);
            }
        }

        // FIX: Single volatile reference swap - truly atomic for readers
        this.aggregatedPrices = newPrices;

        lastRefresh = Instant.now();

        long totalDuration = System.currentTimeMillis() - startTime;
        log.debug("Refreshed prices: {} items from Wiki ({}ms total)",
            aggregatedPrices.size(), totalDuration);
    }

    /**
     * Get aggregated price for a single item.
     */
    public PriceAggregate getPrice(int itemId)
    {
        return aggregatedPrices.get(itemId);
    }

    /**
     * Search for items by name.
     */
    public List<PriceAggregate> searchByName(String query)
    {
        String lowerQuery = query.toLowerCase();
        List<PriceAggregate> results = new ArrayList<>();
        for (PriceAggregate agg : aggregatedPrices.values())
        {
            if (agg.getItemName().toLowerCase().contains(lowerQuery))
            {
                results.add(agg);
            }
        }
        results.sort(Comparator.comparing(PriceAggregate::getItemName));
        return results;
    }

    /**
     * Get top items by margin.
     */
    public List<PriceAggregate> getTopByMargin(int limit, int minVolume)
    {
        List<PriceAggregate> sorted = new ArrayList<>();
        for (PriceAggregate agg : aggregatedPrices.values())
        {
            if (agg.getNetMarginAfterTax() > 0 && agg.getTotalVolume1h() >= minVolume)
            {
                sorted.add(agg);
            }
        }
        sorted.sort(Comparator.comparingLong(PriceAggregate::getNetMarginAfterTax).reversed());
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    /**
     * Get top items by profit per GE limit.
     */
    public List<PriceAggregate> getTopByProfitPerLimit(int limit, int minVolume)
    {
        List<PriceAggregate> sorted = new ArrayList<>();
        for (PriceAggregate agg : aggregatedPrices.values())
        {
            if (agg.getProfitPerLimit() > 0 && agg.getTotalVolume1h() >= minVolume
                && agg.getBuyLimit() > 0)
            {
                sorted.add(agg);
            }
        }
        sorted.sort(Comparator.comparingLong(PriceAggregate::getProfitPerLimit).reversed());
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    /**
     * Get items with margins exceeding a threshold percentage.
     */
    public List<PriceAggregate> getHighMarginItems(double minMarginPercent, int minVolume)
    {
        List<PriceAggregate> results = new ArrayList<>();
        for (PriceAggregate agg : aggregatedPrices.values())
        {
            if (agg.getConsensusMarginPercent() >= minMarginPercent
                && agg.getTotalVolume1h() >= minVolume)
            {
                results.add(agg);
            }
        }
        results.sort(Comparator.comparingDouble(PriceAggregate::getConsensusMarginPercent).reversed());
        return results;
    }

    public WikiPriceClient getWikiClient()
    {
        return wikiClient;
    }

    public Map<Integer, ItemMapping> getItemMappings()
    {
        return wikiClient.getMappingById();
    }

    public boolean isReady()
    {
        return mappingsLoaded && !aggregatedPrices.isEmpty();
    }

    /**
     * Get all tracked item IDs.
     */
    public List<Integer> getAllItemIds()
    {
        return new ArrayList<>(aggregatedPrices.keySet());
    }

    /**
     * Alias for getPrice() - returns PriceAggregate for an item.
     */
    public PriceAggregate getPriceAggregate(int itemId)
    {
        return getPrice(itemId);
    }

    /**
     * Alias for getPrice() - used by InvestmentHorizonAnalyzer.
     */
    public PriceAggregate getPriceData(int itemId)
    {
        return getPrice(itemId);
    }

    /**
     * Get latest PriceData for an item from the Wiki source.
     */
    public PriceData getLatestPrice(int itemId)
    {
        PriceAggregate agg = aggregatedPrices.get(itemId);
        if (agg == null || agg.getSourceData() == null)
        {
            return null;
        }
        return agg.getFromSource(PriceSource.WIKI);
    }

    /**
     * Get price timeseries for an item from the Wiki timeseries API.
     * Returns actual historical data points at 5m or 1h resolution.
     */
    public List<PriceData> getPriceTimeseries(int itemId, int hours)
    {
        try
        {
            // Use 5m resolution for shorter timeframes, 1h for longer
            String timestep = hours <= 6 ? "5m" : "1h";
            List<PriceData> series = wikiClient.fetchTimeSeries(itemId, timestep);
            if (series != null && !series.isEmpty())
            {
                return series;
            }
        }
        catch (IOException e)
        {
            log.debug("Failed to fetch timeseries for item {}: {}", itemId, e.getMessage());
        }

        // Fallback to collected history
        PriceData current = getLatestPrice(itemId);
        if (current == null)
        {
            return new ArrayList<>();
        }
        List<PriceData> series = new ArrayList<>();
        series.add(current);
        return series;
    }

    /**
     * Shutdown the price service and release resources.
     */
    public void shutdown()
    {
        aggregatedPrices.clear();
        mappingsLoaded = false;
        log.info("Price service shut down");
    }
}
