package com.fliphelper.tracker;

import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

// Combines signals from all engines into a single recommendation per item.
@Slf4j
public class SmartAdvisor {

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
    private final ServerIntelligenceConfig serverConfig;
    private final ServerIntelligenceClient serverClient;

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

        // Initialize server-side intelligence support
        this.serverConfig = new ServerIntelligenceConfig();
        this.serverClient = new ServerIntelligenceClient(serverConfig);

        log.info("SmartAdvisor initialized with server-side intelligence support - fallback: {}",
                 serverConfig.getFallbackDescription());
    }

    public enum SmartAction {
        STRONG_BUY("STRONG BUY", "Excellent opportunity — multiple signals align"),
        BUY("BUY", "Good opportunity — favorable conditions"),
        HOLD("HOLD", "Wait — no clear edge right now"),
        SELL("SELL", "Consider selling — conditions weakening"),
        STRONG_SELL("STRONG SELL", "Exit position — strong negative signals"),
        AVOID("AVOID", "Stay away — high risk or poor data");

        @Getter private final String label;
        @Getter private final String description;

        SmartAction(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    public enum Confidence {
        HIGH, MEDIUM, LOW
    }

    public enum RiskLevel {
        LOW, MODERATE, HIGH, EXTREME
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class SmartPick {
        private int itemId;
        private String itemName;
        private int smartScore;           // 0-100 composite
        private SmartAction action;
        private Confidence confidence;
        private RiskLevel risk;
        private List<String> reasons;     // Human-readable explanations
        private List<String> warnings;    // Red flags
        private long currentPrice;
        private long estimatedProfitLow;  // Conservative estimate
        private long estimatedProfitHigh; // Optimistic estimate
        private String holdTime;          // e.g. "2-4 hours", "1-3 days"
        private int jtiScore;
        private int rsi;
        private String regime;
        private String botImpact;
        private long timestamp;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class MarketOverview {
        private int totalItemsAnalyzed;
        private int strongBuys;
        private int buys;
        private int sells;
        private int strongSells;
        private String marketMood;        // "Bullish", "Bearish", "Neutral", "Volatile"
        private String botActivity;       // "Ban wave active", "Bots returning", "Equilibrium"
        private int marketHealthScore;
        private List<SmartPick> topPicks;
        private List<SmartPick> topSells;
        private List<String> alerts;
        private long timestamp;
    }

    
    public SmartPick analyze(int itemId) {
        // ATTEMPT 1: Try server-side intelligence (PRIMARY)
        // Scoring logic runs server-side for data freshness
        if (serverConfig.isUseServerSideIntelligence() && serverConfig.isEnableSmartAdvisor()) {
            try {
                var serverResult = serverClient.getSmartAdvisor(itemId);
                if (serverResult.isPresent()) {
                    // Server result exists - convert and return
                    return convertServerSmartAdvisor(serverResult.get());
                }
            } catch (Exception e) {
                log.debug("Server SmartAdvisor unavailable for item {}, falling back to local", itemId, e);
            }
        }

        // ATTEMPT 2: Fallback to local (ALWAYS AVAILABLE)
        // If server is down or disabled, use local computation
        if (!serverConfig.isFallbackToLocalOnError()) {
            log.warn("Server-side intelligence required but unavailable for item {}", itemId);
            return null;
        }

        return analyzeLocal(itemId);
    }

    
    private SmartPick analyzeLocal(int itemId) {
        PriceAggregate agg = priceService.getPriceAggregate(itemId);
        if (agg == null) return null;

        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        double score = 50.0; // Start neutral

        long currentPrice = agg.getCurrentPrice();
        String itemName = agg.getItemName();

        // === 1. JTI Score (weight: 25%) ===
        int jtiScore = 0;
        try {
            JagexTradeIndex.JTIResult jtiResult = jti.compute(agg);
            if (jtiResult != null) {
                jtiScore = (int) jtiResult.getJtiScore();
                double jtiComponent = (jtiScore / 100.0) * 25;
                score += jtiComponent - 12.5; // Center around 0

                if (jtiScore >= 80) reasons.add("Excellent JTI score (" + jtiScore + "/100) — strong flippability");
                else if (jtiScore >= 60) reasons.add("Good JTI score (" + jtiScore + "/100)");
                else if (jtiScore < 30) {
                    warnings.add("Low JTI score (" + jtiScore + "/100) — poor flipping conditions");
                }
            }
        } catch (Exception e) {
            log.debug("JTI calculation failed for {}: {}", itemId, e.getMessage());
        }

        // === 2. Technical Analysis (weight: 20%) ===
        int rsi = 50;
        String regime = "UNKNOWN";
        try {
            MarketIntelligenceEngine.MarketReport report = marketIntelligence.getFullAnalysis(itemId);
            if (report != null) {
                rsi = report.getRsi();
                regime = report.getMarketRegime().name();

                // RSI signals
                if (rsi < 25) {
                    score += 10;
                    reasons.add("RSI extremely oversold (" + rsi + ") — strong rebound likely");
                } else if (rsi < 35) {
                    score += 6;
                    reasons.add("RSI oversold (" + rsi + ") — potential buying opportunity");
                } else if (rsi > 75) {
                    score -= 10;
                    reasons.add("RSI overbought (" + rsi + ") — price may be peaking");
                } else if (rsi > 65) {
                    score -= 4;
                    warnings.add("RSI getting high (" + rsi + ") — watch for reversal");
                }

                // Momentum
                if (report.getMomentumSignal() != null) {
                    if (report.getMomentumSignal().getSignal() == MarketIntelligenceEngine.Signal.BUY) {
                        score += 5;
                        reasons.add("Positive momentum detected (EMA crossover)");
                    } else if (report.getMomentumSignal().getSignal() == MarketIntelligenceEngine.Signal.SELL) {
                        score -= 5;
                        reasons.add("Negative momentum (bearish EMA crossover)");
                    }
                }

                // Mean reversion
                if (report.getMeanReversionSignal() != null && report.getMeanReversionSignal().isDetected()) {
                    double z = report.getMeanReversionSignal().getZScore();
                    if (z < -2) {
                        score += 8;
                        reasons.add("Price 2+ std devs below mean — mean reversion opportunity");
                    } else if (z > 2) {
                        score -= 8;
                        reasons.add("Price 2+ std devs above mean — likely to pull back");
                    }
                }

                // Regime
                if (report.getMarketRegime() == MarketIntelligenceEngine.MarketRegime.TRENDING_UP) {
                    score += 5;
                    reasons.add("Market regime: uptrend");
                } else if (report.getMarketRegime() == MarketIntelligenceEngine.MarketRegime.DEAD) {
                    score -= 10;
                    warnings.add("Market regime: DEAD — no liquidity");
                } else if (report.getMarketRegime() == MarketIntelligenceEngine.MarketRegime.VOLATILE) {
                    warnings.add("Market regime: volatile — wider stops needed");
                }

                // Liquidity
                if (report.getLiquidityClass() == MarketIntelligenceEngine.LiquidityClass.ILLIQUID) {
                    score -= 8;
                    warnings.add("Very low liquidity — hard to exit positions");
                } else if (report.getLiquidityClass() == MarketIntelligenceEngine.LiquidityClass.HIGHLY_LIQUID) {
                    score += 3;
                    reasons.add("High liquidity — easy entry/exit");
                }

                // Sink pressure
                if (report.isSinkTarget() && report.getSinkPressure() > 5) {
                    score += 4;
                    reasons.add("Item sink active — long-term price support");
                }
            }
        } catch (Exception e) {
            log.debug("Market intelligence failed for {}: {}", itemId, e.getMessage());
        }

        // === 3. Bot Economy (weight: 15%) ===
        String botImpact = "None";
        try {
            if (botEconomy.isBotAffectedItem(itemId)) {
                BotEconomyTracker.BotItemProfile profile = botEconomy.getBotItemProfile(itemId);
                botImpact = profile.getPhase().name() + " (" + (int) profile.getTypicalBotSupplyPercent() + "% bot supply)";

                BotEconomyTracker.SupplyShockDetector shock = botEconomy.getSupplyShockDetector();
                double banWaveConf = shock.getBanWaveConfidence();

                if (banWaveConf > 60) {
                    score += 12;
                    reasons.add("BAN WAVE DETECTED (" + (int) banWaveConf + "% confidence) — bot items spiking!");
                } else if (banWaveConf > 30) {
                    score += 6;
                    reasons.add("Possible ban wave (" + (int) banWaveConf + "% confidence) — monitor closely");
                }

                // Check if this item is currently in supply shock
                if (shock.getSupplyShockItems().contains(itemId)) {
                    score += 5;
                    reasons.add("Item in active supply shock — price elevated");
                }

                // Check if being flooded
                if (shock.getSupplyFloodItems().contains(itemId)) {
                    score -= 5;
                    warnings.add("Bot supply flooding detected — price declining");
                }

                // Pipeline stage awareness
                BotEconomyTracker.F2PToPPipelineTracker pipeline = botEconomy.getPipelineTracker();
                BotEconomyTracker.PipelineStage stage = pipeline.getPipelineStage();
                if (stage == BotEconomyTracker.PipelineStage.BOTS_BANNED && profile.getTypicalBotSupplyPercent() > 40) {
                    score += 8;
                    reasons.add("Bot pipeline: BANNED stage — " + profile.getItemName() + " supply constrained");
                }
            }
        } catch (Exception e) {
            log.debug("Bot economy analysis failed for {}: {}", itemId, e.getMessage());
        }

        // === 4. Dump Detection (weight: 15%) ===
        try {
            List<DumpDetector.PriceAlert> alerts = dumpDetector.detectAnomalies();
            for (DumpDetector.PriceAlert alert : alerts) {
                if (alert.getItemId() == itemId) {
                    if (alert.getType() == DumpDetector.AlertType.DUMP) {
                        score += 10;
                        reasons.add("DUMP DETECTED: " + String.format("%.1f%%", alert.getLowDeviation()) + " drop — potential buy-the-dip");
                    } else if (alert.getType() == DumpDetector.AlertType.PUMP) {
                        score -= 5;
                        warnings.add("PUMP DETECTED: " + String.format("%.1f%%", alert.getHighDeviation()) + " spike — late entry risky");
                    } else if (alert.getType() == DumpDetector.AlertType.VOLUME_SPIKE) {
                        reasons.add("Volume spike detected — increased trading activity");
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Dump detection failed for {}: {}", itemId, e.getMessage());
        }

        // === 5. Margin Quality (weight: 15%) ===
        long margin = agg.getConsensusMargin();
        double marginPct = agg.getConsensusMarginPercent();
        long volume = agg.getTotalVolume1h();

        if (marginPct >= 3.0 && marginPct <= 15.0) {
            score += 8;
            reasons.add("Healthy margin: " + String.format("%.1f%%", marginPct) + " (sweet spot)");
        } else if (marginPct > 15.0) {
            score += 3;
            warnings.add("Very wide margin (" + String.format("%.1f%%", marginPct) + "%) — may be hard to fill");
        } else if (marginPct < 1.0 && marginPct > 0) {
            score -= 5;
            warnings.add("Thin margin (" + String.format("%.1f%%", marginPct) + "%) — barely profitable after tax");
        }

        if (volume > 5000) {
            score += 5;
        } else if (volume > 1000) {
            score += 2;
        } else if (volume < 100) {
            score -= 8;
            warnings.add("Very low volume (" + volume + "/hr) — hard to trade");
        }

        // === 6. Data Quality (weight: 10%) ===
        try {
            DataQualityAnalyzer.DataQualityReport quality = dataQuality.getDataQuality(itemId);
            if (quality != null) {
                if (quality.getOverallScore() < 40) {
                    score -= 10;
                    warnings.add("LOW DATA QUALITY (" + quality.getOverallScore() + "/100) — unreliable prices");
                }
                if (quality.getManipulationRisk() != null &&
                    quality.getManipulationRisk().getManipulationRisk() == DataQualityAnalyzer.ManipulationRisk.HIGH) {
                    score -= 12;
                    warnings.add("HIGH MANIPULATION RISK — possible price fixing detected");
                }
            }
        } catch (Exception e) {
            log.debug("Data quality analysis failed for {}: {}", itemId, e.getMessage());
        }

        // === Clamp and determine action ===
        int finalScore = (int) Math.max(0, Math.min(100, score));

        SmartAction action;
        if (finalScore >= 80) action = SmartAction.STRONG_BUY;
        else if (finalScore >= 65) action = SmartAction.BUY;
        else if (finalScore >= 45) action = SmartAction.HOLD;
        else if (finalScore >= 30) action = SmartAction.SELL;
        else if (finalScore >= 15) action = SmartAction.STRONG_SELL;
        else action = SmartAction.AVOID;

        // Override to AVOID if critical warnings
        if (warnings.stream().anyMatch(w -> w.contains("MANIPULATION") || w.contains("DEAD") || w.contains("LOW DATA"))) {
            if (action == SmartAction.STRONG_BUY || action == SmartAction.BUY) {
                action = SmartAction.HOLD;
                warnings.add("Downgraded from " + action.getLabel() + " due to risk flags");
            }
        }

        // Determine confidence
        Confidence confidence;
        int dataPoints = historyCollector.getDataPointCount(itemId);
        if (dataPoints >= 50 && warnings.size() <= 1 && reasons.size() >= 3) {
            confidence = Confidence.HIGH;
        } else if (dataPoints >= 14 && warnings.size() <= 2) {
            confidence = Confidence.MEDIUM;
        } else {
            confidence = Confidence.LOW;
        }

        // Determine risk
        RiskLevel risk;
        if (warnings.size() >= 4 || marginPct > 20 || volume < 50) {
            risk = RiskLevel.EXTREME;
        } else if (warnings.size() >= 2 || marginPct > 10) {
            risk = RiskLevel.HIGH;
        } else if (warnings.size() >= 1) {
            risk = RiskLevel.MODERATE;
        } else {
            risk = RiskLevel.LOW;
        }

        // Estimate profit range
        int buyLimit = agg.getBuyLimit();
        long estimatedProfitLow = buyLimit > 0 ? (long)(margin * buyLimit * 0.5) : margin * 10;
        long estimatedProfitHigh = buyLimit > 0 ? (long)(margin * buyLimit * 0.9) : margin * 50;

        // Tax adjustment
        long taxPerItem = Math.min((long)(currentPrice * 0.02), 5_000_000L);
        estimatedProfitLow -= taxPerItem * (buyLimit > 0 ? buyLimit : 10);
        estimatedProfitHigh -= taxPerItem * (buyLimit > 0 ? buyLimit : 10);
        estimatedProfitLow = Math.max(0, estimatedProfitLow);
        estimatedProfitHigh = Math.max(0, estimatedProfitHigh);

        // Hold time estimate
        String holdTime;
        if (volume > 10000) holdTime = "< 1 hour";
        else if (volume > 1000) holdTime = "1-4 hours";
        else if (volume > 100) holdTime = "4-12 hours";
        else holdTime = "1-3 days";

        return SmartPick.builder()
            .itemId(itemId)
            .itemName(itemName)
            .smartScore(finalScore)
            .action(action)
            .confidence(confidence)
            .risk(risk)
            .reasons(reasons)
            .warnings(warnings)
            .currentPrice(currentPrice)
            .estimatedProfitLow(estimatedProfitLow)
            .estimatedProfitHigh(estimatedProfitHigh)
            .holdTime(holdTime)
            .jtiScore(jtiScore)
            .rsi(rsi)
            .regime(regime)
            .botImpact(botImpact)
            .timestamp(System.currentTimeMillis())
            .build();
    }

    
    public List<SmartPick> getTopPicks(int limit) {
        try {
            // Start with top margin items as candidates
            List<PriceAggregate> candidates = priceService.getTopByMargin(Math.min(200, limit * 4), 10);

            return candidates.parallelStream()
                .map(agg -> analyze(agg.getItemId()))
                .filter(Objects::nonNull)
                .filter(pick -> pick.getSmartScore() >= 55) // Only viable picks
                .sorted(Comparator.comparingInt(SmartPick::getSmartScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error getting top picks", e);
            return Collections.emptyList();
        }
    }

    
    public List<SmartPick> getTopSells(int limit) {
        try {
            List<PriceAggregate> candidates = priceService.getTopByMargin(200, 10);

            return candidates.parallelStream()
                .map(agg -> analyze(agg.getItemId()))
                .filter(Objects::nonNull)
                .filter(pick -> pick.getSmartScore() < 35)
                .sorted(Comparator.comparingInt(SmartPick::getSmartScore))
                .limit(limit)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error getting top sells", e);
            return Collections.emptyList();
        }
    }

    
    public MarketOverview getMarketOverview() {
        try {
            List<SmartPick> allPicks = getTopPicks(50);
            List<SmartPick> sells = getTopSells(10);

            long strongBuys = allPicks.stream().filter(p -> p.getAction() == SmartAction.STRONG_BUY).count();
            long buys = allPicks.stream().filter(p -> p.getAction() == SmartAction.BUY).count();
            long sellCount = sells.size();
            long strongSellCount = sells.stream().filter(p -> p.getAction() == SmartAction.STRONG_SELL).count();

            // Market mood
            String mood;
            double buyRatio = (double)(strongBuys + buys) / Math.max(1, allPicks.size());
            if (buyRatio > 0.6) mood = "Bullish";
            else if (buyRatio < 0.3) mood = "Bearish";
            else mood = "Neutral";

            // Bot activity summary
            String botActivity;
            double banWaveConf = botEconomy.getSupplyShockDetector().getBanWaveConfidence();
            if (banWaveConf > 50) botActivity = "Ban wave active (" + (int)banWaveConf + "% confidence)";
            else if (botEconomy.getSupplyShockDetector().detectBotReturnSignals()) botActivity = "Bots returning";
            else botActivity = "Equilibrium";

            // Market health
            int healthScore = botEconomy.getMarketHealth().getMarketHealth();

            // Alerts
            List<String> alerts = new ArrayList<>();
            if (banWaveConf > 30) alerts.add("Ban wave signals detected — bot items spiking");
            BotEconomyTracker.PipelineStage stage = botEconomy.getPipelineTracker().getPipelineStage();
            if (stage != BotEconomyTracker.PipelineStage.EQUILIBRIUM) {
                alerts.add("Bot pipeline stage: " + stage.name());
            }
            if (healthScore < 50) alerts.add("Market health low (" + healthScore + "/100) — trade with caution");

            return MarketOverview.builder()
                .totalItemsAnalyzed(allPicks.size())
                .strongBuys((int) strongBuys)
                .buys((int) buys)
                .sells((int) sellCount)
                .strongSells((int) strongSellCount)
                .marketMood(mood)
                .botActivity(botActivity)
                .marketHealthScore(healthScore)
                .topPicks(allPicks.stream().limit(10).collect(Collectors.toList()))
                .topSells(sells.stream().limit(5).collect(Collectors.toList()))
                .alerts(alerts)
                .timestamp(System.currentTimeMillis())
                .build();

        } catch (Exception e) {
            log.error("Error generating market overview", e);
            return null;
        }
    }

    // ==================== SERVER-SIDE INTELLIGENCE CONVERSION ====================
    // These methods convert server responses to Java SmartAdvisor data structures

    
    private SmartPick convertServerSmartAdvisor(ServerIntelligenceClient.SmartAdvisorResult serverResult) {
        if (serverResult == null) return null;

        try {
            SmartAction action = SmartAction.valueOf(serverResult.action);
            Confidence confidence = Confidence.valueOf(serverResult.confidence);
            RiskLevel risk = RiskLevel.valueOf(serverResult.risk);

            return SmartPick.builder()
                    .itemId(serverResult.itemId)
                    .itemName(serverResult.itemName)
                    .smartScore(serverResult.smartScore)
                    .action(action)
                    .confidence(confidence)
                    .risk(risk)
                    .reasons(serverResult.reasons)
                    .warnings(serverResult.warnings)
                    .currentPrice(serverResult.currentPrice)
                    .estimatedProfitLow(serverResult.estimatedProfitLow)
                    .estimatedProfitHigh(serverResult.estimatedProfitHigh)
                    .holdTime(serverResult.holdTime)
                    .timestamp(serverResult.currentPrice)  // Server timestamp
                    .build();
        } catch (Exception e) {
            log.warn("Failed to convert server SmartAdvisor result: {}", e.getMessage());
            return null;
        }
    }

    
    public ServerIntelligenceConfig getServerConfig() {
        return serverConfig;
    }

    
    public boolean isUsingServerIntelligence() {
        return serverConfig.isUseServerSideIntelligence();
    }

    
    public boolean isServerIntelligenceAvailable() {
        return serverConfig.isServerIntelligenceAvailable();
    }
}
