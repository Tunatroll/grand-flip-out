package com.fliphelper.tracker;

import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.model.PriceData;
import com.fliphelper.model.PriceSource;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Slf4j
public class JagexTradeIndex
{
    private final PriceService priceService;

    // Keep rolling averages for velocity calculation
    private final Map<Integer, Deque<PricePoint>> rollingPrices = new ConcurrentHashMap<>();
    private static final int ROLLING_WINDOW = 30; // 30 samples

    public JagexTradeIndex(PriceService priceService)
    {
        this.priceService = priceService;
    }

    
    public void recordTick()
    {
        Instant now = Instant.now();
        var aggregates = priceService.getAggregatedPrices();
        if (aggregates == null) return;

        for (PriceAggregate agg : aggregates.values())
        {
            if (agg == null) continue;
            long high = agg.getBestHighPrice();
            long low = agg.getBestLowPrice();
            if (high <= 0 || low <= 0) continue;

            PricePoint pp = PricePoint.builder()
                .timestamp(now)
                .highPrice(high)
                .lowPrice(low)
                .volume(agg.getTotalVolume1h())
                .build();

            Deque<PricePoint> deque = rollingPrices.computeIfAbsent(
                agg.getItemId(), k -> new ArrayDeque<>());
            deque.addLast(pp);
            while (deque.size() > ROLLING_WINDOW)
            {
                deque.pollFirst();
            }
        }
    }

    
    public List<JTIResult> computeAll()
    {
        List<JTIResult> results = new ArrayList<>();

        var aggregates = priceService.getAggregatedPrices();
        if (aggregates == null)
        {
            return results;
        }

        for (PriceAggregate agg : aggregates.values())
        {
            if (agg == null) continue;
            JTIResult result = compute(agg);
            if (result != null)
            {
                results.add(result);
            }
        }

        results.sort(Comparator.comparingDouble(JTIResult::getJtiScore).reversed());
        return results;
    }

    
    public JTIResult compute(PriceAggregate agg)
    {
        if (agg == null) return null;

        long instaBuy = agg.getBestHighPrice();   // instant buy = high price (what buyers pay)
        long instaSell = agg.getBestLowPrice();    // instant sell = low price (what sellers receive)
        // instaBuy should be >= instaSell for a valid margin (buyers pay more than sellers receive)
        if (instaBuy <= 0 || instaSell <= 0 || instaSell >= instaBuy) return null;

        int limit = agg.getBuyLimit();
        if (limit <= 0) return null;

        PriceData wikiData = agg.getFromSource(PriceSource.WIKI);

        // -- 1. FRESHNESS (0-15)
        double freshnessScore = 0;
        if (wikiData != null)
        {
            long nowSec = Instant.now().getEpochSecond();
            long highAge = wikiData.getHighTime() > 0 ? nowSec - wikiData.getHighTime() : 99999;
            long lowAge = wikiData.getLowTime() > 0 ? nowSec - wikiData.getLowTime() : 99999;
            double avgAgeMinutes = (highAge + lowAge) / 2.0 / 60.0;

            // Full score if < 2 min old, degrades linearly to 0 at 30 min
            freshnessScore = Math.max(0, 15 * (1 - avgAgeMinutes / 30.0));
        }

        // 2. LIQUIDITY (0-20)
        long vol1h = agg.getTotalVolume1h();
        // Liquidity = volume relative to limit. If vol/h >> limit, very liquid.
        double liquidityRatio = (double) vol1h / Math.max(limit, 1);
        // Full score at ratio >= 2.0 (volume is 2x the limit per hour)
        double liquidityScore = Math.min(20, 20 * Math.min(liquidityRatio / 2.0, 1.0));

        // ~~~ 3. MARGIN (0-20) ~~~
        long margin = instaBuy - instaSell;
        long taxPerItem = Math.min((long) (instaBuy * 0.02), 5_000_000L);
        long netMargin = margin - taxPerItem;
        if (netMargin <= 0) return null;

        double marginPercent = (double) netMargin / instaSell * 100.0;
        // Full score at 10% margin, linear below that
        double marginScore = Math.min(20, 20 * Math.min(marginPercent / 10.0, 1.0));

        /* 4. MOMENTUM (0-15) */
        double momentumScore = 0;
        double momentum = 0;
        if (wikiData != null && wikiData.getAvgHighPrice5m() > 0 && wikiData.getAvgHighPrice1h() > 0)
        {
            double avg5m = (wikiData.getAvgHighPrice5m() + wikiData.getAvgLowPrice5m()) / 2.0;
            double avg1h = (wikiData.getAvgHighPrice1h() + wikiData.getAvgLowPrice1h()) / 2.0;
            momentum = avg1h > 0 ? ((avg5m - avg1h) / avg1h) * 100 : 0;

            // Positive momentum (price rising) is good for current holders
            // For flippers, we want STABLE or slightly mean-reverting prices
            // So penalize extreme momentum but reward slight movement
            double absMomentum = Math.abs(momentum);
            if (absMomentum < 1.0)
            {
                momentumScore = 15; // Stable = great for flipping
            }
            else if (absMomentum < 3.0)
            {
                momentumScore = 12; // Slight movement = still good
            }
            else if (absMomentum < 5.0)
            {
                momentumScore = 7;  // Moderate movement = risky
            }
            else
            {
                momentumScore = 2;  // Volatile = dangerous for flips
            }
        }

        // 5. STABILITY (0-15) //
        double spreadPercent = (double) margin / instaSell * 100.0;
        double stabilityScore;
        if (spreadPercent < 2.0)
        {
            stabilityScore = 15; // Tight spread = very stable
        }
        else if (spreadPercent < 5.0)
        {
            stabilityScore = 12;
        }
        else if (spreadPercent < 10.0)
        {
            stabilityScore = 7;
        }
        else if (spreadPercent < 20.0)
        {
            stabilityScore = 3;
        }
        else
        {
            stabilityScore = 0; // Extremely wide = unreliable
        }

        // - 6. VELOCITY (0-15) -
        // How fast can you fill the limit and sell?
        double halfVol = Math.max(vol1h / 2.0, 1);
        double hoursToFill = (double) limit / halfVol;
        double fullCycleHours = hoursToFill * 2; // buy + sell
        // Cap at 4h limit cycle
        fullCycleHours = Math.min(fullCycleHours, 4.0);

        double velocityScore;
        if (fullCycleHours < 0.2)
        {
            velocityScore = 15; // Under 12 min = lightning fast
        }
        else if (fullCycleHours < 0.5)
        {
            velocityScore = 13;
        }
        else if (fullCycleHours < 1.0)
        {
            velocityScore = 10;
        }
        else if (fullCycleHours < 2.0)
        {
            velocityScore = 6;
        }
        else
        {
            velocityScore = Math.max(0, 4 * (1 - (fullCycleHours - 2) / 2));
        }

        // [COMPOSITE JTI]
        double jti = freshnessScore + liquidityScore + marginScore +
            momentumScore + stabilityScore + velocityScore;

        // --- PROFIT PREDICTIONS ---
        long profitPerLimit = netMargin * limit;
        double cyclesPerHour = fullCycleHours > 0 ? 1.0 / fullCycleHours : 0;
        long hourlyProfit = Math.round(profitPerLimit * cyclesPerHour);
        long per4hProfit = profitPerLimit; // 1 full limit cycle
        long dailyProfit = hourlyProfit * 16; // 16h active play
        long capitalRequired = instaSell * (long) limit;

        // -- CONFIDENCE
        // Based on freshness + liquidity + number of data points
        double confidence = (freshnessScore / 15.0 * 40) +
            (liquidityScore / 20.0 * 40) +
            (stabilityScore / 15.0 * 20);
        confidence = Math.min(100, Math.max(0, confidence));

        // MARKET REGIME
        MarketRegime regime;
        double absMomentum = Math.abs(momentum);
        if (vol1h < 10)
        {
            regime = MarketRegime.DEAD;
        }
        else if (absMomentum > 5 && spreadPercent > 10)
        {
            regime = MarketRegime.VOLATILE;
        }
        else if (absMomentum > 3)
        {
            regime = MarketRegime.TRENDING;
        }
        else
        {
            regime = MarketRegime.MEAN_REVERTING; // Best for flipping
        }

        // ~~~ PRICE VELOCITY ~~~
        double priceVelocity = 0;
        Deque<PricePoint> history = rollingPrices.get(agg.getItemId());
        if (history != null && history.size() >= 3)
        {
            PricePoint oldest = history.peekFirst();
            PricePoint newest = history.peekLast();
            long timeDiff = newest.getTimestamp().getEpochSecond() - oldest.getTimestamp().getEpochSecond();
            if (timeDiff > 0)
            {
                long priceDiff = newest.getHighPrice() - oldest.getHighPrice();
                priceVelocity = (double) priceDiff / (timeDiff / 3600.0); // gp per hour
            }
        }

        return JTIResult.builder()
            .itemId(agg.getItemId())
            .itemName(agg.getItemName())
            .jtiScore(Math.round(jti * 10) / 10.0)
            .freshnessScore(Math.round(freshnessScore * 10) / 10.0)
            .liquidityScore(Math.round(liquidityScore * 10) / 10.0)
            .marginScore(Math.round(marginScore * 10) / 10.0)
            .momentumScore(Math.round(momentumScore * 10) / 10.0)
            .stabilityScore(Math.round(stabilityScore * 10) / 10.0)
            .velocityScore(Math.round(velocityScore * 10) / 10.0)
            .buyPrice(instaSell)
            .sellPrice(instaBuy)
            .netMargin(netMargin)
            .marginPercent(Math.round(marginPercent * 100) / 100.0)
            .taxPerItem(taxPerItem)
            .volume1h(vol1h)
            .buyLimit(limit)
            .profitPerLimit(profitPerLimit)
            .hourlyProfit(hourlyProfit)
            .per4hProfit(per4hProfit)
            .dailyProfit(dailyProfit)
            .capitalRequired(capitalRequired)
            .estCycleMinutes(Math.round(fullCycleHours * 60 * 10) / 10.0)
            .confidence(Math.round(confidence * 10) / 10.0)
            .momentum(Math.round(momentum * 100) / 100.0)
            .priceVelocity(Math.round(priceVelocity))
            .regime(regime)
            .timestamp(Instant.now())
            .build();
    }

