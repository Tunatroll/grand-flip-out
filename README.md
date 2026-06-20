# Grand Flip Out

**A Grand Exchange flipping assistant for RuneLite.**

Grand Flip Out helps OSRS players identify profitable flips and track their own GE
performance. Live prices come from the OSRS Wiki Real-Time Prices API; all profit
math accounts for the 2% GE tax (capped at 5M GP); all flip history is stored
locally on your machine.

**You stay in control.** The plugin reads completed offers through RuneLite's event
API and shows you analysis. With the optional GE offer auto-fill enabled (off by
default), it fills the GE offer/search input on your command — you confirm every
offer. It never submits, cancels, or collects an offer for you, never sends chat,
and never clicks on your behalf.

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
- **Flipping Utilities import** — one-time import of existing flip history
- **Keyboard hotkeys** — toggle panel, refresh prices, quick lookup, copy margin/prices,
  toggle overlay, optional GE price-fill assist
- **Optional account** — free account (created on the web) unlocks members-item
  suggestions and premium server features; see *Compliance and data handling* below

## Installation

1. Install [RuneLite](https://runelite.net)
2. Open RuneLite and go to **Plugin Hub**
3. Search for **"Grand Flip Out"** and click **Install**
4. The Grand Flip Out icon appears in the RuneLite sidebar

## Compliance and data handling

- **You confirm every offer** — the plugin never automates, submits, cancels, or
  collects trades. With GE offer auto-fill enabled (off by default) it writes the
  suggested price/quantity into the offer input and pre-fills the item search; you
  review the value and press Confirm yourself. No synthetic clicks or keystrokes, no
  game-packet injection, no chatbox autotyping.
- **Default network is Wiki-only** — out of the box the plugin talks only to
  `prices.runescape.wiki` (plus user-triggered links to public web pages, opened via
  RuneLite's `LinkBrowser`). All `grandflipout.com` communication is **opt-in**:
  - *API key* (empty by default): if you paste an account key, it is sent as a `Bearer`
    token to `grandflipout.com/api/entitlements` **only** to check whether your account
    unlocks members-item suggestions / premium features. No key set → this call is never made.
  - *Advisor* (off by default): to suggest your next flip, your approximate coin total
    and your currently-active GE offers (item, price, quantity, side, slot — a
    `GameStateSnapshot`) are POSTed to `grandflipout.com`.
  - *Server Advisor* (off by default): read-only fetch of signals for the active item.
  - *Contribute trades* (off by default): shares your completed trades (item ID, price,
    quantity, side, timestamp) for crowd-sourced pricing. No account identifiers are
    included in any payload, but — as the in-config warning states — your IP address is
    visible to the server, which is a 3rd-party site not controlled or verified by the
    RuneLite Developers.
- **No player data is sent off-device unless you opt in** — flip history is stored
  locally under `~/.runelite/grand-flip-out/`. No character names or passwords are ever
  sent; the only credential transmitted is the API key you choose to paste.
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
