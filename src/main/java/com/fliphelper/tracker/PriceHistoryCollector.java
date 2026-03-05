package com.fliphelper.tracker;

import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.model.PriceData;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects and stores rolling price history for items, enabling technical analysis.
 *
 * Snapshots the current price of each item every time recordSnapshot() is called
 * (typically every refresh cycle). Stores up to MAX_HISTORY_SIZE data points per item
 * in a circular buffer approach.
 *
 * This bridges the gap between the real-time price APIs (which only give current prices)
 * and the MarketIntelligenceEngine which needs price history for EMA/RSI/MACD/Bollinger.
 */
@Slf4j
public class PriceHistoryCollector {

    private static final int MAX_HISTORY_SIZE = 300; // ~25 hours at 5-min intervals
    private static final int MIN_HISTORY_FOR_ANALYSIS = 14; // Minimum for RSI-14

    private final PriceService priceService;

    // itemId -> list of historical mid-prices (chronological, oldest first)
    private final Map<Integer, LinkedList<Long>> priceHistory = new ConcurrentHashMap<>();

    // itemId -> list of historical timestamps
    private final Map<Integer, LinkedList<Long>> timestampHistory = new ConcurrentHashMap<>();

    // itemId -> list of historical volumes
    private final Map<Integer, LinkedList<Long>> volumeHistory = new ConcurrentHashMap<>();

    // Track items that have been seeded from Wiki timeseries API
    private final Set<Integer> seededItems = ConcurrentHashMap.newKeySet();

    private long lastSnapshotTime = 0;
    private int totalSnapshots = 0;

    public PriceHistoryCollector(PriceService priceService) {
        this.priceService = priceService;
    }

    /**
     * Record a snapshot of all current prices. Call this after each PriceService.refreshAll().
     */
    public void recordSnapshot() {
        long now = System.currentTimeMillis() / 1000;

        // Don't record too frequently (minimum 60 seconds between snapshots)
        if (now - lastSnapshotTime < 60) {
            return;
        }

        int recorded = 0;
        for (Map.Entry<Integer, PriceAggregate> entry : priceService.getAggregatedPrices().entrySet()) {
            int itemId = entry.getKey();
            PriceAggregate agg = entry.getValue();

            long midPrice = agg.getCurrentPrice();
            if (midPrice <= 0) continue;

            long volume = agg.getTotalVolume1h();

            // Get or create history lists
            LinkedList<Long> prices = priceHistory.computeIfAbsent(itemId, k -> new LinkedList<>());
            LinkedList<Long> timestamps = timestampHistory.computeIfAbsent(itemId, k -> new LinkedList<>());
            LinkedList<Long> volumes = volumeHistory.computeIfAbsent(itemId, k -> new LinkedList<>());

            // Add new data point
            prices.addLast(midPrice);
            timestamps.addLast(now);
            volumes.addLast(volume);

            // Trim to max size (circular buffer)
            while (prices.size() > MAX_HISTORY_SIZE) {
                prices.removeFirst();
                timestamps.removeFirst();
                volumes.removeFirst();
            }

            recorded++;
        }

        lastSnapshotTime = now;
        totalSnapshots++;

        if (totalSnapshots % 12 == 0) { // Log every ~1 hour
            log.info("Price history: {} snapshots recorded, tracking {} items, avg {} data points",
                totalSnapshots, priceHistory.size(),
                priceHistory.values().stream().mapToInt(LinkedList::size).average().orElse(0));
        }
    }

