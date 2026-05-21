# Grand Flip Out

**A Grand Exchange flipping assistant for RuneLite.**

Grand Flip Out helps OSRS players identify profitable flips and track their own GE
performance. Live prices come from the OSRS Wiki Real-Time Prices API; all profit
math accounts for the 2% GE tax (capped at 5M GP); all flip history is stored
locally on your machine.

**Information only.** The plugin reads completed offers through RuneLite's event API
and shows you analysis. It does not automate trades or interact with the game
client.

## v1.0.0 — initial Plugin Hub release

This is an intentionally small initial release. Follow-up PRs will add features
back individually for review.

**Included in v1.0.0:**

- **Live Wiki pricing** — refreshes from `prices.runescape.wiki` on a configurable
  interval (default 60s, minimum 60s per Wiki etiquette)
- **Top-flips panel** — items ranked by realistic 4-hour profit (profit-per-GE-limit),
  filtered by minimum trade volume
- **Search** — find any item by name; see its current margin, volume, and timeline
  estimate
- **Flip tracker** — automatically pairs your buy/sell offers into completed flips,
  computes per-flip P&L with the GE tax baked in, persists history between sessions
- **Session stats** — running profit, flip count, average per flip, GP/hr
- **Profit chart** — visual session-profit timeline
- **In-game GE overlay** — margin / volume / profit info while the Grand Exchange
  is open
- **Margin check helper** — quick "buy 1 at X, sell 1 at Y" workflow
- **GP-drop animation** on profitable sells
- **Five keyboard hotkeys** — toggle panel, refresh prices, quick lookup, copy margin,
  toggle overlay

**Planned for follow-up PRs** (kept on a development branch and submitted in smaller
chunks per Plugin Hub maintainer guidance):

- Flip-suggestion engine
- Dump-detection alerts
- Technical indicators (RSI / EMA / MACD / Bollinger)
- Recovery prediction
- Multi-account aggregation

## Installation

1. Install [RuneLite](https://runelite.net)
2. Open RuneLite and go to **Plugin Hub**
3. Search for **"Grand Flip Out"** and click **Install**
4. The Grand Flip Out icon appears in the RuneLite sidebar

## Compliance and data handling

- **No outbound network besides** `prices.runescape.wiki` for price data and an
  optional `Desktop.browse()` link to a public chart page (user-triggered only)
- **No player data is sent off-device** — flip history is stored locally under
  `~/.runelite/grand-flip-out/`
- **No reflection, no `Runtime.exec`, no inbound sockets, no game-packet
  injection**
- **Uses RuneLite's injected `OkHttpClient`** with a proper User-Agent
- **All file I/O is sandboxed** to `RUNELITE_DIR/grand-flip-out/`

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
