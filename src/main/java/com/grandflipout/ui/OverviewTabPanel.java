package com.grandflipout.ui;

import com.grandflipout.GrandFlipOutApiConfig;
import com.grandflipout.GrandFlipOutConfig;
import com.grandflipout.network.GrandFlipOutNetworkManager;
import com.grandflipout.tracking.CompletedFlip;
import com.grandflipout.tracking.OpenPosition;
import com.grandflipout.tracking.SessionStats;
import com.grandflipout.tracking.TradeHistoryManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Overview tab — session P/L, tax paid, recent flips with per-flip detail,
 * open positions, and market connection status. Designed to look like a
 * real GE flipping tracker, not a generic data panel.
 */
public class OverviewTabPanel extends JPanel
{
	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
		.withZone(ZoneId.systemDefault());
	private static final int MAX_RECENT_FLIPS = 8;

	private final GrandFlipOutConfig config;
	private final GrandFlipOutApiConfig apiConfig;
	private final GrandFlipOutNetworkManager networkManager;
	private final TradeHistoryManager tradeHistoryManager;

	private final JPanel onboardingSlot;
	private final JLabel sessionProfitLabel;
	private final JLabel sessionTaxLabel;
	private final JLabel sessionFlipsLabel;
	private final JLabel totalProfitLabel;
	private final JLabel marketStatusLabel;
	private final JPanel recentFlipsContainer;
	private final JPanel positionsContainer;

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
		setBorder(new EmptyBorder(GrandFlipOutStyles.PADDING, GrandFlipOutStyles.PADDING,
			GrandFlipOutStyles.PADDING, GrandFlipOutStyles.PADDING));

		onboardingSlot = new JPanel();
		onboardingSlot.setLayout(new BoxLayout(onboardingSlot, BoxLayout.Y_AXIS));
		onboardingSlot.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(onboardingSlot);

		// --- Session summary card ---
		JPanel sessionCard = GrandFlipOutStyles.createCard();

		JPanel profitRow = new JPanel(new BorderLayout());
		profitRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		profitRow.add(GrandFlipOutStyles.createSmallLabel("Session profit (after tax)", ColorScheme.MEDIUM_GRAY_COLOR), BorderLayout.WEST);
		sessionProfitLabel = GrandFlipOutStyles.createLabel("0 gp", ColorScheme.LIGHT_GRAY_COLOR);
		sessionProfitLabel.setFont(FontManager.getRunescapeBoldFont());
		profitRow.add(sessionProfitLabel, BorderLayout.EAST);
		sessionCard.add(profitRow);
		sessionCard.add(Box.createVerticalStrut(4));

		sessionTaxLabel = GrandFlipOutStyles.createSmallLabel("GE tax paid: 0 gp", ColorScheme.MEDIUM_GRAY_COLOR);
		sessionTaxLabel.setToolTipText("2% GE tax on all sells, capped at 5M per transaction.");
		sessionCard.add(sessionTaxLabel);
		sessionCard.add(Box.createVerticalStrut(2));

		sessionFlipsLabel = GrandFlipOutStyles.createSmallLabel("Flips: 0  |  Buys: 0  |  Sells: 0", ColorScheme.MEDIUM_GRAY_COLOR);
		sessionCard.add(sessionFlipsLabel);
		sessionCard.add(Box.createVerticalStrut(6));

		JPanel totalRow = new JPanel(new BorderLayout());
		totalRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		totalRow.add(GrandFlipOutStyles.createSmallLabel("All-time profit", ColorScheme.MEDIUM_GRAY_COLOR), BorderLayout.WEST);
		totalProfitLabel = GrandFlipOutStyles.createLabel("0 gp", ColorScheme.BRAND_ORANGE);
		totalProfitLabel.setFont(FontManager.getRunescapeBoldFont());
		totalRow.add(totalProfitLabel, BorderLayout.EAST);
		sessionCard.add(totalRow);

		add(sessionCard);
		add(Box.createVerticalStrut(GrandFlipOutStyles.PADDING));

