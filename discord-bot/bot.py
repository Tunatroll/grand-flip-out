"""
Grand Flip Out Discord Bot - Main Entry Point
Modularized cog-based architecture with critical bug fixes

Cogs:
- market.py: Market data commands (/price, /top, /dumps, /compare, /hotlist)
- analysis.py: Learning & analysis (/analyze, /floors, /calc, /stats)
- trading.py: Trading tools (/flip, /watchlist, /alert, /portfolio)
- admin.py: Admin commands (/setchannel, /help) + scheduled tasks

Fixes:
- Race condition: Alert state protected with asyncio.Lock()
- Blocking I/O: File operations use asyncio
- N+1 API calls: Dump alert checks use cached prices
- Unbounded dicts: Periodic cleanup (todo in tasks)
- Input validation: Added price/item validation
"""

import discord
from discord.ext import commands, tasks
import aiohttp
import os
from dotenv import load_dotenv
import json
import asyncio
from difflib import SequenceMatcher
from datetime import datetime
from typing import Optional, List, Dict
import math
import heapq
import logging

# Import utilities
from utils import (
    OSRS_GOLD, format_gp,
    vol_per_hour, realistic_4h_profit, is_excluded
)

# Load environment
load_dotenv()

DISCORD_TOKEN = os.getenv('DISCORD_TOKEN')
if not DISCORD_TOKEN:
    print("ERROR: DISCORD_TOKEN not set in .env file")
    print("Copy .env.example to .env and add your bot token")
    exit(1)

API_URL = os.getenv('API_URL', 'http://localhost:3001')
WEBSITE_URL = os.getenv('WEBSITE_URL', 'https://tunatroll.github.io/grand-flip-out/')
BOT_INVITE_URL = 'https://discord.com/oauth2/authorize?client_id=1479089173209284608&permissions=1126745612873728&integration_type=0&scope=bot'
DUMP_ALERT_CHANNEL_ID = int(os.getenv('DUMP_ALERT_CHANNEL_ID', '0')) if os.getenv('DUMP_ALERT_CHANNEL_ID') else None

# OSRS Wiki API
WIKI_API_BASE = 'https://prices.runescape.wiki/api/v1/osrs'
WIKI_USER_AGENT = 'GrandFlipOut Discord Bot - OSRS flipping tool'

# Logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


# ============================================
# WIKI API CLIENT
# ============================================

