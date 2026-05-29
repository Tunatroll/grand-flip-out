/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.ui;

import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.model.Recipe;
import com.fliphelper.util.GeTax;
import com.fliphelper.util.RecipeCatalog;
import net.runelite.client.ui.ColorScheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;

/**
 * Native combination-item arbitrage view: for each bundled {@link Recipe}, compares the
 * cost of buying the component pieces against the price of the assembled item, AFTER the
 * GE sell tax, and surfaces whichever direction is profitable.
 *
 * <p>Two directions are evaluated per recipe:
 * <ul>
 *   <li><b>Assemble</b> — buy the pieces (at insta-buy / ask), combine, sell the set
 *       (at insta-sell / bid, minus tax on the set).</li>
 *   <li><b>Split</b> — buy the set (at ask), split it, sell each piece (at bid, minus
 *       per-piece tax).</li>
 * </ul>
 * The better of the two is shown as the headline profit. Prices come from the live
 * {@link PriceService}; no network or website round-trip.
 */
public class RecipePanel extends JPanel
{
    // Granary dark theme — mirrors GrandFlipOutPanel.
    private static final Color BRAND_GOLD = new Color(0xE0, 0xA8, 0x2E);
    private static final Color PANEL_DEEP = new Color(0x0D, 0x0D, 0x0C);
    private static final Color PANEL_CARD = new Color(0x1A, 0x18, 0x15);
    private static final Color TEXT_DIM = new Color(0x7A, 0x71, 0x5C);
    private static final Color TEXT_LIGHT = new Color(0xD8, 0xCF, 0xBE);
    private static final Color PROFIT_GREEN = new Color(0x5F, 0xB8, 0x5F);
    private static final Color LOSS_RED = new Color(0xD4, 0x6A, 0x6A);
    private static final Color PANEL_BORDER = new Color(0x2A, 0x26, 0x20);
    private static final Color PANEL_BUTTON = new Color(0x14, 0x13, 0x12);

    private final transient PriceService priceService;
    private final JPanel listPanel;

    public RecipePanel(PriceService priceService)
    {
        this.priceService = priceService;
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        add(buildHeader(), BorderLayout.NORTH);

        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        listPanel.setBorder(new EmptyBorder(6, 8, 8, 8));

        // Anchor the card list to the top so BoxLayout cards keep their preferred
        // height instead of stretching to fill extra vertical space.
        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(ColorScheme.DARK_GRAY_COLOR);
        holder.add(listPanel, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
        add(scroll, BorderLayout.CENTER);

        refresh();
    }

    private JPanel buildHeader()
    {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(PANEL_DEEP);
        header.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel title = new JLabel("Recipe / Set Arbitrage");
        title.setForeground(BRAND_GOLD);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);

        JLabel sub = new JLabel("Set vs pieces, after 2% GE tax");
        sub.setForeground(TEXT_DIM);
        sub.setFont(sub.getFont().deriveFont(10f));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(Box.createVerticalStrut(2));
        header.add(sub);
        header.add(Box.createVerticalStrut(6));

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setFont(refreshBtn.getFont().deriveFont(11f));
        refreshBtn.setForeground(BRAND_GOLD);
        refreshBtn.setBackground(PANEL_BUTTON);
        refreshBtn.setFocusPainted(false);
        refreshBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PANEL_BORDER),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        refreshBtn.addActionListener(e -> refresh());
        header.add(refreshBtn);

