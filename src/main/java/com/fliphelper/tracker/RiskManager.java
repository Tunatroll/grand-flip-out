package com.fliphelper.tracker;

import com.fliphelper.model.FlipItem;
import com.fliphelper.model.PriceAggregate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class RiskManager {

    /**
     * Calculate recommended position size based on risk parameters
     */
    public long calculatePositionSize(long totalCapital, long itemPrice, int geLimit, double maxCapitalPercent) {
        if (itemPrice <= 0 || totalCapital <= 0) {
            return 0;
        }

        // Calculate based on capital percentage
        long capitalPerItem = (long) (totalCapital * maxCapitalPercent);
        long quantityByCapital = capitalPerItem / itemPrice;

        // Limit by GE slot limit
        long recommendedSize = Math.min(quantityByCapital, geLimit);

        log.debug("Calculated position size: {} (capital: {}, item price: {}, limit: {})",
                recommendedSize, capitalPerItem, itemPrice, geLimit);

        return recommendedSize;
    }

    /**
     * Get diversification score (0-100) based on how spread out capital is
     * Higher score = better diversification
     */
    public int getDiversificationScore(Map<Integer, FlipItem> activeFlips) {
        if (activeFlips == null || activeFlips.isEmpty()) {
            return 100; // No capital deployed = no concentration risk
        }

        long totalCapital = activeFlips.values().stream()
                .mapToLong(flip -> flip.getBuyPrice() * flip.getQuantity())
                .sum();

        if (totalCapital <= 0) {
            return 100;
        }

        // Calculate Herfindahl-Hirschman Index for concentration
        double hhi = 0.0;
        for (FlipItem flip : activeFlips.values()) {
            long itemCapital = flip.getBuyPrice() * flip.getQuantity();
            double percentage = (double) itemCapital / totalCapital;
            hhi += percentage * percentage;
        }

        // Convert HHI to score (0-100)
        // HHI ranges from 1/n (perfect diversification) to 1.0 (complete concentration)
        double minHhi = 1.0 / Math.max(activeFlips.size(), 1);
        double denominator = 1.0 - minHhi;
        double diversificationRatio = denominator > 0.001 ? (1.0 - hhi) / denominator : 0.0;
        int score = (int) Math.max(0, Math.min(100, diversificationRatio * 100));

        log.debug("Diversification score: {} for {} active flips (HHI: {}, ratio: {})",
                score, activeFlips.size(), hhi, diversificationRatio);

        return score;
    }

    /**
     * Check if a position should be stopped out based on loss threshold
     */
    public boolean shouldStopLoss(int itemId, long currentPrice, long buyPrice, double maxLossPercent) {
        if (buyPrice <= 0 || currentPrice < 0) {
            return false;
        }

        double lossPercent = ((double) (buyPrice - currentPrice) / buyPrice) * 100;
        boolean shouldStop = lossPercent >= maxLossPercent;

        if (shouldStop) {
            log.warn("Stop loss triggered for item {}: current={}, buy={}, loss={}%",
                    itemId, currentPrice, buyPrice, String.format("%.2f", lossPercent));
        }

        return shouldStop;
    }

    /**
     * Get maximum recommended capital per slot
     */
    public long getMaxCapitalPerItem(long totalCapital, int totalSlots) {
        if (totalSlots <= 0) {
            return 0;
        }
        long maxPerSlot = totalCapital / totalSlots;
        log.debug("Max capital per slot: {} (total: {}, slots: {})", maxPerSlot, totalCapital, totalSlots);
        return maxPerSlot;
    }

    /**
     * Perform comprehensive risk assessment on an item
     */
    public RiskAssessment getRiskAssessment(PriceAggregate agg) {
        if (agg == null) {
            return RiskAssessment.builder()
                    .level(RiskLevel.EXTREME)
                    .reasons(Collections.singletonList("No price data available"))
                    .build();
        }

        RiskAssessment.RiskAssessmentBuilder builder = RiskAssessment.builder();
        List<String> reasons = new ArrayList<>();
        RiskLevel level = RiskLevel.LOW;

        // Check spread
        long spread = agg.getHighPrice() - agg.getLowPrice();
        long avgPrice = (agg.getHighPrice() + agg.getLowPrice()) / 2;
        if (avgPrice > 0) {
            double spreadPercent = (double) spread / avgPrice * 100;
            if (spreadPercent > 10) {
                reasons.add(String.format("High spread: %.1f%%", spreadPercent));
                level = RiskLevel.MEDIUM;
            }
            if (spreadPercent > 20) {
                reasons.add(String.format("Very high spread: %.1f%%", spreadPercent));
                level = RiskLevel.HIGH;
            }
        }

        // Check volume
        if (agg.getVolume() < 100) {
            reasons.add("Low volume: < 100 units/day");
            level = RiskLevel.HIGH;
        } else if (agg.getVolume() < 500) {
            reasons.add("Medium volume: < 500 units/day");
            if (level == RiskLevel.LOW) {
                level = RiskLevel.MEDIUM;
            }
        }

        // Check bid-ask spread (called "volatility" but actually measures spread, not standard deviation)
        // This is the percentage difference between buy and sell prices on the current snapshot
        long priceRange = agg.getHighPrice() - agg.getLowPrice();
        if (avgPrice > 0) {
            double bidAskSpread = (double) priceRange / avgPrice * 100;
            if (bidAskSpread > 15) {
                reasons.add(String.format("High bid-ask spread: %.1f%%", bidAskSpread));
                level = RiskLevel.MEDIUM;
            }
            if (bidAskSpread > 30) {
                reasons.add(String.format("Extreme bid-ask spread: %.1f%%", bidAskSpread));
                level = RiskLevel.HIGH;
            }
        }

        // Check margin
        if (agg.getBuyPrice() > 0 && agg.getSellPrice() > 0) {
            long margin = agg.getSellPrice() - agg.getBuyPrice();
            double marginPercent = (double) margin / agg.getBuyPrice() * 100;
            if (marginPercent < 1) {
                reasons.add(String.format("Very tight margin: %.2f%%", marginPercent));
                level = RiskLevel.HIGH;
            } else if (marginPercent < 2) {
                reasons.add(String.format("Tight margin: %.2f%%", marginPercent));
                if (level == RiskLevel.LOW) {
                    level = RiskLevel.MEDIUM;
                }
            }
        }

        if (reasons.isEmpty()) {
            reasons.add("Safe price action and volume");
        }

        // Calculate suggested position sizing
        long suggestedMaxQty = calculatePositionSizeFromVolume(agg.getVolume());
        double suggestedStopLoss = calculateSuggestedStopLoss(spread, avgPrice);

        return RiskAssessment.builder()
                .level(level)
                .reasons(reasons)
                .suggestedMaxQuantity(suggestedMaxQty)
                .suggestedStopLoss(suggestedStopLoss)
                .spreadPercent(avgPrice > 0 ? (double) spread / avgPrice * 100 : 0)
                .volumePerDay(agg.getVolume())
                .build();
    }

    /**
     * Calculate position size based on daily volume
     */
    private long calculatePositionSizeFromVolume(long dailyVolume) {
        // Recommend at most 10% of daily volume to ensure quick exits
        return Math.max(1, dailyVolume / 10);
    }

    /**
     * Calculate suggested stop loss percentage
     */
    private double calculateSuggestedStopLoss(long spread, long avgPrice) {
        if (avgPrice <= 0) {
            return 5.0; // Default 5% stop loss
        }
        double spreadPercent = (double) spread / avgPrice * 100;
        // Stop loss should be higher than the spread to avoid whipsaws
        return Math.max(5.0, spreadPercent * 1.5);
    }

    /**
     * Risk level enumeration with colors and descriptions
     */
    public enum RiskLevel {
        LOW("Low Risk", "#00AA00", "Safe to trade with standard position sizing"),
        MEDIUM("Medium Risk", "#FFAA00", "Moderate risk - reduce position size by 25%"),
        HIGH("High Risk", "#FF5500", "High risk - reduce position size by 50% or skip"),
        EXTREME("Extreme Risk", "#FF0000", "Extreme risk - do not trade");

        private final String displayName;
        private final String color;
        private final String description;

        RiskLevel(String displayName, String color, String description) {
            this.displayName = displayName;
            this.color = color;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColor() {
            return color;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Risk assessment result
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class RiskAssessment {
        private RiskLevel level;
        private List<String> reasons;
        private long suggestedMaxQuantity;
        private double suggestedStopLoss;
        private double spreadPercent;
        private long volumePerDay;

        public RiskAssessment() {
            this.level = RiskLevel.MEDIUM;
            this.reasons = new ArrayList<>();
            this.suggestedStopLoss = 5.0;
        }

        public String getSummary() {
            return String.format("%s - %s", level.getDisplayName(), String.join("; ", reasons));
        }

        public String getRecommendedAction() {
            return level.getDescription();
        }

        public double getPositionSizeMultiplier() {
            switch (level) {
                case LOW: return 1.0;
                case MEDIUM: return 0.75;
                case HIGH: return 0.5;
                case EXTREME: return 0.0;
                default: return 0.5;
            }
        }
    }
}
