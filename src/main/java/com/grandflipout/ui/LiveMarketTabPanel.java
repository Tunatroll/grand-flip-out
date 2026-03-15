package com.grandflipout.ui;

import com.grandflipout.GrandFlipOutConfig;
import com.grandflipout.network.GrandFlipOutNetworkManager;
import com.grandflipout.network.MarketDataResponse;
import com.grandflipout.network.MarketItemDto;
import com.grandflipout.network.MarketOpportunityDto;
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
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.PluginErrorPanel;

/**
 * Live Market tab: market data list with buy/sell/margin %, sorted by margin.
 */
public class LiveMarketTabPanel extends JPanel
{
	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
		.withZone(ZoneId.systemDefault());

	private final GrandFlipOutConfig config;
	private final GrandFlipOutNetworkManager networkManager;
	private final JLabel lastUpdateLabel;
	private final JLabel apiStatusLabel;
	private final JLabel strategyLabel;
	private final JPanel opportunityContainer;
	private final JPanel itemsContainer;

	public LiveMarketTabPanel(
		GrandFlipOutConfig config,
		GrandFlipOutNetworkManager networkManager)
	{
		this.config = config;
		this.networkManager = networkManager;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(GrandFlipOutStyles.PADDING, GrandFlipOutStyles.PADDING, GrandFlipOutStyles.PADDING, GrandFlipOutStyles.PADDING));

		JPanel headerCard = GrandFlipOutStyles.createCard();
		JLabel sectionTitle = GrandFlipOutStyles.createLabel("Live market data", ColorScheme.BRAND_ORANGE);
		sectionTitle.setFont(net.runelite.client.ui.FontManager.getRunescapeBoldFont());
		headerCard.add(sectionTitle);
		JLabel poweredBy = GrandFlipOutStyles.createSmallLabel("Powered by grandflipout.com", ColorScheme.MEDIUM_GRAY_COLOR);
		poweredBy.setToolTipText("Ranked opportunities and live prices come from your Grand Flip Out API.");
		headerCard.add(poweredBy);
		headerCard.add(Box.createVerticalStrut(4));
		lastUpdateLabel = GrandFlipOutStyles.createSmallLabel("Last update: —", ColorScheme.MEDIUM_GRAY_COLOR);
		headerCard.add(lastUpdateLabel);
		headerCard.add(Box.createVerticalStrut(2));
		apiStatusLabel = GrandFlipOutStyles.createSmallLabel("API status: —", ColorScheme.MEDIUM_GRAY_COLOR);
		headerCard.add(apiStatusLabel);
		strategyLabel = GrandFlipOutStyles.createSmallLabel("Strategy: default", ColorScheme.MEDIUM_GRAY_COLOR);
		strategyLabel.setToolTipText("Configuration → Grand Flip Out → Opportunity strategy");
		headerCard.add(strategyLabel);
		add(headerCard);

		add(Box.createVerticalStrut(GrandFlipOutStyles.PADDING));

		JPanel opportunitySection = GrandFlipOutStyles.createSection("Top opportunities (from API)");
		opportunitySection.setToolTipText("Ranked and scored by grandflipout.com. Order and confidence come from your backend.");
		opportunityContainer = new JPanel();
		opportunityContainer.setLayout(new BoxLayout(opportunityContainer, BoxLayout.Y_AXIS));
		opportunityContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		opportunitySection.add(GrandFlipOutStyles.wrapScroll(opportunityContainer));
		add(opportunitySection);

		add(Box.createVerticalStrut(GrandFlipOutStyles.PADDING));

