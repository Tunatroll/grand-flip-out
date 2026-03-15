package com.grandflipout.tracking;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.grandflipout.GrandFlipOutConfig;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Manages local trade history and profit calculation.
 * Thread-safe for use from both client thread and background threads.
 * Supports open positions, FIFO-matched completed flips, session stats, and profit breakdowns.
 */
@Slf4j
@Singleton
public class TradeHistoryManager
{
	private static final int GE_SLOT_COUNT = 8;
	private static final String CONFIG_GROUP = "grandflipout";
	private static final String CONFIG_KEY_RECORDS = "tradeHistoryJson";
	private static final int SCHEMA_VERSION = 1;
	private static final Type RECORD_LIST_TYPE = new TypeToken<List<TradeRecord>>(){}.getType();

	private final GrandFlipOutConfig config;
	private final ConfigManager configManager;
	private final Gson gson = new Gson();
	private final List<TradeRecord> records = new CopyOnWriteArrayList<>();
	private final AtomicLong nextId = new AtomicLong(1);
	private volatile long sessionStartMs = System.currentTimeMillis();
	private final Map<Integer, OfferSlotState> offerSlots = new ConcurrentHashMap<>();

	@Inject
	public TradeHistoryManager(GrandFlipOutConfig config, ConfigManager configManager)
	{
		this.config = config;
		this.configManager = configManager;
	}

	/**
	 * Starts a new session. Resets session stats baseline.
	 */
	public void startNewSession()
	{
		sessionStartMs = System.currentTimeMillis();
		log.debug("New tracking session started.");
	}

	/**
	 * Records a single trade. Safe to call from any thread.
	 */
	public void addTrade(TradeRecord record)
	{
		if (!config.trackingEnabled())
		{
			return;
		}
		if (record.getId() <= 0)
		{
			record.setId(nextId.getAndIncrement());
		}
		records.add(record);
		trimByConfigLimit();
		log.debug("Recorded {} {} x{} @ {} gp", record.getType(), record.getItemName(), record.getQuantity(), record.getPricePerUnit());
	}

	/**
	 * Returns an immutable snapshot of all records. Newest last if insertion order is preserved.
	 */
	public List<TradeRecord> getRecords()
	{
		return Collections.unmodifiableList(new ArrayList<>(records));
	}

	/**
	 * Returns records for a specific item id.
	 */
	public List<TradeRecord> getRecordsForItem(int itemId)
	{
		List<TradeRecord> out = new ArrayList<>();
		for (TradeRecord r : records)
		{
			if (r.getItemId() == itemId)
			{
				out.add(r);
			}
		}
		return out;
	}

	/**
	 * Computes profit/loss summary per item. Uses all recorded buys and sells.
	 */
	public List<FlipSummary> getFlipSummaries()
	{
		Map<Integer, ItemAccumulator> byItem = new ConcurrentHashMap<>();
		for (TradeRecord r : records)
		{
			byItem.compute(r.getItemId(), (id, acc) -> {
				ItemAccumulator a = acc != null ? acc : new ItemAccumulator(id, r.getItemName());
				if (r.getType() == TradeRecord.Type.BUY)
				{
					a.totalBoughtQty += r.getQuantity();
					a.totalBuyValue += r.getTotalValue();
					a.buyCount++;
				}
				else
				{
					a.totalSoldQty += r.getQuantity();
					a.totalSellValue += r.getTotalValue();
					a.sellCount++;
				}
				return a;
			});
		}
		List<FlipSummary> result = new ArrayList<>();
		for (ItemAccumulator a : byItem.values())
		{
			result.add(FlipSummary.builder()
				.itemId(a.itemId)
				.itemName(a.itemName)
				.totalBoughtQty(a.totalBoughtQty)
				.totalSoldQty(a.totalSoldQty)
				.totalBuyValue(a.totalBuyValue)
				.totalSellValue(a.totalSellValue)
				.profitLoss(a.totalSellValue - a.totalBuyValue)
				.buyCount(a.buyCount)
				.sellCount(a.sellCount)
				.build());
		}
		return result;
	}

	/**
	 * Total profit/loss across all items (sell proceeds minus buy cost).
	 */
	public long getTotalProfitLoss()
	{
		long total = 0;
		for (FlipSummary s : getFlipSummaries())
		{
			total += s.getProfitLoss();
		}
		return total;
	}

