/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.api;

import com.fliphelper.model.FlipItem;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thin client for Grand Flip Out server intelligence (optional, off by default).
 * Read-mostly; the only writes are the opt-in trade contribution and the linked
 * account's watchlist union (putAccountWatchlist).
 */
@Slf4j
public class IntelligenceClient
{
    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final Gson gson;

    public IntelligenceClient(OkHttpClient sharedClient, String baseUrl, Gson gson)
    {
        String normalized = baseUrl != null ? baseUrl.trim() : "https://grandflipout.com";
        if (normalized.endsWith("/"))
        {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.baseUrl = normalized;
        this.httpClient = sharedClient;
        this.gson = gson;
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

    /**
     * Public top flips for the FIRST-RUN Advisor teaser: a plain GET on the public
     * plugin-suggestions feed with a generic 10M bankroll. Sends NOTHING about the
     * player (contrast {@link #fetchBasket}, which posts the game-state snapshot) —
     * but it is still a network call, so callers must keep it behind the
     * enableServerFunctionality master switch like every other request here.
     */
    public List<Suggestion> fetchPublicTopFlips(int limit) throws IOException
    {
        HttpUrl url = HttpUrl.parse(baseUrl + "/api/plugin/suggestions")
            .newBuilder()
            .addQueryParameter("limit", String.valueOf(limit))
            .addQueryParameter("budget", "10000000")
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
            JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
            List<Suggestion> flips = new ArrayList<>();
            if (root.has("suggestions") && root.get("suggestions").isJsonArray())
            {
                for (com.google.gson.JsonElement el : root.get("suggestions").getAsJsonArray())
                {
                    if (!el.isJsonObject())
                    {
                        continue;
                    }
                    JsonObject o = el.getAsJsonObject();
                    // Feed shape (server routes/plugin.js): itemId, name, buyPrice, sellPrice,
                    // netProfit (per item, after tax), limit, expectedProfit (4h realizable),
                    // action ("Buy at X -> Sell at Y" display line), confidence (0-100).
                    String actionLine = o.has("action") ? o.get("action").getAsString() : "";
                    flips.add(Suggestion.builder()
                        .action("BUY")
                        .itemId(o.has("itemId") ? o.get("itemId").getAsInt() : 0)
                        .itemName(o.has("name") ? o.get("name").getAsString() : "")
                        .price(o.has("buyPrice") ? o.get("buyPrice").getAsLong() : 0)
                        .quantity(0)
                        .expectedProfit(o.has("expectedProfit") ? o.get("expectedProfit").getAsLong() : 0)
                        .confidence(o.has("confidence") ? o.get("confidence").getAsDouble() : 0)
                        .reasons(actionLine.isEmpty()
                            ? Collections.emptyList() : Collections.singletonList(actionLine))
                        .targetSlot(-1)
                        .marginPer(o.has("netProfit") ? o.get("netProfit").getAsLong() : 0)
                        .geLimit(o.has("limit") ? o.get("limit").getAsInt() : 0)
                        .build());
                }
            }
            return flips;
        }
    }

    /**
     * Raw server watchlist items (id + alert rules) — the push-back leg needs them
     * VERBATIM so a PUT (which replaces the whole list server-side) never drops the
     * rules the user configured on the website.
     */
    public com.google.gson.JsonArray fetchAccountWatchlistRaw(String apiKey) throws IOException
    {
        HttpUrl url = HttpUrl.parse(baseUrl + "/api/account/watchlist").newBuilder().build();
        Request request = new Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", "GrandFlipOut-Plugin/1.0")
            .header("Authorization", "Bearer " + apiKey)
            .build();
        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                throw new IOException("HTTP " + response.code());
            }
            JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
            return (root.has("items") && root.get("items").isJsonArray())
                ? root.get("items").getAsJsonArray() : new com.google.gson.JsonArray();
        }
    }

    /**
     * Replace the account watchlist (#190 push-back leg). The caller supplies the FULL
     * merged array (server items verbatim + new local ids), because the server contract
     * is replace-not-merge ("merge is a client concern" — routes/account.js). Fail-soft
     * at the call site: a 400/402 (caps) must never break the plugin.
     */
    public boolean putAccountWatchlist(String apiKey, com.google.gson.JsonArray items) throws IOException
    {
        JsonObject body = new JsonObject();
        body.add("items", items);
        Request request = new Request.Builder()
            .url(HttpUrl.parse(baseUrl + "/api/account/watchlist").newBuilder().build())
            .put(okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), gson.toJson(body)))
            .header("Accept", "application/json")
            .header("User-Agent", "GrandFlipOut-Plugin/1.0")
            .header("Authorization", "Bearer " + apiKey)
            .build();
        try (Response response = httpClient.newCall(request).execute())
        {
            return response.isSuccessful();
        }
    }

    /**
     * Item ids present in a raw server watchlist array. Malformed entries (non-objects,
     * missing or non-numeric itemId) are SKIPPED, never thrown on — a single junk row
     * must not abort the whole pull+push sync cycle.
     */
    public static java.util.Set<Integer> watchlistIds(com.google.gson.JsonArray serverItems)
    {
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        if (serverItems == null)
        {
            return ids;
        }
        for (com.google.gson.JsonElement el : serverItems)
        {
            try
            {
                if (el.isJsonObject() && el.getAsJsonObject().has("itemId"))
                {
                    com.google.gson.JsonElement id = el.getAsJsonObject().get("itemId");
                    if (id.isJsonPrimitive() && id.getAsJsonPrimitive().isNumber())
                    {
                        ids.add(id.getAsInt());
                    }
                }
            }
            catch (Exception ignored)
            {
                // skip the malformed entry, keep the rest
            }
        }
        return ids;
    }

    /**
     * The #190 push-back union: server entries ride VERBATIM (the PUT replaces the whole
     * list server-side — resending them untouched is what preserves the alert rules the
     * user configured on the website, and the client never filters entries it doesn't
     * understand, because a filtered PUT would DELETE them) + local-only stars appended
     * as bare {itemId} rows, deduped. Returns null when the server already knows every
     * local star — the caller skips the PUT entirely.
     */
    public static com.google.gson.JsonArray mergeWatchlistForPushBack(
        com.google.gson.JsonArray serverItems, java.util.Collection<Integer> localStars)
    {
        java.util.Set<Integer> known = watchlistIds(serverItems);
        com.google.gson.JsonArray merged = null;
        if (localStars != null)
        {
            for (Integer id : localStars)
            {
                if (id == null || known.contains(id))
                {
                    continue;
                }
                if (merged == null)
                {
                    merged = serverItems == null ? new com.google.gson.JsonArray() : serverItems.deepCopy();
                }
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("itemId", id);
                merged.add(o);
                known.add(id);
            }
        }
        return merged;
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
                .marginQuality(optString(root, "marginQuality"))
                .priceTier(optString(root, "priceTier"))
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
                        .marginQuality(optString(o, "marginQuality"))
                        .priceTier(optString(o, "priceTier"))
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
                        .marginQuality(optString(o, "marginQuality"))
                        .priceTier(optString(o, "priceTier"))
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

    /**
     * Fire-and-forget: report one COMPLETED (buy-sell paired) flip to the anonymous
     * flip-outcome telemetry endpoint. The caller gates on the same double opt-in as
     * {@link #submitTrade} (enableServerFunctionality + contributeTrades). Only live
     * fills are reported: GE-history imports replay old trades through the same
     * tracker path, so anything whose sell is not recent is dropped here. Payload is
     * item/prices/quantity/timings only — never account identity.
     */
    public void submitFlipOutcome(FlipItem flip)
    {
        try
        {
            if (!shouldSubmitOutcome(flip, Instant.now()))
            {
                return;
            }

            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                okhttp3.MediaType.parse("application/json"), flipOutcomeJson(flip));

            Request request = new Request.Builder()
                .url(baseUrl + "/api/flip-outcomes")
                .post(body)
                .header("Content-Type", "application/json")
                .header("User-Agent", "GrandFlipOut-Plugin/1.0")
                .build();

            httpClient.newCall(request).enqueue(new okhttp3.Callback()
            {
                @Override public void onFailure(okhttp3.Call call, IOException e)
                {
                    log.debug("Flip outcome sync failed: {}", e.getMessage());
                }
                @Override public void onResponse(okhttp3.Call call, Response response)
                {
                    response.close();
                }
            });
        }
        catch (Exception e)
        {
            log.debug("Flip outcome sync error: {}", e.getMessage());
        }
    }

    /**
     * Fire-and-forget: log one completed flip to the player's OWN website flip log
     * (POST /api/profile/flips, X-API-Key). This is the return leg of the flip-sync
     * value exchange (M58, owner GO 2026-07-13): the anonymous telemetry above improves
     * the shared fill-time data; THIS gives the contributor their synced flip history +
     * P&L on grandflipout.com and opt-in leaderboard eligibility. The caller gates on
     * the same double opt-in (enableServerFunctionality + contributeTrades) AND a
     * linked key; the same live-witnessed guard applies here, so GE-history imports
     * never sync. The server computes tax/profit (ge-tax SSOT) — no client math.
     */
    public void logProfileFlip(FlipItem flip, String apiKey)
    {
        try
        {
            if (apiKey == null || apiKey.trim().isEmpty() || !shouldSubmitOutcome(flip, Instant.now()))
            {
                return;
            }

            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("itemId", flip.getItemId());
            o.addProperty("itemName", flip.getItemName());
            o.addProperty("buyPrice", flip.getBuyPrice());
            o.addProperty("sellPrice", flip.getSellPrice());
            o.addProperty("quantity", flip.getQuantity());
            o.addProperty("source", "plugin");

            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                okhttp3.MediaType.parse("application/json"), o.toString());

            Request request = new Request.Builder()
                .url(baseUrl + "/api/profile/flips")
                .post(body)
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey.trim())
                .header("User-Agent", "GrandFlipOut-Plugin/1.0")
                .build();

            httpClient.newCall(request).enqueue(new okhttp3.Callback()
            {
                @Override public void onFailure(okhttp3.Call call, IOException e)
                {
                    log.debug("Profile flip sync failed: {}", e.getMessage());
                }
                @Override public void onResponse(okhttp3.Call call, Response response)
                {
                    response.close();
                }
            });
        }
        catch (Exception e)
        {
            log.debug("Profile flip sync error: {}", e.getMessage());
        }
    }

    /** Live-fill guard: true when the flip's sell completed within the last 10 minutes. */
    static boolean isRecentFill(FlipItem flip, Instant now)
    {
        return flip != null && flip.getSellTime() != null
            && !flip.getSellTime().isAfter(now.plusSeconds(60))
            && Duration.between(flip.getSellTime(), now).toMinutes() < 10;
    }

    /**
     * Telemetry gate: recency alone is NOT enough — GeHistoryImporter stamps its whole
     * batch with one shared `now` (buy==sell, geSlot -1), so imported pairs passed the
     * recency guard and fabricated 0-minute fill durations (every captured row to
     * 2026-07-13). Only pairs whose BOTH legs were live-witnessed GE events submit.
     */
    static boolean shouldSubmitOutcome(FlipItem flip, Instant now)
    {
        return flip != null && flip.isLiveWitnessed() && isRecentFill(flip, now);
    }

    /** Build the /api/flip-outcomes payload (matches the server's sanitizeOutcome contract). */
    static String flipOutcomeJson(FlipItem flip)
    {
        StringBuilder sb = new StringBuilder(160);
        sb.append("{\"itemId\":").append(flip.getItemId())
            .append(",\"buyPrice\":").append(flip.getBuyPrice())
            .append(",\"sellPrice\":").append(flip.getSellPrice())
            .append(",\"qty\":").append(flip.getQuantity())
            .append(",\"outcome\":\"filled\"");
        if (flip.getBuyTime() != null)
        {
            sb.append(",\"placedAt\":").append(flip.getBuyTime().toEpochMilli());
        }
        if (flip.getSellTime() != null)
        {
            sb.append(",\"filledAt\":").append(flip.getSellTime().toEpochMilli());
        }
        if (flip.getFrozenSellPrice() > 0)
        {
            sb.append(",\"hitTarget\":").append(flip.getSellPrice() >= flip.getFrozenSellPrice());
        }
        sb.append('}');
        return sb.toString();
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

    /** Optional string field — null when absent/null. */
    private static String optString(JsonObject o, String key)
    {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
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
