package com.grandflipout.tracking;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Versioned persistence envelope for flip logs.
 */
@Data
public class FlipLogSnapshot
{
	private int schemaVersion = 1;
	private List<FlipLogEntry> logs = new ArrayList<>();
}

