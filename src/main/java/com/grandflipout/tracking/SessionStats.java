package com.grandflipout.tracking;

import lombok.Builder;
import lombok.Data;

/**
 * Session-level statistics for the current tracking session.
 * Resets when the plugin starts or when the user explicitly starts a new session.
 */
@Data
@Builder
public class SessionStats
{
	/** When this session started (ms). */
	private long sessionStartMs;
	/** Profit/loss this session (gp). */
	private long profitLoss;
	/** Number of buy trades this session. */
	private int buyCount;
	/** Number of sell trades this session. */
	private int sellCount;
	/** Total gp spent on buys this session. */
	private long totalBuyValue;
	/** Total gp received from sells this session. */
	private long totalSellValue;
	/** Number of distinct items traded this session. */
	private int itemsTraded;
}