        return header;
    }

    /** Rebuild all recipe rows from current prices. Safe to call on the EDT. */
    public void refresh()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }
        listPanel.removeAll();

        List<Recipe> recipes = RecipeCatalog.all();
        boolean priced = priceService != null;
        int rendered = 0;
        for (Recipe recipe : recipes)
        {
            JPanel card = buildRecipeCard(recipe);
            if (card != null)
            {
                listPanel.add(card);
                listPanel.add(Box.createVerticalStrut(6));
                rendered++;
            }
        }

        if (rendered == 0)
        {
            JLabel empty = new JLabel(priced
                ? "Prices loading — try Refresh in a moment."
                : "No price service available.");
            empty.setForeground(TEXT_DIM);
            empty.setBorder(new EmptyBorder(12, 4, 12, 4));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            listPanel.add(empty);
        }

        listPanel.revalidate();
        listPanel.repaint();
    }

    /**
     * Build a card for one recipe, or {@code null} if not enough live prices exist to
     * compute anything meaningful (avoids showing rows full of dashes).
     */
    private JPanel buildRecipeCard(Recipe recipe)
    {
        PriceAggregate outAgg = priceService != null ? priceService.getPrice(recipe.getOutputItemId()) : null;

        // Component buy cost (sum of asks) and component sell value (sum of bids minus tax).
        long piecesBuyCost = 0;      // buy each piece at ask
        long piecesSellValue = 0;    // sell each piece at bid, after per-piece tax
        boolean piecesComplete = true;
        for (int inId : recipe.getInputItemIds())
        {
            PriceAggregate in = priceService != null ? priceService.getPrice(inId) : null;
            long ask = in != null ? in.getBestLowPrice() : 0;
            long bid = in != null ? in.getBestHighPrice() : 0;
            if (ask <= 0 || bid <= 0)
            {
                piecesComplete = false;
                break;
            }
            piecesBuyCost += ask;
            piecesSellValue += bid - GeTax.tax(inId, bid, 1);
        }

        long setAsk = outAgg != null ? outAgg.getBestLowPrice() : 0;
        long setBid = outAgg != null ? outAgg.getBestHighPrice() : 0;

        // Not enough data to evaluate either direction.
        if (!piecesComplete || setAsk <= 0 || setBid <= 0)
        {
            return null;
        }

        // Assemble: buy pieces at ask, sell set at bid (minus tax on the set).
        long setSellAfterTax = setBid - GeTax.tax(recipe.getOutputItemId(), setBid, 1);
        long assembleProfit = setSellAfterTax - piecesBuyCost;

        // Split: buy set at ask, sell pieces at bid (already net of per-piece tax).
        long splitProfit = piecesSellValue - setAsk;

        boolean assembleBetter = assembleProfit >= splitProfit;
        long bestProfit = Math.max(assembleProfit, splitProfit);
        String direction = assembleBetter ? "Assemble" : "Split";

        JPanel card = new JPanel(new BorderLayout(6, 2));
        card.setBackground(PANEL_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bestProfit > 0 ? PROFIT_GREEN : PANEL_BORDER),
            new EmptyBorder(8, 10, 8, 10)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // ---- Top row: name + headline profit ----
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel name = new JLabel(recipe.getName());
        name.setForeground(TEXT_LIGHT);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 12f));
        top.add(name, BorderLayout.WEST);

        JLabel profit = new JLabel(signed(bestProfit));
        profit.setForeground(bestProfit > 0 ? PROFIT_GREEN : LOSS_RED);
        profit.setFont(profit.getFont().deriveFont(Font.BOLD, 12f));
        top.add(profit, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        // ---- Detail grid ----
        JPanel grid = new JPanel(new GridLayout(0, 2, 6, 1));
        grid.setOpaque(false);
        grid.setBorder(new EmptyBorder(4, 0, 2, 0));

        grid.add(metaLabel("Action", direction));
        grid.add(metaLabel("Pieces", recipe.getInputItemIds().size() + " items"));
        grid.add(metaLabel("Pieces buy", formatGp(piecesBuyCost) + " gp"));
        grid.add(metaLabel("Set sell (net)", formatGp(setSellAfterTax) + " gp"));
        grid.add(metaLabel("Set buy", formatGp(setAsk) + " gp"));
        grid.add(metaLabel("Pieces sell (net)", formatGp(piecesSellValue) + " gp"));
        grid.add(metaLabel("Assemble", signed(assembleProfit)));
        grid.add(metaLabel("Split", signed(splitProfit)));
        card.add(grid, BorderLayout.CENTER);

        // ---- Note ----
        JLabel note = new JLabel("<html><div style='width:200px'>" + recipe.getNote() + "</div></html>");
        note.setForeground(TEXT_DIM);
        note.setFont(note.getFont().deriveFont(9f));
        card.add(note, BorderLayout.SOUTH);

        return card;
    }

    private JPanel metaLabel(String key, String value)
    {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JLabel k = new JLabel(key);
        k.setForeground(TEXT_DIM);
        k.setFont(k.getFont().deriveFont(9f));
        JLabel v = new JLabel(value);
        v.setForeground(TEXT_LIGHT);
        v.setFont(v.getFont().deriveFont(10f));
        p.add(k, BorderLayout.NORTH);
        p.add(v, BorderLayout.SOUTH);
        return p;
    }

    private static String signed(long v)
    {
        return (v >= 0 ? "+" : "") + formatGp(v) + " gp";
    }

    /** Compact, negative-safe gp formatter (e.g. -1.2M, 350.0K, 42). */
    private static String formatGp(long v)
    {
        long abs = Math.abs(v);
        String sign = v < 0 ? "-" : "";
        if (abs >= 1_000_000_000L)
        {
            return sign + String.format("%.1fB", abs / 1_000_000_000.0);
        }
        if (abs >= 1_000_000L)
        {
            return sign + String.format("%.1fM", abs / 1_000_000.0);
        }
        if (abs >= 1_000L)
        {
            return sign + String.format("%.1fK", abs / 1_000.0);
        }
        return sign + abs;
    }
}