class WikiApiClient:
    """Direct OSRS Wiki API client - works without backend server"""

    def __init__(self):
        self.session = None
        self.item_mapping = {}       # id -> {name, icon, limit, value, ...}
        self.item_by_name = {}       # lowercase name -> id
        self.latest_prices = {}      # id -> {high, low, highTime, lowTime}
        self.volume_data = {}        # id -> {buyVol, sellVol}
        self.prev_prices = {}        # id -> prev avg price (for dump detection)
        self.last_refresh = 0

    async def init_session(self):
        if self.session is None or self.session.closed:
            self.session = aiohttp.ClientSession(headers={'User-Agent': WIKI_USER_AGENT})

    async def close_session(self):
        if self.session and not self.session.closed:
            await self.session.close()

    async def wiki_get(self, endpoint: str) -> dict:
        """Fetch directly from OSRS Wiki API"""
        await self.init_session()
        try:
            url = f"{WIKI_API_BASE}{endpoint}"
            async with self.session.get(url, timeout=aiohttp.ClientTimeout(total=15)) as resp:
                if resp.status == 200:
                    return await resp.json()
                else:
                    return {'error': f'Wiki API returned {resp.status}'}
        except asyncio.TimeoutError:
            return {'error': 'Wiki API timed out'}
        except Exception as e:
            return {'error': str(e)}

    async def load_mapping(self):
        """Load item ID -> name mapping from Wiki API"""
        data = await self.wiki_get('/mapping')
        if isinstance(data, list):
            self.item_mapping.clear()
            self.item_by_name.clear()
            for item in data:
                if not item or not item.get('id'):
                    continue
                item_id = item['id']
                self.item_mapping[item_id] = item
                name_lower = item.get('name', '').lower()
                if name_lower:
                    self.item_by_name[name_lower] = item_id
            logger.info(f"Loaded {len(self.item_mapping)} items from Wiki mapping")
        else:
            logger.warning(f"Failed to load mapping: {data.get('error', 'unknown')}")

    async def refresh_prices(self):
        """Refresh latest prices and volumes from Wiki API"""
        latest = await self.wiki_get('/latest')
        volumes = await self.wiki_get('/5m')

        if 'data' in (latest or {}):
            # Save previous prices before clearing
            prev = dict(self.latest_prices)
            for item_id, price_data in prev.items():
                if price_data.get('high') and price_data.get('low'):
                    self.prev_prices[item_id] = (price_data['high'] + price_data['low']) // 2

            self.latest_prices = {}
            for id_str, price_data in latest['data'].items():
                if price_data and price_data.get('high') and price_data.get('low'):
                    self.latest_prices[int(id_str)] = price_data

        if 'data' in (volumes or {}):
            self.volume_data = {}
            for id_str, vol_data in volumes['data'].items():
                if vol_data:
                    self.volume_data[int(id_str)] = {
                        'buyVol': vol_data.get('lowPriceVolume', 0) or 0,
                        'sellVol': vol_data.get('highPriceVolume', 0) or 0,
                    }

        self.last_refresh = datetime.now().timestamp()
        logger.info(f"Refreshed prices for {len(self.latest_prices)} items")

    def find_item_by_name(self, query: str) -> Optional[dict]:
        """Fuzzy match item by name"""
        if not self.item_mapping:
            return None

        query_lower = query.lower()

        # Exact match
        if query_lower in self.item_by_name:
            return self._build_item_data(self.item_by_name[query_lower])

        # Fuzzy match
        best_match_id = None
        best_ratio = 0
        for name_lower, item_id in self.item_by_name.items():
            ratio = SequenceMatcher(None, query_lower, name_lower).ratio()
            if ratio > best_ratio:
                best_ratio = ratio
                best_match_id = item_id

        if best_ratio > 0.6 and best_match_id:
            return self._build_item_data(best_match_id)
        return None

    def _build_item_data(self, item_id: int) -> dict:
        meta = self.item_mapping.get(item_id, {})
        prices = self.latest_prices.get(item_id, {})
        vols = self.volume_data.get(item_id, {})

        buy_price = prices.get('low', 0) or 0
        sell_price = prices.get('high', 0) or 0
        ge_tax = min(int(sell_price * 0.02), 5000000)
        margin = sell_price - buy_price - ge_tax

        return {
            'id': item_id,
            'name': meta.get('name', f'Item {item_id}'),
            'icon': meta.get('icon', ''),
            'buy_price': buy_price,
            'sell_price': sell_price,
            'current_price': (buy_price + sell_price) // 2 if buy_price and sell_price else 0,
            'margin': margin,
            'ge_tax': ge_tax,
            'jti': self._calculate_jti(item_id),
            'buy_volume': vols.get('buyVol', 0),
            'sell_volume': vols.get('sellVol', 0),
            'ge_limit': meta.get('limit', 0),
            'highalch': meta.get('highalch', 0),
        }

    def _calculate_jti(self, item_id: int) -> float:
        """JTI: Jump-Trade Index - quality metric for flips"""
        prices = self.latest_prices.get(item_id, {})
        vols = self.volume_data.get(item_id, {})

        buy_price = prices.get('low', 0) or 0
        sell_price = prices.get('high', 0) or 0
        high_time = prices.get('highTime', 0) or 0
        low_time = prices.get('lowTime', 0) or 0
        total_vol = (vols.get('buyVol', 0)) + (vols.get('sellVol', 0))

        # Freshness (15pts)
        freshness = 0
        if high_time or low_time:
            age = int(datetime.now().timestamp()) - max(high_time, low_time)
            if age < 30: freshness = 100
            elif age < 60: freshness = 95
            elif age < 120: freshness = 85
            elif age < 300: freshness = 70
            elif age < 600: freshness = 50
            elif age < 1200: freshness = 25
            else: freshness = max(0, 100 - (age / 600) * 100)

        # Liquidity (20pts)
        liquidity = 0
        if total_vol >= 100000: liquidity = 100
        elif total_vol >= 50000: liquidity = 90
        elif total_vol >= 10000: liquidity = 75
        elif total_vol >= 1000: liquidity = 50
        elif total_vol >= 100: liquidity = 25
        elif total_vol > 0: liquidity = min(100, (total_vol / 100) * 25)

        # Margin (20pts)
        margin_score = 0
        if buy_price > 0 and sell_price > 0:
            spread = (sell_price - buy_price) / buy_price
            if spread <= 0: margin_score = 0
            elif spread <= 0.025: margin_score = 40
            elif spread <= 0.05: margin_score = 100
            elif spread <= 0.10: margin_score = 80
            elif spread <= 0.20: margin_score = 50
            elif spread <= 0.40: margin_score = 25
            else: margin_score = max(0, 100 - spread * 50)

        # Velocity (15pts)
        velocity = 0
        if total_vol >= 10000: velocity = 100
        elif total_vol >= 5000: velocity = 90
        elif total_vol >= 1000: velocity = 75
        elif total_vol >= 500: velocity = 60
        elif total_vol >= 100: velocity = 45
        elif total_vol >= 50: velocity = 30
        elif total_vol >= 10: velocity = 15
        elif total_vol > 0: velocity = min(100, (total_vol / 10) * 15)

        jti = round((freshness * 15 + liquidity * 20 + margin_score * 20 + 50 * 15 + 50 * 15 + velocity * 15) / 100)
        return max(0, min(100, jti))

    def get_top_items(self, sort_by='realistic', limit=10):
        """Get top items by various metrics"""
        items = []
        for item_id in self.latest_prices:
            data = self._build_item_data(item_id)
            if is_excluded(data.get('name', '')):
                continue
            if data['margin'] > 0 and (data['buy_volume'] + data['sell_volume']) > 0:
                items.append(data)

        if sort_by == 'volume':
            return heapq.nlargest(limit, items, key=lambda x: x['buy_volume'] + x['sell_volume'])
        elif sort_by == 'margin':
            return heapq.nlargest(limit, items, key=lambda x: x.get('margin', 0))
        else:  # realistic (default)
            return heapq.nlargest(limit, items, key=lambda x: realistic_4h_profit(x))

    JUNK_KEYWORDS = ['burnt ', 'broken ', 'damaged ', 'cake tin', 'pie dish', 'beer glass',
                     'wooden shield', 'leather body', 'leather chaps', 'bronze ', 'iron ',
                     'steel ', 'mithril ', 'adamant ', 'rune pickaxe', 'rune axe']

    def get_dumps(self, limit=10, for_alert=False):
        """Find items with big buy-sell margins (dump opportunities)"""
        dumps = []
        for item_id, prices in self.latest_prices.items():
            meta = self.item_mapping.get(item_id, {})
            name = meta.get('name', f'Item {item_id}')
            name_lower = name.lower()

            # Skip junk items and excluded items (bonds)
            if any(junk in name_lower for junk in self.JUNK_KEYWORDS):
                continue
            if is_excluded(name):
                continue

            current_buy = prices.get('low', 0) or 0
            current_sell = prices.get('high', 0) or 0
            if current_buy == 0 or current_sell == 0:
                continue

            # Minimum value gate
            avg_price = (current_buy + current_sell) // 2
            if avg_price < 5000:
                continue

            # Volume gate
            vols = self.volume_data.get(item_id, {})
            total_vol = (vols.get('buyVol', 0)) + (vols.get('sellVol', 0))
            if total_vol < 1:
                high_time = prices.get('highTime', 0) or 0
                low_time = prices.get('lowTime', 0) or 0
                now_ts = int(datetime.now().timestamp())
                recent_buy = (now_ts - low_time) < 900 if low_time else False
                recent_sell = (now_ts - high_time) < 900 if high_time else False
                if recent_buy and recent_sell:
                    total_vol = 1
                else:
                    continue

            # Calculate actual flip profit
            ge_tax = min(int(current_sell * 0.02), 5000000)
            flip_profit = current_sell - current_buy - ge_tax
            if flip_profit <= 0:
                continue

            margin_pct = (flip_profit / current_buy * 100) if current_buy > 0 else 0

            if not for_alert:
                if margin_pct < 5 and flip_profit < 10000:
                    continue
            else:
                if flip_profit < 100000:
                    continue
                if avg_price < 100000:
                    continue
                if total_vol < 3:
                    continue
                if margin_pct < 3:
                    continue

            vol_per_hr = total_vol * 12
            vol_4h = vol_per_hr * 4
            ge_limit = meta.get('limit', 0) or 999999
            can_buy = min(ge_limit, int(vol_4h))
            total_profit = flip_profit * max(1, can_buy)

            prev_avg = self.prev_prices.get(item_id, 0)
            price_dropped = prev_avg > 0 and current_buy < prev_avg * 0.95

            dumps.append({
                'id': item_id,
                'name': name,
                'icon': meta.get('icon', ''),
                'buy_price': current_buy,
                'sell_price': current_sell,
                'flip_profit': flip_profit,
                'margin_pct': round(margin_pct, 1),
                'total_profit': total_profit,
                'can_buy': can_buy,
                'volume': total_vol,
                'normal_price': prev_avg if prev_avg > 0 else avg_price,
                'price_dropped': price_dropped,
            })

        dumps.sort(key=lambda x: (x['price_dropped'], x['flip_profit']), reverse=True)
        return dumps[:limit]

    def get_market_summary(self):
        """Get overall market summary"""
        total_volume = 0
        total_margin = 0
        count = 0
        top_item = None
        top_val = 0
        for item_id in self.latest_prices:
            d = self._build_item_data(item_id)
            v = d['buy_volume'] + d['sell_volume']
            total_volume += v
            if d['margin'] > 0:
                total_margin += d['margin']
                count += 1
                if d['margin'] > top_val:
                    top_val = d['margin']
                    top_item = d
        return {
            'total_volume': total_volume,
            'avg_margin': total_margin // max(1, count),
            'active_items': len(self.latest_prices),
            'top_item_by_margin': top_item,
        }


