package com.grandflipout.tracking;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.grandflipout.GrandFlipOutConfig;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Manages persistent flip logs independent of computed analytics.
 */
@Slf4j
@Singleton
public class FlipLogManager
{
	private static final String CONFIG_GROUP = "grandflipout";
	private static final String CONFIG_KEY = "flipLogsJson";
	private static final int SCHEMA_VERSION = 1;
	private static final Type FLIP_LOG_LIST_TYPE = new TypeToken<List<FlipLogEntry>>(){}.getType();

	private final GrandFlipOutConfig config;
	private final ConfigManager configManager;
	private final Gson gson = new Gson();
	private final List<FlipLogEntry> logs = new CopyOnWriteArrayList<>();
	private final AtomicLong nextId = new AtomicLong(1);

	@Inject
	public FlipLogManager(GrandFlipOutConfig config, ConfigManager configManager)
	{
		this.config = config;
		this.configManager = configManager;
	}

	public void addFromTrade(TradeRecord trade)
	{
		FlipLogEntry entry = FlipLogEntry.builder()
			.id(nextId.getAndIncrement())
			.timestampMs(trade.getTimestampMs())
			.itemId(trade.getItemId())
			.itemName(trade.getItemName())
			.type(trade.getType())
			.quantity(trade.getQuantity())
			.pricePerUnit(trade.getPricePerUnit())
			.totalValue(trade.getTotalValue())
			.offerSlot(trade.getOfferSlot())
			.build();
		logs.add(entry);
		trimToMax();
	}

	public List<FlipLogEntry> getLogs()
	{
		return Collections.unmodifiableList(new ArrayList<>(logs));
	}

	public void clear()
	{
		logs.clear();
		nextId.set(1);
	}

	public void load()
	{
		if (!config.persistFlipLogs())
		{
			return;
		}

		try
		{
			String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY);
			if (json == null || json.isBlank())
			{
				return;
			}
			List<FlipLogEntry> loaded;
			if (json.trim().startsWith("{"))
			{
				FlipLogSnapshot snapshot = gson.fromJson(json, FlipLogSnapshot.class);
				loaded = snapshot != null ? snapshot.getLogs() : null;
			}
			else
			{
				// Backward compatibility: previously persisted as plain list.
				loaded = gson.fromJson(json, FLIP_LOG_LIST_TYPE);
			}
			if (loaded == null)
			{
				return;
			}
			logs.clear();
			logs.addAll(loaded);
			long maxId = 0;
			for (FlipLogEntry e : loaded)
			{
				maxId = Math.max(maxId, e.getId());
			}
			nextId.set(maxId + 1);
			trimToMax();
		}
		catch (Exception e)
		{
			log.warn("Failed to load flip logs: {}", e.getMessage());
		}
	}

	public void save()
	{
		if (!config.persistFlipLogs())
		{
			return;
		}
		try
		{
			FlipLogSnapshot snapshot = new FlipLogSnapshot();
			snapshot.setSchemaVersion(SCHEMA_VERSION);
			snapshot.setLogs(new ArrayList<>(logs));
			String json = gson.toJson(snapshot);
			configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY, json);
		}
		catch (Exception e)
		{
			log.warn("Failed to save flip logs: {}", e.getMessage());
		}
	}

	public String exportJson()
	{
		FlipLogSnapshot snapshot = new FlipLogSnapshot();
		snapshot.setSchemaVersion(SCHEMA_VERSION);
		snapshot.setLogs(new ArrayList<>(logs));
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
			List<FlipLogEntry> imported;
			if (json.trim().startsWith("{"))
			{
				FlipLogSnapshot snapshot = gson.fromJson(json, FlipLogSnapshot.class);
				imported = snapshot != null ? snapshot.getLogs() : null;
			}
			else
			{
				imported = gson.fromJson(json, FLIP_LOG_LIST_TYPE);
			}
			if (imported == null)
			{
				return false;
			}
			logs.clear();
			logs.addAll(imported);
			long maxId = 0;
			for (FlipLogEntry e : imported)
			{
				maxId = Math.max(maxId, e.getId());
			}
			nextId.set(maxId + 1);
			trimToMax();
			return true;
		}
		catch (Exception e)
		{
			log.warn("Failed to import flip log JSON: {}", e.getMessage());
			return false;
		}
	}

	public String exportCsv()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("id,timestampMs,itemId,itemName,type,quantity,pricePerUnit,totalValue,offerSlot\n");
		for (FlipLogEntry e : logs)
		{
			sb.append(e.getId()).append(',')
				.append(e.getTimestampMs()).append(',')
				.append(e.getItemId()).append(',')
				.append(csvEscape(e.getItemName())).append(',')
				.append(e.getType()).append(',')
				.append(e.getQuantity()).append(',')
				.append(e.getPricePerUnit()).append(',')
				.append(e.getTotalValue()).append(',')
				.append(e.getOfferSlot()).append('\n');
		}
		return sb.toString();
	}

	private void trimToMax()
	{
		int max = Math.max(50, config.maxFlipLogEntries());
		while (logs.size() > max)
		{
			logs.remove(0);
		}
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
}

