package com.fliphelper.api;

import com.fliphelper.model.PriceData;
import com.fliphelper.model.PriceSource;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Supplementary price client that fetches 5-minute averaged data
 * from the OSRS Wiki API as a second data source for cross-validation.
 *
 * FIX: Previous version parsed response as JSON array — Wiki /5m returns
 * {"data": {"itemId": {...}}} which is an object, not an array.
 * ClassCastException at runtime prevented this source from ever loading.
 */
@Slf4j
public class RuneLitePriceClient
{
    private static final String PRICES_URL = "https://prices.runescape.wiki/api/v1/osrs/5m";

    private final OkHttpClient httpClient;
    private Map<Integer, PriceData> prices = new HashMap<>();

    public RuneLitePriceClient(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    /**
     * Fetch 5-minute averaged prices from the Wiki API.
     */
    public Map<Integer, PriceData> fetchPrices() throws IOException
    {
        Request request = new Request.Builder()
            .url(PRICES_URL)
            .header("User-Agent", "AwfullyPure RuneLite Plugin")
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                log.warn("RuneLite price API returned {}", response.code());
                return prices;
            }

            String body = response.body().string();

            // FIX: Wiki /5m returns {"data": {"itemId": {...}}} — an object, NOT an array
            JsonObject json = new JsonParser().parse(body).getAsJsonObject();
            JsonObject data = json.getAsJsonObject("data");

            if (data == null)
            {
                log.warn("RuneLite price API returned no 'data' field");
                return prices;
            }

            Map<Integer, PriceData> newPrices = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : data.entrySet())
            {
                try
                {
                    int itemId = Integer.parseInt(entry.getKey());
                    JsonObject item = entry.getValue().getAsJsonObject();

                    long avgHigh = getJsonLong(item, "avgHighPrice");
                    long avgLow = getJsonLong(item, "avgLowPrice");
                    long highVol = getJsonLong(item, "highPriceVolume");
                    long lowVol = getJsonLong(item, "lowPriceVolume");

                    if (avgHigh <= 0 && avgLow <= 0) continue;

                    PriceData priceData = PriceData.builder()
                        .itemId(itemId)
                        .itemName("Item " + itemId)
                        .highPrice(avgHigh)
                        .lowPrice(avgLow)
                        .avgHighPrice5m(avgHigh)
                        .avgLowPrice5m(avgLow)
                        .highVolume5m(highVol)
                        .lowVolume5m(lowVol)
                        .source(PriceSource.RUNELITE)
                        .build();

                    newPrices.put(itemId, priceData);
                }
                catch (NumberFormatException e) { /* skip non-numeric keys */ }
            }

            prices = newPrices;
            log.debug("Fetched {} prices from supplementary API", prices.size());
            return prices;
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch supplementary prices: {}", e.getMessage());
            return prices;
        }
    }

    public Map<Integer, PriceData> getPrices() { return prices; }
    public PriceData getPrice(int itemId) { return prices.get(itemId); }

    private long getJsonLong(JsonObject obj, String key)
    {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) return 0;
        return element.getAsLong();
    }
}
