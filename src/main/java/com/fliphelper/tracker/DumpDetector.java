package com.fliphelper.tracker;

import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.model.PriceData;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;


@Slf4j
public class DumpDetector
{
    // Configurable thresholds — more sensitive to catch dumps early
    private static final double PRICE_CHANGE_THRESHOLD = 2.0;   // 2% deviation = alert (was 5%)
    private static final double VOLUME_SPIKE_MULTIPLIER = 2.0;  // 2x normal volume = spike (was 3x)
    private static final double SPREAD_WARNING_PERCENT = 8.0;   // 8% spread = unusual (was 10%)
    private static final double PRESSURE_THRESHOLD = 2.5;       // 2.5x sell/buy imbalance = pressure

    private final PriceService priceService;

    // Historical snapshots for trend analysis
    private final Map<Integer, List<PriceSnapshot>> priceHistory = new ConcurrentHashMap<>();
    private static final int MAX_SNAPSHOTS = 60;       // Keep 60 snapshots per item (~5h at 5min intervals)
    private static final int MAX_TRACKED_ITEMS = 5000;  // Cap tracked items to prevent memory bloat
    private static final long STALE_ITEM_MILLIS = 86_400_000; // 24h — remove items with no new data
    private long lastCleanupTime = System.currentTimeMillis();

