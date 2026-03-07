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


class HelpView(discord.ui.View):
    """Interactive help menu with category selection"""
    def __init__(self, timeout=300):
        super().__init__(timeout=timeout)

    @discord.ui.select(
        placeholder="Choose a command category...",
        options=[
            discord.SelectOption(label="Market Data", description="/price, /top, /dumps, /compare, /hotlist", emoji="📊"),
            discord.SelectOption(label="Analysis Tools", description="/analyze, /floors, /calc, /stats", emoji="🔍"),
            discord.SelectOption(label="Trading Tools", description="/flip, /watchlist, /alert, /portfolio", emoji="💰"),
            discord.SelectOption(label="Admin Commands", description="/setchannel, /help", emoji="⚙️"),
        ]
    )
    async def category_select(self, interaction: discord.Interaction, select: discord.ui.Select):
        category = select.values[0]

        category_embeds = {
            "Market Data": discord.Embed(
                title="📊 Market Data Commands",
                color=OSRS_GOLD,
                description="Fetch live prices and market analysis"
            ).add_field(
                name="/price <item>",
                value="Look up current buy/sell prices and profit margins for an item",
                inline=False
            ).add_field(
                name="/top [sort] [limit]",
                value="Show top flipping opportunities sorted by realistic profit, margin, or volume",
                inline=False
            ).add_field(
                name="/dumps",
                value="Show items with wide buy/sell spreads (dump opportunities)",
                inline=False
            ).add_field(
                name="/compare <item1> <item2>",
                value="Compare two items side-by-side with all metrics",
                inline=False
            ).add_field(
                name="/hotlist",
                value="See the most profitable items trading right now",
                inline=False
            ).set_footer(text="Use /price to get started!"),

            "Analysis Tools": discord.Embed(
                title="🔍 Analysis & Learning Commands",
                color=OSRS_GOLD,
                description="Understand supply/demand dynamics and make informed decisions"
            ).add_field(
                name="/analyze <item>",
                value="Learn supply/demand drivers, price floors, and market psychology for an item",
                inline=False
            ).add_field(
                name="/floors",
                value="See items trading near known NPC buy prices (potential buy signals)",
                inline=False
            ).add_field(
                name="/calc <item>",
                value="Detailed flip profit analysis with volume, margins, and risk assessment",
                inline=False
            ).add_field(
                name="/stats",
                value="Market overview: profitable flips, trading volume, and bot statistics",
                inline=False
            ).set_footer(text="Educational analysis helps you flip smarter"),

            "Trading Tools": discord.Embed(
                title="💰 Trading Tools",
                color=OSRS_GOLD,
                description="Calculate, track, and automate your trading decisions"
            ).add_field(
                name="/flip <item> [qty] [buy] [sell]",
                value="Calculate flip profit with custom quantities and prices",
                inline=False
            ).add_field(
                name="/watchlist [add/remove/show] [item]",
                value="Build a personal watchlist of items to flip",
                inline=False
            ).add_field(
                name="/alert [add/remove/list] [item] [opts]",
                value="Set price alerts to get notified when items meet your flip criteria",
                inline=False
            ).add_field(
                name="/portfolio [add/view/remove] [item] [qty] [price]",
                value="Track your active flip positions and P&L",
                inline=False
            ).set_footer(text="Automate your flipping workflow"),

            "Admin Commands": discord.Embed(
                title="⚙️ Admin Commands",
                color=OSRS_GOLD,
                description="Server management and bot configuration"
            ).add_field(
                name="/setchannel [set/clear]",
                value="Configure a channel to receive daily market summaries (admin only)",
                inline=False
            ).add_field(
                name="/help",
                value="Show this command list with interactive navigation",
                inline=False
            ).set_footer(text="Admin commands require admin permissions"),
        }

        embed = category_embeds[category]
        embed.timestamp = datetime.now()
        await interaction.response.send_message(embed=embed, ephemeral=True)

    async def on_timeout(self):
        for item in self.children:
            item.disabled = True

