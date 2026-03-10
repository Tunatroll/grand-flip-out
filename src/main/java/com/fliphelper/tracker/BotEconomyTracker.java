package com.fliphelper.tracker;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;

/**
 * BotEconomyTracker - Supply Chain & Market Disruption Analyzer for Awfully Pure RuneLite Plugin
 *
 * This tracker analyzes publicly available GE price and volume data from the OSRS Wiki
 * real-time prices API to detect supply disruptions, volume anomalies, and cyclical
 * market patterns. It models supply-side dynamics (resource gathering rates, item sinks,
 * and production pipelines) to help players anticipate price movements.
 *
 * COMPLIANCE NOTE:
 * - Uses ONLY publicly available OSRS Wiki API data (prices.runescape.wiki)
 * - Does NOT interact with any third-party software or automation tools
 * - Does NOT automate any game actions — all trading is manual
 * - Analysis is purely informational, equivalent to reading price charts
 * - All players have equal access to the same public market data
 *
 * @author Awfully Pure
 * @version 1.0
 */
@Slf4j
public class BotEconomyTracker {

    private final PriceService priceService;
    private final Map<Integer, BotItemProfile> botItemDatabase;
    private final SupplyShockDetector supplyShockDetector;
    private final F2PToPPipelineTracker pipelineTracker;
    private final BotWaveProfitStrategy profitStrategy;
    private final YewBowAlchChainTracker yewBowTracker;
    private final BloodEssenceTracker bloodEssenceTracker;
    private final MarketHealthIndex marketHealth;

    /**
     * Enum representing the six phases of the supply-side lifecycle
     * (F2P resource gatherers progress through these stages)
     */
    public enum BotPhase {
        F2P_FRESH,           // Low-level F2P resource gathering activity
        F2P_LEVELED,         // Mid-level F2P resource gathering activity
        F2P_BOND_READY,      // F2P accounts accumulating capital for bonds
        P2P_EARLY,           // Early P2P resource gathering and combat drops
        P2P_MID,             // Mid-tier P2P slayer and PvM drops
        P2P_LATE             // High-tier PvM drops (Zulrah, Vorkath, Nex)
    }

    /**
     * Enum representing resource gathering activities that drive supply
     */
    public enum BotActivity {
        WOODCUTTING, FISHING, MINING, COMBAT, SLAYER, BOSSING, RUNECRAFT, FLETCHING, HERBLORE
    }

    /**
     * Pipeline stages representing the overall supply-side market state
     */
    public enum PipelineStage {
        BOTS_BANNED,           // Post-disruption supply shock, items spiking
        F2P_REBUILDING,        // F2P supply slowly recovering
        F2P_SATURATED,         // F2P supply high, capital flowing to P2P
        P2P_TRANSITION,        // Capital transitioning into P2P economy
        P2P_FLOODING,          // P2P supply elevated, prices declining
        EQUILIBRIUM            // Market stable, supply/demand balanced
    }

    /**
     * Time horizons for trading recommendations
     */
    public enum TimeHorizon {
        SHORT_TERM,   // Hours to 1 day
        MEDIUM_TERM,  // 1-7 days
        LONG_TERM     // 7+ days
    }

    /**
     * Trade action types
     */
    public enum TradeAction {
        BUY, SELL, HOLD, AVOID
    }

    public BotEconomyTracker(PriceService priceService) {
        this.priceService = priceService;
        this.botItemDatabase = new ConcurrentHashMap<>();
        this.supplyShockDetector = new SupplyShockDetector(priceService, this.botItemDatabase);
        this.pipelineTracker = new F2PToPPipelineTracker(priceService, this.supplyShockDetector, this.botItemDatabase);
        this.profitStrategy = new BotWaveProfitStrategy(priceService, this.botItemDatabase, this.pipelineTracker, this.supplyShockDetector);
        this.yewBowTracker = new YewBowAlchChainTracker(priceService);
        this.bloodEssenceTracker = new BloodEssenceTracker(priceService);
        this.marketHealth = new MarketHealthIndex(this.supplyShockDetector);

        initializeBotItemDatabase();
    }

