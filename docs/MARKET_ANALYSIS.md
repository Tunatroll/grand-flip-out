# Market Analysis — Compiled Thoughts & Strategy Debates

> Cross-referencing multiple analytical perspectives to find the highest-confidence flips.
> Each section presents competing arguments, then resolves with a verdict.

---

## Current Market State Assessment

### Macro Conditions to Monitor

The OSRS economy is driven by a few key forces. Understanding which regime the market is in changes which strategy wins:

**1. Update Cycles**
When Jagex announces or releases a game update, items related to that content spike in demand (new BiS gear, new consumables needed). Items made obsolete crash. The engine should track the OSRS news RSS and flag items in affected categories.

**2. Bot Economy Pressure**
Resource items (ores, logs, herbs, runes) have artificially depressed prices due to bot farming. When bot ban waves hit, supply drops sharply and margins widen. These are high-confidence SHORT-TERM opportunities — margins spike for 2–12 hours then normalize.

**3. Seasonal Patterns**
Weekends see higher player counts but also more casual sellers who accept lower prices. Margins are often wider from Friday evening to Sunday night. Leagues and tournaments cause temporary surges in specific item categories.

**4. Inflation/Deflation**
GP enters the economy through alching and PvM drops. GP exits through the GE tax, death costs, and gold sinks. In inflationary periods, high-alch items become floor-priced and margins compress. Track the ratio of item prices to their high-alch values as an inflation gauge.

---

## Strategy Debate: Volume vs. Margin

### The Volume Argument
*"Trade a lot of cheap things. Each flip earns little, but you fill limits fast and compound."*

**Evidence for:**
- Air runes: 1 gp margin, 50,000 limit = 50,000 gp per cycle. 12 cycles per day = 600,000 gp/day with almost zero risk.
- Water runes: Similar math. These items ALWAYS have buyers and sellers.
- Fill rate is nearly 100% because volume far exceeds buy limit.
- Ideal for accounts with limited starting capital (under 1M).

**Evidence against:**
- Opportunity cost: Your GE slot is occupied for 4 hours earning 50K when it could earn 500K on a mid-tier item.
- GE slots are limited (8 per account). Volume trading wastes slots on low returns.
- Tax eats into thin margins more significantly (percentage-wise).

### The Margin Argument
*"Trade expensive things less often. Each flip earns a lot, and you need fewer GE slots."*

**Evidence for:**
- Mithril ore: 11 gp margin, 13,000 limit = 143K per cycle. One slot, one cycle, solid return.
- Gold bar: 8 gp margin, 10,000 limit = 80K per cycle with high ROI (8%).
- Dragon items: 500–5,000 gp margins with limits of 8–15K = 4M–75M per cycle.

**Evidence against:**
- Higher-margin items often have lower fill rates. You might set a buy offer and wait hours.
- Margins are less stable. What was 5K margin at 2pm might be 500 gp at 6pm.
- Capital requirements are higher. Need 10M+ to play in the dragon tier.

### Verdict
**Hybrid approach wins.** Use 2–3 GE slots for volume flips (guaranteed income) and 5–6 slots for margin flips (higher potential). The intelligence engine ranks by `EV / capitalRequired / timeRequired` to find the optimal mix for your available capital.

---

## Strategy Debate: Trend Following vs. Mean Reversion

### The Trend Following Argument
*"The trend is your friend. Buy things that are going up, sell things that are going down."*

**Evidence for:**
- When an item gets a buff (game update), the price trends upward for hours/days. Buying early captures the move.
- EMA crossovers (5 > 20) correctly identify sustained moves ~60% of the time.
- Works especially well for high-value items where fundamentals drive price (new content, meta shifts).

**Evidence against:**
- Most GE items range-trade, not trend. Only ~15% of items show sustained directional moves.
- By the time you detect the trend (EMA lag), much of the move has already happened.
- The GE tick rate (5-minute updates from Wiki API) means trends can reverse before you react.

### The Mean Reversion Argument
*"Prices always come back to the average. Buy cheap, sell expensive, relative to the mean."*

**Evidence for:**
- Stable consumables (runes, food, potions) exhibit strong mean reversion. Z-score > 2 = reliable buy signal.
- Works on 85%+ of items that range-trade.
- Simpler to execute: just wait for price to deviate, then trade the bounce.

