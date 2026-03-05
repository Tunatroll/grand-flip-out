# Self-Improvement Protocol

> How Grand Flip Out learns, adapts, and gets smarter over time.
> This is the meta-document that governs all other analysis documents.

---

## Design Principle: Intelligence Without Waste

The system must be smart enough to provide genuinely useful flip recommendations while being efficient enough to run entirely client-side from a static GitHub Pages site. No backend. No database. No server costs. Just the OSRS Wiki API and clever client-side computation.

### What "Smart" Means Here

1. **Never show useless data.** Profit per item alone is meaningless. Always show: potential profit per buy-limit cycle, realistic quantity (min of buy limit and estimated 4h volume), capital required, ROI, and time to fill.

2. **Debate every recommendation.** No single metric should drive a decision. The system weighs margin, volume, trend, manipulation risk, and fill probability against each other. See FLIP_INTELLIGENCE.md.

3. **Self-correct.** When predictions are wrong, the system adjusts its weights. When new market patterns emerge, the system incorporates them. See MARKET_ANALYSIS.md.

4. **Minimize user effort.** The website should auto-refresh, sort intelligently, and highlight the best opportunities without requiring the user to dig through data.

---

## The Three-Document System

This project uses three competing analysis documents that cross-reference and challenge each other:

### Document 1: FLIP_INTELLIGENCE.md
**Role**: The core engine. Defines how items are scored (JTI), how advisors debate, and how predictions are generated.

**Self-improvement mechanism**: Tracks prediction accuracy. If the Margin Hunter advisor is consistently wrong about an item category, its weight is reduced for that category. If the Volume Trader is right 80% of the time on consumables, its weight increases there.

### Document 2: MARKET_ANALYSIS.md
**Role**: The strategist. Analyzes macro conditions, compares strategies (volume vs. margin, trend vs. mean reversion), and compiles current best-practice recommendations.

**Self-improvement mechanism**: When a strategy debate verdict is proven wrong by market data, the verdict is updated. For example, if the system recommends "trend following for godswords" but godsword prices mean-revert instead, the analysis is corrected.

### Document 3: SELF_IMPROVEMENT.md (this document)
**Role**: The meta-controller. Governs how the other two documents evolve and ensures the system doesn't drift into bad habits.

**Self-improvement mechanism**: This document is updated whenever a systemic failure is identified — not just a single bad prediction, but a pattern of failures that indicates a fundamental assumption is wrong.

### How They Debate Each Other

The documents form a triangle of accountability:

```
FLIP_INTELLIGENCE.md      MARKET_ANALYSIS.md
    (Engine)        <->      (Strategy)
         \                    /
          \                  /
           v                v
      SELF_IMPROVEMENT.md
          (Meta-Controller)
```

- FLIP_INTELLIGENCE says "this item scores 85, BUY signal"
- MARKET_ANALYSIS says "but we're in a volatile regime, reduce confidence by 20%"
- SELF_IMPROVEMENT says "last time you disagreed like this, the strategy doc was right 65% of the time — go with the reduced confidence"

---

## Improvement Cycles

### Micro-Cycle (Every Auto-Refresh, ~30s)
- Update prices from Wiki API
- Recalculate all scores
- Detect any new manipulation flags
- Re-sort recommendations

### Short Cycle (Every 4 Hours)
- One full buy-limit cycle has elapsed
- Compare predicted profit vs. achievable profit
- Log whether volume estimates were accurate
- Adjust item-specific confidence modifiers

### Medium Cycle (Daily)
- Analyze which strategies performed best today
- Detect day-of-week patterns
- Update macro market assessment
- Identify items that should be added/removed from focus list

### Long Cycle (Weekly)
- Deep analysis of all prediction accuracy
- Weight rebalancing across all three advisors
- Strategy debate resolution based on accumulated evidence
- Document updates to all three .md files

---

## Error Taxonomy

### Type 1: False Positive (Recommended a Bad Flip)
**Cause**: Margin existed at scan time but evaporated before execution.
**Fix**: Factor in margin persistence. If margin has only existed for < 10 minutes, reduce confidence.

### Type 2: False Negative (Missed a Good Flip)
**Cause**: Item was filtered out by manipulation detection or low JTI score.
**Fix**: Review filter thresholds. If an item was profitable despite a low score, the scoring model needs recalibration.

### Type 3: Volume Overestimate
**Cause**: Projected 4h volume from 1h * 4 but actual volume was lower (off-peak hours, weekend).
**Fix**: Apply time-of-day and day-of-week multipliers to volume estimates.

### Type 4: Regime Misclassification
**Cause**: Classified market as "ranging" when it was actually "trending" due to a game update.
**Fix**: Incorporate external signals (OSRS news, update schedules) into regime classification.

### Type 5: Manipulation False Alarm
**Cause**: Natural volatility in a thinly-traded item was flagged as manipulation.
**Fix**: Increase spread tolerance for items with naturally high volatility (low-volume equipment).

---

## Implementation Notes

### Client-Side Only
All computation runs in the browser. The website fetches from:
- `https://prices.runescape.wiki/api/v1/osrs/latest` (prices)
- `https://prices.runescape.wiki/api/v1/osrs/5m` (5-minute timeseries)
- `https://prices.runescape.wiki/api/v1/osrs/1h` (1-hour timeseries)
- `https://prices.runescape.wiki/api/v1/osrs/mapping` (item metadata)

The Website caches responses and computes everything locally. No user data leaves the browser.

### Plugin Integration
The RuneLite plugin uses the same API endpoints plus RuneLite's injected OkHttpClient. It can also access the GrandExchangeOfferChanged event to track actual completed trades, enabling real accuracy measurement for the self-improvement loop.

### No Token Waste
The system is designed to be self-sufficient. It does not require AI tokens to operate. The intelligence is encoded in the scoring algorithms, debate frameworks, and improvement protocols described in these documents. AI assistance is only used for occasional document updates and code improvements — not for real-time operation.

---

## Version History

| Version | Date | Change |
|---------|------|--------|
| 1.0 | 2026-03-05 | Initial creation. Three-document system established. |

---

## Compliance

All analysis is **information only**. No game automation. No trade execution. No memory reading beyond RuneLite Client API. Compliant with Jagex Third-Party Client Guidelines.

---

*This document governs the evolution of the Grand Flip Out intelligence system.*
