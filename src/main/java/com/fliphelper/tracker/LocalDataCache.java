package com.fliphelper.tracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class LocalDataCache
{
    private static final String CACHE_DIR = "cache";
    private static final String INTELLIGENCE_DIR = "intelligence";

    private final File cacheDir;
    private final File intelligenceDir;
    private final Gson gson;
    private final Map<String, CacheStats> stats = new ConcurrentHashMap<>();

    public LocalDataCache(File dataDir)
    {
        this.cacheDir = new File(dataDir, CACHE_DIR);
        this.intelligenceDir = new File(cacheDir, INTELLIGENCE_DIR);

        this.cacheDir.mkdirs();
        this.intelligenceDir.mkdirs();

        this.gson = new GsonBuilder().create();

        cleanupOldCaches();
        initializeStats();
    }

    // PRICE CACHE

    public void savePriceCache(String cacheType, JsonObject priceData)
    {
        if (priceData == null || priceData.size() == 0)
        {
            return;
        }

        String filename = "prices-" + cacheType + ".json";
        File cacheFile = new File(cacheDir, filename);

        try (FileWriter writer = new FileWriter(cacheFile))
        {
            IntelligenceCache cache = IntelligenceCache.builder()
                .data(priceData)
                .lastUpdated(Instant.now())
                .build();

            gson.toJson(cache, writer);
            recordHit("price-" + cacheType + "-write");
        }
        catch (IOException e)
        {
            log.error("Failed to save {} price cache: {}", cacheType, e.getMessage());
        }
    }

    public Map<Integer, JsonObject> loadPriceCache(String cacheType)
    {
        String filename = "prices-" + cacheType + ".json";
        File cacheFile = new File(cacheDir, filename);

        if (!cacheFile.exists())
        {
            recordMiss("price-" + cacheType + "-read");
            return null;
        }

        try (FileReader reader = new FileReader(cacheFile))
        {
            JsonElement element = new JsonParser().parse(reader);
            if (element == null || !element.isJsonObject())
            {
                recordMiss("price-" + cacheType + "-read");
                return null;
            }

            JsonObject cache = element.getAsJsonObject();
            long lastUpdated = cache.has("lastUpdated")
                ? cache.get("lastUpdated").getAsLong()
                : 0;

            long age = System.currentTimeMillis() / 1000 - lastUpdated;
            long ttlSeconds = getTTL(cacheType);

            if (age > ttlSeconds)
            {
                recordMiss("price-" + cacheType + "-read");
                return null;
            }

            if (cache.has("data") && cache.get("data").isJsonObject())
            {
                JsonObject dataObj = cache.getAsJsonObject("data");
                Map<Integer, JsonObject> result = new HashMap<>();

                for (String key : dataObj.keySet())
                {
                    try
                    {
                        int itemId = Integer.parseInt(key);
                        result.put(itemId, dataObj.getAsJsonObject(key));
                    }
                    catch (NumberFormatException e)
                    {
                        // Skip non-integer keys
                    }
                }

                recordHit("price-" + cacheType + "-read");
                return result;
            }

            recordMiss("price-" + cacheType + "-read");
            return null;
        }
        catch (Exception e)
        {
            log.warn("Failed to load {} price cache (will use stale fallback): {}", cacheType, e.getMessage());
            recordMiss("price-" + cacheType + "-read");
            return null;
        }
    }

    public boolean isPriceCacheExpired(String cacheType)
    {
        String filename = "prices-" + cacheType + ".json";
        File cacheFile = new File(cacheDir, filename);

        if (!cacheFile.exists())
        {
            return true;
        }

        try (FileReader reader = new FileReader(cacheFile))
        {
            JsonElement element = new JsonParser().parse(reader);
            if (element == null || !element.isJsonObject())
            {
                return true;
            }

            JsonObject cache = element.getAsJsonObject();
            long lastUpdated = cache.has("lastUpdated")
                ? cache.get("lastUpdated").getAsLong()
                : 0;

            long age = System.currentTimeMillis() / 1000 - lastUpdated;
            long ttlSeconds = getTTL(cacheType);

            return age > ttlSeconds;
        }
        catch (Exception e)
        {
            return true;
        }
    }

    // INTELLIGENCE CACHE

    public void saveIntelligenceCache(String cacheType, JsonObject data)
    {
        if (data == null)
        {
            return;
        }

        String filename = cacheType + ".json";
        File cacheFile = new File(intelligenceDir, filename);

        try (FileWriter writer = new FileWriter(cacheFile))
        {
            IntelligenceCache cache = IntelligenceCache.builder()
                .data(data)
                .lastUpdated(Instant.now())
                .build();

            gson.toJson(cache, writer);
            recordHit("intelligence-" + cacheType + "-write");
        }
        catch (IOException e)
        {
            log.error("Failed to save {} intelligence cache: {}", cacheType, e.getMessage());
        }
    }

    public JsonObject loadIntelligenceCache(String cacheType)
    {
        String filename = cacheType + ".json";
        File cacheFile = new File(intelligenceDir, filename);

        if (!cacheFile.exists())
        {
            recordMiss("intelligence-" + cacheType + "-read");
            return null;
        }

        try (FileReader reader = new FileReader(cacheFile))
        {
            JsonElement element = new JsonParser().parse(reader);
            if (element == null || !element.isJsonObject())
            {
                recordMiss("intelligence-" + cacheType + "-read");
                return null;
            }

            JsonObject cache = element.getAsJsonObject();
            long lastUpdated = cache.has("lastUpdated")
                ? cache.get("lastUpdated").getAsLong()
                : 0;

            long age = System.currentTimeMillis() / 1000 - lastUpdated;
            long ttlSeconds = 30; // Intelligence cache TTL = 30 seconds

            if (age > ttlSeconds)
            {
                recordMiss("intelligence-" + cacheType + "-read");
                return null;
            }

            if (cache.has("data"))
            {
                recordHit("intelligence-" + cacheType + "-read");
                return cache.getAsJsonObject("data");
            }

            recordMiss("intelligence-" + cacheType + "-read");
            return null;
        }
        catch (Exception e)
        {
            log.warn("Failed to load {} intelligence cache (will serve without): {}", cacheType, e.getMessage());
            recordMiss("intelligence-" + cacheType + "-read");
            return null;
        }
    }

    // ITEM MAPPING CACHE

    public void saveItemMappingCache(String json)
    {
        if (json == null || json.isEmpty())
        {
            return;
        }

        File cacheFile = new File(cacheDir, "item-mapping.json");

        try (FileWriter writer = new FileWriter(cacheFile))
        {
            ItemMappingCache cache = ItemMappingCache.builder()
                .data(json)
                .lastUpdated(Instant.now())
                .build();

            gson.toJson(cache, writer);
            recordHit("item-mapping-write");
        }
        catch (IOException e)
        {
            log.error("Failed to save item mapping cache: {}", e.getMessage());
        }
    }

    public String loadItemMappingCache()
    {
        File cacheFile = new File(cacheDir, "item-mapping.json");

        if (!cacheFile.exists())
        {
            recordMiss("item-mapping-read");
            return null;
        }

        try (FileReader reader = new FileReader(cacheFile))
        {
            JsonElement element = new JsonParser().parse(reader);
            if (element == null || !element.isJsonObject())
            {
                recordMiss("item-mapping-read");
                return null;
            }

            JsonObject cache = element.getAsJsonObject();
            long lastUpdated = cache.has("lastUpdated")
                ? cache.get("lastUpdated").getAsLong()
                : 0;

            long age = System.currentTimeMillis() / 1000 - lastUpdated;
            long ttlSeconds = 86400; // 24 hours

            if (age > ttlSeconds)
            {
                recordMiss("item-mapping-read");
                return null;
            }

            if (cache.has("data"))
            {
                recordHit("item-mapping-read");
                return cache.get("data").getAsString();
            }

            recordMiss("item-mapping-read");
            return null;
        }
        catch (Exception e)
        {
            log.warn("Failed to load item mapping cache: {}", e.getMessage());
            recordMiss("item-mapping-read");
            return null;
        }
    }

    // CACHE STATS

    public Map<String, CacheStats> getCacheStats()
    {
        return Collections.unmodifiableMap(new HashMap<>(stats));
    }

    private void recordHit(String cacheName)
    {
        stats.computeIfAbsent(cacheName, k -> new CacheStats())
            .recordHit();
    }

    private void recordMiss(String cacheName)
    {
        stats.computeIfAbsent(cacheName, k -> new CacheStats())
            .recordMiss();
    }

    // MAINTENANCE

    private void cleanupOldCaches()
    {
        long now = System.currentTimeMillis();
        long maxAge = 48 * 60 * 60 * 1000; // 48 hours in milliseconds

        File[] files = cacheDir.listFiles();
        if (files == null)
        {
            return;
        }

        for (File file : files)
        {
            if (file.isFile() && (now - file.lastModified()) > maxAge)
            {
                if (file.delete())
                {
                    log.debug("Cleaned up old cache file: {}", file.getName());
                }
            }
        }

        File[] intelligenceFiles = intelligenceDir.listFiles();
        if (intelligenceFiles != null)
        {
            for (File file : intelligenceFiles)
            {
                if (file.isFile() && (now - file.lastModified()) > maxAge)
                {
                    if (file.delete())
                    {
                        log.debug("Cleaned up old intelligence cache file: {}", file.getName());
                    }
                }
            }
        }
    }

    private void initializeStats()
    {
        stats.clear();
    }

    private long getTTL(String cacheType)
    {
        if ("latest".equals(cacheType))
        {
            return 60; // 60 seconds
        }
        else if ("5m".equals(cacheType))
        {
            return 300; // 5 minutes
        }
        else if ("1h".equals(cacheType))
        {
            return 3600; // 1 hour
        }
        return 300;
    }

    // DATA MODELS

    @Data
    @Builder
    public static class IntelligenceCache
    {
        private JsonObject data;
        private Instant lastUpdated;
    }

    @Data
    @Builder
    public static class ItemMappingCache
    {
        private String data;
        private Instant lastUpdated;
    }

    @Data
    @AllArgsConstructor
    public static class CacheStats
    {
        private long hits = 0;
        private long misses = 0;
        private long staleFallbacks = 0;
        private Instant lastUpdate = Instant.now();

        public CacheStats()
        {
            this.hits = 0;
            this.misses = 0;
            this.staleFallbacks = 0;
            this.lastUpdate = Instant.now();
        }

        public void recordHit()
        {
            this.hits++;
            this.lastUpdate = Instant.now();
        }

        public void recordMiss()
        {
            this.misses++;
            this.lastUpdate = Instant.now();
        }

        public void recordStaleFallback()
        {
            this.staleFallbacks++;
            this.lastUpdate = Instant.now();
        }

        public double getHitRate()
        {
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        }
    }
}
