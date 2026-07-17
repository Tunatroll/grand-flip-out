/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup("grandflipout")
public interface GrandFlipOutConfig extends Config
{
    // ==================== SECTIONS ====================
    // Order: the network opt-ins first (the decisions that matter), then the
    // local features, then hotkeys, then the rarely-touched knobs collapsed
    // under Advanced. Section constants keep their original string keys so no
    // user's saved values move.

    @ConfigSection(
        name = "grandflipout.com features",
        description = "The one master switch plus the opt-ins. Everything network lives here; "
            + "all of it is off by default and the plugin runs fully on local Wiki prices without it. "
            + "The Guide tab can enable these for you with the same disclosure.",
        position = 0
    )
    String intelligenceSection = "intelligence";

    @ConfigSection(
        name = "Advisor",
        description = "Suggests your next flip (item, price, quantity). Off by default — when on, "
            + "your current GE offers and approximate coins are sent to grandflipout.com to generate suggestions.",
        position = 1
    )
    String advisorSection = "advisor";

    @ConfigSection(
        name = "Flip Tracker",
        description = "Local flip tracking and P&L",
        position = 2
    )
    String flipTrackerSection = "flipTracker";

    @ConfigSection(
        name = "Overlay",
        description = "In-game GE overlay",
        position = 3
    )
    String overlaySection = "overlay";

    @ConfigSection(
        name = "Hotkeys",
        description = "All optional and unbound by default — every action is also reachable from "
            + "the panel. Bind only what you actually use.",
        position = 4
    )
    String hotkeysSection = "hotkeys";

    @ConfigSection(
        name = "Advanced",
        description = "Developer / rarely-needed knobs. The defaults are correct for normal use.",
        position = 5,
        closedByDefault = true
    )
    String advancedSection = "advanced";

    // ==================== GRANDFLIPOUT.COM FEATURES ====================

    @ConfigItem(
        keyName = "enableServerFunctionality",
        name = "Enable grandflipout.com functionality",
        description = "Master switch for ALL grandflipout.com network features (Advisor suggestions, "
            + "Server Intelligence signals, trade contribution, and the account/entitlement check). "
            + "Off by default — while off, the plugin runs entirely on local OSRS Wiki prices and makes "
            + "no requests to grandflipout.com. The individual feature toggles below only take effect "
            + "once this is enabled.",
        section = intelligenceSection,
        position = 0,
        warning = "This plugin submits your IP address to a 3rd party website not controlled or verified by the RuneLite Developers. "
            + "When enabled, your Grand Exchange offer and trade data (item, price, quantity, flip timings, and approximate coins) are sent to grandflipout.com. "
            + "If you link an account, your starred watchlist items sync to it (both directions)."
    )
    default boolean enableServerFunctionality()
    {
        return false;
    }

    @ConfigItem(
        keyName = "enableServerIntelligence",
        name = "Live intelligence (dumps, signals)",
        description = "Off by default. When enabled, fetch BUY/SELL/HOLD signals, VPIN alerts, dump predictions, and screener data from grandflipout.com. Read-only — your trades are not sent unless you also enable 'Contribute trades'.",
        section = intelligenceSection,
        position = 1
    )
    default boolean enableServerIntelligence()
    {
        return false;
    }

    @ConfigItem(
        keyName = "contributeTrades",
        name = "Sync flips (crowdsourced data)",
        description = "Off by default. Share your completed GE trades (item, price, quantity, buy/sell) "
            + "and completed-flip outcomes (paired buy/sell prices with placed-to-filled timings) "
            + "with grandflipout.com — this data trains the fill-time and recovery predictions the "
            + "plugin shows you. With a linked account your flips also sync to your own website "
            + "flip history + P&L and count toward the opt-in leaderboard; anonymous when unlinked. "
            + "Independent of the read-only intelligence above.",
        section = intelligenceSection,
        position = 2
    )
    default boolean contributeTrades()
    {
        return false;
    }

    // ==================== ADVISOR ====================

    @ConfigItem(
        keyName = "enableAdvisor",
        name = "Enable Advisor",
        description = "Off by default. When on, the Advisor tab suggests your next flip "
            + "(item, buy/sell price, quantity) based on your coins and free GE slots. Anonymous "
            + "users get free-to-play suggestions; link your account to unlock all items. "
            + "Your current GE offers and approximate coin total are sent to grandflipout.com "
            + "to generate suggestions; nothing is submitted to the GE automatically.",
        section = advisorSection,
        position = 0
    )
    default boolean enableAdvisor()
    {
        return false;
    }

    @ConfigItem(
        keyName = "enableGePriceFill",
        name = "GE offer auto-fill",
        description = "Off by default. When enabled, the Advisor's 'Fill offer' button and the Price-Fill hotkey write the suggested item name into the GE item search and the suggested price/quantity into the offer's input when you open them — the same mechanism Flipping Copilot uses. You always review the value and press Confirm yourself; nothing is ever submitted automatically.",
        section = advisorSection,
        position = 1
    )
    default boolean enableGePriceFill()
    {
        return false;
    }

