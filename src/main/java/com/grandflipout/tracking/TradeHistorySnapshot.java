package com.grandflipout.tracking;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Versioned persistence envelope for trade history records.
 */
@Data
public class TradeHistorySnapshot
{
	private int schemaVersion = 1;
	private List<TradeRecord> records = new ArrayList<>();
}

