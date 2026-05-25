/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.api;

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
