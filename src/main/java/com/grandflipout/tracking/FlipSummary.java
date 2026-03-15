package com.grandflipout.tracking;

import lombok.Builder;
import lombok.Data;

/**
 * Aggregated profit/loss for an item or a set of trades (e.g. one flip).
 */
@Data
@Builder
public class FlipSummary
{
	private int itemId;
	private String itemName;
	/** Total quantity bought. */
	private int totalBoughtQty;
	/** Total quantity sold. */
	private int totalSoldQty;
	/** Total gp spent on buys. */
	private long totalBuyValue;
	/** Total gp received from sells. */
	private long totalSellValue;
	/** totalSellValue - totalBuyValue. */
	private long profitLoss;
	/** Number of buy records. */
	private int buyCount;
	/** Number of sell records. */
	private int sellCount;
}
