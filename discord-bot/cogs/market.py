"""
Market Data Commands Cog
/price, /top, /dumps, /hotlist, /compare - Price fetching and market analysis
"""
import discord
from discord.ext import commands
from discord import app_commands
from datetime import datetime
from typing import Optional, List

from utils import (
    OSRS_GOLD, OSRS_GREEN, OSRS_RED,
    format_gp, format_gp_short, format_vol_per_hour,
    vol_per_hour, realistic_buys, realistic_4h_profit, get_verdict, VERDICT_EMOJI,
    get_item_intelligence, PaginatedView
)


class ItemDetailView(discord.ui.View):
    """Interactive buttons for item details"""
    def __init__(self, item_name, item_data, author_id, bot, timeout=180):
        super().__init__(timeout=timeout)
        self.item_name = item_name
        self.item_data = item_data
        self.author_id = author_id
        self.bot = bot

    @discord.ui.button(label="📊 Analysis", style=discord.ButtonStyle.primary)
    async def analyze_btn(self, interaction: discord.Interaction, button: discord.ui.Button):
        if interaction.user.id != self.author_id:
            return await interaction.response.send_message("Not your command", ephemeral=True)

        await interaction.response.defer(ephemeral=True)
        intel = get_item_intelligence(self.item_name)
        if not intel:
            await interaction.followup.send(f"No special intelligence available for **{self.item_name}**.", ephemeral=True)
            return

        embed = discord.Embed(
            title=f"📚 How to Think About: {self.item_name}",
            description=intel.get('analysis_example', 'N/A'),
            color=OSRS_GOLD
        )
        if intel.get('floor_price'):
            pct_to_floor = (
                ((self.item_data['current_price'] - intel['floor_price']) / intel['floor_price'] * 100)
                if intel['floor_price'] > 0 else 0
            )
            embed.add_field(
                name="🏛️ Price Floor",
                value=f"**{format_gp(intel['floor_price'])}/ea**\n_{intel.get('floor_reason', '')}_",
                inline=False
            )
            embed.add_field(name="Distance to Floor", value=f"**{pct_to_floor:+.1f}%**", inline=True)

        if intel.get('demand_drivers'):
            embed.add_field(
                name="🔍 Demand Drivers",
                value="\n".join(f"• {d}" for d in intel['demand_drivers'][:3]),
                inline=False
            )

        embed.set_footer(text="Educational analysis, not trading advice")
        await interaction.followup.send(embed=embed, ephemeral=True)

    @discord.ui.button(label="⭐ Watchlist", style=discord.ButtonStyle.secondary)
    async def watchlist_btn(self, interaction: discord.Interaction, button: discord.ui.Button):
        if interaction.user.id != self.author_id:
            return await interaction.response.send_message("Not your command", ephemeral=True)

        await interaction.response.defer(ephemeral=True)
        from cogs.trading import load_watchlists, save_watchlists

        uid = str(interaction.user.id)
        wl = load_watchlists()
        if uid not in wl:
            wl[uid] = []

        if self.item_name not in wl[uid]:
            wl[uid].append(self.item_name)
            save_watchlists(wl)
            await interaction.followup.send(f"✅ Added **{self.item_name}** to your watchlist!", ephemeral=True)
        else:
            await interaction.followup.send(f"Already on your watchlist!", ephemeral=True)

    @discord.ui.button(label="🔔 Alert", style=discord.ButtonStyle.secondary)
    async def alert_btn(self, interaction: discord.Interaction, button: discord.ui.Button):
        if interaction.user.id != self.author_id:
            return await interaction.response.send_message("Not your command", ephemeral=True)

        await interaction.response.send_modal(AlertModal(self.item_name, self.author_id))

    async def on_timeout(self):
        for item in self.children:
            item.disabled = True


