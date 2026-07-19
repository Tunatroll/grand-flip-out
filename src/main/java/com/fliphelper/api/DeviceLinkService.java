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
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Device-code account linking (server contract: /api/device-link/*).
 *
 * The plugin mints a short user code, renders it in the panel ("enter this at
 * grandflipout.com/link"), then polls until the user approves in their browser
 * — the API key arrives over TLS exactly once and is handed to the plugin's
 * onLinked callback (which writes it into the RuneLite config). No clipboard,
 * no key paste. All HTTP runs on the plugin's shared executor, NEVER the
 * client thread; UI updates are the listener's job (invokeLater there).
 * The key is never logged.
 */
@Slf4j
public class DeviceLinkService
{
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** Parsed /start response. */
    public static class StartResponse
    {
        public boolean ok;
        public String reason;
        public String userCode;
        public String deviceCode;
        public String verificationUri;
        public long expiresAt;
        public int intervalSec = 5;
    }

    public enum PollStatus { PENDING, SLOW_DOWN, APPROVED, EXPIRED, UNKNOWN, ERROR }

    /** Parsed /poll response. */
    public static class PollResult
    {
        public PollStatus status = PollStatus.ERROR;
        public String apiKey;
        public String displayName;
    }

    /** Panel-facing progress callbacks (called on the executor thread). */
    public interface Listener
    {
        void onCode(String userCode, String verificationUri, long expiresAt);
        void onLinked(String apiKey, String displayName);
        void onFailed(String humanMessage);
    }

    static StartResponse parseStart(String json, Gson gson)
    {
        StartResponse out = new StartResponse();
        try
        {
            JsonObject o = gson.fromJson(json, JsonObject.class);
            out.ok = o.has("ok") && o.get("ok").getAsBoolean();
            if (o.has("reason")) out.reason = o.get("reason").getAsString();
            if (o.has("userCode")) out.userCode = o.get("userCode").getAsString();
            if (o.has("deviceCode")) out.deviceCode = o.get("deviceCode").getAsString();
            if (o.has("verificationUri")) out.verificationUri = o.get("verificationUri").getAsString();
            if (o.has("expiresAt")) out.expiresAt = o.get("expiresAt").getAsLong();
            if (o.has("interval")) out.intervalSec = Math.max(1, o.get("interval").getAsInt());
        }
        catch (Exception e)
        {
            out.ok = false;
        }
        return out;
    }

    static PollResult parsePoll(String json, Gson gson)
    {
        PollResult out = new PollResult();
        try
        {
            JsonObject o = gson.fromJson(json, JsonObject.class);
            String s = o.has("status") ? o.get("status").getAsString() : "";
            switch (s)
            {
                case "pending": out.status = PollStatus.PENDING; break;
                case "slow_down": out.status = PollStatus.SLOW_DOWN; break;
                case "approved": out.status = PollStatus.APPROVED; break;
                case "expired": out.status = PollStatus.EXPIRED; break;
                case "unknown": out.status = PollStatus.UNKNOWN; break;
                default: out.status = PollStatus.ERROR;
            }
            if (out.status == PollStatus.APPROVED)
            {
                if (o.has("apiKey")) out.apiKey = o.get("apiKey").getAsString();
                if (o.has("displayName") && !o.get("displayName").isJsonNull())
                {
                    out.displayName = o.get("displayName").getAsString();
                }
            }
        }
        catch (Exception e)
        {
            out.status = PollStatus.ERROR;
        }
        return out;
    }

    /**
     * Next poll delay for a status, ms; -1 = terminal (never poll again).
     * slow_down backs off interval+2s (RFC 8628); transient errors back off to
     * 2x interval so a 502 storm never hammers the API.
     */
    static long nextPollDelayMs(PollStatus status, int intervalSec)
    {
        switch (status)
        {
            case PENDING: return intervalSec * 1000L;
            case SLOW_DOWN: return (intervalSec + 2) * 1000L;
            case ERROR: return intervalSec * 2000L;
            default: return -1;
        }
    }

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String baseUrl;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean active = new AtomicBoolean(false);

    public DeviceLinkService(OkHttpClient httpClient, Gson gson, String baseUrl,
                             ScheduledExecutorService executor)
    {
        this.httpClient = httpClient;
        this.gson = gson;
        String normalized = baseUrl != null ? baseUrl.trim() : "https://grandflipout.com";
        this.baseUrl = normalized.endsWith("/")
            ? normalized.substring(0, normalized.length() - 1) : normalized;
        this.executor = executor;
    }

    /** Begin a link flow. One at a time; a second call cancels the first. */
    public void startLink(Listener listener)
    {
        active.set(true);
        executor.execute(() ->
        {
            StartResponse start;
            try
            {
                start = parseStart(post("/api/device-link/start", "{}"), gson);
            }
            catch (IOException e)
            {
                listener.onFailed("Couldn't reach grandflipout.com — check your connection and retry.");
                return;
            }
            if (!start.ok || start.deviceCode == null || start.userCode == null)
            {
                listener.onFailed("rate_limited".equals(start.reason)
                    ? "Too many link attempts from this connection — wait a few minutes."
                    : "Link service unavailable — try again shortly.");
                return;
            }
            listener.onCode(start.userCode, start.verificationUri, start.expiresAt);
            schedulePoll(start, listener, start.intervalSec * 1000L);
        });
    }

    /** Stop polling (panel closed / user cancelled / plugin shutdown). */
    public void cancel()
    {
        active.set(false);
    }

    private void schedulePoll(StartResponse start, Listener listener, long delayMs)
    {
        executor.schedule(() ->
        {
            if (!active.get())
            {
                return;
            }
            PollResult r;
            try
            {
                r = parsePoll(post("/api/device-link/poll",
                    "{\"deviceCode\":\"" + start.deviceCode + "\"}"), gson);
            }
            catch (IOException e)
            {
                r = new PollResult(); // ERROR — backs off, keeps trying until expiry
            }
            if (r.status == PollStatus.APPROVED && r.apiKey != null)
            {
                active.set(false);
                listener.onLinked(r.apiKey, r.displayName);
                return;
            }
            if (r.status == PollStatus.EXPIRED || r.status == PollStatus.UNKNOWN)
            {
                active.set(false);
                listener.onFailed("The code expired — press Link account for a fresh one.");
                return;
            }
            long next = nextPollDelayMs(r.status, start.intervalSec);
            if (next < 0 || System.currentTimeMillis() > start.expiresAt)
            {
                active.set(false);
                listener.onFailed("The code expired — press Link account for a fresh one.");
                return;
            }
            schedulePoll(start, listener, next);
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private String post(String path, String jsonBody) throws IOException
    {
        Request request = new Request.Builder()
            .url(baseUrl + path)
            .post(RequestBody.create(JSON, jsonBody))
            .header("User-Agent", "GrandFlipOut-RuneLite")
            .build();
        try (Response response = httpClient.newCall(request).execute())
        {
            return response.body() != null ? response.body().string() : "";
        }
    }
}
