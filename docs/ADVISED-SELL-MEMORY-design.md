# Design — Advised sell memory (open positions)

**Date:** 2026-07-25
**Status:** APPROVED (brainstorm complete, owner-approved)
**Scope:** RuneLite plugin only. No server change required for v1.
**Constraints:** Hub PR < 500 LOC, one PR in flight (lane is CLEAR — `e43bf8a` is the live
manifest pin and equals local HEAD, so #14269 merged), Java 11, owner fires every push.

---

## 1. Problem (owner, 2026-07-25)

> "it's gotta be remembering what you bought and what you haven't sold, because if someone
> buys something on a recommendation then you need to remember that — people are buying and
> then the number they should've sold for isn't shown anymore to them."

### ⚠ Corrected 2026-07-25 after deeper grounding — a target-memory ALREADY EXISTS

The first draft of this spec proposed building position memory from scratch. That was wrong
and would have duplicated a working system. Verified facts:

- **`FlipItem.frozenSellPrice` already exists, is populated, and persists.** Set at
  `tracker/FlipTracker.java:178` on the buy, carried across the sell at :225.
- It is **already displayed** — `GrandFlipOutOverlay.java:418-423` renders
  `"Sell target: X (+drift)"`, and `InventoryTooltipOverlay.java:195` renders `"Frozen sell:
  X"`.
- It is **already sent to the server** — `IntelligenceClient.java:784` ships it and derives
  `hitTarget`.

So the plugin *does* remember a sell target per position. The real defect is two narrower
things:

**Gap 1 — it remembers the wrong number.** `frozen = agg.getBestHighPrice()`
(`FlipTracker.java:165-169`) is the **market** high price at buy time, taken from the local
price cache. It is **not** what the advisor advised. When the advisor's target and raw market
high differ, the plugin remembers the market number and the advice is lost.

**Gap 2 — the panel never shows it.** `GrandFlipOutPanel.buildFlipCard` computes
`expectedSell = agg.getBestHighPrice()` — the **live** price at render time — and shows profit
derived from it. It never renders `frozenSellPrice`. The remembered target is visible only on
the in-game overlay and the inventory tooltip. A player working in the side panel sees a
number that silently drifts, which is exactly the reported experience.

Supporting: `model/Suggestion.java` has `price` (BUY) and `marginPer` but **no target-sell
field**, and `grep Suggestion src/main/java/com/fliphelper/tracker/*.java` returns nothing —
so nothing links a recommendation to the flip it produced. `cbea103` holds one card in memory
for one session and persists nothing.

**Competitive note:** Flipping Copilot issue #73 (open since 2026-02-05) is the same class of
bug. Neither product currently remembers advice across positions.

## 2. Leverage what already exists — do NOT rebuild

- **`FlipItem.frozenSellPrice`** — the target-memory field, already populated and persisted.
- **`tracker/FlipTracker.java`** already models `BUYING → BOUGHT → SELLING → COMPLETE`
  (`model/FlipState.java`) and already persists behind `config.persistHistory()`.
- **`GrandFlipOutPanel.activeFlipsPanel` + `buildFlipCard`** — the open-positions list already
  exists in the panel (`:1127-1142`, newest-first, honest empty state "No active flips"). **Do
  NOT build a second one.**
- **`GrandFlipOutOverlay:418`** already renders the target with drift; **`InventoryTooltipOverlay:195`**
  already renders it in-game.
- **Sell arming** already exists: `armOfferFill` / `injectGeInput` / `fillGe*`, gated by
  `enableGePriceFill` (off by default).

**Implication:** v1 is (a) capture the ADVISED number instead of losing it, and (b) render the
already-remembered target in the panel card. No new section, no new store, no new persistence,
no new overlay. Estimated ~120 LOC, not the ~380 the first draft assumed.

## 3. Design

### 3.1 Data

Add **alongside** `frozenSellPrice`, not replacing it:

| Field | Meaning |
|---|---|
| `advisedSellPrice` | the sell price the advisor quoted when this buy was recommended (0 = none) |
| `advisedAt` | epoch ms the advice was given (0 = none) |

**Why not just repurpose `frozenSellPrice`:** it is already shipped to the server and is the
basis of the `hitTarget` metric (`IntelligenceClient.java:784-786`). Silently changing its
meaning from "market high at buy time" to "what the advisor advised" would redefine a live
metric underneath itself — a metric-drift bug, and a second source of truth for a number the
server already reasons about. Keep `frozenSellPrice` exactly as-is; add the advised number
beside it.

`fromAdvisor` is not a separate field: `advisedSellPrice > 0` IS the provenance flag, matching
the codebase's existing `frozenSellPrice > 0` idiom (`Overlay:418`, `Tooltip:193`).

Both ride the existing `saveHistory()` / `loadHistory()` path — Gson leaves them `0` on rows
from older history files, which is the correct fail-closed default (same convention as
`liveWitnessed` and `accountId`).

### 3.2 Capture

When the player acts on a BUY recommendation, hold a **pending advice** keyed by `itemId`
alone. When `FlipTracker` opens a `BOUGHT` lot for that item, stamp the advice onto that lot
and clear the pending entry.

**Match on `itemId` only — deliberately not on price.** Players routinely buy at a price other
than the advised one (they undercut, or the book moves between advice and offer). Keying on
price would silently fail to stamp exactly the lots the player most needs remembered. Rules:

- most-recent pending advice for that item wins;
- pending advice expires after 30 minutes unstamped (stale advice must not attach to an
  unrelated later buy);
- the advised price is recorded as advised, and the lot separately records what was actually
  paid — the row can then honestly show both when they differ.

A lot with no matching pending advice gets `fromAdvisor = false` and **no advised number** —
rendered `—`. Never fabricate an advised price for a flip the advisor did not recommend.

### 3.3 Display — extend the EXISTING flip card

`GrandFlipOutPanel.buildFlipCard` already renders every open position and already computes a
live `expectedSell`. Add one line to that card showing the **remembered** target beside the
live number:

```
Magic logs   ×2,400 · Bought
bought 1,024 · now 1,088
target 1,102 (advised 14:32)          ← new line
```

Target resolution, in order: `advisedSellPrice` when set, else `frozenSellPrice`, else omit the
line entirely. Never fabricate a target for a flip that has neither.

No new section, no new list, no new empty state — `activeFlipsPanel` already handles all three
(including "No active flips. Buy something in the GE!").

### 3.4 Action

One click on the card arms the sell fill at the remembered target via the existing
`armOfferFill`, behind the existing `enableGePriceFill` gate. No new injection path; the
one-hotkey-one-action rule is untouched.

### 3.5 Honesty boundary

The advised sell is a **historical fact**, rendered with its timestamp ("advised 1,102 ·
14:32") and never silently rewritten. The live price is a **separate column**. When the market
moves, the player sees both numbers and draws their own conclusion. The plugin never restates
old advice as current truth, and never recomputes profit client-side (that would be a second
source of truth against the server's `realizable.js`).

### 3.6 Relationship to auto-updating suggestions

The owner's earlier answer — *refresh numbers, never swap the item* — is satisfied here: the
**live price column** is what refreshes on a timer; the advised number and the position never
move. Dead-pick auto-skip (owner-selected) applies to the **buy side only** and must never
fire while a position is awaiting sale, or it destroys the very numbers this feature exists to
preserve.

## 4. S3 folded in (owner-approved)

Issue #225 S3 — "flips not recorded when cash is collected before the last unit sells" — lives
in this exact code path. If position tracking becomes the source of truth for *what do I still
hold*, a missed recording silently corrupts it. It is in scope.

`OfferStateRecordingTest` today pins only: cancelled-offers-are-terminal,
active/empty-never-record, and buy-side-read-from-state. **None** covers the
collect-before-last-unit case (verified by reading the file, not inferred from its name). This
needs its own failing test first.

Copilot #6 (open since 2026-03-31) is the same bug, unsolved there.

## 5. Edge cases

- **Partial fills** — a position tracks remaining unsold quantity, not just "bought".
- **Multiple lots of the same item** — advice is per lot, not per item.
- **Bought without a recommendation** — `—`, never a fabricated advised price.
- **Relog mid-position** — persists via the existing history path.
- **`persistHistory` disabled** — positions are session-only; the section says so rather than
  appearing broken.
- **Advised price now unreachable** — still shown, with the live price beside it. No hiding.

## 6. Testing (TDD — failing test first, every item)

- `advisedSellPrice` is stamped onto the lot the recommendation produced
- a lot bought without a recommendation keeps `advisedSellPrice == 0` (never fabricated)
- `frozenSellPrice` is UNCHANGED by this work — still market-high-at-buy, so `hitTarget`
  semantics on the server do not move (regression guard)
- advised fields survive a save/load round-trip; absent on legacy rows they read `0`
- pending advice older than 30 min does not attach to a later buy
- target resolution prefers advised, falls back to frozen, omits when neither
- **S3:** a flip is recorded when cash is collected before the last unit sells (red first)
- dead-pick auto-skip does not fire while a position awaits sale

## 7. Size

Revised after the corrected grounding: 2 fields ~10 LOC, capture ~35, one card line ~20,
sell arming ~15, S3 fix ~30, tests ~120 → **~230 LOC**, comfortably under the 500-LOC ceiling.
No split needed. (The first draft estimated ~380 because it assumed a new Open Positions
section that turned out already to exist.)

## 8. Out of scope (v1)

- Panel decluttering and the one-key action loop (the owner's "too many clicks" / "too much on
  screen") — a separate PR; it touches the injection path and deserves its own review.
- Any server change. v1 uses only what the advisor already returns.
- Surfacing positions on the website.

## 9. Sources

Read this session: `model/Suggestion.java`, `model/FlipState.java`, `model/FlipItem.java`,
`tracker/FlipTracker.java`, `util/BlacklistStore.java`, `GrandFlipOutPlugin.java`
(`requestSuggestion` :888, triggers :322-326/606/622/720/1072, 3s throttle :900),
`OfferStateRecordingTest.java`, `AdvisorHoldForSellTest.java`; Hub manifest pin for
`grand-flip-out`; `gh pr list -R runelite/plugin-hub --author Tunatroll --state open` (empty).
