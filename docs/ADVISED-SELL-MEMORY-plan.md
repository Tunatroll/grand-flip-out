# Advised Sell Memory — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The sell price the advisor recommended is remembered against the position it produced and stays visible in the side panel until the item is sold.

**Architecture:** The server already computes the advised sell and discards it before serving; expose it. The plugin already has a persisted per-position target (`FlipItem.frozenSellPrice`) and an open-positions list (`activeFlipsPanel`); stamp the advised number onto the lot and render it on the existing card. No new store, no new panel section.

**Tech Stack:** Java 11, RuneLite, Lombok `@Builder`, Gson (injected), JUnit 4.12. Server: Node/Express.

## Global Constraints

- **Design doc:** `docs/ADVISED-SELL-MEMORY-design.md` — read it first.
- **JDK 11 only.** `~/.gradle/gradle.properties` pins Temurin 11. Host java is noise.
- **Build oracle is `./gradlew cleanTest test`.** Plain `test` reports BUILD SUCCESSFUL from cache without running. Verify via totals in `build/test-results/test/*.xml`, never the BUILD line.
- **Hub PR < 500 LOC**, one PR in flight. Lane currently clear.
- **Owner fires every public push.** Commit locally; never push either repo.
- **No new dependencies.** `@Inject` the client's Gson, never `new Gson()`.
- **Never recompute profit client-side** — display server numbers, don't re-derive them.
- `frozenSellPrice` semantics must NOT change: it feeds the server's `hitTarget` metric.

---

### Task 1: Server — serve the advised sell price

**Repo:** `~/projects/gfo-ecosystem`

**Files:**
- Modify: `server/routes/intelligence-tools.js` (candidate object ~:164; `/suggest` response ~:360)
- Test: `server/__tests__/advisor-sell-price.test.js` (create)

**Interfaces:**
- Produces: `/api/intelligence/suggest` BUY responses gain `sellPrice` (number, gp). Consumed by Task 2.

- [ ] **Step 1: Write the failing test**

```js
import { test } from 'node:test';
import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
function assert(c, m) { if (!c) throw new Error(m || 'assertion failed'); }
const here = dirname(fileURLToPath(import.meta.url));
const src = readFileSync(join(here, '..', 'routes', 'intelligence-tools.js'), 'utf8');

test('rankBuyCandidates returns sellPrice on the candidate', () => {
  assert(/buyPrice,\s*sellPrice,\s*marginPer/.test(src),
    'the candidate object must carry sellPrice — it is computed then dropped');
});

test('/suggest serves sellPrice so the plugin can remember the advised target', () => {
  assert(/sellPrice:\s*best\.sellPrice/.test(src),
    '/suggest must serve the advised sell price, not only bury it in reasons prose');
});
```

- [ ] **Step 2: Run it and confirm it FAILS**

```bash
cd ~/projects/gfo-ecosystem/server && npx node --test __tests__/advisor-sell-price.test.js
```
Expected: 2 failures.

- [ ] **Step 3: Add `sellPrice` to the returned candidate**

In `rankBuyCandidates`, the returned candidate currently reads:

```js
        buyPrice, marginPer, qty, score, vol, weakSide, estFillMin, geLimit, signalReason,
```

Change to:

```js
        buyPrice, sellPrice, marginPer, qty, score, vol, weakSide, estFillMin, geLimit, signalReason,
```

- [ ] **Step 4: Serve it on the BUY response**

In the `/suggest` response object, directly after `price: best.buyPrice,` add:

```js
        sellPrice: best.sellPrice,                        // the advised sell target (plugin remembers it)
```

- [ ] **Step 5: Register the test file**

Add `'advisor-sell-price.test.js'` to BOTH `testFiles` and `nodeTestFiles` in `server/__tests__/test-runner.js` (the runner rejects unregistered files).

- [ ] **Step 6: Run the full server suite**

```bash
cd ~/projects/gfo-ecosystem && flock -w 3600 /tmp/gfo-heavy.lock npm run test:api
```
Expected: all pass, count up by 2.

- [ ] **Step 7: Commit (do NOT push)**

