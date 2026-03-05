package com.fliphelper.model;

import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Aggregated price data from multiple sources for a single item.
 */
@Data
@Builder
public class PriceAggregate
{
    private final int itemId;
    private final String itemName;
    private final Map<PriceSource, PriceData> sourceData;
    private final ItemMapping mapping;

    /**
     * External price history provider — set by PriceService to connect to PriceHistoryCollector.
     * This allows getPriceHistory() to return actual data instead of empty lists.
     */
    @Setter
    @Builder.Default
    private transient Function<Integer, List<Long>> priceHistoryProvider = null;

    /**
     * Get the best (most recent) high price across all sources.
     */
    public long getBestHighPrice()
    {
        long bestTime = 0;
        long bestPrice = 0;
        for (PriceData data : sourceData.values())
        {
            if (data.getHighTime() > bestTime && data.getHighPrice() > 0)
            {
                bestTime = data.getHighTime();
                bestPrice = data.getHighPrice();
            }
        }
        return bestPrice;
    }

    /**
     * Get the best (most recent) low price across all sources.
     */
    public long getBestLowPrice()
    {
        long bestTime = 0;
        long bestPrice = 0;
        for (PriceData data : sourceData.values())
        {
            if (data.getLowTime() > bestTime && data.getLowPrice() > 0)
            {
                bestTime = data.getLowTime();
                bestPrice = data.getLowPrice();
            }
        }
        return bestPrice;
    }

    /**
     * Get the consensus margin using best available prices.
     */
    public long getConsensusMargin()
    {
        long high = getBestHighPrice();
        long low = getBestLowPrice();
        if (high <= 0 || low <= 0)
        {
            return 0;
        }
        return high - low;
    }

    /**
     * Get consensus margin as a percentage.
     */
    public double getConsensusMarginPercent()
    {
        long low = getBestLowPrice();
        if (low <= 0)
        {
            return 0;
        }
        return (double) getConsensusMargin() / low * 100.0;
    }

    /**
     * Get GE buy limit from mapping data.
     */
    public int getBuyLimit()
    {
        return mapping != null ? mapping.getLimit() : 0;
    }

    /**
     * Calculate potential profit per GE limit cycle (4 hours).
     */
    public long getProfitPerLimit()
    {
        int limit = getBuyLimit();
        if (limit <= 0)
        {
            return 0;
        }
        long margin = getConsensusMargin();
        // Account for 2% GE tax capped at 5M per item
        long sellPrice = getBestHighPrice();
        long taxPerItem = Math.min((long) (sellPrice * 0.02), 5_000_000L);
        return (margin - taxPerItem) * limit;
    }

    /**
     * Get total trade volume across sources (1h).
     */
    public long getTotalVolume1h()
    {
        long total = 0;
        for (PriceData data : sourceData.values())
        {
            total = Math.max(total, data.getTotalVolume1h());
        }
        return total;
    }

    /**
     * Get price data from a specific source.
     */
    public PriceData getFromSource(PriceSource source)
    {
        return sourceData.get(source);
    }

    /**
     * Check if high alch is profitable compared to buy price.
     */
    public boolean isAlchProfitable()
    {
        if (mapping == null || mapping.getHighalch() <= 0)
        {
            return false;
        }
        long buyPrice = getBestLowPrice();
        // Nature rune cost approximately 150gp
        return mapping.getHighalch() - 150 > buyPrice;
    }

    // ==================== Convenience aliases for dependent classes ====================

    /** Alias for getBestHighPrice() - used by RiskManager and other trackers. */
    public long getHighPrice() { return getBestHighPrice(); }

    /** Alias for getBestLowPrice() - used by RiskManager and other trackers. */
    public long getLowPrice() { return getBestLowPrice(); }

    /** Alias for getTotalVolume1h() - used by RiskManager. */
    public long getVolume() { return getTotalVolume1h(); }

    /** Alias for getBestLowPrice() - represents insta-buy price (lowest ask). */
    public long getBuyPrice() { return getBestLowPrice(); }

    /** Alias for getBestHighPrice() - represents insta-sell price (highest bid). */
    public long getSellPrice() { return getBestHighPrice(); }

    /** Alias for getBestLowPrice() - current buy price for investment analysis. */
    public long getCurrentBuyPrice() { return getBestLowPrice(); }

    /** Alias for getBestHighPrice() - current sell price for investment analysis. */
    public long getCurrentSellPrice() { return getBestHighPrice(); }

    /** Alias for getTotalVolume1h() - hourly volume for scoring. */
    public long getHourlyVolume() { return getTotalVolume1h(); }

    /** Alias for the average of high/low as "current price". */
    public long getCurrentPrice()
    {
        long high = getBestHighPrice();
        long low = getBestLowPrice();
        if (high <= 0 && low <= 0) return 0;
        if (high <= 0) return low;
        if (low <= 0) return high;
        return (high + low) / 2;
    }

    /** Estimated daily volume based on 1h volume × 24. */
    public long getEstimatedDailyVolume()
    {
        return getTotalVolume1h() * 24;
    }

    /** Get price history from the PriceHistoryCollector if wired up. */
    public List<Long> getPriceHistory()
    {
        if (priceHistoryProvider != null)
        {
            List<Long> history = priceHistoryProvider.apply(itemId);
            if (history != null && !history.isEmpty())
            {
                return history;
            }
        }
        return new ArrayList<>();
    }

    /** Check if both buy and sell activity exists. */
    public boolean hasBuyAndSellActivity()
    {
        return getBestHighPrice() > 0 && getBestLowPrice() > 0;
    }

    /** Stub - whether item is primarily sold by resource farmers/bots. */
    public boolean isPrimarilySoldByFarmers()
    {
        return false;
    }

    /** Get last updated timestamp as Date (from the most recent source data). */
    public Date getLastUpdated()
    {
        long latestTime = 0;
        for (PriceData data : sourceData.values())
        {
            latestTime = Math.max(latestTime, Math.max(data.getHighTime(), data.getLowTime()));
        }
        if (latestTime <= 0) return null;
        return new Date(latestTime * 1000L);
    }
}
