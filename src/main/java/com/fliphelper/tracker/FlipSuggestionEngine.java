package com.fliphelper.tracker;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzes price data and suggests the best items to flip based on
 * margin, volume, buy limits, and configurable criteria.
 */
@Slf4j
public class FlipSuggestionEngine
{
    private final PriceService priceService;
    private final GrandFlipOutConfig config;
    private final QuickFlipAnalyzer quickFlipAnalyzer;

    public FlipSuggestionEngine(PriceService priceService, GrandFlipOutConfig config, QuickFlipAnalyzer quickFlipAnalyzer)
    {
        this.priceService = priceService;
        this.config = config;
        this.quickFlipAnalyzer = quickFlipAnalyzer;
    }

    /**
     * Generate flip suggestions based on current config and market data.
     * Uses a bounded priority queue for efficient top-N selection.
     */
    public List<FlipSuggestion> generateSuggestions()
    {
        long startTime = System.currentTimeMillis();
        int maxSuggestions = config.maxSuggestions();

        // Use min-heap (priority queue with reversed comparator) to maintain top-N efficiently
        PriorityQueue<FlipSuggestion> topSuggestions = new PriorityQueue<>(
            maxSuggestions + 1,
            getSortComparator().reversed()
        );

        for (PriceAggregate agg : priceService.getAggregatedPrices().values())
        {
            if (agg == null)
            {
                continue;
            }

            long margin = agg.getConsensusMargin();
            long volume = agg.getTotalVolume1h();
            int limit = agg.getBuyLimit();

            // Apply filters
            if (margin < config.minSuggestionMargin())
            {
                continue;
            }
            if (volume < config.minSuggestionVolume())
            {
                continue;
            }
            if (limit <= 0)
            {
                continue;
            }

            long buyPrice = agg.getBestLowPrice();
            long sellPrice = agg.getBestHighPrice();
            if (buyPrice <= 0 || sellPrice <= 0)
            {
                continue;
            }

            // Calculate tax
            long taxPerItem = Math.min((long) (sellPrice * 0.02), 5_000_000L);
            long netMargin = margin - taxPerItem;
            if (netMargin <= 0)
            {
                continue;
            }

            long profitPerLimit = netMargin * limit;
            double marginPercent = agg.getConsensusMarginPercent();
            double roi = (double) netMargin / buyPrice * 100.0;
            long capitalRequired = buyPrice * limit;

            // Full scoring available via server API — see /api/suggestions
            // Using simple fallback: sort by profitPerLimit (no complex weighting)
            double compositeScore = profitPerLimit; // Simple fallback for local sorting

            // Analyze for quick flip suitability
            QuickFlipAnalyzer.QuickFlipResult qfResult = quickFlipAnalyzer.analyze(agg);

            FlipSuggestion suggestion = new FlipSuggestion();
            suggestion.setItemId(agg.getItemId());
            suggestion.setItemName(agg.getItemName() != null ? agg.getItemName() : "Unknown");
            suggestion.setBuyPrice(buyPrice);
            suggestion.setSellPrice(sellPrice);
            suggestion.setMargin(netMargin);
            suggestion.setMarginPercent(marginPercent);
            suggestion.setTaxPerItem(taxPerItem);
            suggestion.setVolume1h(volume);
            suggestion.setBuyLimit(limit);
            suggestion.setProfitPerLimit(profitPerLimit);
            suggestion.setRoi(roi);
            suggestion.setCapitalRequired(capitalRequired);
            suggestion.setCompositeScore(compositeScore);
            suggestion.setAlchProfitable(agg.isAlchProfitable());

            // Set quick flip data
            if (qfResult != null)
            {
                suggestion.setQfScore(qfResult.getQfScore());
                suggestion.setQfGrade(qfResult.getQfGrade().name());
                suggestion.setEstimatedFillTime(qfResult.getEstimatedFillTime());
            }
            else
            {
                suggestion.setQfScore(0);
                suggestion.setQfGrade("F");
                suggestion.setEstimatedFillTime("unknown");
            }

            // Add to priority queue, evicting worst if we exceed capacity
            topSuggestions.offer(suggestion);
            if (topSuggestions.size() > maxSuggestions)
            {
                topSuggestions.poll();
            }
        }

        // Convert priority queue to sorted list (in reverse order)
        List<FlipSuggestion> suggestions = new ArrayList<>(topSuggestions);
        suggestions.sort(getSortComparator());

        long duration = System.currentTimeMillis() - startTime;
        log.debug("Generated {} flip suggestions in {}ms", suggestions.size(), duration);
        return suggestions;
    }

    private Comparator<FlipSuggestion> getSortComparator()
    {
        switch (config.suggestionSortBy())
        {
            case MARGIN:
                return Comparator.comparingLong(FlipSuggestion::getMargin).reversed();
            case MARGIN_PERCENT:
                return Comparator.comparingDouble(FlipSuggestion::getMarginPercent).reversed();
            case VOLUME:
                return Comparator.comparingLong(FlipSuggestion::getVolume1h).reversed();
            case ROI:
                return Comparator.comparingDouble(FlipSuggestion::getRoi).reversed();
            case PROFIT_PER_LIMIT:
            default:
                return Comparator.comparingLong(FlipSuggestion::getProfitPerLimit).reversed();
        }
    }

    /**
     * Get suggestions specifically optimized for quick flipping.
     * Sorted by QF score descending, filtered to grade B or better (score >= 50).
     */
    public List<FlipSuggestion> getQuickFlipSuggestions()
    {
        List<FlipSuggestion> all = generateSuggestions();
        return all.stream()
            .filter(s -> s.getQfScore() >= 50)
            .sorted(Comparator.comparingInt(FlipSuggestion::getQfScore).reversed())
            .collect(Collectors.toList());
    }

    @Data
    public static class FlipSuggestion
    {
        private int itemId;
        private String itemName;
        private long buyPrice;
        private long sellPrice;
        private long margin;
        private double marginPercent;
        private long taxPerItem;
        private long volume1h;
        private int buyLimit;
        private long profitPerLimit;
        private double roi;
        private long capitalRequired;
        private double compositeScore;
        private boolean alchProfitable;
        private int qfScore;                // Quick flip score (0-100)
        private String qfGrade;             // Quick flip grade (S/A/B/C/F)
        private String estimatedFillTime;   // Estimated fill time for quick flip
    }
}