```bash
cd ~/projects/gfo-ecosystem
git add server/routes/intelligence-tools.js server/__tests__/advisor-sell-price.test.js server/__tests__/test-runner.js
git commit -m "feat(advisor): serve the advised sellPrice on /suggest

rankBuyCandidates computes sellPrice = item.highPrice and uses it, but dropped it from the
returned candidate, so the number only ever reached the plugin inside the prose reasons
string. The plugin needs it structurally to remember what it advised."
```

---

### Task 2: Plugin — `Suggestion.sellPrice`

**Files:**
- Modify: `src/main/java/com/fliphelper/model/Suggestion.java`
- Test: `src/test/java/com/fliphelper/api/IntelligenceClientTest.java`

**Interfaces:**
- Consumes: `sellPrice` from Task 1.
- Produces: `Suggestion.getSellPrice()` → `long`. Consumed by Task 4.

- [ ] **Step 1: Write the failing test**

Append to `IntelligenceClientTest`:

```java
    @Test
    public void suggestionCarriesTheAdvisedSellPrice()
    {
        Suggestion s = Suggestion.builder().itemId(4151).price(1024L).sellPrice(1102L).build();
        assertEquals(1102L, s.getSellPrice());
    }
```

- [ ] **Step 2: Run it and confirm it FAILS**

```bash
cd ~/projects/grand-flip-out-hub && ./gradlew cleanTest test --tests '*IntelligenceClientTest*'
```
Expected: compile error — `sellPrice` is not a builder method.

- [ ] **Step 3: Add the field**

In `Suggestion.java`, directly after `long price;`:

```java
    /** The sell target the advisor quoted for this buy (0 = the server did not supply one). */
    long sellPrice;
```

Gson leaves it `0` on older server responses — the correct fail-closed default.

- [ ] **Step 4: Run the test — expect PASS**

```bash
./gradlew cleanTest test --tests '*IntelligenceClientTest*'
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fliphelper/model/Suggestion.java src/test/java/com/fliphelper/api/IntelligenceClientTest.java
git commit -m "feat(model): Suggestion carries the advised sellPrice"
```

---

### Task 3: Plugin — advised fields on the tracked flip

**Files:**
- Modify: `src/main/java/com/fliphelper/model/FlipItem.java`
- Test: `src/test/java/com/fliphelper/model/AdvisedSellTest.java` (create)

**Interfaces:**
- Produces: `FlipItem.getAdvisedSellPrice()` → `long`, `FlipItem.getAdvisedAt()` → `long`. Consumed by Tasks 4 and 5.

- [ ] **Step 1: Write the failing test**

```java
/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */
package com.fliphelper.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AdvisedSellTest
{
    @Test
    public void advisedFieldsDefaultToZeroOnLegacyRows()
    {
        FlipItem f = FlipItem.builder().itemId(4151).build();
        assertEquals(0L, f.getAdvisedSellPrice());
        assertEquals(0L, f.getAdvisedAt());
    }

    @Test
    public void advisedSellIsIndependentOfFrozenSell()
    {
        // frozenSellPrice = market high at buy time; advisedSellPrice = what we advised.
        // They differ, and BOTH must survive — frozenSellPrice feeds the server's hitTarget.
        FlipItem f = FlipItem.builder()
            .itemId(4151).frozenSellPrice(1088L).advisedSellPrice(1102L).advisedAt(1784000000000L)
            .build();
        assertEquals(1088L, f.getFrozenSellPrice());
        assertEquals(1102L, f.getAdvisedSellPrice());
    }
}
```

- [ ] **Step 2: Run it and confirm it FAILS**

```bash
./gradlew cleanTest test --tests '*AdvisedSellTest*'
```
Expected: compile error — no `advisedSellPrice` builder method.

- [ ] **Step 3: Add the fields**

In `FlipItem.java`, directly after the `frozenSellPrice` declaration:

```java
    /**
     * The sell price the ADVISOR recommended for this lot (0 = this buy did not come from a
     * recommendation). Distinct from {@link #frozenSellPrice}, which is the raw market high at
     * buy time and feeds the server's hitTarget metric — do not conflate them.
     */
    private long advisedSellPrice;
    /** Epoch ms the advice was given (0 = none). Rendered so old advice is never read as current. */
    private long advisedAt;
```