# Channel configuration storage
CHANNELS_CONFIG_FILE = 'gfo_channels_config.json'
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


def load_channels_config():
    """Load GFO channels configuration from file"""
    try:
        with open(CHANNELS_CONFIG_FILE, 'r') as f:
            return json.load(f)
    except FileNotFoundError:
        return {
            'alerts_channel': None,
            'topflips_channel': None,
            'market_summary_channel': None,
            'discussion_channel': None,
            'commands_channel': None
        }


def save_channels_config(config):
    """Save GFO channels configuration to file"""
    with open(CHANNELS_CONFIG_FILE, 'w') as f:
        json.dump(config, f, indent=2)


def load_market_summary_channel():
    """Load market summary channel from file (legacy compatibility)"""
    try:
        with open(MARKET_SUMMARY_CHANNEL_FILE, 'r') as f:
            data = json.load(f)
            return data.get('channel_id', None)
    except FileNotFoundError:
        # Try loading from new config
        config = load_channels_config()
        return config.get('market_summary_channel', None)


def save_market_summary_channel(channel_id):
    """Save market summary channel to file (legacy compatibility)"""
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
        self.channels_config = load_channels_config()
        # Legacy compatibility
        self.market_summary_channel = self.channels_config.get('market_summary_channel')
        self.post_market_summary.start()
        self.check_dumps.start()
        self.rotate_status.start()

    def cog_unload(self):
        self.post_market_summary.cancel()
        self.check_dumps.cancel()
        self.rotate_status.cancel()

    @app_commands.command(name="setupchannels", description="Create organized GFO channels in this server (admin only)")
    @app_commands.default_permissions(administrator=True)
    async def setup_channels(self, interaction: discord.Interaction):
        """Create organized channels for the Grand Flip Out bot"""
        await interaction.response.defer()

        try:
            guild = interaction.guild

            # Create category "Grand Flip Out"
            category = await guild.create_category("Grand Flip Out")

            # Create channels
            alerts_channel = await guild.create_text_channel(
                "gfo-alerts",
                category=category,
                topic="Dump and pump alerts from Grand Flip Out bot"
            )

            topflips_channel = await guild.create_text_channel(
                "gfo-top-flips",
                category=category,
                topic="Hourly top flip summaries"
            )

            market_summary_channel = await guild.create_text_channel(
                "gfo-market-summary",
                category=category,
                topic="Daily market overview and statistics"
            )

            discussion_channel = await guild.create_text_channel(
                "gfo-discussion",
                category=category,
                topic="General flipping discussion and sharing"
            )

            commands_channel = await guild.create_text_channel(
                "gfo-bot-commands",
                category=category,
                topic="Bot commands and queries"
            )

            # Store channel IDs in config
            self.channels_config = {
                'alerts_channel': alerts_channel.id,
                'topflips_channel': topflips_channel.id,
                'market_summary_channel': market_summary_channel.id,
                'discussion_channel': discussion_channel.id,
                'commands_channel': commands_channel.id
            }
            save_channels_config(self.channels_config)

            # Update market_summary_channel for legacy compatibility
            self.market_summary_channel = market_summary_channel.id

            # Build response
            embed = discord.Embed(
                title="✅ Grand Flip Out Channels Created",
                color=OSRS_GREEN,
                description="Your server is now set up for Grand Flip Out!"
            )

            embed.add_field(
                name="Channels Created",
                value=(
                    f"{alerts_channel.mention} - Dump/pump alerts\n"
                    f"{topflips_channel.mention} - Hourly top flips\n"
                    f"{market_summary_channel.mention} - Daily market summary\n"
                    f"{discussion_channel.mention} - General discussion\n"
                    f"{commands_channel.mention} - Bot commands"
                ),
                inline=False
            )

            embed.add_field(
                name="Configuration",
                value="Channel assignments have been saved. Use `/setchannel` to customize further.",
                inline=False
            )

            await interaction.followup.send(embed=embed)

        except Exception as e:
            await interaction.followup.send(f"❌ Failed to create channels: {str(e)}")

    @app_commands.command(name="setchannel", description="Configure specific channels for GFO features (admin only)")
    @app_commands.describe(
        channel_type="alerts, topflips, or market_summary",
        action="set or clear"
    )
    @app_commands.default_permissions(administrator=True)
    async def setchannel_command(self, interaction: discord.Interaction, channel_type: str, action: str):
        """Configure specific channel destinations for GFO features"""
        await interaction.response.defer()

        channel_type = channel_type.lower()
        action = action.lower()

        valid_types = ["alerts", "topflips", "market_summary"]
        if channel_type not in valid_types:
            await interaction.followup.send(f"❌ Invalid channel type. Use: {', '.join(valid_types)}")
            return

        if action not in ["set", "clear"]:
            await interaction.followup.send("❌ Action must be 'set' or 'clear'")
            return

        # Map channel_type to config key
        config_key_map = {
            "alerts": "alerts_channel",
            "topflips": "topflips_channel",
            "market_summary": "market_summary_channel"
        }
        config_key = config_key_map[channel_type]

        if action == "set":
            channel_id = interaction.channel.id
            self.channels_config[config_key] = channel_id
            save_channels_config(self.channels_config)

            # Update legacy market_summary_channel if needed
            if channel_type == "market_summary":
                self.market_summary_channel = channel_id

            feature_names = {
                "alerts": "Dump/pump alerts",
                "topflips": "Hourly top flips",
                "market_summary": "Daily market summaries"
            }

            await interaction.followup.send(
                f"✅ {feature_names[channel_type]} will now be posted to {interaction.channel.mention}"
            )
        else:  # clear
            self.channels_config[config_key] = None
            save_channels_config(self.channels_config)

            if channel_type == "market_summary":
                self.market_summary_channel = None

            await interaction.followup.send(f"✅ {channel_type} channel cleared.")

    @app_commands.command(name="help", description="Show bot commands and features")
    async def help_command(self, interaction: discord.Interaction):
        """Show help information with interactive menu"""
        await interaction.response.defer()

        embed = discord.Embed(
            title="Grand Flip Out Bot — Command Help",
            description="Complete flipping toolkit for OSRS Grand Exchange",
            color=OSRS_GOLD,
            timestamp=datetime.now()
        )

        embed.add_field(
            name="📊 Market Data",
            value="Price lookups and market analysis tools",
            inline=True
        )
        embed.add_field(
            name="🔍 Analysis Tools",
            value="Learn supply/demand and make informed decisions",
            inline=True
        )
        embed.add_field(
            name="💰 Trading Tools",
            value="Calculate, track, and automate your trades",
            inline=True
        )
        embed.add_field(
            name="⚙️ Admin Commands",
            value="Server configuration and management",
            inline=True
        )

        embed.add_field(
            name="🌐 Resources",
            value="Website: https://tunatroll.github.io/grand-flip-out/",
            inline=False
        )

        embed.set_footer(text="Grand Flip Out | Select a category below to learn more")

        view = HelpView()
        await interaction.followup.send(embed=embed, view=view)

    @tasks.loop(hours=24)
    async def post_market_summary(self):
        """Post daily market summary to configured channel"""
        # Ensure we have the latest config
        market_ch_id = self.channels_config.get('market_summary_channel') or self.market_summary_channel
        if not market_ch_id:
            return

        try:
            ch = self.bot.get_channel(market_ch_id)
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
        """Post dump opportunities to configured alerts channel"""
        global _alert_last_global, _alert_item_times, _alert_count_this_hour

        # Try to get alerts channel from config, fallback to env var for legacy support
        dump_channel_id = self.channels_config.get('alerts_channel')
        if not dump_channel_id:
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
