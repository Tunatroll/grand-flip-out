/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

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
     * Get the timestamp of the most recent high price.
     */
    public long getLatestHighTime()
    {
        long latestTime = 0;
        for (PriceData data : sourceData.values())
        {
            if (data.getHighTime() > latestTime)
            {
                latestTime = data.getHighTime();
            }
        }
        return latestTime;
    }

    /**
     * Get the timestamp of the most recent low price.
     */
    public long getLatestLowTime()
    {
        long latestTime = 0;
        for (PriceData data : sourceData.values())
        {
            if (data.getLowTime() > latestTime)
            {
                latestTime = data.getLowTime();
            }
        }
        return latestTime;
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

    /**
     * Lightweight confidence score (0-100) for UI explainability.
     * Hub-safe heuristic: higher liquidity + valid spread + buy limit => higher confidence.
     */
    public int getLocalConfidenceScore()
    {
        long volume1h = getTotalVolume1h();
        long low = getBestLowPrice();
        long high = getBestHighPrice();
        long margin = getConsensusMargin();
        int limit = getBuyLimit();

        if (low <= 0 || high <= 0 || margin <= 0)
        {
            return 0;
        }

        int score = 25; // baseline for valid bid/ask

        if (volume1h >= 20_000)
        {
            score += 35;
        }
        else if (volume1h >= 5_000)
        {
            score += 25;
        }
        else if (volume1h >= 1_000)
        {
            score += 15;
        }
        else
        {
            score += 5;
        }

        double marginPct = getConsensusMarginPercent();
        if (marginPct >= 1.0 && marginPct <= 8.0)
        {
            score += 20;
        }
        else if (marginPct > 0.4 && marginPct <= 12.0)
        {
            score += 10;
        }

        if (limit >= 1000)
        {
            score += 12;
        }
        else if (limit > 0)
        {
            score += 6;
        }

        return Math.min(100, Math.max(0, score));
    }

    /**
     * Human-readable confidence bucket for quick scanning.
     */
    public String getLocalConfidenceLabel()
    {
        int score = getLocalConfidenceScore();
        if (score >= 75)
        {
            return "High";
        }
        if (score >= 50)
        {
            return "Medium";
        }
        if (score > 0)
        {
            return "Low";
        }
        return "N/A";
    }

    /**
     * Lightweight risk bucket derived from spread shape and liquidity stress.
     */
    public String getLocalRiskLabel()
    {
        long volume1h = getTotalVolume1h();
        double marginPct = getConsensusMarginPercent();

        if (volume1h <= 0 || marginPct <= 0.0)
        {
            return "Unknown";
        }

        // Wide spreads on thin volume are most likely to gap/slip.
        if (volume1h < 1_000 && marginPct > 6.0)
        {
            return "High";
        }

        if (volume1h < 3_000 && marginPct > 3.5)
        {
            return "Medium";
        }

        if (volume1h >= 10_000 && marginPct <= 5.0)
        {
            return "Low";
        }

        return "Medium";
    }

    /**
     * Compact reason code for local signal transparency (no black-box feel).
     */
    public String getLocalSignalReasonCode()
    {
        long volume1h = getTotalVolume1h();
        double marginPct = getConsensusMarginPercent();
        int limit = getBuyLimit();

        String volCode;
        if (volume1h >= 20_000)
        {
            volCode = "VOL_H";
        }
        else if (volume1h >= 5_000)
        {
            volCode = "VOL_M";
        }
        else if (volume1h > 0)
        {
            volCode = "VOL_L";
        }
        else
        {
            volCode = "VOL_0";
        }

        String spreadCode;
        if (marginPct <= 0.0)
        {
            spreadCode = "SPR_0";
        }
        else if (marginPct > 8.0)
        {
            spreadCode = "SPR_W";
        }
        else if (marginPct > 3.0)
        {
            spreadCode = "SPR_M";
        }
        else
        {
            spreadCode = "SPR_T";
        }

        String limitCode = limit >= 1000 ? "LIM_H" : (limit > 0 ? "LIM_L" : "LIM_0");
        return volCode + "|" + spreadCode + "|" + limitCode;
    }
}
