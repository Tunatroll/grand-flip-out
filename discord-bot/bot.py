import discord
from discord.ext import commands, tasks
from discord import app_commands
import aiohttp
import os
from dotenv import load_dotenv
import json
import asyncio
from difflib import SequenceMatcher
from datetime import datetime
from typing import Optional, List, Dict, Tuple
import math
import traceback
import heapq

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
WATCHLIST_FILE = 'watchlists.json'
USER_ALERTS_FILE = 'user_alerts.json'
MARKET_SUMMARY_CHANNEL_FILE = 'market_summary_channel.json'

# OSRS Wiki API
WIKI_API_BASE = 'https://prices.runescape.wiki/api/v1/osrs'
WIKI_USER_AGENT = 'GrandFlipOut Discord Bot - OSRS flipping tool'

# OSRS colors
OSRS_GOLD = 0xFF981F
OSRS_GREEN = 0x00FF00
OSRS_RED = 0xFF0000
OSRS_BLUE = 0x0099FF

# Never flip bonds — that's unethical. Exclude from all recommendations.
EXCLUDED_ITEMS = {'old school bond'}

def is_excluded(name: str) -> bool:
    return name.lower().strip() in EXCLUDED_ITEMS

# ============================================
# ITEM INTELLIGENCE KNOWLEDGE BASE
# ============================================
ITEM_INTELLIGENCE = {
    'blood rune': {
        'floor_price': 200,
        'floor_reason': "Ali's Wares in Al Kharid buys at 200gp each",
        'demand_drivers': ['Blood Runecrafting supply boost', 'Altar construction training', 'Essence runners buying'],
        'supply_info': 'Blood Runecrafting, Nex drops, Theatre of Blood loot, NPC purchase floor',
        'synergies': ['Blood essence', 'Daeyalt essence', 'Small pouch'],
        'analysis_example': 'Blood runes have a hard floor at 200gp due to Ali\'s Wares. When GE price approaches this floor, buy signal is strong. Supply from blood RC is limited by botting crackdowns, creating regular cycles.',
        'notable_conditions': 'Price within 10% of 200gp floor — indicates strong demand vs supply shortage',
        'category': 'rune'
    },
    'death rune': {
        'floor_price': 180,
        'floor_reason': "Ali's Wares in Al Kharid buys at 180gp each",
        'demand_drivers': ['Ancients barrage/burst training', 'PvM high-level spells', 'Herb tar crafting'],
        'supply_info': 'High Alchemy of death items, Monster drops, NPC purchase floor',
        'synergies': ['Chaos rune', 'Air rune', 'Curse spellbooks'],
        'analysis_example': 'Death runes are consumed heavily in high-level PvM content and Ancients training. The 180gp floor from Ali\'s makes this a consistent buy zone. When supply tightens, prices spike 20-30% above floor.',
        'notable_conditions': 'Price near 180gp floor suggests oversupply; wait for demand spike in PvM seasons',
        'category': 'rune'
    },
    'nature rune': {
        'floor_price': 90,
        'floor_reason': "Lundail (Al Kharid) and Ali's Wares NPC buy floors",
        'demand_drivers': ['High Alchemy demand (alching items)', 'Crafting Amulet of nature', 'Spellbook swapping'],
        'supply_info': 'Monster drops, Runecrafting, NPC purchase floor',
        'synergies': ['Dragon bones', 'Onyx', 'Jewelry alching', 'Crafting demand'],
        'analysis_example': 'Nature runes are essential for high alching. Price tightly bound to alchable items like dragon bones. When alch profit spikes, nature rune demand increases, pushing price up from floor.',
        'notable_conditions': 'Price at floor (90gp) while dragon bones high = strong upside as alchers buy',
        'category': 'rune'
    },
    'cosmic rune': {
        'floor_price': 50,
        'floor_reason': 'Vendor buy price creates soft floor',
        'demand_drivers': ['Runecrafting training', 'Magic training spells', 'Crafting cosmics'],
        'supply_info': 'Runecrafting drops, Monster loot, Shop purchases',
        'synergies': ['Essence runners', 'Portal chambers'],
        'analysis_example': 'Cosmic runes have stable demand from training and magic gear. Supply varies with runecrafting popularity. Less volatile than blood/death runes but consistent 10-15% margins.',
        'notable_conditions': 'Below 50gp rare; indicates oversupply from RC bots',
        'category': 'rune'
    },
    'eclectic impling jar': {
        'floor_price': None,
        'floor_reason': 'Dual demand structure supports price floor',
        'demand_drivers': ['Clue scroll hunting (eclectic imps catch scrolls)', 'Jar generator ingredients (only 1 of 3 imps used)', 'Collection log completionists'],
        'supply_info': 'Implings found in wilderness and populated areas, player catches, jar generators create output',
        'synergies': ['Nature impling jar', 'Essence impling jar', 'Jar generators'],
        'analysis_example': 'Eclectic implings are UNIQUE: they\'re the ONLY imp used in jar generators AND catch clue scrolls. This dual demand keeps prices stable. Only 3 imps are jar generator ingredients.',
        'notable_conditions': 'Drops below 2,000gp = likely oversupply from implings rotations; wait for clue demand spike',
        'category': 'imp_jar'
    },
    'nature impling jar': {
        'floor_price': None,
        'floor_reason': 'Jar generator ingredient — one of only 3 imps used',
        'demand_drivers': ['Jar generator ingredient (critical bottleneck)', 'Clue scroll hunting for nature imps', 'Collection log', 'Passive income staking'],
        'supply_info': 'Nature implings found in woodlands, jar generators as output, player catches',
        'synergies': ['Eclectic impling jar', 'Essence impling jar', 'Jar generator demand cycles'],
        'analysis_example': 'Nature implings are one of only 3 imps used as jar generator ingredients. This creates consistent baseline demand. Supply is limited because impling spawn is constrained.',
        'notable_conditions': 'Price drops with high impling activity; rises when jar generator demand increases',
        'category': 'imp_jar'
    },
    'essence impling jar': {
        'floor_price': None,
        'floor_reason': 'Jar generator ingredient — one of only 3 imps used',
        'demand_drivers': ['Jar generator ingredient (essential)', 'Runecrafting training demand cycles', 'Passive income staking', 'Collection log'],
        'supply_info': 'Essence implings from rune essence areas, jar generators output, player catches',
        'synergies': ['Eclectic impling jar', 'Nature impling jar', 'Jar generator market phases'],
        'analysis_example': 'Essence implings compete with Eclectic & Nature for the critical jar generator demand. All 3 imp types are necessary, creating supply/demand tension. When one becomes scarce, prices for all 3 rise.',
        'notable_conditions': 'All 3 imps drop together = jar generator demand weak; rise together = high demand',
        'category': 'imp_jar'
    },
    'blood essence': {
        'floor_price': None,
        'floor_reason': 'Supply tied to blood runecrafting consumption',
        'demand_drivers': ['Blood runecrafting ingredient (1 essence per 10 runes yields +1 extra)', 'Runecrafting efficiency gains', 'Passive AFK RC training'],
        'supply_info': 'Dropped by blood RC creatures at Ourania Altar',
        'synergies': ['Blood rune demand', 'Daeyalt essence', 'RC training seasons'],
        'analysis_example': 'Blood essence is undervalued because its value proposition is misunderstood. It boosts RC output by 10% when used. When blood rune prices spike (indict PvM demand), essences become scarce and expensive.',
        'notable_conditions': 'Low price while blood runes are high = buy essences ahead of RC meta shifts',
        'category': 'essence'
    },
    'dragon bones': {
        'floor_price': None,
        'floor_reason': 'Determined by alchemy output and supply',
        'demand_drivers': ['Prayer training (largest demand driver)', 'High alchemy (nature rune cost)', 'PvM boss drops create supply', 'Daily volume extremely high'],
        'supply_info': 'Dragon slayer tasks, Boss drops (Dragons, Cerberus, etc.), Wilderness sources',
        'synergies': ['Nature rune prices', 'Prayer training cycles', 'Superior dragon bones'],
        'analysis_example': 'Dragon bones are the most stable flip in OSRS due to predictable daily volume. Prayer training demand never stops. Margins are thin but volume is massive — good for bulk trading.',
        'notable_conditions': 'Price crashes below alch value = flipping window opens for high-volume traders',
        'category': 'prayer'
    },
    'superior dragon bones': {
        'floor_price': None,
        'floor_reason': 'Premium to dragon bones based on experience gain (50% more XP)',
        'demand_drivers': ['High-level Prayer training (mains rushing 99)', 'Quest cape grinding', 'Ironman training'],
        'supply_info': 'Cerberus exclusive drop (rare), limited supply cycles with Cerberus meta',
        'synergies': ['Dragon bones', 'Prayer flasks', 'Ectoplasm'],
        'analysis_example': 'Superior bones are Cerberus-exclusive. When Cerberus is meta, supply floods and prices crash. During dry spells, supply dries up. Highly volatile but 3-4x daily volume multiplier creates profit margins.',
        'notable_conditions': 'Price premium narrows to <30% over regular dragon bones = oversupply from Cerberus, time to sell',
        'category': 'prayer'
    },
    'cannonballs': {
        'floor_price': 5,
        'floor_reason': 'Crafting cost ceiling (mithril bar + gunpowder costs ~5gp per ball)',
        'demand_drivers': ['Slayer training (cannonball usage is massive)', 'PvM bursting', 'Melee training efficiency'],
        'supply_info': 'Crafting (steel bars + gunpowder), Monster drops, Player crafting',
        'synergies': ['Mithril bars', 'Steel bars', 'Gunpowder'],
        'analysis_example': 'Cannonballs are consumed at massive daily volume in Slayer. Demand is predictable and stable. The crafting cost sets a hard floor. Best for high-volume, low-margin flipping.',
        'notable_conditions': 'Below 5gp floor = buy and arbitrage back above floor immediately',
        'category': 'other'
    },
    'ranarr weed': {
        'floor_price': None,
        'floor_reason': 'Determined by Herblore training demand and supply',
        'demand_drivers': ['Herblore training (staple herb)', 'Herblore contract demand', 'Stamina/Super energy pots', 'Restoration pots'],
        'supply_info': 'Herb farming, Monster drops (Ranarr seeds grown)',
        'synergies': ['Herb seeds', 'Snapdragon', 'Toadflax', 'Herblore training cycles'],
        'analysis_example': 'Ranarr is the most consistent herblore ingredient for training. Supply cycles with herb farming seasons. Demand is constant from training and potions. Good margins during supply shortages.',
        'notable_conditions': 'Price dips when herb seeds drop in price (more supply coming); rises when seeds scarce',
        'category': 'herb'
    },
    'snapdragon': {
        'floor_price': None,
        'floor_reason': 'High-tier herb driven by farming demand',
        'demand_drivers': ['Herblore training (stamina/super energy)', 'Prayer pot demand (high-level PvM)', 'Potion flasking'],
        'supply_info': 'Herb farming (snapdragon seeds), Monster drops',
        'synergies': ['Snapdragon seeds', 'Stamina potions', 'Prayer potions'],
        'analysis_example': 'Snapdragon prices follow farming cycles. When herb seeds are cheap, farmers plant more, supply increases 2 weeks later. Best entry points are before farming seasons.',
        'notable_conditions': 'Price spikes after farming event announcements; drops when fresh supply enters market',
        'category': 'herb'
    },
    'zulrah scales': {
        'floor_price': None,
        'floor_reason': 'Determined by DPS in degradable gear demand',
        'demand_drivers': ['Gear degradation (equipment sinks)', 'Ranged gear maintenance (blowpipe, crossbows)', 'PvM supply cost'],
        'supply_info': 'Zulrah exclusive drops, Boss-only source',
        'synergies': ['Toxic blowpipe demand', 'Gear repair costs', 'PvM activity levels'],
        'analysis_example': 'Zulrah scales are a consumable sink for PvM players. Demand spikes when PvM is meta and players use BiS gear. Supply is Zulrah kill-rate dependent. Great for following PvM trends.',
        'notable_conditions': 'Price drops when Zulrah botting increases; rises when raid content drops',
        'category': 'other'
    },
    'amethyst arrows': {
        'floor_price': 2,
        'floor_reason': 'Crafting cost (amethyst + feathers) creates floor',
        'demand_drivers': ['Ranged training (arrows consumed)', 'Slayer cannoning', 'PvM budget ranged'],
        'supply_info': 'Arrow crafting (amethyst chunks + feathers), Monster drops',
        'synergies': ['Amethyst shards', 'Feathers', 'Runite arrows'],
        'analysis_example': 'Amethyst arrows are mid-tier ranged consumables. Demand steady from training and slayer. Supply cycles with amethyst availability. Margins tight but volume is moderate.',
        'notable_conditions': 'Below crafting cost = buy and craft arbitrage immediately',
        'category': 'ranged'
    },
    'stamina potion': {
        'floor_price': None,
        'floor_reason': 'Ingredient cost (snapdragon + amylase) sets soft floor',
        'demand_drivers': ['Skilling (universal stamina use)', 'PvM dodging mechanics', 'Runecrafting efficiency', 'Woodcutting/Fishing AFK training'],
        'supply_info': 'Herblore crafting from snapdragon',
        'synergies': ['Snapdragon', 'Amylase crystal', 'Energy potions'],
        'analysis_example': 'Stamina pots have universal demand across all PvE and PvP. The demand is truly constant. Supply is limited by herblore popularity. Very stable flip margins.',
        'notable_conditions': 'Price below ingredient cost = craft immediately for arbitrage; indicates supply overstock',
        'category': 'potions'
    }
}

def get_item_intelligence(item_name: str) -> Optional[dict]:
    """Retrieve intelligence data for an item"""
    return ITEM_INTELLIGENCE.get(item_name.lower())

# Intents
intents = discord.Intents.default()
bot = commands.Bot(command_prefix='!', intents=intents)

# ── Backend Server Integration ──────────────────────────────────
# When the Express server is running, the bot can pull enriched data
# (JTI scores, category analysis, recipe profits) from the server API
# AND receive real-time WebSocket pushes for dump alerts.
# When the server is offline, it falls back to the direct Wiki API.

class BackendClient:
    """Optional connection to the Grand Flip Out Express backend server.
    Provides enriched data (JTI, categories, recipes) and real-time
    WebSocket alerts when available. Falls back gracefully when offline."""

    def __init__(self, api_url: str):
        self.api_url = api_url.rstrip('/')
        self.ws_url = api_url.replace('http', 'ws', 1).rstrip('/')
        self.session = None
        self.ws = None
        self.connected = False
        self._ws_task = None

    async def init_session(self):
        if self.session is None or self.session.closed:
            self.session = aiohttp.ClientSession()

    async def close(self):
        if self.ws and not self.ws.closed:
            await self.ws.close()
        if self.session and not self.session.closed:
            await self.session.close()

    async def is_server_up(self) -> bool:
        """Quick health check — is the backend server running?"""
        await self.init_session()
        try:
            async with self.session.get(f'{self.api_url}/health', timeout=aiohttp.ClientTimeout(total=3)) as resp:
                self.connected = resp.status == 200
                return self.connected
        except (aiohttp.ClientError, asyncio.TimeoutError) as e:
            self.connected = False
            return False

    async def get(self, endpoint: str) -> Optional[dict]:
        """GET from backend API. Returns None if server is offline."""
        await self.init_session()
        try:
            async with self.session.get(f'{self.api_url}/api{endpoint}', timeout=aiohttp.ClientTimeout(total=10)) as resp:
                if resp.status == 200:
                    return await resp.json()
        except (aiohttp.ClientError, asyncio.TimeoutError):
            pass
        return None

    async def connect_ws(self, on_message):
        """Connect to backend WebSocket for real-time alerts."""
        while True:
            try:
                await self.init_session()
                async with self.session.ws_connect(self.ws_url, heartbeat=30) as ws:
                    self.ws = ws
                    print(f"[WS] Connected to backend at {self.ws_url}")
                    async for msg in ws:
                        if msg.type == aiohttp.WSMsgType.TEXT:
                            try:
                                data = json.loads(msg.data)
                                await on_message(data)
                            except json.JSONDecodeError:
                                pass
                        elif msg.type in (aiohttp.WSMsgType.CLOSED, aiohttp.WSMsgType.ERROR):
                            break
            except Exception as e:
                print(f"[WS] Connection failed: {e}")
            print("[WS] Reconnecting in 30s...")
            await asyncio.sleep(30)

    def start_ws(self, on_message):
        """Start WebSocket listener as a background task."""
        self._ws_task = asyncio.create_task(self.connect_ws(on_message))


