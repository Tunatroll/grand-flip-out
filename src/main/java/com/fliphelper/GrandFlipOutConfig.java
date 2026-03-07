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
        name = "Data Sources",
        description = "Configure which pricing APIs to use",
        position = 0
    )
    String dataSourcesSection = "dataSources";

    @ConfigSection(
        name = "Flip Tracker",
        description = "Configure flip tracking behavior",
        position = 1
    )
    String flipTrackerSection = "flipTracker";

    @ConfigSection(
        name = "Alerts",
        description = "Configure price alerts",
        position = 2
    )
    String alertsSection = "alerts";

    @ConfigSection(
        name = "Overlay",
        description = "Configure in-game overlay display",
        position = 3
    )
    String overlaySection = "overlay";

    @ConfigSection(
        name = "Hotkeys",
        description = "Configure keyboard shortcuts",
        position = 4
    )
    String hotkeysSection = "hotkeys";

    @ConfigSection(
        name = "Suggestions",
        description = "Configure flip suggestion engine",
        position = 5
    )
    String suggestionsSection = "suggestions";

    // ==================== DATA SOURCES ====================

    @ConfigItem(
        keyName = "useWikiPrices",
        name = "OSRS Wiki Prices",
        description = "Pull real-time prices from the OSRS Wiki API (recommended, most accurate)",
        section = dataSourcesSection,
        position = 0
    )
    default boolean useWikiPrices()
    {
        return true;
    }

    @ConfigItem(
        keyName = "useRuneLitePrices",
        name = "RuneLite Prices",
        description = "Pull prices from the RuneLite API",
        section = dataSourcesSection,
        position = 1
    )
    default boolean useRuneLitePrices()
    {
        return true;
    }

    @ConfigItem(
        keyName = "useOfficialGePrices",
        name = "Official GE API",
        description = "Pull prices from the official Jagex GE API (may be delayed)",
        section = dataSourcesSection,
        position = 2
    )
    default boolean useOfficialGePrices()
    {
        return false;
    }

    @ConfigItem(
        keyName = "priceRefreshInterval",
        name = "Refresh Interval (seconds)",
        description = "How often to refresh price data from APIs (minimum 60s to be respectful)",
        section = dataSourcesSection,
        position = 3
    )
    @Range(min = 60, max = 600)
    default int priceRefreshInterval()
    {
        return 60;
    }

    @ConfigItem(
        keyName = "userAgent",
        name = "Wiki API User-Agent",
        description = "Identifying user-agent for the Wiki API (required). Use format: PluginName - ContactInfo",
        section = dataSourcesSection,
        position = 4
    )
    default String userAgent()
    {
        return "GrandFlipOut RuneLite Plugin - github.com/Tunatroll/grand-flip-out";
    }

    // ==================== FLIP TRACKER ====================

    @ConfigItem(
        keyName = "autoTrackFlips",
        name = "Auto-Track Flips",
        description = "Automatically detect and track flips from GE transactions",
        section = flipTrackerSection,
        position = 0
    )
    default boolean autoTrackFlips()
    {
        return true;
    }

    @ConfigItem(
        keyName = "accountForTax",
        name = "Account for GE Tax",
        description = "Factor in the 2% GE tax (capped at 5M per item) in profit calculations",
        section = flipTrackerSection,
        position = 1
    )
    default boolean accountForTax()
    {
        return true;
    }

    @ConfigItem(
        keyName = "persistHistory",
        name = "Save Flip History",
        description = "Save flip history between sessions",
        section = flipTrackerSection,
        position = 2
    )
    default boolean persistHistory()
    {
        return true;
    }

    @ConfigItem(
        keyName = "maxHistoryEntries",
        name = "Max History Entries",
        description = "Maximum number of flip records to keep in history",
        section = flipTrackerSection,
        position = 3
    )
    @Range(min = 50, max = 10000)
    default int maxHistoryEntries()
    {
        return 1000;
    }

    // ==================== ALERTS ====================

    @ConfigItem(
        keyName = "enableAlerts",
        name = "Enable Price Alerts",
        description = "Enable notifications when item prices hit target values",
        section = alertsSection,
        position = 0
    )
    default boolean enableAlerts()
    {
        return true;
    }

    @ConfigItem(
        keyName = "alertSound",
        name = "Alert Sound",
        description = "Play a sound when a price alert is triggered",
        section = alertsSection,
        position = 1
    )
    default boolean alertSound()
    {
        return true;
    }

    @ConfigItem(
        keyName = "marginAlertThreshold",
        name = "Margin Alert (%)",
        description = "Alert when an item's margin exceeds this percentage",
        section = alertsSection,
        position = 2
    )
    @Range(min = 1, max = 100)
    default int marginAlertThreshold()
    {
        return 5;
    }

    // ==================== OVERLAY ====================

    @ConfigItem(
        keyName = "showGEOverlay",
        name = "Show GE Overlay",
        description = "Display price and margin info as an overlay when the GE is open",
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
        description = "Display running profit/loss overlay during session",
        section = overlaySection,
        position = 1
    )
    default boolean showProfitOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showMarginInTooltip",
        name = "Margin in Tooltip",
        description = "Show current margin when hovering items in the GE",
        section = overlaySection,
        position = 2
    )
    default boolean showMarginInTooltip()
    {
        return true;
    }

    @ConfigItem(
        keyName = "overlayShowVolume",
        name = "Show Volume in Overlay",
        description = "Include trade volume information in the overlay",
        section = overlaySection,
        position = 3
    )
    default boolean overlayShowVolume()
    {
        return true;
    }

    // ==================== HOTKEYS ====================

    @ConfigItem(
        keyName = "togglePanelHotkey",
        name = "Toggle Panel",
        description = "Hotkey to open/close the Grand Flip Out panel",
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
        description = "Hotkey to force-refresh all price data",
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
        description = "Hotkey to open the quick item search/lookup popup",
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
        description = "Copy the current item's margin data to clipboard",
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
        description = "Hotkey to toggle the in-game GE overlay on/off",
        section = hotkeysSection,
        position = 4
    )
    default Keybind toggleOverlayHotkey()
    {
        return new Keybind(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
    }

    @ConfigItem(
        keyName = "dumpScanHotkey",
        name = "Scan for Dumps",
        description = "Hotkey to immediately scan for active dump opportunities",
        section = hotkeysSection,
        position = 5
    )
    default Keybind dumpScanHotkey()
    {
        return new Keybind(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
    }

    @ConfigItem(
        keyName = "nextAccountHotkey",
        name = "Next Account",
        description = "Hotkey to cycle to the next account in multi-account view",
        section = hotkeysSection,
        position = 6
    )
    default Keybind nextAccountHotkey()
    {
        return new Keybind(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
    }

    @ConfigItem(
        keyName = "quickBuyPlanHotkey",
        name = "Quick Buy Plan",
        description = "Hotkey to generate a multi-account buy plan for the selected item",
        section = hotkeysSection,
        position = 7
    )
    default Keybind quickBuyPlanHotkey()
    {
        return new Keybind(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
    }

    // ==================== DUMP DETECTION ====================

    @ConfigSection(
        name = "Dump Detection",
        description = "Configure dump/crash detection and knowledge engine",
        position = 6
    )
    String dumpDetectionSection = "dumpDetection";

    @ConfigItem(
        keyName = "enableDumpDetection",
        name = "Enable Dump Detection",
        description = "Monitor for sudden price crashes and generate buy opportunities",
        section = dumpDetectionSection,
        position = 0
    )
    default boolean enableDumpDetection()
    {
        return true;
    }

    @ConfigItem(
        keyName = "dumpAlertThreshold",
        name = "Dump Alert Threshold (%)",
        description = "Minimum price drop percentage to trigger a dump alert",
        section = dumpDetectionSection,
        position = 1
    )
    @Range(min = 2, max = 50)
    default int dumpAlertThreshold()
    {
        return 5;
    }

    @ConfigItem(
        keyName = "dumpNotifySound",
        name = "Dump Alert Sound",
        description = "Play a sound when a significant dump is detected",
        section = dumpDetectionSection,
        position = 2
    )
    default boolean dumpNotifySound()
    {
        return true;
    }

    @ConfigItem(
        keyName = "dumpAutoAnalyze",
        name = "Auto-Analyze Dumps",
        description = "Automatically run dump knowledge analysis when a dump is detected",
        section = dumpDetectionSection,
        position = 3
    )
    default boolean dumpAutoAnalyze()
    {
        return true;
    }

    @ConfigItem(
        keyName = "dumpMinVolume",
        name = "Min Volume for Dump Alert",
        description = "Minimum hourly volume for an item to trigger dump alerts (filters noise)",
        section = dumpDetectionSection,
        position = 4
    )
    @Range(min = 10, max = 10000)
    default int dumpMinVolume()
    {
        return 50;
    }

    @ConfigItem(
        keyName = "dumpWatchlistOnly",
        name = "Watchlist Only",
        description = "Only detect dumps for items on your watchlist (reduces noise)",
        section = dumpDetectionSection,
        position = 5
    )
    default boolean dumpWatchlistOnly()
    {
        return false;
    }

    // ==================== MULTI-ACCOUNT ====================

    @ConfigSection(
        name = "Multi-Account",
        description = "Track flipping across multiple accounts (information only, Jagex compliant)",
        position = 7
    )
    String multiAccountSection = "multiAccount";

    @ConfigItem(
        keyName = "enableMultiAccount",
        name = "Enable Multi-Account Tracking",
        description = "Track portfolio and GE limits across multiple accounts. Information only — all trades are manual.",
        section = multiAccountSection,
        position = 0
    )
    default boolean enableMultiAccount()
    {
        return false;
    }

    @ConfigItem(
        keyName = "accountCount",
        name = "Number of Accounts",
        description = "How many accounts to track (1-10)",
        section = multiAccountSection,
        position = 1
    )
    @Range(min = 1, max = 10)
    default int accountCount()
    {
        return 1;
    }

    @ConfigItem(
        keyName = "showCombinedBuyingPower",
        name = "Show Combined Buying Power",
        description = "Display total buying power across all accounts for each item",
        section = multiAccountSection,
        position = 2
    )
    default boolean showCombinedBuyingPower()
    {
        return true;
    }

    @ConfigItem(
        keyName = "rebalanceAlerts",
        name = "Rebalance Alerts",
        description = "Alert when one account is over-concentrated in a single item",
        section = multiAccountSection,
        position = 3
    )
    default boolean rebalanceAlerts()
    {
        return true;
    }

    // ==================== SUGGESTIONS ====================

    @ConfigItem(
        keyName = "enableSuggestions",
        name = "Enable Flip Suggestions",
        description = "Show suggested items to flip based on volume and margin analysis",
        section = suggestionsSection,
        position = 0
    )
    default boolean enableSuggestions()
    {
        return true;
    }

    @ConfigItem(
        keyName = "minSuggestionMargin",
        name = "Min Margin (gp)",
        description = "Minimum margin in gp for an item to appear in suggestions",
        section = suggestionsSection,
        position = 1
    )
    @Range(min = 1, max = 10000000)
    default int minSuggestionMargin()
    {
        return 500;
    }

    @ConfigItem(
        keyName = "minSuggestionVolume",
        name = "Min Volume (1h)",
        description = "Minimum 1-hour trade volume for an item to appear in suggestions",
        section = suggestionsSection,
        position = 2
    )
    @Range(min = 1, max = 100000)
    default int minSuggestionVolume()
    {
        return 50;
    }

    @ConfigItem(
        keyName = "maxSuggestions",
        name = "Max Suggestions",
        description = "Maximum number of suggested flips to display",
        section = suggestionsSection,
        position = 3
    )
    @Range(min = 5, max = 100)
    default int maxSuggestions()
    {
        return 25;
    }

    @ConfigItem(
        keyName = "suggestionSortBy",
        name = "Sort Suggestions By",
        description = "How to sort flip suggestions",
        section = suggestionsSection,
        position = 4
    )
    default SuggestionSort suggestionSortBy()
    {
        return SuggestionSort.PROFIT_PER_LIMIT;
    }

    @ConfigItem(
        keyName = "minProfitThreshold",
        name = "Min Profit Threshold (gp)",
        description = "Only show suggestions with profit per item above this amount",
        section = suggestionsSection,
        position = 5
    )
    @Range(min = 1, max = 1000000)
    default int minProfitThreshold()
    {
        return 1000;
    }

    // ==================== NOTIFICATIONS ====================

    @ConfigSection(
        name = "Notifications",
        description = "Configure notification preferences",
        position = 8
    )
    String notificationsSection = "notifications";

    @ConfigItem(
        keyName = "enableSoundAlerts",
        name = "Enable Sound Alerts",
        description = "Play a sound for dump alerts and price notifications",
        section = notificationsSection,
        position = 0
    )
    default boolean enableSoundAlerts()
    {
        return true;
    }

    @ConfigItem(
        keyName = "notifyDumpDetection",
        name = "Notify on Dump Detection",
        description = "Send chat notification when a dump is detected",
        section = notificationsSection,
        position = 1
    )
    default boolean notifyDumpDetection()
    {
        return true;
    }

    // ==================== OVERLAY POSITION ====================

    @ConfigItem(
        keyName = "overlayPosition",
        name = "Overlay Position",
        description = "Where to display the overlay (TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT)",
        section = overlaySection,
        position = 4
    )
    default OverlayPositionType overlayPosition()
    {
        return OverlayPositionType.TOP_LEFT;
    }

    enum OverlayPositionType
    {
        TOP_LEFT("Top Left"),
        TOP_RIGHT("Top Right"),
        BOTTOM_LEFT("Bottom Left"),
        BOTTOM_RIGHT("Bottom Right");

        private final String displayName;

        OverlayPositionType(String displayName)
        {
            this.displayName = displayName;
        }

        @Override
        public String toString()
        {
            return displayName;
        }
    }

    enum SuggestionSort
    {
        MARGIN("Highest Margin"),
        MARGIN_PERCENT("Highest Margin %"),
        VOLUME("Highest Volume"),
        PROFIT_PER_LIMIT("Profit per GE Limit"),
        ROI("Return on Investment");

        private final String displayName;

        SuggestionSort(String displayName)
        {
            this.displayName = displayName;
        }

        @Override
        public String toString()
        {
            return displayName;
        }
    }
}
