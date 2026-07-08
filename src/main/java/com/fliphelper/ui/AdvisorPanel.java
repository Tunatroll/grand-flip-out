/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.ui;

import com.fliphelper.model.DumpFeedEntry;
import com.fliphelper.model.Suggestion;
import net.runelite.client.ui.ColorScheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;

/**
 * The Advisor tab: shows one next-action suggestion at a time (Copilot-style).
 * Pure view — the plugin owns fetching/state and pushes results in via
 * {@link #showSuggestion}/{@link #showMessage}. Skip/Block/Pause are reported back
 * through {@link Listener}.
 */
public class AdvisorPanel extends JPanel
{
    public interface Listener
    {
        void onSkip(int itemId);

        void onBlock(int itemId);

        void onPauseToggled(boolean paused);

        /** Arm the GE price/quantity auto-fill for the suggested flip (user opens the offer to apply). */
        void onFillOffer(int itemId, long price, int quantity);
    }

    private static final Color GOLD = new Color(0xFF, 0x98, 0x1F);
    private static final Color GREEN = new Color(0x00, 0xD2, 0x6A);
    private static final Color RED = new Color(0xFF, 0x47, 0x57);
    private static final Color DIM = new Color(0x9A, 0x9A, 0x9A);

    private final Listener listener;
    private final JPanel content = new JPanel();
    private final JPanel dumpFeed = new JPanel();
    private final JButton pauseBtn = new JButton("Pause");
    private boolean paused;

    public AdvisorPanel(Listener listener)
    {
        this.listener = listener;
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(8, 8, 4, 8));
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        JLabel title = new JLabel("Advisor");
        title.setForeground(GOLD);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        header.add(title, BorderLayout.WEST);

        pauseBtn.setFont(pauseBtn.getFont().deriveFont(10f));
        pauseBtn.setFocusPainted(false);
        pauseBtn.addActionListener(e ->
        {
            paused = !paused;
            pauseBtn.setText(paused ? "Resume" : "Pause");
            listener.onPauseToggled(paused);
        });
        header.add(pauseBtn, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        content.setBorder(new EmptyBorder(4, 8, 8, 8));
        add(content, BorderLayout.CENTER);

        dumpFeed.setLayout(new BoxLayout(dumpFeed, BoxLayout.Y_AXIS));
        dumpFeed.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        dumpFeed.setBorder(new EmptyBorder(4, 8, 8, 8));
        add(dumpFeed, BorderLayout.SOUTH);

        showMessage("Enable the Advisor in plugin config to get next-flip suggestions.");
    }

    public boolean isPaused()
    {
        return paused;
    }

    /** Render a simple centered status message (disabled / loading / offline / no-flip). */
    public void showMessage(String message)
    {
        content.removeAll();
        JLabel label = new JLabel("<html><div style='width:200px'>" + message + "</div></html>");
        label.setForeground(DIM);
        label.setBorder(new EmptyBorder(24, 4, 24, 4));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(label);
        content.revalidate();
        content.repaint();
    }

