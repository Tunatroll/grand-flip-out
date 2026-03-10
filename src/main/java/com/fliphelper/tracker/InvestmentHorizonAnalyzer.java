package com.fliphelper.tracker;

import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// Scores items by ROI across different hold times (quick flips vs longer investments).
@Slf4j
public class InvestmentHorizonAnalyzer {
    private static final double RISK_FREE_RATE = 0.0; // No risk-free rate in OSRS
    private static final int QUICK_FLIP_MAX_HOURS = 4;
    private static final int SHORT_HOLD_MAX_DAYS = 7;
    private static final int MEDIUM_HOLD_MAX_DAYS = 14;
    private static final int LONG_HOLD_MAX_DAYS = 28;

    private static final Set<Integer> ITEM_SINK_IDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            20997, 22325, 27275, 22323, 22324, 22322, 22326, 22327, 22328,
            24419, 24420, 24421, 24417, 24422, 24511, 24514, 24517,
            26382, 26384, 26386, 21018, 21021, 21024, 13652, 11802, 11804,
            11806, 11808, 11826, 11828, 11830, 11832, 11834, 11836, 12821,
            12825, 12817, 13576, 21015, 21003
    )));

    private final PriceService priceService;
    private final QuickFlipScorer quickFlipScorer;
    private final TrendHoldScorer trendHoldScorer;
    private final StructuralValueScorer structuralValueScorer;
    private final RiskAdjustedRanker riskAdjustedRanker;

    private final ConcurrentHashMap<Integer, HorizonReport> reportCache = new ConcurrentHashMap<>();

    // TODO: Add cache eviction - some items sink/rise permanently, stale reports mislead
    public InvestmentHorizonAnalyzer(PriceService priceService) {
        this.priceService = priceService;
        this.quickFlipScorer = new QuickFlipScorer(priceService);
        this.trendHoldScorer = new TrendHoldScorer(priceService);
        this.structuralValueScorer = new StructuralValueScorer(priceService);
        this.riskAdjustedRanker = new RiskAdjustedRanker(this.reportCache);
    }

    
    public HorizonReport analyzeItem(int itemId) {
        return reportCache.computeIfAbsent(itemId, id -> {
            try {
                PriceAggregate priceData = priceService.getPriceData(itemId);
                if (priceData == null) {
                    return null;
                }

                List<HorizonBand> bands = new ArrayList<>();

                // Evaluate each horizon
                HorizonBand quickFlip = evaluateQuickFlip(itemId, priceData);
                if (quickFlip != null) bands.add(quickFlip);

                HorizonBand shortHold = evaluateShortHold(itemId, priceData);
                if (shortHold != null) bands.add(shortHold);

                HorizonBand mediumHold = evaluateMediumHold(itemId, priceData);
                if (mediumHold != null) bands.add(mediumHold);

                HorizonBand longHold = evaluateLongHold(itemId, priceData);
                if (longHold != null) bands.add(longHold);

                HorizonBand veryLongHold = evaluateVeryLongHold(itemId, priceData);
                if (veryLongHold != null) bands.add(veryLongHold);

                // Determine best horizon
                HorizonBand bestBand = bands.stream()
                        .filter(b -> b.isRecommended())
                        .max(Comparator.comparingDouble(b -> b.getAnnualizedRoi()))
                        .orElse(bands.isEmpty() ? null : bands.get(0));

                double bestRoi = bestBand != null ? bestBand.getEstimatedRoi() : 0;
                int bestHoldDays = bestBand != null ? bestBand.getMaxDays() : 0;
                String verdict = generateVerdict(priceData, bestBand, bands);

                return HorizonReport.builder()
                        .itemId(itemId)
                        .itemName(priceData.getItemName())
                        .currentBuyPrice(priceData.getCurrentBuyPrice())
                        .currentSellPrice(priceData.getCurrentSellPrice())
                        .bands(bands)
                        .bestBand(bestBand)
                        .overallVerdict(verdict)
                        .bestRoi(bestRoi)
                        .bestHoldDays(bestHoldDays)
                        .build();
            } catch (Exception e) {
                log.warn("Failed to analyze item {}: {}", itemId, e.getMessage());
                return null;
            }
        });
    }

    private HorizonBand evaluateQuickFlip(int itemId, PriceAggregate priceData) {
        double score = quickFlipScorer.scoreQuickFlip(itemId, priceData);
        if (score < 30) return null;

        double margin = calculateMargin(priceData);
        double estimatedFlipsPerHour = estimateFlipsPerHour(itemId, priceData);
        double estimatedRoi = (margin / 100.0) * estimatedFlipsPerHour;

        return HorizonBand.builder()
                .type(HorizonType.QUICK_FLIP)
                .minDays(0)
                .maxDays(0) // Same GE cycle
                .estimatedRoi(estimatedRoi)
                .annualizedRoi(estimatedRoi * 365 * 6) // Annualize assuming 6 cycles/day
                .confidence(Math.min(100, 60 + score * 0.4))
                .reasoning("Traditional margin flip. Buy low within GE cycle, sell high before limit resets. " +
                        String.format("%.1f%% margin × ~%.1f flips/hr = %.2f%% ROI", margin, estimatedFlipsPerHour, estimatedRoi * 100))
                .catalysts(Arrays.asList("High GE volume", "Tight bid-ask spread", "Fresh price data"))
                .risks(Arrays.asList("Price manipulation", "Stale data causing false arbitrage", "Low available quantity"))
                .riskReward(RiskRewardRatio.builder()
                        .potentialGain(margin)
                        .potentialLoss(Math.max(2, margin * 0.5))
                        .ratio(margin / Math.max(1, margin * 0.5))
                        .build())
                .recommended(score >= 40)
                .build();
    }

    private HorizonBand evaluateShortHold(int itemId, PriceAggregate priceData) {
        double score = trendHoldScorer.scoreShortTermTrend(itemId, priceData);
        if (score < 25) return null;

        double estimatedRoi = calculateShortHoldRoi(itemId, priceData, score);

        List<String> catalysts = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        String reasoning;

        if (isInDumpRecovery(priceData)) {
            catalysts.add("Recent price dump detected, now recovering");
            catalysts.add("Mean reversion momentum");
            reasoning = "Item recently dumped >10%. Now showing recovery momentum. Buy during dip, " +
                    "sell as price normalizes over 1-7 days.";
        } else if (isMomentumPositive(priceData)) {
            catalysts.add("Positive price momentum (1h vs 24h trending up)");
            catalysts.add("Volume increasing");
            reasoning = "Item showing positive momentum. Likely to continue uptrend over next week. " +
                    "Hold and sell on resistance or event.";
        } else {
            catalysts.add("Item trending upward in short-term chart");
            reasoning = "Short-term uptrend detected. Expected to continue 1-7 days before stabilizing.";
        }

        risks.add("Momentum could reverse on news/updates");
        risks.add("Larger players could dump, breaking uptrend");

        return HorizonBand.builder()
                .type(HorizonType.SHORT_HOLD)
                .minDays(1)
                .maxDays(SHORT_HOLD_MAX_DAYS)
                .estimatedRoi(estimatedRoi)
                .annualizedRoi(estimatedRoi * 365.0 / 4.0) // Annualize assuming 4-day average hold
                .confidence(Math.min(100, score + 20))
                .reasoning(reasoning)
                .catalysts(catalysts)
                .risks(risks)
                .riskReward(RiskRewardRatio.builder()
                        .potentialGain(estimatedRoi)
                        .potentialLoss(estimatedRoi * 0.6)
                        .ratio(estimatedRoi / Math.max(0.1, estimatedRoi * 0.6))
                        .build())
                .recommended(score >= 35)
                .build();
    }

    private HorizonBand evaluateMediumHold(int itemId, PriceAggregate priceData) {
        double score = trendHoldScorer.scoreMediumTermValue(itemId, priceData);
        if (score < 20) return null;

        double estimatedRoi = calculateMediumHoldRoi(itemId, priceData, score);

        List<String> catalysts = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        String reasoning;

        // Check for Wednesday update plays
        if (isUpdateDriven(itemId)) {
            catalysts.add("Wednesday update likely to affect this item");
            catalysts.add("Historical volatility around update cycle");
            reasoning = "Update-driven play. Jagex Wednesday updates cause volatility. Buy pre-update, " +
                    "sell post-announcement. 1-2 week horizon based on update cycle.";
        } else if (isBanWaveRecovery(itemId)) {
            catalysts.add("Bot-farmed item showing recovery post-ban wave");
            catalysts.add("Supply gradually normalizing");
            reasoning = "Ban wave recovery play. Bots are being removed faster than they return. " +
                    "Item supply will tighten over 1-2 weeks as botted supply dries up.";
        } else {
            catalysts.add("Seasonal demand pattern");
            catalysts.add("Price mean reversion expected");
            reasoning = "Medium-term value play based on seasonal or cyclical patterns. " +
                    "Expected ROI over 1-2 weeks as market recognizes undervaluation.";
        }

        risks.add("Update could affect this item negatively");
        risks.add("Bot detection could improve, flooding supply");
        risks.add("Player behavior shift could change demand");

        return HorizonBand.builder()
                .type(HorizonType.MEDIUM_HOLD)
                .minDays(7)
                .maxDays(MEDIUM_HOLD_MAX_DAYS)
                .estimatedRoi(estimatedRoi)
                .annualizedRoi(estimatedRoi * 365.0 / 10.0) // Annualize for ~10 day hold
                .confidence(Math.min(100, score + 15))
                .reasoning(reasoning)
                .catalysts(catalysts)
                .risks(risks)
                .riskReward(RiskRewardRatio.builder()
                        .potentialGain(estimatedRoi)
                        .potentialLoss(estimatedRoi * 0.5)
                        .ratio(estimatedRoi / Math.max(0.1, estimatedRoi * 0.5))
                        .build())
                .recommended(score >= 30)
                .build();
    }

    private HorizonBand evaluateLongHold(int itemId, PriceAggregate priceData) {
        StructuralValueScorer.LongHoldAnalysis analysis =
                structuralValueScorer.analyzeLongHoldPotential(itemId, priceData);

        if (analysis == null || !analysis.isJustified()) {
            return null;
        }

        return HorizonBand.builder()
                .type(HorizonType.LONG_HOLD)
                .minDays(14)
                .maxDays(LONG_HOLD_MAX_DAYS)
                .estimatedRoi(analysis.getEstimatedRoi())
                .annualizedRoi(analysis.getEstimatedRoi() * 365.0 / 21.0) // Annualize for ~21 day hold
                .confidence(analysis.getConfidence())
                .reasoning(analysis.getReasoning())
                .catalysts(analysis.getCatalysts())
                .risks(analysis.getRisks())
                .riskReward(RiskRewardRatio.builder()
                        .potentialGain(analysis.getTargetPrice() - priceData.getCurrentBuyPrice())
                        .potentialLoss(priceData.getCurrentBuyPrice() - analysis.getStopLossPrice())
                        .ratio((analysis.getTargetPrice() - priceData.getCurrentBuyPrice()) /
                               Math.max(1, priceData.getCurrentBuyPrice() - analysis.getStopLossPrice()))
                        .build())
                .recommended(true)
                .build();
    }

    private HorizonBand evaluateVeryLongHold(int itemId, PriceAggregate priceData) {
        StructuralValueScorer.VeryLongHoldAnalysis analysis =
                structuralValueScorer.analyzeVeryLongHoldPotential(itemId, priceData);

        if (analysis == null || !analysis.isStructurallyJustified()) {
            return null;
        }

        return HorizonBand.builder()
                .type(HorizonType.VERY_LONG_HOLD)
                .minDays(30)
                .maxDays(90)
                .estimatedRoi(analysis.getEstimatedRoi())
                .annualizedRoi(analysis.getEstimatedRoi() * 365.0 / 60.0) // Annualize for ~60 day hold
                .confidence(analysis.getConfidence())
                .reasoning(analysis.getReasoning())
                .catalysts(analysis.getCatalysts())
                .risks(analysis.getRisks())
                .riskReward(RiskRewardRatio.builder()
                        .potentialGain(analysis.getTargetPrice() - priceData.getCurrentBuyPrice())
                        .potentialLoss(priceData.getCurrentBuyPrice() - analysis.getStopLossPrice())
                        .ratio((analysis.getTargetPrice() - priceData.getCurrentBuyPrice()) /
                               Math.max(1, priceData.getCurrentBuyPrice() - analysis.getStopLossPrice()))
                        .build())
                .recommended(true)
                .build();
    }

    public List<HorizonReport> getQuickFlips(int limit) {
        return riskAdjustedRanker.rankByHorizon(HorizonType.QUICK_FLIP, limit);
    }

    public List<HorizonReport> getShortHolds(int limit) {
        return riskAdjustedRanker.rankByHorizon(HorizonType.SHORT_HOLD, limit);
    }

    public List<HorizonReport> getMediumHolds(int limit) {
        return riskAdjustedRanker.rankByHorizon(HorizonType.MEDIUM_HOLD, limit);
    }

    public List<HorizonReport> getLongHolds(int limit) {
        return riskAdjustedRanker.rankByHorizon(HorizonType.LONG_HOLD, limit);
    }

    public List<HorizonReport> getVeryLongHolds(int limit) {
        return riskAdjustedRanker.rankByHorizon(HorizonType.VERY_LONG_HOLD, limit);
    }

    // HELPER METHODS

    private static double calculateMargin(PriceAggregate priceData) {
        long margin = priceData.getCurrentSellPrice() - priceData.getCurrentBuyPrice();
        return (margin * 100.0) / priceData.getCurrentBuyPrice();
    }

    private static double estimateFlipsPerHour(int itemId, PriceAggregate priceData) {
        long hourlyVolume = priceData.getHourlyVolume();
        // FIX: Use actual GE buy limit from item mapping (was: price * 10, nonsensical)
        int geLimit = priceData.getBuyLimit();
        if (geLimit <= 0) {
            // Fallback: estimate based on price tier
            long price = priceData.getCurrentBuyPrice();
            if (price >= 10_000_000) geLimit = 8;
            else if (price >= 1_000_000) geLimit = 70;
            else if (price >= 100_000) geLimit = 500;
            else if (price >= 10_000) geLimit = 2000;
            else geLimit = 10000;
        }

        double potentialFlipsBasedOnVolume = hourlyVolume / (double) Math.max(1, geLimit);
        return Math.min(6, potentialFlipsBasedOnVolume); // Cap at 6 flips per hour
    }

    private static double calculateShortHoldRoi(int itemId, PriceAggregate priceData, double score) {
        // Estimate price increase over 1-7 days
        double expectedIncrease = priceData.getCurrentBuyPrice() * 0.02 * (score / 50.0); // 2-4% range
        return (expectedIncrease * 100.0) / priceData.getCurrentBuyPrice();
    }

    private static double calculateMediumHoldRoi(int itemId, PriceAggregate priceData, double score) {
        // Estimate price increase over 1-2 weeks
        double expectedIncrease = priceData.getCurrentBuyPrice() * 0.05 * (score / 50.0); // 5-10% range
        return (expectedIncrease * 100.0) / priceData.getCurrentBuyPrice();
    }

    private static boolean isInDumpRecovery(PriceAggregate priceData) {
        // Check if item recently dumped and now recovering
        if (priceData.getPriceHistory() == null || priceData.getPriceHistory().isEmpty()) {
            return false;
        }
        List<Long> history = priceData.getPriceHistory();
        if (history.size() < 10) return false;

        long lowestRecent = history.stream().skip(Math.max(0, history.size() - 20))
                .mapToLong(Long::longValue).min().orElse(Long.MAX_VALUE);
        long currentPrice = priceData.getCurrentBuyPrice();
        long oldestInWindow = history.get(Math.max(0, history.size() - 20));

        return (oldestInWindow - lowestRecent) > (oldestInWindow * 0.10) &&
               (currentPrice - lowestRecent) > (lowestRecent * 0.02);
    }

    private static boolean isMomentumPositive(PriceAggregate priceData) {
        // Check if price is trending upward
        if (priceData.getPriceHistory() == null || priceData.getPriceHistory().size() < 24) {
            return false;
        }

        List<Long> history = priceData.getPriceHistory();
        int size = history.size();

        long recentAvg = (long) history.stream().skip(Math.max(0, size - 6))
                .mapToLong(Long::longValue).average().orElse(0);
        long olderAvg = (long) history.stream().skip(Math.max(0, size - 24)).limit(18)
                .mapToLong(Long::longValue).average().orElse(0);

        return recentAvg > olderAvg;
    }

    private static boolean isUpdateDriven(int itemId) {
        // Items affected by Wednesday updates (combat gear, skilling items, etc.)
        return (itemId >= 11 && itemId <= 14) || // Armor
               (itemId >= 2412 && itemId <= 2414) || // Weapons
               (itemId >= 1704 && itemId <= 1712); // Herblore
    }

    private static boolean isBanWaveRecovery(int itemId) {
        // Items commonly botted (herbs, ores, logs, seeds)
        return (itemId >= 199 && itemId <= 213) || // Seeds
               (itemId >= 2484 && itemId <= 2488) || // Herbs
               (itemId >= 436 && itemId <= 449); // Ores
    }

    private static String generateVerdict(PriceAggregate priceData, HorizonBand bestBand, List<HorizonBand> allBands) {
        if (bestBand == null || allBands.isEmpty()) {
            return "No profitable horizon identified for this item.";
        }

        StringBuilder sb = new StringBuilder();

        if (bestBand.getType() == HorizonType.QUICK_FLIP) {
            sb.append("QUICK FLIP: ");
        } else if (bestBand.getType() == HorizonType.SHORT_HOLD) {
            sb.append("SHORT HOLD (1-7 days): ");
        } else if (bestBand.getType() == HorizonType.MEDIUM_HOLD) {
            sb.append("MEDIUM HOLD (1-2 weeks): ");
        } else if (bestBand.getType() == HorizonType.LONG_HOLD) {
            sb.append("LONG HOLD (2-4 weeks): ");
        } else {
            sb.append("VERY LONG HOLD (1+ month): ");
        }

        sb.append(String.format("Estimated %.2f%% ROI. %s",
                bestBand.getEstimatedRoi() * 100, bestBand.getReasoning()));

        return sb.toString();
    }

    // INNER CLASSES

    private static class QuickFlipScorer {
        private final PriceService priceService;

        QuickFlipScorer(PriceService priceService) {
            this.priceService = priceService;
        }

        double scoreQuickFlip(int itemId, PriceAggregate priceData) {
            double score = 50; // Base score

            // Margin quality
            double margin = calculateMargin(priceData);
            if (margin >= 5) score += 20;
            else if (margin >= 3) score += 10;
            else score -= 10;

            // Volume
            long hourlyVolume = priceData.getHourlyVolume();
            if (hourlyVolume > 200) score += 20;
            else if (hourlyVolume > 50) score += 10;
            else score -= 20;

            // Spread tightness
            double spreadPercent = calculateMargin(priceData);
            if (spreadPercent <= 2) score += 15;
            else if (spreadPercent <= 5) score += 8;
            else score -= 10;

            // Data freshness
            if (priceData.getLastUpdated() != null) {
                long minutesOld = (System.currentTimeMillis() - priceData.getLastUpdated().getTime()) / 60000;
                if (minutesOld <= 5) score += 10;
                else if (minutesOld <= 15) score += 5;
                else score -= 15;
            }

            return Math.max(0, Math.min(100, score));
        }
    }

    private static class TrendHoldScorer {
        private final PriceService priceService;

        TrendHoldScorer(PriceService priceService) {
            this.priceService = priceService;
        }

        double scoreShortTermTrend(int itemId, PriceAggregate priceData) {
            double score = 50;

            if (isMomentumPositive(priceData)) score += 20;
            if (isInDumpRecovery(priceData)) score += 15;

            if (priceData.getHourlyVolume() < 20) score -= 15;

            return Math.max(0, Math.min(100, score));
        }

        double scoreMediumTermValue(int itemId, PriceAggregate priceData) {
            double score = 50;

            if (isUpdateDriven(itemId)) score += 20;
            if (isBanWaveRecovery(itemId)) score += 15;
            if (ITEM_SINK_IDS.contains(itemId)) score += 10;

            return Math.max(0, Math.min(100, score));
        }
    }

    private static class StructuralValueScorer {
        private final PriceService priceService;

        StructuralValueScorer(PriceService priceService) {
            this.priceService = priceService;
        }

        @Data
        private static class LongHoldAnalysis {
            private boolean justified;
            private double estimatedRoi;
            private double confidence;
            private String reasoning;
            private List<String> catalysts;
            private List<String> risks;
            private long targetPrice;
            private long stopLossPrice;
            private int timeHorizonDays;

            public LongHoldAnalysis(boolean justified, double estimatedRoi, double confidence,
                    String reasoning, List<String> catalysts, List<String> risks,
                    long targetPrice, long stopLossPrice, int timeHorizonDays) {
                this.justified = justified;
                this.estimatedRoi = estimatedRoi;
                this.confidence = confidence;
                this.reasoning = reasoning;
                this.catalysts = catalysts;
                this.risks = risks;
                this.targetPrice = targetPrice;
                this.stopLossPrice = stopLossPrice;
                this.timeHorizonDays = timeHorizonDays;
            }
        }

        @Data
        private static class VeryLongHoldAnalysis {
            private boolean structurallyJustified;
            private double estimatedRoi;
            private double confidence;
            private String reasoning;
            private List<String> catalysts;
            private List<String> risks;
            private long targetPrice;
            private long stopLossPrice;
            private int timeHorizonDays;

            public VeryLongHoldAnalysis(boolean structurallyJustified, double estimatedRoi, double confidence,
                    String reasoning, List<String> catalysts, List<String> risks,
                    long targetPrice, long stopLossPrice, int timeHorizonDays) {
                this.structurallyJustified = structurallyJustified;
                this.estimatedRoi = estimatedRoi;
                this.confidence = confidence;
                this.reasoning = reasoning;
                this.catalysts = catalysts;
                this.risks = risks;
                this.targetPrice = targetPrice;
                this.stopLossPrice = stopLossPrice;
                this.timeHorizonDays = timeHorizonDays;
            }
        }

        LongHoldAnalysis analyzeLongHoldPotential(int itemId, PriceAggregate priceData) {
            List<String> catalysts = new ArrayList<>();
            List<String> risks = new ArrayList<>();
            String reasoning = "";
            boolean justified = false;
            double estimatedRoi = 0;
            double confidence = 50;
            long targetPrice = priceData.getCurrentBuyPrice();
            long stopLossPrice = (long)(priceData.getCurrentBuyPrice() * 0.95);
            int timeHorizonDays = 21;

            // Check item sink pressure
            if (ITEM_SINK_IDS.contains(itemId)) {
                long estimatedRemovalPerDay = 3; // Conservative estimate
                long currentSupply = priceData.getCurrentBuyPrice() * 1000; // Rough estimate
                long daysToMeaningfulImpact = currentSupply / (estimatedRemovalPerDay * 1000);

                if (daysToMeaningfulImpact <= 28) {
                    catalysts.add(String.format("Item sink removing ~%d/day. Supply tightens in %d+ days.",
                            estimatedRemovalPerDay, daysToMeaningfulImpact));
                    reasoning = String.format("Item sink pressure. This item is being removed from the game " +
                            "at ~%d per day. Meaningful supply reduction in ~%d days will support price.",
                            estimatedRemovalPerDay, daysToMeaningfulImpact);
                    estimatedRoi = 0.05 * (28.0 / daysToMeaningfulImpact); // 5-10% depending on timeline
                    confidence = 65;
                    justified = true;
                }
            }

            // Check meta shift potential
            if (!justified && isMetaShiftItem(itemId)) {
                catalysts.add("Upcoming meta shift affects this item");
                reasoning = "Meta shift play. Combat rebalance or new content will increase demand " +
                        "for this item over 2-4 weeks as players adapt their gear.";
                estimatedRoi = 0.07;
                confidence = 60;
                justified = true;
            }

            // Check supply reduction signals
            if (!justified && isBotFarmTarget(itemId)) {
                catalysts.add("Bot detection improvements removing supply");
                reasoning = "Bot supply drying up. Recent anti-bot measures removing farmed supply " +
                        "faster than bots return. Price floor rising over 2-4 weeks.";
                estimatedRoi = 0.06;
                confidence = 55;
                justified = true;
            }

            if (justified) {
                risks.add("Timeline could extend beyond 4 weeks");
                risks.add("Jagex could implement alternative item sink");
                risks.add("Player behavior shift could reduce demand");
                targetPrice = (long)(priceData.getCurrentBuyPrice() * (1 + estimatedRoi));
            }

            return justified ? new LongHoldAnalysis(true, estimatedRoi, confidence, reasoning,
                    catalysts, risks, targetPrice, stopLossPrice, timeHorizonDays) : null;
        }

        VeryLongHoldAnalysis analyzeVeryLongHoldPotential(int itemId, PriceAggregate priceData) {
            List<String> catalysts = new ArrayList<>();
            List<String> risks = new ArrayList<>();
            String reasoning = "";
            boolean justified = false;
            double estimatedRoi = 0;
            double confidence = 50;
            long targetPrice = priceData.getCurrentBuyPrice();
            long stopLossPrice = (long)(priceData.getCurrentBuyPrice() * 0.90);
            int timeHorizonDays = 60;

            // Only justify very long holds with STRUCTURAL changes
            if (isStructuralChangeItem(itemId)) {
                catalysts.add("Announced new content affecting this item's ecosystem");
                catalysts.add("Permanent supply reduction mechanism in place");
                reasoning = "Structural change justifying 1+ month hold. New content announced or " +
                        "permanent supply reduction mechanism guarantees meaningful price movement. " +
                        "Requires explicit structural catalyst, not just speculation.";
                estimatedRoi = 0.12;
                confidence = 70;
                justified = true;
            }

            if (justified) {
                risks.add("Jagex could reverse announced changes");
                risks.add("New content could be delayed or cancelled");
                risks.add("Price could rise immediately, eliminating entry opportunity");
                targetPrice = (long)(priceData.getCurrentBuyPrice() * (1 + estimatedRoi));
            }

            return justified ? new VeryLongHoldAnalysis(true, estimatedRoi, confidence, reasoning,
                    catalysts, risks, targetPrice, stopLossPrice, timeHorizonDays) : null;
        }

        private boolean isMetaShiftItem(int itemId) {
            return (itemId >= 11 && itemId <= 14) || (itemId >= 2412 && itemId <= 2414);
        }

        private boolean isBotFarmTarget(int itemId) {
            return (itemId >= 199 && itemId <= 213) || (itemId >= 2484 && itemId <= 2488) ||
                    (itemId >= 436 && itemId <= 449);
        }

        private boolean isStructuralChangeItem(int itemId) {
            // Items explicitly mentioned in announced content updates
            return false; // Would be populated when Jagex announces updates
        }
    }

    private static class RiskAdjustedRanker {
        private final ConcurrentHashMap<Integer, HorizonReport> reportCache;

        RiskAdjustedRanker(ConcurrentHashMap<Integer, HorizonReport> reportCache) {
            this.reportCache = reportCache;
        }

        List<HorizonReport> rankByHorizon(HorizonType type, int limit) {
            return reportCache.values().stream()
                    .filter(Objects::nonNull)
                    .filter(r -> r.getBands().stream().anyMatch(b -> b.getType() == type))
                    .sorted((r1, r2) -> {
                        HorizonBand b1 = r1.getBands().stream()
                                .filter(b -> b.getType() == type).findFirst().orElse(null);
                        HorizonBand b2 = r2.getBands().stream()
                                .filter(b -> b.getType() == type).findFirst().orElse(null);

                        if (b1 == null || b2 == null) return 0;
                        if (!b1.isRecommended()) return 1;
                        if (!b2.isRecommended()) return -1;

                        // Rank by annualized ROI
                        return Double.compare(b2.getAnnualizedRoi(), b1.getAnnualizedRoi());
                    })
                    .limit(limit)
                    .collect(Collectors.toList());
        }
    }
}

// MODEL CLASSES

@Data
@Builder
class HorizonReport {
    private int itemId;
    private String itemName;
    private long currentBuyPrice;
    private long currentSellPrice;
    private List<HorizonBand> bands;
    private HorizonBand bestBand;
    private String overallVerdict;
    private double bestRoi;
    private int bestHoldDays;
}

@Data
@Builder
class HorizonBand {
    private HorizonType type;
    private int minDays;
    private int maxDays;
    private double estimatedRoi;
    private double annualizedRoi;
    private double confidence; // 0-100
    private String reasoning;
    private List<String> catalysts;
    private List<String> risks;
    private RiskRewardRatio riskReward;
    private boolean recommended;
}

@Data
@Builder
class RiskRewardRatio {
    private double potentialGain;
    private double potentialLoss;
    private double ratio; // gain / loss
}

enum HorizonType {
    QUICK_FLIP,
    SHORT_HOLD,
    MEDIUM_HOLD,
    LONG_HOLD,
    VERY_LONG_HOLD
}