    /* DATA CLASSES */

    @Data
    @Builder
    public static class JTIResult
    {
        private final int itemId;
        private final String itemName;

        // Composite score
        private final double jtiScore;

        // Sub-scores
        private final double freshnessScore;
        private final double liquidityScore;
        private final double marginScore;
        private final double momentumScore;
        private final double stabilityScore;
        private final double velocityScore;

        // Prices
        private final long buyPrice;
        private final long sellPrice;
        private final long netMargin;
        private final double marginPercent;
        private final long taxPerItem;

        // Volume & limits
        private final long volume1h;
        private final int buyLimit;

        // Profit projections
        private final long profitPerLimit;
        private final long hourlyProfit;
        private final long per4hProfit;
        private final long dailyProfit;
        private final long capitalRequired;

        // Timing
        private final double estCycleMinutes;

        // Quality
        private final double confidence;
        private final double momentum;
        private final long priceVelocity;
        private final MarketRegime regime;
        private final Instant timestamp;
    }

    @Data
    @Builder
    public static class PricePoint
    {
        private final Instant timestamp;
        private final long highPrice;
        private final long lowPrice;
        private final long volume;
    }

    public enum MarketRegime
    {
        MEAN_REVERTING("Mean-Reverting", "Ideal for flipping - prices bounce between buy/sell"),
        TRENDING("Trending", "Prices moving directionally - risky for flips"),
        VOLATILE("Volatile", "High uncertainty - wide spreads, use caution"),
        DEAD("Dead", "Very low volume - hard to fill orders");

        private final String displayName;
        private final String description;

        MarketRegime(String displayName, String description)
        {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
}
