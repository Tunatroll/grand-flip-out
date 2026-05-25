package com.fliphelper.tracker;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.model.FlipItem;
import com.fliphelper.model.FlipState;
import com.fliphelper.model.TradeRecord;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    private final File dataDir;
    private final Gson gson;

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

    public FlipTracker(GrandFlipOutConfig config, File dataDir, Gson gson)
    {
        this.config = config;
        this.dataDir = dataDir;
        this.gson = gson;

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

                // Create or update active flip
                FlipItem flip = FlipItem.builder()
                    .itemId(trade.getItemId())
                    .itemName(trade.getItemName())
                    .quantity(trade.getQuantity())
                    .buyPrice(trade.getPrice())
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
                // Record sell - try to match with a pending buy
                Deque<TradeRecord> buys = pendingBuys.get(trade.getItemId());
                if (buys != null && !buys.isEmpty())
                {
                    TradeRecord matchedBuy = buys.pollFirst();

                    FlipItem completedFlip = FlipItem.builder()
                        .itemId(trade.getItemId())
                        .itemName(trade.getItemName())
                        .quantity(Math.min(matchedBuy.getQuantity(), trade.getQuantity()))
                        .buyPrice(matchedBuy.getPrice())
                        .sellPrice(trade.getPrice())
                        .buyTime(matchedBuy.getTimestamp())
                        .sellTime(trade.getTimestamp())
                        .state(FlipState.COMPLETE)
                        .geSlot(trade.getGeSlot())
                        .build();

                    completedFlips.add(0, completedFlip);
                    activeFlips.remove(trade.getGeSlot());

                    long profit = completedFlip.getProfit();
                    sessionProfit.addAndGet(profit);
                    sessionFlipCount.incrementAndGet();

                    log.info("Completed flip: {}x {} | Buy: {}gp Sell: {}gp | Profit: {}gp",
                        completedFlip.getQuantity(), completedFlip.getItemName(),
                        completedFlip.getBuyPrice(), completedFlip.getSellPrice(), profit);

                    // Record flip completion in debug manager
                    // Notify listener outside the critical section to avoid deadlocks
                    FlipItem flipToNotify = completedFlip;

                    // Trim history
                    while (completedFlips.size() > config.maxHistoryEntries())
                    {
                        completedFlips.remove(completedFlips.size() - 1);
                    }

                    if (config.persistHistory())
                    {
                        saveHistory();
                    }

                    appendTradeLogEntry(completedFlip, "ge_event");

                    // Notify listener (profile logging, Discord alerts, etc.)
                    if (flipCompleteListener != null)
                    {
                        try { flipCompleteListener.onFlipComplete(flipToNotify); }
                        catch (Exception e) { log.debug("Flip listener error: {}", e.getMessage()); }
                    }
                }
                else
                {
                    // Sell without a tracked buy (could be a normal sale, not a flip)
                    log.debug("Sell recorded without matching buy for {}", trade.getItemName());
                }
            }
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
            appendTradeLogEntry(flip, "manual_add");
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
     */
    private void saveHistory()
    {
        try
        {
            dataDir.mkdirs();
            File historyFile = new File(dataDir, "flip_history.json");
            File tempFile = new File(dataDir, "flip_history.json.tmp");

            try (Writer writer = new FileWriter(tempFile))
            {
                gson.toJson(completedFlips, writer);
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
                        gson.toJson(completedFlips, writer);
                    }
                }
            }
            log.debug("Saved {} flip records to history", completedFlips.size());
        }
        catch (IOException e)
        {
            log.warn("Failed to save flip history: {}", e.getMessage());
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
                + "roi_percent,gp_per_hour,buy_time,sell_time,duration_seconds\n");
            synchronized (completedFlips)
            {
                for (FlipItem flip : completedFlips)
                {
                    if (!flip.isComplete() || flip.getSellTime() == null)
                    {
                        continue;
                    }

                    writer.write(String.format(Locale.US,
                        "%d,\"%s\",%d,%d,%d,%d,%d,%.2f,%d,%s,%s,%d\n",
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
                        flip.getFlipDurationSeconds()
                    ));
                }
            }
        }

        return csvFile.getAbsolutePath();
    }

    private void appendTradeLogEntry(FlipItem flip, String source)
    {
        if (flip == null || !flip.isComplete())
        {
            return;
        }

        try
        {
            dataDir.mkdirs();
            File logFile = new File(dataDir, "trade_log.ndjson");
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

            try (Writer writer = new BufferedWriter(new FileWriter(logFile, true)))
            {
                writer.write(gson.toJson(entry));
                writer.write('\n');
            }
        }
        catch (IOException e)
        {
            log.debug("Failed to append trade log entry: {}", e.getMessage());
        }
    }
}
