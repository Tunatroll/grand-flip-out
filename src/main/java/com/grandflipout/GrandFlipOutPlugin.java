package com.grandflipout;

import com.google.inject.Provides;
import com.grandflipout.network.GrandFlipOutNetworkManager;
import com.grandflipout.network.MarketDataResponse;
import com.grandflipout.network.MarketOpportunityDto;
import com.grandflipout.tracking.FlipLogManager;
import com.grandflipout.tracking.TradeRecord;
import com.grandflipout.tracking.TradeHistoryManager;
import com.grandflipout.ui.GrandFlipOutPanel;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;

/**
 * Grand Flip Out — live market API polling plus local trade history
 * and profit tracking for the Grand Exchange.
 */
@Slf4j
@PluginDescriptor(
	name = "Grand Flip Out",
	description = "Live market API polling plus local trade history and profit tracking for the Grand Exchange.",
	tags = {"grand exchange", "flip", "profit", "market", "tracking"}
)
public class GrandFlipOutPlugin extends Plugin
{
	@Inject
	private GrandFlipOutConfig config;

	@Inject
	private GrandFlipOutApiConfig apiConfig;

	@Inject
	private GrandFlipOutNetworkManager networkManager;

	@Inject
	private TradeHistoryManager tradeHistoryManager;

	@Inject
	private FlipLogManager flipLogManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private Notifier notifier;

	@Inject
	private ConfigManager configManager;

	private static final String CONFIG_GROUP = "grandflipout";
	private static final String CONFIG_SCHEMA_VERSION_KEY = "configSchemaVersion";
	private static final int CURRENT_CONFIG_SCHEMA_VERSION = 1;

