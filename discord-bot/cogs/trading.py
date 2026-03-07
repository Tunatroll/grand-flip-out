"""
Trading Tools Cog
/flip, /watchlist, /alert, /portfolio - Trading calculation and tracking
"""
import discord
from discord.ext import commands, tasks
from discord import app_commands
from datetime import datetime
from typing import Optional, List
import json
import asyncio

from utils import (
    OSRS_GOLD, OSRS_GREEN, OSRS_RED, OSRS_BLUE,
    format_gp, format_gp_short, format_vol_per_hour,
    vol_per_hour, realistic_buys, realistic_4h_profit, get_verdict, VERDICT_EMOJI,
    GE_TAX_RATE, GE_MAX_TAX, PaginatedView, ConfirmView
)

# File paths for data persistence
WATCHLIST_FILE = 'watchlists.json'
USER_ALERTS_FILE = 'user_alerts.json'
PORTFOLIO_FILE = 'user_portfolios.json'

# Rate limiting state for auto-alerts - with lock protection
_alert_last_global = 0
_alert_item_times = {}
_alert_count_this_hour = []
_alert_lock = asyncio.Lock()
ALERT_GLOBAL_COOLDOWN = 600  # 10 minutes between any alerts
ALERT_ITEM_COOLDOWN = 1800  # 30 min before same item can alert again
ALERT_MAX_PER_HOUR = 6  # max 6 alerts per hour total

# User alert rate limiting
_user_alert_last_triggered = {}
_user_dms_this_hour = {}
USER_ALERT_COOLDOWN = 1800  # 30 minutes per user per item
USER_DM_MAX_PER_HOUR = 3  # max 3 DMs per user per hour


def load_watchlists():
    """Load watchlists from file"""
    try:
        with open(WATCHLIST_FILE, 'r') as f:
            return json.load(f)
    except FileNotFoundError:
        return {}


def save_watchlists(watchlists):
    """Save watchlists to file"""
    with open(WATCHLIST_FILE, 'w') as f:
        json.dump(watchlists, f, indent=2)


def load_user_alerts():
    """Load user alerts from file"""
    try:
        with open(USER_ALERTS_FILE, 'r') as f:
            return json.load(f)
    except FileNotFoundError:
        return {}


def save_user_alerts(alerts):
    """Save user alerts to file"""
    with open(USER_ALERTS_FILE, 'w') as f:
        json.dump(alerts, f, indent=2)


def load_portfolios():
    """Load portfolios from file — persists across bot restarts"""
    try:
        with open(PORTFOLIO_FILE, 'r') as f:
            return json.load(f)
    except FileNotFoundError:
        return {}


def save_portfolios(portfolios):
    """Save portfolios to file"""
    with open(PORTFOLIO_FILE, 'w') as f:
        json.dump(portfolios, f, indent=2)


def _get_item_icon_url(item_id: int, item_name: str = "", icon: str = ""):
    """Generate OSRS Wiki icon URL"""
    if icon:
        return f"https://oldschool.runescape.wiki/images/{icon.replace(' ', '_')}"
    return f"https://oldschool.runescape.wiki/images/{item_name.replace(' ', '_')}_detailed.png"


