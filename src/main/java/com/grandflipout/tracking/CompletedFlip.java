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
	/** GE tax rate (2% since May 2025, capped at 5M per transaction). */
	public static final double GE_TAX_RATE = 0.02;
	public static final long GE_TAX_CAP = 5_000_000;

	private int itemId;
	private String itemName;
	private int quantity;
	private long buyCost;
	/** Gross sell proceeds before GE tax. */
	private long sellProceeds;
	/** GE tax deducted from the sell. */
	private long taxPaid;
	/** sellProceeds - buyCost - taxPaid. */
	private long profit;
	private long completedTimestampMs;
	/** Margin per unit before tax (sellPrice - buyPrice). */
	private long marginPerUnit;

	/** Calculate GE tax on a sell transaction. 2% of sell value, capped at 5M. */
	public static long calculateTax(long sellProceeds)
	{
		long tax = Math.round(sellProceeds * GE_TAX_RATE);
		return Math.min(tax, GE_TAX_CAP);
	}
}
