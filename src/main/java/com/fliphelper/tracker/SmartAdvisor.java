package com.fliphelper.tracker;

import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SmartAdvisor - Unified Intelligence Engine for Grand Flip Out.
 * Combines signals from all analysis engines into a single, actionable
 * recommendation per item.
 *
 * INFORMATION ONLY - does not automate any trades.
 */
@Slf4j
public class SmartAdvisor {

    private static final int PRICE_STALENESS_WARN_SECS  = 300;
    private static final int PRICE_STALENESS_AVOID_SECS = 900;
    private static final long GE_LIMIT_WINDOW_SECS = 4 * 60 * 60;

    private final PriceService priceService;
    private final MarketIntelligenceEngine marketIntelligence;
    private final BotEconomyTracker botEconomy;
    private final JagexTradeIndex jti;
    private final DumpDetector dumpDetector;
    private final DumpKnowledgeEngine dumpKnowledge;
    private final DataQualityAnalyzer dataQuality;
    private final RiskManager riskManager;
    private final InvestmentHorizonAnalyzer horizonAnalyzer;
    private final PriceHistoryCollector historyCollector;

    public SmartAdvisor(PriceService priceService,
                        MarketIntelligenceEngine marketIntelligence,
                        BotEconomyTracker botEconomy,
                        JagexTradeIndex jti,
                        DumpDetector dumpDetector,
                        DumpKnowledgeEngine dumpKnowledge,
                        DataQualityAnalyzer dataQuality,
                        RiskManager riskManager,
                        InvestmentHorizonAnalyzer horizonAnalyzer,
                        PriceHistoryCollector historyCollector) {
        this.priceService = priceService;
        this.marketIntelligence = marketIntelligence;
        this.botEconomy = botEconomy;
        this.jti = jti;
        this.dumpDetector = dumpDetector;
        this.dumpKnowledge = dumpKnowledge;
        this.dataQuality = dataQuality;
        this.riskManager = riskManager;
        this.horizonAnalyzer = horizonAnalyzer;
        this.historyCollector = historyCollector;
    }

    public enum SmartAction {
        STRONG_BUY("STRONG BUY", "Excellent opportunity - multiple signals align"),
        BUY("BUY", "Good opportunity - favorable conditions"),
        HOLD("HOLD", "Wait - no clear edge right now"),
        SELL("SELL", "Consider selling - conditions weakening"),
        STRONG_SELL("STRONG SELL", "Exit position - strong negative signals"),
        AVOID("AVOID", "Stay away - high risk or poor data");