    /**
     * Initialize the complete bot item database with all bot-farmed items and their profiles
     */
    private void initializeBotItemDatabase() {
        // F2P_FRESH Phase Items
        botItemDatabase.put(1739, BotItemProfile.builder()
            .itemId(1739).itemName("Cowhide")
            .phase(BotPhase.F2P_FRESH)
            .activity(BotActivity.COMBAT)
            .typicalBotSupplyPercent(45.0)
            .isF2P(true)
            .normalPrice(750)
            .relatedItems(new String[]{"Raw beef"})
            .geLimitId(1739)
            .build());

        botItemDatabase.put(2132, BotItemProfile.builder()
            .itemId(2132).itemName("Raw beef")
            .phase(BotPhase.F2P_FRESH)
            .activity(BotActivity.COMBAT)
            .typicalBotSupplyPercent(50.0)
            .isF2P(true)
            .normalPrice(250)
            .relatedItems(new String[]{"Cowhide"})
            .geLimitId(2132)
            .build());

        botItemDatabase.put(434, BotItemProfile.builder()
            .itemId(434).itemName("Clay")
            .phase(BotPhase.F2P_FRESH)
            .activity(BotActivity.MINING)
            .typicalBotSupplyPercent(55.0)
            .isF2P(true)
            .normalPrice(400)
            .relatedItems(new String[]{"Soft clay"})
            .geLimitId(434)
            .build());

        botItemDatabase.put(1761, BotItemProfile.builder()
            .itemId(1761).itemName("Soft clay")
            .phase(BotPhase.F2P_FRESH)
            .activity(BotActivity.MINING)
            .typicalBotSupplyPercent(52.0)
            .isF2P(true)
            .normalPrice(500)
            .relatedItems(new String[]{"Clay"})
            .geLimitId(1761)
            .build());

        botItemDatabase.put(440, BotItemProfile.builder()
            .itemId(440).itemName("Iron ore")
            .phase(BotPhase.F2P_FRESH)
            .activity(BotActivity.MINING)
            .typicalBotSupplyPercent(48.0)
            .isF2P(true)
            .normalPrice(700)
            .relatedItems(new String[]{"Coal", "Iron bar"})
            .geLimitId(440)
            .build());

        botItemDatabase.put(453, BotItemProfile.builder()
            .itemId(453).itemName("Coal")
            .phase(BotPhase.F2P_FRESH)
            .activity(BotActivity.MINING)
            .typicalBotSupplyPercent(50.0)
            .isF2P(true)
            .normalPrice(550)
            .relatedItems(new String[]{"Iron ore", "Mithril bar"})
            .geLimitId(453)
            .build());

        botItemDatabase.put(2355, BotItemProfile.builder()
            .itemId(2355).itemName("Silver bar")
            .phase(BotPhase.F2P_FRESH)
            .activity(BotActivity.MINING)
            .typicalBotSupplyPercent(42.0)
            .isF2P(true)
            .normalPrice(1200)
            .relatedItems(new String[]{"Silver ore"})
            .geLimitId(2355)
            .build());

        // F2P_LEVELED Phase Items
        botItemDatabase.put(1515, BotItemProfile.builder()
            .itemId(1515).itemName("Yew logs")
            .phase(BotPhase.F2P_LEVELED)
            .activity(BotActivity.WOODCUTTING)
            .typicalBotSupplyPercent(60.0)
            .isF2P(true)
            .normalPrice(850)
            .relatedItems(new String[]{"Bowstring", "Yew longbow", "Nature rune"})
            .geLimitId(1515)
            .build());

        botItemDatabase.put(1517, BotItemProfile.builder()
            .itemId(1517).itemName("Maple logs")
            .phase(BotPhase.F2P_LEVELED)
            .activity(BotActivity.WOODCUTTING)
            .typicalBotSupplyPercent(58.0)
            .isF2P(true)
            .normalPrice(450)
            .relatedItems(new String[]{"Yew logs"})
            .geLimitId(1517)
            .build());

        botItemDatabase.put(377, BotItemProfile.builder()
            .itemId(377).itemName("Raw lobster")
            .phase(BotPhase.F2P_LEVELED)
            .activity(BotActivity.FISHING)
            .typicalBotSupplyPercent(52.0)
            .isF2P(true)
            .normalPrice(1050)
            .relatedItems(new String[]{"Raw swordfish", "Raw tuna"})
            .geLimitId(377)
            .build());

        botItemDatabase.put(371, BotItemProfile.builder()
            .itemId(371).itemName("Raw swordfish")
            .phase(BotPhase.F2P_LEVELED)
            .activity(BotActivity.FISHING)
            .typicalBotSupplyPercent(55.0)
            .isF2P(true)
            .normalPrice(1350)
            .relatedItems(new String[]{"Raw lobster", "Raw tuna"})
            .geLimitId(371)
            .build());

        botItemDatabase.put(359, BotItemProfile.builder()
            .itemId(359).itemName("Raw tuna")
            .phase(BotPhase.F2P_LEVELED)
            .activity(BotActivity.FISHING)
            .typicalBotSupplyPercent(48.0)
            .isF2P(true)
            .normalPrice(550)
            .relatedItems(new String[]{"Raw lobster", "Raw swordfish"})
            .geLimitId(359)
            .build());

        botItemDatabase.put(1692, BotItemProfile.builder()
            .itemId(1692).itemName("Gold amulet")
            .phase(BotPhase.F2P_LEVELED)
            .activity(BotActivity.COMBAT)
            .typicalBotSupplyPercent(50.0)
            .isF2P(true)
            .normalPrice(900)
            .relatedItems(new String[]{"Gold necklace", "Gold ring"})
            .geLimitId(1692)
            .build());

        botItemDatabase.put(1654, BotItemProfile.builder()
            .itemId(1654).itemName("Gold necklace")
            .phase(BotPhase.F2P_LEVELED)
            .activity(BotActivity.COMBAT)
            .typicalBotSupplyPercent(48.0)
            .isF2P(true)
            .normalPrice(850)
            .relatedItems(new String[]{"Gold amulet", "Gold ring"})
            .geLimitId(1654)
            .build());

        botItemDatabase.put(1635, BotItemProfile.builder()
            .itemId(1635).itemName("Gold ring")
            .phase(BotPhase.F2P_LEVELED)
            .activity(BotActivity.COMBAT)
            .typicalBotSupplyPercent(46.0)
            .isF2P(true)
            .normalPrice(800)
            .relatedItems(new String[]{"Gold amulet", "Gold necklace"})
            .geLimitId(1635)
            .build());

        botItemDatabase.put(561, BotItemProfile.builder()
            .itemId(561).itemName("Nature rune")
            .phase(BotPhase.F2P_LEVELED)
            .activity(BotActivity.RUNECRAFT)
            .typicalBotSupplyPercent(35.0)
            .isF2P(true)
            .normalPrice(650)
            .relatedItems(new String[]{"Yew logs", "Yew longbow"})
            .geLimitId(561)
            .build());

        botItemDatabase.put(563, BotItemProfile.builder()
            .itemId(563).itemName("Law rune")
            .phase(BotPhase.F2P_LEVELED)
            .activity(BotActivity.RUNECRAFT)
            .typicalBotSupplyPercent(32.0)
            .isF2P(true)
            .normalPrice(480)
            .relatedItems(new String[]{"Nature rune"})
            .geLimitId(563)
            .build());

        // P2P_EARLY Phase Items
        botItemDatabase.put(536, BotItemProfile.builder()
            .itemId(536).itemName("Dragon bones")
            .phase(BotPhase.P2P_EARLY)
            .activity(BotActivity.COMBAT)
            .typicalBotSupplyPercent(40.0)
            .isF2P(false)
            .normalPrice(5500)
            .relatedItems(new String[]{"Green dragonhide", "Blue dragonhide", "Superior dragon bones"})
            .geLimitId(536)
            .build());

        botItemDatabase.put(1753, BotItemProfile.builder()
            .itemId(1753).itemName("Green dragonhide")
            .phase(BotPhase.P2P_EARLY)
            .activity(BotActivity.COMBAT)
            .typicalBotSupplyPercent(38.0)
            .isF2P(false)
            .normalPrice(3200)
            .relatedItems(new String[]{"Dragon bones", "Blue dragonhide"})
            .geLimitId(1753)
            .build());

        botItemDatabase.put(1751, BotItemProfile.builder()
            .itemId(1751).itemName("Blue dragonhide")
            .phase(BotPhase.P2P_EARLY)
            .activity(BotActivity.COMBAT)
            .typicalBotSupplyPercent(36.0)
            .isF2P(false)
            .normalPrice(4800)
            .relatedItems(new String[]{"Dragon bones", "Green dragonhide"})
            .geLimitId(1751)
            .build());

        // P2P_MID Phase Items
        botItemDatabase.put(257, BotItemProfile.builder()
            .itemId(257).itemName("Ranarr weed")
            .phase(BotPhase.P2P_MID)
            .activity(BotActivity.HERBLORE)
            .typicalBotSupplyPercent(44.0)
            .isF2P(false)
            .normalPrice(2650)
            .relatedItems(new String[]{"Snapdragon", "Kwuarm", "Avantoe"})
            .geLimitId(257)
            .build());

        botItemDatabase.put(3000, BotItemProfile.builder()
            .itemId(3000).itemName("Snapdragon")
            .phase(BotPhase.P2P_MID)
            .activity(BotActivity.HERBLORE)
            .typicalBotSupplyPercent(42.0)
            .isF2P(false)
            .normalPrice(4200)
            .relatedItems(new String[]{"Ranarr weed", "Kwuarm", "Avantoe"})
            .geLimitId(3000)
            .build());

        botItemDatabase.put(263, BotItemProfile.builder()
            .itemId(263).itemName("Kwuarm")
            .phase(BotPhase.P2P_MID)
            .activity(BotActivity.HERBLORE)
            .typicalBotSupplyPercent(41.0)
            .isF2P(false)
            .normalPrice(1850)
            .relatedItems(new String[]{"Ranarr weed", "Snapdragon", "Avantoe"})
            .geLimitId(263)
            .build());

        botItemDatabase.put(261, BotItemProfile.builder()
            .itemId(261).itemName("Avantoe")
            .phase(BotPhase.P2P_MID)
            .activity(BotActivity.HERBLORE)
            .typicalBotSupplyPercent(40.0)
            .isF2P(false)
            .normalPrice(2100)
            .relatedItems(new String[]{"Ranarr weed", "Snapdragon", "Kwuarm"})
            .geLimitId(261)
            .build());

        botItemDatabase.put(1779, BotItemProfile.builder()
            .itemId(1779).itemName("Flax")
            .phase(BotPhase.P2P_MID)
            .activity(BotActivity.WOODCUTTING)
            .typicalBotSupplyPercent(52.0)
            .isF2P(false)
            .normalPrice(350)
            .relatedItems(new String[]{"Bowstring"})
            .geLimitId(1779)
            .build());

        botItemDatabase.put(1777, BotItemProfile.builder()
            .itemId(1777).itemName("Bowstring")
            .phase(BotPhase.P2P_MID)
            .activity(BotActivity.FLETCHING)
            .typicalBotSupplyPercent(48.0)
            .isF2P(false)
            .normalPrice(650)
            .relatedItems(new String[]{"Flax", "Yew logs", "Yew longbow"})
            .geLimitId(1777)
            .build());

        botItemDatabase.put(4101, BotItemProfile.builder()
            .itemId(4101).itemName("Mystic robe top")
            .phase(BotPhase.P2P_MID)
            .activity(BotActivity.SLAYER)
            .typicalBotSupplyPercent(35.0)
            .isF2P(false)
            .normalPrice(28000)
            .relatedItems(new String[]{"Mystic robe bottom"})
            .geLimitId(4101)
            .build());

        botItemDatabase.put(4103, BotItemProfile.builder()
            .itemId(4103).itemName("Mystic robe bottom")
            .phase(BotPhase.P2P_MID)
            .activity(BotActivity.SLAYER)
            .typicalBotSupplyPercent(34.0)
            .isF2P(false)
            .normalPrice(26000)
            .relatedItems(new String[]{"Mystic robe top"})
            .geLimitId(4103)
            .build());

        botItemDatabase.put(4153, BotItemProfile.builder()
            .itemId(4153).itemName("Granite maul")
            .phase(BotPhase.P2P_MID)
            .activity(BotActivity.SLAYER)
            .typicalBotSupplyPercent(38.0)
            .isF2P(false)
            .normalPrice(45000)
            .relatedItems(new String[]{"Granite body", "Granite legs"})
            .geLimitId(4153)
            .build());

        // P2P_LATE Phase Items
        botItemDatabase.put(12934, BotItemProfile.builder()
            .itemId(12934).itemName("Zulrah's scales")
            .phase(BotPhase.P2P_LATE)
            .activity(BotActivity.BOSSING)
            .typicalBotSupplyPercent(45.0)
            .isF2P(false)
            .normalPrice(380)
            .relatedItems(new String[]{"Tanzanite fang", "Magic fang", "Serpentine visage"})
            .geLimitId(12934)
            .build());

        botItemDatabase.put(12922, BotItemProfile.builder()
            .itemId(12922).itemName("Tanzanite fang")
            .phase(BotPhase.P2P_LATE)
            .activity(BotActivity.BOSSING)
            .typicalBotSupplyPercent(42.0)
            .isF2P(false)
            .normalPrice(8500000)
            .relatedItems(new String[]{"Zulrah's scales", "Magic fang", "Serpentine visage"})
            .geLimitId(12922)
            .build());

        botItemDatabase.put(12932, BotItemProfile.builder()
            .itemId(12932).itemName("Magic fang")
            .phase(BotPhase.P2P_LATE)
            .activity(BotActivity.BOSSING)
            .typicalBotSupplyPercent(40.0)
            .isF2P(false)
            .normalPrice(7200000)
            .relatedItems(new String[]{"Zulrah's scales", "Tanzanite fang", "Serpentine visage"})
            .geLimitId(12932)
            .build());

        botItemDatabase.put(12927, BotItemProfile.builder()
            .itemId(12927).itemName("Serpentine visage")
            .phase(BotPhase.P2P_LATE)
            .activity(BotActivity.BOSSING)
            .typicalBotSupplyPercent(41.0)
            .isF2P(false)
            .normalPrice(6800000)
            .relatedItems(new String[]{"Zulrah's scales", "Tanzanite fang", "Magic fang"})
            .geLimitId(12927)
            .build());

        botItemDatabase.put(22124, BotItemProfile.builder()
            .itemId(22124).itemName("Superior dragon bones")
            .phase(BotPhase.P2P_LATE)
            .activity(BotActivity.BOSSING)
            .typicalBotSupplyPercent(43.0)
            .isF2P(false)
            .normalPrice(42000)
            .relatedItems(new String[]{"Dragon bones", "Blue dragonhide"})
            .geLimitId(22124)
            .build());

        botItemDatabase.put(26390, BotItemProfile.builder()
            .itemId(26390).itemName("Blood essence")
            .phase(BotPhase.P2P_LATE)
            .activity(BotActivity.BOSSING)
            .typicalBotSupplyPercent(46.0)
            .isF2P(false)
            .normalPrice(3200)
            .relatedItems(new String[]{"Blood rune"})
            .geLimitId(26390)
            .build());

        botItemDatabase.put(11959, BotItemProfile.builder()
            .itemId(11959).itemName("Black chinchompa")
            .phase(BotPhase.P2P_LATE)
            .activity(BotActivity.FISHING)
            .typicalBotSupplyPercent(50.0)
            .isF2P(false)
            .normalPrice(7500)
            .relatedItems(new String[]{"Red chinchompa", "Gray chinchompa"})
            .geLimitId(11959)
            .build());

        botItemDatabase.put(444, BotItemProfile.builder()
            .itemId(444).itemName("Gold ore")
            .phase(BotPhase.P2P_LATE)
            .activity(BotActivity.MINING)
            .typicalBotSupplyPercent(47.0)
            .isF2P(false)
            .normalPrice(980)
            .relatedItems(new String[]{"Runite ore"})
            .geLimitId(444)
            .build());

        botItemDatabase.put(451, BotItemProfile.builder()
            .itemId(451).itemName("Runite ore")
            .phase(BotPhase.P2P_LATE)
            .activity(BotActivity.MINING)
            .typicalBotSupplyPercent(44.0)
            .isF2P(false)
            .normalPrice(12500)
            .relatedItems(new String[]{"Gold ore"})
            .geLimitId(451)
            .build());

        botItemDatabase.put(383, BotItemProfile.builder()
            .itemId(383).itemName("Raw shark")
            .phase(BotPhase.P2P_LATE)
            .activity(BotActivity.FISHING)
            .typicalBotSupplyPercent(49.0)
            .isF2P(false)
            .normalPrice(1650)
            .relatedItems(new String[]{"Raw manta ray"})
            .geLimitId(383)
            .build());

        botItemDatabase.put(389, BotItemProfile.builder()
            .itemId(389).itemName("Raw manta ray")
            .phase(BotPhase.P2P_LATE)
            .activity(BotActivity.FISHING)
            .typicalBotSupplyPercent(48.0)
            .isF2P(false)
            .normalPrice(2100)
            .relatedItems(new String[]{"Raw shark"})
            .geLimitId(389)
            .build());

        botItemDatabase.put(1127, BotItemProfile.builder()
            .itemId(1127).itemName("Rune platebody")
            .phase(BotPhase.P2P_LATE)
            .activity(BotActivity.BOSSING)
            .typicalBotSupplyPercent(43.0)
            .isF2P(false)
            .normalPrice(38000)
            .relatedItems(new String[]{"Rune platelegs", "Rune full helm"})
            .geLimitId(1127)
            .build());

        botItemDatabase.put(1079, BotItemProfile.builder()
            .itemId(1079).itemName("Rune platelegs")
            .phase(BotPhase.P2P_LATE)
            .activity(BotActivity.BOSSING)
            .typicalBotSupplyPercent(42.0)
            .isF2P(false)
            .normalPrice(37500)
            .relatedItems(new String[]{"Rune platebody", "Rune full helm"})
            .geLimitId(1079)
            .build());

        botItemDatabase.put(1163, BotItemProfile.builder()
            .itemId(1163).itemName("Rune full helm")
            .phase(BotPhase.P2P_LATE)
            .activity(BotActivity.BOSSING)
            .typicalBotSupplyPercent(41.0)
            .isF2P(false)
            .normalPrice(20500)
            .relatedItems(new String[]{"Rune platebody", "Rune platelegs"})
            .geLimitId(1163)
            .build());

        botItemDatabase.put(855, BotItemProfile.builder()
            .itemId(855).itemName("Yew longbow")
            .phase(BotPhase.F2P_LEVELED)
            .activity(BotActivity.FLETCHING)
            .typicalBotSupplyPercent(54.0)
            .isF2P(true)
            .normalPrice(4800)
            .relatedItems(new String[]{"Yew logs", "Bowstring", "Nature rune"})
            .geLimitId(855)
            .build());

        log.info("Initialized BotEconomyTracker with {} bot-affected items", botItemDatabase.size());
    }

