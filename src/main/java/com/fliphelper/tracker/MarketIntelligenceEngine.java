package com.fliphelper.tracker;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.model.PriceData;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// Advanced market analysis from Wiki API timeseries.
@Slf4j
public class MarketIntelligenceEngine {

    private final PriceService priceService;
    private final TimeSeriesAnalyzer timeSeriesAnalyzer;
    private final UpdateImpactAnalyzer updateImpactAnalyzer;
    private final CrossItemCorrelator crossItemCorrelator;
    private final LiquidityAnalyzer liquidityAnalyzer;
    private final RecipeFlipDetector recipeFlipDetector;
    private final SeasonalPatternDetector seasonalPatternDetector;
    private final ItemSinkTracker itemSinkTracker;
    private final MarketRegimeDetector marketRegimeDetector;

    public MarketIntelligenceEngine(PriceService priceService) {
        this.priceService = priceService;
        this.timeSeriesAnalyzer = new TimeSeriesAnalyzer(priceService);
        this.updateImpactAnalyzer = new UpdateImpactAnalyzer(priceService);
        this.crossItemCorrelator = new CrossItemCorrelator(priceService);
        this.liquidityAnalyzer = new LiquidityAnalyzer(priceService);
        this.recipeFlipDetector = new RecipeFlipDetector(priceService);
        this.seasonalPatternDetector = new SeasonalPatternDetector(priceService);
        this.itemSinkTracker = new ItemSinkTracker(priceService);
        this.marketRegimeDetector = new MarketRegimeDetector(priceService);
    }

    
    public MarketReport getFullAnalysis(int itemId) {
        try {
            PriceAggregate aggregate = priceService.getPriceAggregate(itemId);
            if (aggregate == null) {
                log.warn("No price data available for item {}", itemId);
                return null;
            }

            List<Long> prices = aggregate.getPriceHistory();
            if (prices == null || prices.isEmpty()) {
                log.warn("No price history available for item {}", itemId);
                return null;
            }

            long currentPrice = aggregate.getCurrentPrice();
            long volume1h = aggregate.getHourlyVolume();
            long avgVolume24h = aggregate.getEstimatedDailyVolume();
            long high = aggregate.getHighPrice();
            long low = aggregate.getLowPrice();

            // Time series analysis
            // FIXME: VWAP calculation needs volume per price point (wiki API doesn't provide this)
            double ema14 = timeSeriesAnalyzer.calculateEMA(prices, 14);
            double ema50 = timeSeriesAnalyzer.calculateEMA(prices, 50);
            int rsi = timeSeriesAnalyzer.calculateRSI(prices, 14);
            BollingerBands bollingerBands = timeSeriesAnalyzer.calculateBollingerBands(prices, 20);
            MACD macd = timeSeriesAnalyzer.calculateMACD(prices);
            double vwap = timeSeriesAnalyzer.calculateVWAP(prices, new ArrayList<>()); // Volume data not available

            // Mean reversion and momentum
            MeanReversionSignal meanReversionSignal = timeSeriesAnalyzer.detectMeanReversion(prices, 30);
            MomentumSignal momentumSignal = timeSeriesAnalyzer.detectMomentum(prices, 12, 26);

            // Market regime
            MarketRegime regime = marketRegimeDetector.detectRegime(prices);
            String optimalStrategy = marketRegimeDetector.getOptimalStrategy(regime);

            // Liquidity
            double bidAskSpread = liquidityAnalyzer.calculateBidAskSpread(high, low);
            double spreadPercent = liquidityAnalyzer.calculateBidAskSpreadPercent(high, low);
            int marketDepth = liquidityAnalyzer.calculateMarketDepth(volume1h, avgVolume24h);
            int liquidityScore = liquidityAnalyzer.getLiquidityScore(volume1h, spreadPercent);
            LiquidityClass liquidityClass = liquidityAnalyzer.classifyLiquidity(liquidityScore);

            // Update impact
            List<String> affectedByUpdates = updateImpactAnalyzer.analyzeItemUpdateSensitivity(itemId);

            // Item sink impact
            boolean isSinkTarget = itemSinkTracker.isItemSinkTarget(itemId);
            double sinkPressure = isSinkTarget ?
                itemSinkTracker.getSinkPressure(itemId, currentPrice, (long) prices.stream().mapToLong(Long::longValue).average().orElse(currentPrice)) :
                0;

            // Composite intelligence score
            double intelligenceScore = calculateCompositeScore(rsi, momentumSignal, regime,
                liquidityScore, meanReversionSignal, sinkPressure);

            return MarketReport.builder()
                .itemId(itemId)
                .currentPrice(currentPrice)
                .ema14(ema14)
                .ema50(ema50)
                .rsi(rsi)
                .bollingerBands(bollingerBands)
                .macd(macd)
                .vwap(vwap)
                .meanReversionSignal(meanReversionSignal)
                .momentumSignal(momentumSignal)
                .marketRegime(regime)
                .optimalStrategy(optimalStrategy)
                .bidAskSpread(bidAskSpread)
                .spreadPercent(spreadPercent)
                .marketDepth(marketDepth)
                .liquidityScore(liquidityScore)
                .liquidityClass(liquidityClass)
                .volume1h(volume1h)
                .avgVolume24h(avgVolume24h)
                .updateSensitivity(affectedByUpdates)
                .isSinkTarget(isSinkTarget)
                .sinkPressure(sinkPressure)
                .intelligenceScore(intelligenceScore)
                .timestamp(System.currentTimeMillis())
                .build();

        } catch (Exception e) {
            log.error("Error analyzing item {}", itemId, e);
            return null;
        }
    }

    
    public List<MarketReport> getTopOpportunities(int limit) {
        try {
            List<Integer> items = new ArrayList<>(priceService.getAllItemIds());
            return items.parallelStream()
                .map(this::getFullAnalysis)
                .filter(Objects::nonNull)
                .filter(report -> report.intelligenceScore >= 60) // Only viable opportunities
                .sorted(Comparator.comparingDouble(MarketReport::getIntelligenceScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error getting top opportunities", e);
            return Collections.emptyList();
        }
    }

    
    public MarketBriefing getMarketBriefing() {
        try {
            List<MarketReport> reports = getTopOpportunities(100);
            if (reports.isEmpty()) {
                return null;
            }

            double avgIntelligenceScore = reports.stream()
                .mapToDouble(MarketReport::getIntelligenceScore)
                .average()
                .orElse(0);

            long trendingUp = reports.stream()
                .filter(r -> r.momentumSignal.signal == Signal.BUY)
                .count();

            long trendingDown = reports.stream()
                .filter(r -> r.momentumSignal.signal == Signal.SELL)
                .count();

            double volatilityAvg = reports.stream()
                .mapToDouble(r -> r.rsi > 70 || r.rsi < 30 ? 1.0 : 0.0)
                .average()
                .orElse(0);

            List<MarketReport> topMovers = reports.stream()
                .sorted((a, b) -> Double.compare(Math.abs(b.getMomentumSignal().getStrength()), Math.abs(a.getMomentumSignal().getStrength())))
                .limit(10)
                .collect(Collectors.toList());

            return MarketBriefing.builder()
                .timestamp(System.currentTimeMillis())
                .avgIntelligenceScore(avgIntelligenceScore)
                .itemsTrendingUp(trendingUp)
                .itemsTrendingDown(trendingDown)
                .volatilityLevel(calculateVolatilityLevel(volatilityAvg))
                .topMovers(topMovers)
                .build();

        } catch (Exception e) {
            log.error("Error generating market briefing", e);
            return null;
        }
    }

    
    private double calculateCompositeScore(int rsi, MomentumSignal momentum, MarketRegime regime,
                                          int liquidityScore, MeanReversionSignal meanReversion,
                                          double sinkPressure) {
        double score = 0;

        // RSI component (0-100): neutral 50, overbought >70, oversold <30
        double rsiComponent = 100 - Math.abs(rsi - 50);
        score += rsiComponent * 0.15;

        // Momentum component (0-100)
        score += (momentum.strength * 100) * 0.25;

        // Liquidity component (0-100)
        score += liquidityScore * 0.20;

        // Mean reversion component
        score += meanReversion.zScore > 2 ? 80 :
                 meanReversion.zScore > 1 ? 60 :
                 meanReversion.zScore < -2 ? 80 :
                 meanReversion.zScore < -1 ? 60 : 40;
        score *= 0.20;

        // Regime alignment
        if (regime == MarketRegime.TRENDING_UP || regime == MarketRegime.TRENDING_DOWN) {
            score += 70 * 0.15;
        } else if (regime == MarketRegime.MEAN_REVERTING) {
            score += meanReversion.detected ? 75 : 40;
            score *= 0.15;
        } else {
            score += 50 * 0.15;
        }

        // Sink pressure bonus
        score += Math.min(sinkPressure * 10, 25);

        return Math.min(100, Math.max(0, score));
    }

    private String calculateVolatilityLevel(double volatilityAvg) {
        if (volatilityAvg > 0.7) return "EXTREME";
        if (volatilityAvg > 0.5) return "HIGH";
        if (volatilityAvg > 0.3) return "MODERATE";
        return "LOW";
    }

    // ~~~ = ~~~
    // analysis engine types
    /* = */

    
    public static class TimeSeriesAnalyzer {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketIntelligenceEngine.TimeSeriesAnalyzer.class);
        private final PriceService priceService;

        public TimeSeriesAnalyzer(PriceService priceService) {
            this.priceService = priceService;
        }

        public double calculateEMA(List<Long> prices, int period) {
            if (prices == null || prices.size() < period) {
                return prices == null || prices.isEmpty() ? 0 : prices.get(prices.size() - 1);
            }

            double multiplier = 2.0 / (period + 1);
            double ema = prices.subList(0, period).stream()
                .mapToDouble(Long::doubleValue)
                .average()
                .orElse(0);

            for (int i = period; i < prices.size(); i++) {
                ema = (prices.get(i) - ema) * multiplier + ema;
            }

            return ema;
        }

        public int calculateRSI(List<Long> prices, int period) {
            if (prices == null || prices.size() < period + 1) {
                return 50; // Neutral if insufficient data
            }

            double upSum = 0;
            double downSum = 0;

            for (int i = prices.size() - period; i < prices.size(); i++) {
                long change = prices.get(i) - prices.get(i - 1);
                if (change > 0) {
                    upSum += change;
                } else {
                    downSum += Math.abs(change);
                }
            }

            double avgGain = upSum / period;
            double avgLoss = downSum / period;

            if (avgLoss == 0) {
                return avgGain == 0 ? 50 : 100;
            }

            double rs = avgGain / avgLoss;
            return (int) (100 - (100 / (1 + rs)));
        }

        public BollingerBands calculateBollingerBands(List<Long> prices, int period) {
            if (prices == null || prices.size() < period) {
                return new BollingerBands(0, 0, 0);
            }

            List<Long> recent = prices.subList(Math.max(0, prices.size() - period), prices.size());
            double sma = recent.stream().mapToDouble(Long::doubleValue).average().orElse(0);

            double variance = recent.stream()
                .mapToDouble(p -> Math.pow(p - sma, 2))
                .average()
                .orElse(0);
            double stdDev = Math.sqrt(variance);

            return new BollingerBands(
                sma + (2 * stdDev), // upper
                sma,                // middle
                sma - (2 * stdDev)  // lower
            );
        }

        public MACD calculateMACD(List<Long> prices) {
            if (prices == null || prices.size() < 26) {
                return new MACD(0, 0, 0);
            }

            double ema12 = calculateEMA(prices, 12);
            double ema26 = calculateEMA(prices, 26);
            double macdLine = ema12 - ema26;

            // Signal line is EMA of MACD
            List<Long> macdValues = new ArrayList<>();
            for (int i = 26; i < prices.size(); i++) {
                double e12 = calculateEMA(prices.subList(0, i + 1), 12);
                double e26 = calculateEMA(prices.subList(0, i + 1), 26);
                macdValues.add(Math.round(e12 - e26));
            }

            double signalLine = calculateEMA(macdValues, 9);
            double histogram = macdLine - signalLine;

            return new MACD(macdLine, signalLine, histogram);
        }

        public double calculateVWAP(List<Long> prices, List<Long> volumes) {
            if (prices == null || volumes == null || prices.size() != volumes.size() || prices.isEmpty()) {
                return 0;
            }

            double pv = 0;
            long volume = 0;

            for (int i = 0; i < prices.size(); i++) {
                pv += prices.get(i) * volumes.get(i);
                volume += volumes.get(i);
            }

            return volume == 0 ? 0 : pv / volume;
        }

        public MeanReversionSignal detectMeanReversion(List<Long> prices, int lookback) {
            if (prices == null || prices.size() < lookback) {
                return new MeanReversionSignal(false, 0);
            }

            List<Long> recent = prices.subList(Math.max(0, prices.size() - lookback), prices.size());
            double mean = recent.stream().mapToDouble(Long::doubleValue).average().orElse(0);
            double stdDev = Math.sqrt(recent.stream()
                .mapToDouble(p -> Math.pow(p - mean, 2))
                .average()
                .orElse(0));

            long current = prices.get(prices.size() - 1);
            double zScore = stdDev == 0 ? 0 : (current - mean) / stdDev;

            boolean detected = Math.abs(zScore) > 2; // 2+ standard deviations away
            return new MeanReversionSignal(detected, zScore);
        }

        public MomentumSignal detectMomentum(List<Long> prices, int shortPeriod, int longPeriod) {
            if (prices == null || prices.size() < longPeriod) {
                return new MomentumSignal(Signal.NEUTRAL, 0);
            }

            double shortEMA = calculateEMA(prices, shortPeriod);
            double longEMA = calculateEMA(prices, longPeriod);

            double ratio = longEMA == 0 ? 1 : shortEMA / longEMA;
            double strength = Math.min(1.0, Math.abs(ratio - 1.0) / 0.1); // Cap at 10% divergence

            Signal signal;
            if (ratio > 1.02) {
                signal = Signal.BUY;
            } else if (ratio < 0.98) {
                signal = Signal.SELL;
            } else {
                signal = Signal.NEUTRAL;
            }

            return new MomentumSignal(signal, strength);
        }
    }

    
    public enum UpdateCategory {
        COMBAT_UPDATES,
        SKILL_UPDATES,
        BOSS_RELEASES,
        QUEST_RELEASES,
        PVP_UPDATES
    }

    
    public static class UpdateImpactAnalyzer {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketIntelligenceEngine.UpdateImpactAnalyzer.class);
        private final PriceService priceService;

        public UpdateImpactAnalyzer(PriceService priceService) {
            this.priceService = priceService;
        }

        private static final Map<Integer, Set<UpdateCategory>> ITEM_UPDATE_MAPPINGS = new HashMap<>();

        static {
            // Combat updates affect combat gear
            Set<Integer> combatGearIds = new HashSet<>(Arrays.asList(
                11804, 11806, 11808, // Bandos armor (helm, body, legs)
                11830, 11832, 11834, // Armadyl armor
                13087, 13089, 13091, // Ancestral armor
                22109, 22111, 22113  // Torva armor (post-Nex)
            ));

            // Skill updates affect resources
            Set<Integer> skillResourceIds = new HashSet<>(Arrays.asList(
                249, 251, 253, // Herb seeds
                7936, 7937, 7938, // Grimy herbs
                1619, 1621, 1623, // Raw fish
                2363, 2364, 2365  // Ore
            ));

            // Boss releases crash drop table items
            Set<Integer> bossDropIds = new HashSet<>(Arrays.asList(
                12926, 12927, 12928, // Zulrah unique drops
                11835, 11836, 11837, // Arma unique drops
                10551, 10552, 10553  // Sara unique drops
            ));

            // Quest releases spike quest items
            Set<Integer> questItemIds = new HashSet<>(Arrays.asList(
                1741, 1743, 1745, // Quest item examples
                2371, 2373, 2375  // More quest items
            ));

            // PvP updates cause volatility in PK gear
            Set<Integer> pvpGearIds = new HashSet<>(Arrays.asList(
                4587, 4585, 4675, // Ancient staff variants
                12654, 12655, 12656 // Shadow staff
            ));
        }

        public List<String> analyzeItemUpdateSensitivity(int itemId) {
            List<String> categories = new ArrayList<>();

            if (isCombatGear(itemId)) categories.add("COMBAT_UPDATES");
            if (isSkillResource(itemId)) categories.add("SKILL_UPDATES");
            if (isBossDrop(itemId)) categories.add("BOSS_RELEASES");
            if (isQuestItem(itemId)) categories.add("QUEST_RELEASES");
            if (isPvpGear(itemId)) categories.add("PVP_UPDATES");

            return categories;
        }

        public List<Integer> getPreUpdatePositions(String updateType) {
            // Items to buy BEFORE updates drop
            List<Integer> positions = new ArrayList<>();

            switch (updateType.toUpperCase()) {
                case "COMBAT_UPDATES":
                    positions.addAll(Arrays.asList(
                        11804, 11806, 11808, // Bandos
                        11830, 11832, 11834, // Armadyl
                        13087, 13089, 13091  // Ancestral
                    ));
                    break;
                case "SKILL_UPDATES":
                    positions.addAll(Arrays.asList(
                        7936, 7937, 7938,   // Grimy herbs
                        1621, 1623,         // Raw fish
                        2363, 2364          // Ore
                    ));
                    break;
                case "BOSS_RELEASES":
                    // Before a boss releases, related unique materials spike
                    positions.addAll(Arrays.asList(
                        12926, 12927, 12928  // Zulrah materials
                    ));
                    break;
                case "PVP_UPDATES":
                    positions.addAll(Arrays.asList(
                        4587, 4585, 4675,   // Ancient staff variants
                        12654, 12655        // Shadow staff
                    ));
                    break;
            }

            return positions;
        }

        public List<Integer> getPostUpdateExits(String updateType) {
            // When to sell after update hype fades
            return getPreUpdatePositions(updateType);
        }

        private boolean isCombatGear(int itemId) {
            return Arrays.asList(11804, 11806, 11808, 11830, 11832, 11834, 13087, 13089, 13091, 22109, 22111, 22113)
                .contains(itemId);
        }

        private boolean isSkillResource(int itemId) {
            return Arrays.asList(249, 251, 253, 7936, 7937, 7938, 1619, 1621, 1623, 2363, 2364, 2365)
                .contains(itemId);
        }

        private boolean isBossDrop(int itemId) {
            return Arrays.asList(12926, 12927, 12928, 11835, 11836, 11837, 10551, 10552, 10553)
                .contains(itemId);
        }

        private boolean isQuestItem(int itemId) {
            return Arrays.asList(1741, 1743, 1745, 2371, 2373, 2375)
                .contains(itemId);
        }

        private boolean isPvpGear(int itemId) {
            return Arrays.asList(4587, 4585, 4675, 12654, 12655, 12656)
                .contains(itemId);
        }
    }

    
    public static class CrossItemCorrelator {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketIntelligenceEngine.CrossItemCorrelator.class);
        private final PriceService priceService;