- [ ] **Step 4: Run the test — expect PASS**

```bash
./gradlew cleanTest test --tests '*AdvisedSellTest*'
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fliphelper/model/FlipItem.java src/test/java/com/fliphelper/model/AdvisedSellTest.java
git commit -m "feat(model): remember the advised sell price alongside the frozen market price"
```

---

### Task 4: Plugin — capture the advice onto the lot it produced

**Files:**
- Create: `src/main/java/com/fliphelper/tracker/PendingAdvice.java`
- Modify: `src/main/java/com/fliphelper/tracker/FlipTracker.java` (buy branch, ~:164-181)
- Test: `src/test/java/com/fliphelper/tracker/PendingAdviceTest.java` (create)

**Interfaces:**
- Consumes: `Suggestion.getSellPrice()` (Task 2), `FlipItem.advisedSellPrice` (Task 3).
- Produces: `PendingAdvice.record(int itemId, long sellPrice, long nowMs)`, `PendingAdvice.claim(int itemId, long nowMs)` → `long[] {sellPrice, advisedAt}` or `null`.

- [ ] **Step 1: Write the failing test**

```java
/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */
package com.fliphelper.tracker;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

public class PendingAdviceTest
{
    private static final long T0 = 1_784_000_000_000L;

    @Test
    public void aClaimedAdviceReturnsItsPriceAndTimestamp()
    {
        PendingAdvice p = new PendingAdvice();
        p.record(4151, 1102L, T0);
        assertArrayEquals(new long[]{1102L, T0}, p.claim(4151, T0 + 5_000));
    }

    @Test
    public void adviceIsClaimedOnceOnly()
    {
        PendingAdvice p = new PendingAdvice();
        p.record(4151, 1102L, T0);
        p.claim(4151, T0 + 1_000);
        assertNull("a second buy must not inherit the first buy's advice", p.claim(4151, T0 + 2_000));
    }

    @Test
    public void adviceOlderThanThirtyMinutesNeverAttaches()
    {
        PendingAdvice p = new PendingAdvice();
        p.record(4151, 1102L, T0);
        assertNull(p.claim(4151, T0 + 30 * 60_000 + 1));
    }

    @Test
    public void mostRecentAdviceForAnItemWins()
    {
        PendingAdvice p = new PendingAdvice();
        p.record(4151, 1102L, T0);
        p.record(4151, 1150L, T0 + 60_000);
        assertArrayEquals(new long[]{1150L, T0 + 60_000}, p.claim(4151, T0 + 61_000));
    }

    @Test
    public void anUnadvisedItemClaimsNothing()
    {
        assertNull(new PendingAdvice().claim(4151, T0));
    }
}
```

- [ ] **Step 2: Run it and confirm it FAILS**

```bash
./gradlew cleanTest test --tests '*PendingAdviceTest*'
```
Expected: compile error — `PendingAdvice` does not exist.

- [ ] **Step 3: Implement `PendingAdvice`**

```java
/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */
package com.fliphelper.tracker;

import java.util.HashMap;
import java.util.Map;

/**
 * Advice waiting to be attached to the buy it produced.
 *
 * Keyed on itemId ALONE, deliberately not on price: players routinely buy at a price other
 * than the advised one (undercutting, or the book moves between advice and offer), and
 * price-keying would silently fail to stamp exactly the lots most worth remembering.
 * Claim-once, and expire after 30 minutes so stale advice can never attach to an unrelated
 * later buy.
 */
public class PendingAdvice
{
    private static final long TTL_MS = 30 * 60_000L;

    private final Map<Integer, long[]> pending = new HashMap<>();

    /** Remember that we advised {@code sellPrice} for {@code itemId} at {@code nowMs}. */
    public void record(int itemId, long sellPrice, long nowMs)
    {
        if (sellPrice <= 0)
        {
            return;
        }
        pending.put(itemId, new long[]{sellPrice, nowMs});
    }

    /**
     * Take the advice for this item, if any is still live.
     *
     * @return {@code {sellPrice, advisedAt}}, or {@code null} when there is none or it expired.
     */
    public long[] claim(int itemId, long nowMs)
    {
        long[] entry = pending.remove(itemId);
        if (entry == null || nowMs - entry[1] > TTL_MS)
        {
            return null;
        }
        return entry;
    }
}
```