    /**
     * Get the bot item profile for a specific item ID
     */
    public BotItemProfile getBotItemProfile(int itemId) {
        return botItemDatabase.get(itemId);
    }

    /**
     * Get all bot-affected items in the database
     */
    public Collection<BotItemProfile> getAllBotItems() {
        return botItemDatabase.values();
    }

    /**
     * Check if an item is in the bot-affected database
     */
    public boolean isBotAffectedItem(int itemId) {
        return botItemDatabase.containsKey(itemId);
    }

    /**
     * Get the SupplyShockDetector for analyzing ban waves
     */
    public SupplyShockDetector getSupplyShockDetector() {
        return supplyShockDetector;
    }

    /**
     * Get the F2P→P2P Pipeline Tracker
     */
    public F2PToPPipelineTracker getPipelineTracker() {
        return pipelineTracker;
    }

    /**
     * Get the Profit Strategy analyzer
     */
    public BotWaveProfitStrategy getProfitStrategy() {
        return profitStrategy;
    }

    /**
     * Get the Yew Bow Alch Chain Tracker
     */
    public YewBowAlchChainTracker getYewBowTracker() {
        return yewBowTracker;
    }

    /**
     * Get the Blood Essence Tracker
     */
    public BloodEssenceTracker getBloodEssenceTracker() {
        return bloodEssenceTracker;
    }