    public DumpDetector(PriceService priceService)
    {
        this.priceService = priceService;
    }

    
    public void takeSnapshot()
    {
        long startTime = System.currentTimeMillis();
        Instant now = Instant.now();
        var aggregates = priceService.getAggregatedPrices();
        if (aggregates == null) return;

        for (PriceAggregate agg : aggregates.values())
        {
            if (agg == null) continue;
            PriceSnapshot snapshot = new PriceSnapshot();
            snapshot.setTimestamp(now);
            snapshot.setHighPrice(agg.getBestHighPrice());
            snapshot.setLowPrice(agg.getBestLowPrice());
            snapshot.setVolume1h(agg.getTotalVolume1h());

            List<PriceSnapshot> history = priceHistory.computeIfAbsent(agg.getItemId(), k -> new CopyOnWriteArrayList<>());
            history.add(snapshot);

            // Trim old snapshots — enforce strict cap to prevent memory leaks
            if (history.size() > MAX_SNAPSHOTS)
            {
                // Remove oldest 10% of entries to amortize removal cost
                int toRemove = Math.max(1, MAX_SNAPSHOTS / 10);
                for (int i = 0; i < toRemove && !history.isEmpty(); i++)
                {
                    history.remove(0);
                }
            }
        }

        // Periodic stale item cleanup (every 30 minutes)
        long now2 = System.currentTimeMillis();
        if (now2 - lastCleanupTime > 1_800_000)
        {
            lastCleanupTime = now2;
            Instant staleThreshold = Instant.now().minusMillis(STALE_ITEM_MILLIS);
            priceHistory.entrySet().removeIf(entry -> {
                List<PriceSnapshot> h = entry.getValue();
                if (h.isEmpty()) return true;
                return h.get(h.size() - 1).getTimestamp().isBefore(staleThreshold);
            });

            // If still over cap, remove items with fewest snapshots
            if (priceHistory.size() > MAX_TRACKED_ITEMS)
            {
                List<Map.Entry<Integer, List<PriceSnapshot>>> sorted = new ArrayList<>(priceHistory.entrySet());
                sorted.sort(Comparator.comparingInt(e -> e.getValue().size()));
                int toRemove = priceHistory.size() - MAX_TRACKED_ITEMS;
                for (int i = 0; i < toRemove && i < sorted.size(); i++)
                {
                    priceHistory.remove(sorted.get(i).getKey());
                }
            }
            log.debug("DumpDetector cleanup: tracking {} items", priceHistory.size());
        }

        long duration = System.currentTimeMillis() - startTime;
        log.debug("Price snapshot taken for {} items in {}ms", priceHistory.size(), duration);
    }

    
    public List<PriceAlert> detectAnomalies()
    {
        long startTime = System.currentTimeMillis();
        List<PriceAlert> alerts = new ArrayList<>();

        for (PriceAggregate agg : priceService.getAggregatedPrices().values())
        {
            if (agg == null) continue;
            PriceData wikiData = agg.getFromSource(com.fliphelper.model.PriceSource.WIKI);
            if (wikiData == null) continue;

            long instaBuy = agg.getBestHighPrice();
            long instaSell = agg.getBestLowPrice();
            if (instaBuy <= 0 || instaSell <= 0) continue;

            long avg1hHigh = wikiData.getAvgHighPrice1h();
            long avg1hLow = wikiData.getAvgLowPrice1h();
            long avg5mHigh = wikiData.getAvgHighPrice5m();
            long avg5mLow = wikiData.getAvgLowPrice5m();

            // Skip items with no average data
            if (avg1hHigh <= 0 || avg1hLow <= 0) continue;

            // 1. Price deviation from 1h average
            double highDeviation = avg1hHigh > 0 ? ((double)(instaBuy - avg1hHigh) / avg1hHigh) * 100 : 0;
            double lowDeviation = avg1hLow > 0 ? ((double)(instaSell - avg1hLow) / avg1hLow) * 100 : 0;

            // 2. Short-term momentum (5m vs 1h)
            double momentum = 0;
            if (avg5mHigh > 0 && avg1hHigh > 0)
            {
                momentum = ((double)(avg5mHigh - avg1hHigh) / avg1hHigh) * 100;
            }

            // 3. Volume spike detection
            long currentVol = wikiData.getTotalVolume5m();
            long hourlyVol = wikiData.getTotalVolume1h();
            // Normalize: expected 5m vol = hourly / 12
            long expected5mVol = hourlyVol / 12;
            double volumeSpike = expected5mVol > 0 ? (double) currentVol / expected5mVol : 0;

            // 4. Spread analysis
            double spreadPercent = instaSell > 0 ? ((double)(instaBuy - instaSell) / instaSell) * 100 : 0;

            // Sell/buy pressure analysis (matches website logic)
            // highVolume = volume at insta-buy prices (buy-side activity)
            // lowVolume  = volume at insta-sell prices (sell-side activity)
            long buySideVol5m = wikiData.getHighVolume5m();
            long sellSideVol5m = wikiData.getLowVolume5m();
            // sellPressure > 1 means more selling than buying → bearish (dump signal)
            double sellPressure = buySideVol5m > 0 ? (double) sellSideVol5m / buySideVol5m : 0;
            // buyPressure > 1 means more buying than selling → bullish (pump signal)
            double buyPressure = sellSideVol5m > 0 ? (double) buySideVol5m / sellSideVol5m : 0;

            // Determine if this is alertworthy — more sensitive detection
            boolean isDump = highDeviation < -PRICE_CHANGE_THRESHOLD || lowDeviation < -PRICE_CHANGE_THRESHOLD
                || (sellPressure > PRESSURE_THRESHOLD && (highDeviation < -0.5 || lowDeviation < -0.5));
            boolean isPump = highDeviation > PRICE_CHANGE_THRESHOLD || lowDeviation > PRICE_CHANGE_THRESHOLD
                || (buyPressure > PRESSURE_THRESHOLD && (highDeviation > 0.5 || lowDeviation > 0.5));
            boolean isVolSpike = volumeSpike > VOLUME_SPIKE_MULTIPLIER;
            boolean isWideSPread = spreadPercent > SPREAD_WARNING_PERCENT;

            if (isDump || isPump || isVolSpike || isWideSPread)
            {
                PriceAlert alert = new PriceAlert();
                alert.setItemId(agg.getItemId());
                alert.setItemName(agg.getItemName());
                alert.setCurrentHigh(instaBuy);
                alert.setCurrentLow(instaSell);
                alert.setAvg1hHigh(avg1hHigh);
                alert.setAvg1hLow(avg1hLow);
                alert.setHighDeviation(highDeviation);
                alert.setLowDeviation(lowDeviation);
                alert.setMomentum(momentum);
                alert.setVolumeSpike(volumeSpike);
                alert.setSpreadPercent(spreadPercent);
                alert.setTimestamp(Instant.now());

                // Classify the alert with composite severity (matches website scoring)
                if (isDump) {
                    alert.setType(AlertType.DUMP);
                    double baseSev = Math.abs(Math.min(highDeviation, lowDeviation));
                    if (sellPressure > 2) baseSev += sellPressure;
                    if (currentVol > 100) baseSev += 1;
                    alert.setSeverity(baseSev);
                } else if (isPump) {
                    alert.setType(AlertType.PUMP);
                    double baseSev = Math.max(highDeviation, lowDeviation);
                    if (buyPressure > 2) baseSev += buyPressure;
                    alert.setSeverity(baseSev);
                } else if (isVolSpike) {
                    alert.setType(AlertType.VOLUME_SPIKE);
                    alert.setSeverity(volumeSpike * 10);
                } else {
                    alert.setType(AlertType.WIDE_SPREAD);
                    alert.setSeverity(spreadPercent);
                }

                // Calculate opportunity score
                // Dumps can be buying opportunities, pumps can be sell signals
                double opportunityScore = 0;
                if (isDump && agg.getTotalVolume1h() > 50) {
                    // Good dump buy opportunity: high volume + big drop + reasonable spread
                    opportunityScore = Math.abs(lowDeviation) * Math.min(volumeSpike, 5) * (100 / Math.max(spreadPercent, 1));
                } else if (isPump && agg.getTotalVolume1h() > 50) {
                    opportunityScore = highDeviation * Math.min(volumeSpike, 5);
                }
                alert.setOpportunityScore(opportunityScore);

                alerts.add(alert);
            }
        }

        // Sort by severity descending
        alerts.sort(Comparator.comparingDouble(PriceAlert::getSeverity).reversed());

        long duration = System.currentTimeMillis() - startTime;
        log.debug("Anomaly detection scan completed: {} alerts found in {}ms", alerts.size(), duration);
        return alerts;
    }

    
    public List<PriceVelocity> getTopMovers(int limit)
    {
        List<PriceVelocity> movers = new ArrayList<>();

        for (Map.Entry<Integer, List<PriceSnapshot>> entry : priceHistory.entrySet())
        {
            List<PriceSnapshot> history = entry.getValue();
            if (history.size() < 3) continue;

            PriceSnapshot latest = history.get(history.size() - 1);
            PriceSnapshot oldest = history.get(0);

            long priceDiff = latest.getHighPrice() - oldest.getHighPrice();
            double durationHours = (latest.getTimestamp().getEpochSecond() - oldest.getTimestamp().getEpochSecond()) / 3600.0;
            if (durationHours <= 0) continue;

            double velocity = priceDiff / durationHours; // gp per hour
            double velocityPercent = oldest.getHighPrice() > 0 ? (priceDiff / (double) oldest.getHighPrice()) * 100 : 0;

            PriceAggregate agg = priceService.getPrice(entry.getKey());
            String name = agg != null ? agg.getItemName() : "Item " + entry.getKey();

            PriceVelocity pv = new PriceVelocity();
            pv.setItemId(entry.getKey());
            pv.setItemName(name);
            pv.setVelocityGpPerHour(velocity);
            pv.setVelocityPercent(velocityPercent);
            pv.setDirection(priceDiff > 0 ? "UP" : "DOWN");
            pv.setCurrentPrice(latest.getHighPrice());

            movers.add(pv);
        }

        movers.sort(Comparator.comparingDouble(m -> -Math.abs(m.getVelocityPercent())));
        return movers.stream().limit(limit).collect(Collectors.toList());
    }

    // DATA CLASSES

    @Data
    public static class PriceSnapshot
    {
        private Instant timestamp;
        private long highPrice;
        private long lowPrice;
        private long volume1h;
    }

    @Data
    public static class PriceAlert
    {
        private int itemId;
        private String itemName;
        private AlertType type;
        private long currentHigh;
        private long currentLow;
        private long avg1hHigh;
        private long avg1hLow;
        private double highDeviation;
        private double lowDeviation;
        private double momentum;
        private double volumeSpike;
        private double spreadPercent;
        private double severity;
        private double opportunityScore;
        private Instant timestamp;
    }

    @Data
    public static class PriceVelocity
    {
        private int itemId;
        private String itemName;
        private double velocityGpPerHour;
        private double velocityPercent;
        private String direction;
        private long currentPrice;
    }

    public enum AlertType
    {
        DUMP("Price Dump"),
        PUMP("Price Pump"),
        VOLUME_SPIKE("Volume Spike"),
        WIDE_SPREAD("Wide Spread");

        private final String displayName;
        AlertType(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }
}