		// --- Market status ---
		marketStatusLabel = GrandFlipOutStyles.createSmallLabel("Market: —", ColorScheme.MEDIUM_GRAY_COLOR);
		marketStatusLabel.setToolTipText("Live price feed from grandflipout.com");
		add(marketStatusLabel);
		add(Box.createVerticalStrut(GrandFlipOutStyles.PADDING));

		// --- Recent flips ---
		JPanel flipsSection = GrandFlipOutStyles.createSection("Recent flips");
		recentFlipsContainer = new JPanel();
		recentFlipsContainer.setLayout(new BoxLayout(recentFlipsContainer, BoxLayout.Y_AXIS));
		recentFlipsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		flipsSection.add(GrandFlipOutStyles.wrapScroll(recentFlipsContainer));
		add(flipsSection);
		add(Box.createVerticalStrut(GrandFlipOutStyles.PADDING));

		// --- Open positions ---
		JPanel posSection = GrandFlipOutStyles.createSection("Waiting to sell");
		positionsContainer = new JPanel();
		positionsContainer.setLayout(new BoxLayout(positionsContainer, BoxLayout.Y_AXIS));
		positionsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		posSection.add(GrandFlipOutStyles.wrapScroll(positionsContainer));
		add(posSection);

		add(Box.createVerticalGlue());
	}

	public void refresh()
	{
		// Onboarding
		onboardingSlot.removeAll();
		String key = apiConfig.apiKey();
		if (key == null || key.isBlank())
		{
			onboardingSlot.add(OnboardingPanel.create(apiConfig.apiBaseUrl()));
			onboardingSlot.add(Box.createVerticalStrut(GrandFlipOutStyles.PADDING));
		}
		onboardingSlot.revalidate();
		onboardingSlot.repaint();

		// Session stats
		SessionStats stats = tradeHistoryManager.getSessionStats();
		long sessionProfit = stats.getProfitLoss();
		long totalProfit = tradeHistoryManager.getTotalProfitLoss();

		List<CompletedFlip> allFlips = tradeHistoryManager.getCompletedFlips();
		long totalTax = 0;
		for (CompletedFlip f : allFlips)
		{
			totalTax += f.getTaxPaid();
		}

		sessionProfitLabel.setText(GrandFlipOutStyles.formatGpShort(sessionProfit) + " gp");
		sessionProfitLabel.setToolTipText(GrandFlipOutStyles.formatGp(sessionProfit) + " gp");
		sessionProfitLabel.setForeground(sessionProfit >= 0 ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);

		sessionTaxLabel.setText("GE tax paid: " + GrandFlipOutStyles.formatGpShort(totalTax) + " gp");
		sessionFlipsLabel.setText("Flips: " + allFlips.size()
			+ "  |  Buys: " + stats.getBuyCount()
			+ "  |  Sells: " + stats.getSellCount());

		totalProfitLabel.setText(GrandFlipOutStyles.formatGpShort(totalProfit) + " gp");
		totalProfitLabel.setToolTipText(GrandFlipOutStyles.formatGp(totalProfit) + " gp");
		totalProfitLabel.setForeground(totalProfit >= 0 ? ColorScheme.BRAND_ORANGE : ColorScheme.PROGRESS_ERROR_COLOR);

		// Market status
		long lastFetch = networkManager.getLastFetchTimeMs();
		if (lastFetch > 0)
		{
			long minsAgo = (System.currentTimeMillis() - lastFetch) / 60_000;
			marketStatusLabel.setText("Market: updated " + (minsAgo <= 1 ? "just now" : minsAgo + "m ago"));
			marketStatusLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		}
		else
		{
			String err = networkManager.getLastErrorMessage();
			if (err != null && !err.isBlank())
			{
				marketStatusLabel.setText("Market: " + err);
				marketStatusLabel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
			}
			else
			{
				marketStatusLabel.setText(config.apiEnabled() ? "Market: connecting…" : "Market: polling off");
				marketStatusLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			}
		}

		// Recent flips (newest first)
		recentFlipsContainer.removeAll();
		if (allFlips.isEmpty())
		{
			recentFlipsContainer.add(GrandFlipOutStyles.createSmallLabel(
				"No completed flips yet. Buy an item at the GE and sell it for profit.",
				ColorScheme.MEDIUM_GRAY_COLOR));
		}
		else
		{
			List<CompletedFlip> sorted = new ArrayList<>(allFlips);
			sorted.sort(Comparator.comparingLong(CompletedFlip::getCompletedTimestampMs).reversed());
			int show = Math.min(MAX_RECENT_FLIPS, sorted.size());
			for (int i = 0; i < show; i++)
			{
				recentFlipsContainer.add(buildFlipRow(sorted.get(i)));
				recentFlipsContainer.add(Box.createVerticalStrut(GrandFlipOutStyles.ROW_GAP));
			}
		}
		recentFlipsContainer.revalidate();
		recentFlipsContainer.repaint();

		// Open positions
		positionsContainer.removeAll();
		List<OpenPosition> positions = tradeHistoryManager.getOpenPositions();
		if (positions.isEmpty())
		{
			positionsContainer.add(GrandFlipOutStyles.createSmallLabel(
				"No items waiting to sell.", ColorScheme.MEDIUM_GRAY_COLOR));
		}
		else
		{
			for (OpenPosition pos : positions)
			{
				positionsContainer.add(buildPositionRow(pos));
				positionsContainer.add(Box.createVerticalStrut(GrandFlipOutStyles.ROW_GAP));
			}
		}
		positionsContainer.revalidate();
		positionsContainer.repaint();
	}

	private JPanel buildFlipRow(CompletedFlip flip)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(GrandFlipOutStyles.contentBorder());

		String name = flip.getItemName() != null ? flip.getItemName() : "Item " + flip.getItemId();
		String time = TIME_FMT.format(Instant.ofEpochMilli(flip.getCompletedTimestampMs()));
		var left = GrandFlipOutStyles.createSmallLabel(name + " x" + flip.getQuantity(), ColorScheme.LIGHT_GRAY_COLOR);

		long buyEach = flip.getQuantity() > 0 ? flip.getBuyCost() / flip.getQuantity() : 0;
		long sellEach = flip.getQuantity() > 0 ? flip.getSellProceeds() / flip.getQuantity() : 0;
		left.setToolTipText("<html>" + name
			+ "<br>Buy: " + GrandFlipOutStyles.formatGp(buyEach) + " gp ea"
			+ "<br>Sell: " + GrandFlipOutStyles.formatGp(sellEach) + " gp ea"
			+ "<br>Margin: " + GrandFlipOutStyles.formatGp(flip.getMarginPerUnit()) + " gp/ea"
			+ "<br>Tax: " + GrandFlipOutStyles.formatGp(flip.getTaxPaid()) + " gp"
			+ "<br>Time: " + time + "</html>");
		row.add(left, BorderLayout.WEST);

		Color profitColor = flip.getProfit() >= 0 ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR;
		var profitLabel = GrandFlipOutStyles.createSmallLabel(
			(flip.getProfit() >= 0 ? "+" : "") + GrandFlipOutStyles.formatGpShort(flip.getProfit()) + " gp",
			profitColor);
		profitLabel.setFont(FontManager.getRunescapeBoldFont());
		row.add(profitLabel, BorderLayout.EAST);
		return row;
	}

	private JPanel buildPositionRow(OpenPosition pos)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(GrandFlipOutStyles.contentBorder());

		String name = pos.getItemName() != null ? pos.getItemName() : "Item " + pos.getItemId();
		var left = GrandFlipOutStyles.createSmallLabel(name + " x" + pos.getQuantity(), ColorScheme.LIGHT_GRAY_COLOR);
		left.setToolTipText("Avg cost: " + GrandFlipOutStyles.formatGp(pos.getAverageCostPerUnit()) + " gp ea");
		row.add(left, BorderLayout.WEST);

		var cost = GrandFlipOutStyles.createSmallLabel(
			GrandFlipOutStyles.formatGpShort(pos.getTotalCost()) + " gp invested",
			ColorScheme.GRAND_EXCHANGE_PRICE);
		row.add(cost, BorderLayout.EAST);
		return row;
	}
}
