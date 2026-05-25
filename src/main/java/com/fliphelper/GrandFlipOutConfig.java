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
        name = "Server Intelligence",
        description = "Optional read-only calls to grandflipout.com (off by default)",
        position = 3
    )
    String intelligenceSection = "intelligence";

    @ConfigSection(
        name = "Hotkeys",
        description = "Keyboard shortcuts",
        position = 4
    )
    String hotkeysSection = "hotkeys";

    // ==================== DATA SOURCE ====================

    @ConfigItem(
        keyName = "priceRefreshInterval",
        name = "Refresh Interval (seconds)",
        description = "How often to refresh price data from the OSRS Wiki API (minimum 60s per Wiki etiquette).",
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
        return "GrandFlipOut RuneLite Plugin - github.com/Tunatroll/grand-flip-out";
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

    // ==================== OVERLAY ====================

    @ConfigItem(
        keyName = "showGEOverlay",
        name = "Show GE Overlay",
        description = "Display price and margin info as an overlay when the Grand Exchange is open.",
        section = overlaySection,
        position = 0
    )
    default boolean showGEOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showProfitOverlay",
        name = "Show Profit Overlay",
        description = "Display running session profit/loss as an overlay.",
        section = overlaySection,
        position = 1
    )
    default boolean showProfitOverlay()
    {
        return true;
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

    // ==================== SERVER INTELLIGENCE ====================

    @ConfigItem(
        keyName = "enableServerIntelligence",
        name = "Enable Server Advisor",
        description = "Fetch read-only smart-advisor hints from grandflipout.com for the active GE item.",
        section = intelligenceSection,
        position = 0
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
        position = 1
    )
    default String intelligenceBaseUrl()
    {
        return "https://grandflipout.com";
    }

    @ConfigItem(
        keyName = "marginAssistPercent",
        name = "Margin Assist ±%",
        description = "Percent above/below Wiki buy/sell used for ± margin clipboard strings.",
        section = intelligenceSection,
        position = 2
    )
    @Range(min = 1, max = 25)
    default int marginAssistPercent()
    {
        return 5;
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
        keyName = "copyMarginHotkey",
        name = "Copy Margin",
        description = "Copy the current item's margin data to clipboard.",
        section = hotkeysSection,
        position = 3
    )
    default Keybind copyMarginHotkey()
    {
        return new Keybind(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
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
        keyName = "priceFillHotkey",
        name = "Price-Fill Assist",
        description = "Copy recommended buy/sell prices to clipboard for manual GE entry.",
        section = hotkeysSection,
        position = 5
    )
    default Keybind priceFillHotkey()
    {
        return new Keybind(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
    }

    @ConfigItem(
        keyName = "copyBuyPriceHotkey",
        name = "Copy Buy Price",
        description = "Copy Wiki buy price (and ±% variants) for active GE slot to clipboard.",
        section = hotkeysSection,
        position = 6
    )
    default Keybind copyBuyPriceHotkey()
    {
        return new Keybind(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
    }

    @ConfigItem(
        keyName = "copySellPriceHotkey",
        name = "Copy Sell Price",
        description = "Copy Wiki sell price (and ±% variants) for active GE slot to clipboard.",
        section = hotkeysSection,
        position = 7
    )
    default Keybind copySellPriceHotkey()
    {
        return new Keybind(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
    }

    @ConfigItem(
        keyName = "copySlotAssistHotkey",
        name = "Copy Slot Assist",
        description = "Copy buy/sell/qty suggestion block for the first active GE slot.",
        section = hotkeysSection,
        position = 8
    )
    default Keybind copySlotAssistHotkey()
    {
        return new Keybind(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
    }
}
