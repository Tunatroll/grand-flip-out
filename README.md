# Grand Flip Out

**A Grand Exchange flipping assistant for RuneLite.**

Grand Flip Out helps OSRS players identify profitable flips and track their own GE
performance. Live prices come from the OSRS Wiki Real-Time Prices API; all profit
math accounts for the 2% GE tax (capped at 5M GP); all flip history is stored
locally on your machine.

**Information only.** The plugin reads completed offers through RuneLite's event API
and shows you analysis. It does not automate trades or send synthetic input. The
optional GE price/quantity pre-fill writes text into the offer field you have open
(the same mechanism as Flipping Copilot) — you review it and press Confirm yourself.

## Features

- **Live Wiki pricing** — refreshes from `prices.runescape.wiki` on a configurable
  interval (default 60s, minimum 60s per Wiki etiquette)
- **Ranked flip suggestions** — items ranked by realistic 4-hour profit
  (profit-per-GE-limit), absolute margin, or margin %, volume-floored so only
  flippable items appear; sortable, category-filterable
- **Search** — find any item by name; see its current margin, volume, and timeline
  estimate (free for every item)
- **Flip tracker** — automatically pairs your buy/sell offers into completed flips,
  computes per-flip P&L with the GE tax baked in, persists history between sessions,
  tracked **per in-game account**
- **Session stats panel** — running profit, ROI %, flip count, tax paid, GP/hr, and
  session time, over a selectable interval
- **In-game GE overlay** — margin / volume / profit, per-slot buy/sell targets, slot
  timers, and remaining 4-hour buy limit while the Grand Exchange is open
- **Inventory price tooltips** — hover values for items in your inventory
- **Margin check helper** — quick "buy 1 at X, sell 1 at Y" workflow
- **GP-drop animation** on profitable sells
- **Keyboard hotkeys** — toggle panel, refresh prices, quick lookup, copy margin & slot assist,
  toggle overlay, optional GE buy/sell price-fill (off by default)
- **Optional account** — free account (created on the web) unlocks members-item
  suggestions and premium server features; see *Compliance and data handling* below

## Installation

1. Install [RuneLite](https://runelite.net)
2. Open RuneLite and go to **Plugin Hub**
3. Search for **"Grand Flip Out"** and click **Install**
4. The Grand Flip Out icon appears in the RuneLite sidebar

## Compliance and data handling

- **Information only** — never automates trades and never injects synthetic
  clicks/keystrokes or game packets. The optional GE price/quantity pre-fill writes
  text into the offer input you have open; you confirm every offer yourself
- **Default network is Wiki-only** — out of the box the plugin talks only to
  `prices.runescape.wiki` (plus user-triggered `LinkBrowser` links to public web
  pages). All `grandflipout.com` communication is **opt-in**:
  - *API key* (empty by default): if you paste an account key, it is sent as a `Bearer`
    token to `grandflipout.com/api/entitlements` **only** to check whether your account
    unlocks members-item suggestions / premium features. No key set → this call is never made.
  - *Advisor / Server Advisor* (off by default): to generate suggestions it sends your
    current GE offers, approximate coin total, and the IDs of items you've skipped/blocked (no character name) to grandflipout.com
    and returns ranked flips/signals for the active item.
  - *Contribute trades* (off by default): sends anonymized completed trades (item ID,
    price, quantity, side, timestamp, plugin version) for crowd-sourced pricing.
- **No player data is sent off-device by default** — flip history is stored locally under
  `~/.runelite/grand-flip-out/`. No character names or passwords are ever sent; the only
  credential transmitted is the API key you choose to paste.
- **No reflection, no `Runtime.exec`, no inbound sockets, no game-packet injection**
- **Uses RuneLite's injected `OkHttpClient`** with a proper User-Agent
- **All file I/O is sandboxed** to `RUNELITE_DIR/grand-flip-out/`
- **No in-client payment** — any account/premium gating is server-side; the plugin never
  takes in-game gold or in-client real-money payment (Jagex rule)

## Requirements

- RuneLite (latest stable)
- Java 11 runtime (already included in RuneLite)

## Development

```bash
./gradlew build       # build the plugin jar
./gradlew test        # run the test suite
```

The compiled JAR will be in `build/libs/`.

## Support

- **Bug reports & feature requests:** [GitHub Issues](https://github.com/Tunatroll/grand-flip-out/issues)
- **RuneLite community:** [RuneLite Discord](https://runelite.net/discord)

## License

BSD 2-Clause License — see [LICENSE](LICENSE).

---

**Author:** tuna troll (Max) · *Not affiliated with Jagex, RuneLite, or the OSRS
Wiki. Old School RuneScape is a trademark of Jagex Ltd.*
