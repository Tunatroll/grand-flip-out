/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Imports trade history from Flipping Utilities (FU) JSON exports into
 * GFO's NDJSON trade log format.
 *
 * <p>FU exports one JSON file per account, typically located at:
 * <ul>
 *   <li>{@code ~/.runelite/profiles/[profile]/flipping/[username].json}</li>
 *   <li>{@code ~/.runelite/flipping/[username].json}</li>
 * </ul>
 *
 * <p>Each FU file contains a top-level {@code "trades"} array. Each trade
 * object has fields like {@code itemId}, {@code buy}, {@code price},
 * {@code quantity}, and {@code time} (epoch millis).</p>
 *
 * <p>This importer pairs consecutive buy/sell trades for the same itemId
 * and writes them as GFO {@code flip_completed} NDJSON entries, applying
 * the standard 2% GE tax (capped at 5M GP per item) via {@link GeTax}.</p>
 */
@Slf4j
public final class FlippingUtilitiesImporter
{
    private FlippingUtilitiesImporter()
    {
    }

    /**
     * Parse a Flipping Utilities JSON export and convert trades into
     * GFO {@link TradeLogEntry} objects.
     *
     * <p>FU trades are paired: a buy followed by a sell of the same item
     * produces one completed flip entry. Unpaired buys/sells at the end
     * of the list are skipped.</p>
     *
     * @param jsonFile the FU export file (e.g. "Awful Pure.json")
     * @return list of converted entries, or empty list on parse failure
     */
    public static List<TradeLogEntry> importFromFile(File jsonFile, Gson gson)
    {
        if (jsonFile == null || !jsonFile.exists() || !jsonFile.canRead())
        {
            log.warn("FU import file does not exist or is unreadable: {}", jsonFile);
            return Collections.emptyList();
        }

        JsonObject root;
        try (Reader reader = new FileReader(jsonFile))
        {
            root = gson.fromJson(reader, JsonObject.class);
        }
        catch (Exception e)
        {
            log.warn("Failed to parse FU JSON file {}: {}", jsonFile.getName(), e.getMessage());
            return Collections.emptyList();
        }

        if (!root.has("trades") || !root.get("trades").isJsonArray())
        {
            log.warn("FU JSON missing 'trades' array in {}", jsonFile.getName());
            return Collections.emptyList();
        }

        JsonArray trades = root.getAsJsonArray("trades");
        List<TradeLogEntry> results = new ArrayList<>();

        // Group trades by itemId, maintaining insertion order within each group
        Map<Integer, List<JsonObject>> buysByItem = new LinkedHashMap<>();
        Map<Integer, List<JsonObject>> sellsByItem = new LinkedHashMap<>();

        for (JsonElement elem : trades)
        {
            if (!elem.isJsonObject())
            {
                continue;
            }
            JsonObject trade = elem.getAsJsonObject();
            int itemId = getInt(trade, "itemId");
            if (itemId <= 0)
            {
                continue;
            }

            boolean isBuy = getBool(trade, "buy");
            if (isBuy)
            {
                buysByItem.computeIfAbsent(itemId, k -> new ArrayList<>()).add(trade);
            }
            else
            {
                sellsByItem.computeIfAbsent(itemId, k -> new ArrayList<>()).add(trade);
            }
        }

        // Pair buys with sells for each item (FIFO matching)
        for (Map.Entry<Integer, List<JsonObject>> entry : buysByItem.entrySet())
        {
            int itemId = entry.getKey();
            List<JsonObject> buys = entry.getValue();
            List<JsonObject> sells = sellsByItem.getOrDefault(itemId, Collections.emptyList());

            int pairCount = Math.min(buys.size(), sells.size());
            for (int i = 0; i < pairCount; i++)
            {
                JsonObject buy = buys.get(i);
                JsonObject sell = sells.get(i);

                TradeLogEntry logEntry = convertPairToEntry(itemId, buy, sell);
                if (logEntry != null)
                {
                    results.add(logEntry);
                }
            }
        }

        log.info("Parsed {} completed flips from FU file {}", results.size(), jsonFile.getName());
        return results;
    }

