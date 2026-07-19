/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public final class TradeLogReader
{
    private TradeLogReader()
    {
    }

    public static List<TradeLogEntry> readRecent(File dataDir, int maxEntries, Gson gson)
    {
        if (dataDir == null || maxEntries <= 0)
        {
            return Collections.emptyList();
        }

        File logFile = new File(dataDir, "trade_log.ndjson");
        if (!logFile.exists())
        {
            return Collections.emptyList();
        }

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (!line.trim().isEmpty())
                {
                    lines.add(line);
                }
            }
        }
        catch (IOException e)
        {
            log.debug("Failed to read trade log: {}", e.getMessage());
            return Collections.emptyList();
        }

        int start = Math.max(0, lines.size() - maxEntries);
        List<TradeLogEntry> entries = new ArrayList<>();
        for (int i = lines.size() - 1; i >= start; i--)
        {
            TradeLogEntry entry = parseLine(lines.get(i), gson);
            if (entry != null)
            {
                entries.add(entry);
            }
        }
        return entries;
    }

    public static long sumProfit(List<TradeLogEntry> entries)
    {
        long total = 0;
        for (TradeLogEntry e : entries)
        {
            total += e.getProfit();
        }
        return total;
    }

    private static TradeLogEntry parseLine(String line, Gson gson)
    {
        try
        {
            JsonObject obj = gson.fromJson(line, JsonObject.class);
            if (!"flip_completed".equals(getString(obj, "event")))
            {
                return null;
            }

            TradeLogEntry entry = new TradeLogEntry();
            entry.setEvent(getString(obj, "event"));
            entry.setSource(getString(obj, "source"));
            entry.setTimestamp(getString(obj, "timestamp"));
            entry.setItemId(getInt(obj, "itemId"));
            entry.setItemName(getString(obj, "itemName"));
            entry.setQuantity(getInt(obj, "quantity"));
            entry.setBuyPrice(getLong(obj, "buyPrice"));
            entry.setSellPrice(getLong(obj, "sellPrice"));
            entry.setTax(getLong(obj, "tax"));
            entry.setProfit(getLong(obj, "profit"));
            entry.setRoiPercent(getDouble(obj, "roiPercent"));
            entry.setGeSlot(getInt(obj, "geSlot"));
            entry.setCoinGp(getLongObj(obj, "coinGp"));
            entry.setInventoryGp(getLongObj(obj, "inventoryGp"));
            entry.setBankGp(getLongObj(obj, "bankGp"));
            entry.setTotalWealthGp(getLongObj(obj, "totalWealthGp"));
            entry.setAccountName(getString(obj, "accountName"));
            entry.setAccountId(getLong(obj, "accountId"));
            return entry;
        }
        catch (Exception e)
        {
            log.debug("Skipping malformed trade log line: {}", e.getMessage());
            return null;
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

    private static double getDouble(JsonObject obj, String key)
    {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsDouble() : 0.0;
    }

    private static Long getLongObj(JsonObject obj, String key)
    {
        if (!obj.has(key) || obj.get(key).isJsonNull())
        {
            return null;
        }
        return obj.get(key).getAsLong();
    }
}
