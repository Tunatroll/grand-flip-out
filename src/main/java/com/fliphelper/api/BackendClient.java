package com.fliphelper.api;

import com.fliphelper.debug.DebugManager;
import com.google.gson.Gson;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Slf4j
@Singleton
public class BackendClient
{
    private static final String DEFAULT_BACKEND_URL = "";
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final int FLUSH_INTERVAL_SECONDS = 15;
    private static final int MAX_BATCH_SIZE = 100;

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final ConcurrentLinkedQueue<TradePayload> payloadQueue;
    private ScheduledExecutorService flushExecutor;
    private String backendUrl;
    private boolean enabled;

    // P2P network — when set, trade batches fan out to ALL healthy peers
    private PeerNetwork peerNetwork;

    @Setter
    private DebugManager debugManager;

    @Inject
    public BackendClient(OkHttpClient httpClient, Gson gson)
    {
        this.httpClient = httpClient;
        this.gson = gson;
        this.payloadQueue = new ConcurrentLinkedQueue<>();
        this.backendUrl = DEFAULT_BACKEND_URL;
        this.enabled = true;
    }

    
    public void start()
    {
        if (flushExecutor != null)
        {
            return;
        }
        flushExecutor = Executors.newSingleThreadScheduledExecutor();
        flushExecutor.scheduleAtFixedRate(
            this::flushQueue,
            FLUSH_INTERVAL_SECONDS,
            FLUSH_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        log.info("BackendClient started — flushing every {}s", FLUSH_INTERVAL_SECONDS);
    }

    
    public void stop()
    {
        // Final flush to avoid losing data on logout
        flushQueue();

        if (flushExecutor != null)
        {
            flushExecutor.shutdown();
            try
            {
                flushExecutor.awaitTermination(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            flushExecutor = null;
        }
        log.info("BackendClient stopped");
    }

    
    public void queueTrade(int itemId, long price, int quantityDelta, boolean isBuy)
    {
        if (!enabled || quantityDelta <= 0)
        {
            return;
        }

        payloadQueue.add(new TradePayload(
            itemId,
            price,
            quantityDelta,
            isBuy ? "BUY" : "SELL",
            System.currentTimeMillis()
        ));
    }

    
    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    
    public void setBackendUrl(String url)
    {
        if (url == null || url.trim().isEmpty())
        {
            this.backendUrl = DEFAULT_BACKEND_URL;
            return;
        }
        String trimmed = url.trim().toLowerCase();
        // Enforce HTTPS and reject localhost for Plugin Hub compliance
        if (!trimmed.startsWith("https://"))
        {
            log.warn("BackendClient: Rejecting non-HTTPS backend URL '{}', using default", url);
            this.backendUrl = DEFAULT_BACKEND_URL;
            return;
        }
        if (trimmed.contains("localhost") || trimmed.contains("127.0.0.1") || trimmed.contains("0.0.0.0"))
        {
            log.warn("BackendClient: Rejecting localhost backend URL '{}', using default", url);
            this.backendUrl = DEFAULT_BACKEND_URL;
            return;
        }
        this.backendUrl = url;
    }

    
    public void setPeerNetwork(PeerNetwork peerNetwork)
    {
        this.peerNetwork = peerNetwork;
    }

    
    public int getQueueSize()
    {
        return payloadQueue.size();
    }

    
    private void flushQueue()
    {
        if (payloadQueue.isEmpty())
        {
            return;
        }

        List<TradePayload> batch = new ArrayList<>();
        while (!payloadQueue.isEmpty() && batch.size() < MAX_BATCH_SIZE)
        {
            TradePayload payload = payloadQueue.poll();
            if (payload != null)
            {
                batch.add(payload);
            }
        }

        if (batch.isEmpty())
        {
            return;
        }

        String jsonPayload = gson.toJson(batch);

        // P2P MODE: Fan out to ALL healthy peers so every relay gets the data
        if (peerNetwork != null && peerNetwork.getHealthyCount() > 0)
        {
            long p2pStart = System.currentTimeMillis();
            peerNetwork.fanoutPost("/api/contribute", jsonPayload);
            long p2pDuration = System.currentTimeMillis() - p2pStart;
            log.debug("P2P fanout: {} trade(s) to {} peer(s)",
                batch.size(), peerNetwork.getHealthyCount());
            if (debugManager != null)
            {
                debugManager.recordAPICall("p2p/contribute", p2pDuration, true);
                debugManager.recordEvent("CONTRIBUTE_P2P",
                    String.format("%d trade(s) to %d peers", batch.size(), peerNetwork.getHealthyCount()),
                    null);
            }
            return;
        }

        // FALLBACK: Direct POST to single backendUrl (legacy mode)
        RequestBody body = RequestBody.create(JSON_TYPE, jsonPayload);

        Request request = new Request.Builder()
            .url(backendUrl)
            .post(body)
            .header("User-Agent", "GrandFlipOut/2.0.0 RuneLite")
            .header("Content-Type", "application/json")
            .build();

        httpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.debug("Backend unreachable, dropped {} trade(s): {}",
                    batch.size(), e.getMessage());
                if (debugManager != null)
                {
                    debugManager.recordAPICall("backend/contribute", 0, false);
                }
                // Don't re-queue — graceful degradation is better than memory buildup
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                try
                {
                    if (response.isSuccessful())
                    {
                        log.debug("Backend accepted {} trade(s)", batch.size());
                        if (debugManager != null)
                        {
                            debugManager.recordAPICall("backend/contribute", 0, true);
                        }
                    }
                    else
                    {
                        log.debug("Backend rejected batch: HTTP {}", response.code());
                        if (debugManager != null)
                        {
                            debugManager.recordAPICall("backend/contribute", 0, false);
                        }
                    }
                }
                finally
                {
                    response.close(); // CRITICAL: prevents memory leaks (RuneLite audit check)
                }
            }
        });
    }

