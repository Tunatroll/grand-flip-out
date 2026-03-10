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


@Data
@Builder
public class PriceAggregate
{
    private final int itemId;
    private final String itemName;
    private final Map<PriceSource, PriceData> sourceData;
    private final ItemMapping mapping;

    
    @Setter
    @Builder.Default
    private transient Function<Integer, List<Long>> priceHistoryProvider = null;

    
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

    
    public double getConsensusMarginPercent()
    {
        long low = getBestLowPrice();
        if (low <= 0)
        {
            return 0;
        }
        return (double) getConsensusMargin() / low * 100.0;
    }

    
    public int getBuyLimit()
    {
        return mapping != null ? mapping.getLimit() : 0;
    }

    
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

    
    public long getTotalVolume1h()
    {
        long total = 0;
        for (PriceData data : sourceData.values())
        {
            total = Math.max(total, data.getTotalVolume1h());
        }
        return total;
    }

    
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

    
    public PriceData getFromSource(PriceSource source)
    {
        return sourceData.get(source);
    }

    
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

    public long getHighPrice() { return getBestHighPrice(); }

    public long getLowPrice() { return getBestLowPrice(); }

    public long getVolume() { return getTotalVolume1h(); }

    public long getBuyPrice() { return getBestLowPrice(); }

    public long getSellPrice() { return getBestHighPrice(); }

    public long getCurrentBuyPrice() { return getBestLowPrice(); }

    public long getCurrentSellPrice() { return getBestHighPrice(); }

    public long getHourlyVolume() { return getTotalVolume1h(); }

    public long getCurrentPrice()
    {
        long high = getBestHighPrice();
        long low = getBestLowPrice();
        if (high <= 0 && low <= 0) return 0;
        if (high <= 0) return low;
        if (low <= 0) return high;
        return (high + low) / 2;
    }

    public long getEstimatedDailyVolume()
    {
        return getTotalVolume1h() * 24;
    }

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

    public boolean hasBuyAndSellActivity()
    {
        return getBestHighPrice() > 0 && getBestLowPrice() > 0;
    }

    public boolean isPrimarilySoldByFarmers()
    {
        return false;
    }

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
