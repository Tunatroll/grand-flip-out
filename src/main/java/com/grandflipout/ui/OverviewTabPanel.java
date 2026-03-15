package com.grandflipout.ui;

import com.grandflipout.GrandFlipOutApiConfig;
import com.grandflipout.GrandFlipOutConfig;
import com.grandflipout.network.GrandFlipOutNetworkManager;
import com.grandflipout.tracking.CompletedFlip;
import com.grandflipout.tracking.OpenPosition;
import com.grandflipout.tracking.SessionStats;
import com.grandflipout.tracking.TradeHistoryManager;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * Overview tab: at-a-glance session profit, all-time profit, market status, positions, flips.
 */
public class OverviewTabPanel extends JPanel
{
	private final GrandFlipOutConfig config;
	private final GrandFlipOutApiConfig apiConfig;
	private final GrandFlipOutNetworkManager networkManager;
	private final TradeHistoryManager tradeHistoryManager;

	private final JPanel onboardingSlot;
	private final JLabel sessionProfitLabel;
	private final JLabel totalProfitLabel;
	private final JLabel marketStatusLabel;
	private final JLabel positionsLabel;
	private final JLabel flipsLabel;

	public OverviewTabPanel(
		GrandFlipOutConfig config,
		GrandFlipOutApiConfig apiConfig,
		GrandFlipOutNetworkManager networkManager,
		TradeHistoryManager tradeHistoryManager)
	{
		this.config = config;
		this.apiConfig = apiConfig;
		this.networkManager = networkManager;
		this.tradeHistoryManager = tradeHistoryManager;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(GrandFlipOutStyles.PADDING, GrandFlipOutStyles.PADDING, GrandFlipOutStyles.PADDING, GrandFlipOutStyles.PADDING));

		onboardingSlot = new JPanel();
		onboardingSlot.setLayout(new BoxLayout(onboardingSlot, BoxLayout.Y_AXIS));
		onboardingSlot.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(onboardingSlot);

		JPanel heroCard = GrandFlipOutStyles.createCard();
		heroCard.add(GrandFlipOutStyles.createSmallLabel("Session profit", ColorScheme.MEDIUM_GRAY_COLOR));
		heroCard.add(Box.createVerticalStrut(2));
		sessionProfitLabel = GrandFlipOutStyles.createLabel("0 gp", ColorScheme.LIGHT_GRAY_COLOR);
		sessionProfitLabel.setFont(net.runelite.client.ui.FontManager.getRunescapeBoldFont());
		heroCard.add(sessionProfitLabel);
		heroCard.add(Box.createVerticalStrut(12));
		heroCard.add(GrandFlipOutStyles.createSmallLabel("All-time profit", ColorScheme.MEDIUM_GRAY_COLOR));
		heroCard.add(Box.createVerticalStrut(2));
		totalProfitLabel = GrandFlipOutStyles.createLabel("0 gp", ColorScheme.BRAND_ORANGE);
		totalProfitLabel.setFont(net.runelite.client.ui.FontManager.getRunescapeBoldFont());
		heroCard.add(totalProfitLabel);
		add(heroCard);

		add(Box.createVerticalStrut(GrandFlipOutStyles.PADDING));

		JPanel statusCard = GrandFlipOutStyles.createCard();
		marketStatusLabel = GrandFlipOutStyles.createSmallLabel("Market: —", ColorScheme.MEDIUM_GRAY_COLOR);
		marketStatusLabel.setToolTipText("Last time live market data was updated from your API.");
		statusCard.add(marketStatusLabel);
		statusCard.add(Box.createVerticalStrut(4));
		positionsLabel = GrandFlipOutStyles.createSmallLabel("Open positions: 0", ColorScheme.LIGHT_GRAY_COLOR);
		positionsLabel.setToolTipText("Items you've bought but not yet sold.");
		statusCard.add(positionsLabel);
		statusCard.add(Box.createVerticalStrut(4));
		flipsLabel = GrandFlipOutStyles.createSmallLabel("Completed flips: 0", ColorScheme.LIGHT_GRAY_COLOR);
		flipsLabel.setToolTipText("Matched buy+sell pairs (FIFO).");
		statusCard.add(flipsLabel);
		add(statusCard);

		add(Box.createVerticalGlue());

		JLabel brandLabel = GrandFlipOutStyles.createSmallLabel("More at grandflipout.com", ColorScheme.MEDIUM_GRAY_COLOR);
		brandLabel.setToolTipText("Your market research and API.");
		JPanel brandWrap = new JPanel(new BorderLayout());
		brandWrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		brandWrap.setBorder(new EmptyBorder(GrandFlipOutStyles.PADDING, 0, 0, 0));
		brandWrap.add(brandLabel, BorderLayout.CENTER);
		add(brandWrap);
	}

	public void refresh()
	{
		onboardingSlot.removeAll();
		String key = apiConfig.apiKey();
		if (key == null || key.isBlank())
		{
			onboardingSlot.add(OnboardingPanel.create(apiConfig.apiBaseUrl()));
			onboardingSlot.add(Box.createVerticalStrut(GrandFlipOutStyles.PADDING));
		}
		onboardingSlot.revalidate();
		onboardingSlot.repaint();

		SessionStats stats = tradeHistoryManager.getSessionStats();
		long sessionProfit = stats.getProfitLoss();
		long totalProfit = tradeHistoryManager.getTotalProfitLoss();

		sessionProfitLabel.setText(GrandFlipOutStyles.formatGpShort(sessionProfit) + " gp");
		sessionProfitLabel.setToolTipText(GrandFlipOutStyles.formatGp(sessionProfit) + " gp");
		sessionProfitLabel.setForeground(sessionProfit >= 0 ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
		totalProfitLabel.setText(GrandFlipOutStyles.formatGpShort(totalProfit) + " gp");
		totalProfitLabel.setToolTipText(GrandFlipOutStyles.formatGp(totalProfit) + " gp");
		totalProfitLabel.setForeground(totalProfit >= 0 ? ColorScheme.BRAND_ORANGE : ColorScheme.PROGRESS_ERROR_COLOR);

		long lastFetch = networkManager.getLastFetchTimeMs();
		if (lastFetch > 0)
		{
			long minsAgo = (System.currentTimeMillis() - lastFetch) / 60_000;
			marketStatusLabel.setText("Market: updated " + (minsAgo <= 1 ? "just now" : minsAgo + "m ago"));
			marketStatusLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		}
		else
		{
			marketStatusLabel.setText(config.apiEnabled() ? "Market: waiting for data…" : "Market: disabled");
			marketStatusLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		}

		List<OpenPosition> positions = tradeHistoryManager.getOpenPositions();
		positionsLabel.setText("Open positions: " + positions.size());

		List<CompletedFlip> flips = tradeHistoryManager.getCompletedFlips();
		flipsLabel.setText("Completed flips: " + flips.size());
	}
}
