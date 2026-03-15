package com.grandflipout.ui;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * First-run onboarding card shown in the Overview tab when no API key is configured.
 * Guides the user through signup, key creation, and plugin config.
 */
public final class OnboardingPanel
{
	private OnboardingPanel() { }

	public static JPanel create(String apiBaseUrl)
	{
		JPanel card = GrandFlipOutStyles.createCard();

		JLabel title = GrandFlipOutStyles.createLabel("Welcome to Grand Flip Out", ColorScheme.BRAND_ORANGE);
		title.setFont(FontManager.getRunescapeBoldFont());
		card.add(title);
		card.add(Box.createVerticalStrut(8));

		addStep(card, "1", "Create an account", "Head to " + siteUrl(apiBaseUrl) + " and sign up with your email.");
		addStep(card, "2", "Get your API key", "Log in, open your dashboard, and click \"Create new API key\". Copy it—you won't see it again.");
		addStep(card, "3", "Plug it into RuneLite", "Configuration (wrench) → Grand Flip Out API. Paste your key and set the Server URL.");
		addStep(card, "4", "You're set", "Turn on API polling in Grand Flip Out config if you want live data. The Live Market tab will show prices and opportunities.");

		card.add(Box.createVerticalStrut(8));
		JLabel note = GrandFlipOutStyles.createSmallLabel("Your trades and flip logs stay on your PC. We never see them unless you export.", ColorScheme.MEDIUM_GRAY_COLOR);
		card.add(note);

		return card;
	}

	private static void addStep(JPanel parent, String number, String heading, String detail)
	{
		parent.add(Box.createVerticalStrut(6));
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(0, 0, 0, 0));

		JLabel h = GrandFlipOutStyles.createLabel(number + ". " + heading, ColorScheme.LIGHT_GRAY_COLOR);
		h.setFont(FontManager.getRunescapeBoldFont());
		row.add(h);

		JLabel d = GrandFlipOutStyles.createSmallLabel("   " + detail, ColorScheme.MEDIUM_GRAY_COLOR);
		row.add(d);

		parent.add(row);
	}

	private static String siteUrl(String baseUrl)
	{
		if (baseUrl == null || baseUrl.isBlank())
		{
			return "grandflipout.com";
		}
		return baseUrl.replaceAll("/$", "");
	}
}