backend = BackendClient(API_URL)


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
            print(f"Loaded {len(self.item_mapping)} items from Wiki mapping")
        else:
            print(f"Failed to load mapping: {data.get('error', 'unknown')}")

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

            # Update streaming stats for each item
            for iid, prices in self.latest_prices.items():
                avg_price = ((prices.get('high', 0) or 0) + (prices.get('low', 0) or 0)) // 2
                if avg_price > 0:
                    update_item_stats(iid, avg_price)

        if 'data' in (volumes or {}):
            self.volume_data = {}
            for id_str, vol_data in volumes['data'].items():
                if vol_data:
                    self.volume_data[int(id_str)] = {
                        'buyVol': vol_data.get('lowPriceVolume', 0) or 0,
                        'sellVol': vol_data.get('highPriceVolume', 0) or 0,
                    }

        self.last_refresh = datetime.now().timestamp()
        print(f"Refreshed prices for {len(self.latest_prices)} items")

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
        items = []
        for item_id in self.latest_prices:
            data = self._build_item_data(item_id)
            if is_excluded(data.get('name', '')):
                continue
            if data['margin'] > 0 and (data['buy_volume'] + data['sell_volume']) > 0:
                items.append(data)

        # heapq.nlargest is O(n log k) vs sorted O(n log n) — faster for small limit
        if sort_by == 'volume':
            return heapq.nlargest(limit, items, key=lambda x: x['buy_volume'] + x['sell_volume'])
        elif sort_by == 'margin':
            return heapq.nlargest(limit, items, key=lambda x: x.get('margin', 0))
        else:  # realistic (default)
            return heapq.nlargest(limit, items, key=lambda x: realistic_4h_profit(x))

    # Junk items that should never trigger alerts
    JUNK_KEYWORDS = ['burnt ', 'broken ', 'damaged ', 'cake tin', 'pie dish', 'beer glass',
                     'wooden shield', 'leather body', 'leather chaps', 'bronze ', 'iron ',
                     'steel ', 'mithril ', 'adamant ', 'rune pickaxe', 'rune axe']

    def get_dumps(self, limit=10, for_alert=False):
        """Find items with big buy-sell margins (dump opportunities).

        Dump = someone sold items below market value, creating a wide spread
        between the instant-buy (low) and instant-sell (high) prices.

        If for_alert=True, applies stricter quality bars for auto-channel alerts.
        """
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

            # Minimum value gate — item must be worth something
            avg_price = (current_buy + current_sell) // 2
            if avg_price < 5000:
                continue

            # Volume gate — use 5m data if available, otherwise check trade freshness
            vols = self.volume_data.get(item_id, {})
            total_vol = (vols.get('buyVol', 0)) + (vols.get('sellVol', 0))
            # If no 5m volume data, check if latest trades are recent (within 15 min)
            if total_vol < 1:
                high_time = prices.get('highTime', 0) or 0
                low_time = prices.get('lowTime', 0) or 0
                now_ts = int(datetime.now().timestamp())
                recent_buy = (now_ts - low_time) < 900 if low_time else False
                recent_sell = (now_ts - high_time) < 900 if high_time else False
                if recent_buy and recent_sell:
                    total_vol = 1  # Treat as minimally active
                else:
                    continue

            # Calculate actual flip profit (after GE tax)
            ge_tax = min(int(current_sell * 0.02), 5000000)
            flip_profit = current_sell - current_buy - ge_tax
            if flip_profit <= 0:
                continue

            # Margin percentage — how wide is the spread relative to buy price
            margin_pct = (flip_profit / current_buy * 100) if current_buy > 0 else 0

            # For /dumps command: show items with meaningful margins
            if not for_alert:
                # At least 5% margin OR 10k+ profit per item
                if margin_pct < 5 and flip_profit < 10000:
                    continue
            else:
                # For auto-alerts: stricter quality bars
                if flip_profit < 100000:
                    continue
                if avg_price < 100000:
                    continue
                if total_vol < 3:
                    continue
                if margin_pct < 3:
                    continue

            # Estimate how many you can buy
            vol_per_hr = total_vol * 12
            vol_4h = vol_per_hr * 4
            ge_limit = meta.get('limit', 0) or 999999
            can_buy = min(ge_limit, int(vol_4h))
            total_profit = flip_profit * max(1, can_buy)

            # Check for actual price drop (if we have previous data)
            prev_avg = self.prev_prices.get(item_id, 0)
            price_dropped = prev_avg > 0 and current_buy < prev_avg * 0.95  # 5%+ drop

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

        # Sort: items with confirmed price drops first, then by flip profit
        dumps.sort(key=lambda x: (x['price_dropped'], x['flip_profit']), reverse=True)
        return dumps[:limit]

    def get_pumps(self, limit=10):
        pumps = []
        for item_id in self.latest_prices:
            meta = self.item_mapping.get(item_id, {})
            name = meta.get('name', f'Item {item_id}')

            # Quality gate — skip junk
            if any(junk in name.lower() for junk in self.JUNK_KEYWORDS):
                continue

            prices = self.latest_prices.get(item_id, {})
            avg_price = ((prices.get('high', 0) or 0) + (prices.get('low', 0) or 0)) // 2
            if avg_price < 50000:
                continue

            vols = self.volume_data.get(item_id, {})
            bv, sv = vols.get('buyVol', 0), vols.get('sellVol', 0)
            if bv > sv * 1.5 and bv > 50:
                pumps.append({'name': name, 'buy_volume': bv, 'sell_volume': sv, 'avg_price': avg_price})
        pumps.sort(key=lambda x: x['buy_volume'], reverse=True)
        return pumps[:limit]

    def get_market_summary(self):
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


api_client = WikiApiClient()


def load_watchlists():
    if os.path.exists(WATCHLIST_FILE):
        try:
            with open(WATCHLIST_FILE, 'r') as f:
                return json.load(f)
        except (json.JSONDecodeError, IOError, OSError) as e:
            print(f"Warning: Failed to load watchlists: {e}")
            return {}
    return {}


def save_watchlists(watchlists):
    with open(WATCHLIST_FILE, 'w') as f:
        json.dump(watchlists, f, indent=2)


def load_user_alerts():
    """Load all user alerts from file"""
    if os.path.exists(USER_ALERTS_FILE):
        try:
            with open(USER_ALERTS_FILE, 'r') as f:
                return json.load(f)
        except (json.JSONDecodeError, IOError, OSError) as e:
            print(f"Warning: Failed to load user alerts: {e}")
            return {}
    return {}


def save_user_alerts(alerts):
    """Save all user alerts to file"""
    with open(USER_ALERTS_FILE, 'w') as f:
        json.dump(alerts, f, indent=2)


def format_gp(value):
    """Full exact numbers with commas — no rounding, no abbreviations.
    When you're placing a GE offer, you need the exact GP figure."""
    if value is None: return "N/A"
    return f"{int(value):,} gp"

def format_gp_short(value):
    """Abbreviated version for tight spaces (status bar, compact summaries)."""
    if value is None: return "N/A"
    if abs(value) >= 1000000000: return f"{value/1000000000:.1f}B"
    if abs(value) >= 1000000: return f"{value/1000000:.1f}M"
    if abs(value) >= 1000: return f"{value/1000:.0f}K"
    return f"{int(value):,}"

def vol_per_hour(item_data):
    """Estimate hourly volume from 5-min sample"""
    return (item_data.get('buy_volume', 0) + item_data.get('sell_volume', 0)) * 12

def realistic_buys(item_data):
    """How many you can actually buy in 4 hours = min(buy limit, 4h est. volume)"""
    vol_4h = vol_per_hour(item_data) * 4
    limit = item_data.get('ge_limit', 0) or 999999
    return min(limit, int(vol_4h))

def realistic_4h_profit(item_data):
    """Realistic 4-hour profit based on actual volume, not fantasy buy limits"""
    return item_data.get('margin', 0) * realistic_buys(item_data)

def get_verdict(item_data):
    """Human-readable flip quality based on margin AND volume"""
    vph = vol_per_hour(item_data)
    profit_4h = realistic_4h_profit(item_data)
    if vph < 12: return "Dead"
    if vph < 60 or profit_4h < 10000: return "Slow"
    if profit_4h < 100000 or vph < 200: return "Decent"
    if profit_4h < 500000: return "Good Flip"
    return "Great Flip"

def format_vol_per_hour(item_data):
    vph = vol_per_hour(item_data)
    if vph == 0: return "0/hr (Dead)"
    if vph < 12: return f"~{vph}/hr (Very Low)"
    if vph < 60: return f"~{vph:,}/hr (Low)"
    if vph < 500: return f"~{vph:,}/hr (Medium)"
    return f"~{vph:,}/hr (High)"

VERDICT_EMOJI = {
    "Great Flip": "🟢", "Good Flip": "🟡", "Decent": "🟠", "Slow": "🔴", "Dead": "⚫"
}


# ── Streaming Statistics (Welford's Online Algorithm) ──────────────
# O(1) memory per item — calculate mean/stddev incrementally
class StreamingStats:
    __slots__ = ('n', 'mean', 'M2', 'min_val', 'max_val')

    def __init__(self):
        self.n = 0
        self.mean = 0.0
        self.M2 = 0.0
        self.min_val = float('inf')
        self.max_val = float('-inf')

    def update(self, x):
        self.n += 1
        delta = x - self.mean
        self.mean += delta / self.n
        delta2 = x - self.mean
        self.M2 += delta * delta2
        if x < self.min_val: self.min_val = x
        if x > self.max_val: self.max_val = x

    @property
    def variance(self):
        return self.M2 / (self.n - 1) if self.n > 1 else 0

    @property
    def stddev(self):
        return self.variance ** 0.5

    @property
    def z_score(self):
        """Z-score of the last known mean vs overall distribution."""
        return 0  # Need current price for z-score

    def get_z(self, current):
        """Z-score of a value against this distribution."""
        if self.stddev == 0 or self.n < 3:
            return 0
        return (current - self.mean) / self.stddev

# Per-item streaming stats tracker
_item_stats = {}

def update_item_stats(item_id, price):
    """Update streaming statistics for an item."""
    if item_id not in _item_stats:
        _item_stats[item_id] = StreamingStats()
    _item_stats[item_id].update(price)

def get_item_z_score(item_id, current_price):
    """Get z-score of current price vs historical distribution."""
    stats = _item_stats.get(item_id)
    if not stats or stats.n < 5:
        return 0
    return round(stats.get_z(current_price), 2)


def get_item_icon_url(item_id, item_name="", icon=""):
    if icon:
        return f"https://oldschool.runescape.wiki/images/{icon.replace(' ', '_')}"
    if item_name:
        return f"https://oldschool.runescape.wiki/images/{item_name.replace(' ', '_')}.png"
    return f"https://oldschool.runescape.wiki/images/item_{item_id}.png"


async def create_item_embed(item_data, title=None, alert_type="item"):
    """Create item embed. alert_type can be 'item', 'personal', or 'channel'"""
    item_id = item_data.get('id')
    item_name = item_data.get('name', 'Unknown')
    verdict = get_verdict(item_data)
    emoji = VERDICT_EMOJI.get(verdict, "")

    # Determine title prefix based on alert type
    if alert_type == "personal":
        title_prefix = "⚡ Personal Alert"
    elif alert_type == "channel":
        title_prefix = "💰 Channel Alert"
    else:
        title_prefix = emoji

    embed = discord.Embed(title=title or f"{title_prefix} {item_name}", color=OSRS_GOLD)
    embed.set_thumbnail(url=get_item_icon_url(item_id, item_name, item_data.get('icon', '')))

    # === PRICES — exact numbers, prominent ===
    buy_price = item_data.get('buy_price', 0)
    sell_price = item_data.get('sell_price', 0)
    margin = item_data.get('margin', 0)
    ge_tax = item_data.get('ge_tax', 0)

    if buy_price:
        embed.add_field(name="💰 Buy At (Insta-Buy)", value=f"**{format_gp(buy_price)}**", inline=True)
    if sell_price:
        embed.add_field(name="💰 Sell At (Insta-Sell)", value=f"**{format_gp(sell_price)}**", inline=True)

    embed.add_field(name="Verdict", value=f"**{verdict}**", inline=True)

    # Profit breakdown with exact numbers
    profit_detail = f"**{'+' if margin > 0 else ''}{format_gp(margin)}**/item"
    if ge_tax:
        profit_detail += f"\n(after {format_gp(ge_tax)} tax)"
    embed.add_field(name="Profit/Item", value=profit_detail, inline=True)

    # === VOLUME — much more visible ===
    buy_vol = item_data.get('buy_volume', 0)
    sell_vol = item_data.get('sell_volume', 0)
    vph = vol_per_hour(item_data)
    ge_limit = item_data.get('ge_limit', 0)
    rb = realistic_buys(item_data)

    vol_detail = f"**{format_vol_per_hour(item_data)}**"
    vol_detail += f"\nBuyers (5m): {buy_vol:,} | Sellers (5m): {sell_vol:,}"
    if ge_limit:
        vol_detail += f"\nGE Limit: **{ge_limit:,}** per 4h"
    embed.add_field(name="📊 Volume", value=vol_detail, inline=True)

    # How many you can realistically buy
    vol_limited = ge_limit and (vph * 4) < ge_limit
    can_buy_detail = f"**{rb:,} items**"
    if vol_limited:
        can_buy_detail += " ⚠️ (volume-limited)"
    elif ge_limit:
        can_buy_detail += " (limit-capped)"
    if buy_price and rb > 0:
        capital = buy_price * rb
        can_buy_detail += f"\nCapital needed: **{format_gp(capital)}**"
    embed.add_field(name="🛒 You Can Buy", value=can_buy_detail, inline=True)

    # Realistic profit with exact numbers
    rp = realistic_4h_profit(item_data)
    profit_line = f"**{format_gp(rp)}** in 4 hours"
    if rb > 0 and margin > 0:
        profit_line += f"\n({rb:,} × {format_gp(margin)} each)"
    embed.add_field(name="💎 Realistic 4h Profit", value=profit_line, inline=False)

    footer_text = "Grand Flip Out | Exact numbers, real data"
    if alert_type == "personal":
        footer_text += " | Set alerts with /alert add"
    embed.set_footer(text=footer_text)
    embed.timestamp = datetime.now()
    return embed


async def handle_ws_message(data):
    """Handle real-time messages from the backend WebSocket."""
    msg_type = data.get('type', '')

    if msg_type == 'dump_alert':
        # Server detected a dump — forward to alert channel immediately
        dumps = data.get('dumps', [])
        if not dumps or not DUMP_ALERT_CHANNEL_ID:
            return

        ch = bot.get_channel(DUMP_ALERT_CHANNEL_ID)
        if not ch:
            return

        for d in dumps[:3]:  # Max 3 per WS push
            name = d.get('name') or d.get('itemName') or f"Item {d.get('itemId', '?')}"
            change = d.get('percentChange', 0)
            buy_price = d.get('buyPrice', 0)
            sell_price = d.get('sellPrice', 0)
            margin = d.get('margin', 0)
            ge_tax = d.get('geTax', 0)
            ge_limit = d.get('geLimit', 0)
            vol_hr = d.get('volPerHour', 0)
            effective_buys = d.get('effectiveBuys', 0)
            total_profit = d.get('totalProfit', 0)
            capital_req = d.get('capitalRequired', 0)
            icon = d.get('icon', '')

            # Intelligence fields (used internally for color/emoji, NOT shown raw)
            action = d.get('action', 'WATCH')
            fillability = d.get('fillability', 'unknown')

            # Reasoning from each module — pick the most useful lines
            reasoning = d.get('reasoning', {})
            profit_reasons = reasoning.get('profit', [])

            # ── Embed color: warm gradient based on opportunity quality ──
            if action == 'BUY':
                embed_color = OSRS_GREEN
                title_emoji = '💰'
            elif action == 'WATCH':
                embed_color = OSRS_GOLD
                title_emoji = '👀'
            elif action == 'RISKY':
                embed_color = 0xFF8C00
                title_emoji = '⚠️'
            else:
                embed_color = OSRS_RED
                title_emoji = '📉'

            # ── Title: Item name + what happened ──
            embed = discord.Embed(
                title=f"{title_emoji} {name}  —  crashed {abs(change):.1f}%",
                color=embed_color,
            )

            if icon:
                icon_url = f"https://oldschool.runescape.wiki/images/{icon.replace(' ', '_')}"
                embed.set_thumbnail(url=icon_url)

            # ── The Flip: what matters to a flipper ──
            if buy_price and sell_price:
                embed.add_field(name="Buy At", value=f"**{format_gp(buy_price)}**", inline=True)
                embed.add_field(name="Sell At", value=f"**{format_gp(sell_price)}**", inline=True)
                profit_str = f"**{format_gp(margin)}**"
                if ge_tax:
                    profit_str += f"\n-{format_gp(ge_tax)} tax"
                embed.add_field(name="Profit/Item", value=profit_str, inline=True)

            # ── How much can you make? ──
            profit_block = ""
            if total_profit > 0:
                profit_block += f"💰 **{format_gp(total_profit)}** if you fill"
            if effective_buys > 0 and ge_limit > 0:
                profit_block += f"\n📦 Buy up to **{effective_buys:,.0f}** (limit {ge_limit:,})"
            if capital_req > 0:
                profit_block += f"\n🏦 Needs **{format_gp(capital_req)}** capital"
            if profit_block:
                embed.add_field(name="Potential", value=profit_block.strip(), inline=False)

            # ── Volume: can you actually trade this? ──
            vol_line = f"~{vol_hr:,.0f}/hr"
            if fillability == 'full':
                vol_line += " · fills fast ✅"
            elif fillability == 'volume_limited':
                vol_line += " · volume limited ⚠️"
            elif fillability == 'barely':
                vol_line += " · barely any trades ⚠️"
            elif fillability == 'partial':
                vol_line += " · partial fills"
            embed.add_field(name="Volume", value=vol_line, inline=True)

            # ── Fill time estimate from profit reasons ──
            fill_time = ""
            for r in profit_reasons:
                if "fill" in r.lower() or "minutes" in r.lower() or "hours" in r.lower():
                    fill_time = r
                    break
            if fill_time:
                embed.add_field(name="Fill Time", value=f"⏱️ {fill_time}", inline=True)

            embed.set_footer(text="Grand Flip Out")
            embed.timestamp = datetime.now()
            try:
                await ch.send(embed=embed)
            except Exception as e:
                print(f"[WS] Failed to send dump alert: {e}")

    elif msg_type == 'price_update':
        # We could use this to update our local cache, but the Wiki
        # direct polling is already solid. Log it for visibility.
        count = len(data.get('items', []))
        if count:
            print(f"[WS] Price update: {count} items from server")


