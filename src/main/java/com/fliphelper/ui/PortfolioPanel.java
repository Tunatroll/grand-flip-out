package com.fliphelper.ui;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.tracker.FlipSuggestionEngine;
import com.fliphelper.tracker.SmartAdvisor;
import com.fliphelper.tracker.SlotOptimizer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.QuantityFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class PortfolioPanel extends JPanel
{
    private static final int GE_SLOTS = 8;

    private final GrandFlipOutConfig config;
    private final PriceService priceService;
    private final SmartAdvisor smartAdvisor;
    private final SlotOptimizer slotOptimizer;
    private final FlipSuggestionEngine suggestionEngine;

    private final SlotCard[] slotCards = new SlotCard[GE_SLOTS];
    private JTextField capitalField;
    private JLabel totalExpectedLabel;
    private JLabel diversityLabel;
    private JLabel riskLabel;
    private JButton generateBtn;
    private JComboBox<String> strategyBox;

    private long userCapital = 5_000_000;
    private List<PortfolioEntry> currentPortfolio = new ArrayList<>();

    public PortfolioPanel(GrandFlipOutConfig config, PriceService priceService,
                         SmartAdvisor smartAdvisor, SlotOptimizer slotOptimizer,
                         FlipSuggestionEngine suggestionEngine)
    {
        this.config = config;
        this.priceService = priceService;
        this.smartAdvisor = smartAdvisor;
        this.slotOptimizer = slotOptimizer;
        this.suggestionEngine = suggestionEngine;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        buildUI();
    }

    private void buildUI()
    {
        // Top controls
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
        controls.setBorder(new EmptyBorder(8, 8, 4, 8));

        // Capital input row
        JPanel capRow = new JPanel(new BorderLayout(6, 0));
        capRow.setOpaque(false);
        capRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JLabel capLabel = new JLabel("Capital:");
        capLabel.setForeground(new Color(0xA0, 0xA0, 0xC0));
        capLabel.setFont(capLabel.getFont().deriveFont(11f));
        capRow.add(capLabel, BorderLayout.WEST);

        capitalField = new JTextField("5m");
        capitalField.setBackground(new Color(0x1A, 0x1A, 0x2E));
        capitalField.setForeground(new Color(0xFF, 0xB8, 0x00));
        capitalField.setCaretColor(new Color(0xFF, 0xB8, 0x00));
        capitalField.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(0x2A, 0x2A, 0x45)),
            new EmptyBorder(4, 6, 4, 6)));
        capitalField.addActionListener(e -> regeneratePortfolio());
        capRow.add(capitalField, BorderLayout.CENTER);

        controls.add(capRow);
        controls.add(Box.createVerticalStrut(6));

        // Strategy + generate row
        JPanel actionRow = new JPanel(new BorderLayout(6, 0));
        actionRow.setOpaque(false);
        actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        strategyBox = new JComboBox<>(new String[]{
            "Balanced", "Aggressive", "Safe", "Volume Hunter", "Margin Hunter"
        });
        strategyBox.setBackground(new Color(0x1A, 0x1A, 0x2E));
        strategyBox.setForeground(Color.WHITE);
        actionRow.add(strategyBox, BorderLayout.CENTER);

        generateBtn = new JButton("Generate");
        generateBtn.setBackground(new Color(0x00, 0x7A, 0x33));
        generateBtn.setForeground(Color.WHITE);
        generateBtn.setFocusPainted(false);
        generateBtn.setBorderPainted(false);
        generateBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        generateBtn.addActionListener(e -> regeneratePortfolio());
        actionRow.add(generateBtn, BorderLayout.EAST);

        controls.add(actionRow);
        controls.add(Box.createVerticalStrut(8));

        // Summary stats bar
        JPanel summaryBar = new JPanel(new GridLayout(1, 3, 4, 0));
        summaryBar.setOpaque(false);
        summaryBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        totalExpectedLabel = makeSummaryCard("EST. PROFIT", "—", new Color(0x00, 0xD2, 0x6A));
        diversityLabel = makeSummaryCard("DIVERSITY", "—", new Color(0x3B, 0x82, 0xF6));
        riskLabel = makeSummaryCard("RISK", "—", new Color(0xFF, 0xB8, 0x00));

        summaryBar.add(totalExpectedLabel.getParent());
        summaryBar.add(diversityLabel.getParent());
        summaryBar.add(riskLabel.getParent());

        controls.add(summaryBar);

        add(controls, BorderLayout.NORTH);

        // Slot grid (2 columns x 4 rows, matching GE layout)
        JPanel grid = new JPanel(new GridLayout(4, 2, 6, 6));
        grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
        grid.setBorder(new EmptyBorder(6, 8, 8, 8));

        for (int i = 0; i < GE_SLOTS; i++)
        {
            slotCards[i] = new SlotCard(i);
            grid.add(slotCards[i]);
        }

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
    }

    private JLabel makeSummaryCard(String title, String value, Color valueColor)
    {
        JPanel card = new JPanel(new BorderLayout(0, 1));
        card.setBackground(new Color(0x1A, 0x1A, 0x2E));
        card.setBorder(new EmptyBorder(4, 6, 4, 6));

        JLabel t = new JLabel(title);
        t.setForeground(new Color(0x60, 0x60, 0x80));
        t.setFont(t.getFont().deriveFont(Font.BOLD, 7f));
        card.add(t, BorderLayout.NORTH);

        JLabel v = new JLabel(value);
        v.setForeground(valueColor);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 11f));
        card.add(v, BorderLayout.CENTER);

        return v;
    }

    public void regeneratePortfolio()
    {
        userCapital = parseCapital(capitalField.getText());
        String strategy = (String) strategyBox.getSelectedItem();

        // run off EDT
        new Thread(() -> {
            List<PortfolioEntry> entries = buildPortfolio(strategy);
            SwingUtilities.invokeLater(() -> applyPortfolio(entries));
        }).start();
    }

    private List<PortfolioEntry> buildPortfolio(String strategy)
    {
        List<PortfolioEntry> entries = new ArrayList<>();

        List<FlipSuggestionEngine.FlipSuggestion> suggestions =
            suggestionEngine.generateSuggestions();
        if (suggestions == null || suggestions.isEmpty())
        {
            return entries;
        }

        // Score and rank by strategy
        List<ScoredItem> scored = new ArrayList<>();
        for (FlipSuggestionEngine.FlipSuggestion s : suggestions)
        {
            if (s.getCapitalRequired() <= 0) continue;
            double margin = s.getMarginPercent();
            double vol = s.getVolume1h();
            double profitEff = (double) s.getProfitPerLimit() / Math.max(1, s.getCapitalRequired());

            double score;
            switch (strategy)
            {
                case "Aggressive":
                    score = profitEff * 0.5 + margin * 0.4 + (vol > 50 ? 0.1 : 0);
                    break;
                case "Safe":
                    score = (vol > 100 ? 0.5 : vol / 200.0) + (margin > 2 ? 0.3 : 0) + profitEff * 0.2;
                    break;
                case "Volume Hunter":
                    score = vol * 0.6 + profitEff * 0.3 + (margin > 1 ? 0.1 : 0);
                    break;
                case "Margin Hunter":
                    score = margin * 0.6 + profitEff * 0.3 + (vol > 30 ? 0.1 : 0);
                    break;
                default: // Balanced
                    score = profitEff * 0.35 + (vol / 200.0) * 0.35 + margin * 0.2 + (margin > 3 ? 0.1 : 0);
                    break;
            }

            scored.add(new ScoredItem(s, score));
        }

        scored.sort((a, b) -> Double.compare(b.score, a.score));

        // Category diversification — don't stack same type
        Set<String> usedCategories = new HashSet<>();
        long remaining = userCapital;
        int slot = 0;

        for (ScoredItem item : scored)
        {
            if (slot >= GE_SLOTS || remaining <= 0) break;

            FlipSuggestionEngine.FlipSuggestion s = item.suggestion;
            String cat = categorize(s.getItemName());

            // In Balanced/Safe, cap 2 items per category
            if (("Balanced".equals(strategy) || "Safe".equals(strategy))
                && Collections.frequency(new ArrayList<>(usedCategories), cat) >= 2)
            {
                continue;
            }

            long needed = s.getCapitalRequired();
            if (needed > remaining) continue;

            // SmartAdvisor signal if available
            String signal = "—";
            String confidence = "—";
            if (smartAdvisor != null)
            {
                try
                {
                    SmartAdvisor.SmartPick pick = smartAdvisor.analyze(s.getItemId());
                    if (pick != null)
                    {
                        signal = pick.getAction().getLabel();
                        confidence = pick.getConfidence().name();
                    }
                }
                catch (Exception ignored) {}
            }

            entries.add(new PortfolioEntry(
                slot, s.getItemId(), s.getItemName(), cat,
                s.getBuyPrice(), s.getSellPrice(),
                s.getProfitPerLimit(), needed,
                s.getMarginPercent(), s.getVolume1h(),
                signal, confidence
            ));

            usedCategories.add(cat);
            remaining -= needed;
            slot++;
        }

        return entries;
    }

    private void applyPortfolio(List<PortfolioEntry> entries)
    {
        currentPortfolio = entries;

        // Clear all slots first
        for (int i = 0; i < GE_SLOTS; i++)
        {
            slotCards[i].clear();
        }

        long totalProfit = 0;
        Set<String> cats = new HashSet<>();

        for (PortfolioEntry e : entries)
        {
            slotCards[e.slot].fill(e);
            totalProfit += e.expectedProfit;
            cats.add(e.category);
        }

        totalExpectedLabel.setText(QuantityFormatter.quantityToRSDecimalStack((int) totalProfit, true) + " gp");
        diversityLabel.setText(cats.size() + " types");

        int avgRisk = entries.isEmpty() ? 0 :
            (int) entries.stream().mapToDouble(e -> e.marginPct < 2 ? 3 : e.marginPct < 5 ? 2 : 1).average().orElse(0);
        String riskText = avgRisk >= 3 ? "HIGH" : avgRisk >= 2 ? "MED" : "LOW";
        riskLabel.setText(riskText);
        riskLabel.setForeground(avgRisk >= 3 ? new Color(0xF4, 0x43, 0x36) :
                               avgRisk >= 2 ? new Color(0xFF, 0xB8, 0x00) :
                               new Color(0x00, 0xD2, 0x6A));
    }

    private String categorize(String name)
    {
        if (name == null) return "Other";
        String lower = name.toLowerCase();
        if (lower.contains("rune") || lower.contains("adamant") || lower.contains("dragon") ||
            lower.contains("platebody") || lower.contains("chainbody") || lower.contains("helm"))
            return "Equipment";
        if (lower.contains("potion") || lower.contains("brew") || lower.contains("restore") ||
            lower.contains("food") || lower.contains("shark") || lower.contains("karambwan"))
            return "Consumable";
        if (lower.contains("ore") || lower.contains("bar") || lower.contains("log") ||
            lower.contains("herb") || lower.contains("seed") || lower.contains("bone"))
            return "Resource";
        if (lower.contains("nature") || lower.contains("death") || lower.contains("blood") ||
            lower.contains("cosmic") || lower.contains("law"))
            return "Rune";
        return "Other";
    }

    private long parseCapital(String text)
    {
        text = text.trim().toLowerCase().replace(",", "").replace(" ", "");
        try
        {
            if (text.endsWith("b")) return (long) (Double.parseDouble(text.substring(0, text.length() - 1)) * 1_000_000_000);
            if (text.endsWith("m")) return (long) (Double.parseDouble(text.substring(0, text.length() - 1)) * 1_000_000);
            if (text.endsWith("k")) return (long) (Double.parseDouble(text.substring(0, text.length() - 1)) * 1_000);
            return Long.parseLong(text);
        }
        catch (NumberFormatException e)
        {
            return 5_000_000;
        }
    }

    public List<PortfolioEntry> getCurrentPortfolio()
    {
        return Collections.unmodifiableList(currentPortfolio);
    }

    // Inner classes

    private static class ScoredItem
    {
        final FlipSuggestionEngine.FlipSuggestion suggestion;
        final double score;

        ScoredItem(FlipSuggestionEngine.FlipSuggestion suggestion, double score)
        {
            this.suggestion = suggestion;
            this.score = score;
        }
    }

    public static class PortfolioEntry
    {
        public final int slot;
        public final int itemId;
        public final String itemName;
        public final String category;
        public final long buyPrice;
        public final long sellPrice;
        public final long expectedProfit;
        public final long capitalNeeded;
        public final double marginPct;
        public final double volPerHr;
        public final String signal;
        public final String confidence;

        PortfolioEntry(int slot, int itemId, String itemName, String category,
                      long buyPrice, long sellPrice, long expectedProfit, long capitalNeeded,
                      double marginPct, double volPerHr, String signal, String confidence)
        {
            this.slot = slot;
            this.itemId = itemId;
            this.itemName = itemName;
            this.category = category;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.expectedProfit = expectedProfit;
            this.capitalNeeded = capitalNeeded;
            this.marginPct = marginPct;
            this.volPerHr = volPerHr;
            this.signal = signal;
            this.confidence = confidence;
        }
    }

    // Visual card for each GE slot
    private class SlotCard extends JPanel
    {
        private final int slotNum;
        private final JLabel nameLabel;
        private final JLabel priceLabel;
        private final JLabel profitLabel;
        private final JLabel signalLabel;
        private final JLabel catLabel;
        private final JPanel contentPanel;

        SlotCard(int slotNum)
        {
            this.slotNum = slotNum;
            setLayout(new BorderLayout());
            setBackground(new Color(0x1A, 0x1A, 0x2E));
            setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(0x2A, 0x2A, 0x45), 1, true),
                new EmptyBorder(6, 8, 6, 8)));
            setPreferredSize(new Dimension(0, 72));

            // Slot number badge
            JLabel badge = new JLabel(String.valueOf(slotNum + 1));
            badge.setForeground(new Color(0x40, 0x40, 0x60));
            badge.setFont(badge.getFont().deriveFont(Font.BOLD, 9f));
            badge.setBorder(new EmptyBorder(0, 0, 0, 4));
            add(badge, BorderLayout.WEST);

            contentPanel = new JPanel();
            contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
            contentPanel.setOpaque(false);

            nameLabel = new JLabel("Empty");
            nameLabel.setForeground(new Color(0x50, 0x50, 0x70));
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 11f));
            nameLabel.setAlignmentX(LEFT_ALIGNMENT);
            contentPanel.add(nameLabel);

            JPanel midRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            midRow.setOpaque(false);
            midRow.setAlignmentX(LEFT_ALIGNMENT);

            priceLabel = new JLabel("");
            priceLabel.setForeground(new Color(0x80, 0x80, 0xA0));
            priceLabel.setFont(priceLabel.getFont().deriveFont(10f));
            midRow.add(priceLabel);

            catLabel = new JLabel("");
            catLabel.setFont(catLabel.getFont().deriveFont(Font.ITALIC, 9f));
            catLabel.setForeground(new Color(0x50, 0x50, 0x70));
            midRow.add(catLabel);
            contentPanel.add(midRow);

            JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            bottomRow.setOpaque(false);
            bottomRow.setAlignmentX(LEFT_ALIGNMENT);

            profitLabel = new JLabel("");
            profitLabel.setForeground(new Color(0x00, 0xD2, 0x6A));
            profitLabel.setFont(profitLabel.getFont().deriveFont(Font.BOLD, 10f));
            bottomRow.add(profitLabel);

            signalLabel = new JLabel("");
            signalLabel.setFont(signalLabel.getFont().deriveFont(Font.BOLD, 9f));
            bottomRow.add(signalLabel);
            contentPanel.add(bottomRow);

            add(contentPanel, BorderLayout.CENTER);
        }

        void fill(PortfolioEntry entry)
        {
            nameLabel.setText(entry.itemName);
            nameLabel.setForeground(Color.WHITE);
            priceLabel.setText("Buy: " + QuantityFormatter.quantityToRSDecimalStack((int) entry.buyPrice, true));
            catLabel.setText(entry.category);
            profitLabel.setText("+" + QuantityFormatter.quantityToRSDecimalStack((int) entry.expectedProfit, true) + " gp");

            String sig = entry.signal;
            signalLabel.setText(sig);
            if ("STRONG BUY".equals(sig) || "BUY".equals(sig))
            {
                signalLabel.setForeground(new Color(0x00, 0xD2, 0x6A));
                setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(0x00, 0x7A, 0x33), 1, true),
                    new EmptyBorder(6, 8, 6, 8)));
            }
            else if ("SELL".equals(sig) || "STRONG SELL".equals(sig))
            {
                signalLabel.setForeground(new Color(0xF4, 0x43, 0x36));
                setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(0x7A, 0x00, 0x00), 1, true),
                    new EmptyBorder(6, 8, 6, 8)));
            }
            else
            {
                signalLabel.setForeground(new Color(0x80, 0x80, 0xA0));
                setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(0x2A, 0x2A, 0x45), 1, true),
                    new EmptyBorder(6, 8, 6, 8)));
            }

            repaint();
        }

        void clear()
        {
            nameLabel.setText("Empty");
            nameLabel.setForeground(new Color(0x50, 0x50, 0x70));
            priceLabel.setText("");
            catLabel.setText("");
            profitLabel.setText("");
            signalLabel.setText("");
            setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(0x2A, 0x2A, 0x45), 1, true),
                new EmptyBorder(6, 8, 6, 8)));
            repaint();
        }
    }
}
