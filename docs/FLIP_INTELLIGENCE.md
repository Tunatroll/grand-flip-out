# Flip Intelligence Engine

> Self-improving analysis system for OSRS Grand Exchange flipping.
> This document evolves as market data is collected and patterns emerge.

## Philosophy

Every flip decision should be a debate between competing strategies. No single metric tells the whole story. The intelligence engine weighs multiple perspectives against each other and picks the strategy with the highest expected value per unit of time and capital.

## Core Scoring: JTI (Jagex Trade Index)

The JTI score (0–100) combines:

| Component | Weight | What It Measures |
|-----------|--------|-----------------|
| Margin % | 25% | Profit per GP invested |
| Volume Score | 25% | How many actually trade per hour |
| Buy Limit Efficiency | 20% | Can you fill a full limit cycle? |
| Spread Stability | 15% | Is the margin consistent or erratic? |
| Manipulation Risk | 15% | Signs of artificial price movement |

### Score Interpretation

- **90–100**: Elite flip. High volume, stable margins, low risk. Execute immediately.
- **75–89**: Strong flip. Reliable profit. Minor concerns (volume or spread variance).
- **60–74**: Moderate flip. Proceed with caution. Check charts for trend direction.
- **40–59**: Weak flip. Margin exists but something is off (low volume, unstable, or risky).
- **0–39**: Avoid. Likely manipulated, illiquid, or margin is noise.

---

## The Debate Framework

For every item, three "advisors" argue their case:

### Advisor 1: The Margin Hunter
- Focuses on raw GP profit per buy limit cycle
- Formula: `potentialProfit = margin * min(buyLimit, est4hVolume)`
- Loves high-margin items even if volume is moderate
- Weakness: Ignores trend direction and manipulation risk

### Advisor 2: The Volume Trader
- Focuses on items that trade thousands per hour
- Prefers items where `est4hVolume > buyLimit * 3` (guaranteed fills)
- Willing to accept thin margins if volume ensures consistent execution
- Formula: `throughput = margin * (buyLimit / limitResetHours) * hoursPerSession`
- Weakness: Thin margins can evaporate with a single price tick

### Advisor 3: The Trend Surfer
- Uses timeseries data to identify momentum
- Looks for items where buy price is rising (demand increasing)
- Avoids items where sell price is falling (supply flooding)
- Uses EMA crossover: if EMA5 > EMA20, trend is bullish
- Weakness: Lagging indicator, can enter just as trend reverses

### Resolution Protocol

The engine resolves disagreements by calculating Expected Value:

```
EV = potentialProfit * probabilityOfFill * (1 - manipulationRisk)

where:
  potentialProfit = margin * realisticQty
  realisticQty = min(buyLimit, est4hVolume)
  probabilityOfFill = min(1.0, est4hVolume / buyLimit)
  manipulationRisk = 0.0 to 1.0 based on spread variance + volume spikes
```

The item with the highest `EV / capitalRequired` wins the debate.

---

## Flip Categories

### Tier 1: Bread and Butter (Consumables)
Items that always trade. Runes, arrows, food, potions.

**Debate Position**: These are the safest flips. Volume is astronomical (50k+ per 4h), margins are thin (1–5 gp), but profit per limit cycle is reliable. The Volume Trader wins here.

**When to avoid**: When a bot ban wave hits and supply floods the market temporarily.

### Tier 2: Mid-Range Equipment
Dragon weapons, barrows pieces, popular training gear.

**Debate Position**: Moderate volume, decent margins. The Margin Hunter typically wins because margins can be 2–10% with limits of 8–15k. However, spreads are less stable.

**When to avoid**: After major game updates that change BiS (Best in Slot) calculations.

### Tier 3: High-Value Flips
Godswords, spirit shields, ancestral, twisted bow.

**Debate Position**: Huge margins (100k–10M+) but buy limits of 1–8 and volume measured in dozens per hour. The Trend Surfer is critical here — buying into a declining trend is catastrophic at these values.

**When to avoid**: When approaching a game update that might change the meta.

### Tier 4: Processing / Recipes
Buy raw materials, process them, sell the result.

**Debate Position**: Not true flipping but consistent GP/hr. Requires skill levels. The engine tracks profitable recipes and shows ROI.

---

## Prediction Engine

### Short-Term (Next 1–4 Hours)
Uses the 5-minute timeseries to calculate:
- **Price momentum**: Is the buy/sell moving up or down?
- **Mean reversion probability**: If price deviated >2 standard deviations, expect snapback
- **Volume trend**: Increasing volume + rising price = strong continuation

### Medium-Term (Next 4–24 Hours)
Uses 1-hour timeseries:
- **Support/resistance levels**: Price ranges where buy/sell historically cluster
- **Time-of-day patterns**: Some items have predictable daily cycles (peak trading hours)
- **Day-of-week effects**: Weekend vs weekday volume and margin differences

### Signals
- **STRONG BUY**: JTI > 80, bullish momentum, volume increasing, no manipulation flags
- **BUY**: JTI > 65, stable or bullish, adequate volume
- **HOLD**: JTI 50–65, unclear direction, wait for confirmation
- **SELL**: JTI < 50 or bearish momentum with declining volume
- **AVOID**: Manipulation detected, abnormal spread variance, or dead volume

---

## Self-Improvement Protocol

This system improves by tracking outcomes:

1. **Record predictions**: Each signal (BUY/SELL/HOLD) is logged with timestamp and item data
2. **Measure accuracy**: After 4h, check if the predicted direction was correct
3. **Adjust weights**: If margin predictions are consistently wrong, reduce Margin Hunter weight
4. **Detect regime changes**: If overall accuracy drops below 55%, recalibrate all weights
5. **Track seasonal patterns**: Build a calendar of market events (updates, league starts, holidays)

### Accuracy Targets
- Signal accuracy: > 60% correct direction
- Profit prediction: Within 25% of actual profit achieved
- Volume estimation: Within 30% of actual 4h volume

### Learning from Mistakes

Common failure modes and corrections:
- **False manipulation flag**: Item had natural volatility. Increase spread tolerance for that category.
- **Volume overestimate**: Used 1h volume * 4 but actual volume was lower due to off-peak hours. Apply time-of-day multiplier.
- **Margin evaporation**: Margin existed at check time but disappeared by execution. Factor in margin persistence (how long has this margin held?).

---

## API Integration

All data sourced from the OSRS Wiki Prices API:
- `/api/v1/osrs/latest` — Current prices (refreshed every 60s)
- `/api/v1/osrs/5m` — 5-minute averages (rolling)
- `/api/v1/osrs/1h` — 1-hour averages (rolling)
- `/api/v1/osrs/mapping` — Item names, IDs, limits, and metadata

No backend server needed. The website fetches directly from the Wiki API and computes everything client-side.

---

## Compliance Note

This analysis is **information only**. It does not automate any trades or interact with the game client. All recommendations are suggestions for manual execution. Compliant with Jagex Third-Party Client Guidelines.

---

*Last updated by the intelligence engine. This document is regenerated as the system learns.*
