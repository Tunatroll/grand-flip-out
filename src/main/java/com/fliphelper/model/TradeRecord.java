package com.fliphelper.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

/**
 * Represents a completed trade recorded from the GE history.
 */
@Data
@Builder
public class TradeRecord
{
    private final int itemId;
    private final String itemName;
    private final int quantity;
    private final long price;
    private final boolean bought;
    private final Instant timestamp;
    private final int geSlot;
}
