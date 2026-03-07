package com.fliphelper.tracker;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Analyzes price data and suggests the best items to flip based on
 * margin, volume, buy limits, and configurable criteria.
 */
@Slf4j
public class FlipSuggestionEngine
{
    private final PriceService priceService;
    private final GrandFlipOutConfig config;

    public FlipSuggestionEngine(PriceService priceService, GrandFlipOutConfig config)
    {
        this.priceService = priceService;
        this.config = config;
    }

    /**
     * Generate flip suggestions based on current config and market data.
     */
    public List<FlipSuggestion> generateSuggestions()
    {
        List<FlipSuggestion> suggestions = new ArrayList<>();

        for (PriceAggregate agg : priceService.getAggregatedPrices().values())
        {
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

            // Calculate a composite score
            // Weighs margin, volume, and roi
            double volumeScore = Math.min(volume / 100.0, 10.0); // cap at 10
            double marginScore = Math.min(marginPercent, 20.0); // cap at 20%
            double roiScore = Math.min(roi, 15.0);
            double compositeScore = (volumeScore * 0.3) + (marginScore * 0.3) + (roiScore * 0.4);

            FlipSuggestion suggestion = new FlipSuggestion();
            suggestion.setItemId(agg.getItemId());
            suggestion.setItemName(agg.getItemName());
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

            suggestions.add(suggestion);
        }

        // Sort by configured criteria
        Comparator<FlipSuggestion> comparator = getSortComparator();
        suggestions.sort(comparator);

        int max = config.maxSuggestions();
        if (suggestions.size() > max)
        {
            suggestions = suggestions.subList(0, max);
        }

        log.debug("Generated {} flip suggestions", suggestions.size());
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
    }
}
