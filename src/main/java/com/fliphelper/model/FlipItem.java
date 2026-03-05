package com.fliphelper.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class FlipItem
{
    private final int itemId;
    private final String itemName;
    private int quantity;
    private long buyPrice;
    private long sellPrice;
    private Instant buyTime;
    private Instant sellTime;
    private FlipState state;
    private int geSlot;

    public long getProfit()
    {
        if (buyPrice <= 0 || sellPrice <= 0)
        {
            return 0;
        }
        // GE tax is 2% capped at 5m per item
        long revenue = sellPrice * quantity;
        long cost = buyPrice * quantity;
        long tax = Math.min((long) (sellPrice * 0.02), 5_000_000L) * quantity;
        return revenue - cost - tax;
    }

    public double getProfitPercent()
    {
        if (buyPrice <= 0)
        {
            return 0;
        }
        return (double) getProfit() / (buyPrice * quantity) * 100.0;
    }

    public long getFlipDurationSeconds()
    {
        if (buyTime == null || sellTime == null)
        {
            return 0;
        }
        return sellTime.getEpochSecond() - buyTime.getEpochSecond();
    }

    public boolean isComplete()
    {
        return state == FlipState.COMPLETE;
    }
}
