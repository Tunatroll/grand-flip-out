package com.fliphelper.ui;

import com.fliphelper.api.PeerNetwork;
import com.fliphelper.api.ProfileClient;
import com.fliphelper.api.ProfileClient.AccountTier;
import com.fliphelper.api.ProfileClient.Feature;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.QuantityFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Locale;


public class ProfilePanel extends JPanel
{
    private final ProfileClient profileClient;
    private final PeerNetwork peerNetwork;

    // Card layout to swap between login and dashboard views
    private final CardLayout cardLayout;
    private static final String CARD_LOGIN = "login";
    private static final String CARD_DASHBOARD = "dashboard";

    // Login view components
    private JTextField loginKeyField;
    private JTextField createNameField;
    private JLabel loginStatusLabel;
    private JButton loginBtn;
    private JButton createBtn;

    // Dashboard view components
    private JLabel nameLabel;
    private JLabel tierBadge;
    private JLabel profitLabel;
    private JLabel flipsLabel;
    private JLabel streakLabel;
    private JLabel taxLabel;
    private JPanel charactersPanel;
    private JPanel peerStatusPanel;

    public ProfilePanel(ProfileClient profileClient, PeerNetwork peerNetwork)
    {
        this.profileClient = profileClient;
        this.peerNetwork = peerNetwork;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        cardLayout = new CardLayout();
        JPanel cardPanel = new JPanel(cardLayout);
        cardPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        cardPanel.add(buildLoginView(), CARD_LOGIN);
        cardPanel.add(buildDashboardView(), CARD_DASHBOARD);

        add(cardPanel, BorderLayout.CENTER);

        // Show the right card based on login state
        if (profileClient.isLoggedIn())
        {
            cardLayout.show(cardPanel, CARD_DASHBOARD);
            refreshDashboard();
        }
        else
        {
            cardLayout.show(cardPanel, CARD_LOGIN);
        }
    }

    // login view

    private JPanel buildLoginView()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Header
        JLabel header = new JLabel("Grand Flip Out Account");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
        header.setForeground(Color.WHITE);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(header);
        panel.add(Box.createVerticalStrut(15));

        // -- Existing account login --
        JLabel loginLabel = new JLabel("Have an API key? Login:");
        loginLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        loginLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(loginLabel);
        panel.add(Box.createVerticalStrut(5));

        loginKeyField = new JTextField();
        loginKeyField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        loginKeyField.setAlignmentX(Component.LEFT_ALIGNMENT);
        loginKeyField.setToolTipText("Paste your AP API key here");
        panel.add(loginKeyField);
        panel.add(Box.createVerticalStrut(5));

        loginBtn = new JButton("Login");
        loginBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        loginBtn.setToolTipText("Sign in with your existing API key");
        loginBtn.addActionListener(e -> doLogin());
        panel.add(loginBtn);
        panel.add(Box.createVerticalStrut(20));

        // -- Create new account --
        JLabel createLabel = new JLabel("New here? Create a free account:");
        createLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        createLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(createLabel);
        panel.add(Box.createVerticalStrut(5));

        createNameField = new JTextField();
        createNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        createNameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        createNameField.setToolTipText("Choose a display name (max 32 chars)");
        panel.add(createNameField);
        panel.add(Box.createVerticalStrut(5));

        createBtn = new JButton("Create Account");
        createBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        createBtn.setToolTipText("Create a free Grand Flip Out account");
        createBtn.addActionListener(e -> doCreateAccount());
        panel.add(createBtn);
        panel.add(Box.createVerticalStrut(10));

        // Status label for errors/success
        loginStatusLabel = new JLabel(" ");
        loginStatusLabel.setForeground(new Color(255, 100, 100));
        loginStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(loginStatusLabel);

        panel.add(Box.createVerticalStrut(20));

        // Tier info
        JLabel tierInfo = new JLabel("<html><b>Free tier</b> includes basic price lookups and flip tracking.<br>"
            + "Upgrade to <b>Pro</b> for smart suggestions, dump detection, and P&L tracking.<br>"
            + "Go <b>Elite</b> for multi-account, market intelligence, and priority relay access.</html>");
        tierInfo.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        tierInfo.setFont(tierInfo.getFont().deriveFont(11f));
        tierInfo.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(tierInfo);