@bot.tree.error
async def on_app_command_error(interaction: discord.Interaction, error: app_commands.AppCommandError):
    """Global error handler for slash commands — handles cooldowns and other errors."""
    if isinstance(error, app_commands.CommandOnCooldown):
        await interaction.response.send_message(
            f"Slow down! Try again in {error.retry_after:.1f}s.",
            ephemeral=True
        )
    elif isinstance(error, app_commands.CheckFailure):
        await interaction.response.send_message(
            "You don't have permission to use this command.",
            ephemeral=True
        )
    else:
        print(f"Command error in /{interaction.command.name if interaction.command else '?'}: {error}")
        if not interaction.response.is_done():
            await interaction.response.send_message(
                "Something went wrong. Try again in a moment.",
                ephemeral=True
            )


@bot.event
async def on_ready():
    print(f'{bot.user} has logged in')
    await api_client.load_mapping()
    await api_client.refresh_prices()
    refresh_prices_loop.start()
    refresh_mapping_loop.start()
    check_dumps.start()
    check_user_alerts.start()
    post_market_summary.start()
    rotate_status.start()

    # Connect to backend WebSocket if server is running
    server_up = await backend.is_server_up()
    if server_up:
        print(f"Backend server is online at {API_URL} — connecting WebSocket")
        backend.start_ws(handle_ws_message)
    else:
        print(f"Backend server offline — running standalone (Wiki API direct)")

    try:
        synced = await bot.tree.sync()
        print(f"Synced {len(synced)} command(s)")
    except Exception as e:
        print(f"Failed to sync commands: {e}")
    print('Bot is ready!')


@tasks.loop(seconds=30)
async def refresh_prices_loop():
    await api_client.refresh_prices()

@refresh_prices_loop.before_loop
async def before_refresh():
    await bot.wait_until_ready()

@tasks.loop(hours=6)
async def refresh_mapping_loop():
    """Periodically refresh item mapping to pick up new items."""
    print("[GFO] Refreshing item mapping...")
    await api_client.load_mapping()
    print(f"[GFO] Mapping refreshed: {len(api_client.item_mapping)} items")

@refresh_mapping_loop.before_loop
async def before_mapping_refresh():
    await bot.wait_until_ready()


# Rotating status messages showing live market data
_status_index = 0

@tasks.loop(minutes=5)
async def rotate_status():
    global _status_index
    try:
        if not api_client.latest_prices:
            return

        total_items = len(api_client.item_mapping)

        # Count profitable flips
        profitable = 0
        top_profit = 0
        top_name = ""
        total_volume = 0

        for item_id, meta in api_client.item_mapping.items():
            iid = int(item_id)
            prices = api_client.latest_prices.get(iid, {})
            high = prices.get('high', 0)
            low = prices.get('low', 0)
            if high > 0 and low > 0:
                margin = high - low
                tax = min(int(high * 0.02), 5000000)
                profit = margin - tax
                if profit > 0:
                    profitable += 1
                    if profit > top_profit:
                        top_profit = profit
                        top_name = meta.get('name', '')

            vol_data = api_client.volume_data.get(iid, {})
            total_volume += (vol_data.get('highPriceVolume', 0) or 0) + (vol_data.get('lowPriceVolume', 0) or 0)

        statuses = [
            discord.Activity(type=discord.ActivityType.watching, name=f"{profitable:,} profitable flips"),
            discord.Activity(type=discord.ActivityType.watching, name=f"{total_items:,} GE items"),
            discord.Activity(type=discord.ActivityType.playing, name=f"Top: {top_name[:30]}"),
            discord.Activity(type=discord.ActivityType.watching, name=f"{total_volume:,} trades (5m)"),
            discord.Activity(type=discord.ActivityType.listening, name="/flip · /alch · /risk"),
        ]

        _status_index = _status_index % len(statuses)
        await bot.change_presence(activity=statuses[_status_index])
        _status_index += 1
    except Exception as e:
        print(f"Status rotation error: {e}")

@rotate_status.before_loop
async def before_rotate_status():
    await bot.wait_until_ready()


# ── AUTOCOMPLETE ──────────────────────────────────────────────
async def item_name_autocomplete(
    interaction: discord.Interaction,
    current: str,
) -> List[app_commands.Choice[str]]:
    """Fuzzy autocomplete for item names across all slash commands."""
    if not current or len(current) < 2:
        # Show popular flipping items when no input yet
        popular = ['Twisted bow', 'Bandos godsword', 'Armadyl crossbow', 'Dragon claws',
                   'Ancestral robe top', 'Scythe of vitur', 'Tumeken\'s shadow',
                   'Abyssal whip', 'Saradomin godsword', 'Elder maul']
        return [app_commands.Choice(name=n, value=n) for n in popular[:25]]

    current_lower = current.lower()
    matches = []
    for item_id, meta in api_client.item_mapping.items():
        name = meta.get('name', '')
        name_lower = name.lower()
        if current_lower in name_lower:
            # Exact start match = highest priority, contains = lower
            priority = 0 if name_lower.startswith(current_lower) else 1
            matches.append((priority, name))

    # Sort: starts-with first, then alphabetical
    matches.sort(key=lambda x: (x[0], x[1]))
    return [app_commands.Choice(name=m[1], value=m[1]) for m in matches[:25]]


# ── PAGINATION VIEW ──────────────────────────────────────────
class PaginatedView(discord.ui.View):
    """A reusable paginated embed view with Previous/Next buttons."""

    def __init__(self, pages: List[discord.Embed], author_id: int, timeout: float = 120):
        super().__init__(timeout=timeout)
        self.pages = pages
        self.current_page = 0
        self.author_id = author_id
        self._update_buttons()

    def _update_buttons(self):
        self.prev_btn.disabled = self.current_page == 0
        self.next_btn.disabled = self.current_page >= len(self.pages) - 1
        self.page_label.label = f"{self.current_page + 1}/{len(self.pages)}"

    @discord.ui.button(label="◀", style=discord.ButtonStyle.secondary)
    async def prev_btn(self, interaction: discord.Interaction, button: discord.ui.Button):
        if interaction.user.id != self.author_id:
            await interaction.response.send_message("This isn't your command!", ephemeral=True)
            return
        self.current_page = max(0, self.current_page - 1)
        self._update_buttons()
        await interaction.response.edit_message(embed=self.pages[self.current_page], view=self)

    @discord.ui.button(label="1/1", style=discord.ButtonStyle.secondary, disabled=True)
    async def page_label(self, interaction: discord.Interaction, button: discord.ui.Button):
        await interaction.response.defer()

    @discord.ui.button(label="▶", style=discord.ButtonStyle.secondary)
    async def next_btn(self, interaction: discord.Interaction, button: discord.ui.Button):
        if interaction.user.id != self.author_id:
            await interaction.response.send_message("This isn't your command!", ephemeral=True)
            return
        self.current_page = min(len(self.pages) - 1, self.current_page + 1)
        self._update_buttons()
        await interaction.response.edit_message(embed=self.pages[self.current_page], view=self)

    async def on_timeout(self):
        for item in self.children:
            item.disabled = True


def build_paginated_embeds(items: list, title: str, per_page: int = 5, format_fn=None) -> List[discord.Embed]:
    """Build a list of embeds from items, splitting into pages."""
    pages = []
    total_pages = max(1, math.ceil(len(items) / per_page))
    for page_num in range(total_pages):
        start = page_num * per_page
        page_items = items[start:start + per_page]
        embed = discord.Embed(
            title=f"{title} (Page {page_num + 1}/{total_pages})",
            color=OSRS_GOLD,
        )
        if format_fn:
            for i, item in enumerate(page_items, start + 1):
                name, value = format_fn(i, item)
                embed.add_field(name=name, value=value, inline=False)
        embed.set_footer(text="Grand Flip Out | OSRS Wiki API")
        embed.timestamp = datetime.now()
        pages.append(embed)
    return pages


@bot.tree.command(name="price", description="Look up current price and profit margins for an item")
@app_commands.describe(item_name="Name of the item to look up")
@app_commands.autocomplete(item_name=item_name_autocomplete)
@app_commands.checks.cooldown(1, 3.0, key=lambda i: (i.guild_id, i.user.id))
async def price_command(interaction: discord.Interaction, item_name: str):
    await interaction.response.defer()
    item = api_client.find_item_by_name(item_name)
    if not item:
        await interaction.followup.send(f"Couldn't find item matching '{item_name}'. Try a different name.")
        return
    embed = await create_item_embed(item)
    await interaction.followup.send(embed=embed)


@bot.tree.command(name="top", description="Show top items by realistic profit")
@app_commands.describe(sort="Sort by: realistic, margin, volume, or jti", limit="Number of items (1-50)")
@app_commands.checks.cooldown(1, 3.0, key=lambda i: (i.guild_id, i.user.id))
async def top_command(interaction: discord.Interaction, sort: str = "realistic", limit: int = 10):
    await interaction.response.defer()
    sort = sort.lower() if sort else "realistic"
    if sort not in ["realistic", "margin", "volume", "jti"]: sort = "realistic"
    limit = max(1, min(limit, 50))

    # Try backend server first for enriched JTI data
    server_items = None
    if backend.connected or await backend.is_server_up():
        sort_map = {"realistic": "profitPerLimit", "margin": "margin", "volume": "volume", "jti": "jti"}
        data = await backend.get(f'/items?sort={sort_map.get(sort, "jti")}&limit={limit}')
        if data and data.get('items'):
            server_items = data['items']

    if server_items:
        # Use enriched backend data
        sort_label = {"realistic": "Realistic Profit", "margin": "Profit per Item", "volume": "Volume", "jti": "JTI Score"}[sort]
        embed = discord.Embed(title=f"Top {limit} Flips — {sort_label}", color=OSRS_GOLD, description="Live data from Grand Flip Out server with full JTI analysis.")
        for i, item in enumerate(server_items, 1):
            n = item.get('name', '?')
            jti = item.get('jti', 0)
            margin_gp = item.get('margin', {}).get('gp', 0) if isinstance(item.get('margin'), dict) else item.get('margin', 0)
            ppl = item.get('profitPerLimit', 0)
            vol = item.get('volume', {}).get('total', 0) if isinstance(item.get('volume'), dict) else item.get('volume', 0)
            flags = item.get('flags', {})

            flag_str = ""
            if flags.get('isDump'): flag_str += " 🔴DUMP"
            if flags.get('isVolumeSpike'): flag_str += " ⚡SPIKE"
            if jti >= 80: flag_str += " 🟢HOT"

            v = f"JTI: **{jti:.0f}** | {format_gp(margin_gp)}/item | **{format_gp(ppl)}/limit** | Vol: {format_gp(vol)}{flag_str}"
            embed.add_field(name=f"{i}. {n}", value=v, inline=False)
        embed.set_footer(text="Grand Flip Out | Server-enriched JTI data")
        embed.timestamp = datetime.now()
        await interaction.followup.send(embed=embed)
    else:
        # Fallback to direct Wiki data
        items = api_client.get_top_items(sort_by=sort if sort != "jti" else "realistic", limit=limit)
        if not items:
            await interaction.followup.send("No items found. Data may still be loading.")
            return
        sort_label = {"realistic": "Realistic 4h Profit", "margin": "Profit per Item", "volume": "Volume", "jti": "JTI Score"}[sort]

        def format_top(i, item):
            n = item['name']
            verdict = get_verdict(item)
            emoji = VERDICT_EMOJI.get(verdict, "")
            rp = realistic_4h_profit(item)
            rb = realistic_buys(item)
            buy_p = item.get('buy_price', 0)
            sell_p = item.get('sell_price', 0)
            margin_val = item.get('margin', 0)
            # Always show: buy price, sell price, profit, volume, can-buy quantity
            line1 = f"{emoji} Buy: **{format_gp(buy_p)}** → Sell: **{format_gp(sell_p)}**"
            line2 = f"Profit: **{format_gp(margin_val)}**/item → **{format_gp(rp)}**/4h ({rb:,} buyable)"
            line3 = f"Vol: {format_vol_per_hour(item)}"

            # Add intelligence if available
            intel = get_item_intelligence(n)
            if intel and intel.get('floor_price'):
                current_price = item.get('current_price', 0)
                floor_price = intel['floor_price']
                pct_to_floor = ((current_price - floor_price) / floor_price * 100) if floor_price > 0 else 0
                if pct_to_floor <= 10:
                    line3 += f" | 💡 **NEAR FLOOR** ({pct_to_floor:+.0f}%)"

            return f"{i}. {n}", f"{line1}\n{line2}\n{line3}"

        pages = build_paginated_embeds(items, f"Top Flips — {sort_label}", per_page=5, format_fn=format_top)
        if len(pages) > 1:
            view = PaginatedView(pages, interaction.user.id)
            await interaction.followup.send(embed=pages[0], view=view)
        else:
            await interaction.followup.send(embed=pages[0])


@bot.tree.command(name="dumps", description="Show items with wide margins — buy cheap, sell normal, pocket the profit")
@app_commands.checks.cooldown(1, 3.0, key=lambda i: (i.guild_id, i.user.id))
async def dumps_command(interaction: discord.Interaction):
    await interaction.response.defer()
    dumps = api_client.get_dumps()
    if not dumps:
        # Debug info to help diagnose empty results
        price_count = len(api_client.latest_prices)
        vol_count = len(api_client.volume_data)
        mapping_count = len(api_client.item_mapping)
        await interaction.followup.send(
            f"No dump opportunities right now. Market is stable.\n"
            f"-# Tracking {price_count:,} prices, {vol_count:,} volumes, {mapping_count:,} items mapped"
        )
        return
    def format_dump(i, item):
        drop_indicator = " 📉" if item.get('price_dropped') else ""
        buy_p = item.get('buy_price', 0)
        sell_p = item.get('sell_price', 0)
        can_buy = item.get('can_buy', 0)
        vol = item.get('volume', 0)
        return (
            f"{item['name']}{drop_indicator} — {format_gp(item['flip_profit'])}/item",
            f"Buy: **{format_gp(buy_p)}** → Sell: **{format_gp(sell_p)}**\n"
            f"Total profit: **{format_gp(item['total_profit'])}** ({can_buy:,} buyable)\n"
            f"Vol (5m): {vol:,} trades"
        )

    pages = build_paginated_embeds(dumps, "💰 Dump Opportunities", per_page=5, format_fn=format_dump)
    if len(pages) > 1:
        view = PaginatedView(pages, interaction.user.id)
        await interaction.followup.send(embed=pages[0], view=view)
    else:
        await interaction.followup.send(embed=pages[0])