- [ ] **Step 4: Run the test — expect PASS**

```bash
./gradlew cleanTest test --tests '*PendingAdviceTest*'
```

- [ ] **Step 5: Wire it into the buy branch**

In `FlipTracker`, add a field and expose the recorder:

```java
    private final PendingAdvice pendingAdvice = new PendingAdvice();

    /** Called when the player acts on a BUY recommendation. */
    public void recordAdvice(int itemId, long advisedSellPrice)
    {
        pendingAdvice.record(itemId, advisedSellPrice, System.currentTimeMillis());
    }
```

In the buy branch (immediately before `FlipItem flip = FlipItem.builder()`, after `frozen` is computed):

```java
                long[] advice = pendingAdvice.claim(trade.getItemId(), System.currentTimeMillis());
```

and add to that builder chain, directly after `.frozenSellPrice(frozen)`:

```java
                    .advisedSellPrice(advice != null ? advice[0] : 0L)
                    .advisedAt(advice != null ? advice[1] : 0L)
```

- [ ] **Step 6: Record advice when the player acts on a recommendation**

In `GrandFlipOutPlugin`, in the `AdvisorPanel.Listener` implementation, extend `onFillOffer`
so the advised sell is captured at the moment the player acts on the card. Add as the FIRST
statement of the existing `onFillOffer` body:

```java
                // Remember what we advised BEFORE the buy lands, so FlipTracker can stamp it
                // onto the lot. currentAdvisedSell() returns 0 when the pick carries no target.
                flipTracker.recordAdvice(itemId, currentAdvisedSell(itemId));
```

And add the resolver to `GrandFlipOutPlugin`:

```java
    /** The advised sell for the item currently shown by the advisor, or 0 if unknown. */
    private long currentAdvisedSell(int itemId)
    {
        java.util.List<com.fliphelper.model.Suggestion> active = activeSuggestions;
        if (active == null)
        {
            return 0L;
        }
        for (com.fliphelper.model.Suggestion s : active)
        {
            if (s.getItemId() == itemId)
            {
                return s.getSellPrice();
            }
        }
        return 0L;
    }
```

- [ ] **Step 7: Run the full suite**

```bash
./gradlew cleanTest test
```
Expected: all pass. Confirm real totals in `build/test-results/test/*.xml` — never the BUILD line.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/fliphelper/tracker/PendingAdvice.java src/main/java/com/fliphelper/tracker/FlipTracker.java src/main/java/com/fliphelper/GrandFlipOutPlugin.java src/test/java/com/fliphelper/tracker/PendingAdviceTest.java
git commit -m "feat(tracker): stamp the advised sell price onto the lot the recommendation produced"
```

---

### Task 5: Plugin — show the remembered target on the flip card

**Files:**
- Modify: `src/main/java/com/fliphelper/ui/GrandFlipOutPanel.java` (`buildFlipCard`)
- Test: `src/test/java/com/fliphelper/ui/TargetLineTest.java` (create)

**Interfaces:**
- Consumes: `FlipItem.getAdvisedSellPrice()`, `getAdvisedAt()`, `getFrozenSellPrice()`.
- Produces: `GrandFlipOutPanel.rememberedTarget(FlipItem)` → `long` (0 = omit the line). Pure and static so it is testable without Swing.

- [ ] **Step 1: Write the failing test**

```java
/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */
package com.fliphelper.ui;

