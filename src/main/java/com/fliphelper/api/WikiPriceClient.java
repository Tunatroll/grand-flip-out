package com.fliphelper.api;

import com.fliphelper.model.ItemMapping;
import com.fliphelper.model.PriceData;
import com.fliphelper.model.PriceSource;
import com.fliphelper.tracker.LocalDataCache;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
public class WikiPriceClient
{
    private static final String BASE_URL = "https://prices.runescape.wiki/api/v1/osrs";

    private final OkHttpClient httpClient;
    private final String userAgent;
    private final Gson gson;
    private final LocalDataCache localCache;

    // Cached data
    private Map<Integer, PriceData> latestPrices = new HashMap<>();
    private Map<Integer, PriceData> prices5m = new HashMap<>();
    private Map<Integer, PriceData> prices1h = new HashMap<>();
    private List<ItemMapping> itemMappings = new ArrayList<>();
    private Map<Integer, ItemMapping> mappingById = new HashMap<>();

    public WikiPriceClient(OkHttpClient httpClient, String userAgent, Gson gson, LocalDataCache localCache)
    {
        this.httpClient = httpClient;
        this.userAgent = userAgent;
        this.gson = gson;
        this.localCache = localCache;
    }


    public List<ItemMapping> fetchMapping() throws IOException
    {
        if (localCache != null)
        {
            String cached = localCache.loadItemMappingCache();
            if (cached != null)
            {
                try
                {
                    Type listType = new TypeToken<List<ItemMapping>>() {}.getType();
                    itemMappings = gson.fromJson(cached, listType);
                    mappingById.clear();
                    for (ItemMapping mapping : itemMappings)
                    {
                        mappingById.put(mapping.getId(), mapping);
                    }
                    log.debug("Loaded {} item mappings from cache", itemMappings.size());
                    return itemMappings;
                }
                catch (Exception e)
                {
                    log.warn("Failed to parse cached item mapping, will fetch fresh: {}", e.getMessage());
                }
            }
        }

        Request request = new Request.Builder()
            .url(BASE_URL + "/mapping")
            .header("User-Agent", userAgent)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Wiki mapping request failed: " + response.code());
            }

            var body = response.body();
            if (body == null)
            {
                throw new IOException("Wiki mapping request returned empty response body");
            }

            String bodyString = body.string();
            Type listType = new TypeToken<List<ItemMapping>>() {}.getType();
            itemMappings = gson.fromJson(bodyString, listType);

            mappingById.clear();
            for (ItemMapping mapping : itemMappings)
            {
                mappingById.put(mapping.getId(), mapping);
            }

            if (localCache != null)
            {
                localCache.saveItemMappingCache(bodyString);
            }