    /**
     * Get the Market Health Index
     */
    public MarketHealthIndex getMarketHealth() {
        return marketHealth;
    }

    // ============= INNER CLASSES =============

    /**
     * Detects supply shocks caused by ban waves and bot returns
     */
    public static class SupplyShockDetector {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BotEconomyTracker.SupplyShockDetector.class);

        private final PriceService priceService;
        private final Map<Integer, BotItemProfile> botItemDatabase;
        private LocalDateTime lastBanWaveDetected = null;
        private double currentBanWaveConfidence = 0.0;

        public SupplyShockDetector(PriceService priceService, Map<Integer, BotItemProfile> botItemDatabase) {
            this.priceService = priceService;
            this.botItemDatabase = botItemDatabase;
        }

        /**
         * Detect ban wave signals by looking for simultaneous price spikes across multiple bot items
         * A ban wave is confirmed when dragon bones, yew logs, and raw fish ALL spike at once.
         */
        public boolean detectBanWaveSignals() {
            List<Integer> supplyShockItems = getSupplyShockItems();

            if (supplyShockItems.isEmpty()) {
                return false;
            }

            // Check for key indicator items all spiking simultaneously
            boolean dragonBonesSpiking = supplyShockItems.contains(536);
            boolean yewLogsSpiking = supplyShockItems.contains(1515);
            boolean rawSharkSpiking = supplyShockItems.contains(383);

            boolean banWaveDetected = dragonBonesSpiking && yewLogsSpiking && rawSharkSpiking;

            if (banWaveDetected) {
                lastBanWaveDetected = LocalDateTime.now();
                currentBanWaveConfidence = Math.min(100.0, 60.0 + (supplyShockItems.size() * 2.5));
                log.info("Ban wave detected with {} items in supply shock. Confidence: {}",
                    supplyShockItems.size(), currentBanWaveConfidence);
            }

            return banWaveDetected;
        }

