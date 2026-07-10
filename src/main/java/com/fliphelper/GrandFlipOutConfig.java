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

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

@ConfigGroup("grandflipout")
public interface GrandFlipOutConfig extends Config
{
    // ==================== SECTIONS ====================

    @ConfigSection(
        name = "Data Source",
        description = "OSRS Wiki price API configuration",
        position = 0
    )
    String dataSourcesSection = "dataSources";

    @ConfigSection(
        name = "Flip Tracker",
        description = "Local flip tracking and P&L",
        position = 1
    )
    String flipTrackerSection = "flipTracker";

    @ConfigSection(
        name = "Overlay",
        description = "In-game GE overlay",
        position = 2
    )
    String overlaySection = "overlay";

    @ConfigSection(
        name = "GFO Account & API",
        description = "Free Grand Flip Out account — unlocks all members items + premium features",
        position = 3
    )
    String accountSection = "account";

    @ConfigSection(
        name = "Advisor",
        description = "Suggests your next flip (item, price, quantity). Off by default — when on, "
            + "your current GE offers and approximate coins are sent to grandflipout.com to generate suggestions.",
        position = 4
    )
    String advisorSection = "advisor";

    @ConfigSection(
        name = "Server Intelligence",
        description = "Optional read-only calls to grandflipout.com (off by default for Plugin Hub)",
        position = 5
    )
    String intelligenceSection = "intelligence";

    @ConfigSection(
        name = "Hotkeys",
        description = "Keyboard shortcuts",
        position = 6
    )
    String hotkeysSection = "hotkeys";

    // ==================== DATA SOURCE ====================

    @ConfigItem(
        keyName = "priceRefreshInterval",
        name = "Refresh Interval (seconds)",
        description = "How often to refresh price data from the OSRS Wiki API (minimum 60s per Wiki etiquette). Live intelligence (dumps/VPIN) is computed server-side at 8-10s and fetched here.",
        section = dataSourcesSection,
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
        section = dataSourcesSection,
        position = 1
    )
    default String userAgent()
    {
        return "GrandFlipOutPlugin - contact@grandflipout.com";
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
        keyName = "maxHistoryEntries",
        name = "Max History Entries",
        description = "Maximum number of flip records to retain in local history.",
        section = flipTrackerSection,
        position = 4
    )
    @Range(min = 50, max = 10000)
    default int maxHistoryEntries()
    {
        return 1000;
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

    // ==================== ACCOUNT ====================

    @ConfigItem(
        keyName = "apiKey",
        name = "GFO Account Token",
        description = "Paste your free Grand Flip Out account key (create one at grandflipout.com) to "
            + "unlock all members items and premium features. Leave blank to keep using the free "
            + "F2P suggestions. Stored locally; only sent to grandflipout.com to check your account.",
        section = accountSection,
        position = 0,
        secret = true
    )
    default String apiKey()
    {
        return "";
    }

    // ==================== ADVISOR ====================

    @ConfigItem(
        keyName = "enableAdvisor",
        name = "Enable Advisor",
        description = "Off by default. When on, the Advisor tab suggests your next flip "
            + "(item, buy/sell price, quantity) based on your coins and free GE slots. Anonymous "
            + "users get free-to-play suggestions; paste an account key to unlock all items. "
            + "Your current GE offers and approximate coin total are sent to grandflipout.com "
            + "to generate suggestions; nothing is submitted to the GE automatically.",
        section = advisorSection,
        position = 0
    )
    default boolean enableAdvisor()
    {
        return false;
    }

    // ==================== SERVER INTELLIGENCE ====================

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
            + "When enabled, your Grand Exchange offer and trade data (item, price, quantity, flip timings, and approximate coins) are sent to grandflipout.com."
    )
    default boolean enableServerFunctionality()
    {
        return false;
    }

    @ConfigItem(
        keyName = "enableServerIntelligence",
        name = "Enable Server Advisor",
        description = "Off by default. When enabled, fetch BUY/SELL/HOLD signals, VPIN alerts, dump predictions, and screener data from grandflipout.com. Read-only — your trades are not sent unless you also enable 'Contribute trades'.",
        section = intelligenceSection,
        position = 1
    )
    default boolean enableServerIntelligence()
    {
        return false;
    }

    @ConfigItem(
        keyName = "intelligenceBaseUrl",
        name = "Intelligence API URL",
        description = "Base URL for Grand Flip Out intelligence API (no trailing slash).",
        section = intelligenceSection,
        position = 2
    )
    default String intelligenceBaseUrl()
    {
        return "https://grandflipout.com";
    }

    @ConfigItem(
        keyName = "contributeTrades",
        name = "Contribute trades (crowdsourced data)",
        description = "Opt in to share your completed GE trades (item, price, quantity, buy/sell) "
            + "and completed-flip outcomes (paired buy/sell prices with placed-to-filled timings) "
            + "with grandflipout.com to improve crowdsourced flip data. Off by default. "
            + "Independent of the read-only advisor above. No account identity is sent.",
        section = intelligenceSection,
        position = 4
    )
    default boolean contributeTrades()
    {
        return false;
    }

    // ==================== HOTKEYS ====================

    @ConfigItem(
        keyName = "togglePanelHotkey",
        name = "Toggle Panel",
        description = "Hotkey to open/close the Grand Flip Out panel.",
        section = hotkeysSection,
        position = 0
    )
    default Keybind togglePanelHotkey()
    {
        return new Keybind(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
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
        return new Keybind(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
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
        return new Keybind(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
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
        return new Keybind(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
    }

    @ConfigItem(
        keyName = "enableGePriceFill",
        name = "GE offer auto-fill",
        description = "Off by default. When enabled, the Advisor's 'Fill offer' button and the Price-Fill hotkey write the suggested price/quantity into the Grand Exchange offer's input when you open it — the same mechanism Flipping Copilot uses. You always review the value and press Confirm yourself; nothing is ever submitted automatically.",
        section = hotkeysSection,
        position = 5
    )
    default boolean enableGePriceFill()
    {
        return false;
    }

    @ConfigItem(
        keyName = "priceFillHotkey",
        name = "Price-Fill Assist",
        description = "Hotkey that fills the recommended price into the open GE offer field (requires 'GE price-fill assist' enabled). You press Confirm yourself.",
        section = hotkeysSection,
        position = 6
    )
    default Keybind priceFillHotkey()
    {
        return new Keybind(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
    }

    @ConfigItem(
        keyName = "copilotHotkey",
        name = "Copilot Next Step (Ctrl+Space)",
        description = "Context-aware GE hotkey. Opens GE search, arms prices/quantities, and guides you through the next action based on your current GE interface.",
        section = hotkeysSection,
        position = 7
    )
    default Keybind copilotHotkey()
    {
        return new Keybind(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK);
    }

    @ConfigItem(
        keyName = "nextSuggestionHotkey",
        name = "Next Suggestion (Up)",
        description = "Cycles to the next actionable flip suggestion and instantly searches for it in the GE.",
        section = hotkeysSection,
        position = 8
    )
    default Keybind nextSuggestionHotkey()
    {
        return new Keybind(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK);
    }

    @ConfigItem(
        keyName = "skipSuggestionHotkey",
        name = "Skip Suggestion (Down)",
        description = "Skips the current flip suggestion and instantly searches the next one in the GE.",
        section = hotkeysSection,
        position = 9
    )
    default Keybind skipSuggestionHotkey()
    {
        return new Keybind(KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK);
    }

}
