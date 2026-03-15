package com.grandflipout;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * Connection settings for the Grand Flip Out API.
 * Server URL, API key, and endpoint paths.
 */
@ConfigGroup("grandflipoutapi")
public interface GrandFlipOutApiConfig extends Config
{
	@ConfigSection(
		name = "API connection",
		description = "Where to get live data. Sign up at the website to get a key.",
		position = 0
	)
	String credentialsSection = "credentials";

	@ConfigItem(
		keyName = "apiBaseUrl",
		name = "Server URL",
		description = "Your API URL (e.g. https://grandflipout.com)",
		section = credentialsSection,
		position = 0
	)
	default String apiBaseUrl()
	{
		return "https://grandflipout.com";
	}

	@ConfigItem(
		keyName = "apiKey",
		name = "API Key",
		description = "Sign up at the website, create a key in the dashboard, and paste it here. Leave blank if auth isn't required.",
		section = credentialsSection,
		position = 1,
		secret = true
	)
	default String apiKey()
	{
		return "";
	}

	@ConfigItem(
		keyName = "apiEndpoint",
		name = "Market endpoint path",
		description = "Path for market data (usually /api/market)",
		section = credentialsSection,
		position = 2
	)
	default String apiEndpoint()
	{
		return "/api/market";
	}

	@ConfigItem(
		keyName = "opportunitiesEndpoint",
		name = "Opportunities endpoint path",
		description = "Ranked opportunities path (usually /api/opportunities). Leave blank to use opportunities from the market response.",
		section = credentialsSection,
		position = 3
	)
	default String opportunitiesEndpoint()
	{
		return "/api/opportunities";
	}
}