        public CrossItemCorrelator(PriceService priceService) {
            this.priceService = priceService;
        }

        private static final Map<String, Set<Integer>> CORRELATION_GROUPS = new HashMap<>();

        static {
            CORRELATION_GROUPS.put("BANDOS_SET", new HashSet<>(Arrays.asList(11804, 11806, 11808)));
            CORRELATION_GROUPS.put("ARMADYL_SET", new HashSet<>(Arrays.asList(11830, 11832, 11834)));
            CORRELATION_GROUPS.put("GWD_ITEMS", new HashSet<>(Arrays.asList(
                11804, 11806, 11808, 11830, 11832, 11834, 11817, 11819
            )));
            CORRELATION_GROUPS.put("RAIDS_ITEMS", new HashSet<>(Arrays.asList(
                12926, 12927, 12928, 13087, 13089, 13091
            )));
            CORRELATION_GROUPS.put("COX_UNIQUES", new HashSet<>(Arrays.asList(
                12926, 12927, 12928
            )));
            CORRELATION_GROUPS.put("TOB_UNIQUES", new HashSet<>(Arrays.asList(
                22109, 22111, 22113
            )));
        }

        
        public Map<Integer, Double> findCorrelatedPairs(Map<Integer, List<Long>> priceHistories) {
            Map<Integer, Double> correlations = new HashMap<>();

            // Note: O(n^2) complexity — typical for OSRS item database
            // Each correlation is O(m) where m = history length
            List<Integer> itemIds = new ArrayList<>(priceHistories.keySet());
            for (int i = 0; i < itemIds.size(); i++) {
                for (int j = i + 1; j < itemIds.size(); j++) {
                    int id1 = itemIds.get(i);
                    int id2 = itemIds.get(j);

                    List<Long> prices1 = priceHistories.get(id1);
                    List<Long> prices2 = priceHistories.get(id2);

                                        if (prices1 == null || prices2 == null)
                    {
                        continue;
                    }

                    double correlation = calculatePearsonCorrelation(prices1, prices2);

                    // Store strong correlations (both positive and negative)
                    if (Math.abs(correlation) > 0.7) {
                        correlations.put(id1 * 10000 + id2, correlation);
                    }
                }
            }

            return correlations;
        }

        
        public List<LeadLagRelationship> detectLeadLagRelationships(Map<Integer, List<Long>> priceHistories) {
            List<LeadLagRelationship> relationships = new ArrayList<>();

            // Note: O(n^2) complexity — acceptable for typical OSRS item count (~3000)
            // If performance becomes an issue, implement incremental updates or sampling
            List<Integer> itemIds = new ArrayList<>(priceHistories.keySet());
            for (int i = 0; i < itemIds.size(); i++) {
                for (int j = 0; j < itemIds.size(); j++) {
                    if (i == j) continue;

                    int leaderId = itemIds.get(i);
                    int followerId = itemIds.get(j);

                    List<Long> leaderPrices = priceHistories.get(leaderId);
                    List<Long> followerPrices = priceHistories.get(followerId);

                                        if (leaderPrices == null || followerPrices == null)
                    {
                        continue;
                    }

                    double laggedCorrelation = calculateLaggedCorrelation(
                        leaderPrices,
                        followerPrices,
                        3 // 3-day lag
                    );

                    if (laggedCorrelation > 0.65) {
                        relationships.add(new LeadLagRelationship(leaderId, followerId, laggedCorrelation, 3));
                    }
                }
            }

            return relationships;
        }

        
        public List<ArbitrageOpportunity> getArbitrageOpportunities(Map<Integer, Long> currentPrices,
                                                                    Map<Integer, List<Long>> priceHistories) {
            List<ArbitrageOpportunity> opportunities = new ArrayList<>();

            if (currentPrices == null || priceHistories == null)
            {
                return opportunities;
            }

            CORRELATION_GROUPS.forEach((groupName, items) -> {
                if (items == null) return;

                List<Integer> itemList = new ArrayList<>(items);

                // Find spreads between correlated items (O(n^2) per group)
                for (int i = 0; i < itemList.size(); i++) {
                    for (int j = i + 1; j < itemList.size(); j++) {
                        int id1 = itemList.get(i);
                        int id2 = itemList.get(j);

                        long price1 = currentPrices.getOrDefault(id1, 0L);
                        long price2 = currentPrices.getOrDefault(id2, 0L);

                        if (price1 == 0 || price2 == 0) continue;

                        List<Long> prices1 = priceHistories.get(id1);
                        List<Long> prices2 = priceHistories.get(id2);

                                                if (prices1 == null || prices2 == null) continue;

                        double correlation = calculatePearsonCorrelation(prices1, prices2);

                        // High correlation but prices diverged = mean reversion opportunity
                        if (correlation > 0.8) {
                            double spread = Math.abs(price1 - price2) / (double) Math.min(price1, price2);
                            if (spread > 0.05) { // >5% spread
                                opportunities.add(new ArbitrageOpportunity(
                                    id1, id2, groupName, correlation, spread,
                                    price1 < price2 ? id1 : id2  // Buy the cheaper one
                                ));
                            }
                        }
                    }
                }
            });

            return opportunities.stream()
                .sorted(Comparator.comparingDouble(ArbitrageOpportunity::getSpread).reversed())
                .collect(Collectors.toList());
        }