@bot.tree.command(name="pumps", description="Show items with unusual buy pressure")
async def pumps_command(interaction: discord.Interaction):
    await interaction.response.defer()
    pumps = api_client.get_pumps()
    if not pumps:
        await interaction.followup.send("No pump alerts at the moment.")
        return
    embed = discord.Embed(title="Pump Alerts - Unusual Buy Pressure!", color=OSRS_GREEN)
    for item in pumps:
        embed.add_field(name=f"📈 {item['name']}", value=f"Buy: {item['buy_volume']:,} | Sell: {item['sell_volume']:,}", inline=False)
    embed.set_footer(text="Grand Flip Out | OSRS Wiki API")
    embed.timestamp = datetime.now()
    await interaction.followup.send(embed=embed)


@bot.tree.command(name="recipe", description="Show profitable processing recipes")
async def recipe_command(interaction: discord.Interaction):
    await interaction.response.defer()
    herbs = [(249,251,'Guam'),(253,255,'Marrentill'),(257,259,'Tarromin'),(261,263,'Harralander'),(265,267,'Ranarr'),(269,271,'Irit'),(273,275,'Avantoe'),(277,279,'Kwuarm'),(281,283,'Cadantine'),(285,287,'Lantadyme'),(289,291,'Dwarf weed'),(293,295,'Torstol'),(301,303,'Snapdragon'),(305,307,'Toadflax')]
    gems = [(1617,1619,'Ruby'),(1621,1623,'Diamond'),(1625,1627,'Dragonstone'),(6573,6575,'Onyx')]
    profitable = []
    for raw_id, clean_id, name in herbs:
        rp = (api_client.latest_prices.get(raw_id, {}).get('low') or 0)
        cp = (api_client.latest_prices.get(clean_id, {}).get('high') or 0)
        if rp > 0 and cp > 0 and cp - rp > 0:
            profitable.append(('🌿', name, rp, cp, cp-rp, (cp-rp)*900))
    for raw_id, cut_id, name in gems:
        rp = (api_client.latest_prices.get(raw_id, {}).get('low') or 0)
        cp = (api_client.latest_prices.get(cut_id, {}).get('high') or 0)
        if rp > 0 and cp > 0 and cp - rp > 0:
            profitable.append(('💎', name, rp, cp, cp-rp, (cp-rp)*120))
    profitable.sort(key=lambda x: x[5], reverse=True)
    if not profitable:
        await interaction.followup.send("No profitable recipes at the moment.")
        return
    embed = discord.Embed(title="Profitable Processing Recipes", color=OSRS_BLUE, description="Herb cleaning (~900/hr) and gem cutting (~120/hr)")
    for i, (icon, name, buy, sell, profit, per_hr) in enumerate(profitable[:12], 1):
        embed.add_field(name=f"{i}. {icon} {name}", value=f"Buy: {format_gp(buy)} → Sell: {format_gp(sell)}\nProfit: {format_gp(profit)} | {format_gp(per_hr)}/hr", inline=False)
    embed.set_footer(text="Grand Flip Out | OSRS Wiki API")
    embed.timestamp = datetime.now()
    await interaction.followup.send(embed=embed)


@bot.tree.command(name="calc", description="Calculate flip profit for an item with realistic market analysis")
@app_commands.describe(item_name="Name of the item")
@app_commands.autocomplete(item_name=item_name_autocomplete)
@app_commands.checks.cooldown(1, 3.0, key=lambda i: (i.guild_id, i.user.id))
async def calc_command(interaction: discord.Interaction, item_name: str):
    await interaction.response.defer()
    item = api_client.find_item_by_name(item_name)
    if not item:
        await interaction.followup.send(f"Couldn't find item matching '{item_name}'.")
        return
    verdict = get_verdict(item)
    emoji = VERDICT_EMOJI.get(verdict, "")
    rp = realistic_4h_profit(item)
    rb = realistic_buys(item)
    vph = vol_per_hour(item)
    ge_limit = item.get('ge_limit', 0)
    vol_limited = ge_limit and (vph * 4) < ge_limit

    embed = discord.Embed(title=f"{emoji} {item['name']} — {verdict}", color=OSRS_GOLD)
    embed.set_thumbnail(url=get_item_icon_url(item['id'], item['name'], item.get('icon', '')))

    # Prices — exact numbers
    embed.add_field(name="💰 Buy At", value=f"**{format_gp(item['buy_price'])}**", inline=True)
    embed.add_field(name="💰 Sell At", value=f"**{format_gp(item['sell_price'])}**", inline=True)
    embed.add_field(name="Profit/Item", value=f"**{format_gp(item['margin'])}** (after {format_gp(item['ge_tax'])} tax)", inline=True)

    # Volume & limits — prominent
    bv_raw = item.get('buy_volume', 0) or 0
    sv_raw = item.get('sell_volume', 0) or 0
    vol_detail = f"**{format_vol_per_hour(item)}**\nBuyers (5m): {bv_raw:,} | Sellers (5m): {sv_raw:,}"
    embed.add_field(name="📊 Volume", value=vol_detail, inline=True)
    embed.add_field(name="GE Limit (4h)", value=f"**{ge_limit:,}**" if ge_limit else "Unknown", inline=True)

    # The honest truth
    profit_text = f"**{format_gp(rp)}** in 4 hours"
    if vol_limited:
        profit_text += f"\n⚠️ Volume limited: only ~{rb:,} available per 4h"
        profit_text += f"\n(GE limit is {ge_limit:,} but not enough trades happening)"
    else:
        profit_text += f"\n~{rb:,} items tradeable in 4h"
    embed.add_field(name="Realistic Profit", value=profit_text, inline=False)

    # Fill rate estimation
    fill_likelihood = min(100, round((vph * 4 / max(1, rb)) * 100)) if vph > 0 else 0
    fill_label = '🟢 Instant' if fill_likelihood >= 90 else '🟡 Fast' if fill_likelihood >= 60 else '🟠 Slow' if fill_likelihood >= 30 else '🔴 Very slow'
    embed.add_field(name="Fill Rate", value=f"{fill_label} ({fill_likelihood}%)", inline=True)

    # Buy/sell pressure
    bv = item.get('buy_volume', 0) or 0
    sv = item.get('sell_volume', 0) or 0
    total_v = bv + sv
    if total_v > 0:
        buy_pct = round(bv / total_v * 100)
        pressure = '🟢 Bullish' if buy_pct > 60 else '🔴 Bearish' if buy_pct < 40 else '⚪ Balanced'
        embed.add_field(name="Order Flow", value=f"Buy {buy_pct}% / Sell {100-buy_pct}% — {pressure}", inline=True)

    # Manipulation risk
    margin_pct = (item['margin'] / item['buy_price'] * 100) if item['buy_price'] > 0 else 0
    risk_score = 0
    risk_reasons = []
    if margin_pct > 15 and vph < 24: risk_score += 35; risk_reasons.append('High margin + low vol')
    elif margin_pct > 10 and vph < 60: risk_score += 20; risk_reasons.append('Elevated margin/vol ratio')
    if ge_limit and ge_limit <= 8: risk_score += 15; risk_reasons.append(f'Low GE limit ({ge_limit})')
    risk_level = '🚨 HIGH' if risk_score >= 50 else '⚠️ MEDIUM' if risk_score >= 25 else '✅ LOW'
    if risk_score > 0:
        embed.add_field(name="Manipulation Risk", value=f"{risk_level} ({risk_score}/100)\n{' · '.join(risk_reasons)}", inline=False)

    # Price z-score from streaming stats
    avg_price = (item['buy_price'] + item['sell_price']) // 2
    z = get_item_z_score(item.get('id', 0), avg_price)
    if abs(z) > 0.5:
        z_label = '🔴 Very Oversold' if z < -2 else '🟠 Oversold' if z < -1 else '🟢 Very Overbought' if z > 2 else '🟡 Overbought' if z > 1 else '⚪ Normal'
        z_detail = f"Z-Score: **{z:+.2f}** — {z_label}"
        if z < -1.5:
            z_detail += "\n💡 Price is significantly below average — potential buy opportunity"
        elif z > 1.5:
            z_detail += "\n💡 Price is significantly above average — consider selling"
        embed.add_field(name="📊 Price Position", value=z_detail, inline=False)

    # Tax efficiency for expensive items
    sell_price = item.get('sell_price', 0)
    if sell_price >= 250000000:
        eff_tax = round(5000000 / sell_price * 100, 2)
        embed.add_field(name="💰 Tax Advantage", value=f"Tax capped at 5M (effective {eff_tax}% vs 2%)", inline=False)

    # Quick verdict explanation
    if verdict == "Dead":
        embed.add_field(name="⚠️ Warning", value="Almost nobody is trading this item. Don't expect your offers to fill.", inline=False)
    elif verdict == "Slow":
        embed.add_field(name="💤 Note", value="Low activity. Your offers might take a while to fill.", inline=False)

    # Stickiness trap warning
    if margin_pct > 5 and vph < 12:
        embed.add_field(name="🪤 STICKINESS TRAP", value="Great margin but near-zero volume. Your GP will be stuck — avoid this flip.", inline=False)

    embed.set_footer(text="Grand Flip Out | Based on real trading volume")
    embed.timestamp = datetime.now()
    await interaction.followup.send(embed=embed)


@bot.tree.command(name="market", description="Show overall market summary")
async def market_command(interaction: discord.Interaction):
    await interaction.response.defer()
    s = api_client.get_market_summary()
    embed = discord.Embed(title="OSRS Market Summary", color=OSRS_GOLD)
    embed.add_field(name="Total 5min Volume", value=f"{s['total_volume']:,}", inline=True)
    embed.add_field(name="Average Margin", value=format_gp(s['avg_margin']), inline=True)
    embed.add_field(name="Active Items", value=f"{s['active_items']:,}", inline=True)
    t = s.get('top_item_by_margin')
    if t:
        embed.add_field(name="Highest Margin", value=f"**{t['name']}**: {format_gp(t['margin'])}", inline=False)
    embed.set_footer(text="Grand Flip Out | OSRS Wiki API")
    embed.timestamp = datetime.now()
    await interaction.followup.send(embed=embed)


@bot.tree.command(name="momentum", description="Show items with the strongest price momentum right now")
@app_commands.describe(direction="rising or falling (default: rising)")
@app_commands.checks.cooldown(1, 5.0, key=lambda i: (i.guild_id, i.user.id))
async def momentum_command(interaction: discord.Interaction, direction: str = "rising"):
    await interaction.response.defer()
    direction = direction.lower() if direction else "rising"
    is_rising = direction != "falling"

    if not api_client.latest_prices or not api_client.prev_prices:
        await interaction.followup.send("Still collecting price data. Try again in a minute.")
        return

    momentum_items = []
    for item_id, prices in api_client.latest_prices.items():
        prev_avg = api_client.prev_prices.get(item_id, 0)
        if not prev_avg or prev_avg == 0:
            continue
        meta = api_client.item_mapping.get(item_id, {})
        name = meta.get('name', f'Item {item_id}')
        if any(junk in name.lower() for junk in api_client.JUNK_KEYWORDS):
            continue

        high = prices.get('high', 0) or 0
        low = prices.get('low', 0) or 0
        if high <= 0 or low <= 0:
            continue
        current_avg = (high + low) // 2
        if current_avg < 5000:
            continue

        pct_change = ((current_avg - prev_avg) / prev_avg) * 100

        # Volume check
        vols = api_client.volume_data.get(item_id, {})
        total_vol = (vols.get('buyVol', 0) or 0) + (vols.get('sellVol', 0) or 0)
        if total_vol < 1:
            continue

        # Buy/sell pressure
        bv = vols.get('buyVol', 0) or 0
        sv = vols.get('sellVol', 0) or 0
        buy_pct = round(bv / (bv + sv) * 100) if (bv + sv) > 0 else 50

        if (is_rising and pct_change > 0.5) or (not is_rising and pct_change < -0.5):
            momentum_items.append({
                'name': name,
                'pct_change': round(pct_change, 2),
                'current': current_avg,
                'prev': prev_avg,
                'volume': total_vol * 12,
                'buy_pressure': buy_pct
            })

    if is_rising:
        momentum_items.sort(key=lambda x: x['pct_change'], reverse=True)
    else:
        momentum_items.sort(key=lambda x: x['pct_change'])

    top = momentum_items[:15]
    if not top:
        await interaction.followup.send(f"No items with strong {'upward' if is_rising else 'downward'} momentum right now.")
        return

    def format_momentum(i, item):
        arrow = '📈' if item['pct_change'] > 0 else '📉'
        pressure = '🟢' if item['buy_pressure'] > 60 else '🔴' if item['buy_pressure'] < 40 else '⚪'
        return (
            f"{arrow} {item['name']}",
            f"**{'+' if item['pct_change'] > 0 else ''}{item['pct_change']}%** ({format_gp(item['prev'])} → {format_gp(item['current'])})\n"
            f"Vol: ~{item['volume']:,}/hr · {pressure} Buy pressure: {item['buy_pressure']}%"
        )

    title = f"{'📈 Rising' if is_rising else '📉 Falling'} Momentum — Price Movers"
    pages = build_paginated_embeds(top, title, per_page=5, format_fn=format_momentum)
    if len(pages) > 1:
        view = PaginatedView(pages, interaction.user.id)
        await interaction.followup.send(embed=pages[0], view=view)
    else:
        await interaction.followup.send(embed=pages[0])


@bot.tree.command(name="risk", description="Check manipulation risk and fill rate for an item")
@app_commands.describe(item_name="Name of the item to check")
@app_commands.autocomplete(item_name=item_name_autocomplete)
@app_commands.checks.cooldown(1, 3.0, key=lambda i: (i.guild_id, i.user.id))
async def risk_command(interaction: discord.Interaction, item_name: str):
    await interaction.response.defer()
    item = api_client.find_item_by_name(item_name)
    if not item:
        await interaction.followup.send(f"Couldn't find item matching '{item_name}'.")
        return

    vph = vol_per_hour(item)
    margin_pct = (item['margin'] / item['buy_price'] * 100) if item['buy_price'] > 0 else 0
    ge_limit = item.get('ge_limit', 0)

    # Manipulation risk
    risk_score = 0
    risk_reasons = []
    if margin_pct > 15 and vph < 24: risk_score += 35; risk_reasons.append('Very high margin + very low volume')
    elif margin_pct > 10 and vph < 60: risk_score += 20; risk_reasons.append('Elevated margin/vol ratio')
    if ge_limit and ge_limit <= 8: risk_score += 15; risk_reasons.append(f'Very low GE limit ({ge_limit})')
    elif ge_limit and ge_limit <= 25: risk_score += 8; risk_reasons.append(f'Low GE limit ({ge_limit})')

    # Check for price anomaly
    prev_avg = api_client.prev_prices.get(item['id'], 0)
    if prev_avg > 0:
        current_avg = (item['buy_price'] + item['sell_price']) // 2
        pct_change = abs((current_avg - prev_avg) / prev_avg * 100)
        if pct_change > 15: risk_score += 25; risk_reasons.append(f'Price moved {pct_change:.0f}% recently')
        elif pct_change > 8: risk_score += 15; risk_reasons.append(f'Price moved {pct_change:.0f}%')

    risk_level = '🚨 HIGH RISK' if risk_score >= 60 else '⚠️ MEDIUM RISK' if risk_score >= 35 else '✅ LOW RISK'
    risk_color = 0xFF4444 if risk_score >= 60 else 0xFFAA00 if risk_score >= 35 else 0x22C55E

    embed = discord.Embed(title=f"{risk_level} — {item['name']}", color=risk_color)
    embed.set_thumbnail(url=get_item_icon_url(item['id'], item['name'], item.get('icon', '')))

    embed.add_field(name="Risk Score", value=f"**{min(100, risk_score)}/100**", inline=True)
    embed.add_field(name="Margin", value=f"{margin_pct:.1f}%", inline=True)
    embed.add_field(name="Volume", value=format_vol_per_hour(item), inline=True)

    # Fill rate
    rb = realistic_buys(item)
    fill_likelihood = min(100, round((vph * 4 / max(1, rb)) * 100)) if vph > 0 else 0
    fill_label = '🟢 Instant' if fill_likelihood >= 90 else '🟡 Fast' if fill_likelihood >= 60 else '🟠 Slow' if fill_likelihood >= 30 else '🔴 Very slow'
    embed.add_field(name="Fill Rate", value=f"{fill_label} ({fill_likelihood}%)", inline=True)

    # Stickiness check
    if margin_pct > 5 and vph < 12:
        embed.add_field(name="🪤 STICKINESS TRAP", value="Great margin but near-zero volume. Your GP will be stuck in the GE. Avoid this flip.", inline=False)

    if risk_reasons:
        embed.add_field(name="Risk Factors", value='\n'.join(f'• {r}' for r in risk_reasons), inline=False)

    # Tax info
    sell_price = item.get('sell_price', 0)
    if sell_price >= 250000000:
        eff_tax = round(5000000 / sell_price * 100, 2)
        embed.add_field(name="💰 Tax Advantage", value=f"Tax capped at 5M GP (effective {eff_tax}% vs 2%)", inline=False)

    embed.set_footer(text="Grand Flip Out | Risk analysis based on volume, margins, and price history")
    embed.timestamp = datetime.now()
    await interaction.followup.send(embed=embed)