	/**
	 * Open positions: items held (bought but not yet sold) with FIFO cost basis.
	 */
	public List<OpenPosition> getOpenPositions()
	{
		Map<Integer, List<TradeRecord>> byItem = groupRecordsByItem();
		List<OpenPosition> result = new ArrayList<>();
		for (Map.Entry<Integer, List<TradeRecord>> e : byItem.entrySet())
		{
			OpenPosition pos = computeOpenPosition(e.getKey(), e.getValue());
			if (pos != null && pos.getQuantity() > 0)
			{
				result.add(pos);
			}
		}
		return result;
	}

	/**
	 * Completed flips with FIFO matching: each sell matched to earliest buys.
	 */
	public List<CompletedFlip> getCompletedFlips()
	{
		Map<Integer, List<TradeRecord>> byItem = groupRecordsByItem();
		List<CompletedFlip> result = new ArrayList<>();
		for (Map.Entry<Integer, List<TradeRecord>> e : byItem.entrySet())
		{
			result.addAll(computeCompletedFlips(e.getKey(), e.getValue()));
		}
		result.sort(Comparator.comparingLong(CompletedFlip::getCompletedTimestampMs));
		return result;
	}

	/**
	 * Session statistics (trades since session start).
	 */
	public SessionStats getSessionStats()
	{
		List<TradeRecord> sessionRecords = new ArrayList<>();
		for (TradeRecord r : records)
		{
			if (r.getTimestampMs() >= sessionStartMs)
			{
				sessionRecords.add(r);
			}
		}
		Set<Integer> items = new HashSet<>();
		long buyValue = 0;
		long sellValue = 0;
		int buyCount = 0;
		int sellCount = 0;
		for (TradeRecord r : sessionRecords)
		{
			items.add(r.getItemId());
			if (r.getType() == TradeRecord.Type.BUY)
			{
				buyValue += r.getTotalValue();
				buyCount++;
			}
			else
			{
				sellValue += r.getTotalValue();
				sellCount++;
			}
		}
		return SessionStats.builder()
			.sessionStartMs(sessionStartMs)
			.profitLoss(sellValue - buyValue)
			.buyCount(buyCount)
			.sellCount(sellCount)
			.totalBuyValue(buyValue)
			.totalSellValue(sellValue)
			.itemsTraded(items.size())
			.build();
	}

	/**
	 * Extended profit breakdown per item (ROI, margin %, averages).
	 */
	public List<ProfitBreakdown> getProfitBreakdowns()
	{
		List<FlipSummary> summaries = getFlipSummaries();
		List<CompletedFlip> flips = getCompletedFlips();
		Map<Integer, Integer> flipCountByItem = new HashMap<>();
		for (CompletedFlip f : flips)
		{
			flipCountByItem.merge(f.getItemId(), 1, Integer::sum);
		}
		List<ProfitBreakdown> result = new ArrayList<>();
		for (FlipSummary s : summaries)
		{
			long cost = s.getTotalBuyValue();
			double roi = cost > 0 ? (100.0 * s.getProfitLoss() / cost) : 0;
			long avgBuy = s.getTotalBoughtQty() > 0 ? s.getTotalBuyValue() / s.getTotalBoughtQty() : 0;
			long avgSell = s.getTotalSoldQty() > 0 ? s.getTotalSellValue() / s.getTotalSoldQty() : 0;
			long margin = avgSell - avgBuy;
			double marginPct = avgBuy > 0 ? (100.0 * margin / avgBuy) : 0;
			result.add(ProfitBreakdown.builder()
				.itemId(s.getItemId())
				.itemName(s.getItemName())
				.profitLoss(s.getProfitLoss())
				.roiPercent(roi)
				.avgBuyPrice(avgBuy)
				.avgSellPrice(avgSell)
				.marginPerUnit(margin)
				.marginPercent(marginPct)
				.totalBoughtQty(s.getTotalBoughtQty())
				.totalSoldQty(s.getTotalSoldQty())
				.flipCount(flipCountByItem.getOrDefault(s.getItemId(), 0))
				.build());
		}
		return result;
	}

	/**
	 * Updates the state of a GE offer slot (0-7).
	 */
	public void updateOfferSlot(int slotIndex, OfferSlotState state)
	{
		if (slotIndex >= 0 && slotIndex < GE_SLOT_COUNT)
		{
			offerSlots.put(slotIndex, state);
		}
	}

	/**
	 * Returns current offer slot states. Empty slots may be absent.
	 */
	public Map<Integer, OfferSlotState> getOfferSlots()
	{
		return Collections.unmodifiableMap(new HashMap<>(offerSlots));
	}