class Trading(commands.Cog):
    def __init__(self, bot: commands.Bot):
        self.bot = bot
        self.check_user_alerts.start()

    def cog_unload(self):
        self.check_user_alerts.cancel()

    async def item_name_autocomplete(
        self,
        interaction: discord.Interaction,
        current: str,
    ) -> List[app_commands.Choice[str]]:
        """Autocomplete for item names"""
        if not current:
            return []

        api_client = self.bot.wiki_client
        matches = []
        current_lower = current.lower()

        for item_id, item_data in list(api_client.item_by_name.items())[:20]:
            if current_lower in item_id:
                matches.append(app_commands.Choice(name=item_data, value=item_data))

        return matches[:25]

    @app_commands.command(name="flip", description="Calculate flip profit for an item with quantity")
    @app_commands.describe(
        item_name="Name of the item to flip",
        quantity="How many to flip (default: buy limit)",
        buy_price="Custom buy price (default: current instant buy)",
        sell_price="Custom sell price (default: current instant sell)"
    )
    @app_commands.autocomplete(item_name=item_name_autocomplete)
    @app_commands.checks.cooldown(1, 3.0, key=lambda i: (i.guild_id, i.user.id))
    async def flip_command(
        self,
        interaction: discord.Interaction,
        item_name: str,
        quantity: Optional[int] = None,
        buy_price: Optional[int] = None,
        sell_price: Optional[int] = None
    ):
        """Calculate flip profit for an item"""
        await interaction.response.defer()

        item = self.bot.wiki_client.find_item_by_name(item_name)
        if not item:
            await interaction.followup.send(f"Couldn't find item matching '{item_name}'. Try a different name.")
            return

        data = self.bot.wiki_client._build_item_data(item['id'])
        if not data or data['buy_price'] == 0:
            await interaction.followup.send(f"No price data for **{item['name']}** right now.")
            return

        bp = buy_price or data['buy_price']
        sp = sell_price or data['sell_price']
        buy_limit = data.get('ge_limit', 0) or 100
        qty = quantity or buy_limit

        # GE tax: 2% of sell price, capped at 5M per item
        tax_per_item = min(int(sp * GE_TAX_RATE), GE_MAX_TAX)
        profit_per_item = sp - bp - tax_per_item
        total_profit = profit_per_item * qty
        total_cost = bp * qty
        roi = (total_profit / total_cost * 100) if total_cost > 0 else 0

        # Estimate fill time based on volume
        vph = vol_per_hour(data)
        fill_str = "Unknown"
        if vph > 0:
            fill_hours = qty / vph
            if fill_hours < 1:
                fill_str = f"~{int(fill_hours * 60)} min"
            else:
                fill_str = f"~{fill_hours:.1f} hrs"

        color = OSRS_GREEN if profit_per_item > 0 else OSRS_RED
        embed = discord.Embed(title=f"Flip Calculator — {item['name']}", color=color, timestamp=datetime.now())
        embed.set_thumbnail(url=_get_item_icon_url(item['id'], item.get('name', '')))
        embed.add_field(name="Buy Price", value=f"{bp:,} gp", inline=True)
        embed.add_field(name="Sell Price", value=f"{sp:,} gp", inline=True)
        embed.add_field(name="GE Tax", value=f"{tax_per_item:,} gp/ea", inline=True)
        embed.add_field(name="Profit/Item", value=f"{profit_per_item:,} gp", inline=True)
        embed.add_field(name="Quantity", value=f"{qty:,}", inline=True)
        embed.add_field(name="Est. Fill Time", value=fill_str, inline=True)
        embed.add_field(name="Total Profit", value=f"**{total_profit:,} gp**", inline=True)
        embed.add_field(name="Capital Required", value=f"{total_cost:,} gp", inline=True)
        embed.add_field(name="ROI", value=f"{roi:.1f}%", inline=True)

        if total_profit > 0 and vph > 0:
            gp_per_hour = int(total_profit / max(qty / vph, 0.25))
            embed.add_field(name="GP/Hour", value=f"~{gp_per_hour:,} gp/hr", inline=False)

        embed.set_footer(text="Grand Flip Out | Prices from OSRS Wiki")
        await interaction.followup.send(embed=embed)

    @app_commands.command(name="watchlist", description="Manage your personal watchlist")
    @app_commands.describe(action="add, remove, or show", item_name="Item name")
    @app_commands.autocomplete(item_name=item_name_autocomplete)
    async def watchlist_command(self, interaction: discord.Interaction, action: str = "show", item_name: str = None):
        """Manage your watchlist"""
        await interaction.response.defer()

        uid = str(interaction.user.id)
        wl = load_watchlists()
        if uid not in wl:
            wl[uid] = []

        action = action.lower()

        if action == "show":
            if not wl[uid]:
                await interaction.followup.send(
                    "Your watchlist is empty!\n"
                    "**Get started:** `/watchlist add Dragon bones` to track your first item.\n"
                    "Or try `/top` to see profitable flips and add ones you like."
                )
                return

            # Create paginated embeds (4 items per page)
            pages = []
            page_size = 4
            for page_num in range(0, len(wl[uid]), page_size):
                page_items = wl[uid][page_num:page_num + page_size]
                embed = discord.Embed(title=f"{interaction.user.name}'s Watchlist", color=OSRS_GOLD, timestamp=datetime.now())
                embed.description = f"Page {len(pages) + 1}/{(len(wl[uid]) + page_size - 1) // page_size}"

                for wn in page_items:
                    item = self.bot.wiki_client.find_item_by_name(wn)
                    if item:
                        v = get_verdict(item)
                        rp = realistic_4h_profit(item)
                        embed.add_field(
                            name=f"{VERDICT_EMOJI.get(v,'')} {wn}",
                            value=(
                                f"Buy: **{format_gp(item['buy_price'])}** → Sell: **{format_gp(item['sell_price'])}**\n"
                                f"Profit: {format_gp(item['margin'])}/item → {format_gp(rp)}/4h\n"
                                f"Vol: {format_vol_per_hour(item)}"
                            ),
                            inline=True
                        )
                embed.set_footer(text="Grand Flip Out")
                pages.append(embed)

            if len(pages) > 1:
                view = PaginatedView(pages, interaction.user.id)
            else:
                view = None

            await interaction.followup.send(embed=pages[0], view=view)

        elif action == "add":
            if not item_name:
                await interaction.followup.send("Specify an item name!")
                return

            item = self.bot.wiki_client.find_item_by_name(item_name)
            if not item:
                await interaction.followup.send(f"Couldn't find '{item_name}'.")
                return

            n = item['name']
            if n not in wl[uid]:
                wl[uid].append(n)
                save_watchlists(wl)
                await interaction.followup.send(
                    f"✅ Added **{n}** to your watchlist! ({len(wl[uid])} items tracked)\n"
                    f"Use `/watchlist show` to see live prices for all your items."
                )
            else:
                await interaction.followup.send(f"**{n}** is already on your watchlist.")

        elif action == "remove":
            if not item_name:
                await interaction.followup.send("Specify an item name!")
                return

            item = self.bot.wiki_client.find_item_by_name(item_name)
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

    @app_commands.command(name="alert", description="Manage your personal price alerts")
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
        self,
        interaction: discord.Interaction,
        action: str = "list",
        item: Optional[str] = None,
        margin_pct: int = 2,
        min_profit: int = 100000,
        min_volume: int = 5000,
        target_price: int = 0
    ):
        """Manage price alerts"""
        await interaction.response.defer()

        uid = str(interaction.user.id)
        alerts = load_user_alerts()
        if uid not in alerts:
            alerts[uid] = []

        action = action.lower()

        if action == "list":
            if not alerts[uid]:
                await interaction.followup.send(
                    "You don't have any alerts set!\n"
                    "**Set one up:** `/alert add Dragon bones` — you'll get a DM when conditions are met.\n"
                    "Customize with `margin_pct`, `min_profit`, and `target_price` options."
                )
                return

            # Create paginated embeds (4 alerts per page)
            pages = []
            page_size = 4
            for page_num in range(0, len(alerts[uid]), page_size):
                page_items = alerts[uid][page_num:page_num + page_size]
                embed = discord.Embed(title=f"{interaction.user.name}'s Price Alerts", color=OSRS_BLUE, timestamp=datetime.now())
                embed.description = f"Page {len(pages) + 1}/{(len(alerts[uid]) + page_size - 1) // page_size}"

                for idx, alert in enumerate(page_items, page_num + 1):
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
                pages.append(embed)

            if len(pages) > 1:
                view = PaginatedView(pages, interaction.user.id)
            else:
                view = None

            await interaction.followup.send(embed=pages[0], view=view)

        elif action == "add":
            if not item:
                await interaction.followup.send("Specify an item name!")
                return

            item_data = self.bot.wiki_client.find_item_by_name(item)
            if not item_data:
                await interaction.followup.send(f"Couldn't find '{item}'.")
                return

            item_id = item_data['id']
            item_name = item_data['name']

            # Check if already exists
            for existing in alerts[uid]:
                if existing.get('item_id') == item_id:
                    await interaction.followup.send(f"You already have an alert for **{item_name}**!")
                    return

            alert = {
                'item_id': item_id,
                'item_name': item_name,
                'margin_pct': margin_pct,
                'min_profit': min_profit,
                'min_volume': min_volume,
                'target_price': target_price,
                'enabled': True,
                'created_at': datetime.now().isoformat()
            }
            alerts[uid].append(alert)
            save_user_alerts(alerts)

            await interaction.followup.send(
                f"✅ Created alert for **{item_name}**:\n"
                f"Margin ≥ {margin_pct}% | Profit ≥ {format_gp(min_profit)} | Volume ≥ {min_volume:,}/day"
            )

        elif action == "remove":
            if not item:
                await interaction.followup.send("Specify an item name!")
                return

            item_data = self.bot.wiki_client.find_item_by_name(item)
            if not item_data:
                await interaction.followup.send(f"Couldn't find '{item}'.")
                return

            item_id = item_data['id']
            has_alert = any(a.get('item_id') == item_id for a in alerts[uid])

            if not has_alert:
                await interaction.followup.send(f"You don't have an alert for **{item_data['name']}**.")
                return

            # Confirmation view
            view = ConfirmView(interaction.user.id)
            await interaction.followup.send(f"Are you sure you want to remove the alert for **{item_data['name']}**?", view=view)

            try:
                await asyncio.wait_for(view.wait(), timeout=30.0)
            except asyncio.TimeoutError:
                return

            if view.value:
                alerts[uid] = [a for a in alerts[uid] if a.get('item_id') != item_id]
                save_user_alerts(alerts)
                await interaction.followup.send(f"✅ Removed alert for **{item_data['name']}**.")
            else:
                await interaction.followup.send("Cancelled.")

        else:
            await interaction.followup.send("Use 'add', 'remove', or 'list'.")

    @app_commands.command(name="portfolio", description="Track your flip positions and P&L")
    @app_commands.describe(
        action="add, view, or remove",
        item="Item name",
        quantity="Number of items",
        buy_price="Price you bought at"
    )
    @app_commands.autocomplete(item=item_name_autocomplete)
    async def portfolio_command(
        self,
        interaction: discord.Interaction,
        action: str,
        item: Optional[str] = None,
        quantity: Optional[int] = None,
        buy_price: Optional[int] = None
    ):
        """Track your flip positions"""
        await interaction.response.defer()

        uid = str(interaction.user.id)
        portfolios = load_portfolios()
        if uid not in portfolios:
            portfolios[uid] = []

        action = action.lower()

        if action == "view":
            if not portfolios[uid]:
                await interaction.followup.send(
                    "Your portfolio is empty!\n"
                    "**Track a flip:** `/portfolio add Dragon bones 100 2500` to log a buy.\n"
                    "Then `/portfolio view` to see live P&L as prices change."
                )
                return

            # Create paginated embeds (3 positions per page)
            pages = []
            page_size = 3
            total_invested = 0
            total_current = 0

            # Calculate totals
            for pos in portfolios[uid]:
                item_id = pos.get('item_id')
                qty = pos.get('quantity', 0)
                buy_p = pos.get('buy_price', 0)
                item_data = self.bot.wiki_client._build_item_data(item_id)
                current_p = item_data.get('sell_price', 0) if item_data else 0
                invested = buy_p * qty
                current_val = current_p * qty
                total_invested += invested
                total_current += current_val

            for page_num in range(0, len(portfolios[uid]), page_size):
                page_items = portfolios[uid][page_num:page_num + page_size]
                embed = discord.Embed(title=f"{interaction.user.name}'s Portfolio", color=OSRS_GOLD, timestamp=datetime.now())
                embed.description = f"Page {len(pages) + 1}/{(len(portfolios[uid]) + page_size - 1) // page_size}"

                for pos in page_items:
                    item_id = pos.get('item_id')
                    item_name = pos.get('item_name', 'Unknown')
                    qty = pos.get('quantity', 0)
                    buy_p = pos.get('buy_price', 0)

                    item_data = self.bot.wiki_client._build_item_data(item_id)
                    current_p = item_data.get('sell_price', 0) if item_data else 0

                    invested = buy_p * qty
                    current_val = current_p * qty
                    pnl = current_val - invested

                    pnl_pct = (pnl / invested * 100) if invested > 0 else 0
                    pnl_emoji = "🟢" if pnl > 0 else "🔴" if pnl < 0 else "⚪"

                    embed.add_field(
                        name=f"{pnl_emoji} {item_name} x{qty:,}",
                        value=f"Bought: {format_gp(buy_p)}/ea → Current: {format_gp(current_p)}/ea\nP&L: {format_gp(pnl)} ({pnl_pct:+.1f}%)",
                        inline=False
                    )

                pages.append(embed)

            # Add summary to last page
            total_pnl = total_current - total_invested
            total_pnl_pct = (total_pnl / total_invested * 100) if total_invested > 0 else 0
            pages[-1].add_field(
                name="Portfolio Summary",
                value=f"Invested: {format_gp(total_invested)}\nCurrent: {format_gp(total_current)}\nTotal P&L: {format_gp(total_pnl)} ({total_pnl_pct:+.1f}%)",
                inline=False
            )
            pages[-1].set_footer(text="Grand Flip Out | Portfolio Tracker")

            if len(pages) > 1:
                view = PaginatedView(pages, interaction.user.id)
            else:
                view = None

            await interaction.followup.send(embed=pages[0], view=view)

        elif action == "add":
            if not all([item, quantity, buy_price]):
                await interaction.followup.send("Specify item name, quantity, and buy price!")
                return

            item_data = self.bot.wiki_client.find_item_by_name(item)
            if not item_data:
                await interaction.followup.send(f"Couldn't find '{item}'.")
                return

            position = {
                'item_id': item_data['id'],
                'item_name': item_data['name'],
                'quantity': quantity,
                'buy_price': buy_price,
                'timestamp': datetime.now().isoformat()
            }
            portfolios[uid].append(position)
            save_portfolios(portfolios)
            current_item = self.bot.wiki_client._build_item_data(item_data['id'])
            current_p = current_item.get('sell_price', 0) if current_item else 0
            await interaction.followup.send(
                f"✅ Added {quantity:,}x **{item_data['name']}** @ {format_gp(buy_price)} to portfolio\n"
                f"Current sell price: {format_gp(current_p)} — use `/portfolio view` to track P&L"
            )

        elif action == "remove":
            if not item:
                await interaction.followup.send("Specify an item name!")
                return

            item_data = self.bot.wiki_client.find_item_by_name(item)
            if not item_data:
                await interaction.followup.send(f"Couldn't find '{item}'.")
                return

            has_position = any(p.get('item_id') == item_data['id'] for p in portfolios[uid])
            if not has_position:
                await interaction.followup.send(f"**{item_data['name']}** not in portfolio")
                return

            # Confirmation view
            view = ConfirmView(interaction.user.id)
            await interaction.followup.send(f"Are you sure you want to remove **{item_data['name']}** from your portfolio?", view=view)

            try:
                await asyncio.wait_for(view.wait(), timeout=30.0)
            except asyncio.TimeoutError:
                return

            if view.value:
                portfolios[uid] = [p for p in portfolios[uid] if p.get('item_id') != item_data['id']]
                save_portfolios(portfolios)
                await interaction.followup.send(f"✅ Removed **{item_data['name']}** from portfolio")
            else:
                await interaction.followup.send("Cancelled.")

        else:
            await interaction.followup.send("Use 'add', 'view', or 'remove'.")

    @tasks.loop(minutes=2)
    async def check_user_alerts(self):
        """Check user-defined alerts and send DMs if conditions are met"""
        try:
            now = datetime.now().timestamp()
            alerts = load_user_alerts()

            # Prune hourly DM counters
            for uid in _user_dms_this_hour:
                _user_dms_this_hour[uid] = [t for t in _user_dms_this_hour[uid] if now - t < 3600]

            for uid, user_alerts in alerts.items():
                if len(_user_dms_this_hour.get(uid, [])) >= USER_DM_MAX_PER_HOUR:
                    continue

                for alert in user_alerts:
                    if not alert.get('enabled', True):
                        continue

                    item_id = alert.get('item_id')
                    item_name = alert.get('item_name', 'Unknown')

                    # Build item data
                    item_data = self.bot.wiki_client._build_item_data(item_id)
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

                    daily_vol = vol_per_hour(item_data) * 24
                    if daily_vol < min_volume:
                        continue

                    realistic_profit = realistic_4h_profit(item_data)
                    if realistic_profit < min_profit:
                        continue

                    buy_price = item_data.get('buy_price', 0)
                    sell_price = item_data.get('sell_price', 0)
                    if buy_price > 0:
                        margin_pct_actual = ((sell_price - buy_price) / buy_price) * 100
                        if margin_pct_actual < margin_pct:
                            continue

                    if target_price > 0 and buy_price > target_price:
                        continue

                    # All conditions met! Send DM
                    embed = discord.Embed(
                        title=f"🔔 Price Alert: {item_name}",
                        color=OSRS_GREEN,
                        timestamp=datetime.now()
                    )
                    embed.add_field(name="Buy", value=format_gp(buy_price), inline=True)
                    embed.add_field(name="Sell", value=format_gp(sell_price), inline=True)
                    embed.add_field(name="Profit/Item", value=format_gp(item_data['margin']), inline=True)

                    try:
                        user = await self.bot.fetch_user(int(uid))
                        await user.send(embed=embed)

                        _user_alert_last_triggered[key] = now
                        if uid not in _user_dms_this_hour:
                            _user_dms_this_hour[uid] = []
                        _user_dms_this_hour[uid].append(now)

                        alert['last_triggered'] = datetime.now().isoformat()
                        save_user_alerts(alerts)
                    except discord.Forbidden:
                        # User has DMs disabled — disable this alert and log it
                        alert['enabled'] = False
                        alert['disabled_reason'] = 'DMs are closed — enable DMs from server members to receive alerts'
                        save_user_alerts(alerts)
                        print(f"Alert disabled for user {uid}: DMs closed")
                    except discord.NotFound:
                        # User left or deleted account — remove their alerts
                        alerts.pop(uid, None)
                        save_user_alerts(alerts)
                        print(f"Removed alerts for deleted user {uid}")
                        break
                    except Exception as e:
                        print(f"Failed to DM user {uid} for {item_name}: {e}")

        except Exception as e:
            print(f"User alert check error: {e}")

    @check_user_alerts.before_loop
    async def before_check_user_alerts(self):
        await self.bot.wait_until_ready()


async def setup(bot: commands.Bot):
    await bot.add_cog(Trading(bot))
