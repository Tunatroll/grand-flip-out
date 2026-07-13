package com.fliphelper.ui;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.api.DeviceLinkService;
import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

public class GuidePanel extends JPanel {

    /**
     * KEEP IN LOCKSTEP with the enableServerFunctionality @ConfigItem warning in
     * GrandFlipOutConfig — EVERY in-panel enable path (this Guide step AND the
     * Advisor first-run teaser) must present the exact same disclosure the RuneLite
     * config panel shows, so consent is equivalent whichever surface the player uses.
     * Package-visible for that reason — new enable surfaces reuse THIS string.
     */
    static final String SERVER_DISCLOSURE =
        "This plugin submits your IP address to a 3rd party website not controlled "
        + "or verified by the RuneLite Developers.\n\n"
        + "When enabled, your Grand Exchange offer and trade data (item, price, "
        + "quantity, flip timings, and approximate coins) are sent to grandflipout.com. "
        + "If you link an account, your starred watchlist items sync to it (both directions).\n\n"
        + "Enable grandflipout.com features?";

    /** Mirrors the contributeTrades @ConfigItem description — same lockstep rule. */
    private static final String CONTRIBUTE_DISCLOSURE =
        "Share your completed GE trades (item, price, quantity, buy/sell) and "
        + "completed-flip outcomes (paired buy/sell prices with placed-to-filled "
        + "timings) with grandflipout.com to improve crowdsourced flip data.\n\n"
        + "No account identity is sent. You can turn this off at any time.\n\n"
        + "Contribute your flips?";