class AlertModal(discord.ui.Modal, title="Set Price Alert"):
    """Modal for setting price alerts"""
    price_input = discord.ui.TextInput(label="Alert Price (gp)", placeholder="e.g., 5000", required=True)
    direction_input = discord.ui.TextInput(label="Direction", placeholder="above or below", required=True)

    async def on_submit(self, interaction: discord.Interaction):
        try:
            price = int(self.price_input.value)
            direction = self.direction_input.value.lower().strip()

            if direction not in ['above', 'below']:
                await interaction.response.send_message("Direction must be 'above' or 'below'.", ephemeral=True)
                return

            await interaction.response.send_message(
                f"✅ Alert set: Notify when **{self.item_name}** goes {direction} **{price:,} gp**",
                ephemeral=True
            )
        except ValueError:
            await interaction.response.send_message("Price must be a valid number.", ephemeral=True)

    def __init__(self, item_name, author_id):
        super().__init__()
        self.item_name = item_name
        self.author_id = author_id


class Market(commands.Cog):
    def __init__(self, bot: commands.Bot):
        self.bot = bot

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

    @app_commands.command(name="price", description="Look up current price and profit margins for an item")
    @app_commands.describe(item_name="Name of the item to look up")
    @app_commands.autocomplete(item_name=item_name_autocomplete)
    @app_commands.checks.cooldown(1, 3.0, key=lambda i: (i.guild_id, i.user.id))
    async def price_command(self, interaction: discord.Interaction, item_name: str):
        """Look up item price and margins"""
        await interaction.response.defer()

        item = self.bot.wiki_client.find_item_by_name(item_name)
        if not item:
            await interaction.followup.send(f"Couldn't find item matching '{item_name}'. Try a different name.")
            return

        embed = await self._create_item_embed(item)
        view = ItemDetailView(item['name'], item, interaction.user.id, self.bot)
        await interaction.followup.send(embed=embed, view=view)

    @app_commands.command(name="top", description="Show top items by realistic profit")
    @app_commands.describe(sort="Sort by: realistic, margin, volume, or jti", limit="Number of items (1-50)")
    @app_commands.checks.cooldown(1, 3.0, key=lambda i: (i.guild_id, i.user.id))
    async def top_command(self, interaction: discord.Interaction, sort: str = "realistic", limit: int = 10):
        """Show top flips by various metrics"""
        await interaction.response.defer()

        sort = sort.lower() if sort else "realistic"
        if sort not in ["realistic", "margin", "volume", "jti"]:
            sort = "realistic"
        limit = max(1, min(limit, 50))

        items = self.bot.wiki_client.get_top_items(sort_by=sort if sort != "jti" else "realistic", limit=limit)
        if not items:
            await interaction.followup.send("No items found. Data may still be loading.")
            return

        sort_label = {
            "realistic": "Realistic 4h Profit",
            "margin": "Profit per Item",
            "volume": "Volume",
            "jti": "JTI Score"
        }[sort]

        # Create paginated embeds (5 items per page)
        pages = []
        page_size = 5
        for page_num in range(0, len(items), page_size):
            page_items = items[page_num:page_num + page_size]
            embed = discord.Embed(
                title=f"Top {limit} Flips — {sort_label}",
                color=OSRS_GOLD,
                timestamp=datetime.now()
            )
            embed.description = f"Page {len(pages) + 1}/{(len(items) + page_size - 1) // page_size}"

            for i, item in enumerate(page_items, page_num + 1):
                name = item.get('name', 'Unknown')
                verdict = get_verdict(item)
                emoji = VERDICT_EMOJI.get(verdict, "")
                rp = realistic_4h_profit(item)
                rb = realistic_buys(item)
                buy_p = item.get('buy_price', 0)
                sell_p = item.get('sell_price', 0)
                margin_val = item.get('margin', 0)

                line1 = f"{emoji} Buy: **{format_gp(buy_p)}** → Sell: **{format_gp(sell_p)}**"
                line2 = f"Profit: **{format_gp(margin_val)}**/item → **{format_gp(rp)}**/4h ({rb:,} buyable)"
                line3 = f"Vol: {format_vol_per_hour(item)}"

                # Add intelligence if available
                intel = get_item_intelligence(name)
                if intel and intel.get('floor_price'):
                    current_price = item.get('current_price', 0)
                    floor_price = intel['floor_price']
                    pct_to_floor = ((current_price - floor_price) / floor_price * 100) if floor_price > 0 else 0
                    if pct_to_floor <= 10:
                        line3 += f" | 💡 **NEAR FLOOR** ({pct_to_floor:+.0f}%)"

                embed.add_field(name=f"{i}. {name}", value=f"{line1}\n{line2}\n{line3}", inline=False)

            embed.set_footer(text="Grand Flip Out | OSRS Wiki API")
            pages.append(embed)

        if len(pages) > 1:
            view = PaginatedView(pages, interaction.user.id)
        else:
            view = None

        await interaction.followup.send(embed=pages[0], view=view)

    @app_commands.command(name="dumps", description="Show items with wide margins — buy cheap, sell normal, pocket the profit")
    @app_commands.checks.cooldown(1, 3.0, key=lambda i: (i.guild_id, i.user.id))
    async def dumps_command(self, interaction: discord.Interaction):
        """Show dump opportunities"""
        await interaction.response.defer()

        dumps = self.bot.wiki_client.get_dumps()
        if not dumps:
            price_count = len(self.bot.wiki_client.latest_prices)
            vol_count = len(self.bot.wiki_client.volume_data)
            mapping_count = len(self.bot.wiki_client.item_mapping)
            await interaction.followup.send(
                f"No dump opportunities right now. Market is stable.\n"
                f"-# Tracking {price_count:,} prices, {vol_count:,} volumes, {mapping_count:,} items mapped"
            )
            return

        # Create paginated embeds (5 items per page)
        pages = []
        page_size = 5
        for page_num in range(0, len(dumps), page_size):
            page_items = dumps[page_num:page_num + page_size]
            embed = discord.Embed(
                title="💰 Dump Opportunities",
                color=OSRS_GOLD,
                timestamp=datetime.now()
            )
            embed.description = f"Page {len(pages) + 1}/{(len(dumps) + page_size - 1) // page_size}"

            for i, item in enumerate(page_items, page_num + 1):
                drop_indicator = " 📉" if item.get('price_dropped') else ""
                buy_p = item.get('buy_price', 0)
                sell_p = item.get('sell_price', 0)
                can_buy = item.get('can_buy', 0)
                vol = item.get('volume', 0)

                embed.add_field(
                    name=f"{i}. {item['name']}{drop_indicator} — {format_gp(item['flip_profit'])}/item",
                    value=(
                        f"Buy: **{format_gp(buy_p)}** → Sell: **{format_gp(sell_p)}**\n"
                        f"Total profit: **{format_gp(item['total_profit'])}** ({can_buy:,} buyable)\n"
                        f"Vol (5m): {vol:,} trades"
                    ),
                    inline=False
                )

            embed.set_footer(text="Grand Flip Out | Wide margins indicate potential arbitrage")
            pages.append(embed)

        if len(pages) > 1:
            view = PaginatedView(pages, interaction.user.id)
        else:
            view = None

        await interaction.followup.send(embed=pages[0], view=view)

    @app_commands.command(name="compare", description="Compare two items side by side")
    @app_commands.describe(item1="First item", item2="Second item")
    @app_commands.autocomplete(item1=item_name_autocomplete, item2=item_name_autocomplete)
    async def compare_command(self, interaction: discord.Interaction, item1: str, item2: str):
        """Compare two items"""
        await interaction.response.defer()

        d1 = self.bot.wiki_client.find_item_by_name(item1)
        d2 = self.bot.wiki_client.find_item_by_name(item2)

        if not d1 or not d2:
            await interaction.followup.send("Couldn't find one or both items.")
            return

        embed = discord.Embed(title=f"{d1['name']} vs {d2['name']}", color=OSRS_GOLD, timestamp=datetime.now())
        embed.add_field(
            name="💰 Buy Price",
            value=f"{d1['name']}: **{format_gp(d1['buy_price'])}**\n{d2['name']}: **{format_gp(d2['buy_price'])}**",
            inline=True
        )
        embed.add_field(
            name="💰 Sell Price",
            value=f"{d1['name']}: **{format_gp(d1['sell_price'])}**\n{d2['name']}: **{format_gp(d2['sell_price'])}**",
            inline=True
        )
        embed.add_field(
            name="Profit/Item",
            value=f"{d1['name']}: **{format_gp(d1['margin'])}**\n{d2['name']}: **{format_gp(d2['margin'])}**",
            inline=True
        )

        rp1, rp2 = realistic_4h_profit(d1), realistic_4h_profit(d2)
        rb1, rb2 = realistic_buys(d1), realistic_buys(d2)
        v1, v2 = get_verdict(d1), get_verdict(d2)

        embed.add_field(
            name="Verdict",
            value=f"{d1['name']}: {VERDICT_EMOJI.get(v1,'')} {v1}\n{d2['name']}: {VERDICT_EMOJI.get(v2,'')} {v2}",
            inline=True
        )
        embed.add_field(
            name="4h Profit",
            value=f"{d1['name']}: **{format_gp(rp1)}** ({rb1:,} buyable)\n{d2['name']}: **{format_gp(rp2)}** ({rb2:,} buyable)",
            inline=True
        )
        embed.add_field(
            name="📊 Volume",
            value=f"{d1['name']}: {format_vol_per_hour(d1)}\n{d2['name']}: {format_vol_per_hour(d2)}",
            inline=True
        )

        winner = d1['name'] if rp1 > rp2 else d2['name']
        embed.add_field(name="Winner", value=f"**{winner}** makes more realistic profit", inline=False)
        embed.set_footer(text="Grand Flip Out | OSRS Wiki API")

        await interaction.followup.send(embed=embed)

    @app_commands.command(name="hotlist", description="Show the most profitable items from this session")
    @app_commands.checks.cooldown(1, 5.0, key=lambda i: (i.guild_id, i.user.id))
    async def hotlist_command(self, interaction: discord.Interaction):
        """Show hotlist - most profitable items currently"""
        await interaction.response.defer()

        api_client = self.bot.wiki_client
        item_stats = getattr(self.bot, 'item_stats', {})

        if not item_stats or len(item_stats) < 5:
            await interaction.followup.send("Not enough data yet. Check back in a few minutes.")
            return

        # Get items with highest profit potential
        candidates = []
        for item_id, stats in item_stats.items():
            item_data = api_client._build_item_data(item_id)
            if item_data and item_data.get('buy_price', 0) > 0:
                profit_4h = realistic_4h_profit(item_data)
                candidates.append((item_data['name'], profit_4h, item_data))

        candidates.sort(key=lambda x: x[1], reverse=True)
        top_items = candidates[:10]

        embed = discord.Embed(title="🔥 Hotlist - Most Profitable Right Now", color=OSRS_RED, timestamp=datetime.now())

        for i, (name, profit_4h, item) in enumerate(top_items, 1):
            verdict = get_verdict(item)
            emoji = VERDICT_EMOJI.get(verdict, "")
            embed.add_field(
                name=f"{i}. {emoji} {name}",
                value=f"4h profit: **{format_gp(profit_4h)}**\nMargin: {format_gp(item['margin'])}/item | Vol: {format_vol_per_hour(item)}",
                inline=True
            )

        embed.set_footer(text="Grand Flip Out | Updated continuously")
        await interaction.followup.send(embed=embed)

    async def _create_item_embed(self, item_data: dict) -> discord.Embed:
        """Create a detailed item embed"""
        name = item_data.get('name', 'Unknown')
        verdict = get_verdict(item_data)
        emoji = VERDICT_EMOJI.get(verdict, "")

        embed = discord.Embed(
            title=f"{emoji} {name}",
            color=OSRS_GOLD,
            timestamp=datetime.now()
        )

        embed.add_field(name="💰 Buy At", value=f"**{format_gp(item_data.get('buy_price', 0))}**", inline=True)
        embed.add_field(name="💰 Sell At", value=f"**{format_gp(item_data.get('sell_price', 0))}**", inline=True)
        embed.add_field(name="Profit/Item", value=f"**{format_gp(item_data.get('margin', 0))}**", inline=True)

        vph = vol_per_hour(item_data)
        embed.add_field(name="📊 Volume", value=f"{format_vol_per_hour(item_data)}", inline=True)
        embed.add_field(name="Verdict", value=f"{verdict}", inline=True)
        embed.add_field(name="4h Profit", value=f"**{format_gp(realistic_4h_profit(item_data))}**", inline=True)

        embed.set_footer(text="Grand Flip Out | OSRS Wiki API")
        return embed


async def setup(bot: commands.Bot):
    await bot.add_cog(Market(bot))