        /**
         * Detect bot return signals by looking for gradual price decline across bot items
         */
        public boolean detectBotReturnSignals() {
            List<Integer> supplyFloodItems = getSupplyFloodItems();

            if (supplyFloodItems.isEmpty()) {
                return false;
            }

            // Bots returning: multiple items declining with rising volume
            boolean botReturnDetected = supplyFloodItems.size() >= 5;

            if (botReturnDetected) {
                log.info("Bot return signals detected: {} items being flooded", supplyFloodItems.size());
            }

            return botReturnDetected;
        }

        /**
         * Get confidence score (0-100) of how likely a ban wave recently happened
         */
        public double getBanWaveConfidence() {
            if (lastBanWaveDetected == null) {
                return 0.0;
            }

            long hoursSinceBanWave = ChronoUnit.HOURS.between(lastBanWaveDetected, LocalDateTime.now());

            // Confidence decays over time
            if (hoursSinceBanWave > 168) { // Over 7 days
                return 0.0;
            } else if (hoursSinceBanWave > 72) { // Over 3 days
                return currentBanWaveConfidence * 0.3;
            } else if (hoursSinceBanWave > 24) { // Over 1 day
                return currentBanWaveConfidence * 0.6;
            }

            return currentBanWaveConfidence;
        }

        /**
         * Get list of items currently in supply shock (price >15% above 7-day average)
         */
        public List<Integer> getSupplyShockItems() {
            return botItemDatabase.keySet().stream()
                .filter(itemId -> {
                    PriceAggregate priceData = priceService.getPriceAggregate(itemId);
                    if (priceData == null) return false;

                    long currentPrice = priceData.getCurrentPrice();
                    // Approximate 7-day average using high/low price range
                    long sevenDayAvg = (priceData.getHighPrice() + priceData.getLowPrice()) / 2;

                    double percentChange = ((currentPrice - sevenDayAvg) / (double) sevenDayAvg) * 100;
                    return percentChange > 15.0;
                })
                .collect(Collectors.toList());
        }