@bot.tree.command(name="alch", description="Find the most profitable items to High Alchemy right now")
@app_commands.describe(sort_by="How to sort results (default: profit)")
@app_commands.choices(sort_by=[
    app_commands.Choice(name="Profit per cast", value="profit"),
    app_commands.Choice(name="GP per hour", value="gp_hr"),
    app_commands.Choice(name="ROI %", value="roi"),
])
async def alch_command(interaction: discord.Interaction, sort_by: str = "profit"):
    """Find items worth more when alched than sold on GE."""
    await interaction.response.defer()

    if not api_client.latest_prices or not api_client.item_mapping:
        await interaction.followup.send("Still loading price data... try again in a moment.")
        return

    # Find nature rune price
    nat_price = 120  # default
    for item_id, meta in api_client.item_mapping.items():
        if meta.get('name', '').lower() == 'nature rune':
            prices = api_client.latest_prices.get(int(item_id), {})
            if prices.get('high', 0) > 0:
                nat_price = prices['high']
            break

    alch_items = []
    for item_id, meta in api_client.item_mapping.items():
        if is_excluded(meta.get('name', '')):
            continue
        alch_value = meta.get('highalch', 0)
        if not alch_value or alch_value <= 0:
            continue

        iid = int(item_id)
        prices = api_client.latest_prices.get(iid, {})
        buy_price = prices.get('high', 0)  # instant-buy price
        if buy_price <= 0:
            continue

        profit = alch_value - buy_price - nat_price
        if profit <= 0:
            continue

        gp_hr = profit * 1200
        roi = (profit / buy_price) * 100 if buy_price > 0 else 0

        # Volume data
        vol_data = api_client.volume_data.get(iid, {})
        vol = (vol_data.get('highPriceVolume', 0) or 0) + (vol_data.get('lowPriceVolume', 0) or 0)

        alch_items.append({
            'name': meta.get('name', f'Item {item_id}'),
            'buy_price': buy_price,
            'alch_value': alch_value,
            'profit': profit,
            'gp_hr': gp_hr,
            'roi': roi,
            'volume': vol,
            'buy_limit': meta.get('limit', 0),
            'icon': meta.get('icon', ''),
        })

    if not alch_items:
        await interaction.followup.send("No profitable alchemy items found right now.")
        return

    # Sort
    if sort_by == "gp_hr":
        alch_items.sort(key=lambda x: x['gp_hr'], reverse=True)
    elif sort_by == "roi":
        alch_items.sort(key=lambda x: x['roi'], reverse=True)
    else:
        alch_items.sort(key=lambda x: x['profit'], reverse=True)

    def format_alch(idx, item):
        vol_label = "🟢" if item['volume'] >= 100 else "🟡" if item['volume'] >= 20 else "🔴"
        limit_str = f" | Limit: {item['buy_limit']:,}" if item['buy_limit'] else ""
        name_line = f"**{idx}. {item['name']}**"
        value_line = (
            f"Buy: {format_gp(item['buy_price'])} → Alch: {format_gp(item['alch_value'])}\n"
            f"Profit: **{format_gp(item['profit'])}/cast** ({format_gp(item['gp_hr'])}/hr) | ROI: {item['roi']:.1f}%\n"
            f"{vol_label} Vol: {item['volume']:,}{limit_str}"
        )
        return (name_line, value_line)

    pages = build_paginated_embeds(
        alch_items[:40],
        f"🔥 Alchemy Scanner (Nature Rune: {format_gp(nat_price)})",
        per_page=5,
        format_fn=format_alch
    )
    for p in pages:
        p.color = OSRS_GOLD

    if len(pages) > 1:
        view = PaginatedView(pages, interaction.user.id)
        await interaction.followup.send(embed=pages[0], view=view)
    else:
        await interaction.followup.send(embed=pages[0])


@bot.tree.command(name="setscan", description="Find item set arbitrage opportunities (buy set vs individual pieces)")
async def setscan_command(interaction: discord.Interaction):
    """Detect profitable set item combinations — buy set, break into pieces (or vice versa)."""
    await interaction.response.defer()

    if not api_client.latest_prices or not api_client.item_mapping:
        await interaction.followup.send("Still loading price data... try again in a moment.")
        return

    # Known OSRS item sets and their components
    # Format: (set_name, set_item_search, [component_searches])
    KNOWN_SETS = [
        ("Dharok's", "dharok's armour set", ["dharok's helm", "dharok's platebody", "dharok's platelegs", "dharok's greataxe"]),
        ("Guthan's", "guthan's armour set", ["guthan's helm", "guthan's platebody", "guthan's chainskirt", "guthan's warspear"]),
        ("Verac's", "verac's armour set", ["verac's helm", "verac's brassard", "verac's plateskirt", "verac's flail"]),
        ("Torag's", "torag's armour set", ["torag's helm", "torag's platebody", "torag's platelegs", "torag's hammers"]),
        ("Ahrim's", "ahrim's armour set", ["ahrim's hood", "ahrim's robetop", "ahrim's robeskirt", "ahrim's staff"]),
        ("Karil's", "karil's armour set", ["karil's coif", "karil's leathertop", "karil's leatherskirt", "karil's crossbow"]),
        ("Justiciar", "justiciar armour set", ["justiciar faceguard", "justiciar chestguard", "justiciar legguards"]),
        ("Inquisitor's", "inquisitor's armour set", ["inquisitor's great helm", "inquisitor's hauberk", "inquisitor's plateskirt"]),
        ("Dragon", "dragon armour set (lg)", ["dragon med helm", "dragon platebody", "dragon platelegs"]),
        ("Rune (lg)", "rune armour set (lg)", ["rune full helm", "rune platebody", "rune platelegs", "rune kiteshield"]),
        ("Adamant (lg)", "adamant armour set (lg)", ["adamant full helm", "adamant platebody", "adamant platelegs", "adamant kiteshield"]),
    ]

    # Build name -> (item_id, price) lookup
    name_lookup = {}
    for item_id, meta in api_client.item_mapping.items():
        name_lower = meta.get('name', '').lower()
        iid = int(item_id)
        prices = api_client.latest_prices.get(iid, {})
        buy = prices.get('high', 0)
        sell = prices.get('low', 0)
        if buy > 0 and sell > 0:
            name_lookup[name_lower] = {'id': iid, 'buy': buy, 'sell': sell, 'name': meta.get('name', '')}

    opportunities = []
    for set_name, set_search, components in KNOWN_SETS:
        set_data = name_lookup.get(set_search.lower())
        if not set_data:
            continue

        # Find all components
        comp_data = []
        all_found = True
        for comp in components:
            cd = name_lookup.get(comp.lower())
            if not cd:
                all_found = False
                break
            comp_data.append(cd)

        if not all_found:
            continue

        set_buy = set_data['buy']
        set_sell = set_data['sell']
        parts_buy_total = sum(c['buy'] for c in comp_data)
        parts_sell_total = sum(c['sell'] for c in comp_data)

        # Strategy 1: Buy set, break into parts, sell individually
        break_profit = parts_sell_total - set_buy
        # Strategy 2: Buy parts individually, combine into set, sell set
        combine_profit = set_sell - parts_buy_total

        # Apply 2% GE tax (capped at 5M) to the selling side
        break_tax = sum(min(math.floor(c['sell'] * 0.02), 5000000) for c in comp_data)
        combine_tax = min(math.floor(set_sell * 0.02), 5000000)

        break_profit_after_tax = break_profit - break_tax
        combine_profit_after_tax = combine_profit - combine_tax

        best_profit = max(break_profit_after_tax, combine_profit_after_tax)
        best_strategy = "Break Set" if break_profit_after_tax >= combine_profit_after_tax else "Combine Parts"

        if best_profit > 0:
            opportunities.append({
                'set_name': set_name,
                'strategy': best_strategy,
                'profit': best_profit,
                'set_buy': set_buy,
                'set_sell': set_sell,
                'parts_buy': parts_buy_total,
                'parts_sell': parts_sell_total,
                'tax': break_tax if best_strategy == "Break Set" else combine_tax,
                'components': [c['name'] for c in comp_data],
            })

    if not opportunities:
        embed = discord.Embed(
            title="📦 Set Scanner",
            description="No profitable set arbitrage opportunities found right now.\n\nSet prices fluctuate — check back later!",
            color=OSRS_GOLD
        )
        await interaction.followup.send(embed=embed)
        return

    opportunities.sort(key=lambda x: x['profit'], reverse=True)

    def format_set(idx, opp):
        strat_icon = "🔨" if opp['strategy'] == "Break Set" else "📦"
        name_line = f"**{idx}. {opp['set_name']}** — {strat_icon} {opp['strategy']}"
        value_line = (
            f"Set Price: Buy {format_gp(opp['set_buy'])} / Sell {format_gp(opp['set_sell'])}\n"
            f"Parts Total: Buy {format_gp(opp['parts_buy'])} / Sell {format_gp(opp['parts_sell'])}\n"
            f"**Profit: {format_gp(opp['profit'])}** (after {format_gp(opp['tax'])} tax)\n"
            f"Parts: {', '.join(opp['components'])}"
        )
        return (name_line, value_line)

    pages = build_paginated_embeds(
        opportunities,
        "📦 Set Arbitrage Scanner",
        per_page=3,
        format_fn=format_set
    )
    for p in pages:
        p.color = OSRS_GOLD
        p.set_footer(text="Buy set → break into parts, or buy parts → combine. Visit GE clerk to exchange.")

    if len(pages) > 1:
        view = PaginatedView(pages, interaction.user.id)
        await interaction.followup.send(embed=pages[0], view=view)
    else:
        await interaction.followup.send(embed=pages[0])


@bot.tree.command(name="compare", description="Compare two items side by side")
@app_commands.describe(item1="First item", item2="Second item")
@app_commands.autocomplete(item1=item_name_autocomplete, item2=item_name_autocomplete)
async def compare_command(interaction: discord.Interaction, item1: str, item2: str):
    await interaction.response.defer()
    d1, d2 = api_client.find_item_by_name(item1), api_client.find_item_by_name(item2)
    if not d1 or not d2:
        await interaction.followup.send("Couldn't find one or both items.")
        return
    embed = discord.Embed(title=f"{d1['name']} vs {d2['name']}", color=OSRS_GOLD)
    embed.add_field(name="💰 Buy Price", value=f"{d1['name']}: **{format_gp(d1['buy_price'])}**\n{d2['name']}: **{format_gp(d2['buy_price'])}**", inline=True)
    embed.add_field(name="💰 Sell Price", value=f"{d1['name']}: **{format_gp(d1['sell_price'])}**\n{d2['name']}: **{format_gp(d2['sell_price'])}**", inline=True)
    embed.add_field(name="Profit/Item", value=f"{d1['name']}: **{format_gp(d1['margin'])}**\n{d2['name']}: **{format_gp(d2['margin'])}**", inline=True)
    rp1, rp2 = realistic_4h_profit(d1), realistic_4h_profit(d2)
    rb1, rb2 = realistic_buys(d1), realistic_buys(d2)
    v1, v2 = get_verdict(d1), get_verdict(d2)
    embed.add_field(name="Verdict", value=f"{d1['name']}: {VERDICT_EMOJI.get(v1,'')} {v1}\n{d2['name']}: {VERDICT_EMOJI.get(v2,'')} {v2}", inline=True)
    embed.add_field(name="4h Profit", value=f"{d1['name']}: **{format_gp(rp1)}** ({rb1:,} buyable)\n{d2['name']}: **{format_gp(rp2)}** ({rb2:,} buyable)", inline=True)
    embed.add_field(name="📊 Volume", value=f"{d1['name']}: {format_vol_per_hour(d1)}\n{d2['name']}: {format_vol_per_hour(d2)}", inline=True)
    winner = d1['name'] if rp1 > rp2 else d2['name']
    embed.add_field(name="Winner", value=f"**{winner}** makes more realistic profit", inline=False)
    embed.set_footer(text="Grand Flip Out | OSRS Wiki API")
    embed.timestamp = datetime.now()
    await interaction.followup.send(embed=embed)


@bot.tree.command(name="watchlist", description="Manage your personal watchlist")
@app_commands.describe(action="add, remove, or show", item_name="Item name")
@app_commands.autocomplete(item_name=item_name_autocomplete)
async def watchlist_command(interaction: discord.Interaction, action: str = "show", item_name: str = None):
    await interaction.response.defer()
    uid = str(interaction.user.id)
    wl = load_watchlists()
    if uid not in wl: wl[uid] = []
    action = action.lower()
    if action == "show":
        if not wl[uid]:
            await interaction.followup.send("Your watchlist is empty. Use `/watchlist add <item>` to add items!")
            return
        embed = discord.Embed(title=f"{interaction.user.name}'s Watchlist", color=OSRS_GOLD)
        for wn in wl[uid][:20]:
            item = api_client.find_item_by_name(wn)
            if item:
                v = get_verdict(item)
                rp = realistic_4h_profit(item)
                embed.add_field(name=f"{VERDICT_EMOJI.get(v,'')} {wn}", value=f"Buy: **{format_gp(item['buy_price'])}** → Sell: **{format_gp(item['sell_price'])}**\nProfit: {format_gp(item['margin'])}/item → {format_gp(rp)}/4h\nVol: {format_vol_per_hour(item)}", inline=True)
        embed.set_footer(text="Grand Flip Out")
        await interaction.followup.send(embed=embed)
    elif action == "add":
        if not item_name:
            await interaction.followup.send("Specify an item name!")
            return
        item = api_client.find_item_by_name(item_name)
        if not item:
            await interaction.followup.send(f"Couldn't find '{item_name}'.")
            return
        n = item['name']
        if n not in wl[uid]:
            wl[uid].append(n)
            save_watchlists(wl)
            await interaction.followup.send(f"Added **{n}** to your watchlist!")
        else:
            await interaction.followup.send(f"**{n}** is already on your watchlist.")
    elif action == "remove":
        if not item_name:
            await interaction.followup.send("Specify an item name!")
            return
        item = api_client.find_item_by_name(item_name)
        if not item:
            await interaction.followup.send(f"Couldn't find '{item_name}'.")
            return
        n = item['name']
        if n in wl[uid]:
            wl[uid].remove(n)
            save_watchlists(wl)
            await interaction.followup.send(f"Removed **{n}** from your watchlist.")
        else:
            await interaction.followup.send(f"**{n}** is not on your watchlist.")
    else:
        await interaction.followup.send("Use 'add', 'remove', or 'show'.")


