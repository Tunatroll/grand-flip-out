package com.grandflipout.tracking;

import lombok.Builder;
import lombok.Data;

/**
 * A single recorded GE trade (buy or sell).
 * Used for local profit tracking and history.
 */
@Data
@Builder
public class TradeRecord
{
	public enum Type
	{
		BUY,
		SELL
	}

	/** Unique id for this record (e.g. for persistence). */
	private long id;
	/** Game item id. */
	private int itemId;
	/** Item name at time of trade (for display). */
	private String itemName;
	private Type type;
	/** Quantity traded. */
	private int quantity;
	/** Price per unit in gp. */
	private long pricePerUnit;
	/** Total value (quantity * pricePerUnit). */
	private long totalValue;
	/** System time when the trade was recorded (ms). */
	private long timestampMs;
	/** Optional: slot or offer index in the GE UI. */
	private int offerSlot;
}