		JPanel itemsSection = GrandFlipOutStyles.createSection("Market items");
		itemsSection.setToolTipText("All items with buy/sell prices from the API, sorted by margin.");
		itemsContainer = new JPanel();
		itemsContainer.setLayout(new BoxLayout(itemsContainer, BoxLayout.Y_AXIS));
		itemsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		itemsSection.add(GrandFlipOutStyles.wrapScroll(itemsContainer));
		add(itemsSection);
	}

	public void refresh()
	{
		long lastFetch = networkManager.getLastFetchTimeMs();
		if (lastFetch > 0)
		{
			String timeStr = TIME_FMT.format(Instant.ofEpochMilli(lastFetch));
			lastUpdateLabel.setText("Last update: " + timeStr);
			lastUpdateLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		}
		else
		{
			lastUpdateLabel.setText(config.apiEnabled() ? "Waiting for data…" : "API polling disabled");
			lastUpdateLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		}
		String lastError = networkManager.getLastErrorMessage();
		if (lastError != null && !lastError.isBlank())
		{
			apiStatusLabel.setText("API status: " + lastError);
			apiStatusLabel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
		}
		else
		{
			apiStatusLabel.setText(config.apiEnabled() ? "API status: connected" : "API status: disabled");
			apiStatusLabel.setForeground(config.apiEnabled() ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.MEDIUM_GRAY_COLOR);
		}
		String strategy = config.opportunityStrategy();
		if (strategy != null && !strategy.isBlank() && !"default".equalsIgnoreCase(strategy.trim()))
		{
			strategyLabel.setText("Strategy: " + strategy.trim());
			strategyLabel.setForeground(ColorScheme.BRAND_ORANGE);
		}
		else
		{
			strategyLabel.setText("Strategy: default");
			strategyLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		}

		itemsContainer.removeAll();
		MarketDataResponse data = networkManager.getLatestMarketData();
		refreshOpportunities(data);
		if (data != null && data.getItems() != null && !data.getItems().isEmpty())
		{
			List<MarketItemDto> items = new ArrayList<>(data.getItems());
			items.sort(Comparator.comparing(this::marginPctForItem).reversed());
			for (MarketItemDto item : items)
			{
				itemsContainer.add(buildMarketItemRow(item));
				itemsContainer.add(Box.createVerticalStrut(GrandFlipOutStyles.ROW_GAP));
			}
		}
		else
		{
			PluginErrorPanel err = new PluginErrorPanel();
			err.setContent("Live market", "No market data yet. Enable API polling in config and ensure grandflipout.com is reachable.");
			itemsContainer.add(err);
		}
		itemsContainer.revalidate();
		itemsContainer.repaint();
	}

	private void refreshOpportunities(MarketDataResponse data)
	{
		opportunityContainer.removeAll();
		if (data == null || data.getOpportunities() == null || data.getOpportunities().isEmpty())
		{
			JLabel empty = GrandFlipOutStyles.createSmallLabel("No ranked opportunities yet. Configure your API in Grand Flip Out settings.", ColorScheme.MEDIUM_GRAY_COLOR);
			empty.setBorder(new EmptyBorder(4, 0, 4, 0));
			empty.setToolTipText("Ranked opportunities are provided by grandflipout.com.");
			opportunityContainer.add(empty);
		}
		else
		{
			List<MarketOpportunityDto> sorted = new ArrayList<>(data.getOpportunities());
			sorted.sort(Comparator
				.comparing(MarketOpportunityDto::getConfidence, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(MarketOpportunityDto::getMarginPercent, Comparator.nullsLast(Comparator.naturalOrder()))
				.reversed());
			int limit = Math.min(8, sorted.size());
			for (int i = 0; i < limit; i++)
			{
				opportunityContainer.add(buildOpportunityRow(sorted.get(i)));
				opportunityContainer.add(Box.createVerticalStrut(GrandFlipOutStyles.ROW_GAP));
			}
		}
		opportunityContainer.revalidate();
		opportunityContainer.repaint();
	}

	private double marginPctForItem(MarketItemDto item)
	{
		Long buy = item.getBuyPrice();
		Long sell = item.getSellPrice();
		if (buy == null || sell == null || buy <= 0) return 0;
		long tax = Math.min(Math.round(sell * 0.02), 5_000_000);
		return 100.0 * (sell - buy - tax) / buy;
	}

	private JPanel buildMarketItemRow(MarketItemDto item)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(GrandFlipOutStyles.contentBorder());

		String name = item.getName() != null ? item.getName() : "Item " + item.getId();
		row.add(GrandFlipOutStyles.createLabel(name, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.WEST);

		JPanel right = new JPanel(new GridLayout(1, 0, 6, 0));
		right.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		Long buy = item.getBuyPrice();
		Long sell = item.getSellPrice();
		if (buy != null)
		{
			JLabel buyL = GrandFlipOutStyles.createSmallLabel(GrandFlipOutStyles.formatGpShort(buy), ColorScheme.GRAND_EXCHANGE_PRICE);
			buyL.setToolTipText("Buy: " + GrandFlipOutStyles.formatGp(buy) + " gp");
			right.add(buyL);
		}
		if (sell != null)
		{
			JLabel sellL = GrandFlipOutStyles.createSmallLabel(GrandFlipOutStyles.formatGpShort(sell), ColorScheme.GRAND_EXCHANGE_ALCH);
			sellL.setToolTipText("Sell: " + GrandFlipOutStyles.formatGp(sell) + " gp");
			right.add(sellL);
		}
		if (buy != null && sell != null && buy > 0)
		{
			long tax = Math.min(Math.round(sell * 0.02), 5_000_000);
			long margin = sell - buy - tax;
			double pct = 100.0 * margin / buy;
			Color marginColor = margin >= 0 ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR;
			JLabel marginL = GrandFlipOutStyles.createSmallLabel(GrandFlipOutStyles.formatPct(pct) + "%", marginColor);
			marginL.setToolTipText("<html>Margin: " + GrandFlipOutStyles.formatGp(margin) + " gp/ea (after 2% tax)"
				+ "<br>Tax: " + GrandFlipOutStyles.formatGp(tax) + " gp/ea</html>");
			right.add(marginL);
		}
		row.add(right, BorderLayout.EAST);

		return row;
	}

	private JPanel buildOpportunityRow(MarketOpportunityDto opp)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(GrandFlipOutStyles.contentBorder());

		String name = opp.getItemName() != null ? opp.getItemName() : "Item " + opp.getItemId();
		JLabel left = GrandFlipOutStyles.createSmallLabel(name, ColorScheme.LIGHT_GRAY_COLOR);
		StringBuilder tip = new StringBuilder("<html>" + name);
		if (opp.getBuyPrice() != null) tip.append("<br>Buy: ").append(GrandFlipOutStyles.formatGp(opp.getBuyPrice())).append(" gp");
		if (opp.getSellPrice() != null) tip.append("<br>Sell: ").append(GrandFlipOutStyles.formatGp(opp.getSellPrice())).append(" gp");
		if (opp.getTaxPerUnit() != null) tip.append("<br>GE tax: ").append(GrandFlipOutStyles.formatGp(opp.getTaxPerUnit())).append(" gp/ea");
		if (opp.getMarginGp() != null) tip.append("<br>Margin (after tax): ").append(GrandFlipOutStyles.formatGp(opp.getMarginGp())).append(" gp/ea");
		if (opp.getVolume() != null) tip.append("<br>Volume: ").append(GrandFlipOutStyles.formatGp(opp.getVolume()));
		String reason = opp.getReason();
		if (reason != null && !reason.isBlank()) tip.append("<br>").append(reason);
		tip.append("</html>");
		left.setToolTipText(tip.toString());
		row.add(left, BorderLayout.WEST);

		JPanel right = new JPanel(new GridLayout(1, 0, 6, 0));
		right.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		if (opp.getMarginGp() != null)
		{
			JLabel gpL = GrandFlipOutStyles.createSmallLabel(GrandFlipOutStyles.formatGpShort(opp.getMarginGp()) + " gp", ColorScheme.GRAND_EXCHANGE_ALCH);
			gpL.setToolTipText(GrandFlipOutStyles.formatGp(opp.getMarginGp()) + " gp margin");
			right.add(gpL);
		}
		if (opp.getMarginPercent() != null)
		{
			JLabel pctL = GrandFlipOutStyles.createSmallLabel(GrandFlipOutStyles.formatPct(opp.getMarginPercent()) + "%", ColorScheme.PROGRESS_COMPLETE_COLOR);
			pctL.setToolTipText("Margin: " + GrandFlipOutStyles.formatPct(opp.getMarginPercent()) + "%");
			right.add(pctL);
		}
		if (opp.getConfidence() != null)
		{
			JLabel confL = GrandFlipOutStyles.createSmallLabel("Conf " + GrandFlipOutStyles.formatPct(opp.getConfidence() * 100) + "%", ColorScheme.BRAND_ORANGE);
			confL.setToolTipText("Confidence score from grandflipout.com. Higher = stronger signal.");
			right.add(confL);
		}
		row.add(right, BorderLayout.EAST);
		return row;
	}
}