    // --- Profile Flip Logging ---

    private String profileApiKey;
    private String profileCharacter;
    private String profileFlipUrl;

    
    public void setProfileConfig(String apiKey, String characterName, String baseUrl)
    {
        this.profileApiKey = (apiKey != null && !apiKey.isEmpty()) ? apiKey : null;
        this.profileCharacter = (characterName != null && !characterName.isEmpty()) ? characterName : null;
        // Derive profile flip URL from the contribute URL base
        this.profileFlipUrl = baseUrl.replace("/api/contribute", "/api/profile/flips");
    }

    
    public void logFlipToProfile(int itemId, String itemName, long buyPrice, long sellPrice, int quantity)
    {
        if (profileApiKey == null)
        {
            return;
        }

        String json = gson.toJson(new FlipPayload(itemId, itemName, buyPrice, sellPrice, quantity, profileCharacter));
        RequestBody body = RequestBody.create(JSON_TYPE, json);

        Request request = new Request.Builder()
            .url(profileFlipUrl)
            .post(body)
            .header("User-Agent", "GrandFlipOut/2.0.0 RuneLite")
            .header("Content-Type", "application/json")
            .header("X-AP-Key", profileApiKey)
            .build();

        httpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.debug("Profile flip log failed: {}", e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                try
                {
                    if (response.isSuccessful())
                    {
                        log.debug("Flip logged to profile: {} x{}", itemName, quantity);
                    }
                    else
                    {
                        log.debug("Profile rejected flip: HTTP {}", response.code());
                    }
                }
                finally
                {
                    response.close();
                }
            }
        });
    }

    
    public static class FlipPayload
    {
        public final int itemId;
        public final String itemName;
        public final long buyPrice;
        public final long sellPrice;
        public final int quantity;
        public final String character;

        public FlipPayload(int itemId, String itemName, long buyPrice, long sellPrice, int quantity, String character)
        {
            this.itemId = itemId;
            this.itemName = itemName;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.quantity = quantity;
            this.character = character;
        }
    }

    
    public static class TradePayload
    {
        public final int itemId;
        public final long price;
        public final int quantity;
        public final String type; // "BUY" or "SELL"
        public final long timestamp;

        public TradePayload(int itemId, long price, int quantity, String type, long timestamp)
        {
            this.itemId = itemId;
            this.price = price;
            this.quantity = quantity;
            this.type = type;
            this.timestamp = timestamp;
        }
    }
}
