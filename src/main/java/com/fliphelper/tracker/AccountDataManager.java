package com.fliphelper.tracker;

import com.fliphelper.model.TradeRecord;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Slf4j
public class AccountDataManager
{
    private static final String ACCOUNTS_DIR = "accounts";
    private static final int MAX_TRADE_HISTORY = 5000;

    private final File accountsDir;
    private final Gson gson;

    @Getter
    private String activeAccount;

    private final Map<String, AccountData> loadedAccounts = new ConcurrentHashMap<>();

    public AccountDataManager(File dataDir, Gson gson)
    {
        this.accountsDir = new File(dataDir, ACCOUNTS_DIR);
        this.accountsDir.mkdirs();
        this.gson = gson;
    }

    // - ACCOUNT LIFECYCLE -

    
    public void onAccountLogin(String rsn)
    {
        if (rsn == null || rsn.isEmpty())
        {
            return;
        }

        String sanitized = sanitizeRsn(rsn);
        this.activeAccount = rsn;

        if (!loadedAccounts.containsKey(sanitized))
        {
            // Try loading from disk
            AccountData loaded = loadFromDisk(sanitized);
            if (loaded != null)
            {
                loadedAccounts.put(sanitized, loaded);
                log.info("Loaded account data for '{}' ({} trades)", rsn, loaded.tradeHistory.size());
            }
            else
            {
                // Brand new account
                AccountData fresh = AccountData.builder()
                    .rsn(rsn)
                    .firstSeen(Instant.now())
                    .lastActive(Instant.now())
                    .tradeHistory(Collections.synchronizedList(new ArrayList<>()))
                    .marginChecks(new ConcurrentHashMap<>())
                    .favoriteItems(Collections.synchronizedList(new ArrayList<>()))
                    .sessionProfit(0L)
                    .totalProfit(0L)
                    .flipCount(0)
                    .build();
                loadedAccounts.put(sanitized, fresh);
                saveToDisk(sanitized);
                log.info("Created new account data for '{}'", rsn);
            }
        }
        else
        {
            loadedAccounts.get(sanitized).lastActive = Instant.now();
        }
    }

    
    public void onAccountLogout()
    {
        if (activeAccount != null)
        {
            String sanitized = sanitizeRsn(activeAccount);
            saveToDisk(sanitized);
            log.info("Saved account data for '{}'", activeAccount);
            activeAccount = null;
        }
    }

    
    public void saveAll()
    {
        for (String key : loadedAccounts.keySet())
        {
            saveToDisk(key);
        }
    }

    // [TRADE RECORDING]

    
    public void recordTrade(TradeRecord trade)
    {
        AccountData data = getActiveAccountData();
        if (data == null)
        {
            return;
        }

        data.tradeHistory.add(trade);
        data.lastActive = Instant.now();

        // Trim history if needed
        while (data.tradeHistory.size() > MAX_TRADE_HISTORY)
        {
            data.tradeHistory.remove(0);
        }

        // Auto-save periodically (every 10 trades)
        if (data.tradeHistory.size() % 10 == 0)
        {
            saveToDisk(sanitizeRsn(activeAccount));
        }
    }

    
    public void recordMarginCheck(int itemId, long buyCheckPrice, long sellCheckPrice)
    {
        AccountData data = getActiveAccountData();
        if (data == null)
        {
            return;
        }

        MarginCheckResult result = MarginCheckResult.builder()
            .itemId(itemId)
            .buyCheckPrice(buyCheckPrice)
            .sellCheckPrice(sellCheckPrice)
            .timestamp(Instant.now())
            .build();

        data.marginChecks.put(itemId, result);
    }

    
    public MarginCheckResult getLastMarginCheck(int itemId)
    {
        AccountData data = getActiveAccountData();
        return data != null ? data.marginChecks.get(itemId) : null;
    }

    // --- FAVORITES ---

    public void addFavorite(int itemId)
    {
        AccountData data = getActiveAccountData();
        if (data != null && !data.favoriteItems.contains(itemId))
        {
            data.favoriteItems.add(itemId);
        }
    }

    public void removeFavorite(int itemId)
    {
        AccountData data = getActiveAccountData();
        if (data != null)
        {
            data.favoriteItems.remove(Integer.valueOf(itemId));
        }
    }

    public List<Integer> getFavorites()
    {
        AccountData data = getActiveAccountData();
        return data != null ? Collections.unmodifiableList(data.favoriteItems) : Collections.emptyList();
    }

