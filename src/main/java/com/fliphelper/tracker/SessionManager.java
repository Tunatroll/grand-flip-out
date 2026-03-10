package com.fliphelper.tracker;

import com.fliphelper.model.FlipItem;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SessionManager {

    private static final String SESSIONS_FILE = "flip_sessions.json";
    private FlipSession activeSession;
    private final List<FlipSession> sessionHistory;
    private final Gson gson;
    private final File dataFile;

    public SessionManager(String dataDirectory, Gson gson) {
        this.sessionHistory = Collections.synchronizedList(new ArrayList<>());
        this.gson = gson;
        this.dataFile = new File(dataDirectory, SESSIONS_FILE);
        loadSessionHistory();
    }

    
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
                .build();

        log.info("Started new session: {} with goal: {}", name, goalAmount);
        return activeSession;
    }

    
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

    
    public void recordFlipToSession(FlipItem flip) {
        if (activeSession == null) {
            log.warn("No active session to record flip");
            return;
        }

        if (flip != null) {
            activeSession.itemsTracked.put(flip.getItemId(), flip);
            activeSession.flipCount++;
            // Use FlipItem.getProfit() which includes GE tax calculation (2% capped at 5M per item)
            long profit = flip.getProfit();
            activeSession.actualProfit += profit;
            log.debug("Recorded flip in session - profit: {}, total: {}", profit, activeSession.actualProfit);
        }
    }

    
    public FlipSession getActiveSession() {
        return activeSession;
    }

    
    public List<FlipSession> getSessionHistory() {
        return new ArrayList<>(sessionHistory);
    }

    
    public double getProgressPercent() {
        if (activeSession == null) {
            return 0;
        }
        if (activeSession.goalAmount <= 0) {
            return 0;
        }
        return (double) activeSession.actualProfit / activeSession.goalAmount * 100.0;
    }

    
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

        public FlipSession() {
            this.startTime = Instant.now();
            this.itemsTracked = new ConcurrentHashMap<>();
        }
    }

    
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
