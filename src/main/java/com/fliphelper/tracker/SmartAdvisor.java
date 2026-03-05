package com.fliphelper.tracker;

import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SmartAdvisor — Unified Intelligence Engine for Grand Flip Out
     *
     * Combines signals from all analysis engines into a single, actionable recommendation
     * per item. This is the "brain" that turns raw data into smart trading advice.
     *
     * INFORMATION ONLY — does not automate any trades.
     */
@Slf4j
    public class SmartAdvisor {

    // How old price data can be before we penalise confidence (seconds)
    private static final int PRICE_STALENESS_WARN_SECS  = 300;  // 5 min
    private static final int PRICE_STALENESS_AVOID_SECS = 900;  // 15 min

    // GE buy-limit window in seconds (4 hours)
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
                STRONG_BUY("STRONG BUY",   "Excellent opportunity — multiple signals align"),
                BUY       ("BUY",          "Good opportunity — favorable conditions"),
                HOLD      ("HOLD",         "Wait — no clear edge right now"),
                SELL      ("SELL",         "Consider selling — conditions weakening"),
                STRONG_SELL("STRONG SELL", "Exit position — strong negative signals"),
                AVOID     ("AVOID",        "Stay away — high risk or poor data");

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
                        private int smartScore;           // 0-100 composite
                private SmartAction action;
                        private Confidence confidence;
                        private RiskLevel risk;
                        private List<String> reasons;     // Human-readable explanations
                private List<String> warnings;    // Red flags
                private long currentPrice;
                        private long estimatedProfitLow;  // Conservative estimate (after tax)
                private long estimatedProfitHigh; // Optimistic estimate (after tax)
                private String holdTime;          // e.g. "2–4 hours", "1–3 days"
                private int jtiScore;
                        private int rsi;
                        private String regime;
                        private String botImpact;
                        /** True if the player's GE limit for this item may still be on cooldown. */
                private boolean geLimitWarning;
                        /** Age of the price data in seconds at analysis time. */
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
                        private String marketMood;    // "Bullish", "Bearish", "Neutral", "Volatile"
                private String botActivity;   // "Ban wave active", "Bots returning", "Equilibrium"
                private int marketHealthScore;
                        private List<SmartPick> topPicks;
                        private List<SmartPick> topSells;
                        private List<String> alerts;
                        private long timestamp;
            }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Get a SmartPick analysis for a single item.
             * Returns null if no price data is available.
             */
    public SmartPick analyze(int itemId) {
                PriceAggregate agg = priceService.getPriceAggregate(itemId);
                if (agg == null) return null;

                List<String> reasons  = new ArrayList<>();
                List<String> warnings = new ArrayList<>();
                double score = 50.0; // Start neutral

                long currentPrice = agg.getCurrentPrice();
                String itemName   = agg.getItemName();

                // ── 0. Price staleness check ──────────────────────────────────────────
                long priceAgeSecs = computePriceAgeSecs(agg);
                if (priceAgeSecs >= PRICE_STALENESS_AVOID_SECS) {
                                warnings.add("Price data is " + (priceAgeSecs / 60) + " min old — may be stale");
                                score -= 15;
                } else if (priceAgeSecs >= PRICE_STALENESS_WARN_SECS) {
                                warnings.add("Price data is " + (priceAgeSecs / 60) + " min old — refresh recommended");
                                score -= 5;
                }

                // ── 1. JTI Score (weight: 25%) ────────────────────────────────────────
                int jtiScore = 0;
                try {
                                JagexTradeIndex.JTIResult jtiResult = jti.compute(agg);
                                if (jtiResult != null) {
                                                    jtiScore = (int) jtiResult.getJtiScore();
                                                    double jtiComponent = (jtiScore / 100.0) * 25;
                                                    score += jtiComponent - 12.5; // Centre around 0
                                    if (jtiScore >= 80)
                                                            reasons.add("Excellent JTI score (" + jtiScore + "/100) — strong flippability");
                                                    else if (jtiScore >= 60)
                                                                            reasons.add("Good JTI score (" + jtiScore + "/100)");
                                                    else if (jtiScore < 30)
                                                                            warnings.add("Low JTI score (" + jtiScore + "/100) — poor flipping conditions");
                                }
                } catch (Exception e) {
                                log.debug("JTI calculation failed for {}: {}", itemId, e.getMessage());
                }

                // ── 2. Technical Analysis (weight: 20%) ───────────────────────────────
                int rsi      = 50;
                String regime = "UNKNOWN";
                try {
                                MarketIntelligenceEngine.MarketReport report = marketIntelligence.getFullAnalysis(itemId);
                                if (report != null) {
                                                    rsi    = report.getRsi();
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
                                                            warnings.add("RSI overbought (" + rsi + ") — price may be peaking");
                                    } else if (rsi > 65) {
                                                            score -= 4;
                                                            warnings.add("RSI getting high (" + rsi + ") — watch for reversal");
                                    }

                                    // Momentum (EMA crossover)
                                    if (report.getMomentumSignal() != null) {
                                                            if (report.getMomentumSignal().getSignal() == MarketIntelligenceEngine.Signal.BUY) {
                                                                                        score += 5;
                                                                                        reasons.add("Positive momentum detected (EMA crossover)");
                                                            } else if (report.getMomentumSignal().getSignal() == MarketIntelligenceEngine.Signal.SELL) {
                                                                                        score -= 5;
                                                                                        warnings.add("Negative momentum (bearish EMA crossover)");
                                                            }
                                    }

                                    // Mean reversion
                                    if (report.getMeanReversionSignal() != null && report.getMeanReversionSignal().isDetected()) {
                                                            double z = report.getMeanReversionSignal().getZScore();
                                                            if (z < -2) {
                                                                                        score += 8;
                                                                                        reasons.add("Price 2+ std-devs below mean — mean reversion opportunity");
                                                            } else if (z > 2) {
                                                                                        score -= 8;
                                                                                        warnings.add("Price 2+ std-devs above mean — likely to pull back");
                                                            }
                                    }

                                    // Market regime
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

                                    // Item sink pressure
                                    if (report.isSinkTarget() && report.getSinkPressure() > 5) {
                                                            score += 4;
                                                            reasons.add("Item sink active — long-term price support");
                                    }
                                }
                } catch (Exception e) {
                                log.debug("Market intelligence failed for {}: {}", itemId, e.getMessage());
                }

                // ── 3. Bot Economy (weight: 15%) ──────────────────────────────────────
                String botImpact = "None";
                try {
                                if (botEconomy.isBotAffectedItem(itemId)) {
                                                    BotEconomyTracker.BotItemProfile profile = botEconomy.getBotItemProfile(itemId);
                                                    botImpact = profile.getPhase().name()
                                                                                + " (" + (int) profile.getTypicalBotSupplyPercent() + "% bot supply)";
                                                    BotEconomyTracker.SupplyShockDetector shock = botEconomy.getSupplyShockDetector();
                                                    double banWaveConf = shock.getBanWaveConfidence();
                                                    if (banWaveConf > 60) {
                                                                            score += 12;
                                                                            reasons.add("BAN WAVE DETECTED (" + (int) banWaveConf + "% confidence) — bot items spiking!");
                                                    } else if (banWaveConf > 30) {
                                                                            score += 6;
                                                                            reasons.add("Possible ban wave (" + (int) banWaveConf + "% confidence) — monitor closely");
                                                    }
                                                    if (shock.getSupplyShockItems().contains(itemId)) {
                                                                            score += 5;
                                                                            reasons.add("Item in active supply shock — price elevated");
                                                    }
                                                    if (shock.getSupplyFloodItems().contains(itemId)) {
                                                                            score -= 5;
                                                                            warnings.add("Bot supply flooding detected — price declining");
                                                    }
                                                    BotEconomyTracker.PipelineStage stage = botEconomy.getPipelineTracker().getPipelineStage();
                                                    if (stage == BotEconomyTracker.PipelineStage.BOTS_BANNED
                                                                                && profile.getTypicalBotSupplyPercent() > 40) {
                                                                            score += 8;
                                                                            reasons.add("Bot pipeline: BANNED — " + profile.getItemName() + " supply constrained");
                                                    }
                                }
                } catch (Exception e) {
                                log.debug("Bot economy analysis failed for {}: {}", itemId, e.getMessage());
                }

                // ── 4. Dump Detection (weight: 15%) ───────────────────────────────────
                try {
                                List<DumpDetector.PriceAlert> alerts = dumpDetector.detectAnomalies();
                                for (DumpDetector.PriceAlert alert : alerts) {
                                                    if (alert.getItemId() == itemId) {
                                                                            if (alert.getType() == DumpDetector.AlertType.DUMP) {
                                                                                                        score += 10;
                                                                                                        reasons.add("DUMP DETECTED: " + String.format("%.1f%%", alert.getLowDeviation())
                                                                                                                                                    + " drop — potential buy-the-dip");
                                                                                } else if (alert.getType() == DumpDetector.AlertType.PUMP) {
                                                                                                        score -= 5;
                                                                                                        warnings.add("PUMP DETECTED: " + String.format("%.1f%%", alert.getHighDeviation())
                                                                                                                                                     + " spike — late entry risky");
                                                                                } else if (alert.getType() == DumpDetector.AlertType.VOLUME_SPIKE) {
                                                                                                        reasons.add("Volume spike detected — increased trading activity");
                                                                                }
                                                    }
                                }
                } catch (Exception e) {
                                log.debug("Dump detection failed for {}: {}", itemId, e.getMessage());
                }

                // ── 5. Margin Quality (weight: 15%) ───────────────────────────────────
                long   margin    = agg.getConsensusMargin();
                double marginPct = agg.getConsensusMarginPercent();
                long   volume    = agg.getTotalVolume1h();

                // After-tax margin (2% tax on sell price, capped at 5M per item)
                long taxPerItem      = Math.min((long) (agg.getBestHighPrice() * 0.02), 5_000_000L);
                long afterTaxMargin  = margin - taxPerItem;

                if (afterTaxMargin <= 0) {
                                score -= 10;
                                warnings.add("Margin is negative after 2% GE tax — not profitable");
                } else if (marginPct >= 3.0 && marginPct <= 15.0) {
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

                // ── 6. Data Quality (weight: 10%) ─────────────────────────────────────
                try {
                                DataQualityAnalyzer.DataQualityReport quality = dataQuality.getDataQuality(itemId);
                                if (quality != null) {
                                                    if (quality.getOverallScore() < 40) {
                                                                            score -= 10;
                                                                            warnings.add("LOW DATA QUALITY (" + quality.getOverallScore() + "/100) — unreliable prices");
                                                    }
                                                    if (quality.getManipulationRisk() != null
                                                                                && quality.getManipulationRisk().getManipulationRisk()
                                                                                   == DataQualityAnalyzer.ManipulationRisk.HIGH) {
                                                                            score -= 12;
                                                                            warnings.add("HIGH MANIPULATION RISK — possible price fixing detected");
                                                    }
                                }
                } catch (Exception e) {
                                log.debug("Data quality analysis failed for {}: {}", itemId, e.getMessage());
                }

                // ── 7. GE Limit cooldown awareness ────────────────────────────────────
                boolean geLimitWarning = false;
                int buyLimit = agg.getBuyLimit();
                try {
                                if (buyLimit > 0) {
                                                    long lastBuyTimestamp = historyCollector.getLastBuyTimestamp(itemId);
                                                    if (lastBuyTimestamp > 0) {
                                                                            long secsSinceLastBuy = Instant.now().getEpochSecond() - lastBuyTimestamp;
                                                                            long secsRemaining    = GE_LIMIT_WINDOW_SECS - secsSinceLastBuy;
                                                                            if (secsRemaining > 0) {
                                                                                                        long minsRemaining = secsRemaining / 60;
                                                                                                        geLimitWarning = true;
                                                                                                        warnings.add("GE limit may still be active — resets in ~" + minsRemaining + " min");
                                                                                                        score -= 5; // Reduce attractiveness if you can't buy the full limit
                                                                            }
                                                    }
                                }
                } catch (Exception e) {
                                log.debug("GE limit check failed for {}: {}", itemId, e.getMessage());
                }

                // ── Clamp score and determine action ──────────────────────────────────
                int finalScore = (int) Math.max(0, Math.min(100, score));

                SmartAction action;
                if (finalScore >= 80)      action = SmartAction.STRONG_BUY;
                else if (finalScore >= 65) action = SmartAction.BUY;
                else if (finalScore >= 45) action = SmartAction.HOLD;
                else if (finalScore >= 30) action = SmartAction.SELL;
                else if (finalScore >= 15) action = SmartAction.STRONG_SELL;
                else                       action = SmartAction.AVOID;

                // Downgrade STRONG_BUY / BUY if critical warnings are present
                boolean hasCriticalWarning = warnings.stream().anyMatch(w ->
                                                                                        w.contains("MANIPULATION") || w.contains("DEAD")
                                                                                        || w.contains("LOW DATA") || w.contains("negative after 2%"));
                if (hasCriticalWarning && (action == SmartAction.STRONG_BUY || action == SmartAction.BUY)) {
                                action = SmartAction.HOLD;
                                warnings.add("Downgraded to HOLD due to critical risk flags");
                }

                // ── Confidence ────────────────────────────────────────────────────────
                int dataPoints = historyCollector.getDataPointCount(itemId);
                Confidence confidence;
                if (dataPoints >= 50 && warnings.size() <= 1 && reasons.size() >= 3
                                    && priceAgeSecs < PRICE_STALENESS_WARN_SECS) {
                                confidence = Confidence.HIGH;
                } else if (dataPoints >= 14 && warnings.size() <= 2
                                           && priceAgeSecs < PRICE_STALENESS_AVOID_SECS) {
                                confidence = Confidence.MEDIUM;
                } else {
                                confidence = Confidence.LOW;
                }

                // ── Risk ──────────────────────────────────────────────────────────────
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

                // ── Profit estimates (after 2% GE tax) ────────────────────────────────
                long estimatedProfitLow  = buyLimit > 0 ? (long) (afterTaxMargin * buyLimit * 0.5)  : afterTaxMargin * 10;
                long estimatedProfitHigh = buyLimit > 0 ? (long) (afterTaxMargin * buyLimit * 0.9)  : afterTaxMargin * 50;
                estimatedProfitLow  = Math.max(0, estimatedProfitLow);
                estimatedProfitHigh = Math.max(0, estimatedProfitHigh);

                // ── Hold time estimate ────────────────────────────────────────────────
                String holdTime;
                if      (volume > 10000) holdTime = "< 1 hour";
                else if (volume > 1000)  holdTime = "1–4 hours";
                else if (volume > 100)   holdTime = "4–12 hours";
                else                     holdTime = "1–3 days";

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
                                    .geLimitWarning(geLimitWarning)
                                    .priceAgeSecs(priceAgeSecs)
                                    .timestamp(System.currentTimeMillis())
                                    .build();
    }

    /**
     * Get top SmartPicks — the best items to trade right now.
             */
    public List<SmartPick> getTopPicks(int limit) {
                try {
                                List<PriceAggregate> candidates = priceService.getTopByMargin(Math.min(200, limit * 4), 10);
                                return candidates.parallelStream()
                                                        .map(agg -> analyze(agg.getItemId()))
                                                        .filter(Objects::nonNull)
                                                        .filter(pick -> pick.getSmartScore() >= 55)
                                                        .sorted(Comparator.comparingInt(SmartPick::getSmartScore).reversed())
                                                        .limit(limit)
                                                        .collect(Collectors.toList());
                } catch (Exception e) {
                                log.error("Error getting top picks", e);
                                return Collections.emptyList();
                }
    }

    /**
     * Get items to sell/avoid right now.
             */
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

    /**
     * Get a full market overview combining all intelligence.
             */
    public MarketOverview getMarketOverview() {
                try {
                                List<SmartPick> allPicks = getTopPicks(50);
                                List<SmartPick> sells   = getTopSells(10);

                    long strongBuys    = allPicks.stream().filter(p -> p.getAction() == SmartAction.STRONG_BUY).count();
                                long buys          = allPicks.stream().filter(p -> p.getAction() == SmartAction.BUY).count();
                                long sellCount     = sells.size();
                                long strongSellCnt = sells.stream().filter(p -> p.getAction() == SmartAction.STRONG_SELL).count();

                    // Market mood
                    double buyRatio = (double) (strongBuys + buys) / Math.max(1, allPicks.size());
                                String mood;
                                if      (buyRatio > 0.6) mood = "Bullish";
                                else if (buyRatio < 0.3) mood = "Bearish";
                                else                     mood = "Neutral";

                    // Bot activity summary
                    double banWaveConf = botEconomy.getSupplyShockDetector().getBanWaveConfidence();
                                String botActivity;
                                if (banWaveConf > 50)
                                                    botActivity = "Ban wave active (" + (int) banWaveConf + "% confidence)";
                                else if (botEconomy.getSupplyShockDetector().detectBotReturnSignals())
                                                    botActivity = "Bots returning";
                                else
                                                    botActivity = "Equilibrium";

                    int healthScore = botEconomy.getMarketHealth().getMarketHealth();

                    // Alerts
                    List<String> alerts = new ArrayList<>();
                                if (banWaveConf > 30)
                                                    alerts.add("Ban wave signals detected — bot items spiking");
                                BotEconomyTracker.PipelineStage stage = botEconomy.getPipelineTracker().getPipelineStage();
                                if (stage != BotEconomyTracker.PipelineStage.EQUILIBRIUM)
                                                    alerts.add("Bot pipeline stage: " + stage.name());
                                if (healthScore < 50)
                                                    alerts.add("Market health low (" + healthScore + "/100) — trade with caution");

                    return MarketOverview.builder()
                                            .totalItemsAnalyzed(allPicks.size())
                                            .strongBuys((int) strongBuys)
                                            .buys((int) buys)
                                            .sells((int) sellCount)
                                            .strongSells((int) strongSellCnt)
                                            .marketMood(mood)
                                            .botActivity(botActivity)
                                            .marketHealthScore(healthScore)
                                            .topPicks(allPicks.stream().limit(10).collect(Collectors.toList()))
                                            .topSells(sells.stream().limit(5).collect(Collectors.toList()))
                                            .alerts(alerts)
                                            .timestamp(System.currentTimeMillis())
                                            .build();
                } catch (Exception e) {
                                log.error("Error generating mar  */
                                              private long computePriceAgeSecs(PriceAggregate agg) {
                                            try {
                                          ket overview", e);
                                return null;
                }
    }      long lastUpdated = agg.get

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ───────────────────────────────────────────────────────LastUpdatedEpochSecs();──────────────────

    /**
                                if (lastUpdated <= 0) return 0;
                    return Math.max(0, Instant.now().getEpochSecond() - lastUpdated);
    } catch (Exception e) {
                
     * Compute how old the price data in an aggregate is, in seconds.return 0;
}
}
}
             * Falls back to 0 if the aggregate carries no timestamp.
       
