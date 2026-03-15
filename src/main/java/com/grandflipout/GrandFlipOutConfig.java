package com.grandflipout;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup("grandflipout")
public interface GrandFlipOutConfig extends Config
{
	@ConfigSection(
		name = "Market API",
		description = "Live prices and ranked opportunities from your API",
		position = 0
	)
	String apiSection = "api";

	@ConfigSection(
		name = "Local tracking",
		description = "Your trade history and profit (stays on your PC)",
		position = 1
	)
	String trackingSection = "tracking";

	@ConfigSection(
		name = "Flip logs",
		description = "Your flip-by-flip record (persists across restarts)",
		position = 2
	)
	String logsSection = "logs";

	@ConfigSection(
		name = "Hotkeys",
		description = "Optional keyboard shortcuts for the panel",
		position = 3
	)
	String hotkeysSection = "hotkeys";

	@ConfigSection(
		name = "Alerts",
		description = "Desktop notifications when good opportunities show up",
		position = 4
	)
	String alertsSection = "alerts";

	// --- API Section ---

	@ConfigItem(
		keyName = "apiEnabled",
		name = "Enable market API polling",
		description = "Turn on to get live prices and opportunities from your API",
		section = apiSection,
		position = 0
	)
	default boolean apiEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "apiPollIntervalSeconds",
		name = "Poll interval (seconds)",
		description = "How often to fetch new data (e.g. 60 = once a minute)",
		section = apiSection,
		position = 1
	)
	@Range(min = 30, max = 600)
	default int apiPollIntervalSeconds()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "opportunityStrategy",
		name = "Opportunity strategy",
		description = "How to rank flips: default, low_risk (safer), high_volume (fast), or high_margin (bigger profit per flip)",
		section = apiSection,
		position = 2
	)
	default String opportunityStrategy()
	{
		return "default";
	}

	// --- Tracking Section ---

	@ConfigItem(
		keyName = "trackingEnabled",
		name = "Enable local trade tracking",
		description = "Record your GE buys and sells so you can see session and all-time profit",
		section = trackingSection,
		position = 0
	)
	default boolean trackingEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "persistTradeHistory",
		name = "Persist trade history",
		description = "Keep your trade history when you close RuneLite",
		section = trackingSection,
		position = 1
	)
	default boolean persistTradeHistory()
	{
		return true;
	}

	@ConfigItem(
		keyName = "maxTradeHistoryEntries",
		name = "Max trade history entries",
		description = "How many trades to keep (oldest are dropped when you go over)",
		section = trackingSection,
		position = 2
	)
	@Range(min = 50, max = 5000)
	default int maxTradeHistoryEntries()
	{
		return 1000;
	}

	// --- Logs Section ---

	@ConfigItem(
		keyName = "persistFlipLogs",
		name = "Persist flip logs",
		description = "Keep your flip log when you close RuneLite",
		section = logsSection,
		position = 0
	)
	default boolean persistFlipLogs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "maxFlipLogEntries",
		name = "Max flip log entries",
		description = "How many log entries to keep (oldest are dropped when you go over)",
		section = logsSection,
		position = 1
	)
	@Range(min = 50, max = 5000)
	default int maxFlipLogEntries()
	{
		return 500;
	}

	// --- Hotkeys Section ---

	@ConfigItem(
		keyName = "cycleTabHotkey",
		name = "Cycle tabs hotkey",
		description = "Press to cycle between the three panel tabs",
		section = hotkeysSection,
		position = 0
	)
	default Keybind cycleTabHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "newSessionHotkey",
		name = "New session hotkey",
		description = "Reset session profit and start fresh",
		section = hotkeysSection,
		position = 1
	)
	default Keybind newSessionHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "refreshUiHotkey",
		name = "Refresh panel hotkey",
		description = "Refresh all panel data right now",
		section = hotkeysSection,
		position = 2
	)
	default Keybind refreshUiHotkey()
	{
		return Keybind.NOT_SET;
	}

	// --- Alerts Section ---

	@ConfigItem(
		keyName = "opportunityAlertsEnabled",
		name = "Enable opportunity alerts",
		description = "Get a desktop notification when a good opportunity comes in",
		section = alertsSection,
		position = 0
	)
	default boolean opportunityAlertsEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "alertMinMarginPercent",
		name = "Min margin %",
		description = "Only alert if the margin is at least this % (e.g. 2 = 2%)",
		section = alertsSection,
		position = 1
	)
	@Range(min = 0, max = 100)
	default int alertMinMarginPercent()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "alertMinConfidencePercent",
		name = "Min confidence %",
		description = "Only alert if confidence is at least this % (e.g. 70 = 70%)",
		section = alertsSection,
		position = 2
	)
	@Range(min = 0, max = 100)
	default int alertMinConfidencePercent()
	{
		return 70;
	}

	@ConfigItem(
		keyName = "alertCooldownMinutes",
		name = "Alert cooldown (minutes)",
		description = "Wait at least this many minutes before alerting about the same item again",
		section = alertsSection,
		position = 3
	)
	@Range(min = 1, max = 180)
	default int alertCooldownMinutes()
	{
		return 10;
	}
}
