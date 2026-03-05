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
 * Client for the RuneLite item pricing API.
 * Uses RuneLite's internal pricing which is based on GE data.
 */
@Slf4j
public class RuneLitePriceClient
{
    private static final String PRICES_URL = "https://prices.runescape.wiki/api/v1/osrs/latest";
    // RuneLite also provides item prices via its own API
    private static final String RUNELITE_API_URL = "https://api.runelite.net/runelite-1.10.34/item/prices.js";

    private final OkHttpClient httpClient;
    private Map<Integer, PriceData> prices = new HashMap<>();

    public RuneLitePriceClient(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    /**
     * Fetch prices from RuneLite's pricing endpoint.
     * RuneLite aggregates GE transaction data from its userbase.
     */
    public Map<Integer, PriceData> fetchPrices() throws IOException
    {
        Request request = new Request.Builder()
            .url(RUNELITE_API_URL)
            .header("User-Agent", "GrandFlipOut RuneLite Plugin")
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                log.warn("RuneLite price API returned {}, falling back", response.code());
                return prices;
            }

            String body = response.body().string();

            // RuneLite returns an array of {id, name, price, ...}
            // Parse as JSON array
            var jsonArray = JsonParser.parseString(body).getAsJsonArray();

            prices.clear();
            for (JsonElement element : jsonArray)
            {
                JsonObject item = element.getAsJsonObject();
                int itemId = item.get("id").getAsInt();
                String name = item.has("name") ? item.get("name").getAsString() : "Item " + itemId;
                long price = item.has("price") ? item.get("price").getAsLong() : 0;

                PriceData priceData = PriceData.builder()
                    .itemId(itemId)
                    .itemName(name)
                    .highPrice(price)
                    .lowPrice(price)
                    .source(PriceSource.RUNELITE)
                    .build();

                prices.put(itemId, priceData);
            }

            log.debug("Fetched {} prices from RuneLite API", prices.size());
            return prices;
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch RuneLite prices: {}", e.getMessage());
            return prices;
        }
    }

    public Map<Integer, PriceData> getPrices()
    {
        return prices;
    }

    public PriceData getPrice(int itemId)
    {
        return prices.get(itemId);
    }
}
