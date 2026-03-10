package com.fliphelper.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Profile & Account Client — manages user accounts, authentication,
 * and feature tier gating within the RuneLite plugin.
 *
 * <h3>Account System</h3>
 * Players create a AP profile (no email required — just a display name).
 * They receive a private API key that authenticates all future requests.
 * Characters (RSNs) are linked to the profile for multi-account P&L tracking.
 *
 * <h3>Tier System (Paywall / Bot Deterrent)</h3>
 * Features are gated behind tiers to:
 *   1. Prevent bots from flooding the system with free accounts
 *   2. Fund server infrastructure for the P2P relay network
 *   3. Reward paying users with advanced analytics
 *
 * Tiers:
 *   - FREE:    Basic flip tracking, price lookup, manual margin checks
 *   - PRO:     Smart suggestions, dump detection, Z-Score alerts, profile P&L
 *   - ELITE:   Multi-account dashboard, market intelligence, bot economy tracker,
 *              investment horizon analysis, priority relay access, API access
 *
 * Tier validation happens server-side. The plugin caches the tier locally
 * and re-validates every 10 minutes to avoid hammering the backend.
 *
 * <h3>P2P Integration</h3>
 * All profile operations route through PeerNetwork. If the primary server
 * is down, the plugin automatically fails over to another relay that has
 * the user's profile (profiles are replicated across federated relays).
 */
@Slf4j
public class ProfileClient
{
    private static final long TIER_CACHE_TTL = 600_000; // 10 min

    private final PeerNetwork peerNetwork;
    private final Gson gson;

    // Cached profile state
    private String apiKey;
    private String profileId;
    private String displayName;
    private AccountTier tier;
    private long tierValidatedAt;
    private List<CharacterInfo> characters;
    private ProfileStats stats;

    public ProfileClient(PeerNetwork peerNetwork, Gson gson)
    {
        this.peerNetwork = peerNetwork;
        this.gson = gson;
        this.tier = AccountTier.FREE;
        this.characters = new ArrayList<>();
    }

    // --- Account Lifecycle ---

