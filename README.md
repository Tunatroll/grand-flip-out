# Grand Flip Out

A comprehensive Grand Exchange flipping companion for Old School RuneScape.

**Information only — does not automate any trades or GE interactions.**

## Features

### SmartAdvisor Intelligence Engine
Combines multiple data sources into a single 0–100 composite score per item, with a clear BUY / HOLD / SELL / AVOID recommendation:
- JTI (Jagex Trade Index) scoring — measures true flippability based on volume, margin, and GE limit
- - RSI, EMA crossovers, MACD, and Bollinger Band analysis via the Wiki timeseries API
  - - Mean reversion detection (Z-score based)
    - - Market regime classification: Trending Up, Ranging, Volatile, Dead
      - - Liquidity classification: Illiquid → Highly Liquid
        - - Data quality and manipulation risk scoring
         
          - ### Dump Detection & Knowledge Engine
          - - Detects sudden price crashes (configurable threshold, default 5%)
            - - Classifies dumps by type: coordinated dump, panic sell, supply flood, or bot ban wave
              - - Recommends target buy price and expected recovery price
                - - Configurable watchlist-only mode to reduce noise
                 
                  - ### Bot Economy Tracker
                  - - Tracks bot-farmed item supply pipeline (F2P → P2P pipeline stages)
                    - - Detects ban wave signals and supply shock events
                      - - Identifies items currently in supply shock or supply flood
                        - - Overall market health score
                         
                          - ### Flip Tracker
                          - - Auto-detects completed GE transactions via RuneLite's event API
                            - - Accounts for the 2% GE tax (capped at 5M)
                              - - Tracks active flips, completed flips, and profit/loss per session
                                - - Persistent history across sessions (configurable limit)
                                 
                                  - ### Flip Suggestion Engine
                                  - - Filters items by minimum margin, minimum volume, and GE limit
                                    - - Sorts by: highest margin, margin %, volume, profit per GE limit, or ROI
                                      - - Configurable number of suggestions
                                       
                                        - ### Multi-Account Portfolio Tracking
                                        - - Track GE limits, cash stacks, and positions across up to 10 accounts
                                          - - Combined buying power display
                                            - - Rebalance alerts when over-concentrated in a single item
                                              - - **Information only** — all trades are placed manually by the player
                                               
                                                - ### In-Game Overlay
                                                - - Displays current margin, volume, and SmartAdvisor score when the GE is open
                                                  - - Toggleable session profit/loss overlay
                                                    - - Configurable via hotkeys
                                                     
                                                      - ### Hotkeys
                                                      - | Hotkey | Action |
                                                      - |--------|--------|
                                                      - | Ctrl+Shift+F | Toggle panel |
                                                      - | Ctrl+Shift+R | Force refresh prices |
                                                      - | Ctrl+Shift+O | Toggle overlay |
                                                      - | Ctrl+Shift+C | Copy margin to clipboard |
                                                      - | Ctrl+Shift+D | Scan for dumps |
                                                      - | Ctrl+Shift+Tab | Cycle accounts (multi-account mode) |
                                                     
                                                      - ## Data Sources
                                                      - - **OSRS Wiki Prices API** — real-time prices and timeseries (recommended)
                                                        - - **RuneLite Prices API** — supplementary pricing
                                                          - - **Official Jagex GE API** — optional, may be delayed
                                                           
                                                            - Price refresh interval is configurable (60–600 seconds). The plugin respects API rate limits.
                                                           
                                                            - ## Jagex & RuneLite Compliance
                                                           
                                                            - This plugin is fully compliant with Jagex's third-party client guidelines and RuneLite Plugin Hub requirements:
                                                           
                                                            - - Does **not** automate any Grand Exchange interactions
                                                              - - Does **not** click, type, or interact with the game client in any way
                                                                - - Only reads data through RuneLite's official event API (`GrandExchangeOfferChanged`)
                                                                  - - All external HTTP requests use RuneLite's injected `OkHttpClient`
                                                                    - - No game packets are sent or intercepted
                                                                      - - No memory is read beyond the RuneLite Client API
                                                                        - - Multi-account tracking is for independent portfolio monitoring only — coordinated market manipulation across accounts is against Jagex ToS and is not facilitated by this plugin
                                                                         
                                                                          - ## Installation (Plugin Hub)
                                                                         
                                                                          - 1. Open RuneLite and go to the Plugin Hub (plug icon in the sidebar)
                                                                            2. 2. Search for **Grand Flip Out**
                                                                               3. 3. Click Install
                                                                                 
                                                                                  4. ## License
                                                                                 
                                                                                  5. BSD 2-Clause License — see [LICENSE](LICENSE)