    /**
     * Import FU trades into a GFO trade log file (NDJSON append).
     *
     * <p>Reads the existing trade log to build a duplicate-detection set,
     * then appends only new entries. Duplicates are identified by the
     * composite key (itemId + buyPrice + sellPrice + quantity + buyTime).</p>
     *
     * @param fuFile      the FU export JSON file
     * @param gfoTradeLog the GFO trade_log.ndjson file (created if absent)
     * @param gson        the client's injected Gson (do not create a fresh instance)
     * @return number of new trades imported (excluding duplicates)
     */
    public static int importToTradeLog(File fuFile, File gfoTradeLog, Gson gson)
    {
        List<TradeLogEntry> entries = importFromFile(fuFile, gson);
        if (entries.isEmpty())
        {
            return 0;
        }

        // Build set of existing trade keys for duplicate detection
        Set<String> existingKeys = loadExistingKeys(gfoTradeLog, gson);

        int imported = 0;

        try
        {
            gfoTradeLog.getParentFile().mkdirs();
            try (Writer writer = new BufferedWriter(new FileWriter(gfoTradeLog, true)))
            {
                for (TradeLogEntry entry : entries)
                {
                    String key = buildDedupeKey(entry);
                    if (existingKeys.contains(key))
                    {
                        continue;
                    }

                    Map<String, Object> ndjsonEntry = toNdjsonMap(entry);
                    writer.write(gson.toJson(ndjsonEntry));
                    writer.write('\n');
                    existingKeys.add(key);
                    imported++;
                }
            }
        }
        catch (IOException e)
        {
            log.warn("Failed to write FU imports to trade log: {}", e.getMessage());
        }

        return imported;
    }

    /**
     * Locate FU data files in the standard RuneLite directories.
     * Searches both the profiles-based path and the legacy flat path.
     *
     * @param runeliteDir the RuneLite base directory (typically ~/.runelite)
     * @return list of FU JSON files found, possibly empty
     */
    public static List<File> findFuFiles(File runeliteDir)
    {
        List<File> found = new ArrayList<>();
        if (runeliteDir == null || !runeliteDir.isDirectory())
        {
            return found;
        }

        // Legacy path: ~/.runelite/flipping/*.json
        File legacyDir = new File(runeliteDir, "flipping");
        addJsonFiles(legacyDir, found);

        // Profiles path: ~/.runelite/profiles/*/flipping/*.json
        File profilesDir = new File(runeliteDir, "profiles");
        if (profilesDir.isDirectory())
        {
            File[] profiles = profilesDir.listFiles(File::isDirectory);
            if (profiles != null)
            {
                for (File profile : profiles)
                {
                    File flippingDir = new File(profile, "flipping");
                    addJsonFiles(flippingDir, found);
                }
            }
        }

        return found;
    }

    // ==================== INTERNAL HELPERS ====================

