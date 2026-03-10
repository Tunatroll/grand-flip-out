package com.fliphelper.tracker;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;


@Slf4j
public class ServerIntelligenceClient {
    private final OkHttpClient httpClient;
    private final ServerIntelligenceConfig config;
    private final Map<String, CachedResult> resultCache;

    public ServerIntelligenceClient(ServerIntelligenceConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getServerTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getServerTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
        this.resultCache = new HashMap<>();
    }

    
    public Optional<SmartAdvisorResult> getSmartAdvisor(int itemId) {
        if (!config.isServerIntelligenceAvailable()) {
            return Optional.empty();
        }
        try {
            String cacheKey = "smart-advisor-" + itemId;
            if (config.isCacheServerResults()) {
                CachedResult cached = resultCache.get(cacheKey);
                if (cached != null && !cached.isExpired()) {
                    return Optional.ofNullable((SmartAdvisorResult) cached.value);
                }
            }
            String url = config.getEndpointUrl("/intelligence/smart-advisor?itemId=" + itemId);
            String json = fetchJson(url);
            SmartAdvisorResult result = parseSmartAdvisorResponse(json);
            if (config.isCacheServerResults() && result != null) {
                resultCache.put(cacheKey, new CachedResult(result, config.getCacheExpirySeconds()));
            }
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.warn("Failed to fetch SmartAdvisor from server: {}", e.getMessage());
            return Optional.empty();
        }
    }

    
    public Optional<QuickFlipResult> getQuickFlip(int itemId) {
        if (!config.isServerIntelligenceAvailable()) {
            return Optional.empty();
        }
        try {
            String cacheKey = "quick-flip-" + itemId;
            if (config.isCacheServerResults()) {
                CachedResult cached = resultCache.get(cacheKey);
                if (cached != null && !cached.isExpired()) {
                    return Optional.ofNullable((QuickFlipResult) cached.value);
                }
            }
            String url = config.getEndpointUrl("/intelligence/quick-flip?itemId=" + itemId);
            String json = fetchJson(url);
            QuickFlipResult result = parseQuickFlipResponse(json);
            if (config.isCacheServerResults() && result != null) {
                resultCache.put(cacheKey, new CachedResult(result, config.getCacheExpirySeconds()));
            }
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.warn("Failed to fetch QuickFlip from server: {}", e.getMessage());
            return Optional.empty();
        }
    }

    
    public Optional<MarketAnalysisResult> getMarketAnalysis(int itemId) {
        if (!config.isServerIntelligenceAvailable()) {
            return Optional.empty();
        }
        try {
            String cacheKey = "market-analysis-" + itemId;
            if (config.isCacheServerResults()) {
                CachedResult cached = resultCache.get(cacheKey);
                if (cached != null && !cached.isExpired()) {
                    return Optional.ofNullable((MarketAnalysisResult) cached.value);
                }
            }
            String url = config.getEndpointUrl("/intelligence/market-analysis?itemId=" + itemId);
            String json = fetchJson(url);
            MarketAnalysisResult result = parseMarketAnalysisResponse(json);
            if (config.isCacheServerResults() && result != null) {
                resultCache.put(cacheKey, new CachedResult(result, config.getCacheExpirySeconds()));
            }
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.warn("Failed to fetch market analysis from server: {}", e.getMessage());
            return Optional.empty();
        }
    }

    
    public Optional<DumpAnalysisResult> getDumpAnalysis(int itemId) {
        if (!config.isServerIntelligenceAvailable()) {
            return Optional.empty();
        }
        try {
            String cacheKey = "dump-analysis-" + itemId;
            if (config.isCacheServerResults()) {
                CachedResult cached = resultCache.get(cacheKey);
                if (cached != null && !cached.isExpired()) {
                    return Optional.ofNullable((DumpAnalysisResult) cached.value);
                }
            }
            String url = config.getEndpointUrl("/intelligence/dump-analysis?itemId=" + itemId);
            String json = fetchJson(url);
            DumpAnalysisResult result = parseDumpAnalysisResponse(json);
            if (config.isCacheServerResults() && result != null) {
                resultCache.put(cacheKey, new CachedResult(result, config.getCacheExpirySeconds()));
            }
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.warn("Failed to fetch dump analysis from server: {}", e.getMessage());
            return Optional.empty();
        }
    }

    
    public Optional<MarketOverviewResult> getMarketOverview() {
        if (!config.isServerIntelligenceAvailable()) {
            return Optional.empty();
        }
        try {
            String cacheKey = "market-overview";
            if (config.isCacheServerResults()) {
                CachedResult cached = resultCache.get(cacheKey);
                if (cached != null && !cached.isExpired()) {
                    return Optional.ofNullable((MarketOverviewResult) cached.value);
                }
            }
            String url = config.getEndpointUrl("/intelligence/market-overview");
            String json = fetchJson(url);
            MarketOverviewResult result = parseMarketOverviewResponse(json);
            if (config.isCacheServerResults() && result != null) {
                resultCache.put(cacheKey, new CachedResult(result, 300));
            }
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.warn("Failed to fetch market overview from server: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /* HTTP Fetch */

    private String fetchJson(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "GrandFlipOut RuneLite Plugin")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException("HTTP " + response.code() + " from " + url);
            }
            return response.body().string();
        }
    }