# ============================================
# BOT SETUP
# ============================================

intents = discord.Intents.default()
intents.message_content = True

class MyBot(commands.Bot):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.wiki_client = WikiApiClient()
        self.item_stats = {}  # Streaming stats for items
        self.config = {
            'DUMP_ALERT_CHANNEL_ID': DUMP_ALERT_CHANNEL_ID,
            'API_URL': API_URL,
        }


bot = MyBot(command_prefix='!', intents=intents)


@bot.event
async def on_ready():
    """Bot startup - load data and start tasks"""
    logger.info(f'{bot.user} has logged in')

    # Load initial data
    await bot.wiki_client.load_mapping()
    await bot.wiki_client.refresh_prices()

    # Start background tasks (in cogs)
    # refresh_prices_loop and refresh_mapping_loop are started here
    refresh_prices_loop.start()
    refresh_mapping_loop.start()

    # Sync commands
    try:
        synced = await bot.tree.sync()
        logger.info(f"Synced {len(synced)} command(s)")
    except Exception as e:
        logger.error(f"Failed to sync commands: {e}")

    logger.info('Bot is ready!')


@tasks.loop(seconds=30)
async def refresh_prices_loop():
    """Continuously refresh prices from Wiki API"""
    await bot.wiki_client.refresh_prices()


