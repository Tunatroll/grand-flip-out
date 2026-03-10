package com.fliphelper.debug;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Comprehensive debugging system for Awfully Pure.
 *
 * Provides:
 * - Ring buffer logging (last 500 entries)
 * - Performance metrics tracking
 * - API call monitoring
 * - Memory usage tracking
 * - Event logging
 * - Debug report export
 */
@Slf4j
public class DebugManager
{
    private static final int LOG_BUFFER_SIZE = 500;
    private static final int MAX_SNAPSHOTS_PER_OP = 1000;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault());

    // Ring buffer for log entries
    private final List<LogEntry> logBuffer = new CopyOnWriteArrayList<>();

    // Performance metrics: operation name -> list of execution times (milliseconds)
    private final Map<String, List<Long>> performanceMetrics = new ConcurrentHashMap<>();

    // API call tracking: endpoint -> APICallStats
    private final Map<String, APICallStats> apiCallStats = new ConcurrentHashMap<>();

    // Event log
    private final List<EventLogEntry> eventLog = new CopyOnWriteArrayList<>();

    // Memory snapshots
    private final List<MemorySnapshot> memorySnapshots = new CopyOnWriteArrayList<>();

    /**
     * Log a message with timestamp and level.
     */
    public synchronized void log(@Nonnull LogLevel level, @Nonnull String sourceClass, @Nonnull String message)
    {
        LogEntry entry = LogEntry.builder()
            .timestamp(Instant.now())
            .level(level)
            .sourceClass(sourceClass)
            .message(message)
            .build();

        logBuffer.add(entry);

        // Maintain ring buffer size
        if (logBuffer.size() > LOG_BUFFER_SIZE)
        {
            logBuffer.remove(0);
        }
    }

    /**
     * Log a debug message.
     */
    public void debug(@Nonnull String sourceClass, @Nonnull String message)
    {
        log(LogLevel.DEBUG, sourceClass, message);
    }

    /**
     * Log an info message.
     */
    public void info(@Nonnull String sourceClass, @Nonnull String message)
    {
        log(LogLevel.INFO, sourceClass, message);
    }

    /**
     * Log a warning message.
     */
    public void warn(@Nonnull String sourceClass, @Nonnull String message)
    {
        log(LogLevel.WARN, sourceClass, message);
    }

    /**
     * Log an error message.
     */
    public void error(@Nonnull String sourceClass, @Nonnull String message)
    {
        log(LogLevel.ERROR, sourceClass, message);
    }

    /**
     * Record execution time for an operation.
     *
     * @param operationName The name of the operation
     * @param durationMs The duration in milliseconds
     */
    public void recordOperationTime(@Nonnull String operationName, long durationMs)
    {
        performanceMetrics.computeIfAbsent(operationName, k -> new CopyOnWriteArrayList<>())
            .add(durationMs);

        // Trim old entries to prevent unbounded growth
        List<Long> times = performanceMetrics.get(operationName);
        if (times != null && times.size() > MAX_SNAPSHOTS_PER_OP)
        {
            times.remove(0);
        }
    }

    /**
     * Get statistics for a specific operation.
     *
     * @param operationName The operation to query
     * @return Statistics or null if no data
     */
    @Nullable
    public OperationStats getOperationStats(@Nonnull String operationName)
    {
        List<Long> times = performanceMetrics.get(operationName);
        if (times == null || times.isEmpty())
        {
            return null;
        }

        Collections.sort(times);
        long min = times.get(0);
        long max = times.get(times.size() - 1);
        double avg = times.stream().mapToLong(Long::longValue).average().orElse(0);
        long median = times.get(times.size() / 2);
        long p95 = times.get((int) (times.size() * 0.95));

        return OperationStats.builder()
            .operationName(operationName)
            .callCount(times.size())
            .minMs(min)
            .maxMs(max)
            .avgMs(avg)
            .medianMs(median)
            .p95Ms(p95)
            .build();
    }

    /**
     * Record an API call.
     */
    public void recordAPICall(@Nonnull String endpoint, long durationMs, boolean success)
    {
        APICallStats stats = apiCallStats.computeIfAbsent(endpoint, k -> new APICallStats(endpoint));
        stats.recordCall(durationMs, success);
    }

    /**
     * Get all API call statistics.
     */
    @Nonnull
    public Map<String, APICallStats> getAPICallStats()
    {
        return new HashMap<>(apiCallStats);
    }

    /**
     * Record memory usage snapshot.
     */
    public void recordMemorySnapshot(long heapUsed, long heapMax, int itemCacheSize, int priceHistoryEntries)
    {
        MemorySnapshot snapshot = MemorySnapshot.builder()
            .timestamp(Instant.now())
            .heapUsedMb(heapUsed / (1024 * 1024))
            .heapMaxMb(heapMax / (1024 * 1024))
            .itemCacheSize(itemCacheSize)
            .priceHistoryEntries(priceHistoryEntries)
            .build();

        memorySnapshots.add(snapshot);

        // Keep last 100 snapshots
        if (memorySnapshots.size() > 100)
        {
            memorySnapshots.remove(0);
        }
    }

    /**
     * Record an event (flip completion, alert trigger, etc).
     */
    public void recordEvent(@Nonnull String eventType, @Nonnull String description, @Nullable String itemName)
    {
        EventLogEntry event = EventLogEntry.builder()
            .timestamp(Instant.now())
            .eventType(eventType)
            .description(description)
            .itemName(itemName)
            .build();

        eventLog.add(event);

        // Keep last 500 events
        if (eventLog.size() > 500)
        {
            eventLog.remove(0);
        }
    }

    /**
     * Export a comprehensive debug report for bug reporting.
     *
     * @return A formatted debug report as a string
     */
    @Nonnull
    public String exportDebugReport()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("=== GRAND FLIP OUT DEBUG REPORT ===\n");
        sb.append("Generated: ").append(TIME_FORMATTER.format(Instant.now())).append("\n\n");

        // ===== LOG BUFFER =====
        sb.append("=== RECENT LOGS (Last ").append(logBuffer.size()).append(" entries) ===\n");
        for (LogEntry entry : logBuffer)
        {
            String timestamp = TIME_FORMATTER.format(entry.getTimestamp());
            sb.append(String.format("[%s] %s - %s: %s\n",
                timestamp,
                entry.getLevel(),
                entry.getSourceClass(),
                entry.getMessage()));
        }
        sb.append("\n");

        // ===== PERFORMANCE METRICS =====
        sb.append("=== PERFORMANCE METRICS ===\n");
        if (performanceMetrics.isEmpty())
        {
            sb.append("No performance data collected.\n");
        }
        else
        {
            for (String opName : performanceMetrics.keySet())
            {
                OperationStats stats = getOperationStats(opName);
                if (stats != null)
                {
                    sb.append(String.format("Operation: %s\n", opName));
                    sb.append(String.format("  Calls: %d\n", stats.getCallCount()));
                    sb.append(String.format("  Min: %.2f ms, Max: %.2f ms, Avg: %.2f ms, Median: %.2f ms, P95: %.2f ms\n",
                        stats.getMinMs(), stats.getMaxMs(), stats.getAvgMs(), stats.getMedianMs(), stats.getP95Ms()));
                }
            }
        }
        sb.append("\n");

        // ===== API CALL STATISTICS =====
        sb.append("=== API CALL STATISTICS ===\n");
        if (apiCallStats.isEmpty())
        {
            sb.append("No API calls recorded.\n");
        }
        else
        {
            for (APICallStats stats : apiCallStats.values())
            {
                sb.append(String.format("Endpoint: %s\n", stats.getEndpoint()));
                sb.append(String.format("  Total Calls: %d\n", stats.getTotalCalls().get()));
                sb.append(String.format("  Successful: %d, Failed: %d\n",
                    stats.getSuccessfulCalls().get(), stats.getFailedCalls().get()));
                sb.append(String.format("  Avg Duration: %.2f ms\n", stats.getAverageDurationMs()));
            }
        }
        sb.append("\n");

        // ===== MEMORY INFORMATION =====
        sb.append("=== MEMORY INFORMATION ===\n");
        if (memorySnapshots.isEmpty())
        {
            sb.append("No memory snapshots recorded.\n");
        }
        else
        {
            MemorySnapshot latest = memorySnapshots.get(memorySnapshots.size() - 1);
            sb.append(String.format("Latest Snapshot: %s\n", TIME_FORMATTER.format(latest.getTimestamp())));
            sb.append(String.format("  Heap Usage: %d MB / %d MB\n", latest.getHeapUsedMb(), latest.getHeapMaxMb()));
            sb.append(String.format("  Item Cache Size: %d items\n", latest.getItemCacheSize()));
            sb.append(String.format("  Price History Entries: %d\n", latest.getPriceHistoryEntries()));
        }
        sb.append("\n");

        // ===== EVENT LOG =====
        sb.append("=== EVENT LOG (Last 50 events) ===\n");
        List<EventLogEntry> recentEvents = eventLog.stream()
            .skip(Math.max(0, eventLog.size() - 50))
            .collect(Collectors.toList());

        if (recentEvents.isEmpty())
        {
            sb.append("No events recorded.\n");
        }
        else
        {
            for (EventLogEntry event : recentEvents)
            {
                String timestamp = TIME_FORMATTER.format(event.getTimestamp());
                String itemStr = event.getItemName() != null ? " (" + event.getItemName() + ")" : "";
                sb.append(String.format("[%s] %s: %s%s\n", timestamp, event.getEventType(), event.getDescription(), itemStr));
            }
        }
        sb.append("\n");

        sb.append("=== END DEBUG REPORT ===\n");
        return sb.toString();
    }

    /**
     * Clear all debug data.
     */
    public void clearAll()
    {
        logBuffer.clear();
        performanceMetrics.clear();
        apiCallStats.clear();
        eventLog.clear();
        memorySnapshots.clear();
        log.info("Debug manager cleared");
    }

    // ===== DATA CLASSES =====

    public enum LogLevel
    {
        DEBUG, INFO, WARN, ERROR
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class LogEntry
    {
        private Instant timestamp;
        private LogLevel level;
        private String sourceClass;
        private String message;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class OperationStats
    {
        private String operationName;
        private int callCount;
        private long minMs;
        private long maxMs;
        private double avgMs;
        private long medianMs;
        private long p95Ms;
    }

    @Data
    public static class APICallStats
    {
        private final String endpoint;
        private final AtomicLong totalCalls = new AtomicLong(0);
        private final AtomicLong successfulCalls = new AtomicLong(0);
        private final AtomicLong failedCalls = new AtomicLong(0);
        private final List<Long> durations = new CopyOnWriteArrayList<>();

        public APICallStats(String endpoint)
        {
            this.endpoint = endpoint;
        }

        public void recordCall(long durationMs, boolean success)
        {
            totalCalls.incrementAndGet();
            if (success)
            {
                successfulCalls.incrementAndGet();
            }
            else
            {
                failedCalls.incrementAndGet();
            }
            durations.add(durationMs);

            // Keep last 1000 durations
            if (durations.size() > 1000)
            {
                durations.remove(0);
            }
        }

        public double getAverageDurationMs()
        {
            if (durations.isEmpty())
            {
                return 0;
            }
            return durations.stream().mapToLong(Long::longValue).average().orElse(0);
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class MemorySnapshot
    {
        private Instant timestamp;
        private long heapUsedMb;
        private long heapMaxMb;
        private int itemCacheSize;
        private int priceHistoryEntries;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class EventLogEntry
    {
        private Instant timestamp;
        private String eventType;        // e.g., "FLIP_COMPLETED", "ALERT_TRIGGERED", "GE_OFFER_PLACED"
        private String description;      // Human-readable description
        @Nullable
        private String itemName;         // Optional: related item name
    }
}
