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
        long revenue = sellPrice * quantity;
        long cost = buyPrice * quantity;
        // GE tax: 2% of sell price per item, capped at 5M per item (not per transaction)
        long taxPerItem = Math.min((long)(sellPrice * 0.02), 5_000_000L);
        long totalTax = taxPerItem * quantity;
        return revenue - cost - totalTax;
    }

    /**
     * Get the GE tax for this flip.
     */
    public long getTax()
    {
        if (sellPrice <= 0)
        {
            return 0;
        }
        long taxPerItem = Math.min((long)(sellPrice * 0.02), 5_000_000L);
        return taxPerItem * quantity;
    }

    /**
     * Get profit per item (after tax).
     */
    public long getProfitPerItem()
    {
        return quantity > 0 ? getProfit() / quantity : 0;
    }

    /**
     * Get return on investment as percentage.
     */
    public double getRoi()
    {
        if (buyPrice <= 0 || quantity <= 0)
        {
            return 0;
        }
        long investment = buyPrice * quantity;
        return (double) getProfit() / investment * 100.0;
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
