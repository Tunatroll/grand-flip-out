package com.fliphelper.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


@Slf4j
public class PeerNetwork
{
    private static final String[] DEFAULT_SEEDS = {
        "https://api.awfullypure.com",      // Official relay
    };

    private static final int DISCOVERY_INTERVAL_SECONDS = 300;  // 5 min
    private static final int HEALTH_CHECK_INTERVAL_SECONDS = 60; // 1 min
    private static final int MAX_PEERS = 20;
    private static final int REQUEST_TIMEOUT_MS = 5000;
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final ConcurrentHashMap<String, PeerInfo> peers;
    private ScheduledExecutorService scheduler;
    private volatile boolean running;

    public PeerNetwork(OkHttpClient httpClient, Gson gson)
    {
        this.httpClient = httpClient;
        this.gson = gson;
        this.peers = new ConcurrentHashMap<>();
    }

    
    public void start(List<String> seedUrls)
    {
        if (running)
        {
            return;
        }
        running = true;

        // Add default seeds
        for (String seed : DEFAULT_SEEDS)
        {
            addPeer(seed);
        }

        // Add user-configured seeds (HTTPS required for security)
        for (String seed : seedUrls)
        {
            String trimmed = seed.trim();
            if (!trimmed.isEmpty())
            {
                if (!isValidPeerUrl(trimmed))
                {
                    log.warn("P2P: Rejecting peer URL '{}' — must use HTTPS and not be localhost", trimmed);
                    continue;
                }
                addPeer(trimmed);
            }
        }

        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "AP-PeerNetwork");
            t.setDaemon(true);
            return t;
        });

        // Run initial discovery immediately, then every 5 minutes
        scheduler.execute(this::discoverPeers);
        scheduler.scheduleAtFixedRate(
            this::discoverPeers,
            DISCOVERY_INTERVAL_SECONDS,
            DISCOVERY_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );

        // Health checks every 60 seconds
        scheduler.scheduleAtFixedRate(
            this::healthCheckAll,
            30, // first check after 30s (give discovery time)
            HEALTH_CHECK_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );

        log.info("PeerNetwork started with {} seed peer(s)", peers.size());
    }

    
    public void stop()
    {
        running = false;
        if (scheduler != null)
        {
            scheduler.shutdown();
            try
            {
                scheduler.awaitTermination(3, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
        log.info("PeerNetwork stopped");
    }

    // --- Peer Management ---

    
    public void addPeer(String baseUrl)
    {
        String normalized = normalizeUrl(baseUrl);
        if (normalized == null || peers.size() >= MAX_PEERS)
        {
            return;
        }
        peers.computeIfAbsent(normalized, url -> {
            PeerInfo info = new PeerInfo();
            info.setBaseUrl(url);
            info.setScore(50); // neutral starting score
            info.setHealthy(true);
            info.setLastSeen(System.currentTimeMillis());
            info.setConsecutiveFailures(0);
            return info;
        });
    }

    
    public PeerInfo getBestPeer()
    {
        return peers.values().stream()
            .filter(PeerInfo::isHealthy)
            .max(Comparator.comparingInt(PeerInfo::getScore))
            .orElse(null);
    }

    
    public List<PeerInfo> getHealthyPeers()
    {
        return peers.values().stream()
            .filter(PeerInfo::isHealthy)
            .sorted(Comparator.comparingInt(PeerInfo::getScore).reversed())
            .collect(Collectors.toList());
    }

    
    public Collection<PeerInfo> getAllPeers()
    {
        return Collections.unmodifiableCollection(peers.values());
    }

    
    public int getHealthyCount()
    {
        return (int) peers.values().stream().filter(PeerInfo::isHealthy).count();
    }

    // --- Request Routing ---

    
    public String getFromBestPeer(String path)
    {
        List<PeerInfo> candidates = getHealthyPeers();
        for (PeerInfo peer : candidates)
        {
            try
            {
                Request request = new Request.Builder()
                    .url(peer.getBaseUrl() + path)
                    .get()
                    .header("User-Agent", "AwfullyPure/2.0.0 RuneLite P2P")
                    .build();

                try (Response response = httpClient.newCall(request).execute())
                {
                    if (response.isSuccessful() && response.body() != null)
                    {
                        promotePeer(peer);
                        return response.body().string();
                    }
                    else
                    {
                        demotePeer(peer, "HTTP " + response.code());
                    }
                }
            }
            catch (IOException e)
            {
                demotePeer(peer, e.getMessage());
            }
        }
        return null; // all peers failed
    }

    
    public String postToBestPeer(String path, String json, Map<String, String> headers)
    {
        List<PeerInfo> candidates = getHealthyPeers();
        RequestBody body = RequestBody.create(JSON_TYPE, json);

        for (PeerInfo peer : candidates)
        {
            try
            {
                Request.Builder builder = new Request.Builder()
                    .url(peer.getBaseUrl() + path)
                    .post(body)
                    .header("User-Agent", "AwfullyPure/2.0.0 RuneLite P2P")
                    .header("Content-Type", "application/json");

                if (headers != null)
                {
                    for (Map.Entry<String, String> h : headers.entrySet())
                    {
                        builder.header(h.getKey(), h.getValue());
                    }
                }

                try (Response response = httpClient.newCall(builder.build()).execute())
                {
                    if (response.isSuccessful())
                    {
                        promotePeer(peer);
                        return response.body() != null ? response.body().string() : "";
                    }
                    else
                    {
                        demotePeer(peer, "HTTP " + response.code());
                    }
                }
            }
            catch (IOException e)
            {
                demotePeer(peer, e.getMessage());
            }
        }
        return null;
    }

    
    public void fanoutPost(String path, String json)
    {
        RequestBody body = RequestBody.create(JSON_TYPE, json);

        for (PeerInfo peer : getHealthyPeers())
        {
            Request request = new Request.Builder()
                .url(peer.getBaseUrl() + path)
                .post(body)
                .header("User-Agent", "AwfullyPure/2.0.0 RuneLite P2P")
                .header("Content-Type", "application/json")
                .build();

            httpClient.newCall(request).enqueue(new Callback()
            {
                @Override
                public void onFailure(Call call, IOException e)
                {
                    demotePeer(peer, e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response)
                {
                    try
                    {
                        if (response.isSuccessful())
                        {
                            promotePeer(peer);
                        }
                        else
                        {
                            demotePeer(peer, "HTTP " + response.code());
                        }
                    }
                    finally
                    {
                        response.close();
                    }
                }
            });
        }
    }

    // --- Discovery & Health ---

    
    private void discoverPeers()
    {
        if (!running)
        {
            return;
        }

        List<PeerInfo> currentPeers = new ArrayList<>(peers.values());
        AtomicInteger discovered = new AtomicInteger(0);

        for (PeerInfo peer : currentPeers)
        {
            try
            {
                Request request = new Request.Builder()
                    .url(peer.getBaseUrl() + "/api/peers")
                    .get()
                    .header("User-Agent", "AwfullyPure/2.0.0 RuneLite P2P")
                    .build();

                try (Response response = httpClient.newCall(request).execute())
                {
                    if (response.isSuccessful() && response.body() != null)
                    {
                        String responseBody = response.body().string();
                        List<String> peerUrls = gson.fromJson(
                            responseBody,
                            new TypeToken<List<String>>(){}.getType()
                        );

                        if (peerUrls != null)
                        {
                            for (String url : peerUrls)
                            {
                                String normalized = normalizeUrl(url);
                                if (normalized != null && !peers.containsKey(normalized) && isValidPeerUrl(url))
                                {
                                    addPeer(url);
                                    discovered.incrementAndGet();
                                }
                            }
                        }
                        promotePeer(peer);
                    }
                }
            }
            catch (Exception e)
            {
                // Discovery failure is non-fatal — we still have our existing peers
                log.debug("Peer discovery failed for {}: {}", peer.getBaseUrl(), e.getMessage());
            }
        }

        if (discovered.get() > 0)
        {
            log.info("P2P: Discovered {} new peer(s), total: {} ({} healthy)",
                discovered.get(), peers.size(), getHealthyCount());
        }
    }

    
    private void healthCheckAll()
    {
        if (!running)
        {
            return;
        }

        for (PeerInfo peer : peers.values())
        {
            try
            {
                Request request = new Request.Builder()
                    .url(peer.getBaseUrl() + "/api/health")
                    .get()
                    .header("User-Agent", "AwfullyPure/2.0.0 RuneLite P2P")
                    .build();

                try (Response response = httpClient.newCall(request).execute())
                {
                    if (response.isSuccessful())
                    {
                        peer.setHealthy(true);
                        peer.setLastSeen(System.currentTimeMillis());
                        peer.setConsecutiveFailures(0);
                        // Slowly promote healthy peers (max score 100)
                        peer.setScore(Math.min(100, peer.getScore() + 1));
                    }
                    else
                    {
                        demotePeer(peer, "HTTP " + response.code());
                    }
                }
            }
            catch (IOException e)
            {
                demotePeer(peer, e.getMessage());
            }
        }

        // Prune peers that have been dead for a long time (>1 hour, score < 10)
        long cutoff = System.currentTimeMillis() - 3_600_000;
        peers.entrySet().removeIf(entry ->
            entry.getValue().getScore() <= 0 &&
            entry.getValue().getLastSeen() < cutoff &&
            !isDefaultSeed(entry.getKey())
        );
    }

    // --- Scoring ---

    private void promotePeer(PeerInfo peer)
    {
        peer.setScore(Math.min(100, peer.getScore() + 2));
        peer.setConsecutiveFailures(0);
        peer.setLastSeen(System.currentTimeMillis());
        if (!peer.isHealthy())
        {
            peer.setHealthy(true);
            log.info("P2P: Peer recovered: {}", peer.getBaseUrl());
        }
    }

    private void demotePeer(PeerInfo peer, String reason)
    {
        peer.setScore(Math.max(0, peer.getScore() - 10));
        peer.setConsecutiveFailures(peer.getConsecutiveFailures() + 1);

        // Mark unhealthy after 3 consecutive failures
        if (peer.getConsecutiveFailures() >= 3 && peer.isHealthy())
        {
            peer.setHealthy(false);
            log.info("P2P: Peer marked unhealthy: {} ({})", peer.getBaseUrl(), reason);
        }
    }

    private boolean isDefaultSeed(String url)
    {
        for (String seed : DEFAULT_SEEDS)
        {
            if (normalizeUrl(seed).equals(url))
            {
                return true;
            }
        }
        return false;
    }

    
    private boolean isValidPeerUrl(String url)
    {
        if (url == null || url.trim().isEmpty())
        {
            return false;
        }
        String lower = url.toLowerCase().trim();
        // Must use HTTPS
        if (!lower.startsWith("https://"))
        {
            return false;
        }
        // Reject localhost and private network addresses
        if (lower.contains("localhost") || lower.contains("127.0.0.1")
            || lower.contains("0.0.0.0") || lower.contains("192.168.")
            || lower.contains("10.0.") || lower.contains("[::1]"))
        {
            return false;
        }
        return true;
    }

    private String normalizeUrl(String url)
    {
        if (url == null || url.trim().isEmpty())
        {
            return null;
        }
        String trimmed = url.trim();
        // Remove trailing slash
        while (trimmed.endsWith("/"))
        {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    // --- Peer Info Model ---

    @Data
    public static class PeerInfo
    {
        private String baseUrl;
        private int score;               // 0-100, higher = more reliable
        private boolean healthy;
        private long lastSeen;           // epoch millis
        private int consecutiveFailures;
        private String version;          // server version from /api/health
        private int itemCount;           // how many items this peer tracks
    }
}
