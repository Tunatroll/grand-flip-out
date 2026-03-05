package com.fliphelper.tracker;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * ManipulationFilter - protects users from recommending commonly manipulated items.
 *
 * Certain items in OSRS are routinely targeted by price manipulation clans
 * (e.g. 3rd age equipment, brutal arrows, certain rare cosmetics).
 * Recommending these as flips is misleading and potentially harmful to the user.
 *
 * Detection strategy (three layers):
 *   1. Hard blacklist  - known chronic manipulation targets by item ID
 *   2. Name heuristics - items whose names match known manipulation patterns
 *   3. Dynamic signals - extreme margin spike, low volume, tiny buy limit
 */
@Slf4j
public class ManipulationFilter
{
    // Hard blacklist - item IDs known to be frequently manipulated
    private static final Set<Integer> BLACKLISTED_IDS = new HashSet<>(Arrays.asList(
        // 3rd age melee
        10330, 10348, 10350, 10352,
        // 3rd age range
        10334, 10336, 10338, 10340,
        // 3rd age mage
        10342, 10344, 10346,
        // 3rd age druidic
        12200, 12203, 12204, 12205,
        // 3rd age longsword / bow / wand / amulet
        12904, 12908, 12926, 12924,
        // Brutal arrows (all types)
        4740, 4741, 4742, 4743, 4744, 4745, 4746,
        // Spirit shields
        13190, 13193, 13196,
        // Spirit shield sigils
        21012, 21006, 21000,
        // Hydra items
        22542, 22547, 22536,
        // Masori set
        26370, 26372, 26374,
        // Raids 3 uniques
        27251, 27254, 27260, 27268, 27272, 27276,
        // Soulreaper axe
        29220
    ));

    // Name-pattern blacklist - catches variant IDs and future items
    private static final String[] BLACKLISTED_NAME_PATTERNS = {
        "3rd age",
        "brutal arrow",
        "elysian",
        "divine spirit",
        "arcane spirit",
        "hydra leather",
        "masori",
        "osmumten",
        "lightbearer",
        "ultor ring",
        "bellator ring",
        "venator ring",
        "magus ring",
        "soulreaper",
        "torva",
        "voidwaker",
        "scythe of vitur",
        "tumeken"
    };

    public enum ManipulationRisk
    {
        SAFE,
        CAUTION,
        HIGH_RISK,
        BLACKLISTED
    }

    @Getter
    public static class RiskAssessment
    {
        private final ManipulationRisk risk;
        private final String reason;

        public RiskAssessment(ManipulationRisk risk, String reason)
        {
            this.risk = risk;
            this.reason = reason;
        }

        public boolean isSuppressed()
        {
            return risk == ManipulationRisk.HIGH_RISK || risk == ManipulationRisk.BLACKLISTED;
        }
    }

    /**
     * Assess an item for manipulation risk.
     *
     * @param itemId         RuneLite item ID
     * @param itemName       Item display name
     * @param currentMargin  Observed net margin (after tax), gp
     * @param avgMargin30d   30-day average margin (0 if unknown)
     * @param volume1h       1-hour trade volume
     * @param buyLimit       GE buy limit
     */
    public RiskAssessment assess(int itemId, String itemName,
                                  long currentMargin, long avgMargin30d,
                                  long volume1h, int buyLimit)
    {
        // Layer 1 - hard ID blacklist
        if (BLACKLISTED_IDS.contains(itemId))
        {
            return new RiskAssessment(ManipulationRisk.BLACKLISTED,
                "Known chronic manipulation target - not recommended.");
        }

        // Layer 2 - name pattern matching
        if (itemName != null)
        {
            String lower = itemName.toLowerCase();
            for (String pattern : BLACKLISTED_NAME_PATTERNS)
            {
                if (lower.contains(pattern))
                {
                    return new RiskAssessment(ManipulationRisk.BLACKLISTED,
                        "Matches known manipulation pattern: " + pattern);
                }
            }
        }

        // Layer 3 - dynamic signals

        // Signal A: tiny buy limit with very high margin = thin market
        if (buyLimit > 0 && buyLimit <= 5 && currentMargin > 500_000)
        {
            return new RiskAssessment(ManipulationRisk.HIGH_RISK,
                "Very low buy limit (" + buyLimit + ") with large margin - thin market manipulation.");
        }

        // Signal B: extreme margin spike vs 30-day average
        if (avgMargin30d > 0 && currentMargin > avgMargin30d * 5)
        {
            long ratio = currentMargin / Math.max(avgMargin30d, 1);
            return new RiskAssessment(ManipulationRisk.HIGH_RISK,
                "Margin is " + ratio + "x the 30-day average - likely a manipulation spike.");
        }

        // Signal C: very low volume with high margin
        if (volume1h < 10 && currentMargin > 1_000_000)
        {
            return new RiskAssessment(ManipulationRisk.CAUTION,
                "Low volume (<10/hr) with high margin - verify price before buying.");
        }

        // Signal D: extremely high absolute margin
        if (currentMargin > 50_000_000)
        {
            return new RiskAssessment(ManipulationRisk.CAUTION,
                "Extremely high margin (>50M gp) - verify this is a real opportunity.");
        }

        return new RiskAssessment(ManipulationRisk.SAFE, "No manipulation signals detected.");
    }

    /**
     * Quick suppression check for use in filtering loops.
     */
    public boolean isSuppressed(int itemId, String itemName,
                                 long currentMargin, long avgMargin30d,
                                 long volume1h, int buyLimit)
    {
        return assess(itemId, itemName, currentMargin, avgMargin30d,
            volume1h, buyLimit).isSuppressed();
    }

    /**
     * Fast hard-blacklist check without dynamic signal evaluation.
     */
    public boolean isHardBlacklisted(int itemId, String itemName)
    {
        if (BLACKLISTED_IDS.contains(itemId)) return true;
        if (itemName == null) return false;
        String lower = itemName.toLowerCase();
        for (String pattern : BLACKLISTED_NAME_PATTERNS)
        {
            if (lower.contains(pattern)) return true;
        }
        return false;
    }
}
