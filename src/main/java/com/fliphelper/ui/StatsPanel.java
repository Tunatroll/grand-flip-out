/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.ui;

import com.fliphelper.model.FlipItem;
import com.fliphelper.tracker.FlipTracker;
import com.fliphelper.tracker.SessionManager;
import lombok.extern.slf4j.Slf4j;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Numbers-only "Stats" block with a time-interval dropdown, matching
 * Flipping Copilot / Flipping Utilities. Recomputes profit, ROI percent, flips
 * made, tax paid, GP/hr (Session only) and session time from the completed-flip
 * history filtered by the selected interval.
 */
@Slf4j
public class StatsPanel extends JPanel
{
    // GFO pastel brand via GfoPalette (granary theme retired 2026-07-10)
    private static final Color BRAND_GOLD = GfoPalette.ACCENT;
    private static final Color PANEL_DEEP = GfoPalette.PANEL;
    private static final Color PANEL_CARD = GfoPalette.CARD;
    private static final Color TEXT_DIM = GfoPalette.TEXT_MUTED;
    private static final Color PROFIT_GREEN = GfoPalette.UP;
    private static final Color LOSS_RED = GfoPalette.DOWN;

    private static final NumberFormat GP_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
    private static final String DASH = "—";

    /** Interval options, matching FC/FU exactly. */
    static final String[] INTERVALS = {
        "Past Hour", "Past 4 Hours", "Past 12 Hours", "Past Day",
        "Past Week", "Past Month", "Session", "All"
    };

    /** Account selector sentinel that aggregates every account. */
    public static final String ALL_ACCOUNTS = "All accounts";

    /** Bucket label for flips with no recorded account (legacy/account-less history). */
    public static final String UNKNOWN_ACCOUNT = "Unknown";

    private final transient FlipTracker flipTracker;
    private final transient SessionManager sessionManager;

    private final JComboBox<String> intervalSelector;
    private final JComboBox<String> accountSelector;

    /** Invoked when the account/interval selection changes, so dependent views re-filter. */
    private transient Runnable selectionListener;

    private JLabel profitTitleLabel;
    private JLabel profitLabel;
    private JLabel roiLabel;
    private JLabel flipsLabel;
    private JLabel taxLabel;
    private JLabel gpHrLabel;
    private JLabel sessionTimeLabel;
    private JPanel sessionTimeRow;

