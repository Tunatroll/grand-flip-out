package com.fliphelper.api;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.model.FlipItem;
import com.fliphelper.model.ServerOpportunity;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Optional read-only poll of GFO /api/market/ge/opportunities (HP brain proxy).
 * Disabled by default — wiki-only plugin remains Hub-compliant without network to GFO.
 */
@Slf4j
public class GfoServerClient
{
    private static final int TIMEOUT_SEC = 8;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final GrandFlipOutConfig config;
    private final Gson gson;

    private volatile List<ServerOpportunity> lastOpportunities = Collections.emptyList();
    private volatile long lastFetchMs;
    private volatile String lastError;

    public GfoServerClient(OkHttpClient sharedClient, GrandFlipOutConfig config, Gson gson)
    {
        this.config = config;
        this.gson = gson;
        this.httpClient = sharedClient.newBuilder()
            .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .build();
    }

    public List<ServerOpportunity> getLastOpportunities()
    {
        return lastOpportunities;
    }

    public long getLastFetchMs()
    {
        return lastFetchMs;
    }

    public String getLastError()
    {
        return lastError;
    }

    public void refreshIfEnabled()
    {
        if (!config.enableServerPoll())
        {
            return;
        }

        String base = config.serverBaseUrl();
        if (base == null || base.trim().isEmpty())
        {
            lastError = "Server URL not configured";
            return;
        }

        String url = base.replaceAll("/+$", "") + "/api/market/ge/opportunities";
        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", config.userAgent())
            .header("Accept", "application/json")
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                lastError = "HTTP " + response.code();
                log.debug("GFO server poll failed: {}", lastError);
                return;
            }

            String body = response.body().string();
            JsonObject root = gson.fromJson(body, JsonObject.class);
            JsonArray opps = root.has("opportunities")
                ? root.getAsJsonArray("opportunities")
                : new JsonArray();

            List<ServerOpportunity> parsed = new ArrayList<>();
            for (JsonElement el : opps)
            {
                if (!el.isJsonObject())
                {
                    continue;
                }
                JsonObject o = el.getAsJsonObject();
                int itemId = o.has("item_id") ? o.get("item_id").getAsInt() : 0;
                if (itemId <= 0)
                {
                    continue;
                }
                parsed.add(ServerOpportunity.builder()
                    .itemId(itemId)
                    .name(o.has("name") ? o.get("name").getAsString() : ("Item " + itemId))
                    .signalType(o.has("signal_type") ? o.get("signal_type").getAsString() : "margin")
                    .buyPrice(o.has("buy_price") ? o.get("buy_price").getAsLong() : 0)
                    .sellPrice(o.has("sell_price") ? o.get("sell_price").getAsLong() : 0)
                    .gpPerHour(o.has("gp_per_hour") ? o.get("gp_per_hour").getAsDouble() : 0)
                    .grade(o.has("grade") ? o.get("grade").getAsString() : "?")
                    .build());
                if (parsed.size() >= config.serverPollMaxItems())
                {
                    break;
                }
            }

            lastOpportunities = Collections.unmodifiableList(parsed);
            lastFetchMs = System.currentTimeMillis();
            lastError = null;
            log.debug("GFO server poll: {} opportunities", parsed.size());
        }
        catch (IOException e)
        {
            lastError = e.getMessage();
            log.debug("GFO server poll error: {}", e.getMessage());
        }
    }

    /**
     * POST completed flip to GFO for federation + signal ledger (opt-in, on by default).
     */
    public void recordFlip(FlipItem flip)
    {
        if (!config.enableServerFlipRecord() || flip == null || !flip.isComplete())
        {
            return;
        }

        String base = config.serverBaseUrl();
        if (base == null || base.trim().isEmpty())
        {
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("item_id", flip.getItemId());
        body.addProperty("item_name", flip.getItemName());
        body.addProperty("buy_price", flip.getBuyPrice());
        body.addProperty("sell_price", flip.getSellPrice());
        body.addProperty("quantity", flip.getQuantity());
        body.addProperty("profit", flip.getProfit());
        body.addProperty("source", "plugin");
        body.addProperty("hold_time_sec", flip.getFlipDurationSeconds());
        body.addProperty("tax_applied", true);

        String url = base.replaceAll("/+$", "") + "/api/flips/record";
        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", config.userAgent())
            .header("Accept", "application/json")
            .post(RequestBody.create(body.toString(), JSON))
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                log.debug("GFO flip record failed: HTTP {}", response.code());
                return;
            }
            log.debug("GFO flip recorded: {} profit {}gp", flip.getItemName(), flip.getProfit());
        }
        catch (IOException e)
        {
            log.debug("GFO flip record error: {}", e.getMessage());
        }
    }
}
