/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.api;

import com.fliphelper.model.GameStateSnapshot;
import com.fliphelper.model.Suggestion;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thin read-only client for Grand Flip Out server intelligence (optional, off by default).
 */
@Slf4j
public class IntelligenceClient
{
    private final OkHttpClient httpClient;
    private final String baseUrl;

    public IntelligenceClient(OkHttpClient sharedClient, String baseUrl)
    {
        String normalized = baseUrl != null ? baseUrl.trim() : "https://grandflipout.com";
        if (normalized.endsWith("/"))
        {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.baseUrl = normalized;
        this.httpClient = sharedClient;
    }

    public SmartAdvisorResult fetchSmartAdvisor(int itemId) throws IOException
    {
        HttpUrl url = HttpUrl.parse(baseUrl + "/api/intelligence/smart-advisor")
            .newBuilder()
            .addQueryParameter("itemId", String.valueOf(itemId))
            .build();

        Request request = new Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", "GrandFlipOut-Plugin/1.0")
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                throw new IOException("HTTP " + response.code());
            }

            String body = response.body().string();
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();
            String action = root.has("action") ? root.get("action").getAsString() : "HOLD";
            int strength = root.has("signalStrength") ? root.get("signalStrength").getAsInt() : 0;
            String itemName = root.has("itemName") ? root.get("itemName").getAsString() : ("Item " + itemId);

            List<String> reasons = new ArrayList<>();
            if (root.has("reasons") && root.get("reasons").isJsonArray())
            {
                root.get("reasons").getAsJsonArray().forEach(el -> reasons.add(el.getAsString()));
            }

            return new SmartAdvisorResult(itemId, itemName, action, strength, reasons);
        }
    }

    public JsonObject fetchScreener(String preset, int limit) throws IOException
    {
        HttpUrl url = HttpUrl.parse(baseUrl + "/api/intelligence/screener")
            .newBuilder()
            .addQueryParameter("preset", preset)
            .addQueryParameter("limit", String.valueOf(limit))
            .build();
        return fetchJson(url);
    }

    public JsonObject fetchHighAlch(int limit, boolean bryoStaff) throws IOException
    {
        HttpUrl url = HttpUrl.parse(baseUrl + "/api/intelligence/high-alch")
            .newBuilder()
            .addQueryParameter("limit", String.valueOf(limit))
            .addQueryParameter("bryoStaff", String.valueOf(bryoStaff))
            .build();
        return fetchJson(url);
    }

    public JsonObject fetchVPIN() throws IOException
    {
        return fetchJson(HttpUrl.parse(baseUrl + "/api/intelligence/vpin"));
    }

    public JsonObject fetchNextDumps(int limit) throws IOException
    {
        HttpUrl url = HttpUrl.parse(baseUrl + "/api/intelligence/next-dumps")
            .newBuilder()
            .addQueryParameter("limit", String.valueOf(limit))
            .build();
        return fetchJson(url);
    }

    public JsonObject fetchDips(int minDip) throws IOException
    {
        HttpUrl url = HttpUrl.parse(baseUrl + "/api/intelligence/dips")
            .newBuilder()
            .addQueryParameter("minDip", String.valueOf(minDip))
            .build();
        return fetchJson(url);
    }

    public JsonObject fetchAlerts(int limit) throws IOException
    {
        HttpUrl url = HttpUrl.parse(baseUrl + "/api/market/alerts")
            .newBuilder()
            .addQueryParameter("limit", String.valueOf(limit))
            .build();
        return fetchJson(url);
    }

    public JsonObject fetchKelly(int itemId, long cashStack) throws IOException
    {
        HttpUrl url = HttpUrl.parse(baseUrl + "/api/intelligence/kelly")
            .newBuilder()
            .addQueryParameter("itemId", String.valueOf(itemId))
            .addQueryParameter("cashStack", String.valueOf(cashStack))
            .build();
        return fetchJson(url);
    }

    public JsonObject fetchOptimize(long cashStack, int slots, String risk) throws IOException
    {
        HttpUrl url = HttpUrl.parse(baseUrl + "/api/intelligence/optimize")
            .newBuilder()
            .addQueryParameter("cashStack", String.valueOf(cashStack))
            .addQueryParameter("slots", String.valueOf(slots))
            .addQueryParameter("risk", risk)
            .build();
        return fetchJson(url);
    }

    public JsonObject fetchBarometer() throws IOException
    {
        return fetchJson(HttpUrl.parse(baseUrl + "/api/intelligence/barometer"));
    }

    public JsonObject fetchBanWave() throws IOException
    {
        return fetchJson(HttpUrl.parse(baseUrl + "/api/intelligence/ban-wave"));
    }

    /**
     * Advisor (Phase 1): POST a game-state snapshot and get the single next action.
     * {@code apiKey} (when non-blank) is sent as a Bearer token to unlock members
     * items + deep-intel ranking; blank = anonymous F2P suggestions. Synchronous —
     * the caller runs it off the client thread.
     */
    public Suggestion fetchSuggestion(GameStateSnapshot snapshot, List<Integer> excludeIds,
                                      boolean f2pOnly, String apiKey) throws IOException
    {
        String json = snapshot.toRequestJson(excludeIds, f2pOnly);
        okhttp3.RequestBody body = okhttp3.RequestBody.create(
            okhttp3.MediaType.parse("application/json"), json);

        Request.Builder builder = new Request.Builder()
            .url(baseUrl + "/api/intelligence/suggest")
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "GrandFlipOut-Plugin/1.0");
        if (apiKey != null && !apiKey.trim().isEmpty())
        {
            builder.header("Authorization", "Bearer " + apiKey.trim());
        }

        try (Response response = httpClient.newCall(builder.build()).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                throw new IOException("HTTP " + response.code());
            }
            JsonObject root = new JsonParser().parse(response.body().string()).getAsJsonObject();

            String action = root.has("action") ? root.get("action").getAsString() : "WAIT";
            int itemId = root.has("itemId") ? root.get("itemId").getAsInt() : 0;
            String itemName = root.has("itemName") ? root.get("itemName").getAsString() : "";
            long price = root.has("price") ? root.get("price").getAsLong() : 0;
            int quantity = root.has("quantity") ? root.get("quantity").getAsInt() : 0;
            long expectedProfit = root.has("expectedProfit") ? root.get("expectedProfit").getAsLong() : 0;
            double confidence = root.has("confidence") ? root.get("confidence").getAsDouble() : 0;
            int targetSlot = root.has("targetSlot") ? root.get("targetSlot").getAsInt() : -1;

            List<String> reasons = new ArrayList<>();
            if (root.has("reasons") && root.get("reasons").isJsonArray())
            {
                root.get("reasons").getAsJsonArray().forEach(el -> reasons.add(el.getAsString()));
            }

            return new Suggestion(action, itemId, itemName, price, quantity,
                expectedProfit, confidence, reasons, targetSlot);
        }
    }

    public void submitTrade(int itemId, long price, int quantity, boolean isBuy)
    {
        try
        {
            String json = "{\"trades\":[{\"item_id\":" + itemId
                + ",\"price\":" + price
                + ",\"quantity\":" + quantity
                + ",\"side\":\"" + (isBuy ? "buy" : "sell") + "\""
                + ",\"ts\":" + System.currentTimeMillis()
                + "}],\"plugin_version\":\"1.0.0\"}";

            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                okhttp3.MediaType.parse("application/json"), json);

            Request request = new Request.Builder()
                .url(baseUrl + "/api/intelligence/trades/submit")
                .post(body)
                .header("Content-Type", "application/json")
                .header("User-Agent", "GrandFlipOut-Plugin/1.0")
                .build();

            httpClient.newCall(request).enqueue(new okhttp3.Callback()
            {
                @Override public void onFailure(okhttp3.Call call, IOException e)
                {
                    log.debug("Trade sync failed: {}", e.getMessage());
                }
                @Override public void onResponse(okhttp3.Call call, Response response)
                {
                    response.close();
                }
            });
        }
        catch (Exception e)
        {
            log.debug("Trade sync error: {}", e.getMessage());
        }
    }

    private JsonObject fetchJson(HttpUrl url) throws IOException
    {
        Request request = new Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", "GrandFlipOut-Plugin/1.0")
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                throw new IOException("HTTP " + response.code());
            }
            return new JsonParser().parse(response.body().string()).getAsJsonObject();
        }
    }

    @Value
    public static class SmartAdvisorResult
    {
        int itemId;
        String itemName;
        String action;
        int signalStrength;
        List<String> reasons;

        public List<String> getReasons()
        {
            return reasons != null ? reasons : Collections.emptyList();
        }
    }
}
