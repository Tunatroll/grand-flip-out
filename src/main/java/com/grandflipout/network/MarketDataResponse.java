package com.grandflipout.network;

import java.util.Collections;
import java.util.List;
import lombok.Data;

/**
 * Container for parsed market API response.
 * Actual field names can be adjusted to match the API (e.g. "items", "data", "results").
 */
@Data
public class MarketDataResponse
{
	private List<MarketItemDto> items = Collections.emptyList();
	private List<MarketOpportunityDto> opportunities = Collections.emptyList();
	private Long timestamp;
	private String source;
}
