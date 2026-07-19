/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.tracker;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.api.PriceService;
import com.fliphelper.model.FlipItem;
import com.fliphelper.model.FlipState;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.model.TradeRecord;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Tracks active flips and maintains flip history.
 * Automatically pairs buy/sell transactions to detect completed flips.
 */
@Slf4j
public class FlipTracker
{
    /** Callback for when a flip completes (buy→sell paired). */
    public interface FlipCompleteListener
    {
        void onFlipComplete(FlipItem flip);
    }

    private FlipCompleteListener flipCompleteListener;

    public void setFlipCompleteListener(FlipCompleteListener listener)
    {
        this.flipCompleteListener = listener;
    }

    private final GrandFlipOutConfig config;
    private final PriceService priceService;
    private final Executor ioExecutor;
    // Serializes the file writes submitted to the shared executor — its pool width is
    // unspecified, and two concurrent temp-file renames would corrupt the history.
    private final Object ioLock = new Object();
    private File dataDir;
    private Gson gson;

    @Getter
    private final Map<Integer, FlipItem> activeFlips = new ConcurrentHashMap<>();

    @Getter
    private final List<FlipItem> completedFlips = Collections.synchronizedList(new ArrayList<>());

    // Track buy orders waiting to be matched with sells
    private final Map<Integer, Deque<TradeRecord>> pendingBuys = new ConcurrentHashMap<>();

    @Getter
    private final AtomicLong sessionProfit = new AtomicLong(0);

    @Getter
    private final AtomicInteger sessionFlipCount = new AtomicInteger(0);

    public FlipTracker(GrandFlipOutConfig config, PriceService priceService, File dataDir, Gson gson, Executor ioExecutor)
    {
        this.config = config;
        this.priceService = priceService;
        this.dataDir = dataDir;
        this.gson = gson;
        this.ioExecutor = ioExecutor;

        if (config.persistHistory())
        {
            loadHistory();
        }
    }

    public void switchDataDir(File newDataDir, Gson newGson)
    {
        if (config.persistHistory())
        {
            saveHistory();
        }
        this.dataDir = newDataDir;
        this.gson = newGson;
        this.completedFlips.clear();
        if (config.persistHistory())
        {
            loadHistory();
        }
    }

    // Lock for atomic buy/sell pairing to prevent race conditions
    private final Object transactionLock = new Object();