    /** Render one suggestion card. */
    public void showSuggestion(Suggestion s)
    {
        if (s == null || s.isWait())
        {
            showMessage("No flip right now — your slots are full or capital is low. "
                + "Collect a finished offer and check back.");
            return;
        }

        content.removeAll();

        String actionText = "<html><center style='line-height: 1.2;'>";
        if ("BUY".equalsIgnoreCase(s.getAction()) || "SELL".equalsIgnoreCase(s.getAction())) {
            String colorHex = "BUY".equalsIgnoreCase(s.getAction()) ? "#00D26A" : "#FF4757"; // Green for buy, Red for sell
            actionText += "<span style='color:" + colorHex + "; font-size:14px; font-weight:bold;'>" + s.getAction() + "</span> " 
                       + "<b>" + s.getQuantity() + "</b><br>"
                       + "<b style='font-size:15px; color:white;'>" + s.getItemName() + "</b><br>"
                       + "<span style='color:#9A9A9A;'>for</span> <b style='color:white;'>" + formatGp(s.getPrice()) + "</b> <span style='color:#9A9A9A;'>gp</span>";
        } else {
            actionText += "<b>" + s.getAction() + "</b><br><b style='font-size:15px; color:white;'>" + s.getItemName() + "</b>";
        }
        actionText += "</center></html>";

        JLabel actionLabel = new JLabel(actionText);
        actionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel actionContainer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        actionContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        actionContainer.add(actionLabel);
        content.add(actionContainer);
        content.add(Box.createVerticalStrut(12));

        // Headline metric: Net profit per item
        if (s.getMarginPer() != 0) {
            String marginStr = formatGp(s.getMarginPer());
            if (s.getMarginPer() > 0) marginStr = "+" + marginStr;
            String marginHtml = "<html><center><b style='color:#00D26A; font-size:14px'>" + marginStr + " gp profit/ea</b><br><span style='color:#9A9A9A; font-size:9px'>after 2% GE tax</span></center></html>";
            if (s.getMarginPer() < 0) {
                marginHtml = "<html><center><b style='color:#FF4757; font-size:14px'>" + marginStr + " gp loss/ea</b><br><span style='color:#9A9A9A; font-size:9px'>after 2% GE tax</span></center></html>";
            }
            JLabel marginLabel = new JLabel(marginHtml);
            marginLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            JPanel marginContainer = new JPanel(new FlowLayout(FlowLayout.CENTER));
            marginContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            marginContainer.add(marginLabel);
            content.add(marginContainer);
            content.add(Box.createVerticalStrut(8));
        }

        // Secondary metrics
        if (s.getExpectedProfit() != 0) {
            content.add(metaColored("Total est. profit", profitText(s.getExpectedProfit()), profitColor(s.getExpectedProfit())));
        }
        if (s.getGeLimit() > 0) {
            content.add(meta("Buy limit", String.format("%,d / 4h", s.getGeLimit())));
        }
        if (s.getVolume() > 0) {
            content.add(meta("Volume (1h)", formatVolume(s.getVolume())));
        }

        // Price-tier badge (label, don't hide): the server grades the QUOTED PRICES
        // themselves (price-quality SSOT). INDICATIVE = stale book (context, not
        // executable quotes); NO_ESTIMATE/NO_DATA = no defensible live price. A fresh
        // EXECUTABLE book renders nothing here (honest default, no badge noise).
        String tier = s.getPriceTier();
        if (tier != null && !"EXECUTABLE".equalsIgnoreCase(tier))
        {
            boolean noBook = "NO_ESTIMATE".equalsIgnoreCase(tier) || "NO_DATA".equalsIgnoreCase(tier);
            content.add(metaColored("Price tier", noBook ? "NO LIVE BOOK" : "INDICATIVE", GOLD));
            content.add(wrapLeft(line("• " + (noBook
                ? "No recent trades — no defensible live price exists for this item"
                : "The order book is stale — prices are approximate context, not executable quotes"),
                DIM, Font.PLAIN, 11f)));
        }

        // Margin-quality badge (label, don't hide): the server grades the quoted
        // round-trip; anything but a fresh two-sided book gets a visible caution.
        String quality = s.getMarginQuality();
        if (quality != null && !"executable".equalsIgnoreCase(quality))
        {
            boolean noEstimate = "no_estimate".equalsIgnoreCase(quality);
            content.add(metaColored("Margin quality", noEstimate ? "NO ESTIMATE" : "ESTIMATE", GOLD));
            content.add(wrapLeft(line("• " + (noEstimate
                ? "No opposite-side price exists — the round-trip margin is unquotable"
                : "One price leg is stale — the quoted margin is an estimate, not executable"),
                DIM, Font.PLAIN, 11f)));
        }

        if (!s.getReasons().isEmpty())
        {
            content.add(Box.createVerticalStrut(4));
            for (String reason : s.getReasons())
            {
                content.add(wrapLeft(line("• " + reason, DIM, Font.PLAIN, 11f)));
            }
        }

        content.add(Box.createVerticalStrut(8));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Fill the suggested price/quantity straight into the GE offer input.
        // Arms the fill; the player opens the offer + confirms.
        JButton fill = new JButton("Fill offer");
        fill.setFont(fill.getFont().deriveFont(10f));
        fill.setFocusPainted(false);
        fill.setToolTipText("Auto-fill this price & quantity into the GE offer — open the offer to apply, you press Confirm");
        fill.addActionListener(e -> listener.onFillOffer(s.getItemId(), s.getPrice(), s.getQuantity()));
        buttons.add(fill);

        JButton skip = new JButton("Skip");
        skip.setFont(skip.getFont().deriveFont(10f));
        skip.setFocusPainted(false);
        skip.addActionListener(e -> listener.onSkip(s.getItemId()));
        buttons.add(skip);

        JButton block = new JButton("Block");
        block.setFont(block.getFont().deriveFont(10f));
        block.setFocusPainted(false);
        block.setToolTipText("Never suggest this item again");
        block.addActionListener(e -> listener.onBlock(s.getItemId()));
        buttons.add(block);

        content.add(buttons);
        content.revalidate();
        content.repaint();
    }

