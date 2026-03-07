# Grand Flip Out Discord Bot - Complete Feature List

## Implementation Checklist

### Core Commands (11 Total)

- [x] `/price <item_name>` - Look up current price, margin, JTI score for an item
  - [x] Fuzzy search by name
  - [x] Shows buy price, sell price, current price
  - [x] Shows profit margin
  - [x] Shows JTI score
  - [x] Item icon from OSRS Wiki

- [x] `/top [sort=jti|margin|volume] [limit=5]` - Show top items
  - [x] Sort by JTI (default)
  - [x] Sort by margin
  - [x] Sort by volume
  - [x] Configurable limit (1-20)
  - [x] Rich embed formatting

- [x] `/dumps` - Show current dump alerts (items dropping >5% in 5min)
  - [x] Fetch from API
  - [x] Display price drop percentage
  - [x] Show current prices
  - [x] Red color theme
  - [x] Sorted by severity

- [x] `/pumps` - Show items with unusual buy pressure
  - [x] High buy volume relative to sell
  - [x] Sorted by buy volume
  - [x] Green color theme
  - [x] Volume comparisons

- [x] `/recipe` - Show profitable processing recipes
  - [x] Herb cleaning recipes
  - [x] Gem cutting recipes
  - [x] Shows input/output items
  - [x] Displays profit per item
  - [x] Up to 10 recipes

- [x] `/jti <item_name>` - Detailed JTI breakdown for an item
  - [x] Fuzzy item search
  - [x] Shows JTI score
  - [x] Shows profit margin
  - [x] Shows buy/sell volumes
  - [x] Recent price history
  - [x] Item icon

- [x] `/market` - Overall market summary
  - [x] Total daily volume
  - [x] Average margins
  - [x] Active items count
  - [x] Highest margin items
  - [x] Market statistics

- [x] `/compare <item1> <item2>` - Side-by-side comparison
  - [x] Fuzzy search for both items
  - [x] Side-by-side pricing
  - [x] Margin comparison
  - [x] JTI comparison
  - [x] Volume comparison

- [x] `/watchlist [add|remove|show] [item_name]` - Per-user watchlist
  - [x] Add items to watchlist
  - [x] Remove items from watchlist
  - [x] Show personal watchlist
  - [x] JSON file storage
  - [x] Per-user tracking (by Discord ID)
  - [x] Display margins for watched items

- [x] `/sinks` - Show items with high sell volume
  - [x] Items with high sell relative to buy
  - [x] Red color theme (GP leaving)
  - [x] Shows buy/sell volumes
  - [x] Sorted by sell volume

- [x] `/setup` - Show bot info and configuration
  - [x] API status check
  - [x] Display API URL
  - [x] List all commands
  - [x] Usage tips
  - [x] OSRS-themed footer

### Backend Integration

- [x] Async HTTP client using aiohttp
- [x] Configurable API URL
- [x] API error handling
- [x] Timeout handling (10 second timeout)
- [x] All 8 API endpoints used:
  - [x] GET /api/items?sort=jti&limit=20
  - [x] GET /api/items/:id
  - [x] GET /api/items/:id/history
  - [x] GET /api/dumps
  - [x] GET /api/top?sort=metric&limit=N
  - [x] GET /api/recipes
  - [x] GET /api/market
  - [x] GET /api/status

### Item Matching

- [x] Fuzzy matching using SequenceMatcher
- [x] Item mapping loaded at startup
- [x] 60% similarity threshold
- [x] Exact name match priority
- [x] Case-insensitive matching
- [x] Handles special characters

### Embed Formatting

