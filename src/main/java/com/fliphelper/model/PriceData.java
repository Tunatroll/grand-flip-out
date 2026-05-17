package com.fliphelper.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;

@Data
@Builder
public class PriceData
{
    private final int itemId;
    private final String itemName;
    private final long highPrice;
    private final long lowPrice;
    private final long highTime;
    private final long lowTime;
    private final long avgHighPrice5m;
    private final long avgLowPrice5m;
    private final long highVolume5m;
    private final long lowVolume5m;
    private final long avgHighPrice1h;
    private final long avgLowPrice1h;
    private final long highVolume1h;
    private final long lowVolume1h;
    private final PriceSource source;

    public long getMargin()
    {
        if (highPrice <= 0 || lowPrice <= 0)
        {
            return 0;
        }
        return highPrice - lowPrice;
    }

    public double getMarginPercent()
    {
        if (lowPrice <= 0)
        {
            return 0;
        }
        return (double) getMargin() / lowPrice * 100.0;
    }

    public long getTotalVolume5m()
    {
        return highVolume5m + lowVolume5m;
    }

    public long getTotalVolume1h()
    {
        return highVolume1h + lowVolume1h;
    }

    public long getRoi(int quantity)
    {
        return getMargin() * quantity;
    }

    /**
     * Get the last updated timestamp as LocalDateTime (derived from highTime/lowTime).
     */
    public LocalDateTime getLastUpdated()
    {
        long latestEpoch = Math.max(highTime, lowTime);
        if (latestEpoch <= 0) return LocalDateTime.now();
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(latestEpoch), ZoneId.systemDefault());
    }
}
