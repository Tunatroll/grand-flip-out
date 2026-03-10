package com.fliphelper.tracker;

import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.model.PriceData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

// Scores price data freshness, reliability, and manipulation risk.
@Slf4j
@Data
@AllArgsConstructor
public class DataQualityAnalyzer {

    private PriceService priceService;
    private PriceLifetimeAnalyzer priceLifetimeAnalyzer;
    private HumanAccuracyScorer humanAccuracyScorer;
    private ManipulationDetector manipulationDetector;
    private PriceReliabilityCache cache;

    public DataQualityAnalyzer(PriceService priceService) {
        this.priceService = priceService;
        this.manipulationDetector = new ManipulationDetector(priceService);
        this.priceLifetimeAnalyzer = new PriceLifetimeAnalyzer(priceService);
        this.humanAccuracyScorer = new HumanAccuracyScorer(priceService, this.manipulationDetector);
        this.cache = new PriceReliabilityCache();
    }

    
    public DataQualityReport getDataQuality(int itemId) {
        DataQualityReport cached = cache.getCachedQuality(itemId);
        if (cached != null) {
            return cached;
        }

        PriceAggregate aggregate = priceService.getPriceAggregate(itemId);
        if (aggregate == null) {
            log.warn("No price data available for item {}", itemId);
            return DataQualityReport.builder()
                    .overallScore(0)
                    .confidenceBand(ConfidenceBand.DO_NOT_TRADE)
                    .recommendation(Recommendation.AVOID)
                    .warnings(Collections.singletonList("No price data available"))
                    .build();
        }

        // Calculate individual component scores
        PriceAge priceAge = priceLifetimeAnalyzer.getPriceAge(itemId);
        int freshnessScore = calculateFreshnessScore(priceAge);

        int humanScore = humanAccuracyScorer.getHumanScore(itemId);

        ManipulationAssessment manipulation = manipulationDetector.detectManipulation(itemId);
        int antiManipScore = calculateAntiManipulationScore(manipulation);

        int volumeScore = calculateVolumeScore(aggregate);

        // Composite score
        int overallScore = (int) ((freshnessScore * 0.25) + (humanScore * 0.25) +
                                  (antiManipScore * 0.30) + (volumeScore * 0.20));

        // Determine confidence band and recommendation
        ConfidenceBand confidenceBand = determineConfidenceBand(overallScore, priceAge, manipulation);
        Recommendation recommendation = determineRecommendation(overallScore, confidenceBand, manipulation);

        // Build warnings list
        List<String> warnings = buildWarnings(priceAge, humanScore, manipulation, aggregate);

        DataQualityReport report = DataQualityReport.builder()
                .itemId(itemId)
                .overallScore(overallScore)
                .priceAge(priceAge)
                .humanScore(humanScore)
                .manipulationRisk(manipulation)
                .sourceReliability(calculateSourceReliability(aggregate))
                .confidenceBand(confidenceBand)
                .warnings(warnings)
                .recommendation(recommendation)
                .generatedAt(LocalDateTime.now())
                .build();

        cache.cache(itemId, report);
        return report;
    }

    
    public Map<Integer, DataQualityReport> getBulkQuality(Set<Integer> itemIds) {
        return itemIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        this::getDataQuality
                ));
    }

    
    public List<Integer> getTopQualityItems(int limit) {
        return priceService.getAllItemIds().stream()
                .map(this::getDataQuality)
                .sorted((a, b) -> Integer.compare(b.getOverallScore(), a.getOverallScore()))
                .limit(limit)
                .map(DataQualityReport::getItemId)
                .collect(Collectors.toList());
    }

    
    public List<Integer> getWorstQualityItems(int limit) {
        return priceService.getAllItemIds().stream()
                .map(this::getDataQuality)
                .sorted(Comparator.comparingInt(DataQualityReport::getOverallScore))
                .limit(limit)
                .map(DataQualityReport::getItemId)
                .collect(Collectors.toList());
    }

    
    public List<Integer> getManipulatedItems() {
        return manipulationDetector.getManipulationBlacklist();
    }

    
    public List<Integer> getStaleItems() {
        return priceService.getAllItemIds().stream()
                .filter(id -> {
                    PriceAge age = priceLifetimeAnalyzer.getPriceAge(id);
                    return age != null && age.isStale();
                })
                .collect(Collectors.toList());
    }

    
    public DataQualitySummary getDataQualitySummary() {
        List<Integer> allItems = priceService.getAllItemIds();
        List<DataQualityReport> allReports = allItems.stream()
                .map(this::getDataQuality)
                .collect(Collectors.toList());

        long goodQuality = allReports.stream()
                .filter(r -> r.getConfidenceBand() == ConfidenceBand.HIGH_CONFIDENCE)
                .count();

        long stale = allReports.stream()
                .filter(r -> r.getPriceAge() != null && r.getPriceAge().isStale())
                .count();

        long manipulated = allReports.stream()
                .filter(r -> r.getManipulationRisk().getManipulationRisk().equals(ManipulationRisk.HIGH)
                          || r.getManipulationRisk().getManipulationRisk().equals(ManipulationRisk.EXTREME))
                .count();

        return DataQualitySummary.builder()
                .totalItems(allItems.size())
                .itemsWithGoodData(goodQuality)
                .itemsStale(stale)
                .itemsManipulated(manipulated)
                .averageScore((int) allReports.stream()
                        .mapToInt(DataQualityReport::getOverallScore)
                        .average()
                        .orElse(0))
                .percentageGoodQuality((int) ((goodQuality * 100) / Math.max(allItems.size(), 1)))
                .percentageStale((int) ((stale * 100) / Math.max(allItems.size(), 1)))
                .percentageManipulated((int) ((manipulated * 100) / Math.max(allItems.size(), 1)))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // Helper methods

    private int calculateFreshnessScore(PriceAge age) {
        if (age == null) return 0;

        long avgAge = age.getAverageAgeMinutes();
        if (avgAge <= 5) return 100;
        if (avgAge <= 15) return 85;
        if (avgAge <= 30) return 70;
        if (avgAge <= 120) return 50;
        if (avgAge <= 360) return 25;
        return 0;
    }

    private int calculateAntiManipulationScore(ManipulationAssessment assessment) {
        if (assessment == null) return 50;

        switch (assessment.getManipulationRisk()) {
            case EXTREME: return 0;
            case HIGH: return 20;
            case MEDIUM: return 50;
            case LOW: return 80;
            default: return 50;
        }
    }

    private int calculateVolumeScore(PriceAggregate aggregate) {
        if (aggregate == null) return 0;

        long dailyVolume = aggregate.getEstimatedDailyVolume();
        if (dailyVolume > 1000) return 100;
        if (dailyVolume > 500) return 85;
        if (dailyVolume > 100) return 70;
        if (dailyVolume > 10) return 40;
        return 0;
    }

    private String calculateSourceReliability(PriceAggregate aggregate) {
        if (aggregate == null) return "UNKNOWN";

        long volume = aggregate.getEstimatedDailyVolume();
        if (volume > 1000) return "VERY_HIGH";
        if (volume > 500) return "HIGH";
        if (volume > 50) return "MODERATE";
        if (volume > 5) return "LOW";
        return "VERY_LOW";
    }

    private ConfidenceBand determineConfidenceBand(int overallScore, PriceAge age, ManipulationAssessment manip) {
        // Always prioritize manipulation risk over volume
        if (manip.getManipulationRisk() == ManipulationRisk.EXTREME) {
            return ConfidenceBand.DO_NOT_TRADE;
        }

        if (age != null && age.isDead()) {
            return ConfidenceBand.UNRELIABLE;
        }

        if (overallScore >= 80) return ConfidenceBand.HIGH_CONFIDENCE;
        if (overallScore >= 60) return ConfidenceBand.MODERATE;
        if (overallScore >= 40) return ConfidenceBand.LOW_CONFIDENCE;
        return ConfidenceBand.UNRELIABLE;
    }

    private Recommendation determineRecommendation(int overallScore, ConfidenceBand band, ManipulationAssessment manip) {
        if (band == ConfidenceBand.DO_NOT_TRADE) {
            return Recommendation.AVOID;
        }

        if (manip.getManipulationRisk() == ManipulationRisk.HIGH || manip.getManipulationRisk() == ManipulationRisk.EXTREME) {
            return Recommendation.VERIFY_MANUALLY;
        }

        if (overallScore >= 75) return Recommendation.SAFE_TO_TRADE;
        if (overallScore >= 50) return Recommendation.TRADE_WITH_CAUTION;
        if (overallScore >= 25) return Recommendation.VERIFY_MANUALLY;
        return Recommendation.AVOID;
    }

    private List<String> buildWarnings(PriceAge age, int humanScore, ManipulationAssessment manip, PriceAggregate agg) {
        List<String> warnings = new ArrayList<>();

        if (age != null) {
            if (age.isDead()) {
                warnings.add("Price data is extremely stale (>6 hours old) - unlikely anyone is trading this");
            } else if (age.isVeryStale()) {
                warnings.add("Price data is very stale (>2 hours old) - use with caution");
            } else if (age.isStale()) {
                warnings.add("Price data is stale (>30 minutes old)");
            }
        }

        if (humanScore < 30) {
            warnings.add("Trading pattern shows strong bot/automated signals");
        } else if (humanScore < 50) {
            warnings.add("Trading pattern appears partially automated");
        }

        if (manip.getManipulationRisk() == ManipulationRisk.EXTREME) {
            warnings.add("EXTREME manipulation risk detected - AVOID trading");
        } else if (manip.getManipulationRisk() == ManipulationRisk.HIGH) {
            warnings.add("HIGH manipulation risk - verify before trading");
            if (!manip.getEvidenceList().isEmpty()) {
                warnings.add("Detected: " + String.join(", ", manip.getEvidenceList()));
            }
        }

        if (agg != null && agg.getEstimatedDailyVolume() < 5) {
            warnings.add("Very low daily volume - prices may be unreliable");
        }

        return warnings;
    }

    // [=]
    
    // --- = ---

    
    @Data
    public static class PriceLifetimeAnalyzer {

        private final PriceService priceService;

        public PriceLifetimeAnalyzer(PriceService priceService) {
            this.priceService = priceService;
        }

        
        public PriceAge getPriceAge(int itemId) {
            PriceData priceData = priceService.getLatestPrice(itemId);
            if (priceData == null) {
                return null;
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lastUpdate = priceData.getLastUpdated();

            long highAgeMinutes = ChronoUnit.MINUTES.between(lastUpdate, now);
            long lowAgeMinutes = ChronoUnit.MINUTES.between(lastUpdate, now);
            long avgAgeMinutes = (highAgeMinutes + lowAgeMinutes) / 2;

            FreshnessBand freshnessBand;
            if (avgAgeMinutes < 5) {
                freshnessBand = FreshnessBand.LIVE;
            } else if (avgAgeMinutes < 15) {
                freshnessBand = FreshnessBand.RECENT;
            } else if (avgAgeMinutes < 30) {
                freshnessBand = FreshnessBand.AGING;
            } else if (avgAgeMinutes < 120) {
                freshnessBand = FreshnessBand.STALE;
            } else if (avgAgeMinutes < 360) {
                freshnessBand = FreshnessBand.VERY_STALE;
            } else {
                freshnessBand = FreshnessBand.DEAD;
            }

            return PriceAge.builder()
                    .highPriceAgeMinutes(highAgeMinutes)
                    .lowPriceAgeMinutes(lowAgeMinutes)
                    .averageAgeMinutes(avgAgeMinutes)
                    .freshnessBand(freshnessBand)
                    .build();
        }

        
        public long getEffectiveLifetime(int itemId) {
            List<PriceData> timeSeries = priceService.getPriceTimeseries(itemId, 24);
            if (timeSeries == null || timeSeries.isEmpty()) {
                return 0;
            }

            PriceData current = timeSeries.get(timeSeries.size() - 1);
            long stableMinutes = 0;

            for (int i = timeSeries.size() - 1; i >= 0; i--) {
                PriceData data = timeSeries.get(i);
                double changePercent = Math.abs((double) (data.getHighPrice() - current.getHighPrice()) / current.getHighPrice());

                if (changePercent <= 0.02) {
                    stableMinutes += 60; // Assuming hourly data points
                } else {
                    break;
                }
            }

            return stableMinutes;
        }

        
        public DecayRate getPriceDecayRate(int itemId) {
            PriceAggregate agg = priceService.getPriceAggregate(itemId);
            if (agg == null) {
                return DecayRate.LOW_VOLUME;
            }

            long volume = agg.getEstimatedDailyVolume();
            if (volume > 1000) return DecayRate.HIGH_VOLUME;
            if (volume > 100) return DecayRate.MEDIUM_VOLUME;
            return DecayRate.LOW_VOLUME;
        }
    }

    
    @Data
    public static class HumanAccuracyScorer {

        private final PriceService priceService;
        private final ManipulationDetector manipulationDetector;

        public HumanAccuracyScorer(PriceService priceService, ManipulationDetector manipulationDetector) {
            this.priceService = priceService;
            this.manipulationDetector = manipulationDetector;
        }

        
        public int getHumanScore(int itemId) {
            PriceAggregate agg = priceService.getPriceAggregate(itemId);
            if (agg == null) {
                return 50; // Ambiguous
            }

            int score = 50; // Start at neutral

            // Check for bot signals (decrease score)
            if (hasEvenTradeIntervals(itemId)) score -= 15;
            if (hasUnusualVolumePattern(agg)) score -= 15;
            if (hasExactRoundPrices(itemId)) score -= 10;
            if (hasConsistentNightActivity(itemId)) score -= 15;
            if (hasZeroSpread(itemId)) score -= 20;
            if (isSupplySideOnly(itemId)) score -= 15;

            // Check for human signals (increase score)
            if (hasVariableSpread(itemId)) score += 15;
            if (hasVolumePeakDuringDay(itemId)) score += 15;
            if (hasIrregularIntervals(itemId)) score += 15;
            if (hasVariedPriceDigits(itemId)) score += 10;
            if (hasBothBuyAndSell(itemId)) score += 15;
            if (hasWeekendVolumeDrops(itemId)) score += 10;

            return Math.max(0, Math.min(100, score));
        }

        
        public double getBotProbability(int itemId) {
            int humanScore = getHumanScore(itemId);
            return 1.0 - (humanScore / 100.0);
        }

        
        public TradePatternType getTradePatternType(int itemId) {
            int humanScore = getHumanScore(itemId);

            if (humanScore >= 70) return TradePatternType.ORGANIC;
            if (humanScore >= 50 && humanScore < 70) return TradePatternType.MIXED;
            if (humanScore >= 30 && humanScore < 50) return TradePatternType.BOT_DOMINATED;

            ManipulationAssessment manip = manipulationDetector.detectManipulation(itemId);
            if (manip.getManipulationRisk() == ManipulationRisk.HIGH || manip.getManipulationRisk() == ManipulationRisk.EXTREME) {
                return TradePatternType.MANIPULATED;
            }

            PriceAggregate agg = priceService.getPriceAggregate(itemId);
            if (agg != null && agg.getEstimatedDailyVolume() < 10) {
                return TradePatternType.THIN;
            }

            return TradePatternType.BOT_DOMINATED;
        }

        private boolean hasEvenTradeIntervals(int itemId) {
            List<PriceData> trades = priceService.getPriceTimeseries(itemId, 24);
            if (trades == null || trades.size() < 5) return false;

            long[] intervals = new long[trades.size() - 1];
            for (int i = 0; i < trades.size() - 1; i++) {
                intervals[i] = ChronoUnit.MINUTES.between(
                        trades.get(i).getLastUpdated(),
                        trades.get(i + 1).getLastUpdated()
                );
            }

            // Check if intervals are suspiciously regular
            long avgInterval = Arrays.stream(intervals).sum() / intervals.length;
            long variance = Arrays.stream(intervals)
                    .map(i -> (i - avgInterval) * (i - avgInterval))
                    .sum() / intervals.length;

            return variance < 100 && avgInterval > 0;
        }

        private boolean hasUnusualVolumePattern(PriceAggregate agg) {
            // Check if volume exactly matches GE limits (suspicious bot behavior)
            long volume = agg.getEstimatedDailyVolume();
            return volume == 10000 || volume == 20000 || volume == 5000;
        }

        private boolean hasExactRoundPrices(int itemId) {
            PriceData price = priceService.getLatestPrice(itemId);
            if (price == null) return false;

            return price.getHighPrice() % 1000 == 0 || price.getLowPrice() % 1000 == 0;
        }

        private boolean hasConsistentNightActivity(int itemId) {
            List<PriceData> trades = priceService.getPriceTimeseries(itemId, 24);
            if (trades == null || trades.isEmpty()) return false;

            long nightTrades = trades.stream()
                    .filter(t -> {
                        int hour = t.getLastUpdated().getHour();
                        return hour >= 23 || hour <= 6;
                    })
                    .count();

            return nightTrades > (trades.size() * 0.5);
        }

        private boolean hasZeroSpread(int itemId) {
            PriceData price = priceService.getLatestPrice(itemId);
            if (price == null) return false;

            return price.getHighPrice() == price.getLowPrice();
        }

        private boolean isSupplySideOnly(int itemId) {
            // Requires tracking buy vs sell volume separately
            // Simplified: check if this is a resource (item type that's primarily farmed)
            PriceAggregate agg = priceService.getPriceAggregate(itemId);
            return agg != null && agg.isPrimarilySoldByFarmers();
        }

        private boolean hasVariableSpread(int itemId) {
            List<PriceData> timeSeries = priceService.getPriceTimeseries(itemId, 24);
            if (timeSeries == null || timeSeries.size() < 5) return false;

            long[] spreads = timeSeries.stream()
                    .mapToLong(t -> t.getHighPrice() - t.getLowPrice())
                    .toArray();

            long avgSpread = Arrays.stream(spreads).sum() / spreads.length;
            long variance = Arrays.stream(spreads)
                    .map(s -> (s - avgSpread) * (s - avgSpread))
                    .sum() / spreads.length;

            return variance > 1000000; // Significant variance
        }

        private boolean hasVolumePeakDuringDay(int itemId) {
            List<PriceData> trades = priceService.getPriceTimeseries(itemId, 24);
            if (trades == null || trades.isEmpty()) return false;

            long peakHours = trades.stream()
                    .filter(t -> {
                        int hour = t.getLastUpdated().getHour();
                        return hour >= 18 && hour <= 23;
                    })
                    .count();

            return peakHours > (trades.size() * 0.4);
        }

        private boolean hasIrregularIntervals(int itemId) {
            List<PriceData> trades = priceService.getPriceTimeseries(itemId, 24);
            if (trades == null || trades.size() < 5) return false;

            long[] intervals = new long[trades.size() - 1];
            for (int i = 0; i < trades.size() - 1; i++) {
                intervals[i] = ChronoUnit.MINUTES.between(
                        trades.get(i).getLastUpdated(),
                        trades.get(i + 1).getLastUpdated()
                );
            }

            long avgInterval = Arrays.stream(intervals).sum() / intervals.length;
            long variance = Arrays.stream(intervals)
                    .map(i -> (i - avgInterval) * (i - avgInterval))
                    .sum() / intervals.length;

            return variance > 10000; // High variance = irregular
        }

        private boolean hasVariedPriceDigits(int itemId) {
            List<PriceData> timeSeries = priceService.getPriceTimeseries(itemId, 24);
            if (timeSeries == null || timeSeries.size() < 5) return false;

            Set<Integer> lastDigits = timeSeries.stream()
                    .map(t -> (int) (t.getHighPrice() % 10))
                    .collect(Collectors.toSet());

            return lastDigits.size() >= 5; // Varied last digits
        }

        private boolean hasBothBuyAndSell(int itemId) {
            PriceAggregate agg = priceService.getPriceAggregate(itemId);
            return agg != null && agg.hasBuyAndSellActivity();
        }

        private boolean hasWeekendVolumeDrops(int itemId) {
            List<PriceData> trades = priceService.getPriceTimeseries(itemId, 7);
            if (trades == null || trades.size() < 2) return false;

            // Group by day of week
            Map<Integer, Long> volumeByDow = new TreeMap<>();
            for (PriceData trade : trades) {
                int dow = trade.getLastUpdated().getDayOfWeek().getValue();
                volumeByDow.merge(dow, 1L, Long::sum);
            }

            // Check if weekends (6,7) have fewer trades
            long weekdayAvg = (long) volumeByDow.entrySet().stream()
                    .filter(e -> e.getKey() < 6)
                    .mapToLong(Map.Entry::getValue)
                    .average()
                    .orElse(0);

            long weekendAvg = (long) volumeByDow.entrySet().stream()
                    .filter(e -> e.getKey() >= 6)
                    .mapToLong(Map.Entry::getValue)
                    .average()
                    .orElse(0);

            return weekdayAvg > weekendAvg * 1.5; // Weekday volume significantly higher
        }
    }

    
    @Data
    public static class ManipulationDetector {

        private final PriceService priceService;

        public ManipulationDetector(PriceService priceService) {
            this.priceService = priceService;
        }

        // Known manipulation-prone items database
        private static final Set<Integer> MANIPULATION_TARGETS = new HashSet<>(Arrays.asList(
                // 3rd Age items (extremely rare, low volume, easy to manipulate)
                10350, 10348, 10346, 10352, 10330, 10332, 10334, 10342, 10338, 10340,
                10344, 10336, 12426, 12424, 12422, 12437, 23345, 23348, 23351, 23354,
                20014, 20011,
                // Brutal arrows (commonly manipulated)
                4773, 4778, 4783, 4788, 4793, 4798, 4803,
                // Other commonly manipulated
                12817, 20997, 22325 // Elysian, Twisted bow, Scythe
        ));

        
        public ManipulationAssessment detectManipulation(int itemId) {
            ManipulationRisk risk = ManipulationRisk.LOW;
            List<String> evidence = new ArrayList<>();
            int confidence = 0;
            ManipulationType type = ManipulationType.ORGANIC;

            // Check if it's a known manipulation target
            boolean isKnownTarget = MANIPULATION_TARGETS.contains(itemId);
            if (isKnownTarget) {
                risk = ManipulationRisk.HIGH;
                confidence += 30;
                evidence.add("Known manipulation target (rare/low-volume item)");
            }

            // Check volume threshold (low-volume items >10M are manipulation-prone)
            PriceAggregate agg = priceService.getPriceAggregate(itemId);
            if (agg != null) {
                if (agg.getEstimatedDailyVolume() < 10 && agg.getCurrentPrice() > 10_000_000) {
                    risk = risk.escalate();
                    confidence += 20;
                    evidence.add("High price with extremely low volume");
                }

                // Detect wash trading
                if (detectWashTrading(itemId, agg)) {
                    type = ManipulationType.WASH_TRADING;
                    risk = risk.escalate();
                    confidence += 25;
                    evidence.add("Detected wash trading (buy/sell at identical prices)");
                }

                // Detect pump and dump
                if (detectPumpAndDump(itemId, agg)) {
                    type = ManipulationType.PUMP_AND_DUMP;
                    risk = risk.escalate();
                    confidence += 30;
                    evidence.add("Detected pump and dump pattern (volume spike + price spike + crash)");
                }

                // Detect price fixing
                if (detectPriceFixing(itemId, agg)) {
                    type = ManipulationType.PRICE_FIXING;
                    risk = risk.escalate();
                    confidence += 25;
                    evidence.add("Detected price fixing (few sellers, rising price with minimal volume)");
                }

                // Detect supply squeeze
                if (detectSupplySqueeze(itemId, agg)) {
                    type = ManipulationType.SUPPLY_SQUEEZE;
                    risk = risk.escalate();
                    confidence += 25;
                    evidence.add("Detected supply squeeze pattern");
                }

                // Detect spread manipulation
                if (detectSpreadManipulation(itemId, agg)) {
                    risk = risk.escalate();
                    confidence += 15;
                    evidence.add("Detected artificial spread manipulation");
                }
            }

            boolean safeToTrade = risk == ManipulationRisk.LOW;

            return ManipulationAssessment.builder()
                    .isKnownManipulationTarget(isKnownTarget)
                    .manipulationRisk(risk)
                    .manipulationConfidence(Math.min(100, confidence))
                    .manipulationType(type)
                    .evidenceList(evidence)
                    .safeToTrade(safeToTrade)
                    .build();
        }

        
        public List<Integer> getManipulationBlacklist() {
            return priceService.getAllItemIds().stream()
                    .filter(id -> {
                        ManipulationAssessment assessment = detectManipulation(id);
                        return assessment.getManipulationRisk() == ManipulationRisk.HIGH ||
                               assessment.getManipulationRisk() == ManipulationRisk.EXTREME;
                    })
                    .collect(Collectors.toList());
        }

        
        public boolean shouldAvoid(int itemId) {
            ManipulationAssessment assessment = detectManipulation(itemId);
            return assessment.getManipulationRisk() == ManipulationRisk.EXTREME ||
                   !assessment.isSafeToTrade();
        }

        private boolean detectWashTrading(int itemId, PriceAggregate agg) {
            List<PriceData> timeSeries = priceService.getPriceTimeseries(itemId, 24);
            if (timeSeries == null || timeSeries.size() < 3) return false;

            for (int i = 0; i < timeSeries.size() - 1; i++) {
                PriceData data1 = timeSeries.get(i);
                PriceData data2 = timeSeries.get(i + 1);

                // Check if bought and sold at nearly identical prices in quick succession
                if (Math.abs(data1.getHighPrice() - data2.getLowPrice()) < 100 &&
                    ChronoUnit.MINUTES.between(data1.getLastUpdated(), data2.getLastUpdated()) < 5) {
                    return true;
                }
            }

            return false;
        }

        private boolean detectPumpAndDump(int itemId, PriceAggregate agg) {
            List<PriceData> timeSeries = priceService.getPriceTimeseries(itemId, 72);
            if (timeSeries == null || timeSeries.size() < 10) return false;

            // Check for sudden volume spike + price increase followed by crash
            PriceData earliest = timeSeries.get(0);
            PriceData peak = timeSeries.stream()
                    .max(Comparator.comparingLong(PriceData::getHighPrice))
                    .orElse(earliest);
            PriceData latest = timeSeries.get(timeSeries.size() - 1);

            long volumeSpike = timeSeries.stream()
                    .mapToLong(p -> agg.getEstimatedDailyVolume())
                    .sum();

            double priceIncrease = (double) (peak.getHighPrice() - earliest.getHighPrice()) / earliest.getHighPrice();
            double priceCrash = (double) (peak.getHighPrice() - latest.getHighPrice()) / peak.getHighPrice();

            return priceIncrease > 0.15 && priceCrash > 0.10 && volumeSpike > 500;
        }

        private boolean detectPriceFixing(int itemId, PriceAggregate agg) {
            PriceData price = priceService.getLatestPrice(itemId);
            if (price == null) return false;

            // Very few sellers, price moving up with minimal volume
            return agg.getEstimatedDailyVolume() < 20 && price.getHighPrice() > 1_000_000;
        }

        private boolean detectSupplySqueeze(int itemId, PriceAggregate agg) {
            List<PriceData> timeSeries = priceService.getPriceTimeseries(itemId, 48);
            if (timeSeries == null || timeSeries.size() < 5) return false;

            // Check for rapid price increase with volume spike
            PriceData first = timeSeries.get(0);
            PriceData last = timeSeries.get(timeSeries.size() - 1);

            double priceIncrease = (double) (last.getHighPrice() - first.getHighPrice()) / first.getHighPrice();
            long volume = agg.getEstimatedDailyVolume();

            // Spread collapsed (buyers and sellers meeting)
            long spreadCollapse = first.getHighPrice() - first.getLowPrice();
            long currentSpread = last.getHighPrice() - last.getLowPrice();

            return priceIncrease > 0.20 && volume > 100 && currentSpread < spreadCollapse / 2;
        }

        private boolean detectSpreadManipulation(int itemId, PriceAggregate agg) {
            List<PriceData> timeSeries = priceService.getPriceTimeseries(itemId, 24);
            if (timeSeries == null || timeSeries.size() < 5) return false;

            // Check for artificial widening/narrowing pattern
            long[] spreads = timeSeries.stream()
                    .mapToLong(t -> t.getHighPrice() - t.getLowPrice())
                    .toArray();

            // Multiple sudden spread changes indicate manipulation
            int changes = 0;
            for (int i = 1; i < spreads.length; i++) {
                if (Math.abs(spreads[i] - spreads[i - 1]) > spreads[i - 1] * 0.5) {
                    changes++;
                }
            }

            return changes > 3;
        }
    }

    
    @Data
    public static class PriceReliabilityCache {
        private final Map<Integer, CachedReport> cache = new HashMap<>();
        private static final long CACHE_VALIDITY_MINUTES = 5;

        
        public DataQualityReport getCachedQuality(int itemId) {
            CachedReport cached = cache.get(itemId);
            if (cached == null) return null;

            long ageMinutes = ChronoUnit.MINUTES.between(cached.getCachedAt(), LocalDateTime.now());
            if (ageMinutes > CACHE_VALIDITY_MINUTES) {
                cache.remove(itemId);
                return null;
            }

            return cached.getReport();
        }

        
        public void cache(int itemId, DataQualityReport report) {
            cache.put(itemId, new CachedReport(report, LocalDateTime.now()));
        }

        
        public void invalidate(int itemId) {
            cache.remove(itemId);
        }

        @Data
        @AllArgsConstructor
        private static class CachedReport {
            private DataQualityReport report;
            private LocalDateTime cachedAt;
        }
    }

    // -- =
    // DATA CLASSES AND ENUMS
    // =

    @Data
    @Builder
    public static class DataQualityReport {
        private int itemId;
        private int overallScore;
        private PriceAge priceAge;
        private int humanScore;
        private ManipulationAssessment manipulationRisk;
        private String sourceReliability;
        private ConfidenceBand confidenceBand;
        private List<String> warnings;
        private Recommendation recommendation;
        private LocalDateTime generatedAt;
    }

    @Data
    @Builder
    public static class PriceAge {
        private long highPriceAgeMinutes;
        private long lowPriceAgeMinutes;
        private long averageAgeMinutes;
        private FreshnessBand freshnessBand;

        public boolean isStale() {
            return averageAgeMinutes > 30;
        }

        public boolean isVeryStale() {
            return averageAgeMinutes > 120;
        }

        public boolean isDead() {
            return averageAgeMinutes > 360;
        }
    }

    @Data
    @Builder
    public static class ManipulationAssessment {
        private boolean isKnownManipulationTarget;
        private ManipulationRisk manipulationRisk;
        private int manipulationConfidence;
        private ManipulationType manipulationType;
        private List<String> evidenceList;
        private boolean safeToTrade;
    }

    @Data
    @Builder
    public static class DataQualitySummary {
        private int totalItems;
        private long itemsWithGoodData;
        private long itemsStale;
        private long itemsManipulated;
        private int averageScore;
        private int percentageGoodQuality;
        private int percentageStale;
        private int percentageManipulated;
        private LocalDateTime generatedAt;
    }

    public enum ConfidenceBand {
        HIGH_CONFIDENCE,
        MODERATE,
        LOW_CONFIDENCE,
        UNRELIABLE,
        DO_NOT_TRADE
    }

    public enum Recommendation {
        SAFE_TO_TRADE,
        TRADE_WITH_CAUTION,
        VERIFY_MANUALLY,
        AVOID
    }

    public enum FreshnessBand {
        LIVE,
        RECENT,
        AGING,
        STALE,
        VERY_STALE,
        DEAD
    }

    public enum DecayRate {
        HIGH_VOLUME,
        MEDIUM_VOLUME,
        LOW_VOLUME
    }

    public enum TradePatternType {
        ORGANIC,
        BOT_DOMINATED,
        MIXED,
        MANIPULATED,
        THIN
    }

    public enum ManipulationRisk {
        LOW, MEDIUM, HIGH, EXTREME;

        public ManipulationRisk escalate() {
            switch (this) {
                case LOW: return MEDIUM;
                case MEDIUM: return HIGH;
                case HIGH: return EXTREME;
                case EXTREME: return EXTREME;
                default: return this;
            }
        }
    }

    public enum ManipulationType {
        PUMP_AND_DUMP,
        PRICE_FIXING,
        WASH_TRADING,
        SUPPLY_SQUEEZE,
        ORGANIC
    }
}
