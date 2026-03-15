package com.grandflipout.tracking;

import lombok.Builder;
import lombok.Data;

/**
 * A single completed flip: matched buy(s) and sell(s) for profit attribution.
 * Uses FIFO matching: earliest buys are matched to earliest sells.
 * Enables granular flip-by-flip profit tracking.
 */
@Data
@Builder
public class CompletedFlip
{
	private int itemId;
	private String itemName;
	/** Quantity flipped in this flip. */
	private int quantity;
	/** Total gp spent on the matched buy(s). */
	private long buyCost;
	/** Total gp received from the matched sell(s). */
	private long sellProceeds;
	/** sellProceeds - buyCost. */
	private long profit;
	/** Timestamp of the sell that completed this flip. */
	private long completedTimestampMs;
	/** Optional: margin per unit (sellPrice - buyPrice) for display. */
	private long marginPerUnit;
}