        @Getter private final String label;
        @Getter private final String description;
        SmartAction(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    public enum Confidence { HIGH, MEDIUM, LOW }
    public enum RiskLevel  { LOW, MODERATE, HIGH, EXTREME }

    @Data @Builder @AllArgsConstructor
    public static class SmartPick {
        private int itemId;
        private String itemName;
        private int smartScore;
        private SmartAction action;
        private Confidence confidence;
        private RiskLevel risk;
        private List<String> reasons;
        private List<String> warnings;
        private long currentPrice;
        private long estimatedProfitLow;
        private long estimatedProfitHigh;
        private String holdTime;
        private int jtiScore;
        private int rsi;
        private String regime;
        private String botImpact;
        private boolean geLimitWarning;
        private long priceAgeSecs;
        private long timestamp;
    }

    @Data @Builder @AllArgsConstructor
    public static class MarketOverview {
        private int totalItemsAnalyzed;
        private int strongBuys;
        private int buys;
        private int sells;
        private int strongSells;
        private String marketMood;
        private String botActivity;
        private int marketHealthScore;
        private List<SmartPick> topPicks;
        private List<SmartPick> topSells;
        private List<String> alerts;
        private long timestamp;
    }

    public SmartPick analyze(int itemId) {
        PriceAggregate agg = priceService.getPriceAggregate(itemId);
        if (agg == null) return null;

        List<String> reasons  = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        double score = 50.0;

        long currentPrice = agg.getCurrentPrice();
        String itemName   = agg.getItemName();

        long priceAgeSecs = computePriceAgeSecs(agg);
        if (priceAgeSecs >= PRICE_STALENESS_AVOID_SECS) {
            warnings.add("Price data is " + (priceAgeSecs / 60) + " min old - may be stale");
            score -= 15;
        } else if (priceAgeSecs >= PRICE_STALENESS_WARN_SECS) {
            warnings.add("Price data is " + (priceAgeSecs / 60) + " min old - refresh recommended");
            score -= 5;
        }

        int jtiScore = 0;
        try {
            JagexTradeIndex.JTIResult jtiResult = jti.compute(agg);
            if (jtiResult != null) {
                jtiScore = (int) jtiResult.getJtiScore();
                score += (jtiScore / 100.0) * 25 - 12.5;
                if (jtiScore >= 80) reasons.add("Excellent JTI score (" + jtiScore + "/100)");
                else if (jtiScore >= 60) reasons.add("Good JTI score (" + jtiScore + "/100)");
                else if (jtiScore < 30) warnings.add("Low JTI score (" + jtiScore + "/100)");
            }
        } catch (Exception e) {
            log.debug("JTI failed for {}: {}", itemId, e.getMessage());
        }

        int rsi = 50;
        String regime = "UNKNOWN";
        try {
            MarketIntelligenceEngine.MarketReport report = marketIntelligence.getFullAnalysis(itemId);
            if (report != null) {
                rsi    = report.getRsi();
                regime = report.getMarketRegime().name();
                if (rsi < 25) { score += 10; reasons.add("RSI extremely oversold (" + rsi + ")"); }
                else if (rsi < 35) { score += 6; reasons.add("RSI oversold (" + rsi + ")"); }
                else if (rsi > 75) { score -= 10; warnings.add("RSI overbought (" + rsi + ")"); }
                else if (rsi > 65) { score -= 4; warnings.add("RSI high (" + rsi + ")"); }

                if (report.getMomentumSignal() != null) {
                    if (report.getMomentumSignal().getSignal() == MarketIntelligenceEngine.Signal.BUY) {
                        score += 5; reasons.add("Positive momentum (EMA crossover)");
                    } else if (report.getMomentumSignal().getSignal() == MarketIntelligenceEngine.Signal.SELL) {
                        score -= 5; warnings.add("Negative momentum (bearish EMA crossover)");
                    }
                }

                if (report.getMeanReversionSignal() != null && report.getMeanReversionSignal().isDetected()) {
                    double z = report.getMeanReversionSignal().getZScore();
                    if (z < -2) { score += 8; reasons.add("Mean reversion: price 2+ stddev below mean"); }
                    else if (z > 2) { score -= 8; warnings.add("Mean reversion: price 2+ stddev above mean"); }
                }

                if (report.getMarketRegime() == MarketIntelligenceEngine.MarketRegime.TRENDING_UP) {
                    score += 5; reasons.add("Regime: uptrend");
                } else if (report.getMarketRegime() == MarketIntelligenceEngine.MarketRegime.DEAD) {
                    score -= 10; warnings.add("Regime: DEAD - no liquidity");
                } else if (report.getMarketRegime() == MarketIntelligenceEngine.MarketRegime.VOLATILE) {
                    warnings.add("Regime: volatile");
                }

                if (report.getLiquidityClass() == MarketIntelligenceEngine.LiquidityClass.ILLIQUID) {
                    score -= 8; warnings.add("Very low liquidity");
                } else if (report.getLiquidityClass() == MarketIntelligenceEngine.LiquidityClass.HIGHLY_LIQUID) {
                    score += 3; reasons.add("High liquidity");
                }

                if (report.isSinkTarget() && report.getSinkPressure() > 5) {
                    score += 4; reasons.add("Item sink active - price support");
                }
            }
        } catch (Exception e) {
            log.debug("Market intelligence failed for {}: {}", itemId, e.getMessage());
        }

        String botImpact = "None";
        try {
            if (botEconomy.isBotAffectedItem(itemId)) {
                BotEconomyTracker.BotItemProfile profile = botEconomy.getBotItemProfile(itemId);
                botImpact = profile.getPhase().name() + " (" + (int) profile.getTypicalBotSupplyPercent() + "% bot supply)";
                BotEconomyTracker.SupplyShockDetector shock = botEconomy.getSupplyShockDetector();
                double banWaveConf = shock.getBanWaveConfidence();
                if (banWaveConf > 60) { score += 12; reasons.add("BAN WAVE DETECTED (" + (int) banWaveConf + "%)"); }
                else if (banWaveConf > 30) { score += 6; reasons.add("Possible ban wave (" + (int) banWaveConf + "%)"); }
                if (shock.getSupplyShockItems().contains(itemId)) { score += 5; reasons.add("Supply shock active"); }
                if (shock.getSupplyFloodItems().contains(itemId)) { score -= 5; warnings.add("Bot supply flood"); }
                BotEconomyTracker.PipelineStage stage = botEconomy.getPipelineTracker().getPipelineStage();
                if (stage == BotEconomyTracker.PipelineStage.BOTS_BANNED && profile.getTypicalBotSupplyPercent() > 40) {
                    score += 8; reasons.add("Bot pipeline: BANNED - supply constrained");
                }
            }
        } catch (Exception e) {
            log.debug("Bot economy failed for {}: {}", itemId, e.getMessage());
        }

        try {
            List<DumpDetector.PriceAlert> dumpAlerts = dumpDetector.detectAnomalies();
            for (DumpDetector.PriceAlert alert : dumpAlerts) {
                if (alert.getItemId() == itemId) {
                    if (alert.getType() == DumpDetector.AlertType.DUMP) {
                        score += 10;
                        reasons.add("DUMP: " + String.format("%.1f%%", alert.getLowDeviation()) + " drop");
                    } else if (alert.getType() == DumpDetector.AlertType.PUMP) {
                        score -= 5;
                        warnings.add("PUMP: " + String.format("%.1f%%", alert.getHighDeviation()) + " spike");
                    } else if (alert.getType() == DumpDetector.AlertType.VOLUME_SPIKE) {
                        reasons.add("Volume spike detected");
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Dump detection failed for {}: {}", itemId, e.getMessage());
        }

        long   margin       = agg.getConsensusMargin();
        double marginPct    = agg.getConsensusMarginPercent();
        long   volume       = agg.getTotalVolume1h();
        long   taxPerItem   = Math.min((long) (agg.getBestHighPrice() * 0.02), 5_000_000L);
        long   afterTaxMargin = margin - taxPerItem;

        if (afterTaxMargin <= 0) { score -= 10; warnings.add("Margin negative after 2% GE tax"); }
        else if (marginPct >= 3.0 && marginPct <= 15.0) { score += 8; reasons.add("Healthy margin: " + String.format("%.1f%%", marginPct)); }
        else if (marginPct > 15.0) { score += 3; warnings.add("Very wide margin (" + String.format("%.1f%%", marginPct) + "%)"); }
        else if (marginPct < 1.0 && marginPct > 0) { score -= 5; warnings.add("Thin margin (" + String.format("%.1f%%", marginPct) + "%)"); }

        if (volume > 5000) score += 5;
        else if (volume > 1000) score += 2;
        else if (volume < 100) { score -= 8; warnings.add("Very low volume (" + volume + "/hr)"); }

        try {
            DataQualityAnalyzer.DataQualityReport quality = dataQuality.getDataQuality(itemId);
            if (quality != null) {
                if (quality.getOverallScore() < 40) { score -= 10; warnings.add("LOW DATA QUALITY (" + quality.getOverallScore() + "/100)"); }
                if (quality.getManipulationRisk() != null &&
                    quality.getManipulationRisk().getManipulationRisk() == DataQualityAnalyzer.ManipulationRisk.HIGH) {
                    score -= 12; warnings.add("HIGH MANIPULATION RISK");
                }
            }
        } catch (Exception e) {
            log.debug("Data quality failed for {}: {}", itemId, e.getMessage());
        }

        boolean geLimitWarning = false;
        int buyLimit = agg.getBuyLimit();
        try {
            if (buyLimit > 0) {
                long lastBuy = historyCollector.getLastBuyTimestamp(itemId);
                if (lastBuy > 0) {
                    long secsRemaining = GE_LIMIT_WINDOW_SECS - (Instant.now().getEpochSecond() - lastBuy);
                    if (secsRemaining > 0) {
                        geLimitWarning = true;
                        warnings.add("GE limit resets in ~" + (secsRemaining / 60) + " min");
                        score -= 5;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("GE limit check failed for {}: {}", itemId, e.getMessage());
        }

        int finalScore = (int) Math.max(0, Math.min(100, score));
        SmartAction action;
        if (finalScore >= 80)      action = SmartAction.STRONG_BUY;
        else if (finalScore >= 65) action = SmartAction.BUY;
        else if (finalScore >= 45) action = SmartAction.HOLD;
        else if (finalScore >= 30) action = SmartAction.SELL;
        else if (finalScore >= 15) action = SmartAction.STRONG_SELL;
        else                       action = SmartAction.AVOID;

        boolean hasCritical = warnings.stream().anyMatch(w ->
            w.contains("MANIPULATION") || w.contains("DEAD") || w.contains("LOW DATA") || w.contains("negative after"));
        if (hasCritical && (action == SmartAction.STRONG_BUY || action == SmartAction.BUY)) {
            action = SmartAction.HOLD;
            warnings.add("Downgraded to HOLD due to risk flags");
        }

        int dataPoints = historyCollector.getDataPointCount(itemId);
        Confidence confidence;
        if (dataPoints >= 50 && warnings.size() <= 1 && reasons.size() >= 3 && priceAgeSecs < PRICE_STALENESS_WARN_SECS)
            confidence = Confidence.HIGH;
        else if (dataPoints >= 14 && warnings.size() <= 2 && priceAgeSecs < PRICE_STALENESS_AVOID_SECS)
            confidence = Confidence.MEDIUM;
        else
            confidence = Confidence.LOW;

        RiskLevel risk;
        if (warnings.size() >= 4 || marginPct > 20 || volume < 50) risk = RiskLevel.EXTREME;
        else if (warnings.size() >= 2 || marginPct > 10)           risk = RiskLevel.HIGH;
        else if (warnings.size() >= 1)                              risk = RiskLevel.MODERATE;
        else                                                         risk = RiskLevel.LOW;

        long profitLow  = Math.max(0, buyLimit > 0 ? (long)(afterTaxMargin * buyLimit * 0.5) : afterTaxMargin * 10);
        long profitHigh = Math.max(0, buyLimit > 0 ? (long)(afterTaxMargin * buyLimit * 0.9) : afterTaxMargin * 50);

        String holdTime;
        if      (volume > 10000) holdTime = "< 1 hour";
        else if (volume > 1000)  holdTime = "1-4 hours";
        else if (volume > 100)   holdTime = "4-12 hours";
        else                     holdTime = "1-3 days";

        return SmartPick.builder()
            .itemId(itemId).itemName(itemName).smartScore(finalScore)
            .action(action).confidence(confidence).risk(risk)
            .reasons(reasons).warnings(warnings).currentPrice(currentPrice)
            .estimatedProfitLow(profitLow).estimatedProfitHigh(profitHigh)
            .holdTime(holdTime).jtiScore(jtiScore).rsi(rsi).regime(regime)
            .botImpact(botImpact).geLimitWarning(geLimitWarning)
            .priceAgeSecs(priceAgeSecs).timestamp(System.currentTimeMillis())
            .build();
    }

    public List<SmartPick> getTopPicks(int limit) {
        try {
            return priceService.getTopByMargin(Math.min(200, limit * 4), 10).parallelStream()
                .map(agg -> analyze(agg.getItemId())).filter(Objects::nonNull)
                .filter(p -> p.getSmartScore() >= 55)
                .sorted(Comparator.comparingInt(SmartPick::getSmartScore).reversed())
                .limit(limit).collect(Collectors.toList());
        } catch (Exception e) { log.error("Error getting top picks", e); return Collections.emptyList(); }
    }

    public List<SmartPick> getTopSells(int limit) {
        try {
            return priceService.getTopByMargin(200, 10).parallelStream()
                .map(agg -> analyze(agg.getItemId())).filter(Objects::nonNull)
                .filter(p -> p.getSmartScore() < 35)
                .sorted(Comparator.comparingInt(SmartPick::getSmartScore))
                .limit(limit).collect(Collectors.toList());
        } catch (Exception e) { log.error("Error getting top sells", e); return Collections.emptyList(); }
    }

    public MarketOverview getMarketOverview() {
        try {
            List<SmartPick> picks = getTopPicks(50);
            List<SmartPick> sells = getTopSells(10);
            long sb = picks.stream().filter(p -> p.getAction() == SmartAction.STRONG_BUY).count();
            long b  = picks.stream().filter(p -> p.getAction() == SmartAction.BUY).count();
            double buyRatio = (double)(sb + b) / Math.max(1, picks.size());
            String mood = buyRatio > 0.6 ? "Bullish" : buyRatio < 0.3 ? "Bearish" : "Neutral";
            double banConf = botEconomy.getSupplyShockDetector().getBanWaveConfidence();
            String botAct = banConf > 50 ? "Ban wave active (" + (int)banConf + "%)" :
                            botEconomy.getSupplyShockDetector().detectBotReturnSignals() ? "Bots returning" : "Equilibrium";
            int health = botEconomy.getMarketHealth().getMarketHealth();
            List<String> alerts = new ArrayList<>();
            if (banConf > 30) alerts.add("Ban wave signals detected");
            BotEconomyTracker.PipelineStage stage = botEconomy.getPipelineTracker().getPipelineStage();
            if (stage != BotEconomyTracker.PipelineStage.EQUILIBRIUM) alerts.add("Bot pipeline: " + stage.name());
            if (health < 50) alerts.add("Market health low (" + health + "/100)");
            return MarketOverview.builder()
                .totalItemsAnalyzed(picks.size()).strongBuys((int)sb).buys((int)b)
                .sells(sells.size()).strongSells((int)sells.stream().filter(p -> p.getAction() == SmartAction.STRONG_SELL).count())
                .marketMood(mood).botActivity(botAct).marketHealthScore(health)
                .topPicks(picks.stream().limit(10).collect(Collectors.toList()))
                .topSells(sells.stream().limit(5).collect(Collectors.toList()))
                .alerts(alerts).timestamp(System.currentTimeMillis()).build();
        } catch (Exception e) { log.error("Error generating market overview", e); return null; }
    }

    private long computePriceAgeSecs(PriceAggregate agg) {
        try {
            long ts = agg.getLastUpdatedEpochSecs();
            return ts <= 0 ? 0 : Math.max(0, Instant.now().getEpochSecond() - ts);
        } catch (Exception e) { return 0; }
    }
                        }
