# Manual QA Test Matrix (Grand Flip Out)

Use this checklist for a quick manual pass before releasing or submitting to the RuneLite Plugin Hub. Run tests on a clean profile when possible.

## Environment

- [ ] RuneLite dev: `./gradlew run` (or installed client with plugin JAR)
- [ ] Plugin appears in the plugin list and can be enabled/disabled
- [ ] No errors in the RuneLite log on startup with plugin enabled

## Panel and navigation

- [ ] Grand Flip Out icon appears in the sidebar; clicking it opens the panel
- [ ] Three tabs visible: Overview, Live Market, Trade History
- [ ] Switching tabs updates content; no blank or broken layout
- [ ] Hotkey “Cycle tabs” (if configured) cycles through tabs
- [ ] Hotkey “New session” (if configured) resets session and shows Trade History tab
- [ ] Hotkey “Refresh panel” (if configured) refreshes visible data

## Live Market (API-dependent)

- [ ] With API disabled in config: panel shows “API polling disabled” / “API status: disabled”
- [ ] With API enabled and valid URL: “Last update” and “API status: connected” appear after a poll
- [ ] With API enabled and invalid URL or network error: “API status” shows an error message (no silent failure)
- [ ] “Powered by grandflipout.com” and “Top opportunities (from API)” are visible when relevant
- [ ] If API returns items: market list shows buy/sell/margin; if API returns opportunities, they appear in order (confidence/margin)
- [ ] Tooltips on opportunity rows show reason and confidence explanation

## Trade History (local)

- [ ] Session summary shows “Session profit”, “Buys / Sells”, “Items traded”, “All-time profit”
- [ ] Open positions: empty state or list of held items with quantity and avg cost
- [ ] Profit by item: empty state or list with ROI/margin
- [ ] Recent trades: empty state or list of recent buy/sell rows
- [ ] Flip logs: empty state or list of log entries with timestamp and side

## GE integration

- [ ] With tracking enabled: perform a buy or sell at the GE (offer completes)
- [ ] After trade: Trade History tab shows the new trade in Recent trades and Session summary updates
- [ ] Flip logs show the new entry
- [ ] Open positions update for buys; completed flips appear in Profit by item when sold

## Data portability

- [ ] Copy Trades JSON / Paste Trades JSON: paste restores trade list (or merge) and UI updates
- [ ] Copy Logs JSON / Paste Logs JSON: same for flip logs
- [ ] Copy Trades CSV / Copy Logs CSV: clipboard contains valid CSV
- [ ] Export trades to file: file chooser opens; saving writes JSON; file is valid
- [ ] Import trades from file: file chooser opens; loading valid JSON updates trade history
- [ ] Export/Import logs to/from file: same for flip logs
- [ ] Clear Trades / Clear Logs: data clears and UI shows empty states

## Persistence

- [ ] With “Persist trade history” and “Persist flip logs” on: do some trades, close RuneLite, reopen
- [ ] Trade history and flip logs are still present after restart
- [ ] Session resets on new session hotkey; all-time profit and history remain until cleared

## Alerts (if enabled)

- [ ] With opportunity alerts on and API returning opportunities above threshold: a desktop notification appears (subject to cooldown)
- [ ] Cooldown: second similar alert does not fire within the configured minutes

## Config and compliance

- [ ] All config sections (API, Tracking, Logs, Hotkeys, Alerts) open and options are changeable
- [ ] API key field (Grand Flip Out API config) is masked
- [ ] No automation: plugin only displays data and records GE events; no input simulation or bot-like behavior

## Submission readiness (final pass)

- [ ] Replace placeholder author/team in `runelite-plugin.properties` if needed
- [ ] README and docs describe external API dependency and setup
- [ ] docs/ORIGINALITY_AND_ATTRIBUTION.md present; README or checklist references it (code origin for reviewers)
- [ ] Backend (grandflipout.com) is stable and reachable for public use (if applicable)
