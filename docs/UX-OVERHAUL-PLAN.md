# Plugin UX/content overhaul — chunk plan (authored 2026-07-06, Fable session)

Owner mandate (2026-07-06): "update how the plugin works and visuals and what it serves and
cut the bad things or organize it better… opus didnt do great at that." Constraints that shape
every chunk: Hub PRs stay **<500 LOC**, one PR in flight at a time (**#13341 is open — nothing
ships until it merges**), no over-refactor of an approvable plugin (owner scope-lock), all
maintainer rules per `.claude/skills/runelite-plugin-dev`.

## Grounded current state (read 2026-07-06)
- `ui/GrandFlipOutPanel.java` is a **2,454-line monolith**: tab shell + Prices/Flips/History
  tabs + Intel tab (`buildIntelTab` :2143) + card builders for flip/history/trade-log/price
  (:1106-1900) + links footer + unlock CTA + header/stat cards — all in one class.
- Separate panels already exist and register via `addTab`: `AdvisorPanel` (604), `RecipePanel`
  (426), `StatsPanel` (422), `GuidePanel`, `PriceChartPanel`.
- Config has 6 sections (Data Source / Flip Tracker / Overlay / GFO Account & API / Advisor /
  Server Intelligence) — sane; no config work needed (renames wipe user settings).
- The API now serves the #162 tier fields on BOTH plugin payloads (`marginQuality`,
  `priceTier`, `roundTripExecutable`) — the plugin consumes `marginQuality` (GOLD
  ESTIMATE/NO-ESTIMATE badge, shipped in #13341) but **not yet `priceTier`/`roundTripExecutable`**.

## What to CUT (weak surfaces — each its own commit inside a chunk)
1. **Tab sprawl**: 8+ tabs (Prices, Flips, History, Guide, Intel, Advisor, Recipes, Stats) on a
   ~240px sidebar is the "opus didn't organize it" smell. Target **5**: `Advisor` (default,
   the product) · `Prices` · `Flips` (absorbs History as a toggle — active/past is ONE
   concern) · `Intel` (absorbs Stats: session stats are 4 numbers, not a tab) · `Guide`.
   Recipes → a collapsible section inside Prices (it's price-context, not a destination).
2. **Duplicate empty-state copy paths** in the monolith's card builders — one shared
   `emptyState(String)` helper.
3. **Unlock CTA duplication** (`buildUnlockCta`) rendered per-tab — build once, reuse.

## What to REORGANIZE (structure, no behavior change)
4. **Split the monolith by tab** (mechanical, ~1 chunk per file, behavior-identical):
   `ui/tabs/PricesTab.java`, `ui/tabs/FlipsTab.java` (+history), `ui/tabs/IntelTab.java`,
   with `GrandFlipOutPanel` reduced to the shell (tab registration + shared header/footer).
   Card builders move with their tab; shared card primitives → `ui/Cards.java`.

## What to SERVE differently (content honesty — the Fable-tier part)
5. **Consume `priceTier` + `roundTripExecutable`** (already on the wire): the Advisor headline
   should lead with the tier the API already grades — EXECUTABLE (mint) / one-sided (the
   existing GOLD badge) / INDICATIVE ("stale book — context only") / NO_ESTIMATE (refuse the
   row). Prices tab rows get the same compact tier glyph. This is doctrine law #10 on the
   plugin: label on display, gate on advice.
6. **Fantasy-vs-realizable contrast** on flip cards: where a card shows margin×limit anywhere,
   pair it with the served realizable number (the site's FlipTicket contrast, ported).
7. **Recovery context on dump advice**: the advisor's dump rows should show the served
   `hist_recovery` line (N recorded · rate · median) exactly as item.html now does — same
   fields, verbatim, sample-gated.

## Chunk sequence (each <500 LOC, one Hub PR at a time, in order)
- **Chunk A** (after #13341 merges): #5 tier consumption + #6 contrast — pure content honesty,
  small diff, high user value. Includes tests where the repo has them + `./gradlew clean build`.
- **Chunk B**: #1 tab consolidation + #2/#3 dedupe (UI reorganization, no new features).
  **✅ BUILT 2026-07-10 on `chunk-b-tabs` (clean build + tests green; local, push owner-gated).**
  Re-grounded first — the 07-06 state above had rotted: the LIVE plugin showed only 4 tabs
  (Advisor/Flips/History/Guide) since the Hub-submission slim (91dec5d); pricesTab/intelPanel/
  recipesTab were BUILT-but-never-added (allocated dark every boot) and buildUnlockCta has ONE
  call site (no per-tab duplication → #3 was moot, skipped as churn). Shipped shape: 5 tabs —
  Advisor (default) · Prices (+Recipes as a segmented CardLayout card) · Flips (+History card) ·
  Intel · Guide; existing builders reused untouched (refresh paths mutate the same instances);
  dead `openRecipesTab` deleted (its recipesBtn caller was removed in the slim); #2 done via one
  `emptyStateLabel` (4 drifted copies). ~72+/29− LOC. REMAINING before the Hub PR: owner runs the
  client for a visual pass (no screenshot harness in this repo) + owner OK to push.
- **Chunk C+**: #4 file split, one tab per PR (mechanical, reviewer-friendly).
- **Chunk D**: #7 recovery history line.

## Hard rules re-checks per chunk
JDK-11 `clean build` green · no banned APIs (injected Gson/OkHttp only) · client reads on the
client thread · Swing on EDT · startUp/shutDown symmetry · sync-public-repo + leak-check before
any public push · **owner OK before every public push / Hub PR** · never resurrect the closed
PR #12972 branch.