    /**
     * Record a GE transaction (buy or sell).
     * Automatically pairs buys with sells to create flip records.
     * Uses synchronized block to ensure atomic buy/sell pairing.
     */
    public void recordTransaction(TradeRecord trade)
    {
        synchronized (transactionLock)
        {
            if (trade.isBought())
            {
                // Record buy - add to pending buys
                pendingBuys.computeIfAbsent(trade.getItemId(), k -> new ArrayDeque<>()).addLast(trade);

                // Capture market sell price at buy time (frozen sell price)
                long frozen = 0;
                PriceAggregate agg = priceService.getPrice(trade.getItemId());
                if (agg != null)
                {
                    frozen = agg.getBestHighPrice();
                }

                // Create or update active flip
                FlipItem flip = FlipItem.builder()
                    .itemId(trade.getItemId())
                    .itemName(trade.getItemName())
                    .quantity(trade.getQuantity())
                    .buyPrice(trade.getPrice())
                    .frozenSellPrice(frozen)
                    .buyTime(trade.getTimestamp())
                    .state(FlipState.BOUGHT)
                    .geSlot(trade.getGeSlot())
                    .build();

                // Key by GE slot (not itemId) to track multiple flips of same item in different slots
                activeFlips.put(trade.getGeSlot(), flip);
                log.info("Recorded buy: {}x {} @ {}gp (slot {})",
                    trade.getQuantity(), trade.getItemName(),
                    trade.getPrice(), trade.getGeSlot());
            }
            else
            {
                // Record sell — match against pending buys FIFO, consuming the
                // full sell quantity across multiple buys and splitting a partial
                // buy so no quantity is dropped when a buy or sell is filled across
                // several offers (previously only one buy was matched per sell event,
                // which under-reported profit on split fills).
                Deque<TradeRecord> buys = pendingBuys.get(trade.getItemId());

                // Recover frozen sell price from the active flip for this slot
                FlipItem activeFlip = activeFlips.get(trade.getGeSlot());
                long frozenSell = activeFlip != null ? activeFlip.getFrozenSellPrice() : 0;

                int remainingSell = trade.getQuantity();
                boolean matchedAny = false;

                while (remainingSell > 0 && buys != null && !buys.isEmpty())
                {
                    TradeRecord matchedBuy = buys.pollFirst();
                    int matchQty = Math.min(matchedBuy.getQuantity(), remainingSell);

                    // Attribute the flip to the account that placed the matched buy
                    // (falls back to the sell trade's account if the buy predates
                    // account tagging, e.g. an in-flight flip across an upgrade).
                    long accountId = matchedBuy.getAccountId() != 0
                        ? matchedBuy.getAccountId() : trade.getAccountId();
                    String accountName = matchedBuy.getAccountName() != null
                        ? matchedBuy.getAccountName() : trade.getAccountName();

                    FlipItem completedFlip = FlipItem.builder()
                        .itemId(trade.getItemId())
                        .itemName(trade.getItemName())
                        .quantity(matchQty)
                        .buyPrice(matchedBuy.getPrice())
                        .sellPrice(trade.getPrice())
                        .frozenSellPrice(frozenSell)
                        .buyTime(matchedBuy.getTimestamp())
                        .sellTime(trade.getTimestamp())
                        .state(FlipState.COMPLETE)
                        .geSlot(trade.getGeSlot())
                        // live GE events carry a real slot; importers stamp geSlot -1
                        .liveWitnessed(matchedBuy.getGeSlot() >= 0 && trade.getGeSlot() >= 0)
                        .sellCoinGp(trade.getCoinGp())
                        .sellInventoryGp(trade.getInventoryGp())
                        .sellBankGp(trade.getBankGp())
                        .sellTotalWealthGp(trade.getTotalWealthGp())
                        .accountId(accountId)
                        .accountName(accountName)
                        .build();

                    recordCompletedFlip(completedFlip, trade);
                    matchedAny = true;
                    remainingSell -= matchQty;

                    // Partial buy: push the unmatched remainder back to the front.
                    if (matchQty < matchedBuy.getQuantity())
                    {
                        buys.addFirst(matchedBuy.toBuilder()
                            .quantity(matchedBuy.getQuantity() - matchQty)
                            .build());
                    }
                }

                if (matchedAny)
                {
                    activeFlips.remove(trade.getGeSlot());
                    if (config.persistHistory())
                    {
                        saveHistory();
                    }
                }
                if (remainingSell > 0)
                {
                    // Sell quantity with no tracked buy (a normal sale, not a flip).
                    log.debug("Sell of {}x {} had {} unmatched (no tracked buy)",
                        trade.getQuantity(), trade.getItemName(), remainingSell);
                }
            }
        }
    }

    /**
     * Record one completed (buy→sell paired) flip: tally profit, log, trim
     * history, persist, append to the trade log, and notify the listener.
     * Called once per matched buy so split fills produce one sub-flip each.
     */
    private void recordCompletedFlip(FlipItem completedFlip, TradeRecord sellTrade)
    {
        completedFlips.add(0, completedFlip);

        long profit = completedFlip.getProfit();
        sessionProfit.addAndGet(profit);
        sessionFlipCount.incrementAndGet();

        log.info("Completed flip: {}x {} | Buy: {}gp Sell: {}gp | Profit: {}gp",
            completedFlip.getQuantity(), completedFlip.getItemName(),
            completedFlip.getBuyPrice(), completedFlip.getSellPrice(), profit);

        // Trim history
        while (completedFlips.size() > config.maxHistoryEntries())
        {
            completedFlips.remove(completedFlips.size() - 1);
        }

        // Note: saveHistory() is batched by the caller (once per sell event) so a
        // split fill matched across several buys writes the history file only once.
        appendTradeLogEntry(completedFlip, sellTrade, "ge_event");

        // Notify listener (profile logging, Discord alerts, etc.)
        if (flipCompleteListener != null)
        {
            try { flipCompleteListener.onFlipComplete(completedFlip); }
            catch (Exception e) { log.debug("Flip listener error: {}", e.getMessage()); }
        }
    }