	/**
	 * Clears an offer slot (e.g. when offer completes or is cancelled).
	 */
	public void clearOfferSlot(int slotIndex)
	{
		offerSlots.remove(slotIndex);
	}

	private Map<Integer, List<TradeRecord>> groupRecordsByItem()
	{
		Map<Integer, List<TradeRecord>> byItem = new HashMap<>();
		for (TradeRecord r : records)
		{
			byItem.computeIfAbsent(r.getItemId(), k -> new ArrayList<>()).add(r);
		}
		for (List<TradeRecord> list : byItem.values())
		{
			list.sort(Comparator.comparingLong(TradeRecord::getTimestampMs));
		}
		return byItem;
	}

	private OpenPosition computeOpenPosition(int itemId, List<TradeRecord> itemRecords)
	{
		Queue<BuyLot> lots = new LinkedList<>();
		String itemName = "";
		for (TradeRecord r : itemRecords)
		{
			itemName = r.getItemName();
			if (r.getType() == TradeRecord.Type.BUY)
			{
				lots.add(new BuyLot(r.getQuantity(), r.getPricePerUnit(), r.getTimestampMs()));
			}
			else
			{
				int remaining = r.getQuantity();
				while (remaining > 0 && !lots.isEmpty())
				{
					BuyLot lot = lots.peek();
					int consume = Math.min(remaining, lot.quantity);
					lot.quantity -= consume;
					remaining -= consume;
					if (lot.quantity <= 0)
					{
						lots.poll();
					}
				}
			}
		}
		int totalQty = 0;
		long totalCost = 0;
		long latestBuyTs = 0;
		for (BuyLot lot : lots)
		{
			totalQty += lot.quantity;
			totalCost += (long) lot.quantity * lot.pricePerUnit;
			if (lot.timestampMs > latestBuyTs)
			{
				latestBuyTs = lot.timestampMs;
			}
		}
		if (totalQty <= 0)
		{
			return null;
		}
		return OpenPosition.builder()
			.itemId(itemId)
			.itemName(itemName)
			.quantity(totalQty)
			.averageCostPerUnit(totalCost / totalQty)
			.totalCost(totalCost)
			.lastBuyTimestampMs(latestBuyTs)
			.build();
	}

	private List<CompletedFlip> computeCompletedFlips(int itemId, List<TradeRecord> itemRecords)
	{
		List<CompletedFlip> result = new ArrayList<>();
		Queue<BuyLot> lots = new LinkedList<>();
		String itemName = "";
		for (TradeRecord r : itemRecords)
		{
			itemName = r.getItemName();
			if (r.getType() == TradeRecord.Type.BUY)
			{
				lots.add(new BuyLot(r.getQuantity(), r.getPricePerUnit(), r.getTimestampMs()));
			}
			else
			{
				int remaining = r.getQuantity();
				long sellPrice = r.getPricePerUnit();
				long sellTimestamp = r.getTimestampMs();
				while (remaining > 0 && !lots.isEmpty())
				{
					BuyLot lot = lots.peek();
					int consume = Math.min(remaining, lot.quantity);
					long lotPrice = lot.pricePerUnit;
					lot.quantity -= consume;
					remaining -= consume;
					if (lot.quantity <= 0)
					{
						lots.poll();
					}
					long buyCost = (long) consume * lotPrice;
					long sellProceeds = (long) consume * sellPrice;
					result.add(CompletedFlip.builder()
						.itemId(itemId)
						.itemName(itemName)
						.quantity(consume)
						.buyCost(buyCost)
						.sellProceeds(sellProceeds)
						.profit(sellProceeds - buyCost)
						.completedTimestampMs(sellTimestamp)
						.marginPerUnit(sellPrice - lotPrice)
						.build());
				}
			}
		}
		return result;
	}

