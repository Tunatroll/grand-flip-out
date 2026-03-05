package com.fliphelper.api;

import com.fliphelper.model.PriceData;
import com.fliphelper.model.PriceSource;
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
 * Client for the official Jagex Grand Exchange API.
 * Note: This API can be slow and data may be delayed.
 * URL: https://secure.runescape.com/m=itemdb_oldschool/api/catalogue/detail.json?item={id}
 */
@Slf4j
public class OfficialGePriceClient
{
    private static final String BASE_URL = "https://secure.runescape.com/m=itemdb_oldschool/api/catalogue/detail.json?item=";

    private final OkHttpClient httpClient;
    private final Map<Integer, PriceData> prices = new HashMap<>();

    public OfficialGePriceClient(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    /**
     * Fetch price for a single item from the official GE API.
     * Note: This API only supports per-item lookups, not bulk.
     */
    public PriceData fetchPrice(int itemId) throws IOException
    {
        Request request = new Request.Builder()
            .url(BASE_URL + itemId)
            .header("User-Agent", "GrandFlipOut RuneLite Plugin")
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                log.warn("Official GE API returned {} for item {}", response.code(), itemId);
                return null;
            }

            String body = response.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonObject item = json.getAsJsonObject("item");

            String name = item.get("name").getAsString();
            JsonObject current = item.getAsJsonObject("current");
            String priceStr = current.get("price").getAsString()
                .replace(",", "")
                .replace(" ", "");

            long price;
            if (priceStr.endsWith("m"))
            {
                price = (long) (Double.parseDouble(priceStr.replace("m", "")) * 1_000_000);
            }
            else if (priceStr.endsWith("k"))
            {
                price = (long) (Double.parseDouble(priceStr.replace("k", "")) * 1_000);
            }
            else
            {
                price = Long.parseLong(priceStr);
            }

            String trend = current.get("trend").getAsString();

            PriceData priceData = PriceData.builder()
                .itemId(itemId)
                .itemName(name)
                .highPrice(price)
                .lowPrice(price)
                .source(PriceSource.OFFICIAL_GE)
                .build();

            prices.put(itemId, priceData);
            log.debug("Fetched official GE price for {}: {}gp", name, price);
            return priceData;
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch official GE price for item {}: {}", itemId, e.getMessage());
            return null;
        }
    }

    /**
     * Fetch prices for multiple items (rate-limited sequential calls).
     */
    public Map<Integer, PriceData> fetchPrices(Iterable<Integer> itemIds) throws IOException
    {
        Map<Integer, PriceData> result = new HashMap<>();
        for (int itemId : itemIds)
        {
            try
            {
                PriceData data = fetchPrice(itemId);
                if (data != null)
                {
                    result.put(itemId, data);
                }
                // Be respectful with rate limiting
                Thread.sleep(600);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return result;
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
