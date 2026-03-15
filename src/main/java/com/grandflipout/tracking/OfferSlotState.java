package com.grandflipout.tracking;

import lombok.Builder;
import lombok.Data;

/**
 * State of a single GE offer slot (0-7).
 * Tracks pending offers for correlating with completed trades.
 */
@Data
@Builder
public class OfferSlotState
{
	/** Slot index in the GE (0-7). */
	private int slotIndex;
	/** Item id in this slot, or -1 if empty. */
	private int itemId;
	/** Item name for display. */
	private String itemName;
	/** Buy or sell offer. */
	private TradeRecord.Type type;
	/** Quantity offered. */
	private int quantity;
	/** Price per unit. */
	private long pricePerUnit;
	/** Quantity already filled (partial fill). */
	private int filledQuantity;
	/** Whether the offer is active (not cancelled). */
	private boolean active;
}
