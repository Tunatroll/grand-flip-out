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

Grounded confirmation, read this session:

- `model/Suggestion.java` carries `price` (the BUY price) and `marginPer`. There is **no
  target sell price field**.
- `grep Suggestion src/main/java/com/fliphelper/tracker/*.java` returns **nothing**. A
  recommendation and the flip it produces are entirely unlinked.
- `FlipItem.sellPrice` is assigned from `trade.getPrice()` (`tracker/FlipTracker.java:224`) —
  the price you *actually* sold at, recorded after the fact. Not the advised one.

So the advised sell number lives only in the advisor card's UI state. Advance the card, buy a
second item, or relog, and it is gone permanently and unrecoverably.

`cbea103` ("keep the flip card until you place the sell offer", pinned by
`AdvisorHoldForSellTest`) mitigates exactly one case: a single card, in memory, this session.
It persists nothing and does not survive a second concurrent position or a relog.

**Competitive note:** Flipping Copilot issue #73 (open since 2026-02-05) is the same class of
bug. Neither product currently remembers advice across positions.

## 2. Leverage what already exists — do NOT rebuild

- **`tracker/FlipTracker.java`** already models `BUYING → BOUGHT → SELLING → COMPLETE`
  (`model/FlipState.java`) and already persists across sessions behind
  `config.persistHistory()` → `loadHistory()` / `saveHistory()`.
- **Local store pattern** for plugin-dir files: `util/BlacklistStore.java`,
  `util/WatchlistStore.java`.
- **Sell arming** already exists: `armOfferFill` / `injectGeInput` / `fillGe*`, gated by
  `enableGePriceFill` (off by default).

**Implication:** this is *attaching advice to an existing tracked flip and surfacing it* — not
a new positions subsystem. No new store, no new persistence mechanism.

## 3. Design

### 3.1 Data

Extend the tracked flip with advice provenance:

| Field | Meaning |
|---|---|
| `advisedSellPrice` | the sell price the advisor quoted when this buy was recommended |
| `advisedMarginPer` | after-tax per-item margin quoted at advice time |
| `advisedAt` | epoch ms the advice was given |
| `fromAdvisor` | true only when this lot originated from a recommendation |

Rides the existing `saveHistory()` / `loadHistory()` path — survives relog for free.

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

### 3.3 Display — the actual fix

A new **Open Positions** section: every lot in `BOUGHT` / `SELLING` with unsold quantity
remaining, showing per row:

```
Magic logs   ×2,400 unsold
bought 1,024 · advised sell 1,102 (14:32) · now 1,088
```

- Independent of whatever card the advisor is currently showing.
- Persists across relog.
- Empty when nothing is held — honestly empty, never a placeholder row.

### 3.4 Action

One click per row arms the sell fill at the advised price via the existing `armOfferFill`,
behind the existing `enableGePriceFill` gate. No new injection path; the one-hotkey-one-action
rule is untouched.

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

- advice is stamped onto the lot that the recommendation produced
- a non-advised lot never receives an advised price
- advice survives a save/load round-trip
- a partially-sold lot reports the correct remaining quantity
- two lots of the same item keep distinct advice
- **S3:** a flip is recorded when cash is collected before the last unit sells (red first)
- dead-pick auto-skip does not fire while a position awaits sale

## 7. Size risk and the split, if needed

Rough estimate: data ~20 LOC, capture ~40, Open Positions UI ~120 (Swing), sell arming ~20,
S3 fix ~30, tests ~150 → **~380 LOC**. That fits under the 500-LOC Hub ceiling, but the Swing
section is the volatile part and the whole diff counts.

If it overruns, split on this seam (NOT arbitrarily):

- **PR 1 — memory:** data fields + capture + persistence + S3 fix + tests. No new UI; the
  advice is stamped and durable but not yet displayed.
- **PR 2 — surface:** the Open Positions section + one-click sell arming.

PR 1 alone is defensible on its own: it stops the data loss. PR 2 alone is not — it would have
nothing to show. Ship in that order if a split is forced.

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