        return panel;
    }

    private void doLogin()
    {
        String key = loginKeyField.getText().trim();
        if (key.isEmpty())
        {
            loginStatusLabel.setText("Enter your API key");
            loginStatusLabel.setForeground(new Color(255, 100, 100));
            return;
        }

        // Disable buttons to prevent double-clicks
        loginBtn.setEnabled(false);
        createBtn.setEnabled(false);
        loginStatusLabel.setText("Logging in...");
        loginStatusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        // Run on background thread to avoid freezing UI
        new SwingWorker<Boolean, Void>()
        {
            @Override
            protected Boolean doInBackground()
            {
                return profileClient.login(key);
            }

            @Override
            protected void done()
            {
                try
                {
                    if (get())
                    {
                        showDashboard();
                    }
                    else
                    {
                        loginStatusLabel.setText("Invalid API key. Check and try again.");
                        loginStatusLabel.setForeground(new Color(255, 100, 100));
                    }
                }
                catch (Exception e)
                {
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("connect"))
                    {
                        loginStatusLabel.setText("Cannot reach server. Check connection.");
                    }
                    else
                    {
                        loginStatusLabel.setText("Login error: " + msg);
                    }
                    loginStatusLabel.setForeground(new Color(255, 100, 100));
                }
                finally
                {
                    loginBtn.setEnabled(true);
                    createBtn.setEnabled(true);
                }
            }
        }.execute();
    }

    private void doCreateAccount()
    {
        String name = createNameField.getText().trim();
        if (name.isEmpty())
        {
            loginStatusLabel.setText("Enter a display name");
            loginStatusLabel.setForeground(new Color(255, 100, 100));
            return;
        }
        if (name.length() > 32)
        {
            loginStatusLabel.setText("Display name must be 32 chars or less");
            loginStatusLabel.setForeground(new Color(255, 100, 100));
            return;
        }

        // Disable buttons to prevent double-clicks
        loginBtn.setEnabled(false);
        createBtn.setEnabled(false);
        loginStatusLabel.setText("Creating account...");
        loginStatusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        new SwingWorker<String, Void>()
        {
            @Override
            protected String doInBackground()
            {
                return profileClient.createAccount(name);
            }

            @Override
            protected void done()
            {
                try
                {
                    String newKey = get();
                    if (newKey != null)
                    {
                        // Copy key to clipboard
                        Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new StringSelection(newKey), null);

                        // Mask the key in the dialog for security — only show first/last 4 chars
                        String maskedKey = newKey.length() > 8
                            ? newKey.substring(0, 4) + "..." + newKey.substring(newKey.length() - 4)
                            : newKey;

                        JOptionPane.showMessageDialog(
                            ProfilePanel.this,
                            "Account created! Your API key has been copied to clipboard.\n\n"
                            + "Key: " + maskedKey + " (copied)\n\n"
                            + "SAVE THIS KEY — you'll need it to log in on other devices.\n"
                            + "Paste it into Grand Flip Out config > Profile API Key field.",
                            "Account Created",
                            JOptionPane.INFORMATION_MESSAGE
                        );

                        showDashboard();
                    }
                    else
                    {
                        loginStatusLabel.setText("Creation failed. Check server connection.");
                        loginStatusLabel.setForeground(new Color(255, 100, 100));
                    }
                }
                catch (Exception e)
                {
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("connect"))
                    {
                        loginStatusLabel.setText("Cannot reach server. Check connection.");
                    }
                    else
                    {
                        loginStatusLabel.setText("Error: " + msg);
                    }
                    loginStatusLabel.setForeground(new Color(255, 100, 100));
                }
                finally
                {
                    loginBtn.setEnabled(true);
                    createBtn.setEnabled(true);
                }
            }
        }.execute();
    }

    // dashboard view

    private JPanel buildDashboardView()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Profile header row
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        nameLabel = new JLabel("Flipper");
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
        nameLabel.setForeground(Color.WHITE);
        headerRow.add(nameLabel, BorderLayout.WEST);

        tierBadge = new JLabel("FREE");
        tierBadge.setFont(tierBadge.getFont().deriveFont(Font.BOLD, 11f));
        tierBadge.setForeground(new Color(180, 180, 180));
        tierBadge.setOpaque(true);
        tierBadge.setBackground(new Color(60, 60, 60));
        tierBadge.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 80, 80)),
            new EmptyBorder(2, 6, 2, 6)
        ));
        headerRow.add(tierBadge, BorderLayout.EAST);

        panel.add(headerRow);
        panel.add(Box.createVerticalStrut(10));

        // Stats grid
        JPanel statsGrid = new JPanel(new GridLayout(2, 2, 8, 8));
        statsGrid.setBackground(ColorScheme.DARK_GRAY_COLOR);
        statsGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        statsGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

        profitLabel = createStatLabel("Total Profit", "0 gp");
        flipsLabel = createStatLabel("Total Flips", "0");
        streakLabel = createStatLabel("Win Streak", "0");
        taxLabel = createStatLabel("Tax Paid", "0 gp");

        statsGrid.add(wrapStatLabel("Total Profit", profitLabel));
        statsGrid.add(wrapStatLabel("Total Flips", flipsLabel));
        statsGrid.add(wrapStatLabel("Win Streak", streakLabel));
        statsGrid.add(wrapStatLabel("Tax Paid", taxLabel));

        panel.add(statsGrid);
        panel.add(Box.createVerticalStrut(15));

        // Characters section
        JLabel charHeader = new JLabel("Linked Characters");
        charHeader.setFont(charHeader.getFont().deriveFont(Font.BOLD, 12f));
        charHeader.setForeground(Color.WHITE);
        charHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(charHeader);
        panel.add(Box.createVerticalStrut(5));

        charactersPanel = new JPanel();
        charactersPanel.setLayout(new BoxLayout(charactersPanel, BoxLayout.Y_AXIS));
        charactersPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        charactersPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(charactersPanel);
        panel.add(Box.createVerticalStrut(5));

        JButton addCharBtn = new JButton("+ Link Character");
        addCharBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        addCharBtn.addActionListener(e -> doAddCharacter());
        panel.add(addCharBtn);
        panel.add(Box.createVerticalStrut(15));

        // P2P Network status
        JLabel peerHeader = new JLabel("P2P Network");
        peerHeader.setFont(peerHeader.getFont().deriveFont(Font.BOLD, 12f));
        peerHeader.setForeground(Color.WHITE);
        peerHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(peerHeader);
        panel.add(Box.createVerticalStrut(5));

        peerStatusPanel = new JPanel();
        peerStatusPanel.setLayout(new BoxLayout(peerStatusPanel, BoxLayout.Y_AXIS));
        peerStatusPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        peerStatusPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(peerStatusPanel);
        panel.add(Box.createVerticalStrut(15));

        // Logout button
        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoutBtn.addActionListener(e -> {
            profileClient.logout();
            showLogin();
        });
        panel.add(logoutBtn);

        return panel;
    }

    private void showDashboard()
    {
        Container parent = getParent();
        if (parent == null)
        {
            // We're the root, find the card panel
            for (Component c : getComponents())
            {
                if (c instanceof JPanel)
                {
                    cardLayout.show((Container) c, CARD_DASHBOARD);
                    break;
                }
            }
        }
        // Fallback: just search all children
        for (Component c : getComponents())
        {
            if (c instanceof JPanel && ((JPanel) c).getLayout() == cardLayout)
            {
                cardLayout.show((Container) c, CARD_DASHBOARD);
            }
        }
        refreshDashboard();
    }

    private void showLogin()
    {
        for (Component c : getComponents())
        {
            if (c instanceof JPanel && ((JPanel) c).getLayout() == cardLayout)
            {
                cardLayout.show((Container) c, CARD_LOGIN);
            }
        }
    }

    
    public void refreshDashboard()
    {
        if (!profileClient.isLoggedIn())
        {
            return;
        }

        nameLabel.setText(profileClient.getDisplayName());

        // Tier badge coloring
        AccountTier tier = profileClient.getTier();
        tierBadge.setText(tier.displayName.toUpperCase());
        switch (tier)
        {
            case PRO:
                tierBadge.setForeground(new Color(100, 200, 255)); // blue
                tierBadge.setBackground(new Color(20, 50, 80));
                break;
            case ELITE:
                tierBadge.setForeground(new Color(255, 215, 0)); // gold
                tierBadge.setBackground(new Color(60, 50, 20));
                break;
            default:
                tierBadge.setForeground(new Color(180, 180, 180));
                tierBadge.setBackground(new Color(60, 60, 60));
        }

        // Stats
        ProfileClient.ProfileStats stats = profileClient.getStats();
        if (stats != null)
        {
            profitLabel.setText(formatGp(stats.getTotalProfit()));
            profitLabel.setForeground(stats.getTotalProfit() >= 0 ? new Color(0, 200, 83) : new Color(255, 82, 82));
            flipsLabel.setText(String.valueOf(stats.getTotalFlips()));
            streakLabel.setText(String.valueOf(stats.getStreakCurrent()));
            taxLabel.setText(formatGp(stats.getTotalTax()));
        }

        // Characters
        charactersPanel.removeAll();
        for (ProfileClient.CharacterInfo ch : profileClient.getCharacters())
        {
            JLabel charLabel = new JLabel("  " + ch.getRsn());
            charLabel.setForeground(Color.WHITE);
            charLabel.setFont(charLabel.getFont().deriveFont(12f));
            charLabel.setBorder(new EmptyBorder(3, 5, 3, 5));
            charactersPanel.add(charLabel);
        }
        if (profileClient.getCharacters().isEmpty())
        {
            JLabel noChars = new JLabel("  No characters linked yet");
            noChars.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            noChars.setFont(noChars.getFont().deriveFont(Font.ITALIC, 11f));
            noChars.setBorder(new EmptyBorder(3, 5, 3, 5));
            charactersPanel.add(noChars);
        }
        charactersPanel.revalidate();
        charactersPanel.repaint();

        // Peer network status
        peerStatusPanel.removeAll();
        if (peerNetwork != null)
        {
            Collection<PeerNetwork.PeerInfo> allPeers = peerNetwork.getAllPeers();
            int healthy = peerNetwork.getHealthyCount();

            JLabel summaryLabel = new JLabel(String.format("  %d/%d peers online", healthy, allPeers.size()));
            summaryLabel.setForeground(healthy > 0 ? new Color(0, 200, 83) : new Color(255, 82, 82));
            summaryLabel.setFont(summaryLabel.getFont().deriveFont(11f));
            summaryLabel.setBorder(new EmptyBorder(3, 5, 3, 5));
            peerStatusPanel.add(summaryLabel);

            for (PeerNetwork.PeerInfo peer : allPeers)
            {
                String status = peer.isHealthy() ? "●" : "○";
                Color statusColor = peer.isHealthy() ? new Color(0, 200, 83) : new Color(255, 82, 82);
                String url = peer.getBaseUrl().replaceAll("https?://", "");

                JLabel peerLabel = new JLabel(String.format("  %s %s (score: %d)", status, url, peer.getScore()));
                peerLabel.setForeground(statusColor);
                peerLabel.setFont(peerLabel.getFont().deriveFont(10f));
                peerLabel.setBorder(new EmptyBorder(1, 10, 1, 5));
                peerStatusPanel.add(peerLabel);
            }
        }
        peerStatusPanel.revalidate();
        peerStatusPanel.repaint();

        revalidate();
        repaint();
    }

    private void doAddCharacter()
    {
        if (!profileClient.isLoggedIn())
        {
            return;
        }
        if (!profileClient.hasFeature(Feature.CHARACTER_LINKING))
        {
            JOptionPane.showMessageDialog(this,
                profileClient.getUpgradeMessage(Feature.CHARACTER_LINKING),
                "Feature Locked", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String rsn = JOptionPane.showInputDialog(this,
            "Enter your RuneScape character name (RSN):",
            "Link Character", JOptionPane.PLAIN_MESSAGE);

        if (rsn != null && !rsn.trim().isEmpty())
        {
            new SwingWorker<Boolean, Void>()
            {
                @Override
                protected Boolean doInBackground()
                {
                    return profileClient.addCharacter(rsn.trim());
                }

                @Override
                protected void done()
                {
                    try
                    {
                        if (get())
                        {
                            refreshDashboard();
                        }
                        else
                        {
                            JOptionPane.showMessageDialog(ProfilePanel.this,
                                "Failed to link character. It may already be linked to another profile.",
                                "Link Failed", JOptionPane.WARNING_MESSAGE);
                        }
                    }
                    catch (Exception e)
                    {
                        // ignore
                    }
                }
            }.execute();
        }
    }

    // helpers

    private JLabel createStatLabel(String title, String value)
    {
        JLabel label = new JLabel(value);
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        return label;
    }

    private JPanel wrapStatLabel(String title, JLabel valueLabel)
    {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        wrapper.setBorder(new EmptyBorder(5, 8, 5, 8));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        titleLabel.setFont(titleLabel.getFont().deriveFont(10f));
        wrapper.add(titleLabel);
        wrapper.add(valueLabel);

        return wrapper;
    }

    private String formatGp(long amount)
    {
        if (Math.abs(amount) >= 1_000_000_000)
        {
            return String.format("%.1fb gp", amount / 1_000_000_000.0);
        }
        else if (Math.abs(amount) >= 1_000_000)
        {
            return String.format("%.1fm gp", amount / 1_000_000.0);
        }
        else if (Math.abs(amount) >= 1_000)
        {
            return String.format("%.1fk gp", amount / 1_000.0);
        }
        return amount + " gp";
    }
}