    @Range(min = 0, max = 8)
    @ConfigItem(
        keyName = "mixVolumeSlots",
        name = "Mix: high-volume slots",
        description = "GE slots the Advisor's Mix chip gives to high-volume flips (small margin, fills fast in quantity). Unassigned slots get the best overall pick.",
        section = advisorSection,
        position = 2
    )
    default int mixVolumeSlots()
    {
        return 2;
    }

    @Range(min = 0, max = 8)
    @ConfigItem(
        keyName = "mixFastSlots",
        name = "Mix: fast-fill slots",
        description = "GE slots the Mix chip gives to flips estimated to fill within ~2 hours (any band).",
        section = advisorSection,
        position = 3
    )
    default int mixFastSlots()
    {
        return 3;
    }

    @Range(min = 0, max = 8)
    @ConfigItem(
        keyName = "mixWhaleSlots",
        name = "Mix: high-ticket slots",
        description = "GE slots the Mix chip gives to high-ticket flips (1M+ per fill, patient by nature).",
        section = advisorSection,
        position = 4
    )
    default int mixWhaleSlots()
    {
        return 3;
    }

    // ==================== FLIP TRACKER ====================

    @ConfigItem(
        keyName = "autoTrackFlips",
        name = "Auto-Track Flips",
        description = "Automatically pair buy/sell transactions into completed flips.",
        section = flipTrackerSection,
        position = 0
    )
    default boolean autoTrackFlips()
    {
        return true;
    }