@refresh_prices_loop.before_loop
async def before_refresh():
    await bot.wait_until_ready()


@tasks.loop(hours=6)
async def refresh_mapping_loop():
    """Periodically refresh item mapping to pick up new items"""
    logger.info("[GFO] Refreshing item mapping...")
    await bot.wiki_client.load_mapping()
    logger.info(f"[GFO] Mapping refreshed: {len(bot.wiki_client.item_mapping)} items")


@refresh_mapping_loop.before_loop
async def before_mapping_refresh():
    await bot.wait_until_ready()


@bot.tree.error
async def on_app_command_error(interaction: discord.Interaction, error: Exception):
    """Global error handler for slash commands — friendly, specific, actionable"""
    logger.error(f"Command error in /{interaction.command.name if interaction.command else 'unknown'}: {error}", exc_info=error)

    # Build a helpful error message based on the error type
    original = getattr(error, 'original', error)

    if isinstance(original, aiohttp.ClientError):
        msg = "Couldn't reach the OSRS Wiki API right now — it might be down or slow. Try again in a minute."
    elif isinstance(original, asyncio.TimeoutError):
        msg = "That request timed out — the API is probably under heavy load. Give it another shot in a moment."
    elif isinstance(original, discord.Forbidden):
        msg = "I don't have permission to do that here. Make sure I have the right role/channel permissions."
    elif isinstance(original, ValueError):
        msg = f"Something didn't look right with the input: {str(original)[:80]}. Double-check and try again."
    elif isinstance(original, KeyError):
        msg = "Couldn't find that item — check the spelling or try the autocomplete suggestions."
    elif 'cooldown' in str(error).lower():
        msg = "You're sending commands too fast — take a breath and try again in a few seconds."
    else:
        msg = f"Something went wrong — this is on our end, not yours. Try the command again in a moment."

    try:
        if not interaction.response.is_done():
            await interaction.response.send_message(f"⚠️ {msg}", ephemeral=True)
        else:
            await interaction.followup.send(f"⚠️ {msg}", ephemeral=True)
    except Exception:
        pass  # Can't respond at all — interaction expired


async def load_cogs():
    """Load all cogs from the cogs directory"""
    cogs_dir = 'cogs'
    for filename in os.listdir(cogs_dir):
        if filename.endswith('.py') and not filename.startswith('_'):
            cog_name = filename[:-3]
            try:
                await bot.load_extension(f'cogs.{cog_name}')
                logger.info(f"Loaded cog: {cog_name}")
            except Exception as e:
                logger.error(f"Failed to load cog {cog_name}: {e}")


async def main():
    """Main entry point"""
    async with bot:
        # Load cogs
        await load_cogs()

        # Start bot
        await bot.start(DISCORD_TOKEN)


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        logger.info("Bot shutting down...")
    finally:
        try:
            asyncio.run(bot.wiki_client.close_session())
        except Exception:
            pass
