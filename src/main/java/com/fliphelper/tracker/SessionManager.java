/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.tracker;

import com.fliphelper.model.FlipItem;
import com.fliphelper.util.GeTax;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SessionManager {

    private static final String SESSIONS_FILE = "flip_sessions.json";
    private FlipSession activeSession;
    private final List<FlipSession> sessionHistory;
    private final Gson gson;
    private File dataFile;

    public SessionManager(String dataDirectory, Gson gson) {
        this.sessionHistory = Collections.synchronizedList(new ArrayList<>());
        this.gson = gson;
        this.dataFile = new File(dataDirectory, SESSIONS_FILE);
        loadSessionHistory();
    }

    public void switchDataDir(String newDataDirectory) {
        saveSessionHistory();
        this.dataFile = new File(newDataDirectory, SESSIONS_FILE);
        this.sessionHistory.clear();
        this.activeSession = null;
        loadSessionHistory();
    }

    /**
     * Start a new flipping session with a goal
     */
    public FlipSession startSession(String name, long goalAmount) {
        if (activeSession != null && activeSession.isActive) {
            log.warn("Active session already exists, ending it first");
            endSession();
        }

        activeSession = FlipSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .name(name)
                .startTime(Instant.now())
                .goalAmount(goalAmount)
                .isActive(true)
                .itemsTracked(new ConcurrentHashMap<>())
                .flipCount(0)
                .actualProfit(0)
                .startTotalWealthGp(0L)
                .endTotalWealthGp(0L)
                .build();

        log.info("Started new session: {} with goal: {}", name, goalAmount);
        return activeSession;
    }

    /**
     * End the active session
     */
    public FlipSession endSession() {
        if (activeSession != null) {
            activeSession.endTime = Instant.now();
            activeSession.isActive = false;
            sessionHistory.add(activeSession);
            log.info("Ended session: {} with profit: {}", activeSession.name, activeSession.actualProfit);
            saveSessionHistory();
            FlipSession ended = activeSession;
            activeSession = null;
            return ended;
        }
        return null;
    }

    /**
     * Record a completed flip to the active session
     */
    public void recordFlipToSession(FlipItem flip) {
        if (activeSession == null) {
            log.warn("No active session to record flip");
            return;
        }

        if (flip != null) {
            activeSession.itemsTracked.put(flip.getItemId(), flip);
            activeSession.flipCount++;
            // GeTax: 5M cap per ITEM (the old inline formula capped the whole
            // stack's tax at 5M) + exemption handling
            long tax = GeTax.tax(flip.getItemId(), flip.getSellPrice(), flip.getQuantity());
            long profit = (flip.getSellPrice() - flip.getBuyPrice()) * (long) flip.getQuantity() - tax;
            activeSession.actualProfit += profit;
            log.debug("Recorded flip in session - profit: {}, total: {}", profit, activeSession.actualProfit);
        }
    }

    /**
     * Record wealth at session start (read-only client estimate).
     */
    public void setStartWealth(long totalWealthGp) {
        if (activeSession != null) {
            activeSession.startTotalWealthGp = totalWealthGp;
            activeSession.endTotalWealthGp = totalWealthGp;
        }
    }

    /**
     * Update latest wealth snapshot on the active session (after each completed flip).
     */
    public void updateSessionWealth(long totalWealthGp) {
        if (activeSession != null) {
            activeSession.endTotalWealthGp = totalWealthGp;
        }
    }

    /**
     * Get the currently active session
     */
    public FlipSession getActiveSession() {
        return activeSession;
    }

    /**
     * Get all past sessions
     */
    public List<FlipSession> getSessionHistory() {
        return new ArrayList<>(sessionHistory);
    }

    /**
     * Get the completion percentage of the active session goal
     */
    public double getProgressPercent() {
        if (activeSession == null) {
            return 0;
        }
        if (activeSession.goalAmount <= 0) {
            return 0;
        }
        return (double) activeSession.actualProfit / activeSession.goalAmount * 100.0;
    }

    /**
     * Estimate time remaining to reach the goal based on current GP/hr
     */
    public long getTimeToGoalMillis() {
        if (activeSession == null || activeSession.goalAmount <= 0) {
            return -1;
        }

        long elapsedMillis = System.currentTimeMillis() - activeSession.startTime.toEpochMilli();
        if (elapsedMillis <= 0) {
            return -1;
        }

        double gpPerHour = (double) activeSession.actualProfit / (elapsedMillis / 3600000.0);
        if (gpPerHour <= 0) {
            return -1;
        }

        long remainingProfit = activeSession.goalAmount - activeSession.actualProfit;
        if (remainingProfit <= 0) {
            return 0;
        }

        return (long) ((remainingProfit / gpPerHour) * 3600000.0);
    }

    /**
     * Get current GP/hr rate for active session
     */
    public double getCurrentGpHourRate() {
        if (activeSession == null) {
            return 0;
        }

        long elapsedMillis = System.currentTimeMillis() - activeSession.startTime.toEpochMilli();
        if (elapsedMillis <= 0) {
            return 0;
        }

        return activeSession.actualProfit / (elapsedMillis / 3600000.0);
    }

    /**
     * Get session statistics summary
     */
    public SessionStats getSessionStats() {
        if (activeSession == null) {
            return null;
        }

        long elapsedMillis = System.currentTimeMillis() - activeSession.startTime.toEpochMilli();
        double hourElapsed = Math.max(1.0, elapsedMillis / 3600000.0);
        double gpPerHour = activeSession.actualProfit / hourElapsed;

        return SessionStats.builder()
                .sessionName(activeSession.name)
                .goalAmount(activeSession.goalAmount)
                .actualProfit(activeSession.actualProfit)
                .progressPercent(getProgressPercent())
                .flipCount(activeSession.flipCount)
                .gPPerHour(gpPerHour)
                .elapsedHours(hourElapsed)
                .timeToGoalMillis(getTimeToGoalMillis())
                .build();
    }

    private void saveSessionHistory() {
        try {
            if (!dataFile.getParentFile().exists()) {
                dataFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(dataFile)) {
                gson.toJson(sessionHistory, writer);
            }
            log.debug("Saved session history to {}", dataFile.getPath());
        } catch (IOException e) {
            log.error("Failed to save session history", e);
        }
    }

    private void loadSessionHistory() {
        if (dataFile.exists()) {
            try (FileReader reader = new FileReader(dataFile)) {
                FlipSession[] sessions = gson.fromJson(reader, FlipSession[].class);
                if (sessions != null) {
                    sessionHistory.addAll(Arrays.asList(sessions));
                    log.info("Loaded {} sessions from history", sessions.length);
                }
            } catch (IOException e) {
                log.warn("Failed to load session history", e);
            }
        }
    }

    /**
     * Inner class representing a flipping session
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class FlipSession {
        private String sessionId;
        private String name;
        private Instant startTime;
        private Instant endTime;
        private long goalAmount;
        private long actualProfit;
        private int flipCount;
        private Map<Integer, FlipItem> itemsTracked;
        private boolean isActive;
        private long startTotalWealthGp;
        private long endTotalWealthGp;

        public FlipSession() {
            this.startTime = Instant.now();
            this.itemsTracked = new ConcurrentHashMap<>();
        }
    }

    /**
     * Session statistics summary
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class SessionStats {
        private String sessionName;
        private long goalAmount;
        private long actualProfit;
        private double progressPercent;
        private int flipCount;
        private double gPPerHour;
        private double elapsedHours;
        private long timeToGoalMillis;

        public String getFormattedProgress() {
            return String.format("%.1f%% (%d / %d GP)", progressPercent, actualProfit, goalAmount);
        }

        public String getFormattedTimeToGoal() {
            if (timeToGoalMillis < 0) {
                return "N/A";
            }
            if (timeToGoalMillis == 0) {
                return "Complete!";
            }
            long hours = timeToGoalMillis / 3600000;
            long minutes = (timeToGoalMillis % 3600000) / 60000;
            return String.format("%dh %dm", hours, minutes);
        }
    }
}