@bot.tree.command(name="alert", description="Manage your personal price alerts")
@app_commands.describe(
    action="add, remove, list, clear, or history",
    item="Item name (for add/remove)",
    margin_pct="Minimum margin % (default 2)",
    min_profit="Minimum profit in gp (default 100000)",
    min_volume="Minimum daily volume (default 5000)",
    target_price="Target price to alert below (0 = disabled)"
)
@app_commands.autocomplete(item=item_name_autocomplete)
async def alert_command(
    interaction: discord.Interaction,
    action: str = "list",
    item: Optional[str] = None,
    margin_pct: int = 2,
    min_profit: int = 100000,
    min_volume: int = 5000,
    target_price: int = 0
):
    await interaction.response.defer()
    uid = str(interaction.user.id)
    alerts = load_user_alerts()
    if uid not in alerts:
        alerts[uid] = []

    action = action.lower()

    if action == "list":
        if not alerts[uid]:
            await interaction.followup.send("You don't have any alerts set. Use `/alert add` to create one!")
            return
        embed = discord.Embed(title=f"{interaction.user.name}'s Price Alerts", color=OSRS_BLUE)
        for idx, alert in enumerate(alerts[uid], 1):
            item_name = alert.get('item_name', 'Unknown')
            margin = alert.get('margin_pct', 2)
            min_p = alert.get('min_profit', 100000)
            min_v = alert.get('min_volume', 5000)
            target_p = alert.get('target_price', 0)
            enabled = "✓" if alert.get('enabled', True) else "✗"

            conditions = f"Margin ≥ {margin}% | Profit ≥ {format_gp(min_p)} | Volume ≥ {min_v:,}/day"
            if target_p > 0:
                conditions += f" | Alert if below {format_gp(target_p)}"
            embed.add_field(name=f"{idx}. {enabled} {item_name}", value=conditions, inline=False)
        embed.set_footer(text="Use /alert remove <item> to delete")
        await interaction.followup.send(embed=embed)

    elif action == "add":
        if not item:
            await interaction.followup.send("Specify an item name!")
            return
        item_data = api_client.find_item_by_name(item)
        if not item_data:
            await interaction.followup.send(f"Couldn't find item '{item}'.")
            return

        item_name = item_data['name']
        item_id = item_data['id']

        # Check if already exists
        for alert in alerts[uid]:
            if alert.get('item_id') == item_id:
                await interaction.followup.send(f"You already have an alert for **{item_name}**. Remove it first with `/alert remove`.")
                return

        new_alert = {
            'item_name': item_name,
            'item_id': item_id,
            'margin_pct': max(0, margin_pct),
            'min_profit': max(0, min_profit),
            'min_volume': max(0, min_volume),
            'target_price': max(0, target_price),
            'enabled': True,
            'created_at': datetime.now().isoformat(),
            'last_triggered': None
        }
        alerts[uid].append(new_alert)
        save_user_alerts(alerts)

        summary = f"Margin ≥ {margin_pct}% | Profit ≥ {format_gp(min_profit)} | Volume ≥ {min_volume:,}/day"
        if target_price > 0:
            summary += f" | Alert if below {format_gp(target_price)}"

        await interaction.followup.send(f"✓ Alert added for **{item_name}**\nConditions: {summary}\nYou'll get DMs when matches are found!")

    elif action == "remove":
        if not item:
            await interaction.followup.send("Specify an item name!")
            return
        item_data = api_client.find_item_by_name(item)
        if not item_data:
            await interaction.followup.send(f"Couldn't find item '{item}'.")
            return

        item_id = item_data['id']
        original_len = len(alerts[uid])
        alerts[uid] = [a for a in alerts[uid] if a.get('item_id') != item_id]

        if len(alerts[uid]) < original_len:
            save_user_alerts(alerts)
            await interaction.followup.send(f"Removed alert for **{item_data['name']}**.")
        else:
            await interaction.followup.send(f"You don't have an alert for **{item_data['name']}**.")

    elif action == "clear":
        if alerts[uid]:
            count = len(alerts[uid])
            alerts[uid] = []
            save_user_alerts(alerts)
            await interaction.followup.send(f"Cleared {count} alert(s).")
        else:
            await interaction.followup.send("You don't have any alerts to clear.")

    elif action == "history":
        history = _get_alert_history()
        if not history:
            await interaction.followup.send("No recent channel alerts.")
            return
        embed = discord.Embed(title="Recent Channel Alerts", color=OSRS_GOLD)
        for idx, record in enumerate(history[:10], 1):
            timestamp = record.get('timestamp', 'Unknown')
            item_name = record.get('item_name', 'Unknown')
            buy_price = record.get('buy_price', 0)
            sell_price = record.get('sell_price', 0)
            embed.add_field(
                name=f"{idx}. {item_name}",
                value=f"Buy: {format_gp(buy_price)} → Sell: {format_gp(sell_price)}\n{timestamp}",
                inline=False
            )
        embed.set_footer(text="Grand Flip Out")
        await interaction.followup.send(embed=embed)

    else:
        await interaction.followup.send("Use 'add', 'remove', 'list', 'clear', or 'history'.")


@bot.tree.command(name="sinks", description="Show items with high sell volume (GP sinks)")
async def sinks_command(interaction: discord.Interaction):
    await interaction.response.defer()
    sinks = []
    for item_id in api_client.latest_prices:
        vols = api_client.volume_data.get(item_id, {})
        bv, sv = vols.get('buyVol', 0), vols.get('sellVol', 0)
        if sv > bv * 1.5 and sv > 50:
            meta = api_client.item_mapping.get(item_id, {})
            sinks.append({'name': meta.get('name', f'Item {item_id}'), 'buy_volume': bv, 'sell_volume': sv})
    sinks.sort(key=lambda x: x['sell_volume'], reverse=True)
    if not sinks[:10]:
        await interaction.followup.send("No major GP sinks detected.")
        return
    embed = discord.Embed(title="GP Sinks", color=OSRS_RED)
    for item in sinks[:10]:
        embed.add_field(name=f"💸 {item['name']}", value=f"Buy: {item['buy_volume']:,} | Sell: {item['sell_volume']:,}", inline=False)
    embed.set_footer(text="Grand Flip Out | OSRS Wiki API")
    embed.timestamp = datetime.now()
    await interaction.followup.send(embed=embed)


@bot.tree.command(name="website", description="Get the Grand Flip Out website link")
async def website_command(interaction: discord.Interaction):
    embed = discord.Embed(title="Grand Flip Out - Website", color=OSRS_GOLD, description="Live GE prices, realistic profit estimates, and honest flipping data.")
    embed.add_field(name="Open Website", value=f"[Click here]({WEBSITE_URL})", inline=False)
    embed.set_footer(text="Grand Flip Out")
    embed.timestamp = datetime.now()
    await interaction.response.send_message(embed=embed)


@bot.tree.command(name="invite", description="Get the bot invite link")
async def invite_command(interaction: discord.Interaction):
    embed = discord.Embed(title="Invite Grand Flip Out", color=OSRS_GOLD)
    embed.add_field(name="Bot Invite", value=f"[Click to invite]({BOT_INVITE_URL})", inline=False)
    embed.add_field(name="Website", value=f"[Open Grand Flip Out]({WEBSITE_URL})", inline=False)
    embed.set_footer(text="Buying gf 10k | Grand Flip Out")
    embed.timestamp = datetime.now()
    await interaction.response.send_message(embed=embed)


@bot.tree.command(name="setup", description="Show bot info and configuration")
async def setup_command(interaction: discord.Interaction):
    await interaction.response.defer()

    server_up = await backend.is_server_up()
    server_status = "🟢 Online" if server_up else "🔴 Offline"
    data_source = "Backend Server + Wiki API" if server_up else "OSRS Wiki API (direct)"

    embed = discord.Embed(title="Grand Flip Out - Setup Info", color=OSRS_GOLD)
    embed.add_field(name="Data Source", value=data_source, inline=True)
    embed.add_field(name="Items Tracked", value=f"{len(api_client.latest_prices):,}", inline=True)
    embed.add_field(name="Bot Version", value="2.2.0", inline=True)
    embed.add_field(name="Backend Server", value=server_status, inline=True)
    embed.add_field(name="WebSocket", value="Connected" if backend.ws and not backend.ws.closed else "Disconnected", inline=True)
    embed.add_field(name="Website", value=f"[Open]({WEBSITE_URL})", inline=True)
    embed.add_field(name="Invite", value=f"[Invite]({BOT_INVITE_URL})", inline=True)

    if server_up:
        # Pull live status from backend
        status_data = await backend.get('/status')
        if status_data:
            scanner = status_data.get('scanner', {})
            embed.add_field(name="Server Items", value=f"{scanner.get('itemsTracked', 0):,}", inline=True)
            embed.add_field(name="Server Uptime", value=f"{int(status_data.get('uptime', 0) / 60)}m", inline=True)

    embed.add_field(name="Commands", value="/price /top /dumps /pumps /recipe /analyze /market /compare /watchlist /alert /sinks /dashboard /website /invite /setup", inline=False)
    embed.set_footer(text="Grand Flip Out v2.2")
    embed.timestamp = datetime.now()
    await interaction.followup.send(embed=embed)


@bot.tree.command(name="dashboard", description="Get the link to the live terminal dashboard")
async def dashboard_command(interaction: discord.Interaction):
    server_up = await backend.is_server_up()
    embed = discord.Embed(title="Grand Flip Out — Terminal Dashboard", color=OSRS_GOLD)

    if server_up:
        embed.description = "Bloomberg-style terminal with live WebSocket data, 8 tabs, full JTI analysis."
        embed.add_field(name="Open Dashboard", value=f"[Launch Terminal]({API_URL})", inline=False)
        embed.add_field(name="Status", value="🟢 Server Online — Real-time data active", inline=False)
    else:
        embed.description = "The backend server is currently offline. Start it to access the terminal dashboard."
        embed.add_field(name="How to Start", value=f"```\ncd server\nnpm start\n```\nThen visit: {API_URL}", inline=False)
        embed.add_field(name="Status", value="🔴 Server Offline", inline=False)

    embed.add_field(name="Website (always on)", value=f"[Open Website]({WEBSITE_URL})", inline=False)
    embed.set_footer(text="Grand Flip Out v2.2")
    embed.timestamp = datetime.now()
    await interaction.response.send_message(embed=embed)


@bot.tree.command(name="flip", description="Calculate flip profit for an item with quantity")
@app_commands.describe(
    item_name="Name of the item to flip",
    quantity="How many to flip (default: buy limit)",
    buy_price="Custom buy price (default: current instant buy)",
    sell_price="Custom sell price (default: current instant sell)"
)
@app_commands.autocomplete(item_name=item_name_autocomplete)
@app_commands.checks.cooldown(1, 3.0, key=lambda i: (i.guild_id, i.user.id))
async def flip_command(interaction: discord.Interaction, item_name: str, quantity: Optional[int] = None, buy_price: Optional[int] = None, sell_price: Optional[int] = None):
    await interaction.response.defer()
    item = api_client.find_item_by_name(item_name)
    if not item:
        await interaction.followup.send(f"Couldn't find item matching '{item_name}'. Try a different name.")
        return

    data = api_client._build_item_data(item['id'])
    if not data or data['buy_price'] == 0:
        await interaction.followup.send(f"No price data for **{item['name']}** right now.")
        return

    bp = buy_price or data['buy_price']
    sp = sell_price or data['sell_price']
    buy_limit = data.get('ge_limit', 0) or 100
    qty = quantity or buy_limit

    # GE tax: 2% of sell price, capped at 5M per item
    tax_per_item = min(int(sp * 0.02), 5_000_000)
    profit_per_item = sp - bp - tax_per_item
    total_profit = profit_per_item * qty
    total_cost = bp * qty
    roi = (total_profit / total_cost * 100) if total_cost > 0 else 0

    # Estimate fill time based on volume
    vol_per_hour = 0
    vols = api_client.volume_data.get(item['id'], {})
    total_vol = (vols.get('buyVol', 0) or 0) + (vols.get('sellVol', 0) or 0)
    vol_per_hour = total_vol * 12  # 5min sample × 12

    fill_str = "Unknown"
    if vol_per_hour > 0:
        fill_hours = qty / vol_per_hour
        if fill_hours < 1:
            fill_str = f"~{int(fill_hours * 60)} min"
        else:
            fill_str = f"~{fill_hours:.1f} hrs"

    color = OSRS_GREEN if profit_per_item > 0 else OSRS_RED
    embed = discord.Embed(title=f"Flip Calculator — {item['name']}", color=color)
    embed.set_thumbnail(url=get_item_icon_url(item['id'], item.get('name', '')))
    embed.add_field(name="Buy Price", value=f"{bp:,} gp", inline=True)
    embed.add_field(name="Sell Price", value=f"{sp:,} gp", inline=True)
    embed.add_field(name="GE Tax", value=f"{tax_per_item:,} gp/ea", inline=True)
    embed.add_field(name="Profit/Item", value=f"{profit_per_item:,} gp", inline=True)
    embed.add_field(name="Quantity", value=f"{qty:,}", inline=True)
    embed.add_field(name="Est. Fill Time", value=fill_str, inline=True)
    embed.add_field(name="Total Profit", value=f"**{total_profit:,} gp**", inline=True)
    embed.add_field(name="Capital Required", value=f"{total_cost:,} gp", inline=True)
    embed.add_field(name="ROI", value=f"{roi:.1f}%", inline=True)

    if total_profit > 0 and vol_per_hour > 0:
        gp_per_hour = int(total_profit / max(qty / vol_per_hour, 0.25))
        embed.add_field(name="GP/Hour", value=f"~{gp_per_hour:,} gp/hr", inline=False)

    embed.set_footer(text="Grand Flip Out v2.2 • Prices from OSRS Wiki")
    embed.timestamp = datetime.now()
    await interaction.followup.send(embed=embed)


@bot.tree.command(name="tax", description="Calculate GE tax for an item or price")
@app_commands.describe(
    item_name="Item name (or just type a number for raw price)",
    quantity="How many items (default: 1)"
)
@app_commands.autocomplete(item_name=item_name_autocomplete)
@app_commands.checks.cooldown(1, 3.0, key=lambda i: (i.guild_id, i.user.id))
async def tax_command(interaction: discord.Interaction, item_name: str, quantity: Optional[int] = 1):
    await interaction.response.defer()
    qty = max(1, quantity or 1)

    # Check if item_name is a raw number (price input)
    try:
        raw_price = int(item_name.replace(',', '').replace('k', '000').replace('m', '000000'))
        tax_per = min(int(raw_price * 0.02), 5_000_000)
        total_tax = tax_per * qty
        after_tax = (raw_price - tax_per) * qty
        embed = discord.Embed(title="GE Tax Calculator", color=OSRS_GOLD)
        embed.add_field(name="Sell Price", value=f"{raw_price:,} gp", inline=True)
        embed.add_field(name="Tax/Item", value=f"{tax_per:,} gp (2%)", inline=True)
        embed.add_field(name="Quantity", value=f"{qty:,}", inline=True)
        embed.add_field(name="Total Tax", value=f"**{total_tax:,} gp**", inline=True)
        embed.add_field(name="You Receive", value=f"**{after_tax:,} gp**", inline=True)
        if raw_price >= 250_000_000:
            embed.set_footer(text="Tax capped at 5M gp per item")
        else:
            embed.set_footer(text="Grand Flip Out v2.2")
        embed.timestamp = datetime.now()
        await interaction.followup.send(embed=embed)
        return
    except ValueError:
        pass

    # Look up item by name
    item = api_client.find_item_by_name(item_name)
    if not item:
        await interaction.followup.send(f"Couldn't find item matching '{item_name}'. You can also type a number like `/tax 1000000`.")
        return

    data = api_client._build_item_data(item['id'])
    if not data:
        await interaction.followup.send(f"No price data for **{item['name']}** right now.")
        return

    sp = data['sell_price']
    tax_per = min(int(sp * 0.02), 5_000_000)
    total_tax = tax_per * qty
    profit_after = (sp - tax_per) * qty

    embed = discord.Embed(title=f"GE Tax — {item['name']}", color=OSRS_GOLD)
    embed.set_thumbnail(url=get_item_icon_url(item['id'], item.get('name', '')))
    embed.add_field(name="Sell Price", value=f"{sp:,} gp", inline=True)
    embed.add_field(name="Tax/Item", value=f"{tax_per:,} gp (2%)", inline=True)
    embed.add_field(name="Quantity", value=f"{qty:,}", inline=True)
    embed.add_field(name="Total Tax", value=f"**{total_tax:,} gp**", inline=True)
    embed.add_field(name="You Receive", value=f"**{profit_after:,} gp**", inline=True)
    if sp >= 250_000_000:
        embed.add_field(name="Note", value="Tax capped at 5M gp per item", inline=False)
    embed.set_footer(text="Grand Flip Out v2.2 • 2% tax, 5M cap")
    embed.timestamp = datetime.now()
    await interaction.followup.send(embed=embed)


@bot.tree.command(name="hotlist", description="Show the most profitable items from this session")
@app_commands.checks.cooldown(1, 5.0, key=lambda i: (i.guild_id, i.user.id))
async def hotlist_command(interaction: discord.Interaction):
    await interaction.response.defer()

    if not api_client.latest_prices:
        await interaction.followup.send("Still loading price data. Try again in a moment.")
        return

    # Build item list with profit calculations
    hot_items = []
    for item_id, prices in api_client.latest_prices.items():
        info = api_client.item_mapping.get(item_id)
        if not info:
            continue
        high = prices.get('high', 0) or 0
        low = prices.get('low', 0) or 0
        if high <= 0 or low <= 0 or high <= low:
            continue
        tax = min(int(high * 0.02), 5_000_000)
        margin = high - low - tax
        if margin <= 0:
            continue

        # Volume check
        vols = api_client.volume_data.get(item_id, {})
        total_vol = (vols.get('buyVol', 0) or 0) + (vols.get('sellVol', 0) or 0)
        vol_per_hour = total_vol * 12

        buy_limit = info.get('limit', 100) or 100
        realistic_buys = min(buy_limit, int(vol_per_hour * 4)) if vol_per_hour > 0 else min(buy_limit, 10)
        profit_4h = margin * realistic_buys

        if profit_4h > 10000:  # Min 10k profit threshold
            hot_items.append({
                'name': info.get('name', f'Item {item_id}'),
                'id': item_id,
                'margin': margin,
                'profit_4h': profit_4h,
                'vol_hr': vol_per_hour,
                'buy': low,
                'sell': high
            })

    hot_items.sort(key=lambda x: x['profit_4h'], reverse=True)
    top = hot_items[:25]

    if not top:
        await interaction.followup.send("No profitable items found right now. Market might be slow.")
        return

    def format_hot(i, item):
        medal = ['🥇', '🥈', '🥉'][i-1] if i <= 3 else f'**{i}.**'
        vol_str = f"~{item['vol_hr']:,.0f}/hr" if item['vol_hr'] > 0 else "Dead"
        return (
            f"{medal} {item['name']}",
            f"Buy: **{item['buy']:,} gp** → Sell: **{item['sell']:,} gp**\n"
            f"Profit: **{item['margin']:,} gp**/ea → **{item['profit_4h']:,} gp**/4h\n"
            f"Vol: {vol_str}"
        )

    pages = build_paginated_embeds(top, "🔥 Hotlist — Most Profitable Flips", per_page=5, format_fn=format_hot)
    if len(pages) > 1:
        view = PaginatedView(pages, interaction.user.id)
        await interaction.followup.send(embed=pages[0], view=view)
    else:
        await interaction.followup.send(embed=pages[0])


