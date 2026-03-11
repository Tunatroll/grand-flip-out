package com.fliphelper.tracker;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

import java.util.*;

// Intelligence computed server-side via Railway backend
@Slf4j
public class SmartAdvisor {

    private final ServerIntelligenceConfig serverConfig;
    private final ServerIntelligenceClient serverClient;

    public SmartAdvisor(OkHttpClient okHttpClient) {
        this.serverConfig = new ServerIntelligenceConfig();
        this.serverClient =
            new ServerIntelligenceClient(serverConfig, okHttpClient);
        log.info("SmartAdvisor ready (server mode)");
    }

    public enum SmartAction {
        STRONG_BUY("Strong Buy"),
        BUY("Buy"),
        HOLD("Hold"),
        SELL("Sell"),
        STRONG_SELL("Strong Sell"),
        WAIT("Wait"),
        MARGIN_CHECK("Margin Check"),
        AVOID("Avoid");

        @Getter private final String label;
        SmartAction(String label) { this.label = label; }
    }

    public enum Confidence { HIGH, MEDIUM, LOW, UNKNOWN }
    public enum RiskLevel { VERY_LOW, LOW, MODERATE, HIGH, EXTREME }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SmartPick {
        private int itemId;
        private String itemName;
        private SmartAction action;
        private Confidence confidence;
        private RiskLevel risk;
        private int score;
        private int smartScore;
        private long estimatedProfit;
        private long estimatedProfitLow;
        private long estimatedProfitHigh;
        private String reasoning;
        private String timeframe;
        private String holdTime;
        @Builder.Default
        private List<String> signals = new ArrayList<>();
        @Builder.Default
        private List<String> reasons = new ArrayList<>();
        @Builder.Default
        private List<String> warnings = new ArrayList<>();
        private long buyPrice;
        private long sellPrice;
        private long currentPrice;
        private double marginPct;
        private int jtiScore;
        private int rsi;
        private String regime;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MarketOverview {
        @Builder.Default
        private List<SmartPick> topPicks = new ArrayList<>();
        @Builder.Default
        private List<SmartPick> topSells = new ArrayList<>();
        private String regime;
        private String summary;
        private String marketMood;
        private int marketHealthScore;
        private String botActivity;
        private int totalItemsAnalyzed;
        @Builder.Default
        private List<String> alerts = new ArrayList<>();
    }

    public MarketOverview getMarketOverview() {
        try {
            var result = serverClient.getMarketOverview();
            if (result.isPresent()) {
                return convertOverview(result.get());
            }
        } catch (Exception e) {
            log.debug("Server unavailable: {}", e.getMessage());
        }
        return MarketOverview.builder()
            .marketMood("Offline")
            .summary("Connect backend for intelligence")
            .build();
    }

    public SmartPick analyze(int itemId) {
        try {
            var result = serverClient.getSmartAdvisor(itemId);
            if (result.isPresent()) {
                return convertResult(result.get());
            }
        } catch (Exception e) {
            log.debug("Analysis unavailable for {}", itemId);
        }
        return SmartPick.builder()
            .itemId(itemId)
            .action(SmartAction.HOLD)
            .confidence(Confidence.UNKNOWN)
            .risk(RiskLevel.MODERATE)
            .reasoning("Server offline")
            .build();
    }

    private SmartPick convertResult(
            ServerIntelligenceClient.SmartAdvisorResult r) {
        return SmartPick.builder()
            .itemId(r.itemId)
            .itemName(r.itemName)
            .action(parseAction(r.action))
            .confidence(parseConfidence(r.confidence))
            .risk(parseRisk(r.risk))
            .score(r.smartScore)
            .smartScore(r.smartScore)
            .estimatedProfit(r.estimatedProfitHigh)
            .reasoning(r.reasons != null && !r.reasons.isEmpty() ? String.join("; ", r.reasons) : "")
            .buyPrice(r.currentPrice)
            .sellPrice(r.currentPrice)
            .marginPct(0)
            .jtiScore(0)
            .regime("")
            .build();
    }

    private MarketOverview convertOverview(
            ServerIntelligenceClient.MarketOverviewResult r) {
        List<SmartPick> picks = new ArrayList<>();
        if (r.topPicks != null) {
            for (var p : r.topPicks) {
                picks.add(convertResult(p));
            }
        }
        return MarketOverview.builder()
            .topPicks(picks)
            .regime(r.marketMood != null ? r.marketMood : "unknown")
            .summary(r.marketMood != null ? r.marketMood + " (" + r.totalItemsAnalyzed + " items)" : "")
            .marketMood(r.marketMood)
            .marketHealthScore(r.marketHealthScore)
            .totalItemsAnalyzed(r.totalItemsAnalyzed)
            .build();
    }

    private SmartAction parseAction(String s) {
        if (s == null) return SmartAction.HOLD;
        try { return SmartAction.valueOf(s.toUpperCase()
            .replace(" ", "_")); }
        catch (Exception e) { return SmartAction.HOLD; }
    }

    private Confidence parseConfidence(String s) {
        if (s == null) return Confidence.UNKNOWN;
        try { return Confidence.valueOf(s.toUpperCase()); }
        catch (Exception e) { return Confidence.UNKNOWN; }
    }

    private RiskLevel parseRisk(String s) {
        if (s == null) return RiskLevel.MODERATE;
        try { return RiskLevel.valueOf(s.toUpperCase()
            .replace(" ", "_")); }
        catch (Exception e) { return RiskLevel.MODERATE; }
    }
}