	/**
	 * Load persisted trade history. No-op if persistence is disabled or not implemented yet.
	 */
	public void load()
	{
		if (!config.persistTradeHistory())
		{
			return;
		}
		try
		{
			String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_RECORDS);
			if (json == null || json.isBlank())
			{
				return;
			}
			List<TradeRecord> loaded;
			if (json.trim().startsWith("{"))
			{
				TradeHistorySnapshot snapshot = gson.fromJson(json, TradeHistorySnapshot.class);
				loaded = snapshot != null ? snapshot.getRecords() : null;
			}
			else
			{
				// Backward compatibility: previously persisted as plain list.
				loaded = gson.fromJson(json, RECORD_LIST_TYPE);
			}
			if (loaded == null)
			{
				return;
			}
			records.clear();
			records.addAll(loaded);
			trimByConfigLimit();
			long maxId = 0;
			for (TradeRecord r : loaded)
			{
				maxId = Math.max(maxId, r.getId());
			}
			nextId.set(maxId + 1);
			log.debug("Loaded {} trade history records.", loaded.size());
		}
		catch (Exception e)
		{
			log.warn("Failed to load trade history: {}", e.getMessage());
		}
	}

	/**
	 * Save trade history to persistent storage. No-op if persistence is disabled or not implemented yet.
	 */
	public void save()
	{
		if (!config.persistTradeHistory())
		{
			return;
		}
		try
		{
			TradeHistorySnapshot snapshot = new TradeHistorySnapshot();
			snapshot.setSchemaVersion(SCHEMA_VERSION);
			snapshot.setRecords(new ArrayList<>(records));
			String json = gson.toJson(snapshot);
			configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_RECORDS, json);
			log.debug("Saved {} trade history records.", records.size());
		}
		catch (Exception e)
		{
			log.warn("Failed to save trade history: {}", e.getMessage());
		}
	}

	public String exportJson()
	{
		TradeHistorySnapshot snapshot = new TradeHistorySnapshot();
		snapshot.setSchemaVersion(SCHEMA_VERSION);
		snapshot.setRecords(new ArrayList<>(records));
		return gson.toJson(snapshot);
	}

	public boolean importJson(String json)
	{
		if (json == null || json.isBlank())
		{
			return false;
		}
		try
		{
			List<TradeRecord> imported;
			if (json.trim().startsWith("{"))
			{
				TradeHistorySnapshot snapshot = gson.fromJson(json, TradeHistorySnapshot.class);
				imported = snapshot != null ? snapshot.getRecords() : null;
			}
			else
			{
				imported = gson.fromJson(json, RECORD_LIST_TYPE);
			}
			if (imported == null)
			{
				return false;
			}
			records.clear();
			records.addAll(imported);
			long maxId = 0;
			for (TradeRecord r : imported)
			{
				maxId = Math.max(maxId, r.getId());
			}
			nextId.set(maxId + 1);
			trimByConfigLimit();
			return true;
		}
		catch (Exception e)
		{
			log.warn("Failed to import trade history JSON: {}", e.getMessage());
			return false;
		}
	}

	public String exportCsv()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("id,timestampMs,itemId,itemName,type,quantity,pricePerUnit,totalValue,offerSlot\n");
		for (TradeRecord r : records)
		{
			sb.append(r.getId()).append(',')
				.append(r.getTimestampMs()).append(',')
				.append(r.getItemId()).append(',')
				.append(csvEscape(r.getItemName())).append(',')
				.append(r.getType()).append(',')
				.append(r.getQuantity()).append(',')
				.append(r.getPricePerUnit()).append(',')
				.append(r.getTotalValue()).append(',')
				.append(r.getOfferSlot()).append('\n');
		}
		return sb.toString();
	}

	public void clearHistory()
	{
		records.clear();
		nextId.set(1);
	}

	private static String csvEscape(String value)
	{
		if (value == null)
		{
			return "";
		}
		String escaped = value.replace("\"", "\"\"");
		return "\"" + escaped + "\"";
	}

	private void trimByConfigLimit()
	{
		int max = Math.max(50, config.maxTradeHistoryEntries());
		while (records.size() > max)
		{
			records.remove(0);
		}
	}

	/** FIFO buy lot for cost basis tracking. */
	private static class BuyLot
	{
		int quantity;
		final long pricePerUnit;
		final long timestampMs;

		BuyLot(int quantity, long pricePerUnit, long timestampMs)
		{
			this.quantity = quantity;
			this.pricePerUnit = pricePerUnit;
			this.timestampMs = timestampMs;
		}
	}

	/** Mutable accumulator for aggregating trade records by item. */
	private static class ItemAccumulator
	{
		final int itemId;
		final String itemName;
		int totalBoughtQty;
		int totalSoldQty;
		long totalBuyValue;
		long totalSellValue;
		int buyCount;
		int sellCount;

		ItemAccumulator(int itemId, String itemName)
		{
			this.itemId = itemId;
			this.itemName = itemName != null ? itemName : "";
		}
	}
}