@bot.tree.command(name="suggest", description="Get flip suggestions based on your capital")
@app_commands.describe(capital="Your total GP budget (e.g. 5000000 or 5m)")
@app_commands.checks.cooldown(1, 5.0, key=lambda i: (i.guild_id, i.user.id))
async def suggest_command(interaction: discord.Interaction, capital: str):
    await interaction.response.defer()

    # Parse capital string (supports k/m suffixes)
    try:
        cap_str = capital.lower().replace(',', '').replace(' ', '')
        if cap_str.endswith('m'):
            cap = int(float(cap_str[:-1]) * 1_000_000)
        elif cap_str.endswith('k'):
            cap = int(float(cap_str[:-1]) * 1_000)
        else:
            cap = int(float(cap_str))
    except (ValueError, TypeError):
        await interaction.followup.send(f"Couldn't parse '{capital}' as a number. Try something like `5m`, `500k`, or `5000000`.")
        return

    if cap < 10000:
        await interaction.followup.send("You need at least 10k GP to start flipping. Try farming some goblins first!")
        return

    # Build item list with profit calculations
    suggestions = []
    for item_id, prices in api_client.latest_prices.items():
        info = api_client.item_mapping.get(item_id)
        if not info:
            continue
        high = prices.get('high', 0) or 0
        low = prices.get('low', 0) or 0
        if high <= 0 or low <= 0 or high <= low:
            continue
        if low > cap * 0.5:  # Can't spend more than 50% on one item
            continue

        tax = min(int(high * 0.02), 5_000_000)
        margin = high - low - tax
        if margin <= 0:
            continue

        spread_pct = (margin / low) * 100 if low > 0 else 0
        if spread_pct < 2:  # Min 2% spread after tax to be worth it
            continue

        vols = api_client.volume_data.get(item_id, {})
        total_vol = (vols.get('buyVol', 0) or 0) + (vols.get('sellVol', 0) or 0)
        vol_per_hour = total_vol * 12

        buy_limit = info.get('limit', 100) or 100
        max_qty = min(buy_limit, int(cap * 0.3 / low))  # Max 30% of cap per item
        if vol_per_hour > 0:
            max_qty = min(max_qty, int(vol_per_hour * 4))
        if max_qty <= 0:
            max_qty = 1

        total_profit = margin * max_qty
        total_cost = low * max_qty
        roi = (total_profit / total_cost * 100) if total_cost > 0 else 0

        suggestions.append({
            'name': info.get('name', f'Item {item_id}'),
            'id': item_id,
            'margin': margin,
            'spread': spread_pct,
            'qty': max_qty,
            'cost': total_cost,
            'profit': total_profit,
            'roi': roi,
            'buy': low,
            'sell': high,
            'vol_hr': vol_per_hour
        })

    # Sort by total profit
    suggestions.sort(key=lambda x: x['profit'], reverse=True)
    top = suggestions[:5]

    if not top:
        await interaction.followup.send(f"No good flip suggestions for {cap:,} gp right now. Market might be tight.")
        return

    # Tier label
    tier = "Starter" if cap < 1_000_000 else "Mid-Range" if cap < 10_000_000 else "High Roller" if cap < 100_000_000 else "Whale"

    embed = discord.Embed(
        title=f"💡 Flip Suggestions — {tier} ({cap:,} gp)",
        description=f"Top 5 flips for your budget. Diversify across 3-4 items for best results.",
        color=OSRS_GOLD
    )

    total_potential = 0
    for i, item in enumerate(top, 1):
        medal = ['🥇', '🥈', '🥉', '4️⃣', '5️⃣'][i-1]
        vol_str = f"{item['vol_hr']:,.0f}/hr" if item['vol_hr'] > 0 else "Low"
        embed.add_field(
            name=f"{medal} {item['name']}",
            value=(
                f"Buy **{item['qty']:,}x** @ {item['buy']:,} = **{item['cost']:,}** gp\n"
                f"Margin: {item['margin']:,}/ea · Spread: {item['spread']:.1f}%\n"
                f"Profit: **{item['profit']:,}** gp · ROI: {item['roi']:.1f}% · Vol: {vol_str}"
            ),
            inline=False
        )
        total_potential += item['profit']

    embed.add_field(
        name="📊 Total Potential",
        value=f"If all flips succeed: **{total_potential:,}** gp profit",
        inline=False
    )
    embed.set_footer(text="Grand Flip Out v2.2 • Not financial advice, prices change fast!")
    embed.timestamp = datetime.now()
    await interaction.followup.send(embed=embed)


@bot.tree.command(name="stats", description="Show bot statistics and market overview")
async def stats_command(interaction: discord.Interaction):
    """Quick market overview with key stats."""
    await interaction.response.defer()

    if not api_client.latest_prices:
        await interaction.followup.send("Still loading data...")
        return

    total_items = len(api_client.item_mapping)
    tracked_prices = len(api_client.latest_prices)
    tracked_stats = len(_item_stats)

    # Market overview calculations
    profitable = 0
    total_volume = 0
    total_margin_gp = 0
    best_flip = {'name': 'None', 'profit': 0}

    for item_id, meta in api_client.item_mapping.items():
        iid = int(item_id)
        prices = api_client.latest_prices.get(iid, {})
        high = prices.get('high', 0) or 0
        low = prices.get('low', 0) or 0
        if high > 0 and low > 0:
            tax = min(int(high * 0.02), 5000000)
            profit = high - low - tax
            if profit > 0:
                profitable += 1
                total_margin_gp += profit
                if profit > best_flip['profit']:
                    best_flip = {'name': meta.get('name', '?'), 'profit': profit}

        vol = api_client.volume_data.get(iid, {})
        total_volume += (vol.get('highPriceVolume', 0) or 0) + (vol.get('lowPriceVolume', 0) or 0)

    avg_margin = total_margin_gp // max(profitable, 1)

    embed = discord.Embed(title="📊 Grand Flip Out — Market Overview", color=OSRS_GOLD)
    embed.add_field(name="Items Tracked", value=f"**{tracked_prices:,}** / {total_items:,}", inline=True)
    embed.add_field(name="Profitable Flips", value=f"**{profitable:,}**", inline=True)
    embed.add_field(name="5m Trade Volume", value=f"**{total_volume:,}** trades", inline=True)
    embed.add_field(name="Best Flip Right Now", value=f"**{best_flip['name']}**\n{format_gp(best_flip['profit'])}/item", inline=True)
    embed.add_field(name="Avg Profit/Item", value=f"**{format_gp(avg_margin)}**", inline=True)
    embed.add_field(name="Stats Tracked", value=f"**{tracked_stats:,}** items with history", inline=True)
    embed.set_footer(text="Grand Flip Out | Live OSRS Wiki data")
    embed.timestamp = datetime.now()
    await interaction.followup.send(embed=embed)


# Rate limiting state for auto-alerts
_alert_last_global = 0          # timestamp of last channel alert sent
_alert_item_times = {}          # item_id -> last alert timestamp
_alert_count_this_hour = []     # list of timestamps for alerts sent this hour
_channel_alert_history = []     # last 50 channel alerts for /alert history
ALERT_GLOBAL_COOLDOWN = 600     # 10 minutes between any alerts
ALERT_ITEM_COOLDOWN = 1800      # 30 min before same item can alert again
ALERT_MAX_PER_HOUR = 6          # max 6 alerts per hour total

# User alert rate limiting
_user_alert_last_triggered = {} # (user_id, item_id) -> timestamp
_user_dms_this_hour = {}        # user_id -> list of timestamps
USER_ALERT_COOLDOWN = 1800      # 30 minutes per user per item
USER_DM_MAX_PER_HOUR = 3        # max 3 DMs per user per hour


def _get_alert_history():
    """Get last 10 channel alerts"""
    return _channel_alert_history[-10:]


def _record_channel_alert(item_name, buy_price, sell_price):
    """Record a channel alert to history"""
    record = {
        'item_name': item_name,
        'buy_price': buy_price,
        'sell_price': sell_price,
        'timestamp': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    }
    _channel_alert_history.append(record)
    # Keep only last 50
    if len(_channel_alert_history) > 50:
        _channel_alert_history.pop(0)


async def _send_dm_safely(user_id: int, embed: discord.Embed) -> bool:
    """Send a DM to a user, gracefully handling if DMs are disabled"""
    try:
        user = await bot.fetch_user(user_id)
        await user.send(embed=embed)
        return True
    except discord.Forbidden:
        print(f"Cannot send DM to user {user_id} - DMs disabled")
        return False
    except Exception as e:
        print(f"Error sending DM to user {user_id}: {e}")
        return False


@tasks.loop(minutes=2)
async def check_user_alerts():
    """Check user-defined alerts and send DMs if conditions are met"""
    global _user_alert_last_triggered, _user_dms_this_hour

    try:
        now = datetime.now().timestamp()
        alerts = load_user_alerts()

        # Prune hourly DM counters
        for uid in _user_dms_this_hour:
            _user_dms_this_hour[uid] = [t for t in _user_dms_this_hour[uid] if now - t < 3600]

        for uid, user_alerts in alerts.items():
            # Check if user has hit DM limit this hour
            if len(_user_dms_this_hour.get(uid, [])) >= USER_DM_MAX_PER_HOUR:
                continue

            for alert in user_alerts:
                if not alert.get('enabled', True):
                    continue

                item_id = alert.get('item_id')
                item_name = alert.get('item_name', 'Unknown')

                # Build item data
                item_data = api_client._build_item_data(item_id)
                if not item_data or item_data['buy_price'] == 0:
                    continue

                # Check cooldown for this user + item
                key = (uid, item_id)
                last_trigger = _user_alert_last_triggered.get(key, 0)
                if now - last_trigger < USER_ALERT_COOLDOWN:
                    continue

                # Check conditions
                margin_pct = alert.get('margin_pct', 2)
                min_profit = alert.get('min_profit', 100000)
                min_volume = alert.get('min_volume', 5000)
                target_price = alert.get('target_price', 0)

                # Volume check (daily volume estimate: vol_per_hour * 24)
                daily_vol = vol_per_hour(item_data) * 24
                if daily_vol < min_volume:
                    continue

                # Realistic profit check
                realistic_profit = realistic_4h_profit(item_data)
                if realistic_profit < min_profit:
                    continue

                # Calculate margin % on the item
                buy_price = item_data.get('buy_price', 0)
                sell_price = item_data.get('sell_price', 0)
                if buy_price > 0:
                    margin_pct_actual = ((sell_price - buy_price) / buy_price) * 100
                    if margin_pct_actual < margin_pct:
                        continue

                # Target price check (if set)
                if target_price > 0 and buy_price > target_price:
                    continue

                # All conditions met! Send DM
                embed = await create_item_embed(item_data, alert_type="personal")
                success = await _send_dm_safely(int(uid), embed)

                if success:
                    _user_alert_last_triggered[key] = now
                    if uid not in _user_dms_this_hour:
                        _user_dms_this_hour[uid] = []
                    _user_dms_this_hour[uid].append(now)

                    # Update last_triggered timestamp
                    alert['last_triggered'] = datetime.now().isoformat()
                    save_user_alerts(alerts)

    except Exception as e:
        print(f"User alert check error: {e}")


@check_user_alerts.before_loop
async def before_check_user_alerts():
    await bot.wait_until_ready()


# ── User Portfolio Storage ──
_user_portfolios = {}  # user_id -> [{ item_id, item_name, quantity, buy_price, timestamp }]

def load_portfolios():
    """Load user portfolios from file"""
    global _user_portfolios
    portfolio_file = 'user_portfolios.json'
    if os.path.exists(portfolio_file):
        try:
            with open(portfolio_file, 'r') as f:
                _user_portfolios = json.load(f)
        except (json.JSONDecodeError, IOError):
            _user_portfolios = {}
    return _user_portfolios

def save_portfolios():
    """Save user portfolios to file"""
    with open('user_portfolios.json', 'w') as f:
        json.dump(_user_portfolios, f, indent=2)

# Load portfolios on startup
load_portfolios()


@bot.tree.command(name="alert", description="Set price alerts for items")
@app_commands.describe(
    action="set, list, or clear",
    item="Item name",
    direction="above or below",
    price="Price threshold"
)
@app_commands.autocomplete(item=item_name_autocomplete)
async def alert_command(interaction: discord.Interaction, action: str, item: Optional[str] = None, direction: Optional[str] = None, price: Optional[int] = None):
    """Manage price alerts - get notified when items hit target prices"""
    await interaction.response.defer()
    user_id = str(interaction.user.id)

    if action.lower() == "set":
        if not item or not direction or price is None:
            await interaction.followup.send("Usage: `/alert set <item> <above|below> <price>`")
            return

        item_data = api_client.find_item_by_name(item)
        if not item_data:
            await interaction.followup.send(f"Couldn't find item matching '{item}'.")
            return

        alerts = load_user_alerts()
        if user_id not in alerts:
            alerts[user_id] = []

        new_alert = {
            'item_id': item_data['id'],
            'item_name': item_data['name'],
            'direction': direction.lower(),
            'target_price': price,
            'enabled': True,
            'created_at': datetime.now().isoformat()
        }
        alerts[user_id].append(new_alert)
        save_user_alerts(alerts)

        embed = discord.Embed(
            title="⚡ Price Alert Set",
            description=f"You'll be notified when **{item_data['name']}** goes **{direction.lower()}** **{price:,} gp**.",
            color=OSRS_GOLD
        )
        embed.set_thumbnail(url=get_item_icon_url(item_data['id'], item_data['name']))
        embed.add_field(name="Current Price", value=f"{item_data['current_price']:,} gp", inline=True)
        embed.set_footer(text="Check alerts with /alert list")
        await interaction.followup.send(embed=embed)

    elif action.lower() == "list":
        alerts = load_user_alerts()
        user_alerts = alerts.get(user_id, [])

        if not user_alerts:
            await interaction.followup.send("You have no price alerts set. Use `/alert set` to create one.")
            return

        embed = discord.Embed(title="📋 Your Price Alerts", color=OSRS_GOLD)
        for i, alert in enumerate(user_alerts, 1):
            item_name = alert.get('item_name', 'Unknown')
            direction = alert.get('direction', '?').upper()
            target = alert.get('target_price', 0)
            status = "🟢 Active" if alert.get('enabled', True) else "🔴 Inactive"
            embed.add_field(
                name=f"{i}. {item_name}",
                value=f"{direction} {target:,} gp • {status}",
                inline=False
            )
        embed.set_footer(text="Use /alert clear [item] to remove an alert")
        await interaction.followup.send(embed=embed)

    elif action.lower() == "clear":
        if not item:
            await interaction.followup.send("Usage: `/alert clear [item]` or `/alert clear all`")
            return

        alerts = load_user_alerts()
        user_alerts = alerts.get(user_id, [])

        if item.lower() == "all":
            alerts[user_id] = []
            save_user_alerts(alerts)
            await interaction.followup.send("✅ All price alerts cleared.")
            return

        item_data = api_client.find_item_by_name(item)
        if not item_data:
            await interaction.followup.send(f"Couldn't find item matching '{item}'.")
            return

        before = len(user_alerts)
        alerts[user_id] = [a for a in user_alerts if a.get('item_id') != item_data['id']]
        after = len(alerts[user_id])

        if before == after:
            await interaction.followup.send(f"You don't have an alert for **{item_data['name']}**.")
            return

        save_user_alerts(alerts)
        await interaction.followup.send(f"✅ Alert for **{item_data['name']}** removed.")

    else:
        await interaction.followup.send("Use: `/alert set`, `/alert list`, or `/alert clear`")


