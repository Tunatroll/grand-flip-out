package com.grandflipout.ui;

import com.grandflipout.tracking.OpenPosition;
import com.grandflipout.tracking.ProfitBreakdown;
import com.grandflipout.tracking.SessionStats;
import com.grandflipout.tracking.FlipLogEntry;
import com.grandflipout.tracking.FlipLogManager;
import com.grandflipout.tracking.TradeHistoryManager;
import com.grandflipout.tracking.TradeRecord;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.PluginErrorPanel;

/**
 * Trade History tab: session summary, open positions, profit by item, recent trades.
 */
public class TradeHistoryTabPanel extends JPanel
{
	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm")
		.withZone(ZoneId.systemDefault());

	private final TradeHistoryManager tradeHistoryManager;
	private final FlipLogManager flipLogManager;
	private final JPanel statsContainer;
	private final JPanel positionsContainer;
	private final JPanel breakdownContainer;
	private final JPanel recordsContainer;
	private final JPanel flipLogsContainer;

	public TradeHistoryTabPanel(TradeHistoryManager tradeHistoryManager, FlipLogManager flipLogManager)
	{
		this.tradeHistoryManager = tradeHistoryManager;
		this.flipLogManager = flipLogManager;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(GrandFlipOutStyles.PADDING, GrandFlipOutStyles.PADDING, GrandFlipOutStyles.PADDING, GrandFlipOutStyles.PADDING));

		JPanel sessionSection = GrandFlipOutStyles.createSection("Session summary");
		statsContainer = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
		statsContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		statsContainer.setBorder(new EmptyBorder(GrandFlipOutStyles.CARD_PADDING, GrandFlipOutStyles.CARD_PADDING, GrandFlipOutStyles.CARD_PADDING, GrandFlipOutStyles.CARD_PADDING));
		sessionSection.add(statsContainer);
		add(sessionSection);

		add(Box.createVerticalStrut(GrandFlipOutStyles.PADDING));

		JPanel positionsSection = GrandFlipOutStyles.createSection("Open positions");
		positionsContainer = new JPanel();
		positionsContainer.setLayout(new BoxLayout(positionsContainer, BoxLayout.Y_AXIS));
		positionsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		positionsSection.add(GrandFlipOutStyles.wrapScroll(positionsContainer));
		add(positionsSection);

		add(Box.createVerticalStrut(GrandFlipOutStyles.PADDING));

		JPanel breakdownSection = GrandFlipOutStyles.createSection("Profit by item");
		breakdownContainer = new JPanel();
		breakdownContainer.setLayout(new BoxLayout(breakdownContainer, BoxLayout.Y_AXIS));
		breakdownContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		breakdownSection.add(GrandFlipOutStyles.wrapScroll(breakdownContainer));
		add(breakdownSection);

		add(Box.createVerticalStrut(GrandFlipOutStyles.PADDING));

		JPanel recentSection = GrandFlipOutStyles.createSection("Recent trades");
		recordsContainer = new JPanel();
		recordsContainer.setLayout(new BoxLayout(recordsContainer, BoxLayout.Y_AXIS));
		recordsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		recentSection.add(GrandFlipOutStyles.wrapScroll(recordsContainer));
		add(recentSection);

		add(Box.createVerticalStrut(GrandFlipOutStyles.PADDING));

		JPanel logsSection = GrandFlipOutStyles.createSection("Flip logs");
		JPanel actionRow = new JPanel(new java.awt.GridLayout(2, 0, 6, 6));
		actionRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JButton copyLogsButton = new JButton("Copy Logs JSON");
		copyLogsButton.addActionListener(e -> copyToClipboard(flipLogManager.exportJson()));
		JButton pasteLogsButton = new JButton("Paste Logs JSON");
		pasteLogsButton.addActionListener(e -> {
			String text = readFromClipboard();
			if (text != null && flipLogManager.importJson(text))
			{
				refreshFlipLogs();
			}
		});
		JButton copyTradesButton = new JButton("Copy Trades JSON");
		copyTradesButton.addActionListener(e -> copyToClipboard(tradeHistoryManager.exportJson()));
		JButton pasteTradesButton = new JButton("Paste Trades JSON");
		pasteTradesButton.addActionListener(e -> {
			String text = readFromClipboard();
			if (text != null && tradeHistoryManager.importJson(text))
			{
				refresh();
			}
		});
		JButton copyLogsCsvButton = new JButton("Copy Logs CSV");
		copyLogsCsvButton.addActionListener(e -> copyToClipboard(flipLogManager.exportCsv()));
		JButton copyTradesCsvButton = new JButton("Copy Trades CSV");
		copyTradesCsvButton.addActionListener(e -> copyToClipboard(tradeHistoryManager.exportCsv()));
		JButton clearLogsButton = new JButton("Clear Logs");
		clearLogsButton.addActionListener(e -> {
			flipLogManager.clear();
			refreshFlipLogs();
		});
		JButton clearTradesButton = new JButton("Clear Trades");
		clearTradesButton.addActionListener(e -> {
			tradeHistoryManager.clearHistory();
			refresh();
		});
		actionRow.add(copyLogsButton);
		actionRow.add(pasteLogsButton);
		actionRow.add(copyTradesButton);
		actionRow.add(pasteTradesButton);
		actionRow.add(copyLogsCsvButton);
		actionRow.add(copyTradesCsvButton);
		actionRow.add(clearLogsButton);
		actionRow.add(clearTradesButton);
		logsSection.add(actionRow);

