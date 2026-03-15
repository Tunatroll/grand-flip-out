package com.grandflipout.tracking;

import lombok.Builder;
import lombok.Data;

/**
 * Persistent flip log entry for auditing and review.
 */
@Data
@Builder
public class FlipLogEntry
{
	private long id;
	private long timestampMs;
	private int itemId;
	private String itemName;
	private TradeRecord.Type type;
	private int quantity;
	private long pricePerUnit;
	private long totalValue;
	private int offerSlot;
}