    @ConfigItem(
        keyName = "persistHistory",
        name = "Save Flip History",
        description = "Save flip history between sessions (stored locally only).",
        section = flipTrackerSection,
        position = 1
    )
    default boolean persistHistory()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showWealthInOverlay",
        name = "Show Wealth Snapshot",
        description = "Display estimated coin/bank/total wealth on the session overlay (read-only, Wiki prices).",
        section = flipTrackerSection,
        position = 3
    )
    default boolean showWealthInOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "importFlippingUtilities",
        name = "Import Flipping Utilities",
        description = "One-time import of trade history from Flipping Utilities on startup. "
            + "Searches ~/.runelite/flipping/ and ~/.runelite/profiles/*/flipping/ for FU export files.",
        section = flipTrackerSection,
        position = 5
    )
    default boolean importFlippingUtilities()
    {
        return false;
    }

    @ConfigItem(
        keyName = "importGeHistory",
        name = "Import GE History Tab",
        description = "Back-fill trades made on mobile or before the plugin loaded by reading the "
            + "in-game Grand Exchange History tab when you open it. Read-only; deduplicated so "
            + "re-opening History never double-counts.",
        section = flipTrackerSection,
        position = 6
    )
    default boolean importGeHistory()
    {
        return true;
    }

    @ConfigItem(
        keyName = "enableAlerts",
        name = "Price / Offer Alerts",
        description = "Off by default. When on, get a RuneLite notification when a watched item "
            + "crosses a buy/sell target price you set (tap the bell on any item in the Prices "
            + "list), and when a Grand Exchange offer fills (bought/sold) or a buy offer sits "
            + "idle too long. Information-only; nothing is submitted to the GE.",
        section = flipTrackerSection,
        position = 7
    )
    default boolean enableAlerts()
    {
        return false;
    }

    @ConfigItem(
        keyName = "buyIdleAlertMinutes",
        name = "Idle buy alert (minutes)",
        description = "When alerts are on, notify if a buy offer has been sitting unfilled for "
            + "this many minutes so you can reprice. Set to 0 to disable the idle-buy alert.",
        section = flipTrackerSection,
        position = 8
    )
    @Range(min = 0, max = 240)
    default int buyIdleAlertMinutes()
    {
        return 15;
    }

    // ==================== OVERLAY ====================

    @ConfigItem(
        keyName = "enableGeOverlay",
        name = "Show GE Overlay",
        description = "Display price and margin info as an overlay when the Grand Exchange is open.",
        section = overlaySection,
        position = 0
    )
    default boolean enableGeOverlay()
    {
        return false;
    }

    @ConfigItem(
        keyName = "enableProfitOverlay",
        name = "Show Profit Overlay",
        description = "Display running session profit/loss as an overlay.",
        section = overlaySection,
        position = 1
    )
    default boolean enableProfitOverlay()
    {
        return false;
    }

    @ConfigItem(
        keyName = "overlayShowVolume",
        name = "Show Volume in Overlay",
        description = "Include trade volume information in the overlay.",
        section = overlaySection,
        position = 2
    )
    default boolean overlayShowVolume()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showSlotColorizer",
        name = "Slot Profit Colorizer",
        description = "Color-code GE slots based on estimated profit/loss.",
        section = overlaySection,
        position = 3
    )
    default boolean showSlotColorizer()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showGpDrops",
        name = "GP Drop Animation",
        description = "Show floating GP animation when a profitable flip completes.",
        section = overlaySection,
        position = 4
    )
    default boolean showGpDrops()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showInventoryTooltips",
        name = "Inventory Tooltips",
        description = "Show buy/sell prices, cost basis, and ROI when hovering inventory items while the GE is open.",
        section = overlaySection,
        position = 5
    )
    default boolean showInventoryTooltips()
    {
        return true;
    }

    // ==================== HOTKEYS (all unbound by default) ====================

    @ConfigItem(
        keyName = "togglePanelHotkey",
        name = "Toggle Panel",
        description = "Hotkey to open/close the Grand Flip Out panel.",
        section = hotkeysSection,
        position = 0
    )
    default Keybind togglePanelHotkey()
    {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
        keyName = "refreshPricesHotkey",
        name = "Refresh Prices",
        description = "Hotkey to force-refresh all price data from the Wiki API.",
        section = hotkeysSection,
        position = 1
    )
    default Keybind refreshPricesHotkey()
    {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
        keyName = "quickLookupHotkey",
        name = "Quick Lookup",
        description = "Hotkey to open the quick item search popup.",
        section = hotkeysSection,
        position = 2
    )
    default Keybind quickLookupHotkey()
    {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
        keyName = "toggleOverlayHotkey",
        name = "Toggle Overlay",
        description = "Hotkey to toggle the in-game GE overlay on/off.",
        section = hotkeysSection,
        position = 4
    )
    default Keybind toggleOverlayHotkey()
    {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
        keyName = "priceFillHotkey",
        name = "Price-Fill Assist",
        description = "Hotkey that fills the recommended price into the open GE offer field (requires 'GE offer auto-fill' enabled). You press Confirm yourself.",
        section = hotkeysSection,
        position = 6
    )
    default Keybind priceFillHotkey()
    {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
        keyName = "copilotHotkey",
        name = "Copilot next step",
        description = "Context-aware GE hotkey. Arms the suggested item, price and quantity — they pre-fill as you open the GE search or offer input yourself (requires \"GE offer auto-fill\").",
        section = hotkeysSection,
        position = 7
    )
    default Keybind copilotHotkey()
    {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
        keyName = "nextSuggestionHotkey",
        name = "Next suggestion",
        description = "Cycles to the next actionable flip suggestion and arms it — the item name pre-fills when you open the GE item search yourself.",
        section = hotkeysSection,
        position = 8
    )
    default Keybind nextSuggestionHotkey()
    {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
        keyName = "skipSuggestionHotkey",
        name = "Skip suggestion",
        description = "Skips the current flip suggestion and arms the next one — it pre-fills when you open the GE item search yourself.",
        section = hotkeysSection,
        position = 9
    )
    default Keybind skipSuggestionHotkey()
    {
        return Keybind.NOT_SET;
    }

    // ==================== ADVANCED ====================

    @ConfigItem(
        keyName = "priceRefreshInterval",
        name = "Refresh Interval (seconds)",
        description = "How often to refresh price data from the OSRS Wiki API (minimum 60s per Wiki etiquette). Live intelligence (dumps/VPIN) is computed server-side at 8-10s and fetched here.",
        section = advancedSection,
        position = 0
    )
    @Range(min = 60, max = 600)
    default int priceRefreshInterval()
    {
        return 60;
    }

    @ConfigItem(
        keyName = "userAgent",
        name = "Wiki API User-Agent",
        description = "Identifying user-agent for the OSRS Wiki API (required by Wiki policy). "
            + "Format: PluginName - ContactInfo",
        section = advancedSection,
        position = 1
    )
    default String userAgent()
    {
        return "GrandFlipOutPlugin - contact@grandflipout.com";
    }

    @ConfigItem(
        keyName = "intelligenceBaseUrl",
        name = "Intelligence API URL",
        description = "Base URL for Grand Flip Out intelligence API (no trailing slash).",
        section = advancedSection,
        position = 2
    )
    default String intelligenceBaseUrl()
    {
        return "https://grandflipout.com";
    }

    @ConfigItem(
        keyName = "apiKey",
        name = "GFO Account Token",
        description = "Normally set for you by the Guide tab's one-click \"Link account\" flow — "
            + "you should not need to touch this. Manual fallback: paste your account key from "
            + "grandflipout.com. Stored locally; only sent to grandflipout.com to check your account.",
        section = advancedSection,
        position = 3,
        secret = true
    )
    default String apiKey()
    {
        return "";
    }

    @ConfigItem(
        keyName = "maxHistoryEntries",
        name = "Max History Entries",
        description = "Maximum number of flip records to retain in local history.",
        section = advancedSection,
        position = 4
    )
    @Range(min = 50, max = 10000)
    default int maxHistoryEntries()
    {
        return 1000;
    }

}
