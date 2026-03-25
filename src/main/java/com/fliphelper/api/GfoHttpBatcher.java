package com.fliphelper.api;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
public class GfoHttpBatcher
{
    private static final String BATCH_ENDPOINT  = "/api/plugin/batch";
    private static final MediaType JSON_TYPE    = MediaType.parse("application/json; charset=utf-8");
    private static final int FLUSH_INTERVAL_S   = 5;
    private static final int MAX_QUEUE_SIZE     = 500;
    private static final int MAX_BATCH_SIZE     = 200; // server-enforced ceiling
    private static final String PLUGIN_VERSION  = "2.0.0";

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final ConcurrentLinkedQueue<Object> eventQueue;
    private final AtomicInteger droppedCount = new AtomicInteger(0);

    private ScheduledExecutorService flushExecutor;
    private String backendBaseUrl = "";
    private boolean enabled = true;

    public GfoHttpBatcher(OkHttpClient httpClient, Gson gson)
    {
        this.httpClient = httpClient;
        this.gson       = gson;
        this.eventQueue = new ConcurrentLinkedQueue<>();
    }

    // --- Lifecycle ---

    public void start()
    {
        if (flushExecutor != null) return;

        flushExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "gfo-batcher");
            t.setDaemon(true);
            return t;
        });
        flushExecutor.scheduleAtFixedRate(
            this::flush, FLUSH_INTERVAL_S, FLUSH_INTERVAL_S, TimeUnit.SECONDS
        );
        log.info("GfoHttpBatcher started — flushing to {} every {}s", backendBaseUrl, FLUSH_INTERVAL_S);
    }

    public void stop()
    {
        flush(); // final drain

        if (flushExecutor != null)
        {
            flushExecutor.shutdown();
            try { flushExecutor.awaitTermination(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            flushExecutor = null;
        }

        int dropped = droppedCount.get();
        if (dropped > 0)
            log.info("GfoHttpBatcher stopped ({} events dropped due to full queue)", dropped);
        else
            log.info("GfoHttpBatcher stopped cleanly");
    }

    // --- Public API ---

    
    public void queueJtiEvent(int itemId, String itemName, double jtiScore,
                               double marginPct, int volume5m,
                               long buyPrice, long sellPrice, long taxAdjustedProfit)
    {
        if (!enabled) return;
        enqueue(new JtiEvent(itemId, itemName, jtiScore, marginPct,
                             volume5m, buyPrice, sellPrice, taxAdjustedProfit,
                             System.currentTimeMillis()));
    }

    
    public void queueZScoreEvent(int itemId, String itemName, double zScore,
                                  String severity, long currentPrice, long avgPrice,
                                  double volumeRatio, String classification,
                                  double confidence, double recoveryProbability)
    {
        if (!enabled) return;
        enqueue(new ZScoreEvent(itemId, itemName, zScore, severity,
                                currentPrice, avgPrice, volumeRatio,
                                classification, confidence, recoveryProbability,
                                System.currentTimeMillis()));
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    
    public void setBackendBaseUrl(String url)
    {
        this.backendBaseUrl = url
            .replaceAll("/api/contribute$", "")
            .replaceAll("/api$", "")
            .replaceAll("/$", "");
    }

    public int getQueueSize() { return eventQueue.size(); }

    // --- Internal ---

    private void enqueue(Object event)
    {
        if (eventQueue.size() >= MAX_QUEUE_SIZE)
        {
            eventQueue.poll(); // drop oldest to make room
            droppedCount.incrementAndGet();
        }
        eventQueue.add(event);
    }

    private void flush()
    {
        if (eventQueue.isEmpty()) return;

        List<Object> batch = new ArrayList<>(Math.min(eventQueue.size(), MAX_BATCH_SIZE));
        while (!eventQueue.isEmpty() && batch.size() < MAX_BATCH_SIZE)
        {
            Object event = eventQueue.poll();
            if (event != null) batch.add(event);
        }
        if (batch.isEmpty()) return;

        String json = gson.toJson(new BatchPayload(PLUGIN_VERSION, batch));
        String url  = backendBaseUrl + BATCH_ENDPOINT;

        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(JSON_TYPE, json))
            .header("User-Agent", "GrandFlipOut/" + PLUGIN_VERSION + " RuneLite")
            .header("Content-Type", "application/json")
            .build();

        httpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                // Backend offline — graceful degradation, don't re-queue
                log.debug("GfoHttpBatcher: backend unreachable, dropped {} event(s): {}",
                    batch.size(), e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                try
                {
                    if (response.isSuccessful())
                        log.debug("GfoHttpBatcher: {} event(s) accepted", batch.size());
                    else
                        log.debug("GfoHttpBatcher: server rejected batch — HTTP {}", response.code());
                }
                finally
                {
                    response.close(); // CRITICAL: prevents OkHttp memory leak (RuneLite audit check)
                }
            }
        });
    }

    // --- Wire Format ---

    public static class BatchPayload
    {
        public final String pluginVersion;
        public final List<Object> events;

        public BatchPayload(String pluginVersion, List<Object> events)
        {
            this.pluginVersion = pluginVersion;
            this.events        = events;
        }
    }

    public static class JtiEvent
    {
        public final String type = "jti"; // server routes on this field
        public final int    itemId;
        public final String itemName;
        public final double jtiScore;
        public final double marginPct;
        public final int    volume5m;
        public final long   buyPrice;
        public final long   sellPrice;
        public final long   taxAdjustedProfit;
        public final long   timestamp;

        public JtiEvent(int itemId, String itemName, double jtiScore, double marginPct,
                        int volume5m, long buyPrice, long sellPrice,
                        long taxAdjustedProfit, long timestamp)
        {
            this.itemId            = itemId;
            this.itemName          = itemName;
            this.jtiScore          = jtiScore;
            this.marginPct         = marginPct;
            this.volume5m          = volume5m;
            this.buyPrice          = buyPrice;
            this.sellPrice         = sellPrice;
            this.taxAdjustedProfit = taxAdjustedProfit;
            this.timestamp         = timestamp;
        }
    }

    
    public static class ZScoreEvent
    {
        public final String type = "zscore"; // server routes on this field
        public final int    itemId;
        public final String itemName;
        public final double zScore;
        public final String severity;          // WARNING | ALERT | MAJOR | CRITICAL
        public final long   currentPrice;
        public final long   avgPrice;
        public final double volumeRatio;
        public final String classification;    // DUMP | PUMP | VOLATILITY_SPIKE | MANIPULATION_SUSPECTED
        public final double confidence;
        public final double recoveryProbability;
        public final long   timestamp;

        public ZScoreEvent(int itemId, String itemName, double zScore, String severity,
                           long currentPrice, long avgPrice, double volumeRatio,
                           String classification, double confidence,
                           double recoveryProbability, long timestamp)
        {
            this.itemId              = itemId;
            this.itemName            = itemName;
            this.zScore              = zScore;
            this.severity            = severity;
            this.currentPrice        = currentPrice;
            this.avgPrice            = avgPrice;
            this.volumeRatio         = volumeRatio;
            this.classification      = classification;
            this.confidence          = confidence;
            this.recoveryProbability = recoveryProbability;
            this.timestamp           = timestamp;
        }
    }
}