    /**
     * Render a coordinated basket of buys (Phase 3): one compact row per pick covering
     * item, action, price, quantity and est. profit, plus a footer with the basket's
     * total outlay and total expected profit. Used when the player has multiple free GE
     * slots; {@link #showSuggestion} still handles the single-flip case. Falls back to a
     * status message when the basket is empty.
     */
    public void showBasket(List<Suggestion> basket)
    {
        if (basket == null || basket.isEmpty())
        {
            showMessage("No basket right now — your slots are full or capital is low. "
                + "Collect a finished offer and check back.");
            return;
        }

        content.removeAll();

        JLabel header = new JLabel("Portfolio basket (" + basket.size() + ")");
        header.setForeground(GOLD);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(wrapLeft(header));
        content.add(Box.createVerticalStrut(2));

        JLabel sub = new JLabel("Coins spread across your free slots");
        sub.setForeground(DIM);
        sub.setFont(sub.getFont().deriveFont(10f));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(wrapLeft(sub));
        content.add(Box.createVerticalStrut(6));

        long totalSpend = 0;
        long totalProfit = 0;
        for (Suggestion s : basket)
        {
            if (s == null)
            {
                continue;
            }
            totalSpend += s.getPrice() * (long) s.getQuantity();
            totalProfit += s.getExpectedProfit();
            content.add(basketRow(s));
            content.add(Box.createVerticalStrut(4));
        }

        content.add(Box.createVerticalStrut(2));
        JPanel sep = new JPanel();
        sep.setBackground(new Color(0x3A, 0x3A, 0x3A));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(sep);
        content.add(Box.createVerticalStrut(4));

        content.add(meta("Total cost", formatGp(totalSpend)));
        content.add(metaColored("Est. profit", profitText(totalProfit), profitColor(totalProfit)));

        content.revalidate();
        content.repaint();
    }

    /**
     * Render the agentic "Next moves" (PRO): a confidence-weighted, reasoned buy plan from
     * the honesty-calibrated reasoning layer. Same compact rows as {@link #showBasket} plus
     * the lead reason per pick and a confidence-weighted footer note, under a distinct header
     * so it reads as the premium, explained variant of the basket. Empty → status message.
     */
    public void showNextMoves(List<Suggestion> moves)
    {
        if (moves == null || moves.isEmpty())
        {
            showMessage("No confident next move right now — capital is low, slots are full, or "
                + "the reasoner is holding back on weak signals. Check back after your next GE action.");
            return;
        }

        content.removeAll();

        JLabel header = new JLabel("Next moves (" + moves.size() + ")");
        header.setForeground(GOLD);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(wrapLeft(header));
        content.add(Box.createVerticalStrut(2));

        JLabel sub = new JLabel("Confidence-weighted, reasoned picks — sized to your coins");
        sub.setForeground(DIM);
        sub.setFont(sub.getFont().deriveFont(10f));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(wrapLeft(sub));
        content.add(Box.createVerticalStrut(6));

        long totalSpend = 0;
        long totalProfit = 0;
        for (Suggestion s : moves)
        {
            if (s == null)
            {
                continue;
            }
            totalSpend += s.getPrice() * (long) s.getQuantity();
            totalProfit += s.getExpectedProfit();
            content.add(basketRow(s));
            // The lead reason (agentic explanation), shown dim under the row.
            if (!s.getReasons().isEmpty())
            {
                JLabel why = new JLabel("<html><div style='width:200px'>" + s.getReasons().get(0) + "</div></html>");
                why.setForeground(DIM);
                why.setFont(why.getFont().deriveFont(10f));
                why.setAlignmentX(Component.LEFT_ALIGNMENT);
                content.add(wrapLeft(why));
            }
            content.add(Box.createVerticalStrut(6));
        }

        content.add(Box.createVerticalStrut(2));
        JPanel sep = new JPanel();
        sep.setBackground(new Color(0x3A, 0x3A, 0x3A));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(sep);
        content.add(Box.createVerticalStrut(4));

        content.add(meta("Total cost", formatGp(totalSpend)));
        content.add(meta("Est. profit", formatGp(totalProfit)));

        content.revalidate();
        content.repaint();
    }

