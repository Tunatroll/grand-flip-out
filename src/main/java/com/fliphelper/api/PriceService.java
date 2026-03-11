package com.fliphelper.api;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.debug.DebugManager;
import com.fliphelper.model.ItemMapping;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.model.PriceData;
import com.fliphelper.model.PriceSource;
import com.fliphelper.tracker.LocalDataCache;
import com.fliphelper.tracker.PriceHistoryCollector;
import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static net.runelite.client.RuneLite.RUNELITE_DIR;


@Slf4j
public class PriceService
{
    private final GrandFlipOutConfig config;
    private final WikiPriceClient wikiClient;
    private final RuneLitePriceClient runeLiteClient;
    private final OfficialGePriceClient officialGeClient;
    private final LocalDataCache localCache;

    @Getter
    private volatile Map<Integer, PriceAggregate> aggregatedPrices = new ConcurrentHashMap<>();

    @Getter
    private Instant lastRefresh = Instant.EPOCH;

    @Getter
    private PriceHistoryCollector historyCollector;

    @Setter
    private DebugManager debugManager;

    private boolean mappingsLoaded = false;

    public PriceService(OkHttpClient httpClient, GrandFlipOutConfig config, Gson gson)
    {
        this.config = config;
        File dataDir = new File(RUNELITE_DIR, "grand-flip-out");
        this.localCache = new LocalDataCache(dataDir, gson);
        this.wikiClient = new WikiPriceClient(httpClient, config.userAgent(), gson, localCache);
        this.runeLiteClient = new RuneLitePriceClient(httpClient);
        this.officialGeClient = new OfficialGePriceClient(httpClient);
        this.historyCollector = new PriceHistoryCollector(this);
    }

    
    public void initialize() throws IOException
    {
        if (!mappingsLoaded)
        {
            wikiClient.fetchMapping();
            mappingsLoaded = true;
            log.info("Price service initialized with {} item mappings", wikiClient.getItemMappings().size());
        }
    }

    
    public void refreshAll() throws IOException
    {
        long startTime = System.currentTimeMillis();

        if (!mappingsLoaded)
        {
            initialize();
        }

        // Fetch from all enabled sources
        Map<Integer, PriceData> wikiLatest = new HashMap<>();
        Map<Integer, PriceData> wiki5m = new HashMap<>();
        Map<Integer, PriceData> wiki1h = new HashMap<>();
        Map<Integer, PriceData> runeLitePrices = new HashMap<>();

        if (config.useWikiPrices())
        {
            long wikiStart = System.currentTimeMillis();
            try
            {
                wikiLatest = wikiClient.fetchLatestPrices();
                wiki5m = wikiClient.fetch5mPrices();
                wiki1h = wikiClient.fetch1hPrices();
                long wikiDuration = System.currentTimeMillis() - wikiStart;
                log.debug("Wiki price fetch took {} ms", wikiDuration);
                if (debugManager != null)
                {
                    debugManager.recordAPICall("wiki/latest+5m+1h", wikiDuration, true);
                }
            }
            catch (IOException e)
            {
                long wikiDuration = System.currentTimeMillis() - wikiStart;
                log.warn("Failed to fetch Wiki prices: {}", e.getMessage());
                if (debugManager != null)
                {
                    debugManager.recordAPICall("wiki/latest+5m+1h", wikiDuration, false);
                }
            }
        }

        if (config.useRuneLitePrices())
        {
            long rlStart = System.currentTimeMillis();
            try
            {
                runeLitePrices = runeLiteClient.fetchPrices();
                long rlDuration = System.currentTimeMillis() - rlStart;
                log.debug("RuneLite price fetch took {} ms", rlDuration);
                if (debugManager != null)
                {
                    debugManager.recordAPICall("runelite/prices", rlDuration, true);
                }
            }
            catch (IOException e)
            {
                long rlDuration = System.currentTimeMillis() - rlStart;
                log.warn("Failed to fetch RuneLite prices: {}", e.getMessage());
                if (debugManager != null)
                {
                    debugManager.recordAPICall("runelite/prices", rlDuration, false);
                }
            }
        }

        // Aggregate all prices
        Set<Integer> allItemIds = new HashSet<>();
        allItemIds.addAll(wikiLatest.keySet());
        allItemIds.addAll(runeLitePrices.keySet());

        // Build new map atomically, then swap — avoids readers seeing partial data
        Map<Integer, PriceAggregate> newPrices = new ConcurrentHashMap<>();
        for (int itemId : allItemIds)
        {
            Map<PriceSource, PriceData> sourceData = new HashMap<>();

            // Wiki combined data (latest + 5m + 1h)
            if (wikiLatest.containsKey(itemId))
            {
                PriceData combined = wikiClient.getCombinedData(itemId);
                if (combined != null)
                {
                    sourceData.put(PriceSource.WIKI, combined);
                }
            }

            // RuneLite data
            if (runeLitePrices.containsKey(itemId))
            {
                sourceData.put(PriceSource.RUNELITE, runeLitePrices.get(itemId));
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
                    .priceHistoryProvider(this::getPriceHistoryForItem)
                    .build();

                newPrices.put(itemId, aggregate);
            }
        }

        // FIX: Single volatile reference swap - truly atomic for readers
        this.aggregatedPrices = newPrices;

        lastRefresh = Instant.now();

        // Record snapshot for price history (enables EMA, RSI, MACD, Bollinger analysis)
        historyCollector.recordSnapshot();

        long totalDuration = System.currentTimeMillis() - startTime;
        log.info("Refreshed prices: {} items aggregated from {} sources ({}ms total)",
            aggregatedPrices.size(), getActiveSourceCount(), totalDuration);
    }

    
    public void fetchOfficialPrices(Collection<Integer> itemIds) throws IOException
    {
        if (!config.useOfficialGePrices())
        {
            return;
        }

        Map<Integer, PriceData> officialPrices = officialGeClient.fetchPrices(itemIds);
        for (Map.Entry<Integer, PriceData> entry : officialPrices.entrySet())
        {
            PriceAggregate existing = aggregatedPrices.get(entry.getKey());
            if (existing != null)
            {
                existing.getSourceData().put(PriceSource.OFFICIAL_GE, entry.getValue());
            }
        }
    }

    
    public PriceAggregate getPrice(int itemId)
    {
        return aggregatedPrices.get(itemId);
    }

    
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

    
    public List<PriceAggregate> getTopByMargin(int limit, int minVolume)
    {
        List<PriceAggregate> sorted = new ArrayList<>();
        for (PriceAggregate agg : aggregatedPrices.values())
        {
            if (agg.getConsensusMargin() > 0 && agg.getTotalVolume1h() >= minVolume)
            {
                sorted.add(agg);
            }
        }
        sorted.sort(Comparator.comparingLong(PriceAggregate::getConsensusMargin).reversed());
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    
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

    
    public List<Integer> getAllItemIds()
    {
        return new ArrayList<>(aggregatedPrices.keySet());
    }

    
    public PriceAggregate getPriceAggregate(int itemId)
    {
        return getPrice(itemId);
    }

    
    public PriceAggregate getPriceData(int itemId)
    {
        return getPrice(itemId);
    }

    
    public PriceData getLatestPrice(int itemId)
    {
        PriceAggregate agg = aggregatedPrices.get(itemId);
        if (agg == null || agg.getSourceData() == null)
        {
            return null;
        }
        // Prefer Wiki data, then RuneLite, then Official
        PriceData data = agg.getFromSource(PriceSource.WIKI);
        if (data == null)
        {
            data = agg.getFromSource(PriceSource.RUNELITE);
        }
        if (data == null)
        {
            data = agg.getFromSource(PriceSource.OFFICIAL_GE);
        }
        return data;
    }

    
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

    
    public List<Long> getPriceHistoryForItem(int itemId)
    {
        // Try collected history first
        List<Long> history = historyCollector.getPriceHistory(itemId);
        if (history.size() >= 14)
        {
            return history;
        }

        // Seed from Wiki timeseries if not enough data
        if (!historyCollector.isSeeded(itemId))
        {
            historyCollector.seedFromTimeseries(itemId);
            history = historyCollector.getPriceHistory(itemId);
        }

        return history;
    }

    
    public void shutdown()
    {
        aggregatedPrices.clear();
        historyCollector = null;
        mappingsLoaded = false;
        log.info("Price service shut down");
    }

    /**
     * Get the most recent N price data points for sparkline rendering.
     * Returns oldest→newest order. Used by SparklineRenderer.
     */
    public List<Long> getRecentPrices(int itemId, int count)
    {
        List<Long> full = getPriceHistoryForItem(itemId);
        if (full == null || full.isEmpty()) return null;
        int start = Math.max(0, full.size() - count);
        return full.subList(start, full.size());
    }

    private int getActiveSourceCount()
    {
        int count = 0;
        if (config.useWikiPrices()) count++;
        if (config.useRuneLitePrices()) count++;
        if (config.useOfficialGePrices()) count++;
        return count;
    }
}