		JPanel fileRow = new JPanel(new java.awt.GridLayout(1, 0, 6, 0));
		fileRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JButton exportTradesFileButton = new JButton("Export trades to file");
		exportTradesFileButton.setToolTipText("Save trade history as a JSON file (no clipboard).");
		exportTradesFileButton.addActionListener(e -> exportTradesToFile());
		JButton importTradesFileButton = new JButton("Import trades from file");
		importTradesFileButton.setToolTipText("Load trade history from a JSON file.");
		importTradesFileButton.addActionListener(e -> importTradesFromFile());
		JButton exportLogsFileButton = new JButton("Export logs to file");
		exportLogsFileButton.setToolTipText("Save flip logs as a JSON file (no clipboard).");
		exportLogsFileButton.addActionListener(e -> exportLogsToFile());
		JButton importLogsFileButton = new JButton("Import logs from file");
		importLogsFileButton.setToolTipText("Load flip logs from a JSON file.");
		importLogsFileButton.addActionListener(e -> importLogsFromFile());
		fileRow.add(exportTradesFileButton);
		fileRow.add(importTradesFileButton);
		fileRow.add(exportLogsFileButton);
		fileRow.add(importLogsFileButton);
		logsSection.add(fileRow);
		logsSection.add(Box.createVerticalStrut(6));