    private static String hex(Color c)
    {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private GrandFlipOutConfig config;
    private DeviceLinkService linkService;
    private Consumer<String> keyWriter;
    private Runnable enableServerAction;
    private Runnable contributeToggleAction;

    private JLabel serverStatus;
    private JButton enableServerButton;
    private JPanel accountBox;
    private JLabel accountStatus;
    private JLabel codeLabel;
    private JButton linkButton;
    private JLabel contributeStatus;
    private JButton contributeButton;

    public GuidePanel() {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JTextPane textPane = new JTextPane();
        textPane.setContentType("text/html");
        textPane.setEditable(false);
        textPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        textPane.setOpaque(false);
        String accent = hex(GfoPalette.ACCENT);
        textPane.setText("<html><body style='color: #b2b2b2; font-family: Tahoma, sans-serif; font-size: 13px; margin: 0; padding: 0;'>"
            + "<h2 style='color: " + accent + "; font-size: 15px; margin-bottom: 5px; margin-top: 0;'>Welcome to Grand Flip Out</h2>"
            + "Out of the box the plugin tracks your flips and prices using OSRS Wiki data — "
            + "nothing leaves your machine.<br><br>"

            + "<h3 style='color: white; margin-bottom: 2px;'>🚀 Get started (three clicks)</h3>"
            + "<ol style='margin-top: 2px; padding-left: 15px; margin-bottom: 8px;'>"
            + "<li><b>Enable grandflipout.com features</b> (button above) — unlocks the Advisor, "
            + "live dump/signal intelligence, and account features.</li>"
            + "<li><b>Link account</b> — free; a short code, no key-pasting.</li>"
            + "<li><b>Contribute your flips</b> (optional) — anonymous crowdsourced data that "
            + "makes fill-time and recovery predictions better for you.</li>"
            + "</ol>"

            + "<h3 style='color: white; margin-bottom: 2px;'>🖱️ GE Price Fill</h3>"
            + "The Advisor tab's <b>Fill offer</b> button prefills the suggested price into an open "
            + "Grand Exchange offer — you always review and press Confirm yourself. Off by default; "
            + "turn on \"GE offer auto-fill\" in the Advisor settings section.<br><br>"

            + "<h3 style='color: white; margin-bottom: 2px;'>⌨️ Hotkeys</h3>"
            + "Every action lives in the panel, so hotkeys are <b>optional and unbound by default</b>. "
            + "If you want them, bind panel toggle / quick lookup / price-fill in the Hotkeys "
            + "settings section.<br><br>"
            + "<hr style='border: 1px solid #222;'>"
            + "<center>grandflipout.com</center>"
            + "</body></html>");

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setBorder(null);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel northStack = new JPanel();
        northStack.setLayout(new BoxLayout(northStack, BoxLayout.Y_AXIS));
        northStack.setOpaque(false);
        northStack.add(buildServerBox());
        northStack.add(Box.createVerticalStrut(6));
        northStack.add(buildAccountBox());
        northStack.add(Box.createVerticalStrut(6));
        northStack.add(buildContributeBox());
        add(northStack, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel cardBox()
    {
        JPanel box = new JPanel(new GridLayout(0, 1, 0, 4));
        box.setBackground(GfoPalette.CARD);
        box.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(GfoPalette.BORDER),
            new EmptyBorder(8, 10, 10, 10)));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        return box;
    }

    private static JLabel cardTitle(String text)
    {
        JLabel title = new JLabel(text);
        title.setForeground(GfoPalette.ACCENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        return title;
    }

    /**
     * Step 1 — the master switch, surfaced where users actually look instead of
     * four levels deep in settings. The button shows the SAME disclosure the
     * config panel's warning shows and only flips the config on an explicit
     * accept; the player can equally use the settings toggle.
     */
    private JPanel buildServerBox()
    {
        JPanel box = cardBox();
        serverStatus = new JLabel("grandflipout.com features: off");
        serverStatus.setForeground(Color.LIGHT_GRAY);

        enableServerButton = new JButton("Enable grandflipout.com features…");
        enableServerButton.setFocusPainted(false);
        enableServerButton.addActionListener(e -> onEnableServerPressed());
        enableServerButton.setEnabled(false);

        box.add(cardTitle("Get started"));
        box.add(serverStatus);
        box.add(enableServerButton);
        return box;
    }

    private JPanel buildAccountBox()
    {
        accountBox = cardBox();

        accountStatus = new JLabel("Not linked");
        accountStatus.setForeground(Color.LIGHT_GRAY);

        codeLabel = new JLabel(" ");
        codeLabel.setForeground(GfoPalette.ACCENT_SOFT);
        codeLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
        codeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        codeLabel.setVisible(false);

        linkButton = new JButton("Link account");
        linkButton.setFocusPainted(false);
        linkButton.addActionListener(e -> onLinkPressed());
        linkButton.setEnabled(false);

        accountBox.add(cardTitle("Account"));
        accountBox.add(accountStatus);
        accountBox.add(codeLabel);
        accountBox.add(linkButton);
        return accountBox;
    }

    /** Step 3 — the crowdsourced-data opt-in, consent-gated, one click. */
    private JPanel buildContributeBox()
    {
        JPanel box = cardBox();
        contributeStatus = new JLabel("Contribute flips: off");
        contributeStatus.setForeground(Color.LIGHT_GRAY);

        contributeButton = new JButton("Contribute your flips…");
        contributeButton.setFocusPainted(false);
        contributeButton.addActionListener(e -> onContributePressed());
        contributeButton.setEnabled(false);

        box.add(cardTitle("Crowdsourced data"));
        box.add(contributeStatus);
        box.add(contributeButton);
        return box;
    }

    /** Called by the plugin at startup with the live wiring. */
    public void enableDeviceLink(GrandFlipOutConfig config, DeviceLinkService linkService,
                                 Consumer<String> keyWriter)
    {
        this.config = config;
        this.linkService = linkService;
        this.keyWriter = keyWriter;
        linkButton.setEnabled(true);
        refreshAccountState();
    }

    /** Called by the plugin at startup: the config-flipping actions for steps 1 and 3. */
    public void enableServerControls(Runnable enableServerAction, Runnable contributeToggleAction)
    {
        this.enableServerAction = enableServerAction;
        this.contributeToggleAction = contributeToggleAction;
        enableServerButton.setEnabled(true);
        contributeButton.setEnabled(true);
        refreshServerState();
    }

    /** Reflect the master-switch + contribute config state (plugin calls on ConfigChanged too). */
    public void refreshServerState()
    {
        if (config == null)
        {
            return;
        }
        boolean on = config.enableServerFunctionality();
        serverStatus.setText(on
            ? "grandflipout.com features: enabled ✓"
            : "Off — Advisor, live intelligence and account features are dormant");
        serverStatus.setForeground(on ? GfoPalette.UP : Color.LIGHT_GRAY);
        enableServerButton.setVisible(!on);

        boolean contributing = config.contributeTrades();
        contributeStatus.setText(contributing
            ? "Contribute flips: on ✓ — thank you!"
            : "Off — your flips stay local");
        contributeStatus.setForeground(contributing ? GfoPalette.UP : Color.LIGHT_GRAY);
        contributeButton.setText(contributing ? "Stop contributing" : "Contribute your flips…");
        contributeButton.setEnabled(contributeToggleAction != null && (on || contributing));
        revalidate();
        repaint();
    }

    /** Reflect the current config key state (also called after a link lands). */
    public void refreshAccountState()
    {
        if (config == null)
        {
            return;
        }
        boolean linked = config.apiKey() != null && !config.apiKey().trim().isEmpty();
        accountStatus.setText(linked
            ? "Linked ✓ — dump alerts & Pro features follow your account"
            : "Not linked — connect your grandflipout.com account");
        accountStatus.setForeground(linked ? GfoPalette.UP : Color.LIGHT_GRAY);
        linkButton.setText(linked ? "Re-link account" : "Link account");
        refreshServerState();
    }

    /** The consent dialog for step 1. True only on an explicit accept. */
    private boolean confirmEnableServer()
    {
        int choice = JOptionPane.showConfirmDialog(this, SERVER_DISCLOSURE,
            "Enable grandflipout.com features", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.OK_OPTION;
    }

    private void onEnableServerPressed()
    {
        if (config == null || enableServerAction == null || config.enableServerFunctionality())
        {
            return;
        }
        if (confirmEnableServer())
        {
            enableServerAction.run();
            refreshServerState();
        }
    }

    private void onContributePressed()
    {
        if (config == null || contributeToggleAction == null)
        {
            return;
        }
        if (!config.contributeTrades())
        {
            // Turning ON needs the master switch + the contribute consent.
            if (!config.enableServerFunctionality())
            {
                if (enableServerAction == null || !confirmEnableServer())
                {
                    return;
                }
                enableServerAction.run();
            }
            int choice = JOptionPane.showConfirmDialog(this, CONTRIBUTE_DISCLOSURE,
                "Contribute your flips", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            if (choice != JOptionPane.OK_OPTION)
            {
                refreshServerState();
                return;
            }
        }
        // Turning OFF needs no ceremony.
        contributeToggleAction.run();
        refreshServerState();
    }

    private void onLinkPressed()
    {
        if (config == null || linkService == null)
        {
            return;
        }
        if (!config.enableServerFunctionality())
        {
            // No dead-end "go find it in settings" — offer the same consent right here.
            if (enableServerAction == null || !confirmEnableServer())
            {
                accountStatus.setText("Linking needs grandflipout.com features enabled");
                accountStatus.setForeground(GfoPalette.DOWN);
                return;
            }
            enableServerAction.run();
            refreshServerState();
        }
        linkButton.setEnabled(false);
        codeLabel.setVisible(false);
        accountStatus.setText("Getting a code…");
        accountStatus.setForeground(Color.LIGHT_GRAY);
        linkService.startLink(new DeviceLinkService.Listener()
        {
            @Override
            public void onCode(String userCode, String verificationUri, long expiresAt)
            {
                SwingUtilities.invokeLater(() ->
                {
                    codeLabel.setText(userCode);
                    codeLabel.setVisible(true);
                    accountStatus.setText("Enter this code at grandflipout.com/link (15 min)");
                    linkButton.setEnabled(true);
                    linkButton.setText("Waiting… (press to restart)");
                });
            }

            @Override
            public void onLinked(String apiKey, String displayName)
            {
                if (keyWriter != null)
                {
                    keyWriter.accept(apiKey);
                }
                SwingUtilities.invokeLater(() ->
                {
                    codeLabel.setVisible(false);
                    accountStatus.setText(displayName != null
                        ? "Linked to " + displayName + " ✓"
                        : "Linked ✓");
                    accountStatus.setForeground(GfoPalette.UP);
                    linkButton.setEnabled(true);
                    linkButton.setText("Re-link account");
                });
            }

            @Override
            public void onFailed(String humanMessage)
            {
                SwingUtilities.invokeLater(() ->
                {
                    codeLabel.setVisible(false);
                    accountStatus.setText(humanMessage);
                    accountStatus.setForeground(GfoPalette.DOWN);
                    linkButton.setEnabled(true);
                    linkButton.setText("Link account");
                });
            }
        });
    }
}