    /**
     * Manually add a flip (for retroactive tracking).
     */
    public void addManualFlip(FlipItem flip)
    {
        if (flip.isComplete())
        {
            completedFlips.add(0, flip);
            sessionProfit.addAndGet(flip.getProfit());
            sessionFlipCount.incrementAndGet();

            if (config.persistHistory())
            {
                saveHistory();
            }
            appendTradeLogEntry(flip, null, "manual_add");
        }
        else
        {
            activeFlips.put(flip.getItemId(), flip);
        }
    }

    /**
     * Cancel an active flip.
     */
    public void cancelFlip(int itemId)
    {
        FlipItem flip = activeFlips.remove(itemId);
        if (flip != null)
        {
            flip.setState(FlipState.CANCELLED);
            completedFlips.add(0, flip);
            pendingBuys.remove(itemId);
        }
    }

    /**
     * Get total profit over a time range.
     */
    public long getProfitSince(Instant since)
    {
        return completedFlips.stream()
            .filter(f -> f.getSellTime() != null && f.getSellTime().isAfter(since))
            .mapToLong(FlipItem::getProfit)
            .sum();
    }

    /**
     * Get the most profitable items historically.
     */
    public Map<String, Long> getMostProfitableItems(int limit)
    {
        return completedFlips.stream()
            .filter(FlipItem::isComplete)
            .collect(Collectors.groupingBy(FlipItem::getItemName,
                Collectors.summingLong(FlipItem::getProfit)))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(limit)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (a, b) -> a,
                LinkedHashMap::new
            ));
    }

    /**
     * Get flip frequency per item.
     */
    public Map<String, Long> getFlipFrequency()
    {
        return completedFlips.stream()
            .filter(FlipItem::isComplete)
            .collect(Collectors.groupingBy(FlipItem::getItemName, Collectors.counting()));
    }

    /**
     * Get average profit per flip.
     */
    public double getAverageProfitPerFlip()
    {
        int flipCount = sessionFlipCount.get();
        if (flipCount == 0)
        {
            return 0;
        }
        return (double) sessionProfit.get() / flipCount;
    }

    /**
     * Reset session statistics.
     */
    public void resetSession()
    {
        sessionProfit.set(0);
        sessionFlipCount.set(0);
        activeFlips.clear();
        pendingBuys.clear();
    }

    /**
     * Get the average buy price for an item across all active (bought, not yet sold) flips.
     * Returns 0 if no active flips exist for the item.
     */
    public long getAverageBuyPrice(int itemId)
    {
        long totalCost = 0;
        int totalQty = 0;
        for (FlipItem flip : activeFlips.values())
        {
            if (flip.getItemId() == itemId && flip.getBuyPrice() > 0)
            {
                totalCost += flip.getBuyPrice() * flip.getQuantity();
                totalQty += flip.getQuantity();
            }
        }
        return totalQty > 0 ? totalCost / totalQty : 0;
    }

    /**
     * Get GP/hr for the current session based on completed flips and elapsed time.
     */
    public long getGpPerHour()
    {
        long profit = sessionProfit.get();
        if (profit <= 0 || completedFlips.isEmpty())
        {
            return 0;
        }

        // Find earliest and latest flip times in session
        Instant earliest = null;
        Instant latest = null;
        for (FlipItem flip : completedFlips)
        {
            if (flip.getSellTime() != null && flip.getBuyTime() != null)
            {
                if (earliest == null || flip.getBuyTime().isBefore(earliest))
                {
                    earliest = flip.getBuyTime();
                }
                if (latest == null || flip.getSellTime().isAfter(latest))
                {
                    latest = flip.getSellTime();
                }
            }
        }

        if (earliest == null || latest == null)
        {
            return 0;
        }

        long elapsedSeconds = latest.getEpochSecond() - earliest.getEpochSecond();
        if (elapsedSeconds <= 0)
        {
            return 0;
        }

        return (long) ((double) profit / elapsedSeconds * 3600);
    }

    /**
     * Get GP/hr for a specific flip.
     */
    public static long getFlipGpPerHour(FlipItem flip)
    {
        if (!flip.isComplete() || flip.getFlipDurationSeconds() <= 0)
        {
            return 0;
        }
        return (long) ((double) flip.getProfit() / flip.getFlipDurationSeconds() * 3600);
    }

    /**
     * Save flip history using atomic writes (write to temp file, then rename).
     * This prevents data corruption if the process crashes mid-write.
     * The list snapshot and destination are captured on the calling thread (cheap);
     * serialization and file I/O run on the shared executor so a GE-offer event
     * never blocks the client thread on disk. Writes serialize on ioLock.
     */
    private void saveHistory()
    {
        final File dir = dataDir;
        final Gson g = gson;
        final List<FlipItem> snapshot;
        synchronized (completedFlips)
        {
            snapshot = new ArrayList<>(completedFlips);
        }
        ioExecutor.execute(() -> writeHistoryFile(dir, g, snapshot));
    }

    private void writeHistoryFile(File dir, Gson g, List<FlipItem> snapshot)
    {
        synchronized (ioLock)
        {
            try
            {
                dir.mkdirs();
                File historyFile = new File(dir, "flip_history.json");
                File tempFile = new File(dir, "flip_history.json.tmp");

                try (Writer writer = new FileWriter(tempFile))
                {
                    g.toJson(snapshot, writer);
                }

                // Atomic rename — if this fails, the original file is untouched
                if (tempFile.exists())
                {
                    if (historyFile.exists())
                    {
                        historyFile.delete();
                    }
                    if (!tempFile.renameTo(historyFile))
                    {
                        log.warn("Atomic rename failed, falling back to direct write");
                        try (Writer writer = new FileWriter(historyFile))
                        {
                            g.toJson(snapshot, writer);
                        }
                    }
                }
                log.debug("Saved {} flip records to history", snapshot.size());
            }
            catch (IOException e)
            {
                log.warn("Failed to save flip history: {}", e.getMessage());
            }
        }
    }

    private void loadHistory()
    {
        File historyFile = new File(dataDir, "flip_history.json");
        if (historyFile.exists())
        {
            try (Reader reader = new FileReader(historyFile))
            {
                Type listType = new TypeToken<List<FlipItem>>() {}.getType();
                List<FlipItem> loaded = gson.fromJson(reader, listType);
                if (loaded != null)
                {
                    completedFlips.addAll(loaded);
                    log.info("Loaded {} flip records from history", loaded.size());
                }
            }
            catch (IOException e)
            {
                log.warn("Failed to load flip history: {}", e.getMessage());
            }
        }
    }

    /**
     * Export completed flips to a CSV file for advanced trade-log analysis.
     *
     * @return absolute path to the generated CSV file
     * @throws IOException when writing fails
     */
    public String exportTradeLogCsv() throws IOException
    {
        dataDir.mkdirs();
        String fileName = "flip_history_export_" + System.currentTimeMillis() + ".csv";
        File csvFile = new File(dataDir, fileName);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

        try (Writer writer = new BufferedWriter(new FileWriter(csvFile)))
        {
            writer.write("item_id,item_name,quantity,buy_price,sell_price,tax,profit,"
                + "roi_percent,gp_per_hour,buy_time,sell_time,duration_seconds,"
                + "coin_gp,inventory_gp,bank_gp,total_wealth_gp\n");
            synchronized (completedFlips)
            {
                for (FlipItem flip : completedFlips)
                {
                    if (!flip.isComplete() || flip.getSellTime() == null)
                    {
                        continue;
                    }

                    writer.write(String.format(Locale.US,
                        "%d,\"%s\",%d,%d,%d,%d,%d,%.2f,%d,%s,%s,%d,%s,%s,%s,%s\n",
                        flip.getItemId(),
                        flip.getItemName().replace("\"", "\"\""),
                        flip.getQuantity(),
                        flip.getBuyPrice(),
                        flip.getSellPrice(),
                        flip.getTax(),
                        flip.getProfit(),
                        flip.getProfitPercent(),
                        flip.getGpPerHour(),
                        dtf.format(flip.getBuyTime()),
                        dtf.format(flip.getSellTime()),
                        flip.getFlipDurationSeconds(),
                        formatCsvLong(flip.getSellCoinGp()),
                        formatCsvLong(flip.getSellInventoryGp()),
                        formatCsvLong(flip.getSellBankGp()),
                        formatCsvLong(flip.getSellTotalWealthGp())
                    ));
                }
            }
        }

        return csvFile.getAbsolutePath();
    }

    private static String formatCsvLong(Long value)
    {
        return value != null ? Long.toString(value) : "";
    }

    private void appendTradeLogEntry(FlipItem flip, TradeRecord sellTrade, String source)
    {
        if (flip == null || !flip.isComplete())
        {
            return;
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("event", "flip_completed");
            entry.put("source", source);
            entry.put("timestamp", Instant.now().toString());
            entry.put("itemId", flip.getItemId());
            entry.put("itemName", flip.getItemName());
            entry.put("quantity", flip.getQuantity());
            entry.put("buyPrice", flip.getBuyPrice());
            entry.put("sellPrice", flip.getSellPrice());
            entry.put("tax", flip.getTax());
            entry.put("profit", flip.getProfit());
            entry.put("roiPercent", flip.getProfitPercent());
            entry.put("geSlot", flip.getGeSlot());
            entry.put("buyTime", flip.getBuyTime() != null ? flip.getBuyTime().toString() : null);
            entry.put("sellTime", flip.getSellTime() != null ? flip.getSellTime().toString() : null);
            if (flip.getAccountName() != null)
            {
                entry.put("accountName", flip.getAccountName());
            }
            if (flip.getAccountId() != 0)
            {
                entry.put("accountId", flip.getAccountId());
            }
            Long coinGp = null;
            Long inventoryGp = null;
            Long bankGp = null;
            Long totalWealthGp = null;
            if (sellTrade != null)
            {
                coinGp = sellTrade.getCoinGp();
                inventoryGp = sellTrade.getInventoryGp();
                bankGp = sellTrade.getBankGp();
                totalWealthGp = sellTrade.getTotalWealthGp();
            }
            else if (flip.getSellTotalWealthGp() != null)
            {
                coinGp = flip.getSellCoinGp();
                inventoryGp = flip.getSellInventoryGp();
                bankGp = flip.getSellBankGp();
                totalWealthGp = flip.getSellTotalWealthGp();
            }
            if (coinGp != null)
            {
                entry.put("coinGp", coinGp);
            }
            if (inventoryGp != null)
            {
                entry.put("inventoryGp", inventoryGp);
            }
            if (bankGp != null)
            {
                entry.put("bankGp", bankGp);
            }
            if (totalWealthGp != null)
            {
                entry.put("totalWealthGp", totalWealthGp);
            }

        // Serialize on the calling thread (cheap string work); append on the shared
        // executor so the GE-event path never touches disk on the client thread.
        final File dir = dataDir;
        final String line = gson.toJson(entry);
        ioExecutor.execute(() ->
        {
            synchronized (ioLock)
            {
                try
                {
                    dir.mkdirs();
                    File logFile = new File(dir, "trade_log.ndjson");
                    try (Writer writer = new BufferedWriter(new FileWriter(logFile, true)))
                    {
                        writer.write(line);
                        writer.write('\n');
                    }
                }
                catch (IOException e)
                {
                    log.debug("Failed to append trade log entry: {}", e.getMessage());
                }
            }
        });
    }
}
