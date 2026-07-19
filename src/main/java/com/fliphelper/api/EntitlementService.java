/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

/**
 * Resolves the user's Grand Flip Out account entitlement from a pasted API key.
 *
 * <p>Compliance + UX design notes:
 * <ul>
 *   <li>NO network call is ever made when the user has not pasted a key — anonymous
 *       users stay fully local (F2P suggestions only) and never touch the server.</li>
 *   <li>The last successful result is cached and kept through a short grace window so a
 *       transient server outage does not instantly re-lock a paying user mid-session.</li>
 *   <li>The unlock <em>criterion</em> lives entirely on the server (account-exists today,
 *       paid later); the plugin only reads {@code unlocked} and never needs an update to
 *       flip from free-unlock to paid-unlock.</li>
 *   <li>No in-client payment — the key is created on the web and pasted in. The key is
 *       only ever sent to grandflipout.com to check entitlement.</li>
 * </ul>
 */
@Slf4j
public class EntitlementService
{
    /** Keep the last-known-good unlocked state for this long if the server is unreachable. */
    private static final long GRACE_MS = 24L * 60 * 60 * 1000;

    /** Entitlement returned for anonymous users and unknown/invalid keys. */
    public static final Entitlement LOCKED = new Entitlement(false, "anonymous", false);

    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final Gson gson;

    private volatile Entitlement cached = LOCKED;
    /** Timestamp of the last SUCCESSFUL server response (drives the grace window). */
    private volatile long lastSuccessAt = 0L;

    public EntitlementService(OkHttpClient sharedClient, String baseUrl, Gson gson)
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

    /** Cheap, non-blocking read of the current entitlement (safe on the EDT). */
    public Entitlement get()
    {
        return cached;
    }

    /** True when the account is unlocked (all items + premium features). */
    public boolean isUnlocked()
    {
        return cached != null && cached.isUnlocked();
    }

    /**
     * Refresh entitlement for the given key. Blocking — call from a background thread.
     * Safe to call with a null/blank key (resets to LOCKED, makes no network call).
     */
    public void refresh(String apiKey)
    {
        if (apiKey == null || apiKey.trim().isEmpty())
        {
            // Anonymous: never call the server.
            cached = LOCKED;
            lastSuccessAt = 0L;
            return;
        }

        try
        {
            Entitlement result = fetch(apiKey.trim());
            cached = result;
            lastSuccessAt = System.currentTimeMillis();
        }
        catch (IOException e)
        {
            // Server unreachable. Keep the last-known-good unlock through the grace
            // window; otherwise fall back to locked so we never grant access on a guess.
            if (cached.isUnlocked() && (System.currentTimeMillis() - lastSuccessAt) <= GRACE_MS)
            {
                log.debug("Entitlement refresh failed ({}), keeping cached unlock within grace", e.getMessage());
            }
            else
            {
                cached = LOCKED;
                log.debug("Entitlement refresh failed ({}), grace expired -> locked", e.getMessage());
            }
        }
    }

    private Entitlement fetch(String apiKey) throws IOException
    {
        Request request = new Request.Builder()
            .url(baseUrl + "/api/entitlements")
            .get()
            .header("Authorization", "Bearer " + apiKey)
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
            boolean authenticated = root.has("authenticated") && root.get("authenticated").getAsBoolean();
            boolean unlocked = root.has("unlocked") && root.get("unlocked").getAsBoolean();
            String tier = root.has("tier") && !root.get("tier").isJsonNull()
                ? root.get("tier").getAsString()
                : (authenticated ? "free" : "anonymous");
            return new Entitlement(authenticated, tier, unlocked);
        }
    }

    @Value
    public static class Entitlement
    {
        boolean authenticated;
        String tier;
        boolean unlocked;
    }
}
