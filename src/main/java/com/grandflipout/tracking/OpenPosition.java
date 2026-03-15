package com.grandflipout.tracking;

import lombok.Builder;
import lombok.Data;

/**
 * Tracks an open position: quantity of an item currently held (bought but not yet sold).
 * Used for unrealized P&amp;L and "what I'm holding" display.
 */
@Data
@Builder
public class OpenPosition
{
	private int itemId;
	private String itemName;
	/** Quantity currently held (total bought - total sold). */
	private int quantity;
	/** Weighted average cost per unit paid for the held quantity. */
	private long averageCostPerUnit;
	/** Total gp spent to acquire the held quantity. */
	private long totalCost;
	/** Timestamp of the most recent buy that added to this position. */
	private long lastBuyTimestampMs;
}
