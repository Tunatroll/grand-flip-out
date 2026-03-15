package com.grandflipout.tracking;

import lombok.Builder;
import lombok.Data;

/**
 * Extended profit metrics for an item or aggregate.
 * Includes ROI, margin, and averages for display and analysis.
 */
@Data
@Builder
public class ProfitBreakdown
{
	private int itemId;
	private String itemName;
	/** Total profit/loss (gp). */
	private long profitLoss;
	/** ROI as percentage: (profit / cost) * 100. Zero if cost is 0. */
	private double roiPercent;
	/** Average buy price per unit. */
	private long avgBuyPrice;
	/** Average sell price per unit. */
	private long avgSellPrice;
	/** Margin per unit: avgSellPrice - avgBuyPrice. */
	private long marginPerUnit;
	/** Margin as percentage of buy price: (margin / avgBuyPrice) * 100. Zero if avgBuyPrice is 0. */
	private double marginPercent;
	/** Total quantity bought. */
	private int totalBoughtQty;
	/** Total quantity sold. */
	private int totalSoldQty;
	/** Number of completed flips (matched buy+sell pairs). */
	private int flipCount;
}
