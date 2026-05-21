package com.fliphelper.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Client for the market analysis engine REST API.
 *
 * The market signal engine runs locally on port 5000 alongside RuneLite, providing:
 *   - Anomaly detection (reconstruction error per input adapter)
 *   - Trading insights from autoencoder analysis
 *   - Bio-system state (emotions, metabolism, curiosity, etc.)
 *   - Market predictions and signals
 *
 * All methods return null gracefully when the signal engine is offline.
 * Results are cached for 10 seconds to prevent excessive polling.
 */
@Slf4j
@Singleton
public class MarketSignalClient
{
    private static final String DEFAULT_SIGNAL_URL = "http://localhost:5000";
    private static final long CACHE_TTL_MS = 10_000;
    private static final int TIMEOUT_SECONDS = 3;

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private volatile String signalUrl = DEFAULT_SIGNAL_URL;
    private volatile boolean enabled = true;

    private static class CacheEntry
    {
        final JsonObject data;
        final long timestamp;

        CacheEntry(JsonObject data)
        {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired()
        {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    @Inject
    public MarketSignalClient()
    {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
        this.gson = new Gson();
    }

    public void setSignalUrl(String url)
    {
        if (url != null && !url.isEmpty())
        {
            this.signalUrl = url;
            cache.clear();
        }
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
        if (!enabled)
        {
            cache.clear();
        }
    }

    // ─── Core fetch with caching ───────────────────────────────

    private JsonObject fetch(String path)
    {
        if (!enabled)
        {
            return null;
        }

        CacheEntry cached = cache.get(path);
        if (cached != null && !cached.isExpired())
        {
            return cached.data;
        }

        Request request = new Request.Builder()
            .url(signalUrl + path)
            .header("User-Agent", "GFO-RuneLitePlugin/2.0")
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (response.isSuccessful())
            {
                ResponseBody body = response.body();
                if (body != null)
                {
                    JsonObject json = gson.fromJson(body.string(), JsonObject.class);
                    cache.put(path, new CacheEntry(json));
                    return json;
                }
            }
        }
        catch (IOException e)
        {
            // Signal engine offline — degrade gracefully
        }

        return null;
    }

    // ─── Public API ────────────────────────────────────────────

    /** Check if the signal engine is reachable. */
    public boolean isAlive()
    {
        return getStatus() != null;
    }

    /** Network architecture, step count, loss EMA, adapter status. */
    public JsonObject getStatus()
    {
        return fetch("/status");
    }

    /** Per-adapter reconstruction error (anomaly scores). */
    public JsonObject getAnomalies()
    {
        return fetch("/anomalies");
    }

    /** Actionable trading insights from autoencoder analysis. */
    public JsonObject getInsights()
    {
        return fetch("/insights");
    }

    /** Market signals and predictions. */
    public JsonObject getSignals()
    {
        return fetch("/signals");
    }

    /** All 8 bio-system statuses (emotions, metabolism, etc.). */
    public JsonObject getBioStatus()
    {
        return fetch("/bio");
    }

    /** Individual bio-system (emotions, memory, ecosystem, etc.). */
    public JsonObject getBioSystem(String system)
    {
        return fetch("/bio/" + system);
    }

    /** Current GE price snapshot from the signal engine's feed. */
    public JsonObject getPrices()
    {
        return fetch("/prices");
    }

    /** Latent space analysis. */
    public JsonObject getLatentAnalysis()
    {
        return fetch("/latent_analysis");
    }

    /** Prediction / reconstruction for current input. */
    public JsonObject getPrediction()
    {
        return fetch("/predict");
    }

    /**
     * Get a summary string suitable for display in the plugin panel.
     * Returns null if signal engine is offline.
     */
    public String getStatusSummary()
    {
        JsonObject status = getStatus();
        if (status == null)
        {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Step: ").append(status.has("step") ? status.get("step").getAsString() : "?");
        sb.append(" | Loss: ").append(status.has("loss_ema") ? String.format("%.6f", status.get("loss_ema").getAsDouble()) : "?");

        JsonObject bio = getBioStatus();
        if (bio != null && bio.has("emotions"))
        {
            JsonObject emo = bio.getAsJsonObject("emotions");
            if (emo != null && emo.has("mood"))
            {
                sb.append(" | Mood: ").append(emo.get("mood").getAsString());
            }
        }

        return sb.toString();
    }
}
