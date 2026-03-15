package com.grandflipout.network;

import lombok.Data;

/**
 * Ranked opportunity item from backend intelligence endpoint.
 */
@Data
public class MarketOpportunityDto
{
	private int itemId;
	private String itemName;
	private Long buyPrice;
	private Long sellPrice;
	private Long marginGp;
	private Long taxPerUnit;
	private Double marginPercent;
	private Double confidence;
	private Long volume;
	private String reason;
}