import com.fliphelper.model.FlipItem;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TargetLineTest
{
    @Test
    public void advisedTargetWinsOverTheFrozenMarketPrice()
    {
        FlipItem f = FlipItem.builder().frozenSellPrice(1088L).advisedSellPrice(1102L).build();
        assertEquals(1102L, GrandFlipOutPanel.rememberedTarget(f));
    }

    @Test
    public void fallsBackToFrozenWhenTheBuyWasNotAdvised()
    {
        FlipItem f = FlipItem.builder().frozenSellPrice(1088L).build();
        assertEquals(1088L, GrandFlipOutPanel.rememberedTarget(f));
    }

    @Test
    public void omitsTheLineWhenThereIsNoTargetAtAll()
    {
        assertEquals(0L, GrandFlipOutPanel.rememberedTarget(FlipItem.builder().build()));
    }
}
```

- [ ] **Step 2: Run it and confirm it FAILS**

```bash
./gradlew cleanTest test --tests '*TargetLineTest*'
```
Expected: compile error — `rememberedTarget` does not exist.

- [ ] **Step 3: Add the resolver**

In `GrandFlipOutPanel`:

```java
    /**
     * The target we REMEMBER for this position: what the advisor advised, else the market high
     * captured at buy time, else nothing. Never fabricated — 0 means render no target line.
     */
    static long rememberedTarget(FlipItem flip)
    {
        if (flip == null)
        {
            return 0L;
        }
        if (flip.getAdvisedSellPrice() > 0)
        {
            return flip.getAdvisedSellPrice();
        }
        return Math.max(flip.getFrozenSellPrice(), 0L);
    }
```

- [ ] **Step 4: Render it on the card**

In `buildFlipCard`, after the existing live `expectedSell` row, add:

```java
        long target = rememberedTarget(flip);
        if (target > 0)
        {
            String when = flip.getAdvisedSellPrice() > 0 && flip.getAdvisedAt() > 0
                ? " (advised " + java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                    .withZone(java.time.ZoneId.systemDefault())
                    .format(java.time.Instant.ofEpochMilli(flip.getAdvisedAt())) + ")"
                : "";
            JLabel targetLabel = new JLabel("target " + formatGp(target) + when);
            targetLabel.setForeground(GfoPalette.ACCENT_2);
            card.add(targetLabel);
        }
```

The remembered target is a HISTORICAL fact and is never recomputed — the live price stays on
its own existing row beside it.

- [ ] **Step 5: Run the test — expect PASS**

```bash
./gradlew cleanTest test --tests '*TargetLineTest*'
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/fliphelper/ui/GrandFlipOutPanel.java src/test/java/com/fliphelper/ui/TargetLineTest.java
git commit -m "feat(panel): show the remembered sell target on the open-position card"
```

---

### Task 6: Verify and hand back

- [ ] **Step 1: Full green build**

```bash
cd ~/projects/grand-flip-out-hub && ./gradlew cleanTest test
grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
  build/test-results/test/*.xml \
  | awk -F'"' '{t+=$2; s+=$4; f+=$6; e+=$8} END{print "tests="t" skipped="s" failures="f" errors="e}'
```
Expected: `failures=0 errors=0`. This is the oracle, not the BUILD line.

- [ ] **Step 2: Check the diff size against the Hub ceiling**

```bash
git diff --stat $(git merge-base HEAD origin/master 2>/dev/null || echo HEAD~6)..HEAD | tail -1
```
Expected: well under 500 LOC. If over, see the split seam in the design doc §7.

- [ ] **Step 3: Compliance re-check**

No `Robot`/synthetic `KeyEvent`/reflection/subprocess added; Swing work on the EDT; client reads on the client thread; `@Inject`ed Gson only. This task adds none of those, but confirm the diff.

- [ ] **Step 4: STOP — do not push**

Both repos are owner-fired. Report: what landed, the test totals, the diff size, and that the server change (Task 1) must deploy before the plugin change is useful — the plugin reads `sellPrice` from `/suggest`, so ship the server first or `advisedSellPrice` is silently 0.

---

## Ordering note

Task 1 (server) must **deploy** before Tasks 2–5 do anything visible: with an old server, `Suggestion.sellPrice` is `0`, `PendingAdvice.record` no-ops on `sellPrice <= 0`, and the card falls back to `frozenSellPrice`. That degradation is deliberate and safe — the feature simply stays at today's behaviour until the server catches up.
