"""
Analysis & Learning Commands Cog
/analyze, /floors, /calc, /stats - Educational analysis and market insights
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
    get_item_intelligence, ITEM_INTELLIGENCE, PaginatedView
)


class Analysis(commands.Cog):
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

    @app_commands.command(name="analyze", description="Analyze an item: learn the framework for thinking critically about prices")
    @app_commands.describe(item="Item name (e.g., Blood rune, Eclectic impling jar)")
    @app_commands.autocomplete(item=item_name_autocomplete)
    async def analyze_intel_command(self, interaction: discord.Interaction, item: str):
        """Educational analysis tool: learn how to evaluate items"""
        await interaction.response.defer()

        # Find the item
        item_data = self.bot.wiki_client.find_item_by_name(item)
        if not item_data:
            await interaction.followup.send(f"❌ Item '{item}' not found in market data.", ephemeral=True)
            return

        # Get intelligence data
        intel = get_item_intelligence(item_data['name'])
        if not intel:
            await interaction.followup.send(
                f"ℹ️ No special intelligence available for **{item_data['name']}**. Use `/price` for current market data.",
                ephemeral=True
            )
            return

        # Build embed
        embed = discord.Embed(
            title=f"📚 How to Think About: {item_data['name']}",
            description=intel.get('analysis_example', 'N/A'),
            color=OSRS_GOLD
        )

        # Floor price and reason
        if intel.get('floor_price'):
            pct_to_floor = (
                ((item_data['current_price'] - intel['floor_price']) / intel['floor_price'] * 100)
                if intel['floor_price'] > 0 else 0
            )
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
            value=(
                f"Buy: {format_gp(item_data['buy_price'])}\n"
                f"Sell: {format_gp(item_data['sell_price'])}\n"
                f"Margin: {format_gp(item_data['margin'])}"
            ),
            inline=True
        )

        embed.set_footer(text="This is educational analysis, not trading advice. Always do your own research.")
        embed.timestamp = datetime.now()

        await interaction.followup.send(embed=embed)

    @app_commands.command(name="floors", description="Show items trading near known NPC buy prices (floor prices)")
    async def floors_command(self, interaction: discord.Interaction):
        """Display items currently trading near their known price floors"""
        await interaction.response.defer()

        api_client = self.bot.wiki_client
        near_floor_items = []

        for item_name, intel in ITEM_INTELLIGENCE.items():
            if not intel.get('floor_price'):
                continue

            # Find item data
            item_data = api_client.find_item_by_name(item_name)
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

        # Create paginated embeds (4 items per page)
        pages = []
        page_size = 4
        for page_num in range(0, len(near_floor_items), page_size):
            page_items = near_floor_items[page_num:page_num + page_size]
            embed = discord.Embed(
                title="🏛️ Items Near Floor Prices",
                description=f"**{len(near_floor_items)}** items currently trading within 10% of known NPC buy prices",
                color=OSRS_GOLD
            )
            embed.description += f"\nPage {len(pages) + 1}/{(len(near_floor_items) + page_size - 1) // page_size}"

            for item in page_items:
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

            embed.set_footer(text="These items are currently trading near known NPC buy prices. See the data and decide for yourself.")
            embed.timestamp = datetime.now()
            pages.append(embed)

        if len(pages) > 1:
            view = PaginatedView(pages, interaction.user.id)
        else:
            view = None

        await interaction.followup.send(embed=pages[0], view=view)

    @app_commands.command(name="calc", description="Calculate flip profit for an item with realistic market analysis")
    @app_commands.describe(item_name="Name of the item")
    @app_commands.autocomplete(item_name=item_name_autocomplete)
    @app_commands.checks.cooldown(1, 3.0, key=lambda i: (i.guild_id, i.user.id))
    async def calc_command(self, interaction: discord.Interaction, item_name: str):
        """Calculate realistic flip profit"""
        await interaction.response.defer()

        item = self.bot.wiki_client.find_item_by_name(item_name)
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

        # Prices — exact numbers
        embed.add_field(name="💰 Buy At", value=f"**{format_gp(item['buy_price'])}**", inline=True)
        embed.add_field(name="💰 Sell At", value=f"**{format_gp(item['sell_price'])}**", inline=True)
        embed.add_field(
            name="Profit/Item",
            value=f"**{format_gp(item['margin'])}** (after {format_gp(item['ge_tax'])} tax)",
            inline=True
        )

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
        if margin_pct > 15 and vph < 24:
            risk_score += 35
            risk_reasons.append('High margin + low vol')
        elif margin_pct > 10 and vph < 60:
            risk_score += 20
            risk_reasons.append('Elevated margin/vol ratio')
        if ge_limit and ge_limit <= 8:
            risk_score += 15
            risk_reasons.append(f'Low GE limit ({ge_limit})')

        risk_level = '🚨 HIGH' if risk_score >= 50 else '⚠️ MEDIUM' if risk_score >= 25 else '✅ LOW'
        if risk_score > 0:
            embed.add_field(
                name="Manipulation Risk",
                value=f"{risk_level} ({risk_score}/100)\n{' · '.join(risk_reasons)}",
                inline=False
            )

        # Stickiness trap warning
        if margin_pct > 5 and vph < 12:
            embed.add_field(
                name="🪤 STICKINESS TRAP",
                value="Great margin but near-zero volume. Your GP will be stuck — avoid this flip.",
                inline=False
            )

        if verdict == "Dead":
            embed.add_field(
                name="⚠️ Warning",
                value="Almost nobody is trading this item. Don't expect your offers to fill.",
                inline=False
            )
        elif verdict == "Slow":
            embed.add_field(
                name="💤 Note",
                value="Low activity. Your offers might take a while to fill.",
                inline=False
            )

        embed.set_footer(text="Grand Flip Out | Based on real trading volume")
        embed.timestamp = datetime.now()
        await interaction.followup.send(embed=embed)

    @app_commands.command(name="stats", description="Show bot statistics and market overview")
    async def stats_command(self, interaction: discord.Interaction):
        """Show market statistics and bot overview"""
        await interaction.response.defer()

        api_client = self.bot.wiki_client
        item_stats = getattr(self.bot, 'item_stats', {})

        embed = discord.Embed(
            title="📊 Market Statistics",
            color=OSRS_GOLD,
            timestamp=datetime.now()
        )

        # Overall market health
        total_items = len(api_client.item_mapping)
        items_with_prices = len(api_client.latest_prices)
        items_with_volume = len(api_client.volume_data)

        embed.add_field(
            name="📦 Tracking",
            value=f"**{items_with_prices:,}** prices\n**{items_with_volume:,}** volumes\n**{total_items:,}** total items",
            inline=True
        )

        # Profitable items
        profitable = 0
        avg_margin = 0
        total_margin = 0
        best_flip = None
        best_profit = 0

        for item_id in api_client.latest_prices:
            item_data = api_client._build_item_data(item_id)
            if item_data and item_data.get('buy_price', 0) > 0:
                margin = item_data.get('margin', 0)
                if margin > 0:
                    profitable += 1
                    total_margin += margin
                    profit_4h = realistic_4h_profit(item_data)
                    if profit_4h > best_profit:
                        best_profit = profit_4h
                        best_flip = item_data

        if profitable > 0:
            avg_margin = total_margin // profitable
            embed.add_field(
                name="Profitable Items",
                value=f"**{profitable:,}** flips\n**{format_gp(avg_margin)}** avg margin",
                inline=True
            )

        # Tracked stats
        tracked_stats = len(item_stats)
        embed.add_field(
            name="📈 History",
            value=f"**{tracked_stats:,}** items with history",
            inline=True
        )

        # Best flip right now
        if best_flip:
            embed.add_field(
                name="Best Flip Right Now",
                value=f"**{best_flip['name']}**\n{format_gp(best_profit)}/item",
                inline=False
            )

        embed.set_footer(text="Grand Flip Out | Live OSRS Wiki data")
        await interaction.followup.send(embed=embed)


async def setup(bot: commands.Bot):
    await bot.add_cog(Analysis(bot))
