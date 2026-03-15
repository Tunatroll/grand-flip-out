package com.grandflipout.ui;

import com.grandflipout.GrandFlipOutApiConfig;
import com.grandflipout.GrandFlipOutConfig;
import com.grandflipout.network.GrandFlipOutNetworkManager;
import com.grandflipout.tracking.FlipLogManager;
import com.grandflipout.tracking.TradeHistoryManager;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JTabbedPane;
import javax.swing.Timer;
import javax.swing.plaf.TabbedPaneUI;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.laf.RuneLiteTabbedPaneUI;

/**
 * Main side panel for Grand Flip Out. Composes Overview, Live Market, and
 * Trade History tab panels and delegates refresh. No monolithic UI logic here.
 */
public class GrandFlipOutPanel extends PluginPanel
{
	private static final int REFRESH_INTERVAL_MS = 3000;

	private final OverviewTabPanel overviewTab;
	private final LiveMarketTabPanel marketTab;
	private final TradeHistoryTabPanel historyTab;
	private final JTabbedPane tabbedPane;
	private final Timer refreshTimer;

	public GrandFlipOutPanel(
		GrandFlipOutConfig config,
		GrandFlipOutApiConfig apiConfig,
		GrandFlipOutNetworkManager networkManager,
		TradeHistoryManager tradeHistoryManager,
		FlipLogManager flipLogManager)
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		overviewTab = new OverviewTabPanel(config, apiConfig, networkManager, tradeHistoryManager);
		marketTab = new LiveMarketTabPanel(config, networkManager);
		historyTab = new TradeHistoryTabPanel(tradeHistoryManager, flipLogManager);

		tabbedPane = new JTabbedPane();
		tabbedPane.setUI((TabbedPaneUI) RuneLiteTabbedPaneUI.createUI(tabbedPane));
		tabbedPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		tabbedPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		tabbedPane.addTab("Overview", overviewTab);
		tabbedPane.addTab("Live Market", marketTab);
		tabbedPane.addTab("Trade History", historyTab);

		add(tabbedPane, BorderLayout.CENTER);

		refreshTimer = new Timer(REFRESH_INTERVAL_MS, e -> refreshAll());
		refreshTimer.setRepeats(true);
	}

	@Override
	public void onActivate()
	{
		refreshAll();
		refreshTimer.start();
	}

	@Override
	public void onDeactivate()
	{
		refreshTimer.stop();
	}

	public void refreshAll()
	{
		overviewTab.refresh();
		marketTab.refresh();
		historyTab.refresh();
	}

	public void cycleTab()
	{
		int count = tabbedPane.getTabCount();
		if (count <= 1)
		{
			return;
		}
		int next = (tabbedPane.getSelectedIndex() + 1) % count;
		tabbedPane.setSelectedIndex(next);
	}

	public void showTradeHistoryTab()
	{
		tabbedPane.setSelectedIndex(2);
	}
}
