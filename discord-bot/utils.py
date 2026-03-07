"""
Shared utilities for Discord bot cogs.
Formatting, constants, and helper functions.
"""
from typing import Optional

# OSRS colors
OSRS_GOLD = 0xFF981F
OSRS_GREEN = 0x00FF00
OSRS_RED = 0xFF0000
OSRS_BLUE = 0x0099FF

# GE Constants
GE_TAX_RATE = 0.02
GE_MAX_TAX = 5000000

# Never flip bonds — that's unethical. Exclude from all recommendations.
EXCLUDED_ITEMS = {'old school bond'}

def is_excluded(name: str) -> bool:
    """Check if an item is excluded from recommendations."""
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


# ============================================
# FORMATTING FUNCTIONS
# ============================================

def format_gp(value):
    """Full exact numbers with commas — no rounding, no abbreviations.
    When you're placing a GE offer, you need the exact GP figure."""
    if value is None:
        return "N/A"
    return f"{int(value):,} gp"


def format_gp_short(value):
    """Abbreviated version for tight spaces (status bar, compact summaries)."""
    if value is None:
        return "N/A"
    if abs(value) >= 1000000000:
        return f"{value/1000000000:.1f}B"
    if abs(value) >= 1000000:
        return f"{value/1000000:.1f}M"
    if abs(value) >= 1000:
        return f"{value/1000:.0f}K"
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
    if vph < 12:
        return "Dead"
    if vph < 60 or profit_4h < 10000:
        return "Slow"
    if profit_4h < 100000 or vph < 200:
        return "Decent"
    if profit_4h < 500000:
        return "Good Flip"
    return "Great Flip"


def format_vol_per_hour(item_data):
    vph = vol_per_hour(item_data)
    if vph == 0:
        return "0/hr (Dead)"
    if vph < 12:
        return f"~{vph}/hr (Very Low)"
    if vph < 60:
        return f"~{vph:,}/hr (Low)"
    if vph < 500:
        return f"~{vph:,}/hr (Medium)"
    return f"~{vph:,}/hr (High)"


VERDICT_EMOJI = {
    "Great Flip": "🟢", "Good Flip": "🟡", "Decent": "🟠", "Slow": "🔴", "Dead": "⚫"
}


# ============================================
# INTERACTIVE VIEWS FOR DISCORD UI
# ============================================

import discord

class PaginatedView(discord.ui.View):
    """Button-based pagination for list commands"""
    def __init__(self, pages, author_id, timeout=120):
        super().__init__(timeout=timeout)
        self.pages = pages
        self.current = 0
        self.author_id = author_id

    @discord.ui.button(label="◀ Prev", style=discord.ButtonStyle.secondary)
    async def prev_button(self, interaction: discord.Interaction, button: discord.ui.Button):
        if interaction.user.id != self.author_id:
            return await interaction.response.send_message("Not your command", ephemeral=True)
        self.current = max(0, self.current - 1)
        await interaction.response.edit_message(embed=self.pages[self.current], view=self)

    @discord.ui.button(label="▶ Next", style=discord.ButtonStyle.secondary)
    async def next_button(self, interaction: discord.Interaction, button: discord.ui.Button):
        if interaction.user.id != self.author_id:
            return await interaction.response.send_message("Not your command", ephemeral=True)
        self.current = min(len(self.pages) - 1, self.current + 1)
        await interaction.response.edit_message(embed=self.pages[self.current], view=self)

    async def on_timeout(self):
        for item in self.children:
            item.disabled = True


class ConfirmView(discord.ui.View):
    """Confirmation buttons for destructive actions"""
    def __init__(self, author_id, timeout=30):
        super().__init__(timeout=timeout)
        self.value = None
        self.author_id = author_id

    @discord.ui.button(label="Confirm", style=discord.ButtonStyle.danger)
    async def confirm(self, interaction: discord.Interaction, button: discord.ui.Button):
        if interaction.user.id != self.author_id:
            return await interaction.response.send_message("Not your command", ephemeral=True)
        self.value = True
        await interaction.response.defer()
        self.stop()

    @discord.ui.button(label="Cancel", style=discord.ButtonStyle.secondary)
    async def cancel(self, interaction: discord.Interaction, button: discord.ui.Button):
        if interaction.user.id != self.author_id:
            return await interaction.response.send_message("Not your command", ephemeral=True)
        self.value = False
        await interaction.response.defer()
        self.stop()

    async def on_timeout(self):
        for item in self.children:
            item.disabled = True
