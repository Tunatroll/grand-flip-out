/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.api;

import com.fliphelper.model.DumpFeedEntry;
import com.google.gson.JsonArray;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
 * Read-only client for the free F2P dump feed (GET /api/intelligence/dump-feed).
 * Anonymous calls return F2P items only; a Bearer token unlocks members items.
 * Kept separate from {@link IntelligenceClient} so the feed is self-contained.
 */
@Slf4j
public class DumpFeedClient
{
    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final Gson gson;
    private final BooleanSupplier networkEnabled;

    public DumpFeedClient(OkHttpClient sharedClient, String baseUrl, Gson gson, BooleanSupplier networkEnabled)
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

    /** Hard opt-in gate -- throws unless grandflipout.com networking is enabled (master toggle). */
    private void ensureNetworkEnabled() throws IOException
    {
        if (networkEnabled == null || !networkEnabled.getAsBoolean())
        {
            throw new IOException("grandflipout.com networking is disabled (opt-in is off)");
        }
    }

    /**
     * Fetch the recent dump feed. {@code apiKey} (when non-blank) is sent as a
     * Bearer token to unlock members items; blank = anonymous F2P-only. Synchronous —
     * the caller runs it off the client thread.
     */
    public List<DumpFeedEntry> fetch(int limit, String apiKey) throws IOException
    {
        ensureNetworkEnabled();
        HttpUrl url = HttpUrl.parse(baseUrl + "/api/intelligence/dump-feed")
            .newBuilder()
            .addQueryParameter("limit", String.valueOf(limit))
            .build();

        Request.Builder builder = new Request.Builder()
            .url(url)
            .get()
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
            List<DumpFeedEntry> out = new ArrayList<>();
            if (root.has("dumps") && root.get("dumps").isJsonArray())
            {
                JsonArray arr = root.getAsJsonArray("dumps");
                for (int i = 0; i < arr.size(); i++)
                {
                    JsonObject d = arr.get(i).getAsJsonObject();
                    out.add(new DumpFeedEntry(
                        d.has("itemId") ? d.get("itemId").getAsInt() : 0,
                        d.has("itemName") ? d.get("itemName").getAsString() : "",
                        d.has("members") && d.get("members").getAsBoolean(),
                        d.has("buyPrice") ? d.get("buyPrice").getAsLong() : 0,
                        d.has("percentChange") ? d.get("percentChange").getAsDouble() : 0,
                        d.has("tier") ? d.get("tier").getAsString() : "watch"));
                }
            }
            return out;
        }
    }

    public static List<DumpFeedEntry> empty()
    {
        return Collections.emptyList();
    }
}
