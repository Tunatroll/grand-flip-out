# Best Flips Debate

> Three advisors argue over which items to flip right now.
> Updated each session. Each advisor makes their case, then the system resolves the debate.

---

## How This Works

Three AI-modeled advisors each select their top 5 items and argue why their picks are best. A resolution engine weighs the arguments and produces a final ranked list. This document captures the debate format so the website can run it programmatically.

---

## Round 1: The Margin Hunter's Picks

*"I only care about one thing: how much GP lands in your pocket per 4-hour limit cycle."*

### Selection Criteria
- Sort all items by: `(sellPrice - buyPrice - geTax) * min(buyLimit, est4hVolume)`
- Filter: margin > 0, volume > 100/hr, no manipulation flag
- Pick top 5 by raw potential profit

### Typical Champions
1. **High-volume runes** (Air, Water, Fire, Earth) — 1 gp margin but 50,000 limit = 50K per cycle
2. **Adamant/Mithril darts** — 2–5 gp margin, 7K–11K limits = 14–55K per cycle
3. **Ores and bars** (Mithril, Gold, Runite) — 6–15 gp margin, 10–13K limits = 60–195K per cycle
4. **Food items** (Trout, Lobster, Shark) — 3–24 gp margin, 6–10K limits = 18–240K per cycle
5. **Bolt tips / Ammunition** — Variable margins, high limits

### Argument
"Look at the raw numbers. Mithril ore alone can generate 117K per 4-hour cycle. That's a GE slot doing real work. I don't care if it's boring or if the trend is flat — the margin exists RIGHT NOW and the volume proves people are buying and selling constantly."

### Weakness (Identified by Others)
"Margin Hunter ignores that these margins can compress to zero. Right now mithril ore has an 11 gp margin, but yesterday it was 4 gp. You're optimizing for a snapshot, not a sustainable edge."

---

## Round 2: The Volume Trader's Picks

*"I want guaranteed fills. If I set a buy offer, I want it filled in under 30 minutes."*

### Selection Criteria
- Sort by: `est4hVolume / buyLimit` (fill ratio)
- Filter: fill ratio > 2.0 (volume is at least 2x the buy limit), margin > 0
- Among those, pick top 5 by GP/hr (accounting for typical fill time)

### Typical Champions
1. **Air rune** — Fill ratio ~infinite. 50K limit fills in minutes. 50K profit per cycle, but you can do 6+ cycles per day.
2. **Water rune** — Same logic. Near-instant fills.
3. **Thread** — 18K limit, enormous daily volume. 1 gp margin = 18K per cycle.
4. **Iron nails** — 13K limit, high volume. Fills fast.
5. **Fishing bait** — 8K limit, very high volume. Reliable.

### Argument
"My picks might not have the biggest single-cycle profit, but I can cycle them 3–6 times per day because they fill so fast. Air runes at 50K per cycle, 6 cycles = 300K/day from ONE GE slot. That's efficient. Plus my risk is basically zero — these items never stop trading."

### Weakness (Identified by Others)
"You're making 300K/day on a slot? Margin Hunter's mithril ore makes 117K per cycle, and with a 4h fill that's still 117K minimum with potential for 2 cycles = 234K. And that's before looking at higher-tier items. Your ceiling is too low."

---

## Round 3: The Trend Surfer's Picks

*"I'm looking at WHERE prices are going, not where they are. I want items with momentum."*

### Selection Criteria
- Calculate EMA(5) and EMA(20) from 5-minute timeseries for each item
- Filter: EMA(5) > EMA(20) (bullish crossover) AND increasing volume
- Pick top 5 by momentum score * potential profit

### Typical Champions
1. **Post-update items** — Whatever just got buffed or became relevant
2. **Bot ban wave beneficiaries** — Resources that just lost bot supply
3. **Seasonal surgers** — Items trending due to leagues, tournaments, events
4. **Recovery plays** — Items that crashed and are bouncing back (mean reversion + momentum confirmation)
5. **High-value movers** — Godsword components, rare drops that are trending up

### Argument
"You two are fighting over scraps. When a bot ban wave hits earth runes, the margin goes from 1 gp to 4 gp for 8–12 hours. That's a 4x multiplier on the Volume Trader's pick. When a new quest releases that needs specific items, prices can jump 20–50% in a day. I'm catching those moves while you're making 50K on air runes."

### Weakness (Identified by Others)
"How often do bot ban waves happen? Once a month? Twice? The rest of the time you're sitting on losing positions waiting for the next move. And your EMA crossover signals are lagging — by the time you see the cross, half the move is done."

---

## Resolution: The Verdict

The system resolves the debate using Expected Value per GP risked:

```
Score = (potentialProfit * fillProbability * (1 - manipRisk) * trendMultiplier) / capitalRequired

Where:
  fillProbability = min(1.0, est4hVolume / buyLimit)
  manipRisk = 0.0 to 1.0 from manipulation detector
  trendMultiplier = 1.0 (neutral), 1.2 (bullish), 0.8 (bearish)
  capitalRequired = buyPrice * realisticQty
```

### Final Rankings (Template)

| Rank | Item | Advisor | Potential Profit | Fill Rate | Trend | Score |
|------|------|---------|-----------------|-----------|-------|-------|
| 1 | [Best item] | [Which advisor championed it] | [GP/cycle] | [%] | [Bull/Bear/Neutral] | [Composite] |
| 2 | ... | ... | ... | ... | ... | ... |
| 3 | ... | ... | ... | ... | ... | ... |
| ... | ... | ... | ... | ... | ... | ... |

*Rankings are computed dynamically by the website's Smart tab using live Wiki API data.*

---

## Historical Debate Outcomes

Tracking which advisor was right most often helps calibrate future debates:

| Date | Winning Advisor | Context | Accuracy |
|------|----------------|---------|----------|
| 2026-03-05 | (Initial) | System created. No historical data yet. | N/A |

As the system accumulates data, this table reveals which advisor strategy dominates in different market conditions. This feeds back into the weight system described in FLIP_INTELLIGENCE.md.

---

## How to Use This

1. Open the website at tunatroll.github.io/grand-flip-out/
2. Go to the **Smart** tab — this implements the debate resolution algorithmically
3. Items are sorted by the composite score that resolves all three advisors' arguments
4. Click any item to see the detailed breakdown (charts, signals, all the data each advisor uses)
5. Make your own decision — the system provides intelligence, you provide judgment

---

*Information only. No game automation. Compliant with Jagex Third-Party Client Guidelines.*
