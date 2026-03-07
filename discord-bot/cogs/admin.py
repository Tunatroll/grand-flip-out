"""
Admin & Utility Commands Cog
/setchannel, /help - Admin configuration and utility commands
Also handles scheduled tasks: market summary, dump alerts, status rotation
"""
import discord
from discord.ext import commands, tasks
from discord import app_commands
from datetime import datetime
from typing import Optional
import json
import asyncio

from utils import (
    OSRS_GOLD, OSRS_GREEN, OSRS_RED, OSRS_BLUE,
    format_gp, format_gp_short, format_vol_per_hour,
    vol_per_hour, realistic_buys, realistic_4h_profit, get_verdict, VERDICT_EMOJI
)

# Market summary channel storage
MARKET_SUMMARY_CHANNEL_FILE = 'market_summary_channel.json'

# Rate limiting state for dump alerts - with lock protection
_alert_last_global = 0
_alert_item_times = {}
_alert_count_this_hour = []
_alert_lock = asyncio.Lock()
_channel_alert_history = []
ALERT_GLOBAL_COOLDOWN = 600
ALERT_ITEM_COOLDOWN = 1800
ALERT_MAX_PER_HOUR = 6

# Status rotation
_status_index = 0


def load_market_summary_channel():
    """Load market summary channel from file"""
    try:
        with open(MARKET_SUMMARY_CHANNEL_FILE, 'r') as f:
            data = json.load(f)
            return data.get('channel_id', None)
    except FileNotFoundError:
        return None


def save_market_summary_channel(channel_id):
    """Save market summary channel to file"""
    with open(MARKET_SUMMARY_CHANNEL_FILE, 'w') as f:
        json.dump({'channel_id': channel_id}, f)


def _record_channel_alert(item_name, buy_price, sell_price):
    """Record a channel alert to history"""
    record = {
        'item_name': item_name,
        'buy_price': buy_price,
        'sell_price': sell_price,
        'timestamp': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    }
    _channel_alert_history.append(record)
    if len(_channel_alert_history) > 50:
        _channel_alert_history.pop(0)


