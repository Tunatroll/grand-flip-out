# Awfully Pure

**A comprehensive Grand Exchange flipping assistant for RuneLite.**

Awfully Pure helps OSRS players identify profitable flips with real-time market analysis, technical indicators, and intelligent scoring. Get instant alerts for price crashes, track your flip history, and optimize your GE trading.

## Features

**Pricing & Analysis**
- Multi-source consensus pricing from OSRS Wiki and RuneLite APIs
- JTI (Jagex Trade Index) composite scoring for flip quality assessment
- Technical indicators: RSI, EMA, MACD, Bollinger Bands on live GE data
- Market regime detection (trending, ranging, volatile)

**Dump Detection & Alerts**
- Real-time price crash detection with velocity analysis
- Recovery prediction and opportunity windows
- Manipulator pattern recognition
- Debounced severity-tiered alerts

**Trading Tools**
- SmartAdvisor: unified intelligence engine combining all signals into BUY/SELL recommendations
- Flip Tracker: personal P&L with session management and statistics
- Recipe Calculator: quick profitability checks for herb cleaning, gem cutting, cooking
- Risk Management: position sizing and portfolio monitoring

**Data Quality**
- All profit calculations include the 2% GE tax (capped at 5M GP)
- Realistic volume estimates, no fantasy margins
- Consistent 4-hour flip windows for comparison

## Installation

1. Install [RuneLite](https://runelite.net)
2. Open RuneLite and go to **Plugin Hub**
3. Search for **"Awfully Pure"** and click **Install**
4. The plugin sidebar will appear automatically in the RuneLite client

## Usage

Once installed, the Awfully Pure sidebar panel displays:
- **Top Flips** — Current highest-profit opportunities sorted by realistic 4-hour returns
- **Dumps** — Active price crashes worth sniping with severity indicators
- **Tracker** — Your personal flip history, P&L, and session statistics
- **Technical** — Live RSI, EMA, MACD, Bollinger Bands for any item
- **SmartAdvisor** — Unified BUY/SELL recommendations from all data sources
- **Bot Economy** — Identifies bot waves and market cycles

Customize thresholds for alerts, flip minimums, and technical indicators. All data updates in real-time from the OSRS Wiki prices API.

## Design Philosophy

- **Information only** — the plugin provides analysis and alerts, never automates trades or interacts with the game client
- **Jagex compliant** — uses only public APIs (OSRS Wiki, RuneLite) with proper User-Agent headers
- **No packet injection** — uses RuneLite's official Client API exclusively
- **Free and open source** — BSD 2-Clause License, fully auditable code

## Requirements

- RuneLite (latest stable)
- Java 11 runtime (already included in RuneLite)

## Development

To build the plugin from source:

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`.

For plugin development docs, see the [RuneLite wiki](https://github.com/runelite/runelite/wiki).

## Support

- **Bug reports & feature requests**: [GitHub Issues](https://github.com/tunatroll/grand-flip-out/issues)
- **RuneLite community**: [RuneLite Discord](https://runelite.net/discord)

## Technical Stack

- **Language**: Java 11
- **Build**: Gradle 7.6.4
- **Framework**: RuneLite Client API
- **Libraries**: Lombok, Gson
- **Data**: OSRS Wiki Real-Time Prices API

## License

BSD 2-Clause License

```
Copyright (c) 2026, Max
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

---

**Author**: tuna troll (Max)

*Not affiliated with Jagex, RuneLite, or the OSRS Wiki. Old School RuneScape is a trademark of Jagex Ltd.*
