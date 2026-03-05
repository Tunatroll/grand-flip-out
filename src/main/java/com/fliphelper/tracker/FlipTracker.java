package com.fliphelper.tracker;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.model.FlipItem;
import com.fliphelper.model.FlipState;
import com.fliphelper.model.TradeRecord;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import java.io.*;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks active flips and maintains flip history.
     * Automatically pairs buy/sell transactions to detect completed flips.
     */
@Slf4j
    public class FlipTracker {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final GrandFlipOutConfig config;
            private final File dataDir;

    /** Optional reference to PriceHistoryCollector for buy-timestamp tracking. */
    private PriceHistoryCollector historyCollector;

    @Getter
            private final Map<Integer, FlipItem> activeFlips = new ConcurrentHashMap<>();

    @Getter
            private final List<FlipItem> completedFlips = Collections.synchronizedList(new ArrayList<>());

    // Track buy orders waiting to be matched with sells
    private final Map<Integer, Deque<TradeRecord>> pendingBuys = new ConcurrentHashMap<>();

    @Getter
            private long sessionProfit = 0;

    @Getter
            private int sessionFlipCount = 0;

    public FlipTracker(GrandFlipOutConfig config, File dataDir) {
                this.config = config;
                this.dataDir = dataDir;
                if (config.persistHistory()) {
                                loadHistory();
                }
    }

    /**
     * Wire in the PriceHistoryCollector so buy timestamps can be tracked.
             * Called from GrandFlipOutPlugin after initialisation.
             */
    public void setHistoryCollector(PriceHistoryCollector historyCollector) {
                this.historyCollector = historyCollector;
    }

    /**
     * Record a GE transaction (buy or sell).
             * Automatically pairs buys with sells to create flip records.
             */
    public void recordTransaction(TradeRecord trade) {
                if (trade.isBought()) {
                                // Record buy - add to pending buys
                    pendingBuys.computeIfAbsent(trade.getItemId(), k -> new ArrayDeque<>()).addLast(trade);

                    // Notify history collector for GE limit tracking
                    if (historyCollector != null) {
                                        historyCollector.recordBuy(trade.getItemId());
                    }

                    FlipItem flip = FlipItem.builder()
                                        .itemId(trade.getItemId())
                                        .itemName(trade.getItemName())
                                        .quantity(trade.getQuantity())
                                        .buyPrice(trade.getPrice())
                                        .buyTime(trade.getTimestamp())
                                        .state(FlipState.BOUGHT)
                                        .geSlot(trade.getGeSlot())
                                        .build();
                                activeFlips.put(trade.getItemId(), flip);

                    log.info("Recorded buy: {}x {} @ {}gp", trade.getQuantity(), trade.getItemName(), trade.getPrice());

                } else {
                                // Record sell - try to match with a pending buy
                    Deque<TradeRecord> buys = pendingBuys.get(trade.getItemId());
                                if (buys != null && !buys.isEmpty()) {
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
                                                    activeFlips.remove(trade.getItemId());

                                    long profit = completedFlip.getProfit();
                                                    sessionProfit += profit;
                                                    sessionFlipCount++;

                                    log.info("Completed flip: {}x {} | Buy: {}gp Sell: {}gp | Profit: {}gp",
                                                            completedFlip.getQuantity(), completedFlip.getItemName(),
                                                        completedFlip.getBuyPrice(), completedFlip.getSellPrice(), profit);

                                    // Trim history
                                    while (completedFlips.size() > config.maxHistoryEntries()) {
                                                            completedFlips.remove(completedFlips.size() - 1);
                                    }

                                    if (config.persistHistory()) {
                                                            saveHistory();
                                    }
                                } else {
                                                    log.debug("Sell recorded without matching buy for {}", trade.getItemName());
                                }
                }
    }

    /** Manually add a flip (for retroactive tracking). */
    public void addManualFlip(FlipItem flip) {
                if (flip.isComplete()) {
                                completedFlips.add(0, flip);
                                sessionProfit += flip.getProfit();
                                sessionFlipCount++;
                                if (config.persistHistory()) saveHistory();
                } else {
                                activeFlips.put(flip.getItemId(), flip);
                }
    }

    /** Cancel an active flip. */
    public void cancelFlip(int itemId) {
                FlipItem flip = activeFlips.remove(itemId);
                if (flip != null) {
                                flip.setState(FlipState.CANCELLED);
                                completedFlips.add(0, flip);
                                pendingBuys.remove(itemId);
                }
    }

    /** Get total profit over a time range. */
    public long getProfitSince(Instant since) {
                return completedFlips.stream()
                                .filter(f -> f.getSellTime() != null && f.getSellTime().isAfter(since))
                                .mapToLong(FlipItem::getProfit)
                                .sum();
    }

    /** Get the most profitable items historically. */
    public Map<String, Long> getMostProfitableItems(int limit) {
                return completedFlips.stream()
                                .filter(FlipItem::isComplete)
                                .collect(Collectors.groupingBy(FlipItem::getItemName, Collectors.summingLong(FlipItem::getProfit)))
                                .entrySet().stream()
                                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                                .limit(limit)
                                .collect(Collectors.toMap(
                                                    Map.Entry::getKey, Map.Entry::getValue,
                                                    (a, b) -> a, LinkedHashMap::new));
    }

    /** Get flip frequency per item. */
    public Map<String, Long> getFlipFrequency() {
                return completedFlips.stream()
                                .filter(FlipItem::isComplete)
                                .collect(Collectors.groupingBy(FlipItem::getItemName, Collectors.counting()));
    }

    /** Get average profit per flip. */
    public double getAverageProfitPerFlip() {
                if (sessionFlipCount == 0) return 0;
                return (double) sessionProfit / sessionFlipCount;
    }

    /** Reset session statistics. */
    public void resetSession() {
                sessionProfit = 0;
                sessionFlipCount = 0;
                activeFlips.clear();
                pendingBuys.clear();
    }

    private void saveHistory() {
                try {
                                File historyFile = new File(dataDir, "flip_history.json");
                                dataDir.mkdirs();
                                try (Writer writer = new FileWriter(historyFile)) {
                                                    GSON.toJson(completedFlips, writer);
                                }
                                log.debug("Saved {} flip records to history", completedFlips.size());
                } catch (IOException e) {
                                log.warn("Failed to save flip history: {}", e.getMessage());
                }
    }

    private void loadHistory() {
                File historyFile = new File(dataDir, "flip_history.json");
                if (historyFile.exists()) {
                                try (Reader reader = new FileReader(historyFile)) {
                                                    Type listType = new TypeToken<List<FlipItem>>() {}.getType();
                                                    List<FlipItem> loaded = GSON.fromJson(reader, listType);
                                                    if (loaded != null) {
                                                                            completedFlips.addAll(loaded);
                                                                            log.info("Loaded {} flip records from history", loaded.size());
                                                    }
                                } catch (IOException e) {
                                                    log.warn("Failed to load flip history: {}", e.getMessage());
                                }
                }
    }
    }