        private double calculatePearsonCorrelation(List<Long> x, List<Long> y) {
            if (x == null || y == null || x.size() != y.size() || x.size() < 2) {
                return 0;
            }

            double meanX = x.stream().mapToDouble(Long::doubleValue).average().orElse(0);
            double meanY = y.stream().mapToDouble(Long::doubleValue).average().orElse(0);

            double numerator = 0;
            double denomX = 0;
            double denomY = 0;

            for (int i = 0; i < x.size(); i++) {
                double dx = x.get(i) - meanX;
                double dy = y.get(i) - meanY;
                numerator += dx * dy;
                denomX += dx * dx;
                denomY += dy * dy;
            }

            double denom = Math.sqrt(denomX * denomY);
            return denom == 0 ? 0 : numerator / denom;
        }

        private double calculateLaggedCorrelation(List<Long> leader, List<Long> follower, int lag) {
            if (leader == null || follower == null || leader.size() <= lag) {
                return 0;
            }

            List<Long> leaderLagged = leader.subList(0, leader.size() - lag);
            List<Long> followerAligned = follower.subList(lag, follower.size());

            return calculatePearsonCorrelation(leaderLagged, followerAligned);
        }
    }

    
    public static class LiquidityAnalyzer {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketIntelligenceEngine.LiquidityAnalyzer.class);
        private final PriceService priceService;

        public LiquidityAnalyzer(PriceService priceService) {
            this.priceService = priceService;
        }

        public double calculateBidAskSpread(long high, long low) {
            if (high == 0 || low == 0) return 0;
            return high - low;
        }

        public double calculateBidAskSpreadPercent(long high, long low) {
            if (high == 0 || low == 0) return 0;
            return ((high - low) / (double) low) * 100;
        }

        public int calculateMarketDepth(long volume1h, long avgVolume24h) {
            if (avgVolume24h == 0) return 0;

            double depthRatio = volume1h / (double) avgVolume24h;
            int score = (int) Math.min(100, depthRatio * 100);

            return Math.max(0, score);
        }

        public double calculateSlippage(int quantity, long volume1h, int geLimit) {
            if (volume1h == 0) return 100; // Max slippage if no volume
            if (quantity > geLimit) return 100; // Can't trade this amount

            double volumePercent = (quantity / (double) geLimit) * 100;
            return Math.min(5, volumePercent * 0.1); // Max 5% slippage
        }

        public int getLiquidityScore(long volume1h, double spreadPercent) {
            int score = 0;

            // Volume component (0-50 points)
            if (volume1h > 100000) score += 50;
            else if (volume1h > 50000) score += 40;
            else if (volume1h > 10000) score += 30;
            else if (volume1h > 1000) score += 15;
            else if (volume1h > 0) score += 5;

            // Spread component (0-50 points)
            if (spreadPercent < 0.5) score += 50;
            else if (spreadPercent < 1) score += 40;
            else if (spreadPercent < 2) score += 30;
            else if (spreadPercent < 5) score += 20;
            else if (spreadPercent < 10) score += 10;

            return Math.min(100, score);
        }

        public LiquidityClass classifyLiquidity(int liquidityScore) {
            if (liquidityScore >= 80) return LiquidityClass.HIGHLY_LIQUID;
            if (liquidityScore >= 60) return LiquidityClass.LIQUID;
            if (liquidityScore >= 40) return LiquidityClass.MODERATE;
            if (liquidityScore >= 20) return LiquidityClass.THIN;
            return LiquidityClass.ILLIQUID;
        }
    }

    
    public static class RecipeFlipDetector {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketIntelligenceEngine.RecipeFlipDetector.class);
        private final PriceService priceService;

        public RecipeFlipDetector(PriceService priceService) {
            this.priceService = priceService;
        }

        private static final Map<Integer, RecipeMapping> RECIPE_MAPPINGS = new HashMap<>();

        static {
            // Herb recipes
            RECIPE_MAPPINGS.put(249, new RecipeMapping(7936, 1, 249, 1)); // Ranarr seed → Grimy ranarr
            RECIPE_MAPPINGS.put(251, new RecipeMapping(7937, 1, 251, 1)); // Snapdragon seed → Grimy snapdragon

            // Cleaning herbs (grimy → clean)
            RECIPE_MAPPINGS.put(7936, new RecipeMapping(7936, 1, 249, 1)); // Grimy ranarr → Clean ranarr
            RECIPE_MAPPINGS.put(7937, new RecipeMapping(7937, 1, 251, 1)); // Grimy snapdragon → Clean snapdragon

            // Gem cutting
            RECIPE_MAPPINGS.put(1617, new RecipeMapping(1617, 1, 1619, 1)); // Uncut ruby → Ruby
            RECIPE_MAPPINGS.put(1621, new RecipeMapping(1621, 1, 1623, 1)); // Uncut diamond → Diamond

            // Fletching
            RECIPE_MAPPINGS.put(1511, new RecipeMapping(1511, 1, 52, 1)); // Logs → Shortbow
            RECIPE_MAPPINGS.put(1512, new RecipeMapping(1512, 1, 838, 1)); // Logs → Longbow
        }

        public List<RecipeOpportunity> findProfitableRecipes() {
            List<RecipeOpportunity> opportunities = new ArrayList<>();

            RECIPE_MAPPINGS.forEach((inputId, mapping) -> {
                try {
                    PriceAggregate inputAggregate = priceService.getPriceAggregate(inputId);
                    PriceAggregate outputAggregate = priceService.getPriceAggregate(mapping.outputId);

                    if (inputAggregate == null || outputAggregate == null) return;

                    long inputPrice = inputAggregate.getCurrentPrice();
                    long outputPrice = outputAggregate.getCurrentPrice();

                    if (inputPrice == 0 || outputPrice == 0) return;

                    long inputCost = inputPrice * mapping.inputQuantity;
                    long outputValue = outputPrice * mapping.outputQuantity;
                    long profit = outputValue - inputCost - calculateGETax(outputValue);

                    if (profit > 0) {
                        // Estimate GP/hr assuming 1000 items/hr (rough average)
                        long profitPerHr = profit * 1000;

                        opportunities.add(new RecipeOpportunity(
                            inputId, mapping.outputId, mapping.inputQuantity, mapping.outputQuantity,
                            inputPrice, outputPrice, profit, profitPerHr
                        ));
                    }
                } catch (Exception e) {
                    log.debug("Error analyzing recipe for item {}", inputId, e);
                }
            });

            return opportunities.stream()
                .sorted(Comparator.comparingLong(RecipeOpportunity::getProfitPerHr).reversed())
                .collect(Collectors.toList());
        }

        private long calculateGETax(long outputValue) {
            // GE tax: 2%, capped at 5M, 0 for items <50gp
            if (outputValue < 50) return 0;
            return Math.min(5_000_000, outputValue / 50);
        }
    }

    
    public static class SeasonalPatternDetector {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketIntelligenceEngine.SeasonalPatternDetector.class);
        private final PriceService priceService;

        public SeasonalPatternDetector(PriceService priceService) {
            this.priceService = priceService;
        }

        public String getBestTradingHours() {
            LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
            int hour = now.getHour();

            // Typical peak UK hours: 13:00-18:00 UTC
            // Tight spreads: 18:00-02:00 UTC (London evening to early morning)
            if (hour >= 18 || hour < 2) {
                return "18:00-02:00 UTC (Tightest spreads, European evening)";
            }
            if (hour >= 13 && hour < 18) {
                return "13:00-18:00 UTC (Peak volume, moderate spreads)";
            }
            return "02:00-13:00 UTC (Low volume, volatile spreads)";
        }

        public String getWeekendEffect(int itemId) {
            // Generally: weekend = lower volume, wider spreads, lower prices for supply items
            LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
            int dayOfWeek = now.getDayOfWeek().getValue();

            if (dayOfWeek >= 6) { // Saturday/Sunday
                return "WEEKEND: Expect lower volume, wider spreads, lower prices for supplies";
            }
            return "WEEKDAY: Higher volume, tighter spreads";
        }

        public String getUpdateDayEffect() {
            // Wednesdays (update day in OSRS): Higher volatility, movement around 14:00 UTC
            LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
            int dayOfWeek = now.getDayOfWeek().getValue();

            if (dayOfWeek == 3) { // Wednesday
                return "UPDATE DAY: High volatility expected around 14:00 UTC update";
            }
            return "Non-update day";
        }
    }

    
    public static class ItemSinkTracker {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketIntelligenceEngine.ItemSinkTracker.class);
        private final PriceService priceService;

        public ItemSinkTracker(PriceService priceService) {
            this.priceService = priceService;
        }

        private static final Set<Integer> SINK_ITEMS = new HashSet<>(Arrays.asList(
            249, 251, 253, 255, 2139, 2140, 2141, // Herb seeds
            1519, 1521, 1523, 1525, // Fish
            2363, 2364, 2365, 2366, // Ore
            1511, 1512, 1513, 1514, // Logs
            1601, 1603, 1605, 1607, // Bars
            1617, 1619, 1621, 1623, // Gems
            4585, 4587, 4589, 4591, // Staffs
            11804, 11806, 11808, 11830, 11832, 11834, // Armor sets
            12926, 12927, 12928, // Uniques
            13087, 13089, 13091, 13101, // Raids items
            22109, 22111, 22113, 22115  // Post-Nex items
        ));

        public boolean isItemSinkTarget(int itemId) {
            return SINK_ITEMS.contains(itemId);
        }

        public double getSinkPressure(int itemId, long currentPrice, long avgPrice) {
            if (!isItemSinkTarget(itemId)) return 0;

            // Sink pressure: how much below average = how much the sink is "pulling" price up
            double deviation = (avgPrice - currentPrice) / (double) avgPrice;
            // Return a 0-100 score where higher = more sink pressure
            return Math.max(0, deviation * 100);
        }

        public Set<Integer> getSinkItems() {
            return new HashSet<>(SINK_ITEMS);
        }
    }

    
    public static class MarketRegimeDetector {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketIntelligenceEngine.MarketRegimeDetector.class);
        private final PriceService priceService;

        public MarketRegimeDetector(PriceService priceService) {
            this.priceService = priceService;
        }

        public MarketRegime detectRegime(List<Long> prices) {
            if (prices == null || prices.size() < 30) {
                return MarketRegime.UNKNOWN;
            }

            double hurst = calculateHurstExponent(prices);

            if (hurst > 0.55) {
                return MarketRegime.TRENDING_UP;
            } else if (hurst < 0.45) {
                return MarketRegime.TRENDING_DOWN;
            } else if (Math.abs(hurst - 0.5) < 0.05) {
                return MarketRegime.RANDOM_WALK;
            }

            // Check volatility
            double volatility = calculateVolatility(prices);
            if (volatility > 0.08) {
                return MarketRegime.VOLATILE;
            }

            // Check range-bound
            long min = prices.stream().mapToLong(Long::longValue).min().orElse(0);
            long max = prices.stream().mapToLong(Long::longValue).max().orElse(0);
            double range = (max - min) / (double) min;

            if (range < 0.05) {
                return MarketRegime.DEAD;
            }

            return MarketRegime.MEAN_REVERTING;
        }

        public String getOptimalStrategy(MarketRegime regime) {
            switch (regime) {
                case TRENDING_UP:
                    return "BUY and HOLD: Momentum strategy, trailing stop-loss";
                case TRENDING_DOWN:
                    return "AVOID or SHORT: Stay in cash or short-sell (via margin)";
                case MEAN_REVERTING:
                    return "RANGE TRADING: Buy dips, sell rallies at mean";
                case RANGE_BOUND:
                    return "SUPPORT/RESISTANCE: Trade the levels";
                case VOLATILE:
                    return "OPTIONS/STRADDLES: High volatility = high premiums";
                case RANDOM_WALK:
                    return "AVOID or SCALP: No edge, stick to short-term scalps";
                case DEAD:
                    return "AVOID: Zero liquidity, no spreads";
                default:
                    return "UNKNOWN regime";
            }
        }

        private double calculateHurstExponent(List<Long> prices) {
            // Simplified Hurst exponent calculation using rescaled range
            if (prices.size() < 20) return 0.5;

            List<Long> returns = new ArrayList<>();
            for (int i = 1; i < prices.size(); i++) {
                returns.add(prices.get(i) - prices.get(i - 1));
            }

            double mean = returns.stream().mapToDouble(Long::doubleValue).average().orElse(0);
            double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - mean, 2))
                .average()
                .orElse(1);

            double stdDev = Math.sqrt(variance);
            if (stdDev == 0) return 0.5;

            // Rough approximation: higher autocorrelation → higher Hurst
            double autocorr = calculateAutocorrelation(returns, 1);
            return 0.5 + autocorr * 0.5;
        }

        private double calculateAutocorrelation(List<Long> data, int lag) {
            if (data.size() <= lag) return 0;

            List<Long> x = data.subList(0, data.size() - lag);
            List<Long> y = data.subList(lag, data.size());

            double meanX = x.stream().mapToDouble(Long::doubleValue).average().orElse(0);
            double meanY = y.stream().mapToDouble(Long::doubleValue).average().orElse(0);

            double numerator = 0;
            double denomX = 0;
            double denomY = 0;

            for (int i = 0; i < x.size(); i++) {
                double dx = x.get(i) - meanX;
                double dy = y.get(i) - meanY;
                numerator += dx * dy;
                denomX += dx * dx;
                denomY += dy * dy;
            }

            double denom = Math.sqrt(denomX * denomY);
            return denom == 0 ? 0 : numerator / denom;
        }

        private double calculateVolatility(List<Long> prices) {
            if (prices.size() < 2) return 0;

            double mean = prices.stream().mapToDouble(Long::doubleValue).average().orElse(0);
            double variance = prices.stream()
                .mapToDouble(p -> Math.pow(p - mean, 2))
                .average()
                .orElse(0);

            return Math.sqrt(variance) / mean;
        }
    }

    // = //
    // DATA CLASSES
    // - = -

    @Data
    @Builder
    @AllArgsConstructor
    public static class MarketReport {
        private int itemId;
        private long currentPrice;
        private double ema14;
        private double ema50;
        private int rsi;
        private BollingerBands bollingerBands;
        private MACD macd;
        private double vwap;
        private MeanReversionSignal meanReversionSignal;
        private MomentumSignal momentumSignal;
        private MarketRegime marketRegime;
        private String optimalStrategy;
        private double bidAskSpread;
        private double spreadPercent;
        private int marketDepth;
        private int liquidityScore;
        private LiquidityClass liquidityClass;
        private long volume1h;
        private long avgVolume24h;
        private List<String> updateSensitivity;
        private boolean isSinkTarget;
        private double sinkPressure;
        private double intelligenceScore;
        private long timestamp;
    }

    @Data
    @AllArgsConstructor
    public static class BollingerBands {
        private double upper;
        private double middle;
        private double lower;
    }

    @Data
    @AllArgsConstructor
    public static class MACD {
        private double macdLine;
        private double signalLine;
        private double histogram;
    }

    @Data
    @AllArgsConstructor
    public static class MeanReversionSignal {
        private boolean detected;
        private double zScore;
    }

    @Data
    @AllArgsConstructor
    public static class MomentumSignal {
        private Signal signal;
        private double strength; // 0-1
    }

    @Data
    @AllArgsConstructor
    public static class LeadLagRelationship {
        private int leaderId;
        private int followerId;
        private double correlation;
        private int lagDays;
    }

    @Data
    @AllArgsConstructor
    public static class ArbitrageOpportunity {
        private int item1;
        private int item2;
        private String groupName;
        private double correlation;
        private double spread; // as percent
        private int buyItem;
    }

    @Data
    @AllArgsConstructor
    public static class RecipeOpportunity {
        private int inputItemId;
        private int outputItemId;
        private int inputQuantity;
        private int outputQuantity;
        private long inputPrice;
        private long outputPrice;
        private long profit;
        private long profitPerHr;
    }

    @Data
    @AllArgsConstructor
    private static class RecipeMapping {
        private int outputId;
        private int outputQuantity;
        private int inputId;
        private int inputQuantity;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class MarketBriefing {
        private long timestamp;
        private double avgIntelligenceScore;
        private long itemsTrendingUp;
        private long itemsTrendingDown;
        private String volatilityLevel;
        private List<MarketReport> topMovers;
    }

    public enum Signal {
        BUY, SELL, NEUTRAL
    }

    public enum LiquidityClass {
        HIGHLY_LIQUID, LIQUID, MODERATE, THIN, ILLIQUID
    }

    public enum MarketRegime {
        TRENDING_UP, TRENDING_DOWN, MEAN_REVERTING, RANGE_BOUND, VOLATILE, RANDOM_WALK, DEAD, UNKNOWN
    }
}