- [x] OSRS gold color (#FF981F) for main embeds
- [x] OSRS green (#00FF00) for positive signals
- [x] OSRS red (#FF0000) for negative signals
- [x] OSRS blue (#0099FF) for data
- [x] Item icons from OSRS Wiki
- [x] Proper field organization
- [x] Timestamps on all embeds
- [x] Footer with bot branding

### GP Formatting

- [x] Commas for thousand separators
- [x] "gp" suffix (not "gold")
- [x] N/A for missing values
- [x] Consistent formatting across all commands

### Background Tasks

- [x] Dump monitoring every 60 seconds
- [x] Filters for severe dumps (>10% drop)
- [x] Posts to configurable alert channel
- [x] Rich embed alerts
- [x] Waits for bot readiness before starting

### Watchlist System

- [x] JSON file storage (`watchlists.json`)
- [x] Per-user watchlists (by Discord user ID)
- [x] Add command with fuzzy search
- [x] Remove command with fuzzy search
- [x] Show command displays margins
- [x] Persistence across bot restarts
- [x] Proper error handling

### Error Handling

- [x] API timeouts (10 second limit)
- [x] API errors (non-200 status codes)
- [x] Connection errors
- [x] Item not found errors
- [x] Missing parameters
- [x] Invalid sort parameters
- [x] Invalid limit values
- [x] Graceful failure messages

### Environment Configuration

- [x] .env file support using python-dotenv
- [x] DISCORD_TOKEN configuration
- [x] API_URL configuration
- [x] Optional DUMP_ALERT_CHANNEL_ID
- [x] .env.example template
- [x] Default values where appropriate

### Files

- [x] bot.py (26KB) - Main bot implementation
- [x] requirements.txt - Dependencies (discord.py, aiohttp, python-dotenv)
- [x] .env.example - Configuration template
- [x] watchlists.json - Watchlist storage (empty dict by default)
- [x] README.md - Full documentation (6.6KB)
- [x] QUICK_START.md - Quick setup guide (4.9KB)
- [x] setup.sh - Setup helper script
- [x] FEATURES.md - This file

### OSRS Theming

- [x] "gp" instead of "gold"
- [x] "Buying gf 10k" easter egg in footer
- [x] JTI (Jump The Queue Index) terminology
- [x] Dump/pump terminology
- [x] GP sink references
- [x] RuneScape slang in messages
- [x] OSRS color scheme
- [x] OSRS Wiki image integration

### Code Quality

- [x] Type hints throughout
- [x] Docstrings for all functions
- [x] Consistent formatting
- [x] Error handling on all endpoints
- [x] Async/await patterns
- [x] Session management
- [x] Resource cleanup
- [x] No hardcoded values (except for OSRS colors)
- [x] Syntax validated (py_compile)

## Testing Checklist

### Before Deployment

- [ ] Python 3.8+ installed
- [ ] All dependencies installed: `pip install -r requirements.txt`
- [ ] .env file created with valid DISCORD_TOKEN
- [ ] Discord bot token generated from Developer Portal
- [ ] Bot invited to test server with proper scopes and permissions
- [ ] Backend API running at configured URL
- [ ] API endpoints responding: curl http://localhost:3001/api/status

### Command Testing

- [ ] /price - Returns item data
- [ ] /top - Returns top items
- [ ] /dumps - Returns dump alerts
- [ ] /pumps - Returns pump alerts
- [ ] /recipe - Returns recipes
- [ ] /jti - Returns JTI breakdown
- [ ] /market - Returns market stats
- [ ] /compare - Compares two items
- [ ] /watchlist add - Adds item
- [ ] /watchlist show - Shows watchlist
- [ ] /watchlist remove - Removes item
- [ ] /sinks - Shows GP sinks
- [ ] /setup - Shows bot info

### Feature Testing

- [ ] Fuzzy matching works (e.g., "ranarr" finds "Ranarr Seed")
- [ ] Item icons load properly
- [ ] GP values format with commas
- [ ] Error messages display for missing items
- [ ] API timeout handling works
- [ ] Dump alerts post to channel (if configured)
- [ ] Watchlists persist across restarts
- [ ] Colors display correctly in embeds
- [ ] Timestamps show correctly

## Known Limitations

1. Fuzzy matching requires 60%+ similarity - very short queries may not work
2. Item icons pull from OSRS Wiki - may fail if wiki is down
3. API timeouts at 10 seconds - slow API could cause delays
4. No database - watchlists stored in JSON file (not scalable for many users)
5. Background dump task runs every minute - not real-time
6. No command cooldowns - users can spam commands

## Future Enhancement Ideas

1. Database backend for watchlists (SQLite, PostgreSQL)
2. User-configurable dump alert thresholds
3. Price alerts when items hit specific thresholds
4. Profit tracking and statistics per user
5. Item history graphs/charts
6. Integration with OSRS Wiki API for more info
7. Scheduled daily/weekly reports
8. Role-based command permissions
9. Leaderboards for profitability
10. Price predictions using historical data

## Performance Notes

- Item mapping loaded once at startup (cache)
- API requests cached per command (no query deduplication)
- 10-second API timeout prevents hanging
- Async operations prevent blocking
- Background dump check runs independently
- JSON watchlists loaded/saved on each edit (could be optimized)

## Dependencies

- discord.py 2.3.2 - Discord API library
- aiohttp 3.9.1 - Async HTTP client
- python-dotenv 1.0.0 - Environment variable management

All dependencies are lightweight and well-maintained.
