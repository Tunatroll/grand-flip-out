package com.fliphelper.tracker;

import com.fliphelper.model.PriceAggregate;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


@Slf4j
public class QuickFlipAnalyzer
{
    private static final long GE_TAX_CAP = 5_000_000L;

    
    public enum QFGrade
    {
        S("Excellent — fills in <1 min, ideal margin"),
        A("Very good — fills fast, solid margin"),
        B("Good — workable quick flip"),
        C("Marginal — slow or thin margin"),
        F("Poor — not suitable for quick flipping");

        private final String description;
        QFGrade(String description) { this.description = description; }
        public String getDescription() { return description; }
    }

    
    @Data
    public static class QuickFlipResult
    {
        private int itemId;
        private String itemName;
        private int qfScore;           // 0-100 composite
        private QFGrade qfGrade;
        private String estimatedFillTime; // e.g. "< 1 min", "5-20 min"
        private long netProfitPerFlip; // after GE tax
        private double marginPercent;
        private long volumePerHour;
        private long buyPrice;
        private long sellPrice;
        private List<String> reasons;
        private List<String> warnings;
    }

    
    public QuickFlipResult analyze(PriceAggregate agg)
    {
        if (agg == null) return null;

        long now = Instant.now().getEpochSecond();
        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        double score = 0.0;

        long buyPrice = agg.getBestLowPrice();
        long sellPrice = agg.getBestHighPrice();

        if (buyPrice <= 0 || sellPrice <= 0)
        {
            QuickFlipResult result = new QuickFlipResult();
            result.setItemId(agg.getItemId());
            result.setItemName(agg.getItemName());
            result.setQfScore(0);
            result.setQfGrade(QFGrade.F);
            result.setEstimatedFillTime("unknown");
            result.setReasons(reasons);
            result.setWarnings(List.of("Missing price data"));
            return result;
        }

        long geTax = Math.min((long)(sellPrice * 0.02), GE_TAX_CAP);
        long netProfit = sellPrice - buyPrice - geTax;
        double marginPct = buyPrice > 0 ? ((double) netProfit / buyPrice) * 100.0 : 0;

        long volume1h = agg.getTotalVolume1h();

        // === COMPONENT 1: Volume (35% weight) ===
        double volScore;
        if (volume1h >= 50000) { volScore = 100; reasons.add("Ultra-high volume (" + volume1h + "/hr) — fills instantly"); }
        else if (volume1h >= 10000) { volScore = 90; reasons.add("Very high volume (" + volume1h + "/hr)"); }
        else if (volume1h >= 5000) { volScore = 80; reasons.add("High volume (" + volume1h + "/hr)"); }
        else if (volume1h >= 1000) { volScore = 60; reasons.add("Moderate volume (" + volume1h + "/hr)"); }
        else if (volume1h >= 300) { volScore = 35; warnings.add("Low volume (" + volume1h + "/hr) — slow fills"); }
        else if (volume1h > 0) { volScore = 15; warnings.add("Very low volume — not ideal for quick flipping"); }
        else { volScore = 0; warnings.add("No volume data"); }
        score += volScore * 0.35;

        // === COMPONENT 2: Price Freshness (25% weight) ===
        double freshScore = 0;
        long highTime = agg.getLatestHighTime();
        long lowTime = agg.getLatestLowTime();
        if (highTime > 0 && lowTime > 0)
        {
            long worstAge = Math.max(now - highTime, now - lowTime);
            if (worstAge < 30) { freshScore = 100; reasons.add("Price data < 30s old — very fresh"); }
            else if (worstAge < 60) { freshScore = 90; reasons.add("Price data < 1 min old"); }
            else if (worstAge < 120) { freshScore = 75; reasons.add("Price data < 2 min old"); }
            else if (worstAge < 300) { freshScore = 55; }
            else if (worstAge < 600) { freshScore = 35; warnings.add("Price data 5-10 min old — getting stale"); }
            else if (worstAge < 1800) { freshScore = 15; warnings.add("Stale price data — risky for quick flipping"); }
            else { freshScore = 0; warnings.add("Very stale data — do NOT quick flip"); }
        }
        else
        {
            freshScore = 0;
            warnings.add("Missing price timestamps");
        }
        score += freshScore * 0.25;

        // === COMPONENT 3: Margin Sweet Spot (25% weight) ===
        double marginScore;
        if (marginPct >= 3.0 && marginPct <= 8.0) { marginScore = 100; reasons.add(String.format("Sweet-spot margin %.1f%% (3-8%% ideal)", marginPct)); }
        else if (marginPct > 8.0 && marginPct <= 12.0) { marginScore = 80; reasons.add(String.format("Good margin %.1f%%", marginPct)); }
        else if (marginPct >= 2.0 && marginPct < 3.0) { marginScore = 75; }
        else if (marginPct >= 1.5 && marginPct < 2.0) { marginScore = 45; warnings.add(String.format("Tight margin %.1f%% — risky after tax", marginPct)); }
        else if (marginPct > 12.0 && marginPct <= 20.0) { marginScore = 55; warnings.add(String.format("Wide margin %.1f%% — may be slow to fill", marginPct)); }
        else if (marginPct > 20.0) { marginScore = 25; warnings.add(String.format("Very wide margin %.1f%% — order will sit", marginPct)); }
        else if (marginPct > 0) { marginScore = 20; warnings.add("Margin too thin"); }
        else { marginScore = 0; warnings.add("No margin — not profitable"); }
        score += marginScore * 0.25;

        // === COMPONENT 4: Capital Efficiency (15% weight) ===
        long avgPrice = (buyPrice + sellPrice) / 2;
        double capScore;
        if (avgPrice >= 10000 && avgPrice <= 500000) { capScore = 100; }
        else if (avgPrice > 500000 && avgPrice <= 2000000) { capScore = 75; warnings.add("High-value item — needs more capital"); }
        else if (avgPrice >= 1000 && avgPrice < 10000) { capScore = 60; }
        else if (avgPrice > 2000000) { capScore = 40; warnings.add("Very expensive — capital intensive"); }
        else { capScore = 20; }
        score += capScore * 0.15;

        // Determine grade
        int finalScore = (int) Math.max(0, Math.min(100, score));
        QFGrade grade;
        if (finalScore >= 80) grade = QFGrade.S;
        else if (finalScore >= 65) grade = QFGrade.A;
        else if (finalScore >= 50) grade = QFGrade.B;
        else if (finalScore >= 35) grade = QFGrade.C;
        else grade = QFGrade.F;

        // Fill time estimate
        String fillTime;
        if (volume1h >= 20000) fillTime = "< 1 min";
        else if (volume1h >= 5000) fillTime = "1-5 min";
        else if (volume1h >= 1000) fillTime = "5-20 min";
        else if (volume1h >= 200) fillTime = "20-60 min";
        else fillTime = "1+ hour";

        QuickFlipResult result = new QuickFlipResult();
        result.setItemId(agg.getItemId());
        result.setItemName(agg.getItemName() != null ? agg.getItemName() : "Unknown");
        result.setQfScore(finalScore);
        result.setQfGrade(grade);
        result.setEstimatedFillTime(fillTime);
        result.setNetProfitPerFlip(netProfit);
        result.setMarginPercent(marginPct);
        result.setVolumePerHour(volume1h);
        result.setBuyPrice(buyPrice);
        result.setSellPrice(sellPrice);
        result.setReasons(reasons);
        result.setWarnings(warnings);
        return result;
    }
}