    // Gson Parsers //

    private SmartAdvisorResult parseSmartAdvisorResponse(String json) {
        try {
            if (json == null || json.isEmpty()) return null;
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            if (root.has("error")) {
                log.warn("Server error in SmartAdvisor: {}", root.get("error").getAsString());
                return null;
            }
            SmartAdvisorResult r = new SmartAdvisorResult();
            r.itemId = getInt(root, "itemId", 0);
            r.itemName = getString(root, "itemName", "");
            r.smartScore = getInt(root, "smartScore", 0);
            r.action = getString(root, "action", "HOLD");
            r.confidence = getString(root, "confidence", "LOW");
            r.risk = getString(root, "risk", "HIGH");
            r.currentPrice = getLong(root, "currentPrice", 0);
            r.estimatedProfitLow = getLong(root, "estimatedProfitLow", 0);
            r.estimatedProfitHigh = getLong(root, "estimatedProfitHigh", 0);
            r.holdTime = getString(root, "holdTime", "unknown");
            r.reasons = getStringList(root, "reasons");
            r.warnings = getStringList(root, "warnings");
            return r;
        } catch (Exception e) {
            log.error("Failed to parse SmartAdvisor response: {}", e.getMessage());
            return null;
        }
    }

    private QuickFlipResult parseQuickFlipResponse(String json) {
        try {
            if (json == null || json.isEmpty()) return null;
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            if (root.has("error")) return null;
            QuickFlipResult r = new QuickFlipResult();
            r.itemId = getInt(root, "itemId", 0);
            r.itemName = getString(root, "itemName", "");
            r.qfScore = getInt(root, "qfScore", 0);
            String grade = getString(root, "qfGrade", "F");
            r.qfGrade = grade.isEmpty() ? 'F' : grade.charAt(0);
            r.estimatedFillTime = getString(root, "estimatedFillTime", "unknown");
            r.netProfitPerFlip = getLong(root, "netProfitPerFlip", 0);
            r.marginPercent = getDouble(root, "marginPercent", 0);
            r.volumePerHour = getLong(root, "volumePerHour", 0);
            r.buyPrice = getLong(root, "buyPrice", 0);
            r.sellPrice = getLong(root, "sellPrice", 0);
            return r;
        } catch (Exception e) {
            log.error("Failed to parse QuickFlip response: {}", e.getMessage());
            return null;
        }
    }

    private MarketAnalysisResult parseMarketAnalysisResponse(String json) {
        try {
            if (json == null || json.isEmpty()) return null;
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            if (root.has("error")) return null;
            MarketAnalysisResult r = new MarketAnalysisResult();
            r.itemId = getInt(root, "itemId", 0);
            r.rsi = getInt(root, "rsi", 50);
            r.ema14 = getDouble(root, "ema14", 0);
            r.ema50 = getDouble(root, "ema50", 0);
            r.momentumSignal = getString(root, "momentumSignal", "NEUTRAL");
            r.bollingerSignal = getString(root, "bollingerSignal", "NEUTRAL");
            r.marketRegime = getString(root, "marketRegime", "UNKNOWN");
            r.macd = new HashMap<>();
            if (root.has("macd") && root.get("macd").isJsonObject()) {
                JsonObject macdObj = root.getAsJsonObject("macd");
                r.macd.put("macd", getDouble(macdObj, "macd", 0));
                r.macd.put("signal", getDouble(macdObj, "signal", 0));
                r.macd.put("histogram", getDouble(macdObj, "histogram", 0));
            }
            return r;
        } catch (Exception e) {
            log.error("Failed to parse MarketAnalysis response: {}", e.getMessage());
            return null;
        }
    }

