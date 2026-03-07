# Grand Flip Out - OSRS Discord Bot

A Discord bot for the Grand Flip Out OSRS flipping crew, providing real-time Grand Exchange flipping intelligence and profit opportunities.

## Features

- **Real-time Item Prices**: Look up current prices, margins, and JTI scores
- **Market Intelligence**: View top items by profitability, volume, or JTI score
- **Dump Alerts**: Get notified when items are crashing (>5% in 5 minutes)
- **Pump Detection**: Spot unusual buy pressure on items
- **Recipe Analysis**: Find profitable processing recipes (herb cleaning, gem cutting, etc.)
- **Market Summary**: See overall market statistics and trends
- **Item Comparison**: Compare two items side-by-side
- **Personal Watchlists**: Track your favorite flipping items
- **GP Sinks**: Monitor items with high sell volume (GP leaving economy)
- **Fuzzy Matching**: Find items even with partial names

## Setup

### Prerequisites

- Python 3.8+
- Discord Bot Token (from [Discord Developer Portal](https://discord.com/developers/applications))
- Backend API server running at `http://localhost:3001` (or configured URL)

### Installation

1. Clone or navigate to the bot directory:
```bash
cd discord-bot
```

2. Install dependencies:
```bash
pip install -r requirements.txt
```

3. Copy the example environment file and configure:
```bash
cp .env.example .env
```

4. Edit `.env` and add your Discord bot token:
```
DISCORD_TOKEN=your_bot_token_here
API_URL=http://localhost:3001
DUMP_ALERT_CHANNEL_ID=1234567890  # Optional: channel ID for automatic dump alerts
```

### Running the Bot

```bash
python bot.py
```

The bot will:
- Connect to Discord
- Sync all slash commands
- Load item data from the backend API
- Start monitoring for dumps every minute (if alert channel is configured)

## Commands

### `/price <item_name>`
Look up current price, margin, and JTI score for an item.
- Uses fuzzy matching - try "ranarr" and it'll find "Ranarr Seed"
- Shows buy price, sell price, profit margin, and JTI score

### `/top [sort=jti|margin|volume] [limit=5]`
Show top items by specified metric.
- `sort`: jti (default), margin, or volume
- `limit`: 1-20 items (default 5)

### `/dumps`
Show current dump alerts - items dropping >5% in 5 minutes.
- Updated in real-time
- Perfect for spotting crash opportunities

### `/pumps`
Show items with unusual buy pressure.
- Opposite of dumps - items being bought aggressively
- Identifies potential price increases

### `/recipe`
Show profitable processing recipes.
- Herb cleaning, gem cutting, etc.
- Shows profit per item processed

### `/jti <item_name>`
Detailed JTI (Jump The Queue Index) breakdown.
- Shows profitability scoring components
- Includes recent price history
- JTI = Jump The Queue Index (higher is better)

### `/market`
Overall market summary and statistics.
- Total daily volume
- Average margins
- Active items
- Highest margin items

### `/compare <item1> <item2>`
Side-by-side comparison of two items.
- Shows all key metrics for both items
- Easy price, margin, and volume comparison

### `/watchlist [add|remove|show] [item_name]`
Manage your personal watchlist.
- `/watchlist show` - View your watched items
- `/watchlist add <item_name>` - Add an item to track
- `/watchlist remove <item_name>` - Remove an item
- Watchlists are per-user and stored locally

### `/sinks`
Show items with high sell volume (GP sinks).
- Items where GP is leaving the economy
- High sell volume indicates demand for GP removal

### `/setup`
Display bot information and configuration.
- Shows API status
- Lists all available commands
- Provides usage tips

## Configuration

### Environment Variables

- `DISCORD_TOKEN` (required): Your Discord bot token
- `API_URL` (optional): Backend API URL (default: http://localhost:3001)
- `DUMP_ALERT_CHANNEL_ID` (optional): Channel ID for automatic dump alerts

### Watchlist Storage

Watchlists are stored in `watchlists.json` in the bot directory:
```json
{
  "user_id": ["Item Name 1", "Item Name 2"],
  "another_user_id": ["Item Name 3"]
}
```

## Features in Detail

### Fuzzy Item Matching
The bot uses fuzzy matching to find items by name. For example:
- "ranarr" → finds "Ranarr Seed"
- "rune" → finds "Rune Essence"
- "iron" → finds "Iron Ore"

The matcher requires a 60%+ similarity to the stored item name.

### Dump Alerts
A background task runs every minute checking for severe dumps (>10% price drop). If enabled, the bot will post alerts to the configured channel.

### Item Icons
Item icons are pulled from the OSRS Wiki API and displayed in all embeds, making it easy to identify items visually.

## API Endpoints Used

The bot communicates with the backend server using these endpoints:

- `GET /api/items?sort=jti&limit=20` - Top items by JTI score
- `GET /api/items/:id` - Single item detail
- `GET /api/items/:id/history` - Price history
- `GET /api/dumps` - Current dump alerts
- `GET /api/top?sort=margin|volume|jti&limit=10` - Top items by metric
- `GET /api/recipes` - Profitable recipes
- `GET /api/market` - Market summary stats
- `GET /api/status` - Server health check

## Troubleshooting

### "Bot is offline"
- Check your Discord token in `.env`
- Verify the bot has proper intents in Developer Portal
- Ensure the bot is invited to your server with slash command permissions

### "Couldn't find API"
- Verify `API_URL` in `.env` is correct
- Ensure the backend server is running
- Check network connectivity to the API server

### Item not found
- The bot uses fuzzy matching - try a different name variation
- Use `/setup` to verify API is connected
- Check if the item exists in the backend database

### Commands not showing up
- Use `/setup` to force command sync (or restart the bot)
- Ensure bot has "applications.commands" scope in Discord
- Wait up to 1 hour for Discord to cache the new commands

## OSRS Slang & Easter Eggs

The bot uses RuneScape slang:
- "gp" not "gold"
- "Buying gf 10k" easter egg in footer
- Color scheme uses OSRS gold (#FF981F)
- References to JTI, dumping, pumping - familiar to flippers

## Development

### Adding New Commands

Commands are defined using `@bot.tree.command()` decorator:

```python
@bot.tree.command(name="mycommand", description="Do something")
@app_commands.describe(param="Parameter description")
async def my_command(interaction: discord.Interaction, param: str):
    await interaction.response.defer()
    # Your code here
    await interaction.followup.send(embed=embed)
```

### Error Handling

All commands include error handling for:
- API failures
- Missing items
- Timeout errors
- Invalid parameters

## License

This bot is part of the Grand Flip Out OSRS flipping project.

## Support

For issues or feature requests, contact the Grand Flip Out crew on Discord.