    /**
     * One compact basket line: name + action tag on top; qty @ price + colored
     * profit/loss on the second line; buy-limit, profit-at-limit and volume on a dim
     * third line so the per-cycle ceiling and liquidity are visible at a glance.
     */
    private JPanel basketRow(Suggestion s)
    {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.setBorder(new EmptyBorder(4, 6, 4, 6));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JLabel name = new JLabel(s.getItemName());
        name.setForeground(Color.WHITE);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 12f));
        JLabel tag = new JLabel(s.getAction(), SwingConstants.RIGHT);
        tag.setForeground(Color.WHITE);
        tag.setOpaque(true);
        tag.setBackground(actionColor(s.getAction()));
        tag.setBorder(new EmptyBorder(1, 6, 1, 6));
        tag.setFont(tag.getFont().deriveFont(Font.BOLD, 10f));
        top.add(name, BorderLayout.WEST);
        JPanel tagWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        tagWrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tagWrap.add(tag);
        top.add(tagWrap, BorderLayout.EAST);
        row.add(top);

        // Line 2: qty @ price  •  signed profit/loss (red when it's a loss).
        JLabel detail = new JLabel(String.format("%,d @ %s  •  %s",
            s.getQuantity(), formatGp(s.getPrice()), profitText(s.getExpectedProfit())));
        detail.setForeground(profitColor(s.getExpectedProfit()));
        detail.setFont(detail.getFont().deriveFont(11f));
        JPanel detailWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        detailWrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
        detailWrap.add(detail);
        row.add(detailWrap);

        // Line 3 (dim): buy limit · profit-at-full-limit · 1h volume.
        StringBuilder sub = new StringBuilder();
        if (s.getGeLimit() > 0)
        {
            sub.append(String.format("Limit %,d", s.getGeLimit()))
                .append("  •  ").append(profitText(s.getProfitPerLimit())).append("/limit");
        }
        if (s.getVolume() > 0)
        {
            if (sub.length() > 0)
            {
                sub.append("  •  ");
            }
            sub.append("vol ").append(formatVolume(s.getVolume()));
        }
        if (s.getMarginQuality() != null && !"executable".equalsIgnoreCase(s.getMarginQuality()))
        {
            if (sub.length() > 0)
            {
                sub.append("  •  ");
            }
            sub.append("margin: ").append(
                "no_estimate".equalsIgnoreCase(s.getMarginQuality()) ? "NO ESTIMATE" : "ESTIMATE");
        }
        if (sub.length() > 0)
        {
            JLabel meta = new JLabel(sub.toString());
            meta.setForeground(DIM);
            meta.setFont(meta.getFont().deriveFont(10f));
            JPanel metaWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            metaWrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
            metaWrap.add(meta);
            row.add(metaWrap);
        }

        return row;
    }

    /**
     * Render the free, no-account "Recent F2P dumps" feed below the suggestion card.
     * F2P-only for anonymous users (the server gates members items). Read-only list —
     * informational, never an action button. Pass an empty/null list to hide it.
     */
    public void showDumpFeed(List<DumpFeedEntry> entries)
    {
        dumpFeed.removeAll();
        if (entries != null && !entries.isEmpty())
        {
            JLabel header = new JLabel("Recent F2P dumps");
            header.setForeground(GOLD);
            header.setFont(header.getFont().deriveFont(Font.BOLD, 11f));
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            dumpFeed.add(wrapLeft(header));
            dumpFeed.add(Box.createVerticalStrut(2));

            int shown = 0;
            for (DumpFeedEntry e : entries)
            {
                if (e == null || shown >= 5)
                {
                    continue;
                }
                JPanel row = new JPanel();
                row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
                row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
                row.setBorder(new EmptyBorder(0, 0, 4, 0));

                JPanel top = new JPanel(new BorderLayout());
                top.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                JLabel name = new JLabel(e.getItemName());
                name.setForeground(Color.WHITE);
                name.setFont(name.getFont().deriveFont(11f));
                JLabel chg = new JLabel(formatPct(e.getPercentChange()), SwingConstants.RIGHT);
                chg.setForeground(e.getPercentChange() < 0 ? RED : DIM);
                chg.setFont(chg.getFont().deriveFont(Font.BOLD, 11f));
                top.add(name, BorderLayout.WEST);
                top.add(chg, BorderLayout.EAST);
                row.add(top);

                boolean enterable = (e.getSellTarget() != null && e.getNetMargin() != null && e.getNetMargin() > 0 &&
                        (e.getRecoveryProb() == null || e.getRecoveryProb() >= 0.05));
                if (enterable) {
                    JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                    bottom.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                    
                    String text = "<html><span style='color:#9A9A9A; font-size:9px'>Buy </span><span style='color:white; font-size:9px'>" 
                            + formatGp(e.getBuyPrice()) 
                            + "</span><span style='color:#9A9A9A; font-size:9px'> • Sell </span><span style='color:white; font-size:9px'>" 
                            + formatGp(e.getSellTarget()) 
                            + "</span><span style='color:#9A9A9A; font-size:9px'> • Profit </span><span style='color:#00D26A; font-size:9px'>+" 
                            + formatGp(e.getNetMargin()) + "/ea</span></html>";
                    
                    JLabel detail = new JLabel(text);
                    bottom.add(detail);
                    row.add(bottom);
                } else if (e.getSellTarget() != null && e.getRecoveryProb() != null && e.getRecoveryProb() < 0.05) {
                    JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                    bottom.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                    JLabel detail = new JLabel("<html><span style='color:#FF4757; font-size:9px'>No entry</span><span style='color:#9A9A9A; font-size:9px'> — information only</span></html>");
                    bottom.add(detail);
                    row.add(bottom);
                }
                
                dumpFeed.add(row);
                shown++;
            }

            JLabel note = new JLabel("Free feed — no account needed");
            note.setForeground(DIM);
            note.setFont(note.getFont().deriveFont(9f));
            note.setAlignmentX(Component.LEFT_ALIGNMENT);
            dumpFeed.add(Box.createVerticalStrut(2));
            dumpFeed.add(wrapLeft(note));
        }
        dumpFeed.revalidate();
        dumpFeed.repaint();
    }

    private static String formatPct(double pct)
    {
        return String.format("%.1f%%", pct);
    }

    private static Color actionColor(String action)
    {
        if ("BUY".equalsIgnoreCase(action))
        {
            return GREEN.darker();
        }
        if ("SELL".equalsIgnoreCase(action))
        {
            return new Color(0x00, 0x88, 0xCC);
        }
        if ("ABORT".equalsIgnoreCase(action))
        {
            return RED.darker();
        }
        return new Color(0x55, 0x55, 0x55);
    }

    private JPanel meta(String label, String value)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        JLabel l = new JLabel(label);
        l.setForeground(DIM);
        l.setFont(l.getFont().deriveFont(11f));
        JLabel v = new JLabel(value, SwingConstants.RIGHT);
        v.setForeground(Color.WHITE);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 11f));
        row.add(l, BorderLayout.WEST);
        row.add(v, BorderLayout.EAST);
        return row;
    }

    /** Like {@link #meta} but the value is rendered in the given colour (for profit/loss). */
    private JPanel metaColored(String label, String value, Color valueColor)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        JLabel l = new JLabel(label);
        l.setForeground(DIM);
        l.setFont(l.getFont().deriveFont(11f));
        JLabel v = new JLabel(value, SwingConstants.RIGHT);
        v.setForeground(valueColor);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 11f));
        row.add(l, BorderLayout.WEST);
        row.add(v, BorderLayout.EAST);
        return row;
    }

    /** "+1,234 gp" for a gain, "-1,234 gp loss" for a loss — never a silent "+-". */
    private static String profitText(long profit)
    {
        if (profit < 0)
        {
            return "-" + formatGp(-profit) + " loss";
        }
        return "+" + formatGp(profit);
    }

    private static Color profitColor(long profit)
    {
        return profit < 0 ? RED : GREEN;
    }

    /** Compact volume: 1,234 / 12.3k / 4.5m — a quick liquidity read. */
    private static String formatVolume(long vol)
    {
        if (vol >= 1_000_000)
        {
            return String.format("%.1fm", vol / 1_000_000.0);
        }
        if (vol >= 10_000)
        {
            return String.format("%.1fk", vol / 1_000.0);
        }
        return String.format("%,d", vol);
    }

    private static JLabel line(String text, Color color, int style, float size)
    {
        JLabel l = new JLabel(text);
        l.setForeground(color);
        l.setFont(l.getFont().deriveFont(style, size));
        return l;
    }

    private static JPanel wrapLeft(Component c)
    {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(c);
        return p;
    }

    private static String formatGp(long gp)
    {
        return String.format("%,d gp", gp);
    }
}