            log.debug("Loaded {} item mappings from Wiki", itemMappings.size());
            return itemMappings;
        }
    }


    public Map<Integer, PriceData> fetchLatestPrices() throws IOException
    {
        if (localCache != null)
        {
            Map<Integer, JsonObject> cached = localCache.loadPriceCache("latest");
            if (cached != null)
            {
                try
                {
                    latestPrices.clear();
                    for (Map.Entry<Integer, JsonObject> entry : cached.entrySet())
                    {
                        int itemId = entry.getKey();
                        JsonObject item = entry.getValue();

                        ItemMapping mapping = mappingById.get(itemId);
                        String name = mapping != null ? mapping.getName() : "Item " + itemId;

                        PriceData priceData = PriceData.builder()
                            .itemId(itemId)
                            .itemName(name)
                            .highPrice(getJsonLong(item, "high"))
                            .highTime(getJsonLong(item, "highTime"))
                            .lowPrice(getJsonLong(item, "low"))
                            .lowTime(getJsonLong(item, "lowTime"))
                            .source(PriceSource.WIKI)
                            .build();

                        latestPrices.put(itemId, priceData);
                    }
                    log.debug("Loaded latest prices for {} items from cache", latestPrices.size());
                    return latestPrices;
                }
                catch (Exception e)
                {
                    log.warn("Failed to parse cached latest prices, will fetch fresh: {}", e.getMessage());
                }
            }
        }

        Request request = new Request.Builder()
            .url(BASE_URL + "/latest")
            .header("User-Agent", userAgent)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Wiki latest prices request failed: " + response.code());
            }

            var body = response.body();
            if (body == null)
            {
                throw new IOException("Wiki latest prices request returned empty response body");
            }

            String bodyString = body.string();
            JsonObject json = new JsonParser().parse(bodyString).getAsJsonObject();
            JsonObject data = json.getAsJsonObject("data");

            latestPrices.clear();
            for (Map.Entry<String, JsonElement> entry : data.entrySet())
            {
                int itemId = Integer.parseInt(entry.getKey());
                JsonObject item = entry.getValue().getAsJsonObject();

                ItemMapping mapping = mappingById.get(itemId);
                String name = mapping != null ? mapping.getName() : "Item " + itemId;

                PriceData priceData = PriceData.builder()
                    .itemId(itemId)
                    .itemName(name)
                    .highPrice(getJsonLong(item, "high"))
                    .highTime(getJsonLong(item, "highTime"))
                    .lowPrice(getJsonLong(item, "low"))
                    .lowTime(getJsonLong(item, "lowTime"))
                    .source(PriceSource.WIKI)
                    .build();

                latestPrices.put(itemId, priceData);
            }

            if (localCache != null)
            {
                localCache.savePriceCache("latest", data);
            }

            log.debug("Fetched latest prices for {} items from Wiki", latestPrices.size());
            return latestPrices;
        }
    }


    public Map<Integer, PriceData> fetch5mPrices() throws IOException
    {
        if (localCache != null)
        {
            Map<Integer, JsonObject> cached = localCache.loadPriceCache("5m");
            if (cached != null)
            {
                try
                {
                    prices5m.clear();
                    for (Map.Entry<Integer, JsonObject> entry : cached.entrySet())
                    {
                        int itemId = entry.getKey();
                        JsonObject item = entry.getValue();

                        ItemMapping mapping = mappingById.get(itemId);
                        String name = mapping != null ? mapping.getName() : "Item " + itemId;

                        PriceData priceData = PriceData.builder()
                            .itemId(itemId)
                            .itemName(name)
                            .avgHighPrice5m(getJsonLong(item, "avgHighPrice"))
                            .avgLowPrice5m(getJsonLong(item, "avgLowPrice"))
                            .highVolume5m(getJsonLong(item, "highPriceVolume"))
                            .lowVolume5m(getJsonLong(item, "lowPriceVolume"))
                            .source(PriceSource.WIKI)
                            .build();

                        prices5m.put(itemId, priceData);
                    }
                    log.debug("Loaded 5m prices for {} items from cache", prices5m.size());
                    return prices5m;
                }
                catch (Exception e)
                {
                    log.warn("Failed to parse cached 5m prices, will fetch fresh: {}", e.getMessage());
                }
            }
        }

        Request request = new Request.Builder()
            .url(BASE_URL + "/5m")
            .header("User-Agent", userAgent)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Wiki 5m prices request failed: " + response.code());
            }

            var body = response.body();
            if (body == null)
            {
                throw new IOException("Wiki 5m prices request returned empty response body");
            }

            String bodyString = body.string();
            JsonObject json = new JsonParser().parse(bodyString).getAsJsonObject();
            JsonObject data = json.getAsJsonObject("data");

            prices5m.clear();
            for (Map.Entry<String, JsonElement> entry : data.entrySet())
            {
                int itemId = Integer.parseInt(entry.getKey());
                JsonObject item = entry.getValue().getAsJsonObject();

                ItemMapping mapping = mappingById.get(itemId);
                String name = mapping != null ? mapping.getName() : "Item " + itemId;

                PriceData priceData = PriceData.builder()
                    .itemId(itemId)
                    .itemName(name)
                    .avgHighPrice5m(getJsonLong(item, "avgHighPrice"))
                    .avgLowPrice5m(getJsonLong(item, "avgLowPrice"))
                    .highVolume5m(getJsonLong(item, "highPriceVolume"))
                    .lowVolume5m(getJsonLong(item, "lowPriceVolume"))
                    .source(PriceSource.WIKI)
                    .build();

                prices5m.put(itemId, priceData);
            }

            if (localCache != null)
            {
                localCache.savePriceCache("5m", data);
            }

            log.debug("Fetched 5m prices for {} items from Wiki", prices5m.size());
            return prices5m;
        }
    }


    public Map<Integer, PriceData> fetch1hPrices() throws IOException
    {
        if (localCache != null)
        {
            Map<Integer, JsonObject> cached = localCache.loadPriceCache("1h");
            if (cached != null)
            {
                try
                {
                    prices1h.clear();
                    for (Map.Entry<Integer, JsonObject> entry : cached.entrySet())
                    {
                        int itemId = entry.getKey();
                        JsonObject item = entry.getValue();

                        ItemMapping mapping = mappingById.get(itemId);
                        String name = mapping != null ? mapping.getName() : "Item " + itemId;

                        PriceData priceData = PriceData.builder()
                            .itemId(itemId)
                            .itemName(name)
                            .avgHighPrice1h(getJsonLong(item, "avgHighPrice"))
                            .avgLowPrice1h(getJsonLong(item, "avgLowPrice"))
                            .highVolume1h(getJsonLong(item, "highPriceVolume"))
                            .lowVolume1h(getJsonLong(item, "lowPriceVolume"))
                            .source(PriceSource.WIKI)
                            .build();

                        prices1h.put(itemId, priceData);
                    }
                    log.debug("Loaded 1h prices for {} items from cache", prices1h.size());
                    return prices1h;
                }
                catch (Exception e)
                {
                    log.warn("Failed to parse cached 1h prices, will fetch fresh: {}", e.getMessage());
                }
            }
        }

        Request request = new Request.Builder()
            .url(BASE_URL + "/1h")
            .header("User-Agent", userAgent)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Wiki 1h prices request failed: " + response.code());
            }

            var body = response.body();
            if (body == null)
            {
                throw new IOException("Wiki 1h prices request returned empty response body");
            }

            String bodyString = body.string();
            JsonObject json = new JsonParser().parse(bodyString).getAsJsonObject();
            JsonObject data = json.getAsJsonObject("data");

            prices1h.clear();
            for (Map.Entry<String, JsonElement> entry : data.entrySet())
            {
                int itemId = Integer.parseInt(entry.getKey());
                JsonObject item = entry.getValue().getAsJsonObject();

                ItemMapping mapping = mappingById.get(itemId);
                String name = mapping != null ? mapping.getName() : "Item " + itemId;

                PriceData priceData = PriceData.builder()
                    .itemId(itemId)
                    .itemName(name)
                    .avgHighPrice1h(getJsonLong(item, "avgHighPrice"))
                    .avgLowPrice1h(getJsonLong(item, "avgLowPrice"))
                    .highVolume1h(getJsonLong(item, "highPriceVolume"))
                    .lowVolume1h(getJsonLong(item, "lowPriceVolume"))
                    .source(PriceSource.WIKI)
                    .build();

                prices1h.put(itemId, priceData);
            }

            if (localCache != null)
            {
                localCache.savePriceCache("1h", data);
            }

            log.debug("Fetched 1h prices for {} items from Wiki", prices1h.size());
            return prices1h;
        }
    }

    
    public List<PriceData> fetchTimeSeries(int itemId, String timestep) throws IOException
    {
        Request request = new Request.Builder()
            .url(BASE_URL + "/timeseries?id=" + itemId + "&timestep=" + timestep)
            .header("User-Agent", userAgent)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                throw new IOException("Wiki timeseries request failed: " + response.code());
            }

            var body = response.body();
            if (body == null)
            {
                throw new IOException("Wiki timeseries request returned empty response body");
            }

            String bodyString = body.string();
            JsonObject json = new JsonParser().parse(bodyString).getAsJsonObject();
            var dataArray = json.getAsJsonArray("data");

            ItemMapping mapping = mappingById.get(itemId);
            String name = mapping != null ? mapping.getName() : "Item " + itemId;

            List<PriceData> series = new ArrayList<>();
            for (JsonElement element : dataArray)
            {
                JsonObject point = element.getAsJsonObject();
                PriceData data = PriceData.builder()
                    .itemId(itemId)
                    .itemName(name)
                    .avgHighPrice1h(getJsonLong(point, "avgHighPrice"))
                    .avgLowPrice1h(getJsonLong(point, "avgLowPrice"))
                    .highVolume1h(getJsonLong(point, "highPriceVolume"))
                    .lowVolume1h(getJsonLong(point, "lowPriceVolume"))
                    .highTime(getJsonLong(point, "timestamp"))
                    .source(PriceSource.WIKI)
                    .build();
                series.add(data);
            }

            return series;
        }
    }

    
    public PriceData getCombinedData(int itemId)
    {
        PriceData latest = latestPrices.get(itemId);
        PriceData fiveMin = prices5m.get(itemId);
        PriceData oneHour = prices1h.get(itemId);

        if (latest == null)
        {
            return null;
        }

        ItemMapping mapping = mappingById.get(itemId);
        String name = mapping != null ? mapping.getName() : latest.getItemName();

        return PriceData.builder()
            .itemId(itemId)
            .itemName(name)
            .highPrice(latest.getHighPrice())
            .highTime(latest.getHighTime())
            .lowPrice(latest.getLowPrice())
            .lowTime(latest.getLowTime())
            .avgHighPrice5m(fiveMin != null ? fiveMin.getAvgHighPrice5m() : 0)
            .avgLowPrice5m(fiveMin != null ? fiveMin.getAvgLowPrice5m() : 0)
            .highVolume5m(fiveMin != null ? fiveMin.getHighVolume5m() : 0)
            .lowVolume5m(fiveMin != null ? fiveMin.getLowVolume5m() : 0)
            .avgHighPrice1h(oneHour != null ? oneHour.getAvgHighPrice1h() : 0)
            .avgLowPrice1h(oneHour != null ? oneHour.getAvgLowPrice1h() : 0)
            .highVolume1h(oneHour != null ? oneHour.getHighVolume1h() : 0)
            .lowVolume1h(oneHour != null ? oneHour.getLowVolume1h() : 0)
            .source(PriceSource.WIKI)
            .build();
    }

    public Map<Integer, ItemMapping> getMappingById()
    {
        return mappingById;
    }

    public List<ItemMapping> getItemMappings()
    {
        return itemMappings;
    }

    public Map<Integer, PriceData> getLatestPrices()
    {
        return latestPrices;
    }

    private long getJsonLong(JsonObject obj, String key)
    {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull())
        {
            return 0;
        }
        return element.getAsLong();
    }
}
