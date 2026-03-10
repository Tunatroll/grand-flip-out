# Awfully Pure

**OSRS Grand Exchange Flipping Ecosystem** — RuneLite Plugin + Live Dashboard + Discord Bot

A comprehensive toolkit for Old School RuneScape Grand Exchange flipping. Get real-time margin analysis, dump detection, technical indicators, and actionable alerts — all powered by the OSRS Wiki Real-Time Prices API.

## Components

### RuneLite Plugin (Java)
Sidebar panel inside RuneLite with:
- **Multi-source pricing** — consensus prices from OSRS Wiki, RuneLite, and Official GE APIs
- **JTI Scoring** — Jagex Trade Index composite rating for flip quality
- **Technical Analysis** — RSI, EMA, MACD, Bollinger Bands on GE data
- **Dump Detection** — real-time crash alerts with velocity analysis and recovery prediction
- **SmartAdvisor** — unified intelligence engine combining all signals into BUY/SELL recommendations
- **Bot Economy Tracker** — identifies bot waves and market cycles
- **Flip Tracker** — personal P&L with session management
- **Recipe Calculator** — herb cleaning, gem cutting, cooking profitability
- **Risk Management** — position sizing and portfolio monitoring
- All profits include the **2% GE tax** (capped at 5M GP)

### Website (GitHub Pages)
Live dashboard at [tunatroll.github.io/awfully-pure](https://tunatroll.github.io/awfully-pure):
- Top flips sorted by realistic 4-hour profit
- Dump Sniper with scoring engine and market regime detection
- TradingView-style candlestick charts
- Watchlist, recipes, special items
- Real-time WebSocket updates

### Discord Bot (Python)
- `/price <item>` — instant GE price check with margin analysis
- `/flip` — top 5 flip opportunities right now
- `/dump` — active price crashes worth sniping
- `/alert add <item>` — personal alerts with configurable thresholds (margin %, min profit, volume)
- `/watchlist` — track your favorite items
- Auto-alerts in server channels with severity tiers and debouncing

### Backend Server (Node.js)
- REST API + WebSocket for real-time data
- 15-second price polling from OSRS Wiki
- JTI calculation, recipe profits, item categorization
- Docker-ready deployment

## Quick Start

### Plugin
1. Install [RuneLite](https://runelite.net)
2. Search "Awfully Pure" in the Plugin Hub
3. Enable the plugin — sidebar panel appears automatically

### Website + Server
```bash
cd server && npm install && npm start    # API on :3001
# Open website/index.html or visit tunatroll.github.io/awfully-pure
```

### Discord Bot
```bash
cd discord-bot
cp .env.example .env    # Add your DISCORD_TOKEN
pip install discord.py aiohttp python-dotenv
python bot.py
```

### Docker (all services)
```bash
docker-compose up -d
```

## Tech Stack

| Component | Stack |
|-----------|-------|
| Plugin | Java 11, RuneLite API, Gradle 7.6.4, Lombok, Gson |
| Website | Vanilla HTML/CSS/JS, Chart.js, TradingView Lightweight Charts |
| Discord Bot | Python 3.8+, discord.py 2.3.2, aiohttp |
| Server | Node.js, Express, WebSocket (ws) |
| Data | OSRS Wiki Real-Time Prices API |

## Compliance

- **Information only** — no trade automation, no game client interaction
- **Jagex compliant** — all data from public APIs (OSRS Wiki, RuneLite)
- **No packet injection** — uses only RuneLite's official Client API
- **BSD 2-Clause License**

## Author

**tuna troll** (Max)

---

*Not affiliated with Jagex, RuneLite, or the OSRS Wiki. Old School RuneScape is a trademark of Jagex Ltd.*
