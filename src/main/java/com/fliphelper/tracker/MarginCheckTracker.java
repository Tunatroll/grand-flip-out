package com.fliphelper.tracker;

import com.fliphelper.model.FlipItem;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class MarginCheckTracker {

    private static final String MARGIN_CHECKS_FILE = "margin_checks.json";
    private final Map<Integer, MarginCheck> activeChecks;
    private final List<MarginCheck> checkHistory;
    private final Gson gson;
    private final File dataFile;

    public MarginCheckTracker(String dataDirectory) {
        this.activeChecks = new ConcurrentHashMap<>();
        this.checkHistory = Collections.synchronizedList(new ArrayList<>());
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataFile = new File(dataDirectory, MARGIN_CHECKS_FILE);
        loadCheckHistory();
    }

    /**
     * Start a margin check for an item
     */
    public void startCheck(int itemId, String name, int slot) {
        MarginCheck check = MarginCheck.builder()
                .itemId(itemId)
                .itemName(name)
                .geSlot(slot)
                .startTime(Instant.now())
                .build();

        activeChecks.put(slot, check);
        log.info("Started margin check for {} (ID: {}) in slot {}", name, itemId, slot);
    }

    /**
     * Record the buy-1 price for a margin check
     */
    public void recordCheckBuy(int slot, long price) {
        MarginCheck check = activeChecks.get(slot);
        if (check != null) {
            check.buyCheckPrice = price;
            log.debug("Recorded buy-1 check price for slot {}: {}", slot, price);
        } else {
            log.warn("No active check for slot {}", slot);
        }
    }

    /**
     * Record the sell-1 price and calculate actual margin
     */
    public void recordCheckSell(int slot, long price) {
        MarginCheck check = activeChecks.get(slot);
        if (check != null) {
            check.sellCheckPrice = price;
            if (check.buyCheckPrice > 0) {
                check.actualMargin = price - check.buyCheckPrice;
                check.endTime = Instant.now();
                checkHistory.add(check);
                activeChecks.remove(slot);
                log.info("Completed margin check for {} - actual margin: {}",
                        check.itemName, check.actualMargin);
                saveCheckHistory();
            }
        } else {
            log.warn("No active check for slot {}", slot);
        }
    }

    /**
     * Get the verified actual margin for an item
     */
    public long getActualMargin(int itemId) {
        return checkHistory.stream()
                .filter(check -> check.itemId == itemId)
                .mapToLong(check -> check.actualMargin)
                .max()
                .orElse(0);
    }

    /**
     * Get all completed margin checks
     */
    public List<MarginCheck> getCheckHistory() {
        return new ArrayList<>(checkHistory);
    }

    /**
     * Get active checks currently in progress
     */
    public Map<Integer, MarginCheck> getActiveChecks() {
        return new ConcurrentHashMap<>(activeChecks);
    }

    /**
     * Clear old margin check history
     */
    public void clearOldChecks(long maxAgeMillis) {
        long cutoffTime = System.currentTimeMillis() - maxAgeMillis;
        checkHistory.removeIf(check -> check.startTime.toEpochMilli() < cutoffTime);
        saveCheckHistory();
        log.info("Cleared margin checks older than {} ms", maxAgeMillis);
    }

    private void saveCheckHistory() {
        try {
            if (!dataFile.getParentFile().exists()) {
                dataFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(dataFile)) {
                gson.toJson(checkHistory, writer);
            }
            log.debug("Saved margin check history to {}", dataFile.getPath());
        } catch (IOException e) {
            log.error("Failed to save margin checks", e);
        }
    }

    private void loadCheckHistory() {
        if (dataFile.exists()) {
            try (FileReader reader = new FileReader(dataFile)) {
                MarginCheck[] checks = gson.fromJson(reader, MarginCheck[].class);
                if (checks != null) {
                    checkHistory.addAll(Arrays.asList(checks));
                    log.info("Loaded {} margin checks from history", checks.length);
                }
            } catch (IOException e) {
                log.warn("Failed to load margin check history", e);
            }
        }
    }

    /**
     * Inner class representing a single margin check
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class MarginCheck {
        private int itemId;
        private String itemName;
        private long buyCheckPrice;
        private long sellCheckPrice;
        private long actualMargin;
        private Instant startTime;
        private Instant endTime;
        private int geSlot;

        public MarginCheck() {
            this.startTime = Instant.now();
        }
    }
}