		flipLogsContainer = new JPanel();
		flipLogsContainer.setLayout(new BoxLayout(flipLogsContainer, BoxLayout.Y_AXIS));
		flipLogsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		logsSection.add(GrandFlipOutStyles.wrapScroll(flipLogsContainer));
		add(logsSection);
	}

	public void refresh()
	{
		refreshSessionStats();
		refreshOpenPositions();
		refreshProfitBreakdown();
		refreshRecentTrades();
		refreshFlipLogs();
	}

	private void refreshSessionStats()
	{
		statsContainer.removeAll();
		SessionStats stats = tradeHistoryManager.getSessionStats();
		statsContainer.add(GrandFlipOutStyles.createStatRow("Session profit", GrandFlipOutStyles.formatGp(stats.getProfitLoss()) + " gp",
			stats.getProfitLoss() >= 0 ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR));
		statsContainer.add(GrandFlipOutStyles.createStatRow("Buys / Sells", stats.getBuyCount() + " / " + stats.getSellCount(), ColorScheme.LIGHT_GRAY_COLOR));
		statsContainer.add(GrandFlipOutStyles.createStatRow("Items traded", String.valueOf(stats.getItemsTraded()), ColorScheme.LIGHT_GRAY_COLOR));
		statsContainer.add(GrandFlipOutStyles.createStatRow("All-time profit", GrandFlipOutStyles.formatGp(tradeHistoryManager.getTotalProfitLoss()) + " gp", ColorScheme.BRAND_ORANGE));
		statsContainer.revalidate();
		statsContainer.repaint();
	}

	private void refreshOpenPositions()
	{
		positionsContainer.removeAll();
		List<OpenPosition> positions = tradeHistoryManager.getOpenPositions();
		if (positions.isEmpty())
		{
			var empty = GrandFlipOutStyles.createSmallLabel("No open positions.", ColorScheme.MEDIUM_GRAY_COLOR);
			empty.setBorder(new EmptyBorder(4, 0, 4, 0));
			positionsContainer.add(empty);
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

	private void refreshProfitBreakdown()
	{
		breakdownContainer.removeAll();
		List<ProfitBreakdown> breakdowns = tradeHistoryManager.getProfitBreakdowns();
		if (breakdowns.isEmpty())
		{
			var empty = GrandFlipOutStyles.createSmallLabel("No profit data yet.", ColorScheme.MEDIUM_GRAY_COLOR);
			empty.setBorder(new EmptyBorder(4, 0, 4, 0));
			breakdownContainer.add(empty);
		}
		else
		{
			for (ProfitBreakdown b : breakdowns)
			{
				breakdownContainer.add(buildBreakdownRow(b));
				breakdownContainer.add(Box.createVerticalStrut(GrandFlipOutStyles.ROW_GAP));
			}
		}
		breakdownContainer.revalidate();
		breakdownContainer.repaint();
	}

	private void refreshRecentTrades()
	{
		recordsContainer.removeAll();
		List<TradeRecord> records = tradeHistoryManager.getRecords();
		int show = Math.min(25, records.size());
		if (records.isEmpty())
		{
			PluginErrorPanel err = new PluginErrorPanel();
			err.setContent("Recent trades", "No trades recorded yet. Trades appear here when you buy or sell at the GE.");
			recordsContainer.add(err);
		}
		else
		{
			for (int i = records.size() - 1; i >= records.size() - show && i >= 0; i--)
			{
				TradeRecord r = records.get(i);
				recordsContainer.add(buildTradeRow(r));
				recordsContainer.add(Box.createVerticalStrut(GrandFlipOutStyles.ROW_GAP));
			}
		}
		recordsContainer.revalidate();
		recordsContainer.repaint();
	}

	private JPanel buildPositionRow(OpenPosition pos)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(GrandFlipOutStyles.contentBorder());
		String name = pos.getItemName() != null ? pos.getItemName() : "Item " + pos.getItemId();
		var nameL = GrandFlipOutStyles.createSmallLabel(name + " x" + pos.getQuantity(), ColorScheme.LIGHT_GRAY_COLOR);
		nameL.setToolTipText("Held: " + pos.getQuantity() + " @ avg " + GrandFlipOutStyles.formatGp(pos.getAverageCostPerUnit()) + " gp");
		row.add(nameL, BorderLayout.WEST);
		var costL = GrandFlipOutStyles.createSmallLabel(GrandFlipOutStyles.formatGp(pos.getTotalCost()) + " gp", ColorScheme.MEDIUM_GRAY_COLOR);
		costL.setToolTipText("Total cost (unrealized)");
		row.add(costL, BorderLayout.EAST);
		return row;
	}

	private JPanel buildBreakdownRow(ProfitBreakdown b)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(GrandFlipOutStyles.contentBorder());
		String name = b.getItemName() != null ? b.getItemName() : "Item " + b.getItemId();
		var nameL = GrandFlipOutStyles.createSmallLabel(name, ColorScheme.LIGHT_GRAY_COLOR);
		nameL.setToolTipText("Flips: " + b.getFlipCount() + " | ROI: " + GrandFlipOutStyles.formatPct(b.getRoiPercent()) + "% | Margin: " + GrandFlipOutStyles.formatPct(b.getMarginPercent()) + "%");
		row.add(nameL, BorderLayout.WEST);
		java.awt.Color profitColor = b.getProfitLoss() >= 0 ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR;
		row.add(GrandFlipOutStyles.createSmallLabel(GrandFlipOutStyles.formatGp(b.getProfitLoss()) + " gp (" + GrandFlipOutStyles.formatPct(b.getRoiPercent()) + "%)", profitColor), BorderLayout.EAST);
		return row;
	}

	private JPanel buildTradeRow(TradeRecord r)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(GrandFlipOutStyles.contentBorder());
		String typeStr = r.getType() == TradeRecord.Type.BUY ? "Buy" : "Sell";
		java.awt.Color typeColor = r.getType() == TradeRecord.Type.BUY ? ColorScheme.GRAND_EXCHANGE_PRICE : ColorScheme.GRAND_EXCHANGE_ALCH;
		String name = r.getItemName() != null ? r.getItemName() : "Item";
		var left = GrandFlipOutStyles.createSmallLabel(typeStr + " " + name + " x" + r.getQuantity(), typeColor);
		left.setToolTipText(r.getPricePerUnit() + " gp each");
		row.add(left, BorderLayout.WEST);
		row.add(GrandFlipOutStyles.createSmallLabel(GrandFlipOutStyles.formatGp(r.getTotalValue()) + " gp", ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.EAST);
		return row;
	}

	private void refreshFlipLogs()
	{
		flipLogsContainer.removeAll();
		List<FlipLogEntry> logs = flipLogManager.getLogs();
		int show = Math.min(40, logs.size());
		if (logs.isEmpty())
		{
			PluginErrorPanel err = new PluginErrorPanel();
			err.setContent("Flip logs", "No flip logs yet. Logs are captured automatically from GE activity.");
			flipLogsContainer.add(err);
		}
		else
		{
			for (int i = logs.size() - 1; i >= logs.size() - show && i >= 0; i--)
			{
				FlipLogEntry entry = logs.get(i);
				flipLogsContainer.add(buildFlipLogRow(entry));
				flipLogsContainer.add(Box.createVerticalStrut(GrandFlipOutStyles.ROW_GAP));
			}
		}
		flipLogsContainer.revalidate();
		flipLogsContainer.repaint();
	}

	private JPanel buildFlipLogRow(FlipLogEntry entry)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(GrandFlipOutStyles.contentBorder());

		String ts = TIME_FMT.format(Instant.ofEpochMilli(entry.getTimestampMs()));
		String side = entry.getType() == TradeRecord.Type.BUY ? "Buy" : "Sell";
		java.awt.Color sideColor = entry.getType() == TradeRecord.Type.BUY ? ColorScheme.GRAND_EXCHANGE_PRICE : ColorScheme.GRAND_EXCHANGE_ALCH;
		String name = entry.getItemName() != null ? entry.getItemName() : "Item";
		var left = GrandFlipOutStyles.createSmallLabel(ts + "  " + side + " " + name + " x" + entry.getQuantity(), sideColor);
		left.setToolTipText("Slot " + entry.getOfferSlot() + " @ " + entry.getPricePerUnit() + " gp");
		row.add(left, BorderLayout.WEST);

		row.add(GrandFlipOutStyles.createSmallLabel(GrandFlipOutStyles.formatGp(entry.getTotalValue()) + " gp", ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.EAST);
		return row;
	}

	private void exportTradesToFile()
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setSelectedFile(new java.io.File("grandflipout-trades-" + LocalDate.now() + ".json"));
		chooser.setFileFilter(new FileNameExtensionFilter("JSON files (*.json)", "json"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		java.io.File file = chooser.getSelectedFile();
		String path = file.getAbsolutePath();
		if (!path.endsWith(".json"))
		{
			path = path + ".json";
		}
		try
		{
			Files.write(java.nio.file.Path.of(path), tradeHistoryManager.exportJson().getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, "Failed to save: " + e.getMessage(), "Export error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void importTradesFromFile()
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setFileFilter(new FileNameExtensionFilter("JSON files (*.json)", "json"));
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		try
		{
			String content = Files.readString(chooser.getSelectedFile().toPath(), StandardCharsets.UTF_8);
			if (tradeHistoryManager.importJson(content))
			{
				refresh();
			}
			else
			{
				JOptionPane.showMessageDialog(this, "File format not recognized or import failed.", "Import error", JOptionPane.ERROR_MESSAGE);
			}
		}
		catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, "Failed to read file: " + e.getMessage(), "Import error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void exportLogsToFile()
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setSelectedFile(new java.io.File("grandflipout-logs-" + LocalDate.now() + ".json"));
		chooser.setFileFilter(new FileNameExtensionFilter("JSON files (*.json)", "json"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		java.io.File file = chooser.getSelectedFile();
		String path = file.getAbsolutePath();
		if (!path.endsWith(".json"))
		{
			path = path + ".json";
		}
		try
		{
			Files.write(java.nio.file.Path.of(path), flipLogManager.exportJson().getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, "Failed to save: " + e.getMessage(), "Export error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void importLogsFromFile()
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setFileFilter(new FileNameExtensionFilter("JSON files (*.json)", "json"));
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		try
		{
			String content = Files.readString(chooser.getSelectedFile().toPath(), StandardCharsets.UTF_8);
			if (flipLogManager.importJson(content))
			{
				refreshFlipLogs();
			}
			else
			{
				JOptionPane.showMessageDialog(this, "File format not recognized or import failed.", "Import error", JOptionPane.ERROR_MESSAGE);
			}
		}
		catch (IOException e)
		{
			JOptionPane.showMessageDialog(this, "Failed to read file: " + e.getMessage(), "Import error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private static void copyToClipboard(String text)
	{
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
		}
		catch (Exception ignored)
		{
			// Clipboard access may fail in restricted environments.
		}
	}

	private static String readFromClipboard()
	{
		try
		{
			if (Toolkit.getDefaultToolkit().getSystemClipboard().isDataFlavorAvailable(DataFlavor.stringFlavor))
			{
				return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
			}
		}
		catch (Exception ignored)
		{
			// Clipboard access may fail in restricted environments.
		}
		return null;
	}
}
