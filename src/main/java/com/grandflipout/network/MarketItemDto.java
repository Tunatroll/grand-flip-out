package com.grandflipout.network;

import lombok.Data;

/**
 * DTO for a single item's market data from the external API.
 * Structure can be adapted to match the actual API response (e.g. GE Tracker, OSRS Wiki).
 */
@Data
public class MarketItemDto
{
	private int id;
	private String name;
	private Long buyPrice;
	private Long sellPrice;
	private Long highPrice;
	private Long lowPrice;
	private Long volume;
}