**Evidence against:**
- Fails catastrophically when fundamentals change (nerfs, new content, economy shifts).
- Mean itself shifts over time. Need to use rolling averages, not static means.
- Slow. Waiting for z-score signals can mean missing faster opportunities.

### Verdict
**Context-dependent.** For consumables and low-tier items, mean reversion dominates. For equipment and high-value items, trend following is more important. The engine uses a regime classifier:
- **Ranging market**: Use mean reversion (z-score signals)
- **Trending market**: Use momentum (EMA crossovers)  
- **Volatile market**: Reduce position size, widen stops
- **Dead market**: Skip (volume too low for reliable fills)

---

## Manipulation Detection

### Red Flags
1. **Volume spike + no price change**: Someone is accumulating without moving the price. Possible setup for a pump.
2. **Price spike + no volume**: Fake wash trades or a single large order. Don't trust the new price.
3. **Spread widening suddenly**: Market makers are pulling out. Something is about to happen.
4. **Unusual buy limit exhaustion**: If you can't fill your limit but the item normally fills easily, someone is cornering supply.

### Defense Protocol
- If manipulation risk score > 0.5 for an item, the engine flags it with a warning.
- Never chase a pump. If price jumped >10% in 1 hour with low relative volume, AVOID.
- Wait for prices to stabilize (spread returning to normal range) before entering.

---

## Capital Allocation Framework

### Starter Account (Under 1M GP)
- Focus entirely on volume flips: runes, arrows, basic food
- Expect 100–300K GP/day from 8 GE slots
- Goal: Build to 5M in 1–2 weeks

### Mid-Range Account (1M–50M GP)
- Mix of volume (3 slots) and mid-tier margin flips (5 slots)
- Items: ores, bars, dragon equipment, potions, bolt tips
- Expect 500K–2M GP/day
- Goal: Build to 100M in 2–4 weeks

### Wealthy Account (50M+ GP)
- Focus on high-margin items with acceptable volume
- Items: Godswords, Zenyte jewelry, ancestral pieces, raid supplies
- Can also bulk-flip processing recipes (buy rune ore + coal, sell rune bars)
- Expect 2–10M GP/day
- Goal: Compound indefinitely

---

## Recipe Analysis

Recipes (processing items for profit) are a separate strategy worth debating:

### Argument For
- Guaranteed profit per item processed (no waiting for GE offers)
- Not subject to margin compression from other flippers
- Combines with volume flipping (buy materials on GE, process, sell product)

### Argument Against
- Requires skill levels and sometimes quest completion
- GP/hr is often lower than pure flipping for the same capital
- Processing time is a hidden cost (clicking, attention)

### Best Recipes (Current Data)
The Recipes tab tracks these dynamically using live Wiki API prices. Top performers typically include:
- High-alch items (Rune platelegs, alch for profit)
- Enchanted jewelry/bolts (diamond bolts(e) → dragon variants)
- Potion mixing (herbs + secondaries)
- Bar smelting (with Blast Furnace for half coal)

---

## Compiled Debate: What Should You Flip Right Now?

This section is the meta-debate. It takes all the above frameworks and compiles a recommendation:

### Decision Tree

```
1. Check capital level → determines tier
2. Check market regime → determines strategy (trend vs. reversion)
3. Pull top items by EV from the Smart tab
4. Cross-reference with manipulation flags
5. Allocate GE slots:
   - 30% to safe volume flips (consumables)
   - 50% to highest-EV margin flips
   - 20% to speculative/trending items
6. Set offers, check back in 30–60 minutes
7. Record results for self-improvement loop
```

### Key Metrics to Watch

| Metric | Good Value | What It Means |
|--------|-----------|---------------|
| JTI Score | > 70 | Item is highly flippable right now |
| Fill Rate | > 80% | Your offers will actually complete |
| Margin Stability | < 15% variance | Margin won't evaporate before you sell |
| Potential Profit/4h | > 50K | Worth occupying a GE slot for |
| ROI | > 5% | Capital is being used efficiently |

---

## Continuous Improvement

This analysis document should be treated as a living system. The website's Smart tab implements these ideas in code. When new patterns emerge or strategies fail, both this document and the code should be updated together.

**Feedback loop:**
1. The website shows recommendations based on these principles
2. Users execute the flips manually
3. Results are compared against predictions
4. Strategies that underperform are demoted; strategies that outperform are promoted
5. This document is updated to reflect the current best understanding

---

*This analysis is for educational purposes. All trading involves risk. Information only — no game automation.*