@bot.tree.command(name="portfolio", description="Track your flip positions and P&L")
@app_commands.describe(action="add, view, or remove", item="Item name", quantity="Number of items", buy_price="Price you bought at")
@app_commands.autocomplete(item=item_name_autocomplete)
async def portfolio_command(interaction: discord.Interaction, action: str, item: Optional[str] = None, quantity: Optional[int] = None, buy_price: Optional[int] = None):
    """Manage your flip portfolio - track positions with live P&L"""
    await interaction.response.defer()
    user_id = str(interaction.user.id)

    if action.lower() == "add":
        if not item or quantity is None or buy_price is None:
            await interaction.followup.send("Usage: `/portfolio add <item> <quantity> <buy_price>`")
            return

        if quantity <= 0 or buy_price <= 0:
            await interaction.followup.send("Quantity and price must be positive numbers.")
            return

        item_data = api_client.find_item_by_name(item)
        if not item_data:
            await interaction.followup.send(f"Couldn't find item matching '{item}'.")
            return

        if user_id not in _user_portfolios:
            _user_portfolios[user_id] = []

        position = {
            'item_id': item_data['id'],
            'item_name': item_data['name'],
            'quantity': quantity,
            'buy_price': buy_price,
            'timestamp': datetime.now().isoformat()
        }
        _user_portfolios[user_id].append(position)
        save_portfolios()

        total_cost = quantity * buy_price
        embed = discord.Embed(
            title="📦 Position Added",
            description=f"Added **{quantity:,}x {item_data['name']}** @ {buy_price:,} gp each",
            color=OSRS_GOLD
        )
        embed.set_thumbnail(url=get_item_icon_url(item_data['id'], item_data['name']))
        embed.add_field(name="Total Cost", value=f"**{total_cost:,} gp**", inline=True)
        embed.add_field(name="Current Price", value=f"{item_data['current_price']:,} gp", inline=True)
        embed.set_footer(text="Check your portfolio with /portfolio view")
        await interaction.followup.send(embed=embed)

    elif action.lower() == "view":
        portfolio = _user_portfolios.get(user_id, [])

        if not portfolio:
            await interaction.followup.send("Your portfolio is empty. Use `/portfolio add` to add positions.")
            return

        embed = discord.Embed(title="💼 Your Portfolio", color=OSRS_GOLD)
        total_invested = 0
        total_value = 0

        for pos in portfolio:
            item_id = pos.get('item_id')
            item_name = pos.get('item_name', 'Unknown')
            qty = pos.get('quantity', 0)
            buy_price = pos.get('buy_price', 0)

            current_data = api_client._build_item_data(item_id)
            current_price = current_data.get('current_price', 0) if current_data else 0

            cost = qty * buy_price
            value = qty * current_price
            pnl = value - cost
            pnl_pct = (pnl / cost * 100) if cost > 0 else 0

            total_invested += cost
            total_value += value

            color = "🟢" if pnl >= 0 else "🔴"
            embed.add_field(
                name=f"{item_name} ({qty:,})",
                value=f"Cost: {format_gp(cost)} @ {buy_price:,} each\nNow: {format_gp(value)} @ {current_price:,} each\n{color} **{format_gp(pnl)}** ({pnl_pct:+.1f}%)",
                inline=False
            )

        total_pnl = total_value - total_invested
        total_pnl_pct = (total_pnl / total_invested * 100) if total_invested > 0 else 0
        color = OSRS_GREEN if total_pnl >= 0 else OSRS_RED

        embed.add_field(name="━━━━━━━━━━", value="", inline=False)
        embed.add_field(name="Total Invested", value=f"**{format_gp(total_invested)}**", inline=True)
        embed.add_field(name="Total Value", value=f"**{format_gp(total_value)}**", inline=True)
        embed.add_field(name="Total P&L", value=f"**{format_gp(total_pnl)}** ({total_pnl_pct:+.1f}%)", inline=True, color=color)
        embed.set_footer(text="Use /portfolio remove <item> to sell a position")
        await interaction.followup.send(embed=embed)

    elif action.lower() == "remove":
        if not item:
            await interaction.followup.send("Usage: `/portfolio remove <item>`")
            return

        portfolio = _user_portfolios.get(user_id, [])
        item_data = api_client.find_item_by_name(item)

        if not item_data or not any(p.get('item_id') == item_data['id'] for p in portfolio):
            await interaction.followup.send(f"You don't have **{item}** in your portfolio.")
            return

        _user_portfolios[user_id] = [p for p in portfolio if p.get('item_id') != item_data['id']]
        save_portfolios()
        await interaction.followup.send(f"✅ Removed **{item_data['name']}** from portfolio.")

    else:
        await interaction.followup.send("Use: `/portfolio add`, `/portfolio view`, or `/portfolio remove`")


def load_market_summary_channel():
    """Load configured market summary channel ID"""
    if os.path.exists(MARKET_SUMMARY_CHANNEL_FILE):
        try:
            with open(MARKET_SUMMARY_CHANNEL_FILE, 'r') as f:
                data = json.load(f)
                return int(data.get('channel_id', 0)) or None
        except (json.JSONDecodeError, IOError, ValueError):
            return None
    return None

def save_market_summary_channel(channel_id):
    """Save configured market summary channel ID"""
    with open(MARKET_SUMMARY_CHANNEL_FILE, 'w') as f:
        json.dump({'channel_id': channel_id}, f, indent=2)

_market_summary_channel = load_market_summary_channel()


@bot.tree.command(name="setchannel", description="Set channel for daily market summary (admin only)")
@app_commands.describe(action="set or clear")
async def setchannel_command(interaction: discord.Interaction, action: str):
    """Configure where to post daily market summaries"""
    global _market_summary_channel

    # Check admin permission
    if not interaction.user.guild_permissions.administrator:
        await interaction.response.send_message("This command requires administrator permissions.", ephemeral=True)
        return

    await interaction.response.defer()

    if action.lower() == "set":
        _market_summary_channel = interaction.channel_id
        save_market_summary_channel(interaction.channel_id)
        await interaction.followup.send(f"✅ Daily market summaries will be posted in {interaction.channel.mention}")

    elif action.lower() == "clear":
        _market_summary_channel = None
        save_market_summary_channel(None)
        await interaction.followup.send("✅ Market summary posting disabled.")

    else:
        await interaction.followup.send("Use: `/setchannel set` or `/setchannel clear`")


# ============================================
# INTELLIGENCE COMMANDS
# ============================================
@bot.tree.command(name="analyze", description="Analyze an item: learn the framework for thinking critically about prices")
@app_commands.describe(item="Item name (e.g., Blood rune, Eclectic impling jar)")
async def analyze_intel_command(interaction: discord.Interaction, item: str):
    """Educational analysis tool: learn how to evaluate items by understanding supply, demand, and price floors"""
    await interaction.response.defer()

    # Find the item
    item_data = wiki_client.find_item_by_name(item)
    if not item_data:
        await interaction.followup.send(f"❌ Item '{item}' not found in market data.", ephemeral=True)
        return

    # Get intelligence data
    intel = get_item_intelligence(item_data['name'])
    if not intel:
        await interaction.followup.send(f"ℹ️ No special intelligence available for **{item_data['name']}**. Use `/price` for current market data.", ephemeral=True)
        return

    # Build embed
    embed = discord.Embed(
        title=f"📚 How to Think About: {item_data['name']}",
        description=intel.get('analysis_example', 'N/A'),
        color=OSRS_GOLD
    )

    # Floor price and reason
    if intel.get('floor_price'):
        pct_to_floor = ((item_data['current_price'] - intel['floor_price']) / intel['floor_price'] * 100) if intel['floor_price'] > 0 else 0
        embed.add_field(
            name="🏛️ Price Floor Information",
            value=f"NPC Buy Price: **{format_gp(intel['floor_price'])}/ea**\n_{intel.get('floor_reason', 'N/A')}_",
            inline=False
        )
        embed.add_field(
            name="Current Price Context",
            value=f"Current: **{format_gp(item_data['current_price'])}**\nRelative to floor: **{pct_to_floor:+.1f}%**",
            inline=True
        )

    # Demand drivers
    if intel.get('demand_drivers'):
        embed.add_field(
            name="🔍 What Creates Demand?",
            value="\n".join(f"• {d}" for d in intel['demand_drivers'][:4]),
            inline=False
        )

    # Supply
    if intel.get('supply_info'):
        embed.add_field(
            name="📦 Where Does Supply Come From?",
            value=intel['supply_info'],
            inline=False
        )

    # Synergies
    if intel.get('synergies'):
        embed.add_field(
            name="🔗 Related Items",
            value=", ".join(intel['synergies'][:5]),
            inline=False
        )

    # Notable conditions
    if intel.get('notable_conditions'):
        embed.add_field(
            name="⚡ Notable Conditions",
            value=intel['notable_conditions'],
            inline=False
        )

    # Category
    embed.add_field(
        name="📋 Category",
        value=intel.get('category', 'Other').title(),
        inline=True
    )

    # Current market stats
    embed.add_field(
        name="💰 Market Snapshot",
        value=f"Buy: {format_gp(item_data['buy_price'])}\nSell: {format_gp(item_data['sell_price'])}\nMargin: {format_gp(item_data['margin'])}",
        inline=True
    )

    embed.set_footer(text="This is educational analysis, not trading advice. Always do your own research.")
    embed.timestamp = datetime.now()

    await interaction.followup.send(embed=embed)


@bot.tree.command(name="floors", description="Show items trading near known NPC buy prices (floor prices)")
async def floors_command(interaction: discord.Interaction):
    """Display items currently trading near their known price floors — neutral market data"""
    await interaction.response.defer()

    # Scan all items in knowledge base for those near floor
    near_floor_items = []

    for item_name, intel in ITEM_INTELLIGENCE.items():
        if not intel.get('floor_price'):
            continue

        # Find item data
        item_data = wiki_client.find_item_by_name(item_name)
        if not item_data:
            continue

        current_price = item_data['current_price']
        floor_price = intel['floor_price']
        pct_to_floor = ((current_price - floor_price) / floor_price * 100) if floor_price > 0 else 0

        # Within 10% of floor = interesting data point
        if pct_to_floor <= 10:
            near_floor_items.append({
                'name': item_name,
                'current': current_price,
                'floor': floor_price,
                'pct': pct_to_floor,
                'reason': intel.get('floor_reason', ''),
                'margin': item_data['margin'],
                'spread': item_data['sell_price'] - item_data['buy_price']
            })

    if not near_floor_items:
        await interaction.followup.send("✅ No items currently near floor price. Market is healthy.")
        return

    # Sort by distance to floor (ascending)
    near_floor_items.sort(key=lambda x: x['pct'])

    # Build embed
    embed = discord.Embed(
        title="🏛️ Items Near Floor Prices",
        description=f"**{len(near_floor_items)}** items currently trading within 10% of known NPC buy prices",
        color=OSRS_GOLD
    )

    for item in near_floor_items[:8]:  # Show top 8
        floor_distance = f"{item['pct']:+.1f}%" if item['pct'] != 0 else "AT FLOOR"
        item_str = (
            f"Current: **{format_gp(item['current'])}**\n"
            f"Floor: **{format_gp(item['floor'])}** ({floor_distance})\n"
            f"Margin: {format_gp(item['margin'])}\n"
            f"_{item['reason']}_"
        )
        embed.add_field(
            name=f"💰 {item['name']}",
            value=item_str,
            inline=False
        )

    if len(near_floor_items) > 8:
        embed.add_field(
            name="... and more",
            value=f"**+{len(near_floor_items) - 8}** more items near floor. Run `/analyze` on any of them for details.",
            inline=False
        )

    embed.set_footer(text="These items are currently trading near known NPC buy prices. See the data and decide for yourself.")
    embed.timestamp = datetime.now()

    await interaction.followup.send(embed=embed)


@tasks.loop(hours=24)
async def post_market_summary():
    """Post daily market summary to configured channel"""
    global _market_summary_channel

    if not _market_summary_channel:
        return

    try:
        ch = bot.get_channel(_market_summary_channel)
        if not ch:
            return

        # Get market summary
        summary = api_client.get_market_summary()
        if not summary:
            return

        embed = discord.Embed(
            title="📊 Daily Market Summary",
            color=OSRS_GOLD,
            timestamp=datetime.now()
        )

        embed.add_field(
            name="Items Tracked",
            value=f"**{summary.get('active_items', 0):,}**",
            inline=True
        )
        embed.add_field(
            name="5m Trade Volume",
            value=f"**{summary.get('total_volume', 0):,}** trades",
            inline=True
        )
        embed.add_field(
            name="Avg Profit/Item",
            value=f"**{format_gp(summary.get('avg_margin', 0))}**",
            inline=True
        )

        if summary.get('top_item_by_margin'):
            top = summary['top_item_by_margin']
            embed.add_field(
                name="Best Flip Opportunity",
                value=f"**{top.get('name', 'Unknown')}**\n{format_gp(top.get('margin', 0))}/item",
                inline=False
            )

        embed.set_footer(text="Grand Flip Out | Live OSRS Wiki data")
        await ch.send(embed=embed)

    except Exception as e:
        print(f"Market summary post error: {e}")

@post_market_summary.before_loop
async def before_post_market_summary():
    await bot.wait_until_ready()


@tasks.loop(minutes=5)
async def check_dumps():
    """Post only the best dump opportunities. Enhanced with confirmation and severity tiers."""
    global _alert_last_global, _alert_item_times, _alert_count_this_hour

    if not DUMP_ALERT_CHANNEL_ID:
        return

    try:
        ch = bot.get_channel(DUMP_ALERT_CHANNEL_ID)
        if not ch:
            return

        now = datetime.now().timestamp()

        # Global cooldown
        if now - _alert_last_global < ALERT_GLOBAL_COOLDOWN:
            return

        # Prune hourly counter
        _alert_count_this_hour = [t for t in _alert_count_this_hour if now - t < 3600]
        if len(_alert_count_this_hour) >= ALERT_MAX_PER_HOUR:
            return

        # Get dumps with quality filtering for alerts
        dumps = api_client.get_dumps(limit=5, for_alert=True)
        if not dumps:
            return

        sent = 0
        for d in dumps:
            if sent >= 2:
                break  # Max 2 alerts per cycle

            item_id = d.get('id', 0)
            flip_profit = d.get('flip_profit', 0)
            margin_pct = d.get('margin_pct', 0)

            # Determine severity tier
            if flip_profit > 2000000 or d.get('price_dropped'):
                tier = 1
                tier_cooldown = 1200  # 20 min
            elif flip_profit > 500000:
                tier = 2
                tier_cooldown = 1800  # 30 min
            else:
                tier = 3
                tier_cooldown = 3600  # 1 hour

            # Per-item cooldown (tier-based)
            if item_id in _alert_item_times and now - _alert_item_times[item_id] < tier_cooldown:
                continue

            # Build the embed
            drop_indicator = " 📉 PRICE DROP" if d.get('price_dropped') else ""
            embed = discord.Embed(
                title=f"💰 {d['name']}{drop_indicator}",
                description=(
                    f"Buy **{format_gp(d['buy_price'])}** → Sell **{format_gp(d['sell_price'])}**\n"
                    f"Flip profit: **{format_gp(d['flip_profit'])}/item** ({margin_pct}% margin)"
                ),
                color=0xFF4444 if d.get('price_dropped') else 0x22C55E,
            )
            icon = d.get('icon', '')
            if icon:
                embed.set_thumbnail(url=f"https://oldschool.runescape.wiki/images/{icon.replace(' ', '_')}")

            # Add tier indicator
            tier_label = {1: "🔴 TIER 1 (HOT)", 2: "🟡 TIER 2", 3: "🟠 TIER 3"}[tier]
            embed.add_field(name="Severity", value=tier_label, inline=True)
            embed.add_field(name="Est. Total", value=f"**{format_gp(d['total_profit'])}** ({d.get('can_buy', 0):,} units)", inline=True)
            embed.add_field(name="Volume (5m)", value=f"{d.get('volume', 0):,}", inline=True)

            embed.set_footer(text="Grand Flip Out | /alert add for personal notifications")
            embed.timestamp = datetime.now()

            await ch.send(embed=embed)

            # Update rate limits
            _alert_last_global = now
            _alert_item_times[item_id] = now
            _alert_count_this_hour.append(now)

            # Record to history
            _record_channel_alert(d['name'], d['buy_price'], d['sell_price'])

            sent += 1

    except Exception as e:
        print(f"Dump check error: {e}")

@check_dumps.before_loop
async def before_check_dumps():
    await bot.wait_until_ready()


async def main():
    async with bot:
        await bot.start(DISCORD_TOKEN)

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("Bot shutting down...")
    finally:
        try:
            asyncio.run(api_client.close_session())
            asyncio.run(backend.close())
        except Exception:
            pass
