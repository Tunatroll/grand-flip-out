package com.grandflipout.ui;

import java.awt.Color;
import java.text.NumberFormat;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Shared UI constants and helpers for Grand Flip Out panels.
 * Keeps styling and formatting in one place (no monolithic panel).
 */
public final class GrandFlipOutStyles
{
	public static final int PADDING = 8;
	public static final int CARD_PADDING = 10;
	public static final int ROW_GAP = 4;

	private static final NumberFormat GP_FORMAT = NumberFormat.getNumberInstance(Locale.US);
	private static final NumberFormat PCT_FORMAT;

	static
	{
		PCT_FORMAT = NumberFormat.getNumberInstance(Locale.US);
		PCT_FORMAT.setMinimumFractionDigits(1);
		PCT_FORMAT.setMaximumFractionDigits(1);
	}

	private GrandFlipOutStyles() { }

	public static String formatGp(long gp)
	{
		return GP_FORMAT.format(gp);
	}

	public static String formatPct(double pct)
	{
		return PCT_FORMAT.format(pct);
	}

	public static JPanel createCard()
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1),
			new EmptyBorder(CARD_PADDING, CARD_PADDING, CARD_PADDING, CARD_PADDING)
		));
		return card;
	}

	public static JPanel createSection(String title)
	{
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setBackground(ColorScheme.DARK_GRAY_COLOR);
		section.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(0, 0, PADDING, 0)
		));
		JLabel titleLabel = createLabel(title, ColorScheme.BRAND_ORANGE);
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		section.add(titleLabel);
		section.add(Box.createVerticalStrut(6));
		return section;
	}

	public static JScrollPane wrapScroll(JPanel content)
	{
		JScrollPane scroll = new JScrollPane(content);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.getViewport().setBackground(content.getBackground());
		scroll.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		return scroll;
	}

	public static JLabel createLabel(String text, Color color)
	{
		JLabel l = new JLabel(text);
		l.setForeground(color);
		l.setFont(FontManager.getRunescapeFont());
		return l;
	}

	public static JLabel createSmallLabel(String text, Color color)
	{
		JLabel l = new JLabel(text);
		l.setForeground(color);
		l.setFont(FontManager.getRunescapeSmallFont());
		return l;
	}

	public static JPanel createStatRow(String label, String value, Color valueColor)
	{
		JPanel row = new JPanel(new java.awt.BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JLabel l = createSmallLabel(label, ColorScheme.MEDIUM_GRAY_COLOR);
		JLabel v = createSmallLabel(value, valueColor);
		v.setFont(FontManager.getRunescapeBoldFont());
		row.add(l, java.awt.BorderLayout.WEST);
		row.add(v, java.awt.BorderLayout.EAST);
		return row;
	}

	public static EmptyBorder contentBorder()
	{
		return new EmptyBorder(6, PluginPanel.BORDER_OFFSET, 6, PluginPanel.BORDER_OFFSET);
	}
}