    // -- ACCESSORS

    public AccountData getActiveAccountData()
    {
        if (activeAccount == null)
        {
            return null;
        }
        return loadedAccounts.get(sanitizeRsn(activeAccount));
    }

    public AccountData getAccountData(String rsn)
    {
        return loadedAccounts.get(sanitizeRsn(rsn));
    }

    public List<String> getAllAccountNames()
    {
        List<String> names = new ArrayList<>();
        for (AccountData data : loadedAccounts.values())
        {
            names.add(data.rsn);
        }
        Collections.sort(names);
        return names;
    }

    public List<TradeRecord> getTradeHistory()
    {
        AccountData data = getActiveAccountData();
        return data != null ? Collections.unmodifiableList(data.tradeHistory) : Collections.emptyList();
    }

    // ACCOUNT ANALYTICS

    
    public List<Map.Entry<Integer, Integer>> getTopFlippedItems(int limit)
    {
        AccountData data = getActiveAccountData();
        if (data == null || data.tradeHistory.isEmpty())
        {
            return Collections.emptyList();
        }

        Map<Integer, Integer> itemCounts = new HashMap<>();
        for (TradeRecord trade : data.tradeHistory)
        {
            itemCounts.merge(trade.getItemId(), 1, Integer::sum);
        }

        return itemCounts.entrySet().stream()
            .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
            .limit(limit)
            .collect(java.util.stream.Collectors.toList());
    }

    
    public long getLifetimeProfit()
    {
        AccountData data = getActiveAccountData();
        if (data == null)
        {
            return 0;
        }
        return data.totalProfit;
    }

    
    public void addCompletedFlipProfit(long profit)
    {
        AccountData data = getActiveAccountData();
        if (data != null)
        {
            data.totalProfit += profit;
            data.flipCount++;
        }
    }

    
    public Map<Integer, MarginCheckResult> getAllFreshMarginChecks()
    {
        AccountData data = getActiveAccountData();
        if (data == null)
        {
            return Collections.emptyMap();
        }

        Map<Integer, MarginCheckResult> fresh = new HashMap<>();
        for (Map.Entry<Integer, MarginCheckResult> entry : data.marginChecks.entrySet())
        {
            if (entry.getValue().isFresh())
            {
                fresh.put(entry.getKey(), entry.getValue());
            }
        }
        return fresh;
    }

    // ~~~ PERSISTENCE ~~~

    private void saveToDisk(String sanitizedRsn)
    {
        AccountData data = loadedAccounts.get(sanitizedRsn);
        if (data == null)
        {
            return;
        }

        File file = new File(accountsDir, sanitizedRsn + ".json");
        try (FileWriter writer = new FileWriter(file))
        {
            gson.toJson(data, writer);
        }
        catch (IOException e)
        {
            log.error("Failed to save account data for {}: {}", sanitizedRsn, e.getMessage());
        }
    }

    private AccountData loadFromDisk(String sanitizedRsn)
    {
        File file = new File(accountsDir, sanitizedRsn + ".json");
        if (!file.exists())
        {
            return null;
        }

        try (FileReader reader = new FileReader(file))
        {
            return gson.fromJson(reader, AccountData.class);
        }
        catch (Exception e)
        {
            log.error("Failed to load account data for {}: {}", sanitizedRsn, e.getMessage());
            return null;
        }
    }

    
    private String sanitizeRsn(String rsn)
    {
        return rsn.toLowerCase()
            .replace(" ", "_")
            .replaceAll("[^a-z0-9_\\-]", "");
    }

    /* DATA MODELS */

    @Data
    @Builder
    public static class AccountData
    {
        private String rsn;
        private Instant firstSeen;
        private Instant lastActive;
        private List<TradeRecord> tradeHistory;
        private Map<Integer, MarginCheckResult> marginChecks;
        private List<Integer> favoriteItems;
        private long sessionProfit;
        private long totalProfit;
        private int flipCount;
    }

    @Data
    @Builder
    public static class MarginCheckResult
    {
        private int itemId;
        private long buyCheckPrice;
        private long sellCheckPrice;
        private Instant timestamp;

        public long getMargin()
        {
            return sellCheckPrice - buyCheckPrice;
        }

        
        public boolean isFresh()
        {
            return timestamp != null &&
                Instant.now().getEpochSecond() - timestamp.getEpochSecond() < 14400;
        }
    }
}
