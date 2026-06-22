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
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
import java.util.function.BooleanSupplier;

/**
 * Thin read-only client for Grand Flip Out server intelligence (optional, off by default).
 *
 * <p>Every method that issues an HTTP request first calls {@link #ensureNetworkEnabled()},
 * which throws unless the single master opt-in toggle is on. The gate lives here -- not at
 * the call sites -- so a request can NEVER reach grandflipout.com while the user has opted
 * out, regardless of which caller invokes it.
 */
@Slf4j
public class IntelligenceClient
{
    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final Gson gson;
    private final BooleanSupplier networkEnabled;

    public IntelligenceClient(OkHttpClient sharedClient, String baseUrl, Gson gson, BooleanSupplier networkEnabled)
    {
        String normalized = baseUrl != null ? baseUrl.trim() : "https://grandflipout.com";
        if (normalized.endsWith("/"))
        {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.baseUrl = normalized;
        this.httpClient = sharedClient;
        this.gson = gson;
        this.networkEnabled = networkEnabled;
    }

    /**
     * Hard opt-in gate. Throws unless the user has enabled grandflipout.com networking via
     * the single master config toggle. Called at the top of every request-issuing method so
     * no request can leak out while the master switch is off.
     */
    private void ensureNetworkEnabled() throws IOException
    {
        if (networkEnabled == null || !networkEnabled.getAsBoolean())
        {
            throw new IOException("grandflipout.com networking is disabled (opt-in is off)");
        }
    }

    public SmartAdvisorResult fetchSmartAdvisor(int itemId) throws IOException
    {
        ensureNetworkEnabled();
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
            JsonObject root = gson.fromJson(body, JsonObject.class);
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
        ensureNetworkEnabled();
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
            JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);

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

            return Suggestion.builder()
                .action(action).itemId(itemId).itemName(itemName).price(price).quantity(quantity)
                .expectedProfit(expectedProfit).confidence(confidence).reasons(reasons).targetSlot(targetSlot)
                .marginPer(optLong(root, "marginPer")).geLimit(optInt(root, "geLimit"))
                .profitPerLimit(optLong(root, "profitPerLimit")).volume(optLong(root, "volume"))
                .build();
        }
    }

    /**
     * Advisor (Phase 3): POST the same game-state snapshot and get a COORDINATED basket
     * of up to freeSlots distinct BUYs with gold allocated across them. Mirrors
     * {@link #fetchSuggestion} (Bearer unlocks members + deep-intel ranking; blank =
     * anonymous F2P). Returns an empty list when no basket fits. Synchronous — the
     * caller runs it off the client thread.
     */
    public List<Suggestion> fetchBasket(GameStateSnapshot snapshot, List<Integer> excludeIds,
                                        boolean f2pOnly, String apiKey) throws IOException
    {
        ensureNetworkEnabled();
        String json = snapshot.toRequestJson(excludeIds, f2pOnly);
        okhttp3.RequestBody body = okhttp3.RequestBody.create(
            okhttp3.MediaType.parse("application/json"), json);

        Request.Builder builder = new Request.Builder()
            .url(baseUrl + "/api/intelligence/suggest-basket")
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
            JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);

            List<Suggestion> basket = new ArrayList<>();
            if (root.has("suggestions") && root.get("suggestions").isJsonArray())
            {
                for (com.google.gson.JsonElement el : root.get("suggestions").getAsJsonArray())
                {
                    if (!el.isJsonObject())
                    {
                        continue;
                    }
                    JsonObject o = el.getAsJsonObject();
                    String action = o.has("action") ? o.get("action").getAsString() : "BUY";
                    int itemId = o.has("itemId") ? o.get("itemId").getAsInt() : 0;
                    String itemName = o.has("itemName") ? o.get("itemName").getAsString() : "";
                    long price = o.has("price") ? o.get("price").getAsLong() : 0;
                    int quantity = o.has("quantity") ? o.get("quantity").getAsInt() : 0;
                    long expectedProfit = o.has("expectedProfit") ? o.get("expectedProfit").getAsLong() : 0;
                    double confidence = o.has("confidence") ? o.get("confidence").getAsDouble() : 0;

                    List<String> reasons = new ArrayList<>();
                    if (o.has("reasons") && o.get("reasons").isJsonArray())
                    {
                        o.get("reasons").getAsJsonArray().forEach(r -> reasons.add(r.getAsString()));
                    }

                    basket.add(Suggestion.builder()
                        .action(action).itemId(itemId).itemName(itemName).price(price).quantity(quantity)
                        .expectedProfit(expectedProfit).confidence(confidence).reasons(reasons).targetSlot(-1)
                        .marginPer(optLong(o, "marginPer")).geLimit(optInt(o, "geLimit"))
                        .profitPerLimit(optLong(o, "profitPerLimit")).volume(optLong(o, "volume"))
                        .build());
                }
            }
            return basket;
        }
    }

    /**
     * Next Moves (agentic): POST the same game-state snapshot to the honesty-calibrated
     * reasoning layer ({@code /api/intelligence/recommendations}) and get a CONFIDENCE-WEIGHTED
     * buy plan — gold allocated across free slots in proportion to each pick's calibrated
     * confidence (vs {@link #fetchBasket}'s even split). PRO-gated server-side: a non-PRO
     * caller gets HTTP 403, which surfaces as an IOException so the caller can fall back to the
     * deterministic basket. The lead reason carries the agentic explanation. Synchronous —
     * the caller runs it off the client thread.
     */
    public List<Suggestion> fetchRecommendations(GameStateSnapshot snapshot, List<Integer> excludeIds,
                                                 boolean f2pOnly, String apiKey) throws IOException
    {
        ensureNetworkEnabled();
        String json = snapshot.toRequestJson(excludeIds, f2pOnly);
        okhttp3.RequestBody body = okhttp3.RequestBody.create(
            okhttp3.MediaType.parse("application/json"), json);

        Request.Builder builder = new Request.Builder()
            .url(baseUrl + "/api/intelligence/recommendations")
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "GrandFlipOut-Plugin/1.0");
        if (apiKey != null && !apiKey.trim().isEmpty())
        {
            // requirePro accepts a Bearer token (same as /suggest*); also send X-API-Key
            // for parity with the web/Coach callers.
            builder.header("Authorization", "Bearer " + apiKey.trim());
            builder.header("X-API-Key", apiKey.trim());
        }

        try (Response response = httpClient.newCall(builder.build()).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                throw new IOException("HTTP " + response.code());
            }
            JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);

            List<Suggestion> moves = new ArrayList<>();
            if (root.has("plan") && root.get("plan").isJsonArray())
            {
                for (com.google.gson.JsonElement el : root.get("plan").getAsJsonArray())
                {
                    if (!el.isJsonObject())
                    {
                        continue;
                    }
                    JsonObject o = el.getAsJsonObject();
                    String action = o.has("action") ? o.get("action").getAsString() : "BUY";
                    int itemId = o.has("itemId") ? o.get("itemId").getAsInt() : 0;
                    String itemName = o.has("itemName") ? o.get("itemName").getAsString() : "";
                    long price = o.has("price") ? o.get("price").getAsLong() : 0;
                    int quantity = o.has("quantity") ? o.get("quantity").getAsInt() : 0;
                    long expectedProfit = o.has("expectedProfit") ? o.get("expectedProfit").getAsLong() : 0;
                    // Server confidence is 0-100; Suggestion stores a 0-1 fraction (UI ×100).
                    double confidence = o.has("confidence") ? o.get("confidence").getAsDouble() / 100.0 : 0;

                    // Lead the reasons with the agentic explanation, then the signal it led with.
                    List<String> reasons = new ArrayList<>();
                    if (o.has("explanation") && !o.get("explanation").isJsonNull())
                    {
                        String exp = o.get("explanation").getAsString();
                        if (!exp.isEmpty())
                        {
                            reasons.add(exp);
                        }
                    }
                    if (o.has("leadSignal") && !o.get("leadSignal").isJsonNull())
                    {
                        String lead = o.get("leadSignal").getAsString();
                        if (!lead.isEmpty())
                        {
                            reasons.add("Lead signal: " + lead);
                        }
                    }

                    moves.add(Suggestion.builder()
                        .action(action).itemId(itemId).itemName(itemName).price(price).quantity(quantity)
                        .expectedProfit(expectedProfit).confidence(confidence).reasons(reasons).targetSlot(-1)
                        .marginPer(optLong(o, "marginPer")).geLimit(optInt(o, "geLimit"))
                        .profitPerLimit(optLong(o, "profitPerLimit")).volume(optLong(o, "volume"))
                        .build());
                }
            }
            return moves;
        }
    }

    public void submitTrade(int itemId, long price, int quantity, boolean isBuy)
    {
        try
        {
            ensureNetworkEnabled();
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
        ensureNetworkEnabled();
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
            return gson.fromJson(response.body().string(), JsonObject.class);
        }
    }

    /** Optional long field — 0 when absent/null (back-compat with older server responses). */
    private static long optLong(JsonObject o, String key)
    {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : 0L;
    }

    /** Optional int field — 0 when absent/null. */
    private static int optInt(JsonObject o, String key)
    {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : 0;
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
