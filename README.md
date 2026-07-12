# Grand Flip Out

**A comprehensive Grand Exchange flipping assistant for RuneLite.**

Grand Flip Out helps OSRS players identify profitable flips with real-time market analysis, technical indicators, and intelligent scoring. Get instant alerts for price crashes, track your flip history, and optimize your GE trading.

## Features

- **Multi-Source Pricing** — Consensus prices from OSRS Wiki and RuneLite APIs
- **JTI Scoring** — Jagex Trade Index composite rating for flip quality assessment
- **Technical Analysis** — RSI, EMA, MACD, Bollinger Bands on live GE data
- **Dump Detection** — Real-time price crash alerts with velocity analysis
- **SmartAdvisor** — Unified intelligence engine combining all signals into BUY/SELL recommendations
- **Flip Tracker** — Personal P&L with session management
- **Recipe Calculator** — Quick profitability checks for herb cleaning, gem cutting, cooking
- **Risk Management** — Position sizing and portfolio monitoring

All profit calculations include the **2% GE tax** (capped at 5M GP).

## Installation

1. Install [RuneLite](https://runelite.net)
2. Open RuneLite and go to **Plugin Hub**
3. Search for **"Grand Flip Out"** and click **Install**
4. The plugin sidebar will appear automatically

## Usage

Once installed, the plugin adds a sidebar panel in RuneLite showing:
- Current top flips by margin
- Active price crashes for sniping
- Your personal flip history and P&L
- Technical indicators and market regime
- SmartAdvisor buy/sell recommendations

## Compliance

- **Information only** — no automated trading or offer submission; the optional GE offer pre-fill (off by default) writes suggested values into open GE inputs and never places or confirms an offer
- **Jagex compliant** — uses public APIs (OSRS Wiki, RuneLite); the optional grandflipout.com features (off by default, consent-gated) send your IP, GE offers and approximate coins to grandflipout.com — see the in-client disclosure
- **No packet injection** — uses RuneLite's official Client API only
- **Open source** — BSD 2-Clause License

## Tech Stack

- Java 11
- RuneLite API
- Gradle 7.6.4
- Lombok
- Gson

## Support

For issues, feature requests, or questions:
- Check the [GitHub Issues](https://github.com/tunatroll/grand-flip-out/issues)
- Visit the [RuneLite Discord](https://runelite.net/discord)

## License

BSD 2-Clause License — See LICENSE file for details.

---

*Not affiliated with Jagex, RuneLite, or the OSRS Wiki. Old School RuneScape is a trademark of Jagex Ltd.*