    public StatsPanel(FlipTracker flipTracker, SessionManager sessionManager)
    {
        this.flipTracker = flipTracker;
        this.sessionManager = sessionManager;

        setLayout(new BorderLayout());
        setBackground(PANEL_DEEP);
        setBorder(new EmptyBorder(8, 10, 8, 10));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel heading = new JLabel("Stats");
        heading.setForeground(BRAND_GOLD);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 13f));
        top.add(heading, BorderLayout.WEST);

        intervalSelector = new JComboBox<>(INTERVALS);
        intervalSelector.setSelectedItem("Session");
        intervalSelector.setFont(intervalSelector.getFont().deriveFont(12f));
        intervalSelector.setBackground(PANEL_CARD);
        intervalSelector.setForeground(Color.LIGHT_GRAY);
        intervalSelector.setFocusable(false);
        intervalSelector.addActionListener(e -> onSelectionChanged());
        top.add(intervalSelector, BorderLayout.EAST);

        // Per-account selector: "All accounts" + each distinct RSN in history.
        accountSelector = new JComboBox<>();
        accountSelector.addItem(ALL_ACCOUNTS);
        accountSelector.setFont(accountSelector.getFont().deriveFont(12f));
        accountSelector.setBackground(PANEL_CARD);
        accountSelector.setForeground(Color.LIGHT_GRAY);
        accountSelector.setFocusable(false);
        accountSelector.addActionListener(e -> onSelectionChanged());
        JPanel accountRow = new JPanel(new BorderLayout());
        accountRow.setOpaque(false);
        accountRow.setBorder(new EmptyBorder(4, 0, 0, 0));
        JLabel accountLabel = new JLabel("Account");
        accountLabel.setForeground(TEXT_DIM);
        accountLabel.setFont(accountLabel.getFont().deriveFont(12f));
        accountRow.add(accountLabel, BorderLayout.WEST);
        accountRow.add(accountSelector, BorderLayout.EAST);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(top, BorderLayout.NORTH);
        header.add(accountRow, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        JPanel grid = new JPanel();
        grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));
        grid.setOpaque(false);
        grid.setBorder(new EmptyBorder(6, 0, 0, 0));

        profitTitleLabel = makeTitle("Profit:");
        profitLabel = makeValue("0 gp");
        roiLabel = makeValue("0.00%");
        flipsLabel = makeValue("0");
        taxLabel = makeValue("0 gp");
        gpHrLabel = makeValue(DASH);
        sessionTimeLabel = makeValue("00:00:00");

        grid.add(buildStatRow(profitTitleLabel, profitLabel));
        grid.add(Box.createVerticalStrut(3));
        grid.add(buildStatRow(makeTitle("ROI:"), roiLabel));
        grid.add(Box.createVerticalStrut(3));
        grid.add(buildStatRow(makeTitle("Flips made:"), flipsLabel));
        grid.add(Box.createVerticalStrut(3));
        grid.add(buildStatRow(makeTitle("Tax paid:"), taxLabel));
        grid.add(Box.createVerticalStrut(3));
        grid.add(buildStatRow(makeTitle("GP/hr:"), gpHrLabel));
        grid.add(Box.createVerticalStrut(3));
        sessionTimeRow = buildStatRow(makeTitle("Session time:"), sessionTimeLabel);
        grid.add(sessionTimeRow);

        add(grid, BorderLayout.CENTER);

        recompute();
    }

    private JLabel makeTitle(String text)
    {
        JLabel t = new JLabel(text);
        t.setForeground(TEXT_DIM);
        t.setFont(t.getFont().deriveFont(12f));
        return t;
    }

    private JLabel makeValue(String text)
    {
        JLabel v = new JLabel(text);
        v.setForeground(Color.LIGHT_GRAY);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 12f));
        v.setHorizontalAlignment(JLabel.RIGHT);
        return v;
    }

    private JPanel buildStatRow(JLabel titleLabel, JLabel valueLabel)
    {
        JPanel row = new JPanel(new GridLayout(1, 2, 6, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        row.add(titleLabel);
        row.add(valueLabel);
        return row;
    }

    /** Recompute the stats from the completed-flip history for the selected interval. */
    public void recompute()
    {
        refreshAccounts();
        String interval = (String) intervalSelector.getSelectedItem();
        if (interval == null)
        {
            interval = "Session";
        }
        boolean isSession = "Session".equals(interval);
        Instant cutoff = cutoffFor(interval);
        String account = getSelectedAccount();

        long profit = 0;
        long costBasis = 0;
        long tax = 0;
        int flips = 0;

        List<FlipItem> completed = flipTracker.getCompletedFlips();
        synchronized (completed)
        {
            for (FlipItem flip : completed)
            {
                if (!flip.isComplete() || flip.getSellTime() == null)
                {
                    continue;
                }
                if (cutoff != null && flip.getSellTime().isBefore(cutoff))
                {
                    continue;
                }
                if (!matchesAccount(flip, account))
                {
                    continue;
                }
                profit += flip.getProfit();
                costBasis += flip.getBuyPrice() * (long) flip.getQuantity();
                tax += flip.getTax();
                flips++;
            }
        }

        boolean loss = profit < 0;
        profitTitleLabel.setText(loss ? "Loss:" : "Profit:");
        profitLabel.setText(GP_FORMAT.format(profit) + " gp");
        profitLabel.setForeground(profit >= 0 ? PROFIT_GREEN : LOSS_RED);

        double roi = costBasis > 0 ? (double) profit / costBasis * 100.0 : 0.0;
        roiLabel.setText(String.format(Locale.US, "%.2f%%", roi));
        roiLabel.setForeground(roi >= 0 ? PROFIT_GREEN : LOSS_RED);

        flipsLabel.setText(String.valueOf(flips));
        taxLabel.setText(GP_FORMAT.format(tax) + " gp");

        double hours = sessionHours();
        if (isSession && hours > 0)
        {
            long gpHr = (long) (profit / hours);
            gpHrLabel.setText(GP_FORMAT.format(gpHr) + " gp");
            gpHrLabel.setForeground(gpHr >= 0 ? BRAND_GOLD : LOSS_RED);
        }
        else
        {
            gpHrLabel.setText(DASH);
            gpHrLabel.setForeground(TEXT_DIM);
        }

        sessionTimeRow.setVisible(isSession);
        if (isSession)
        {
            sessionTimeLabel.setText(formatHms(sessionElapsedSeconds()));
            sessionTimeLabel.setForeground(Color.LIGHT_GRAY);
        }

        revalidate();
        repaint();
    }

    /**
     * Registers a callback invoked whenever the account or interval selection
     * changes, letting the parent panel re-filter dependent views (history list).
     *
     * @param listener the callback to run on selection changes
     */
    public void setSelectionListener(Runnable listener)
    {
        this.selectionListener = listener;
    }

    /**
     * @return the currently selected account label, or {@link #ALL_ACCOUNTS}
     */
    public String getSelectedAccount()
    {
        Object selected = accountSelector.getSelectedItem();
        return selected != null ? (String) selected : ALL_ACCOUNTS;
    }

    /**
     * Maps a flip to its account bucket label, treating null/blank accounts as
     * {@link #UNKNOWN_ACCOUNT} (pre-account history).
     *
     * @param flip the flip to classify
     * @return the account label this flip belongs to
     */
    public static String accountLabel(FlipItem flip)
    {
        String name = flip.getAccountName();
        return (name == null || name.trim().isEmpty()) ? UNKNOWN_ACCOUNT : name;
    }

    private boolean matchesAccount(FlipItem flip, String account)
    {
        return ALL_ACCOUNTS.equals(account) || accountLabel(flip).equals(account);
    }

    private void onSelectionChanged()
    {
        recompute();
        if (selectionListener != null)
        {
            selectionListener.run();
        }
    }

    /**
     * Rebuilds the account dropdown from the distinct accounts present in the
     * completed-flip history, preserving the current selection when still valid.
     * Call after history changes so newly-seen accounts appear.
     */
    public void refreshAccounts()
    {
        java.util.Set<String> distinct = new java.util.TreeSet<>();
        List<FlipItem> completed = flipTracker.getCompletedFlips();
        synchronized (completed)
        {
            for (FlipItem flip : completed)
            {
                if (flip.isComplete())
                {
                    distinct.add(accountLabel(flip));
                }
            }
        }

        java.util.List<String> desired = new java.util.ArrayList<>();
        desired.add(ALL_ACCOUNTS);
        desired.addAll(distinct);

        java.util.List<String> current = new java.util.ArrayList<>();
        for (int i = 0; i < accountSelector.getItemCount(); i++)
        {
            current.add(accountSelector.getItemAt(i));
        }
        if (desired.equals(current))
        {
            return;
        }

        String previous = getSelectedAccount();
        // Avoid firing the action listener while we rebuild the model.
        java.awt.event.ActionListener[] listeners = accountSelector.getActionListeners();
        for (java.awt.event.ActionListener l : listeners)
        {
            accountSelector.removeActionListener(l);
        }
        accountSelector.removeAllItems();
        for (String label : desired)
        {
            accountSelector.addItem(label);
        }
        accountSelector.setSelectedItem(desired.contains(previous) ? previous : ALL_ACCOUNTS);
        for (java.awt.event.ActionListener l : listeners)
        {
            accountSelector.addActionListener(l);
        }
    }

    /** Returns the earliest sell-time we include, or null for "All". */
    private Instant cutoffFor(String interval)
    {
        Instant now = Instant.now();
        switch (interval)
        {
            case "Past Hour":
                return now.minus(Duration.ofHours(1));
            case "Past 4 Hours":
                return now.minus(Duration.ofHours(4));
            case "Past 12 Hours":
                return now.minus(Duration.ofHours(12));
            case "Past Day":
                return now.minus(Duration.ofDays(1));
            case "Past Week":
                return now.minus(Duration.ofDays(7));
            case "Past Month":
                return now.minus(Duration.ofDays(30));
            case "Session":
                SessionManager.FlipSession s = activeSession();
                return s != null && s.getStartTime() != null ? s.getStartTime() : null;
            case "All":
            default:
                return null;
        }
    }

    private SessionManager.FlipSession activeSession()
    {
        return sessionManager != null ? sessionManager.getActiveSession() : null;
    }

    private long sessionElapsedSeconds()
    {
        SessionManager.FlipSession s = activeSession();
        if (s == null || s.getStartTime() == null)
        {
            return 0;
        }
        long secs = Duration.between(s.getStartTime(), Instant.now()).getSeconds();
        return Math.max(0, secs);
    }

    private double sessionHours()
    {
        return sessionElapsedSeconds() / 3600.0;
    }

    private static String formatHms(long totalSeconds)
    {
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }
}