class Admin(commands.Cog):
    def __init__(self, bot: commands.Bot):
        self.bot = bot
        self.market_summary_channel = load_market_summary_channel()
        self.post_market_summary.start()
        self.check_dumps.start()
        self.rotate_status.start()

    def cog_unload(self):
        self.post_market_summary.cancel()
        self.check_dumps.cancel()
        self.rotate_status.cancel()

    @app_commands.command(name="setchannel", description="Set channel for daily market summary (admin only)")
    @app_commands.describe(action="set or clear")
    @app_commands.default_permissions(administrator=True)
    async def setchannel_command(self, interaction: discord.Interaction, action: str):
        """Configure market summary channel"""
        await interaction.response.defer()

        action = action.lower()

        if action == "set":
            channel_id = interaction.channel.id
            self.market_summary_channel = channel_id
            save_market_summary_channel(channel_id)
            await interaction.followup.send(
                f"✅ Market summaries will now be posted to {interaction.channel.mention}"
            )
        elif action == "clear":
            self.market_summary_channel = None
            save_market_summary_channel(None)
            await interaction.followup.send("✅ Market summaries disabled.")
        else:
            await interaction.followup.send("Use 'set' or 'clear'.")

    @app_commands.command(name="help", description="Show bot commands and features")
    async def help_command(self, interaction: discord.Interaction):
        """Show help information"""
        await interaction.response.defer()

        embed = discord.Embed(
            title="Grand Flip Out Bot — Command Help",
            description="Complete flipping toolkit for OSRS Grand Exchange",
            color=OSRS_GOLD,
            timestamp=datetime.now()
        )

        # Market Commands
        embed.add_field(
            name="📊 Market Data",
            value=(
                "`/price <item>` — Look up item price and margins\n"
                "`/top [sort] [limit]` — Top flips (realistic/margin/volume)\n"
                "`/dumps` — Items with wide buy/sell spreads\n"
                "`/compare <item1> <item2>` — Side-by-side item comparison\n"
                "`/hotlist` — Most profitable items right now"
            ),
            inline=False
        )

        # Analysis Commands
        embed.add_field(
            name="📚 Analysis & Learning",
            value=(
                "`/analyze <item>` — Understand supply/demand dynamics\n"
                "`/floors` — Items trading near NPC buy prices\n"
                "`/calc <item>` — Detailed flip profit analysis\n"
                "`/stats` — Market statistics and overview"
            ),
            inline=False
        )

        # Trading Commands
        embed.add_field(
            name="💰 Trading Tools",
            value=(
                "`/flip <item> [qty] [buy] [sell]` — Calculate flip profit\n"
                "`/watchlist [add/remove/show] [item]` — Manage watchlist\n"
                "`/alert [add/remove/list] [item] [opts]` — Price alerts\n"
                "`/portfolio [add/view/remove] [item] [qty] [price]` — Track positions"
            ),
            inline=False
        )

        # Admin Commands
        embed.add_field(
            name="⚙️ Admin",
            value="`/setchannel [set/clear]` — Configure market summary channel (admin only)",
            inline=False
        )

        embed.add_field(
            name="🌐 Resources",
            value="Website: https://tunatroll.github.io/grand-flip-out/",
            inline=False
        )

        embed.set_footer(text="Grand Flip Out | Use /price <item> to get started")
        await interaction.followup.send(embed=embed)

    @tasks.loop(hours=24)
    async def post_market_summary(self):
        """Post daily market summary to configured channel"""
        if not self.market_summary_channel:
            return

        try:
            ch = self.bot.get_channel(self.market_summary_channel)
            if not ch:
                return

            api_client = self.bot.wiki_client
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
    async def before_post_market_summary(self):
        await self.bot.wait_until_ready()

    @tasks.loop(minutes=5)
    async def check_dumps(self):
        """Post dump opportunities to configured channel"""
        global _alert_last_global, _alert_item_times, _alert_count_this_hour

        dump_channel_id = int(self.bot.config.get('DUMP_ALERT_CHANNEL_ID', 0)) if self.bot.config.get('DUMP_ALERT_CHANNEL_ID') else None

        if not dump_channel_id:
            return

        try:
            ch = self.bot.get_channel(dump_channel_id)
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

            # Get dumps with quality filtering
            api_client = self.bot.wiki_client
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
                    tier_cooldown = 1200
                elif flip_profit > 500000:
                    tier = 2
                    tier_cooldown = 1800
                else:
                    tier = 3
                    tier_cooldown = 3600

                # Per-item cooldown
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

                tier_label = {1: "🔴 TIER 1 (HOT)", 2: "🟡 TIER 2", 3: "🟠 TIER 3"}[tier]
                embed.add_field(name="Severity", value=tier_label, inline=True)
                embed.add_field(
                    name="Est. Total",
                    value=f"**{format_gp(d['total_profit'])}** ({d.get('can_buy', 0):,} units)",
                    inline=True
                )
                embed.add_field(name="Volume (5m)", value=f"{d.get('volume', 0):,}", inline=True)

                embed.set_footer(text="Grand Flip Out | /alert add for personal notifications")
                embed.timestamp = datetime.now()

                await ch.send(embed=embed)

                # Update rate limits (lock for race condition protection)
                async with _alert_lock:
                    _alert_last_global = now
                    _alert_item_times[item_id] = now
                    _alert_count_this_hour.append(now)

                _record_channel_alert(d['name'], d['buy_price'], d['sell_price'])
                sent += 1

        except Exception as e:
            print(f"Dump check error: {e}")

    @check_dumps.before_loop
    async def before_check_dumps(self):
        await self.bot.wait_until_ready()

    @tasks.loop(minutes=5)
    async def rotate_status(self):
        """Rotate bot status with market info"""
        global _status_index

        try:
            api_client = self.bot.wiki_client
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
            await self.bot.change_presence(activity=statuses[_status_index])
            _status_index += 1

        except Exception as e:
            print(f"Status rotation error: {e}")

    @rotate_status.before_loop
    async def before_rotate_status(self):
        await self.bot.wait_until_ready()


async def setup(bot: commands.Bot):
    await bot.add_cog(Admin(bot))