    /**
     * Create a new AP account from within the plugin.
     * Returns the API key that the user should save in their config.
     *
     * @param displayName Player's chosen display name (max 32 chars)
     * @return API key string, or null on failure
     */
    public String createAccount(String displayName)
    {
        String json = gson.toJson(Map.of("displayName", displayName));
        String response = peerNetwork.postToBestPeer("/api/profile", json, null);

        if (response == null)
        {
            log.warn("Failed to create profile — no peers available");
            return null;
        }

        try
        {
            JsonObject obj = new JsonParser().parse(response).getAsJsonObject();
            if (obj.has("apiKey"))
            {
                String newKey = obj.get("apiKey").getAsString();
                this.apiKey = newKey;

                // Immediately login with the new key to populate cache
                login(newKey);
                log.info("AP profile created: {}", displayName);
                return newKey;
            }
            else if (obj.has("error"))
            {
                log.warn("Profile creation failed: {}", obj.get("error").getAsString());
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to parse profile creation response: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Login with an existing API key.
     * Fetches profile data and validates tier.
     *
     * @param key API key from config
     * @return true if login succeeded
     */
    public boolean login(String key)
    {
        if (key == null || key.trim().isEmpty())
        {
            return false;
        }

        this.apiKey = key.trim();
        Map<String, String> headers = Map.of("X-AP-Key", this.apiKey);
        String response = peerNetwork.postToBestPeer("/api/profile/validate", "{}", headers);

        // Fallback: try GET /api/profile if validate endpoint doesn't exist
        if (response == null)
        {
            response = getAuthenticatedProfile();
        }

        if (response == null)
        {
            log.warn("Login failed — no peers available or invalid key");
            this.apiKey = null;
            return false;
        }

        try
        {
            JsonObject obj = new JsonParser().parse(response).getAsJsonObject();

            if (obj.has("error"))
            {
                log.warn("Login failed: {}", obj.get("error").getAsString());
                this.apiKey = null;
                return false;
            }

            this.profileId = obj.has("id") ? obj.get("id").getAsString() : null;
            this.displayName = obj.has("displayName") ? obj.get("displayName").getAsString() : "Flipper";

            // Parse tier
            if (obj.has("tier"))
            {
                try
                {
                    this.tier = AccountTier.valueOf(obj.get("tier").getAsString().toUpperCase());
                }
                catch (IllegalArgumentException e)
                {
                    this.tier = AccountTier.FREE;
                }
            }
            else
            {
                this.tier = AccountTier.FREE;
            }
            this.tierValidatedAt = System.currentTimeMillis();

            // Parse characters
            this.characters = new ArrayList<>();
            if (obj.has("characters") && obj.get("characters").isJsonArray())
            {
                for (var elem : obj.getAsJsonArray("characters"))
                {
                    JsonObject ch = elem.getAsJsonObject();
                    CharacterInfo ci = new CharacterInfo();
                    ci.setRsn(ch.has("rsn") ? ch.get("rsn").getAsString() : "");
                    ci.setLastSeen(ch.has("lastSeen") && !ch.get("lastSeen").isJsonNull()
                        ? ch.get("lastSeen").getAsLong() : 0);
                    this.characters.add(ci);
                }
            }

            // Parse aggregate stats
            if (obj.has("stats") && obj.get("stats").isJsonObject())
            {
                JsonObject s = obj.getAsJsonObject("stats");
                this.stats = new ProfileStats();
                this.stats.setTotalProfit(s.has("totalProfit") ? s.get("totalProfit").getAsLong() : 0);
                this.stats.setTotalFlips(s.has("totalFlips") ? s.get("totalFlips").getAsInt() : 0);
                this.stats.setTotalTax(s.has("totalTax") ? s.get("totalTax").getAsLong() : 0);
                this.stats.setStreakCurrent(s.has("streakCurrent") ? s.get("streakCurrent").getAsInt() : 0);
                this.stats.setStreakBest(s.has("streakBest") ? s.get("streakBest").getAsInt() : 0);
            }

            log.info("AP logged in: {} (tier: {}, {} characters, {} total flips)",
                displayName, tier, characters.size(),
                stats != null ? stats.getTotalFlips() : 0);
            return true;
        }
        catch (Exception e)
        {
            log.warn("Failed to parse login response: {}", e.getMessage());
            this.apiKey = null;
            return false;
        }
    }

    /**
     * Logout — clear cached profile data.
     */
    public void logout()
    {
        this.apiKey = null;
        this.profileId = null;
        this.displayName = null;
        this.tier = AccountTier.FREE;
        this.characters = new ArrayList<>();
        this.stats = null;
        log.info("AP logged out");
    }

    /**
     * Check if the user is currently logged in.
     */
    public boolean isLoggedIn()
    {
        return apiKey != null && profileId != null;
    }

    // --- Tier Gating ---

    /**
     * Check if the user has access to a specific feature.
     * Re-validates tier from the server if cache is stale.
     */
    public boolean hasFeature(Feature feature)
    {
        // Free features are always available
        if (feature.requiredTier == AccountTier.FREE)
        {
            return true;
        }

        // Not logged in = free tier only
        if (!isLoggedIn())
        {
            return false;
        }

        // Refresh tier if cache is stale
        if (System.currentTimeMillis() - tierValidatedAt > TIER_CACHE_TTL)
        {
            refreshTier();
        }

        return tier.level >= feature.requiredTier.level;
    }

    /**
     * Get a user-friendly message explaining why a feature is locked.
     */
    public String getUpgradeMessage(Feature feature)
    {
        if (!isLoggedIn())
        {
            return "Create a AP account to unlock " + feature.displayName + " (free)";
        }
        return feature.displayName + " requires " + feature.requiredTier.displayName + " tier. "
            + "Upgrade at api.awfullypure.com/upgrade";
    }

    /**
     * Refresh tier from server.
     */
    private void refreshTier()
    {
        if (apiKey == null)
        {
            return;
        }

        try
        {
            Map<String, String> headers = Map.of("X-AP-Key", apiKey);
            String response = peerNetwork.postToBestPeer("/api/profile/tier", "{}", headers);

            if (response != null)
            {
                JsonObject obj = new JsonParser().parse(response).getAsJsonObject();
                if (obj.has("tier"))
                {
                    this.tier = AccountTier.valueOf(obj.get("tier").getAsString().toUpperCase());
                    this.tierValidatedAt = System.currentTimeMillis();
                }
            }
        }
        catch (Exception e)
        {
            log.debug("Tier refresh failed: {}", e.getMessage());
            // Keep cached tier — don't downgrade on network failure
        }
    }

    // --- Character Management ---

    /**
     * Link an RS character (RSN) to the profile.
     */
    public boolean addCharacter(String rsn)
    {
        if (!isLoggedIn() || rsn == null || rsn.trim().isEmpty())
        {
            return false;
        }

        String json = gson.toJson(Map.of("rsn", rsn.trim()));
        Map<String, String> headers = Map.of("X-AP-Key", apiKey);
        String response = peerNetwork.postToBestPeer("/api/profile/characters", json, headers);

        if (response != null)
        {
            try
            {
                JsonObject obj = new JsonParser().parse(response).getAsJsonObject();
                if (!obj.has("error"))
                {
                    // Re-fetch profile to update character list
                    login(apiKey);
                    return true;
                }
                log.warn("Add character failed: {}", obj.get("error").getAsString());
            }
            catch (Exception e)
            {
                log.warn("Failed to parse add character response: {}", e.getMessage());
            }
        }
        return false;
    }

    /**
     * Log a completed flip to the user's profile.
     * Routes through PeerNetwork for failover.
     */
    public void logFlip(int itemId, String itemName, long buyPrice, long sellPrice, int quantity, String character)
    {
        if (!isLoggedIn())
        {
            return;
        }

        // Tier gate: flip logging requires PRO
        if (!hasFeature(Feature.FLIP_LOGGING))
        {
            return;
        }

        Map<String, Object> flip = new HashMap<>();
        flip.put("itemId", itemId);
        flip.put("itemName", itemName);
        flip.put("buyPrice", buyPrice);
        flip.put("sellPrice", sellPrice);
        flip.put("quantity", quantity);
        if (character != null)
        {
            flip.put("character", character);
        }

        String json = gson.toJson(flip);
        Map<String, String> headers = Map.of("X-AP-Key", apiKey);

        // Fire-and-forget to best peer
        peerNetwork.postToBestPeer("/api/profile/flips", json, headers);
    }

    // --- Profile Data Access ---

    /**
     * Fetch full profile from server (authenticated).
     */
    private String getAuthenticatedProfile()
    {
        if (apiKey == null)
        {
            return null;
        }

        // Use a simple GET with auth header
        // PeerNetwork's getBestPeer doesn't support custom headers for GET,
        // so we route through postToBestPeer with an empty body
        Map<String, String> headers = Map.of("X-AP-Key", apiKey);
        return peerNetwork.postToBestPeer("/api/profile/me", "{}", headers);
    }

    /**
     * Fetch P&L stats for a given time period.
     *
     * @param days Number of days to look back (default 30)
     * @return JSON string of stats, or null on failure
     */
    public String getStats(int days)
    {
        if (!isLoggedIn())
        {
            return null;
        }
        Map<String, String> headers = Map.of("X-AP-Key", apiKey);
        return peerNetwork.postToBestPeer("/api/profile/stats?days=" + days, "{}", headers);
    }

    /**
     * Fetch flip history (paginated).
     */
    public String getFlipHistory(int page, int limit, String character)
    {
        if (!isLoggedIn())
        {
            return null;
        }
        String query = "/api/profile/flips?page=" + page + "&limit=" + limit;
        if (character != null && !character.isEmpty())
        {
            query += "&character=" + character;
        }
        Map<String, String> headers = Map.of("X-AP-Key", apiKey);
        return peerNetwork.postToBestPeer(query, "{}", headers);
    }

    // --- Getters ---

    public String getApiKey() { return apiKey; }
    public String getProfileId() { return profileId; }
    public String getDisplayName() { return displayName; }
    public AccountTier getTier() { return tier; }
    public List<CharacterInfo> getCharacters() { return characters; }
    public ProfileStats getStats() { return stats; }

    // --- Enums & Models ---

    /**
     * Account tiers — each tier unlocks progressively more features.
     * Higher level = more access. Tier validation is server-authoritative.
     */
    public enum AccountTier
    {
        FREE(0, "Free"),
        PRO(1, "Pro"),
        ELITE(2, "Elite");

        public final int level;
        public final String displayName;

        AccountTier(int level, String displayName)
        {
            this.level = level;
            this.displayName = displayName;
        }
    }

    /**
     * Feature gate definitions.
     * Each feature maps to a required tier.
     * The plugin checks hasFeature(Feature.X) before enabling functionality.
     */
    public enum Feature
    {
        // FREE tier — basic functionality
        PRICE_LOOKUP(AccountTier.FREE, "Price Lookup"),
        BASIC_FLIP_TRACKING(AccountTier.FREE, "Basic Flip Tracking"),
        MANUAL_MARGIN_CHECK(AccountTier.FREE, "Manual Margin Check"),
        CROWDSOURCED_CONTRIBUTE(AccountTier.FREE, "Crowdsourced Contributions"),

        // PRO tier — smart analytics
        FLIP_LOGGING(AccountTier.PRO, "Profile Flip Logging"),
        SMART_SUGGESTIONS(AccountTier.PRO, "Smart Flip Suggestions"),
        DUMP_DETECTION(AccountTier.PRO, "Dump Detection & Z-Score Alerts"),
        ZSCORE_ALERTS(AccountTier.PRO, "Z-Score Price Alerts"),
        PROFILE_PNL(AccountTier.PRO, "Profile P&L Dashboard"),
        CHARACTER_LINKING(AccountTier.PRO, "Multi-Character Linking"),
        PRICE_HISTORY(AccountTier.PRO, "Price History & Charts"),
        RECIPE_FLIPPING(AccountTier.PRO, "Recipe Flip Calculator"),

        // ELITE tier — full power
        MULTI_ACCOUNT(AccountTier.ELITE, "Multi-Account Dashboard"),
        MARKET_INTELLIGENCE(AccountTier.ELITE, "Market Intelligence Engine"),
        BOT_ECONOMY(AccountTier.ELITE, "Bot Economy Tracker"),
        INVESTMENT_HORIZON(AccountTier.ELITE, "Investment Horizon Analysis"),
        SMART_ADVISOR(AccountTier.ELITE, "Smart Advisor AI"),
        PRIORITY_RELAY(AccountTier.ELITE, "Priority Relay Access"),
        API_ACCESS(AccountTier.ELITE, "External API Access");

        public final AccountTier requiredTier;
        public final String displayName;

        Feature(AccountTier requiredTier, String displayName)
        {
            this.requiredTier = requiredTier;
            this.displayName = displayName;
        }
    }

    @Data
    public static class CharacterInfo
    {
        private String rsn;
        private long lastSeen;
    }

    @Data
    public static class ProfileStats
    {
        private long totalProfit;
        private int totalFlips;
        private long totalTax;
        private int streakCurrent;
        private int streakBest;
    }
}
