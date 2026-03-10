package com.fliphelper.tracker;

import com.fliphelper.AwfullyPureConfig;
import com.fliphelper.debug.DebugManager;
import com.fliphelper.model.FlipItem;
import com.fliphelper.model.FlipState;
import com.fliphelper.model.TradeRecord;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


@Slf4j
public class FlipTracker
{
    public interface FlipCompleteListener
    {
        void onFlipComplete(FlipItem flip);
    }

    private FlipCompleteListener flipCompleteListener;

    @Setter
    private DebugManager debugManager;

    public void setFlipCompleteListener(FlipCompleteListener listener)
    {
        this.flipCompleteListener = listener;
    }

    private final AwfullyPureConfig config;
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

    @Getter
    private Instant sessionStartTime = Instant.now();

    private final AtomicInteger transactionsSinceLastSave = new AtomicInteger(0);
    private static final int AUTOSAVE_INTERVAL = 3; // Save every 3 transactions

    @Getter
    private final AtomicLong bestFlipProfit = new AtomicLong(0);

    @Getter
    private final AtomicLong worstFlipProfit = new AtomicLong(Long.MAX_VALUE);

    @Getter
    private final AtomicInteger winStreak = new AtomicInteger(0);

    private volatile boolean streakPositive = true;

    @Getter
    private final Map<Integer, ItemStats> itemStatsMap = new ConcurrentHashMap<>();

    public FlipTracker(AwfullyPureConfig config, File dataDir, Gson gson)
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
                log.info("Recorded buy: {}x {} @ {}gp (slot {})", trade.getQuantity(), trade.getItemName(), trade.getPrice(), trade.getGeSlot());

                // Autosave to prevent data loss on unexpected shutdown
                if (config.persistHistory() && transactionsSinceLastSave.incrementAndGet() >= AUTOSAVE_INTERVAL)
                {
                    saveHistory();
                    transactionsSinceLastSave.set(0);
                }
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

                    // Track best/worst flip
                    bestFlipProfit.accumulateAndGet(profit, Math::max);
                    if (worstFlipProfit.get() == Long.MAX_VALUE || profit < worstFlipProfit.get())
                    {
                        worstFlipProfit.set(profit);
                    }

                    // Track win/loss streak
                    if (profit >= 0)
                    {
                        if (streakPositive) winStreak.incrementAndGet();
                        else { winStreak.set(1); streakPositive = true; }
                    }
                    else
                    {
                        if (!streakPositive) winStreak.incrementAndGet();
                        else { winStreak.set(1); streakPositive = false; }
                    }

                    // Per-item stats
                    itemStatsMap.compute(trade.getItemId(), (k, existing) -> {
                        ItemStats stats = existing != null ? existing : new ItemStats(trade.getItemName());
                        stats.addFlip(profit, completedFlip.getQuantity(),
                            completedFlip.getFlipDurationSeconds());
                        return stats;
                    });

                    log.info("Completed flip: {}x {} | Buy: {}gp Sell: {}gp | Profit: {}gp",
                        completedFlip.getQuantity(), completedFlip.getItemName(),
                        completedFlip.getBuyPrice(), completedFlip.getSellPrice(), profit);

                    // Record flip completion in debug manager
                    if (debugManager != null)
                    {
                        debugManager.recordEvent("FLIP_COMPLETED",
                            String.format("%dx | Buy: %dgp Sell: %dgp | Profit: %dgp",
                                completedFlip.getQuantity(),
                                completedFlip.getBuyPrice(), completedFlip.getSellPrice(), profit),
                            completedFlip.getItemName());
                    }

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
        }
        else
        {
            activeFlips.put(flip.getItemId(), flip);
        }
    }

    
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

    
    public long getProfitSince(Instant since)
    {
        return completedFlips.stream()
            .filter(f -> f.getSellTime() != null && f.getSellTime().isAfter(since))
            .mapToLong(FlipItem::getProfit)
            .sum();
    }

    
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

    
    public Map<String, Long> getFlipFrequency()
    {
        return completedFlips.stream()
            .filter(FlipItem::isComplete)
            .collect(Collectors.groupingBy(FlipItem::getItemName, Collectors.counting()));
    }

    
    public double getAverageProfitPerFlip()
    {
        int flipCount = sessionFlipCount.get();
        if (flipCount == 0)
        {
            return 0;
        }
        return (double) sessionProfit.get() / flipCount;
    }

    
    public void resetSession()
    {
        sessionProfit.set(0);
        sessionFlipCount.set(0);
        sessionStartTime = Instant.now();
        bestFlipProfit.set(0);
        worstFlipProfit.set(Long.MAX_VALUE);
        winStreak.set(0);
        streakPositive = true;
        itemStatsMap.clear();
        activeFlips.clear();
        pendingBuys.clear();
    }

    // ==================== ADVANCED ANALYTICS ====================

    
    public double getGpPerHour()
    {
        long elapsedMs = Instant.now().toEpochMilli() - sessionStartTime.toEpochMilli();
        if (elapsedMs <= 0) return 0;
        double hours = elapsedMs / 3_600_000.0;
        return sessionProfit.get() / Math.max(hours, 0.01);
    }

    
    public String getSessionDuration()
    {
        long seconds = Instant.now().getEpochSecond() - sessionStartTime.getEpochSecond();
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }

    
    public double getWinRate()
    {
        int total = sessionFlipCount.get();
        if (total == 0) return 0;
        long wins = completedFlips.stream()
            .filter(f -> f.isComplete() && f.getProfit() >= 0)
            .limit(total)
            .count();
        return (double) wins / total * 100.0;
    }

    
    public List<ItemStats> getTopItems(int n)
    {
        return itemStatsMap.values().stream()
            .sorted(Comparator.comparingLong(ItemStats::getTotalProfit).reversed())
            .limit(n)
            .collect(Collectors.toList());
    }

    
    @Data
    public static class ItemStats
    {
        private final String itemName;
        private long totalProfit = 0;
        private int flipCount = 0;
        private int totalQuantity = 0;
        private long avgFlipDuration = 0;
        private long bestProfit = 0;
        private long worstProfit = Long.MAX_VALUE;

        public ItemStats(String itemName)
        {
            this.itemName = itemName;
        }

        public void addFlip(long profit, int quantity, long durationSeconds)
        {
            totalProfit += profit;
            flipCount++;
            totalQuantity += quantity;
            bestProfit = Math.max(bestProfit, profit);
            if (worstProfit == Long.MAX_VALUE || profit < worstProfit) worstProfit = profit;
            // Rolling average duration
            avgFlipDuration = (avgFlipDuration * (flipCount - 1) + durationSeconds) / flipCount;
        }

        public long getAvgProfitPerFlip()
        {
            return flipCount > 0 ? totalProfit / flipCount : 0;
        }
    }

    private void saveHistory()
    {
        try
        {
            File historyFile = new File(dataDir, "flip_history.json");
            dataDir.mkdirs();
            try (Writer writer = new FileWriter(historyFile))
            {
                gson.toJson(completedFlips, writer);
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
}