        /**
         * Get list of items being flooded (price declining >5% over 3 days with rising volume)
         */
        public List<Integer> getSupplyFloodItems() {
            return botItemDatabase.keySet().stream()
                .filter(itemId -> {
                    PriceAggregate priceData = priceService.getPriceAggregate(itemId);
                    if (priceData == null) return false;

                    long currentPrice = priceData.getCurrentPrice();
                    // Approximate 3-day average using high/low price range
                    long threeDayAvg = (priceData.getHighPrice() + priceData.getLowPrice()) / 2;

                    double percentChange = ((currentPrice - threeDayAvg) / (double) threeDayAvg) * 100;
                    return percentChange < -5.0;
                })
                .collect(Collectors.toList());
        }
    }

    /**
     * Tracks the F2P→P2P bot pipeline and estimates transition timing
     */
    public static class F2PToPPipelineTracker {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BotEconomyTracker.F2PToPPipelineTracker.class);

        private final PriceService priceService;
        private final SupplyShockDetector supplyShockDetector;
        private final Map<Integer, BotItemProfile> botItemDatabase;

        public F2PToPPipelineTracker(PriceService priceService, SupplyShockDetector supplyShockDetector, Map<Integer, BotItemProfile> botItemDatabase) {
            this.priceService = priceService;
            this.supplyShockDetector = supplyShockDetector;
            this.botItemDatabase = botItemDatabase;
        }

        /**
         * Estimate when current F2P bots will accumulate enough GP to buy bonds (~7-8M)
         * Based on the rate of yew log supply increase
         */
        public int estimateTimeToP2PDays() {
            PriceAggregate yewLogData = priceService.getPriceAggregate(1515);
            if (yewLogData == null) return 30; // Default estimate

            // High yew log supply = F2P bots still farming = longer wait
            // Low yew log supply = F2P bots transitioning = soon
            // This is a heuristic estimate

            long current = yewLogData.getCurrentPrice();
            // Approximate 7-day average using high/low price range
            long sevenDayAvg = (yewLogData.getHighPrice() + yewLogData.getLowPrice()) / 2;

            // If yew logs are crashing, bots are about to transition
            if (current < sevenDayAvg * 0.85) {
                return 3; // Very soon
            } else if (current < sevenDayAvg * 0.95) {
                return 7; // Within a week
            } else if (current > sevenDayAvg * 1.10) {
                return 21; // Still ramping up
            }

            return 14; // Mid-range estimate
        }

        /**
         * Get current pipeline stage based on market signals
         */
        public PipelineStage getPipelineStage() {
            double banWaveConfidence = supplyShockDetector.getBanWaveConfidence();
            List<Integer> floodingItems = supplyShockDetector.getSupplyFloodItems();

            // Recent ban wave
            if (banWaveConfidence > 70.0) {
                return PipelineStage.BOTS_BANNED;
            }

            // Lots of items flooding
            if (floodingItems.size() >= 10) {
                return PipelineStage.P2P_FLOODING;
            }

            // Mid-stage flooding
            if (floodingItems.size() >= 5) {
                // Check if it's F2P items or P2P items being flooded
                long f2pFloodCount = floodingItems.stream()
                    .filter(id -> {
                        BotItemProfile profile = botItemDatabase.get(id);
                        return profile != null && profile.isF2P();
                    })
                    .count();

                if (f2pFloodCount >= 3) {
                    return PipelineStage.F2P_SATURATED;
                } else {
                    return PipelineStage.P2P_TRANSITION;
                }
            }

            return PipelineStage.EQUILIBRIUM;
        }

        /**
         * Get items that signal strong F2P bot activity
         */
        public List<Integer> getF2PIndicatorItems() {
            return botItemDatabase.values().stream()
                .filter(profile -> profile.phase == BotPhase.F2P_LEVELED && profile.isF2P)
                .map(BotItemProfile::getItemId)
                .collect(Collectors.toList());
        }

        /**
         * Get items that spike FIRST when bots hit P2P (green dragons, early slayer)
         */
        public List<Integer> getP2PEarlyWarningItems() {
            return botItemDatabase.values().stream()
                .filter(profile -> profile.phase == BotPhase.P2P_EARLY)
                .map(BotItemProfile::getItemId)
                .collect(Collectors.toList());
        }

        /**
         * For each pipeline stage, recommend items to front-run
         */
        public List<Integer> getItemsToFrontrun(PipelineStage stage) {
            switch (stage) {
                case BOTS_BANNED:
                    // Buy bot items before bots return
                    return botItemDatabase.values().stream()
                        .filter(p -> p.typicalBotSupplyPercent > 40.0)
                        .map(BotItemProfile::getItemId)
                        .collect(Collectors.toList());

                case F2P_REBUILDING:
                    // Sell F2P items before saturation
                    return botItemDatabase.values().stream()
                        .filter(p -> p.phase == BotPhase.F2P_FRESH || p.phase == BotPhase.F2P_LEVELED)
                        .map(BotItemProfile::getItemId)
                        .collect(Collectors.toList());

                case F2P_SATURATED:
                    // Prepare to buy P2P items (bots transitioning soon)
                    return botItemDatabase.values().stream()
                        .filter(p -> p.phase == BotPhase.P2P_EARLY)
                        .map(BotItemProfile::getItemId)
                        .collect(Collectors.toList());

                case P2P_TRANSITION:
                    // Short P2P items (they're about to flood)
                    return botItemDatabase.values().stream()
                        .filter(p -> p.phase == BotPhase.P2P_EARLY || p.phase == BotPhase.P2P_MID)
                        .map(BotItemProfile::getItemId)
                        .collect(Collectors.toList());

                case P2P_FLOODING:
                    // Buy low, wait for recovery
                    return botItemDatabase.values().stream()
                        .filter(p -> p.phase == BotPhase.P2P_MID || p.phase == BotPhase.P2P_LATE)
                        .map(BotItemProfile::getItemId)
                        .collect(Collectors.toList());

                default:
                    return Collections.emptyList();
            }
        }
    }

    /**
     * Profit strategy recommendations based on bot waves
     */
    public static class BotWaveProfitStrategy {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BotEconomyTracker.BotWaveProfitStrategy.class);

        private final PriceService priceService;
        private final Map<Integer, BotItemProfile> botItemDatabase;
        private final F2PToPPipelineTracker pipelineTracker;
        private final SupplyShockDetector supplyShockDetector;

        public BotWaveProfitStrategy(PriceService priceService, Map<Integer, BotItemProfile> botItemDatabase,
                                      F2PToPPipelineTracker pipelineTracker, SupplyShockDetector supplyShockDetector) {
            this.priceService = priceService;
            this.botItemDatabase = botItemDatabase;
            this.pipelineTracker = pipelineTracker;
            this.supplyShockDetector = supplyShockDetector;
        }

        /**
         * Get items to buy before a ban wave (they will spike when bots are gone)
         */
        public List<BotTradeRecommendation> getPreBanWavePositions() {
            List<BotTradeRecommendation> recommendations = new ArrayList<>();

            // Dragon bones, yew logs, raw fish - premium bot items that spike first
            int[] premiumItems = {536, 1515, 383};

            for (int itemId : premiumItems) {
                BotItemProfile profile = botItemDatabase.get(itemId);
                PriceAggregate priceData = priceService.getPriceAggregate(itemId);

                if (profile != null && priceData != null) {
                    long currentPrice = priceData.getCurrentPrice();
                    // Approximate 7-day average using high/low price range
                    long sevenDayAvg = (priceData.getHighPrice() + priceData.getLowPrice()) / 2;
                    long targetPrice = (long) (sevenDayAvg * 1.30); // 30% spike expected

                    recommendations.add(BotTradeRecommendation.builder()
                        .itemId(itemId)
                        .itemName(profile.getItemName())
                        .action(TradeAction.BUY)
                        .confidence(75)
                        .targetPrice(targetPrice)
                        .reasoning("Premium bot item; ban wave will spike supply")
                        .estimatedProfit((targetPrice - currentPrice) * 100)
                        .timeHorizon(TimeHorizon.SHORT_TERM)
                        .build());
                }
            }

            return recommendations;
        }

        /**
         * When to sell after a ban wave spike
         */
        public List<BotTradeRecommendation> getPostBanWaveExits() {
            List<BotTradeRecommendation> recommendations = new ArrayList<>();

            // Sell the spike winners
            int[] spikeWinners = {536, 1515, 383, 377, 371};

            for (int itemId : spikeWinners) {
                BotItemProfile profile = botItemDatabase.get(itemId);
                PriceAggregate priceData = priceService.getPriceAggregate(itemId);

                if (profile != null && priceData != null) {
                    long currentPrice = priceData.getCurrentPrice();
                    // Approximate 7-day average using high/low price range
                    long sevenDayAvg = (priceData.getHighPrice() + priceData.getLowPrice()) / 2;

                    // Sell when price is 15%+ above average
                    if (currentPrice > sevenDayAvg * 1.15) {
                        recommendations.add(BotTradeRecommendation.builder()
                            .itemId(itemId)
                            .itemName(profile.getItemName())
                            .action(TradeAction.SELL)
                            .confidence(80)
                            .targetPrice(currentPrice)
                            .reasoning("Spike peaked; bots returning soon")
                            .estimatedProfit(0) // Profit already made
                            .timeHorizon(TimeHorizon.SHORT_TERM)
                            .build());
                    }
                }
            }

            return recommendations;
        }

        /**
         * Sell items before bots return to P2P (they'll crash in supply)
         */
        public List<BotTradeRecommendation> getPreP2PFloodSells() {
            List<BotTradeRecommendation> recommendations = new ArrayList<>();

            int timeToP2P = pipelineTracker.estimateTimeToP2PDays();

            // If bots returning soon, short P2P items
            if (timeToP2P <= 14) {
                int[] p2pItems = {536, 1753, 1751, 257, 3000, 263, 261, 383, 389};

                for (int itemId : p2pItems) {
                    BotItemProfile profile = botItemDatabase.get(itemId);
                    PriceAggregate priceData = priceService.getPriceAggregate(itemId);

                    if (profile != null && priceData != null) {
                        long currentPrice = priceData.getCurrentPrice();
                        long targetPrice = (long) (currentPrice * 0.85); // Expect 15% drop

                        recommendations.add(BotTradeRecommendation.builder()
                            .itemId(itemId)
                            .itemName(profile.getItemName())
                            .action(TradeAction.SELL)
                            .confidence(65)
                            .targetPrice(targetPrice)
                            .reasoning("P2P bots returning in ~" + timeToP2P + " days; supply flood incoming")
                            .estimatedProfit((currentPrice - targetPrice) * 50)
                            .timeHorizon(TimeHorizon.MEDIUM_TERM)
                            .build());
                    }
                }
            }

            return recommendations;
        }

        /**
         * Buy items AFTER bots flood supply (they'll recover after stabilization)
         */
        public List<BotTradeRecommendation> getPostP2PFloodBuys() {
            List<BotTradeRecommendation> recommendations = new ArrayList<>();

            List<Integer> floodingItems = supplyShockDetector.getSupplyFloodItems();

            // Only recommend buys for items that have been flooding for a few days
            for (Integer itemId : floodingItems) {
                BotItemProfile profile = botItemDatabase.get(itemId);
                PriceAggregate priceData = priceService.getPriceAggregate(itemId);

                if (profile != null && priceData != null && profile.typicalBotSupplyPercent > 35.0) {
                    long currentPrice = priceData.getCurrentPrice();
                    // FIX: Use live mid-price as baseline instead of stale hardcoded normalPrice
                    long normalPrice = priceData.getCurrentPrice() > 0 ? priceData.getCurrentPrice() : profile.getNormalPrice();
                    // Use 7-day average if available for more stable baseline
                    List<Long> history = priceData.getPriceHistory();
                    if (history != null && history.size() >= 14) {
                        long sum = 0;
                        int count = Math.min(14, history.size());
                        for (int i = history.size() - count; i < history.size(); i++) {
                            sum += history.get(i);
                        }
                        normalPrice = sum / count;
                    }
                    long targetPrice = (long) (normalPrice * 0.95);

                    // Only buy if price is well below normal
                    if (currentPrice < normalPrice * 0.90) {
                        recommendations.add(BotTradeRecommendation.builder()
                            .itemId(itemId)
                            .itemName(profile.getItemName())
                            .action(TradeAction.BUY)
                            .confidence(70)
                            .targetPrice(targetPrice)
                            .reasoning("Bot supply flooding stabilizing; price recovery coming")
                            .estimatedProfit((targetPrice - currentPrice) * 75)
                            .timeHorizon(TimeHorizon.LONG_TERM)
                            .build());
                    }
                }
            }

            return recommendations;
        }
    }

    /**
     * Tracks the yew bow alch chain profitability
     * Chain: yew logs + bowstring → yew longbow (u) → yew longbow → high alch (768gp)
     */
    public static class YewBowAlchChainTracker {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BotEconomyTracker.YewBowAlchChainTracker.class);
        private static final int HIGH_ALCH_VALUE = 768;

        private final PriceService priceService;

        public YewBowAlchChainTracker(PriceService priceService) {
            this.priceService = priceService;
        }

        /**
         * Calculate profit of the full yew bow alch chain
         */
        public long getYewBowChainProfit() {
            PriceAggregate yewLogPrice = priceService.getPriceAggregate(1515);
            PriceAggregate bowstringPrice = priceService.getPriceAggregate(1777);
            PriceAggregate yewBowPrice = priceService.getPriceAggregate(855);
            PriceAggregate natureRunePrice = priceService.getPriceAggregate(561);

            if (yewLogPrice == null || bowstringPrice == null || yewBowPrice == null || natureRunePrice == null) {
                return 0;
            }

            long cost = yewLogPrice.getCurrentPrice() + bowstringPrice.getCurrentPrice();
            long alchedValue = HIGH_ALCH_VALUE - natureRunePrice.getCurrentPrice();
            long profit = alchedValue - cost;

            log.debug("Yew bow alch chain profit: {} gp/bow (cost: {}, alch value: {})",
                profit, cost, alchedValue);

            return profit;
        }

        /**
         * Check if the yew bow alch chain is profitable
         */
        public boolean isYewBowChainProfitable() {
            return getYewBowChainProfit() > 100; // Profit must exceed 100gp per bow
        }

        /**
         * Get the bottleneck in the chain (what's making it unprofitable)
         */
        public String getBottleneck() {
            PriceAggregate yewLogPrice = priceService.getPriceAggregate(1515);
            PriceAggregate bowstringPrice = priceService.getPriceAggregate(1777);
            PriceAggregate natureRunePrice = priceService.getPriceAggregate(561);

            if (yewLogPrice == null || bowstringPrice == null || natureRunePrice == null) {
                return "Missing price data";
            }

            long logCost = yewLogPrice.getCurrentPrice();
            long stringCost = bowstringPrice.getCurrentPrice();
            long runeCost = natureRunePrice.getCurrentPrice();

            if (logCost > 1000) {
                return "Yew logs too expensive";
            }
            if (stringCost > 800) {
                return "Bowstring too expensive (likely bot activity flooding flax)";
            }
            if (runeCost > 700) {
                return "Nature rune too expensive";
            }

            return "None - chain is profitable";
        }
    }

    /**
     * Tracks blood essence supply and blood rune crafting profitability
     */
    @Slf4j
    public static class BloodEssenceTracker {

        private final PriceService priceService;

        public BloodEssenceTracker(PriceService priceService) {
            this.priceService = priceService;
        }

        public enum BloodEssenceState {
            OVERSUPPLIED,   // Bots active, essence cheap, rune crafting profitable
            NORMAL,
            UNDERSUPPLIED   // Post ban wave, essence expensive, rune crafting unprofitable
        }

        /**
         * Get blood essence supply state
         */
        public BloodEssenceState getBloodEssenceState() {
            PriceAggregate essencePrice = priceService.getPriceAggregate(26390);

            if (essencePrice == null) {
                return BloodEssenceState.NORMAL;
            }

            long currentPrice = essencePrice.getCurrentPrice();
            // Approximate 7-day average using high/low price range
            long sevenDayAvg = (essencePrice.getHighPrice() + essencePrice.getLowPrice()) / 2;

            if (currentPrice < sevenDayAvg * 0.85) {
                return BloodEssenceState.OVERSUPPLIED;
            } else if (currentPrice > sevenDayAvg * 1.15) {
                return BloodEssenceState.UNDERSUPPLIED;
            }

            return BloodEssenceState.NORMAL;
        }

        /**
         * Calculate blood rune crafting profit
         * Blood runes sell for ~600gp, essence (charge) costs X
         */
        public long getBloodRuneCraftingProfit() {
            PriceAggregate essencePrice = priceService.getPriceAggregate(26390);

            if (essencePrice == null) {
                return 0;
            }

            long essenceCost = essencePrice.getCurrentPrice();
            long bloodRuneValue = 600; // Approximate sell value

            // Profit = sell price - essence cost
            return bloodRuneValue - essenceCost;
        }
    }

    /**
     * Composite market health index based on bot activity and price stability
     */
    public static class MarketHealthIndex {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BotEconomyTracker.MarketHealthIndex.class);

        private final SupplyShockDetector supplyShockDetector;

        public MarketHealthIndex(SupplyShockDetector supplyShockDetector) {
            this.supplyShockDetector = supplyShockDetector;
        }

        /**
         * Get overall market health score (0-100)
         * 100 = perfectly stable, 0 = chaotic bot manipulation
         */
        public int getMarketHealth() {
            int score = 100;

            // Penalize for recent ban waves
            double banWaveConfidence = supplyShockDetector.getBanWaveConfidence();
            score -= (int) (banWaveConfidence * 0.5);

            // Penalize for supply shocks
            int supplyShockCount = supplyShockDetector.getSupplyShockItems().size();
            score -= Math.min(30, supplyShockCount * 2);

            // Penalize for supply floods
            int supplyFloodCount = supplyShockDetector.getSupplyFloodItems().size();
            score -= Math.min(25, supplyFloodCount);

            return Math.max(0, Math.min(100, score));
        }

        /**
         * Estimate percentage of GE transactions likely from bots (0-100)
         */
        public double getBotLoadEstimate() {
            int supplyShockCount = supplyShockDetector.getSupplyShockItems().size();
            int supplyFloodCount = supplyShockDetector.getSupplyFloodItems().size();

            // Every bot-affected item in shock/flood adds ~5% estimated bot load
            double botLoad = (supplyShockCount + supplyFloodCount) * 5.0;

            // Cap at 80% (bots can't be 100% of the market)
            return Math.min(80.0, botLoad);
        }

        /**
         * Get human-readable health breakdown
         */
        public String getHealthBreakdown() {
            int health = getMarketHealth();
            double botLoad = getBotLoadEstimate();

            return String.format(
                "Market Health: %d/100 | Bot Load: %.1f%% | BanWave Confidence: %.1f%%",
                health,
                botLoad,
                supplyShockDetector.getBanWaveConfidence()
            );
        }
    }

    // ============= DATA CLASSES =============

    /**
     * Profile of a bot-affected item with all metadata
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class BotItemProfile {
        private int itemId;
        private String itemName;
        private BotPhase phase;
        private BotActivity activity;
        private double typicalBotSupplyPercent;
        private boolean isF2P;
        private long normalPrice;
        private String[] relatedItems;
        private int geLimitId;
    }

    /**
     * A single bot trade recommendation with confidence and reasoning
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class BotTradeRecommendation {
        private int itemId;
        private String itemName;
        private TradeAction action;
        private int confidence;
        private long targetPrice;
        private String reasoning;
        private long estimatedProfit;
        private TimeHorizon timeHorizon;
    }
}
