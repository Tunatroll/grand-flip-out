package com.fliphelper.ui;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.api.DeviceLinkService;
import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

public class GuidePanel extends JPanel {

    private static String hex(Color c)
    {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private GrandFlipOutConfig config;
    private DeviceLinkService linkService;
    private Consumer<String> keyWriter;

    private JPanel accountBox;
    private JLabel accountStatus;
    private JLabel codeLabel;
    private JButton linkButton;

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
            + "Use this plugin alongside the <b>Intelligence Dashboard</b> on the website for maximum profit.<br><br>"

            + "<h3 style='color: white; margin-bottom: 2px;'>⚡ Smart Hotkeys</h3>"
            + "<ul style='margin-top: 2px; padding-left: 15px; margin-bottom: 8px;'>"
            + "<li><b>Ctrl+Shift+L:</b> Quick item lookup. Pops open the search and prefills the item under your cursor.</li>"
            + "<li><b>Ctrl+Shift+O:</b> Toggle in-game overlay.</li>"
            + "</ul>"

            + "<h3 style='color: white; margin-bottom: 2px;'>🖱️ GE Price Fill</h3>"
            + "The Advisor tab provides a <b>Fill offer</b> button. With the Grand Exchange open, it prefills the "
            + "suggested price into the offer setup — you always review and press Confirm yourself. "
            + "The <b>Price-Fill Hotkey (Ctrl+Shift+P)</b> does the same for the item you're viewing. "
            + "Both are off by default — enable them in the plugin settings.<br><br>"

            + "<b>Note:</b> You can customize all hotkeys in the RuneLite Plugin Settings (click the gear icon).<br><br>"
            + "<hr style='border: 1px solid #222;'>"
            + "<center>grandflipout.com</center>"
            + "</body></html>");

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setBorder(null);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

        add(buildAccountBox(), BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * The device-code link section (#177 Lane 3 — kills the API-key paste):
     * press the button, get a short code, enter it at grandflipout.com/link,
     * and the key lands in the plugin config automatically. Armed by the
     * plugin via enableDeviceLink(); renders a settings hint until then.
     */
    private JPanel buildAccountBox()
    {
        accountBox = new JPanel(new GridLayout(0, 1, 0, 4));
        accountBox.setBackground(GfoPalette.CARD);
        accountBox.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(GfoPalette.BORDER),
            new EmptyBorder(8, 10, 10, 10)));

        JLabel title = new JLabel("Account");
        title.setForeground(GfoPalette.ACCENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD));

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

        accountBox.add(title);
        accountBox.add(accountStatus);
        accountBox.add(codeLabel);
        accountBox.add(linkButton);
        return accountBox;
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
    }

    private void onLinkPressed()
    {
        if (config == null || linkService == null)
        {
            return;
        }
        if (!config.enableServerFunctionality())
        {
            accountStatus.setText("Enable \"server functionality\" in the plugin settings first");
            accountStatus.setForeground(GfoPalette.DOWN);
            return;
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
