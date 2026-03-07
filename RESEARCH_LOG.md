# Grand Flip Out — Research Log
Updated: 2026-03-06

Reference this file instead of re-researching topics already covered.

---

## SESSION 1: Architecture & OSRS Economy (2026-03-06)

### Monolith vs Modular Verdict
- Website at 7,600+ lines is 10x too large for single-file
- Bot was 3,328 lines, refactored into 4 cogs + utils (2,393 lines, 28% reduction)
- Java plugin (627 lines, 33 files) is the gold standard architecture
- **Decision**: Keep website as single HTML file (GitHub Pages constraint) but use internal namespacing. Bot is now modular cogs.

### OSRS Economy Research (2025-2026)
- 2% GE tax introduced May 2022, capped at 5M GP per transaction
- Tax is a gold sink removing ~50B GP/day from the economy
- OSRS Wiki Real-Time Prices API: /latest (60s), /5m, /1h, /6h, /24h, /timeseries, /mapping
- Timeseries retention: 5m=~2 days, 1h=~15 days, 6h=~91 days, 24h=~365 days
- Bot bans: 6.9M accounts banned in 2024 for botting
- Sailing skill announced — will create new item demand categories
- Varlamore expansion added new content and item sinks

### NPC Floor Prices (Verified)
- Blood rune: 200gp (Ali's Wares, Al Kharid)
- Death rune: 180gp (Ali's Wares)
- Nature rune: 90gp (Lundail in Mage Arena / Ali's Wares)
- Cosmic rune: 50gp (various NPC shops)
- Cannonballs: ~5gp (crafting cost floor from steel bars)
- Amethyst arrows: ~2gp (crafting cost floor)

### Jar Generator Ingredients (ONLY 3 imps)
- Eclectic impling jar (also gives clue scrolls = dual demand)
- Nature impling jar
- Essence impling jar
- These are the ONLY 3 imps used as ingredients in jar generators

### Supply Sink Categories
- Degradable armor (Barrows, Crystal, Tentacle whip)
- Consumables (food, potions, ammo)
- Fuel items (cannonballs, runes)
- PvP death mechanics
- GE tax (2%, capped 5M)
- Construction (planks, marble, gold leaf)
- Herblore (herbs + secondaries destroyed on use)

---

## SESSION 2: Performance & Optimization (2026-03-06)

### JavaScript Performance Patterns
- CSS containment (`contain: content`) prevents layout thrashing
- `content-visibility: auto` skips rendering off-screen elements
- DocumentFragment for batch DOM insertion (vs one-by-one appendChild)
- requestAnimationFrame wrapping for render batches
- Event delegation (single listener on parent) vs per-element listeners
- IndexedDB for cross-session storage (30+ days vs 5MB localStorage limit)

### Python Async Patterns
- `heapq.nlargest()` for O(n log k) top-N selection vs full sort O(n log n)
- `asyncio.Lock()` for protecting shared mutable state across tasks
- `aiofiles` for non-blocking file I/O in async context
- `cachetools.TTLCache` for automatic expiry of cached data
- `asyncio.TaskGroup` (Python 3.11+) for structured concurrency

### Streaming Statistics
- Welford's online algorithm: O(1) per update for mean, variance, stddev
- EMA (Exponential Moving Average): 5-period fast vs 20-period slow crossover
- Z-score analysis: (price - mean) / stddev to detect outliers

### Memory Leak Patterns Found & Fixed
- Event listeners accumulating every render cycle → use event delegation
- Unbounded caches (TIMESERIES_CACHE) → LRU with max 100 entries
- Chart.js instances not destroyed before recreation → call chart.destroy()
- Global dicts growing forever → periodic cleanup with TTL

---

## SESSION 3: UX Research (2026-03-06)

### Cognitive Load in Dashboards
- Limit to 5-6 primary data points per screen before cognitive overload
- Top-left quadrant gets maximum attention (F-pattern scanning)
- Recognition > recall: show all needed info, don't make users remember
- Progressive disclosure: summary first, details on expand/click
- Bloomberg = power users (high density), Robinhood = beginners (low density), TradingView = middle ground (customizable)

### OSRS Player Demographics & Behavior
- ~195k concurrent players (March 2026), peaked 1.1M in March 2025
- 112k daily active users on mobile, 457k MAU
- ~40% play on mobile
- Typical session: 45 minutes to 2 hours
- 80%+ multitask while playing (Discord, YouTube, tools on second screen)
- Players check tools every 30-60 seconds in 2-3 second glances
- **Key insight**: Build for glanceable secondary-screen use, not full-attention primary use

### Colorblind Accessibility
- 8% of men, 0.5% of women have red-green colorblindness
- They see both red and green as brown/muddy
- Solutions: blue/orange palette, directional arrows (▲▼), shape prefixes, text labels
- Minimum 4.5:1 contrast ratio for WCAG AA
- Test with Color Oracle, NoCoffee, Colorbrewer 2.0

### Mobile Dashboard Design
- Touch targets minimum 24x24 CSS pixels (WCAG 2.2 AA)
- Single-column on mobile, multi-column on desktop
- Swipe-based pagination instead of click-heavy interfaces
- Test with one-handed use cases
- Hamburger/collapsible navigation

### Discord Bot UX
- Slash commands required (verified bots lose message content access 2026)
- Maximum 5 Action Rows per View, 5 components per Row
- Subclass Buttons and Select Menus for reusable code
- Set timeout + implement on_timeout() to disable expired components
- Use interaction_check() to verify only invoking user can interact
- Persistent views: custom IDs + timeout=None, re-add with Client.add_view()
- Error messages: casual tone, explain what happened, suggest action
- Ephemeral messages for errors (visible only to invoking user)
- Sub-second response time expected by 82% of users

### Data Freshness & Trust
- Show relative timestamps ("5 min ago") not absolute ("14:32:15")
- Color-code freshness: green (<1m), yellow (1-5m), red (>5m)
- Keep showing stale data while refreshing (never show blank/spinner)
- Stale data with transparency > no data
- Delta indicators (▲▼) with sparklines build trust
- Animate value changes subtly (200ms color transition, fade back after 3s)

### Progressive Web App (PWA)
- Single-page HTML apps are ideal PWA candidates
- Service worker can cache entire app for offline use
- Stale-while-revalidate: serve cached immediately, update in background
- IndexedDB for structured offline data (prices, watchlists)
- Push notifications possible via Push API (Chrome, Firefox, Safari 16.4+)
- Web App Manifest for install-to-home-screen
- **Decision**: Defer to Phase 3, focus on core UX first

### Competitive Landscape
- GE Tracker: 737k+ users, terrible mobile UX, aggressively paywalled free tier (7 items only)
- Flipping Utilities: RuneLite plugin, community-driven
- Flipping Copilot: AI-focused differentiation
- **Our advantage**: Free unlimited watchlist, colorblind accessibility, mobile-first, educational framing

---

## SESSION 4: Usability Audit Findings (2026-03-06)

### CRITICAL Issues Found

1. **Price abbreviation in bot utils.py**: format_gp() still abbreviates to 1.5M/5.2K in some code paths — user explicitly wants full comma-separated numbers everywhere
2. **Portfolio data not persisted**: bot._user_portfolios stored in RAM only, lost on restart (watchlists properly saved to JSON, portfolios are not)
3. **DM alerts fail silently**: except Exception: pass swallows all errors when DMing users, no notification that alerts are broken

### HIGH Issues

4. **Empty states lack guidance**: "No dump opportunities" doesn't suggest alternatives
5. **Error messages generic**: "An error occurred" with no actionable next step
6. **Verdict ratings unexplained in Discord**: users see "Great Flip" with no context for what makes it great
7. **Jargon overload**: "JTI Score", "Verdict", "Dump Sniper" assume OSRS knowledge
8. **No onboarding flow**: new users land on dashboard with zero guidance

### MEDIUM Issues

9. **Number formatting inconsistent**: some embeds use format_gp() (abbreviated), others use f"{value:,}" (exact)
10. **Cooldown not explained**: "You are on cooldown" with no reason
11. **Alert conditions opaque**: no preview of when alert will trigger
12. **No loading feedback in bot**: defer() goes silent for 3-5 seconds

### State Management Recommendation
- PubSub pattern for vanilla JS: single source of truth, components subscribe to updates
- Proxy-based reactivity: intercept property changes, trigger UI updates
- State transitions: Loading → Data → Success/Error → Stale
- Never clear old data during refresh (show stale with indicator)

---

## IMPLEMENTATION PRIORITY QUEUE

### Done This Session
- [x] Colorblind mode toggle (orange/blue palette swap)
- [x] Directional arrows ▲▼ on all profit/loss
- [x] Shape prefixes on verdict pills (●◆◇✗)
- [x] Welcome banner for first-time users
- [x] Error banner when API fails
- [x] Loading skeleton placeholders
- [x] Tooltips on technical terms
- [x] Compact mobile header
- [x] Tab grouping with separators
- [x] Smooth tab transitions
- [x] Bot cog architecture (4 cogs + utils)
- [x] Interactive buttons/pagination in bot
- [x] Channel management (/setupchannels, /setchannel)
- [x] Manipulation detection system (5 signals)
- [x] Seasonal pattern analyzer
- [x] GE Slot Optimizer widget

### Still To Do
- [ ] Fix format_gp() in bot utils to always use full numbers
- [ ] Persist portfolio data to JSON like watchlists
- [ ] Fix silent DM alert failures (log + notify user)
- [ ] Add actionable empty states with next-step suggestions
- [ ] Improve error messages (casual tone, specific, actionable)
- [ ] Add alert preview before saving
- [ ] PubSub state management refactor for website
- [ ] Sparklines next to price values
- [ ] PWA with service worker (Phase 3)