    private DumpAnalysisResult parseDumpAnalysisResponse(String json) {
        try {
            if (json == null || json.isEmpty()) return null;
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            if (root.has("error")) return null;
            DumpAnalysisResult r = new DumpAnalysisResult();
            r.itemId = getInt(root, "itemId", 0);
            r.itemName = getString(root, "itemName", "");
            r.dumpPercentage = (float) getDouble(root, "dumpPercentage", 0);
            r.currentPrice = getLong(root, "currentPrice", 0);
            r.targetBuyPrice = getLong(root, "targetBuyPrice", 0);
            r.expectedRecoveryPrice = getLong(root, "expectedRecoveryPrice", 0);
            r.recoveryTimeHours = getInt(root, "recoveryTimeHours", 0);
            r.confidence = getString(root, "confidence", "LOW");
            r.recommendedAction = getString(root, "recommendedAction", "HOLD");
            r.riskLevel = getString(root, "riskLevel", "HIGH");
            r.reasoning = getString(root, "reasoning", "");
            return r;
        } catch (Exception e) {
            log.error("Failed to parse DumpAnalysis response: {}", e.getMessage());
            return null;
        }
    }

    private MarketOverviewResult parseMarketOverviewResponse(String json) {
        try {
            if (json == null || json.isEmpty()) return null;
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            if (root.has("error")) return null;
            MarketOverviewResult r = new MarketOverviewResult();
            r.totalItemsAnalyzed = getInt(root, "totalItemsAnalyzed", 0);
            r.strongBuys = getInt(root, "strongBuys", 0);
            r.buys = getInt(root, "buys", 0);
            r.marketMood = getString(root, "marketMood", "Neutral");
            r.marketHealthScore = getInt(root, "marketHealthScore", 50);
            r.topPicks = new ArrayList<>();
            if (root.has("topPicks") && root.get("topPicks").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("topPicks")) {
                    JsonObject pick = el.getAsJsonObject();
                    SmartAdvisorResult p = new SmartAdvisorResult();
                    p.itemId = getInt(pick, "itemId", 0);
                    p.itemName = getString(pick, "itemName", "");
                    p.smartScore = getInt(pick, "smartScore", 0);
                    p.action = getString(pick, "action", "HOLD");
                    p.confidence = getString(pick, "confidence", "LOW");
                    p.estimatedProfitHigh = getLong(pick, "estimatedProfitHigh", 0);
                    r.topPicks.add(p);
                }
            }
            r.alerts = getStringList(root, "alerts");
            return r;
        } catch (Exception e) {
            log.error("Failed to parse MarketOverview response: {}", e.getMessage());
            return null;
        }
    }

    // - JSON Helpers -

    private static int getInt(JsonObject obj, String key, int defaultVal) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : defaultVal;
    }

    private static long getLong(JsonObject obj, String key, long defaultVal) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsLong() : defaultVal;
    }

    private static double getDouble(JsonObject obj, String key, double defaultVal) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsDouble() : defaultVal;
    }

    private static String getString(JsonObject obj, String key, String defaultVal) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : defaultVal;
    }

    private static List<String> getStringList(JsonObject obj, String key) {
        List<String> list = new ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray(key)) {
                list.add(el.getAsString());
            }
        }
        return list;
    }

    // [Inner Classes]

    private static class CachedResult {
        final Object value;
        final long expiryTime;

        CachedResult(Object value, int expirySeconds) {
            this.value = value;
            this.expiryTime = System.currentTimeMillis() + (expirySeconds * 1000L);
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    // Result data classes
    public static class SmartAdvisorResult {
        public int itemId;
        public String itemName;
        public int smartScore;
        public String action;
        public String confidence;
        public String risk;
        public List<String> reasons;
        public List<String> warnings;
        public long currentPrice;
        public long estimatedProfitLow;
        public long estimatedProfitHigh;
        public String holdTime;
    }

    public static class QuickFlipResult {
        public int itemId;
        public String itemName;
        public int qfScore;
        public char qfGrade;
        public String estimatedFillTime;
        public long netProfitPerFlip;
        public double marginPercent;
        public long volumePerHour;
        public long buyPrice;
        public long sellPrice;
    }

    public static class MarketAnalysisResult {
        public int itemId;
        public int rsi;
        public double ema14;
        public double ema50;
        public String momentumSignal;
        public String bollingerSignal;
        public String marketRegime;
        public Map<String, Double> macd;
    }

    public static class DumpAnalysisResult {
        public int itemId;
        public String itemName;
        public float dumpPercentage;
        public long currentPrice;
        public long targetBuyPrice;
        public long expectedRecoveryPrice;
        public int recoveryTimeHours;
        public String confidence;
        public String recommendedAction;
        public String riskLevel;
        public String reasoning;
    }

    public static class MarketOverviewResult {
        public int totalItemsAnalyzed;
        public int strongBuys;
        public int buys;
        public String marketMood;
        public int marketHealthScore;
        public List<SmartAdvisorResult> topPicks;
        public List<String> alerts;
    }
}