    /**
     * Seed price history for a specific item from the Wiki timeseries API.
     * This bootstraps the history so analysis works immediately instead of
     * waiting hours for enough data points.
     */
    public void seedFromTimeseries(int itemId) {
        if (seededItems.contains(itemId)) return;

        try {
            List<PriceData> timeseries = priceService.getWikiClient().fetchTimeSeries(itemId, "5m");

            if (timeseries == null || timeseries.isEmpty()) return;

            LinkedList<Long> prices = priceHistory.computeIfAbsent(itemId, k -> new LinkedList<>());
            LinkedList<Long> timestamps = timestampHistory.computeIfAbsent(itemId, k -> new LinkedList<>());
            LinkedList<Long> volumes = volumeHistory.computeIfAbsent(itemId, k -> new LinkedList<>());

            // Clear existing and fill from timeseries
            prices.clear();
            timestamps.clear();
            volumes.clear();

            for (PriceData pd : timeseries) {
                long avgHigh = pd.getAvgHighPrice1h() > 0 ? pd.getAvgHighPrice1h() : pd.getHighPrice();
                long avgLow = pd.getAvgLowPrice1h() > 0 ? pd.getAvgLowPrice1h() : pd.getLowPrice();

                if (avgHigh <= 0 && avgLow <= 0) continue;

                long mid = avgHigh > 0 && avgLow > 0 ? (avgHigh + avgLow) / 2 :
                          avgHigh > 0 ? avgHigh : avgLow;

                prices.addLast(mid);
                timestamps.addLast(pd.getHighTime());
                volumes.addLast(pd.getHighVolume1h() + pd.getLowVolume1h());
            }

            // Trim
            while (prices.size() > MAX_HISTORY_SIZE) {
                prices.removeFirst();
                timestamps.removeFirst();
                volumes.removeFirst();
            }

            seededItems.add(itemId);
            log.debug("Seeded {} history points for item {} from Wiki timeseries", prices.size(), itemId);

        } catch (Exception e) {
            log.debug("Could not seed timeseries for item {}: {}", itemId, e.getMessage());
        }
    }

    /**
     * Seed the top N most-traded items from timeseries on startup.
     * Rate-limited to avoid hammering the Wiki API.
     */
    public void seedTopItems(int count) {
        try {
            List<PriceAggregate> topItems = priceService.getTopByMargin(count, 10);

            int seeded = 0;
            for (PriceAggregate agg : topItems) {
                if (seeded >= count) break;

                seedFromTimeseries(agg.getItemId());
                seeded++;

                // Rate limit: 1 request per 200ms
                Thread.sleep(200);
            }

            log.info("Seeded price history for {} top items from Wiki timeseries API", seeded);
        } catch (Exception e) {
            log.warn("Error seeding top items: {}", e.getMessage());
        }
    }

    /**
     * Get price history for an item (oldest first).
     */
    public List<Long> getPriceHistory(int itemId) {
        LinkedList<Long> history = priceHistory.get(itemId);
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(history);
    }

    /**
     * Get volume history for an item (oldest first).
     */
    public List<Long> getVolumeHistory(int itemId) {
        LinkedList<Long> history = volumeHistory.get(itemId);
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(history);
    }

    /**
     * Get timestamp history for an item (oldest first).
     */
    public List<Long> getTimestampHistory(int itemId) {
        LinkedList<Long> history = timestampHistory.get(itemId);
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(history);
    }

    /**
     * Check if we have enough data points for meaningful analysis.
     */
    public boolean hasEnoughData(int itemId) {
        LinkedList<Long> history = priceHistory.get(itemId);
        return history != null && history.size() >= MIN_HISTORY_FOR_ANALYSIS;
    }

    /**
     * Get number of data points we have for an item.
     */
    public int getDataPointCount(int itemId) {
        LinkedList<Long> history = priceHistory.get(itemId);
        return history == null ? 0 : history.size();
    }

    /**
     * Get total number of items being tracked.
     */
    public int getTrackedItemCount() {
        return priceHistory.size();
    }

    /**
     * Get total snapshots recorded.
     */
    public int getTotalSnapshots() {
        return totalSnapshots;
    }

    /**
     * Check if an item has been seeded from timeseries.
     */
    public boolean isSeeded(int itemId) {
        return seededItems.contains(itemId);
    }
}