    private static TradeLogEntry convertPairToEntry(int itemId, JsonObject buy, JsonObject sell)
    {
        long buyPrice = getLong(buy, "price");
        long sellPrice = getLong(sell, "price");
        int buyQty = getInt(buy, "quantity");
        int sellQty = getInt(sell, "quantity");
        long buyTimeMs = getLong(buy, "time");
        long sellTimeMs = getLong(sell, "time");

        if (buyPrice <= 0 || sellPrice <= 0 || buyQty <= 0 || sellQty <= 0)
        {
            return null;
        }

        int quantity = Math.min(buyQty, sellQty);

        // 2% GE tax via GeTax — the 5M cap applies PER ITEM, not on the whole
        // stack's revenue (the old inline formula under-taxed multi-item flips
        // of items above 250M gp each), and exempt items pay 0.
        long revenue = sellPrice * quantity;
        long cost = buyPrice * quantity;
        long tax = GeTax.tax(itemId, sellPrice, quantity);
        long profit = revenue - cost - tax;
        double roiPercent = cost > 0 ? (double) profit / cost * 100.0 : 0.0;

        String itemName = getString(buy, "itemName");
        if (itemName == null || itemName.isEmpty())
        {
            itemName = "Item #" + itemId;
        }

        String buyTime = buyTimeMs > 0
            ? Instant.ofEpochMilli(buyTimeMs).toString()
            : null;
        String sellTime = sellTimeMs > 0
            ? Instant.ofEpochMilli(sellTimeMs).toString()
            : null;

        TradeLogEntry entry = new TradeLogEntry();
        entry.setEvent("flip_completed");
        entry.setSource("fu_import");
        entry.setTimestamp(sellTime != null ? sellTime : Instant.now().toString());
        entry.setItemId(itemId);
        entry.setItemName(itemName);
        entry.setQuantity(quantity);
        entry.setBuyPrice(buyPrice);
        entry.setSellPrice(sellPrice);
        entry.setTax(tax);
        entry.setProfit(profit);
        entry.setRoiPercent(roiPercent);
        entry.setGeSlot(0);
        return entry;
    }

    private static Map<String, Object> toNdjsonMap(TradeLogEntry entry)
    {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("event", entry.getEvent());
        map.put("source", entry.getSource());
        map.put("timestamp", entry.getTimestamp());
        map.put("itemId", entry.getItemId());
        map.put("itemName", entry.getItemName());
        map.put("quantity", entry.getQuantity());
        map.put("buyPrice", entry.getBuyPrice());
        map.put("sellPrice", entry.getSellPrice());
        map.put("tax", entry.getTax());
        map.put("profit", entry.getProfit());
        map.put("roiPercent", entry.getRoiPercent());
        map.put("geSlot", entry.getGeSlot());
        return map;
    }

    private static Set<String> loadExistingKeys(File gfoTradeLog, Gson gson)
    {
        Set<String> keys = new HashSet<>();
        if (gfoTradeLog == null || !gfoTradeLog.exists())
        {
            return keys;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(gfoTradeLog)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.trim().isEmpty())
                {
                    continue;
                }
                try
                {
                    JsonObject obj = gson.fromJson(line, JsonObject.class);
                    int itemId = getInt(obj, "itemId");
                    long buyPrice = getLong(obj, "buyPrice");
                    long sellPrice = getLong(obj, "sellPrice");
                    int quantity = getInt(obj, "quantity");
                    String timestamp = getString(obj, "timestamp");
                    keys.add(itemId + ":" + buyPrice + ":" + sellPrice + ":" + quantity + ":" + timestamp);
                }
                catch (Exception ignored)
                {
                    // Skip malformed lines
                }
            }
        }
        catch (IOException e)
        {
            log.debug("Could not read existing trade log for dedup: {}", e.getMessage());
        }

        return keys;
    }

    private static String buildDedupeKey(TradeLogEntry entry)
    {
        return entry.getItemId() + ":"
            + entry.getBuyPrice() + ":"
            + entry.getSellPrice() + ":"
            + entry.getQuantity() + ":"
            + entry.getTimestamp();
    }

    private static void addJsonFiles(File dir, List<File> target)
    {
        if (dir == null || !dir.isDirectory())
        {
            return;
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files != null)
        {
            for (File f : files)
            {
                if (f.isFile() && f.length() > 0)
                {
                    target.add(f);
                }
            }
        }
    }

    private static String getString(JsonObject obj, String key)
    {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    private static int getInt(JsonObject obj, String key)
    {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : 0;
    }

    private static long getLong(JsonObject obj, String key)
    {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsLong() : 0L;
    }

    private static boolean getBool(JsonObject obj, String key)
    {
        return obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).getAsBoolean();
    }
}
