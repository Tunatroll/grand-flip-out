/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.ui;

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
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/**
 * The Advisor tab: shows one next-action suggestion at a time (Copilot-style).
 * Pure view — the plugin owns fetching/state and pushes results in via
 * {@link #showSuggestion}/{@link #showMessage}. Skip/Block/Pause are reported back
 * through {@link Listener}; Copy is handled here (one click → clipboard, compliant).
 */
public class AdvisorPanel extends JPanel
{
    public interface Listener
    {
        void onSkip(int itemId);

        void onBlock(int itemId);

        void onPauseToggled(boolean paused);
    }

    private static final Color GOLD = new Color(0xFF, 0x98, 0x1F);
    private static final Color GREEN = new Color(0x00, 0xD2, 0x6A);
    private static final Color RED = new Color(0xFF, 0x47, 0x57);
    private static final Color DIM = new Color(0x9A, 0x9A, 0x9A);

    private final Listener listener;
    private final JPanel content = new JPanel();
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

        JLabel action = new JLabel(s.getAction());
        action.setForeground(Color.WHITE);
        action.setOpaque(true);
        action.setBackground(actionColor(s.getAction()));
        action.setBorder(new EmptyBorder(2, 8, 2, 8));
        action.setFont(action.getFont().deriveFont(Font.BOLD, 12f));
        action.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(wrapLeft(action));
        content.add(Box.createVerticalStrut(6));

        content.add(wrapLeft(line(s.getItemName(), Color.WHITE, Font.BOLD, 13f)));
        content.add(Box.createVerticalStrut(4));

        content.add(meta("Price", formatGp(s.getPrice())));
        content.add(meta("Quantity", String.valueOf(s.getQuantity())));
        content.add(meta("Est. profit", formatGp(s.getExpectedProfit())));
        content.add(meta("Confidence", Math.round(s.getConfidence() * 100) + "%"));

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

        JButton copy = new JButton("Copy price");
        copy.setFont(copy.getFont().deriveFont(10f));
        copy.setFocusPainted(false);
        copy.addActionListener(e -> copyToClipboard(String.valueOf(s.getPrice())));
        buttons.add(copy);

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

    private static void copyToClipboard(String text)
    {
        try
        {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
        }
        catch (Exception ignored)
        {
            // clipboard unavailable (headless) — nothing to do
        }
    }

    private static String formatGp(long gp)
    {
        return String.format("%,d gp", gp);
    }
}