	private NavigationButton navButton;
	private GrandFlipOutPanel panel;
	private final Map<Integer, SlotSnapshot> slotSnapshots = new HashMap<>();
	private volatile long lastOpportunityAlertMs = 0;
	private volatile String lastOpportunityAlertKey = "";
	private final HotkeyListener cycleTabHotkeyListener = new HotkeyListener(() -> config.cycleTabHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			if (panel != null)
			{
				panel.cycleTab();
			}
		}
	};
	private final HotkeyListener newSessionHotkeyListener = new HotkeyListener(() -> config.newSessionHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			tradeHistoryManager.startNewSession();
			if (panel != null)
			{
				panel.showTradeHistoryTab();
				panel.refreshAll();
			}
		}
	};
	private final HotkeyListener refreshUiHotkeyListener = new HotkeyListener(() -> config.refreshUiHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			if (panel != null)
			{
				panel.refreshAll();
			}
		}
	};

	@Override
	protected void startUp() throws Exception
	{
		log.info("Grand Flip Out started.");
		runConfigSchemaMigration();
		tradeHistoryManager.load();
		flipLogManager.load();
		if (config.apiEnabled())
		{
			networkManager.setMarketDataCallback(this::handleOpportunityAlerts);
			networkManager.startPolling();
		}

		slotSnapshots.clear();
		panel = new GrandFlipOutPanel(config, apiConfig, networkManager, tradeHistoryManager, flipLogManager);
		BufferedImage icon = createIcon();
		navButton = NavigationButton.builder()
			.tooltip("Grand Flip Out")
			.icon(icon)
			.panel(panel)
			.priority(100)
			.build();
		clientToolbar.addNavigation(navButton);
		keyManager.registerKeyListener(cycleTabHotkeyListener);
		keyManager.registerKeyListener(newSessionHotkeyListener);
		keyManager.registerKeyListener(refreshUiHotkeyListener);
	}

	@Override
	protected void shutDown() throws Exception
	{
		keyManager.unregisterKeyListener(cycleTabHotkeyListener);
		keyManager.unregisterKeyListener(newSessionHotkeyListener);
		keyManager.unregisterKeyListener(refreshUiHotkeyListener);
		clientToolbar.removeNavigation(navButton);
		panel = null;
		slotSnapshots.clear();
		networkManager.setMarketDataCallback(null);
		networkManager.stopPolling();
		if (config.persistTradeHistory())
		{
			tradeHistoryManager.save();
		}
		flipLogManager.save();
		log.info("Grand Flip Out stopped.");
	}

	/**
	 * Ensures config schema version is set. Run once on startup before loading persisted data.
	 * Future versions can add migrations here (e.g. if savedVersion &lt; 2 then migrate and set 2).
	 */
	private void runConfigSchemaMigration()
	{
		String saved = configManager.getConfiguration(CONFIG_GROUP, CONFIG_SCHEMA_VERSION_KEY);
		int savedVersion = 0;
		if (saved != null && !saved.isBlank())
		{
			try
			{
				savedVersion = Integer.parseInt(saved.trim());
			}
			catch (NumberFormatException ignored)
			{
				// Treat invalid value as 0 so we set current version.
			}
		}
		if (savedVersion < CURRENT_CONFIG_SCHEMA_VERSION)
		{
			configManager.setConfiguration(CONFIG_GROUP, CONFIG_SCHEMA_VERSION_KEY, String.valueOf(CURRENT_CONFIG_SCHEMA_VERSION));
			log.debug("Config schema version set to {}.", CURRENT_CONFIG_SCHEMA_VERSION);
		}
	}

	private BufferedImage createIcon()
	{
		try
		{
			return ImageUtil.loadImageResource(GrandFlipOutPlugin.class, "icon.png");
		}
		catch (Exception e)
		{
			// Deterministic fallback so the plugin always has a clear sidebar icon,
			// even when a packaged icon file is missing in local dev builds.
			BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = img.createGraphics();
			g.setRenderingHint(
				java.awt.RenderingHints.KEY_ANTIALIASING,
				java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(new java.awt.Color(28, 28, 31));
			g.fillRoundRect(0, 0, 16, 16, 4, 4);
			g.setColor(ColorScheme.BRAND_ORANGE);
			g.fillRoundRect(1, 1, 14, 14, 4, 4);
			g.setColor(new java.awt.Color(32, 32, 36));
			g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 9));
			g.drawString("G", 4, 12);
			g.dispose();
			return img;
		}
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		GrandExchangeOffer offer = event.getOffer();
		if (offer == null || !config.trackingEnabled())
		{
			return;
		}

		int slot = event.getSlot();
		int quantitySold = Math.max(0, offer.getQuantitySold());
		int unitPrice = Math.max(0, offer.getPrice());
		int itemId = offer.getItemId();
		GrandExchangeOfferState state = offer.getState();

		SlotSnapshot previous = slotSnapshots.get(slot);
		SlotSnapshot current = new SlotSnapshot(itemId, quantitySold);
		slotSnapshots.put(slot, current);

		// First snapshot for a slot may reflect historical offer state on login; don't backfill.
		if (previous == null || previous.itemId != itemId)
		{
			return;
		}

		int deltaQty = quantitySold - previous.quantitySold;
		if (deltaQty <= 0 || unitPrice <= 0 || itemId <= 0)
		{
			return;
		}

		TradeRecord.Type tradeType = toTradeType(state);
		if (tradeType == null)
		{
			return;
		}

		String itemName = Text.removeTags(itemManager.getItemComposition(itemId).getName());
		long totalValue = (long) deltaQty * unitPrice;
		TradeRecord tradeRecord = TradeRecord.builder()
			.itemId(itemId)
			.itemName(itemName)
			.type(tradeType)
			.quantity(deltaQty)
			.pricePerUnit(unitPrice)
			.totalValue(totalValue)
			.timestampMs(System.currentTimeMillis())
			.offerSlot(slot)
			.build();

		tradeHistoryManager.addTrade(tradeRecord);
		flipLogManager.addFromTrade(tradeRecord);

		if (panel != null)
		{
			panel.refreshAll();
		}
	}

	private static TradeRecord.Type toTradeType(GrandExchangeOfferState state)
	{
		if (state == null)
		{
			return null;
		}
		switch (state)
		{
			case BUYING:
			case BOUGHT:
			case CANCELLED_BUY:
				return TradeRecord.Type.BUY;
			case SELLING:
			case SOLD:
			case CANCELLED_SELL:
				return TradeRecord.Type.SELL;
			default:
				return null;
		}
	}

	private void handleOpportunityAlerts(MarketDataResponse data)
	{
		if (!config.opportunityAlertsEnabled() || data == null)
		{
			return;
		}
		List<MarketOpportunityDto> opportunities = data.getOpportunities();
		if (opportunities == null || opportunities.isEmpty())
		{
			return;
		}
		int minMargin = config.alertMinMarginPercent();
		int minConfidence = config.alertMinConfidencePercent();
		MarketOpportunityDto best = null;
		for (MarketOpportunityDto opp : opportunities)
		{
			double marginPct = opp.getMarginPercent() != null ? opp.getMarginPercent() : 0;
			double confidencePct = opp.getConfidence() != null ? opp.getConfidence() * 100.0 : 0;
			if (marginPct >= minMargin && confidencePct >= minConfidence)
			{
				best = opp;
				break;
			}
		}
		if (best == null)
		{
			return;
		}

		long now = System.currentTimeMillis();
		long cooldownMs = Math.max(1, config.alertCooldownMinutes()) * 60_000L;
		String alertKey = (best.getItemId() + ":" + (best.getMarginPercent() != null ? best.getMarginPercent() : 0));
		if (alertKey.equals(lastOpportunityAlertKey) && (now - lastOpportunityAlertMs) < cooldownMs)
		{
			return;
		}
		lastOpportunityAlertMs = now;
		lastOpportunityAlertKey = alertKey;
		String itemName = best.getItemName() != null ? best.getItemName() : "item " + best.getItemId();
		String marginStr = best.getMarginPercent() != null ? String.format("%.1f%%", best.getMarginPercent()) : "n/a";
		String confidenceStr = best.getConfidence() != null ? String.format("%.0f%%", best.getConfidence() * 100.0) : "n/a";
		notifier.notify("Grand Flip Out: " + itemName + " opportunity (" + marginStr + ", confidence " + confidenceStr + ")");
	}

	private static final class SlotSnapshot
	{
		private final int itemId;
		private final int quantitySold;

		private SlotSnapshot(int itemId, int quantitySold)
		{
			this.itemId = itemId;
			this.quantitySold = quantitySold;
		}
	}

	@Provides
	GrandFlipOutConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GrandFlipOutConfig.class);
	}

	@Provides
	GrandFlipOutApiConfig provideApiConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GrandFlipOutApiConfig.class);
	}

}
