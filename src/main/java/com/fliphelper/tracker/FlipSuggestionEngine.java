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
 *
 * Integrates ManipulationFilter to suppress commonly manipulated items
 * such as 3rd age equipment, brutal arrows, and other thin-market targets.
 */
@Slf4j
public class FlipSuggestionEngine
{
    private final PriceService priceService;
    private final GrandFlipOutConfig config;
    private final ManipulationFilter manipulationFilter;

    public FlipSuggestionEngine(PriceService priceService, GrandFlipOutConfig config)
    {
        this.priceService = priceService;
        this.config = config;
        this.manipulationFilter = new ManipulationFilter();
    }

    /**
     * Generate flip suggestions based on current config and market data.
     * Items flagged as manipulation targets are automatically excluded.
     */
    public List<FlipSuggestion> generateSuggestions()
    {
        List<FlipSuggestion> suggestions = new ArrayList<>();
        int suppressedCount = 0;

        for (PriceAggregate agg : priceService.getAggregatedPrices().values())
        {
            long margin = agg.getConsensusMargin();
            long volume = agg.getTotalVolume1h();
            int limit = agg.getBuyLimit();

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

            long taxPerItem = Math.min((long) (sellPrice * 0.02), 5_000_000L);
            long netMargin = margin - taxPerItem;
            if (netMargin <= 0)
            {
                continue;
            }

            // Manipulation filter: suppress known bad items and dynamic risk signals.
            // avgMargin30d passed as 0 until PriceHistoryCollector provides 30-day data.
            ManipulationFilter.RiskAssessment risk = manipulationFilter.assess(
                agg.getItemId(), agg.getItemName(),
                netMargin, 0L, volume, limit);

            if (risk.isSuppressed())
            {
                suppressedCount++;
                log.debug("Suppressed {} (id={}) from suggestions: {}",
                    agg.getItemName(), agg.getItemId(), risk.getReason());
                continue;
            }

            long profitPerLimit = netMargin * limit;
            double marginPercent = agg.getConsensusMarginPercent();
            double roi = (double) netMargin / buyPrice * 100.0;
            long capitalRequired = buyPrice * limit;

            double volumeScore = Math.min(volume / 100.0, 10.0);
            double marginScore = Math.min(marginPercent, 20.0);
            double roiScore = Math.min(roi, 15.0);

            // CAUTION items get a ranking penalty so they appear lower in the list
            double manipPenalty = (risk.getRisk() == ManipulationFilter.ManipulationRisk.CAUTION) ? 0.6 : 1.0;
            double compositeScore = ((volumeScore * 0.3) + (marginScore * 0.3) + (roiScore * 0.4)) * manipPenalty;

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
            suggestion.setManipulationRisk(risk.getRisk());
            suggestion.setManipulationWarning(
                risk.getRisk() == ManipulationFilter.ManipulationRisk.CAUTION ? risk.getReason() : null);

            suggestions.add(suggestion);
        }

        if (suppressedCount > 0)
        {
            log.info("Suppressed {} manipulation-risk items from suggestions", suppressedCount);
        }

        Comparator<FlipSuggestion> comparator = getSortComparator();
        suggestions.sort(comparator);

        int max = config.maxSuggestions();
        if (suggestions.size() > max)
        {
            suggestions = suggestions.subList(0, max);
        }

        log.debug("Generated {} flip suggestions ({} suppressed as manipulation risk)",
            suggestions.size(), suppressedCount);
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
        /** Manipulation risk level for this item (SAFE, CAUTION, HIGH_RISK, BLACKLISTED). */
        private ManipulationFilter.ManipulationRisk manipulationRisk;
        /** Non-null only when risk is CAUTION - a human-readable warning. */
        private String manipulationWarning;
    }
}
