# Grand Flip Out Discord Bot - Quick Start Guide

## Installation (5 minutes)

1. **Install Python dependencies:**
```bash
pip install -r requirements.txt
```

2. **Set up environment variables:**
```bash
cp .env.example .env
# Edit .env and add your Discord bot token
```

3. **Get your Discord Bot Token:**
   - Go to https://discord.com/developers/applications
   - Click "New Application"
   - Name it "Grand Flip Out"
   - Go to "Bot" section and click "Add Bot"
   - Copy the token and paste it in `.env`
   - Enable "Message Content Intent" under "Privileged Gateway Intents"
   - Add OAuth2 scopes: `bot` and `applications.commands`
   - Add permissions: `Send Messages`, `Embed Links`, `Read Messages/View Channels`

4. **Invite bot to your server:**
   - In Developer Portal, go to OAuth2 → URL Generator
   - Select scopes: `bot`, `applications.commands`
   - Select permissions: `Send Messages`, `Embed Links`
   - Copy the generated URL and open it in browser to invite bot

5. **Start the bot:**
```bash
python bot.py
```

The bot will sync all slash commands automatically!

## Quick Test

Once running, in your Discord server:

1. Type `/` and you should see all bot commands
2. Try `/setup` to verify everything is working
3. Try `/price Ranarr Seed` to test API connectivity
4. Try `/top` to see top items by JTI score

## All 11 Commands

| Command | Purpose |
|---------|---------|
| `/price <item_name>` | Look up item price, margin, JTI |
| `/top [sort] [limit]` | Top items by jti/margin/volume |
| `/dumps` | Items crashing >5% in 5min |
| `/pumps` | Unusual buy pressure |
| `/recipe` | Profitable processing recipes |
| `/jti <item_name>` | Detailed JTI breakdown |
| `/market` | Overall market summary |
| `/compare <item1> <item2>` | Compare two items |
| `/watchlist [add\|remove\|show]` | Personal watchlist |
| `/sinks` | GP sinks (high sell volume) |
| `/setup` | Bot info & configuration |

## Configuration

### Optional: Automatic Dump Alerts

To get real-time dump notifications in a channel:

1. Get your channel ID (enable Developer Mode in Discord, right-click channel → Copy ID)
2. Add to `.env`:
```
DUMP_ALERT_CHANNEL_ID=1234567890123456789
```
3. Restart the bot

The bot will post alerts for items dropping >10% every minute.

### API Configuration

By default, bot connects to `http://localhost:3001`. To change:

```
API_URL=http://your-api-server:3001
```

## Troubleshooting

**"API request timed out"**
- Is the backend server running? Check `API_URL` in `.env`
- Try: `curl http://localhost:3001/api/status`

**Commands not showing up**
- Restart the bot
- Ensure bot has "applications.commands" scope
- Wait up to 1 hour for Discord to sync

**Item not found**
- Try a shorter name: "ranarr" instead of "Ranarr Seed"
- Use `/setup` to verify API is online

**Bot is offline**
- Check your Discord token in `.env` is correct and copied fully
- Check bot has required intents in Developer Portal

## Features Overview

### Fuzzy Item Matching
All item name parameters use fuzzy matching:
- "ranarr" → Ranarr Seed
- "rune" → Rune Essence
- "iron ore" → Iron Ore

### Item Icons
Item icons are pulled from OSRS Wiki and displayed in embeds for easy identification.

### GP Formatting
All GP values shown with commas: 1,234,567 gp

### Color Scheme
- Gold (#FF981F) - Main OSRS theme
- Green (#00FF00) - Pumps, positive signals
- Red (#FF0000) - Dumps, negative signals
- Blue (#0099FF) - Market data

## File Structure

```
discord-bot/
├── bot.py              # Main bot with all 11 commands
├── requirements.txt    # Python dependencies
├── .env.example        # Example environment config
├── .env                # Your actual config (create from .env.example)
├── watchlists.json     # Per-user watchlist storage
├── README.md           # Full documentation
└── QUICK_START.md      # This file
```

## Key Code Components

### ApiClient Class
Handles all communication with the backend API:
- Session management with aiohttp
- Item mapping with fuzzy matching
- Error handling for timeouts and API failures

### Embed Creation
`create_item_embed()` function formats item data into rich Discord embeds with:
- Item icons from OSRS Wiki
- Color-coded pricing data
- Organized field layout

### Background Task
`check_dumps()` runs every minute and:
- Fetches dump alerts from API
- Filters for severe drops (>10%)
- Posts to configured alert channel

### Watchlist Management
Per-user watchlists stored in JSON:
- Load on command
- Save after modifications
- User ID = Discord user ID

## Next Steps

1. Customize the bot (channel ID for alerts, API URL)
2. Invite crew to the Discord server
3. Set up alerts channel
4. Start flipping with real-time intelligence!

## Support

For issues:
1. Check the main `README.md` for detailed documentation
2. Verify API is running: `curl http://localhost:3001/api/status`
3. Check bot permissions in Discord server settings
4. Enable Debug logging if needed

Buying gf 10k!
