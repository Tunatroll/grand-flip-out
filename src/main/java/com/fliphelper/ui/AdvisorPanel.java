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
import javax.swing.JScrollPane;
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

        /** #215: a band/fast-fill chip changed — refetch the suggestion under the new filters. */
        void onFiltersChanged();
    }

    // GFO pastel brand via GfoPalette (OSRS-gold locals retired 2026-07-10)
    private static final Color GOLD = GfoPalette.ACCENT;
    private static final Color GREEN = GfoPalette.UP;
    private static final Color RED = GfoPalette.DOWN;
    private static final Color DIM = GfoPalette.TEXT_MUTED;

    private final Listener listener;
    private final JPanel content = new JPanel();
    private final JPanel dumpFeed = new JPanel();
    private final JButton pauseBtn = new JButton("Pause");
    private boolean paused;

    // #215 band chips — wire values are the flip-bands taxonomy keys (null = All).
    // Volatile: the EDT writes on chip clicks, the fetch executor reads.
    private volatile String selectedBand;
    private volatile boolean fastFillsOnly;
    private static final String[] BAND_VALUES = {null, "throughput", "patient_whale"};
    private final JButton[] bandChips = new JButton[BAND_VALUES.length];
    private JButton fastChip;

    // #215 items 5+6 (power-user batch): basket/next-moves rows render COMPACT
    // (two lines) by default and expand IN PLACE on click to the full detail +
    // per-item Fill/Skip/Block. One row open at a time — vertical space is the
    // scarce resource at 242px ("takes a lot of space"). The list is kept so a
    // toggle can re-render without a refetch.
    private int expandedItemId = -1;
    private List<Suggestion> listRows;
    private boolean listIsNextMoves;

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
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        header.add(title, BorderLayout.WEST);

        pauseBtn.setFont(pauseBtn.getFont().deriveFont(12f));
        pauseBtn.setFocusPainted(false);
        pauseBtn.addActionListener(e ->
        {
            paused = !paused;
            pauseBtn.setText(paused ? "Resume" : "Pause");
            listener.onPauseToggled(paused);
        });
        header.add(pauseBtn, BorderLayout.EAST);

        // #215 (user ask, verbatim: "a high volume advisor tab and a tab for like a high
        // ticket advisor" + fill-time buckets): band chips narrow the SERVER-side pick.
        // Plain JButtons with explicit borders — the LAF's default JToggleButton chrome
        // ellipsized labels at this width (the Prices category row is the proven pattern).
        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        chips.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        chips.setBorder(new EmptyBorder(2, 6, 4, 6));
        String[] chipLabels = {"All", "Volume", "Whales"};
        String[] chipTips = {
            "Every profitable flip, best score first",
            "Liquid quantity plays — small margin, fills the GE limit fast",
            "Patient whales — one fill nets 1M+; thin by nature",
        };
        for (int i = 0; i < chipLabels.length; i++)
        {
            final int idx = i;
            JButton chip = new JButton(chipLabels[i]);
            chip.setFont(chip.getFont().deriveFont(11f));
            chip.setFocusPainted(false);
            chip.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            chip.setToolTipText(chipTips[i]);
            chip.addActionListener(e ->
            {
                selectedBand = BAND_VALUES[idx];
                syncChipStyles();
                listener.onFiltersChanged();
            });
            bandChips[i] = chip;
            chips.add(chip);
        }
        fastChip = new JButton("≤2h");
        fastChip.setFont(fastChip.getFont().deriveFont(11f));
        fastChip.setFocusPainted(false);
        fastChip.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        fastChip.setToolTipText("Only flips estimated to fill within ~2 hours");
        fastChip.addActionListener(e ->
        {
            fastFillsOnly = !fastFillsOnly;
            syncChipStyles();
            listener.onFiltersChanged();
        });
        chips.add(fastChip);
        syncChipStyles();

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        chips.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(header);
        north.add(chips);
        add(north, BorderLayout.NORTH);

        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        content.setBorder(new EmptyBorder(4, 8, 8, 8));
        content.setAlignmentX(Component.LEFT_ALIGNMENT);

        dumpFeed.setLayout(new BoxLayout(dumpFeed, BoxLayout.Y_AXIS));
        dumpFeed.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        dumpFeed.setBorder(new EmptyBorder(4, 8, 8, 8));
        dumpFeed.setAlignmentX(Component.LEFT_ALIGNMENT);

        // User report 2026-07-16 ("plugin seems to not scroll so it just forces the client
        // to expand"): this tab had NO viewport — a tall suggestion card plus dump-feed rows
        // grew the panel's preferred height unbounded. Same class as the width clamp shipped
        // the same morning: the PANEL scrolls, the client never resizes to fit us. The body
        // must be Scrollable — a plain JPanel view makes the scroll pane PREFER the full
        // content height, which is exactly the pathology (AdvisorScrollTest pins invariance).
        JPanel body = new ScrollBody();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        body.add(content);
        body.add(dumpFeed);
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        add(scroll, BorderLayout.CENTER);

        showMessage("Enable the Advisor in plugin config to get next-flip suggestions.");
    }

    public boolean isPaused()
    {
        return paused;
    }

    /** #215: the selected band chip — flip-bands key (throughput | patient_whale) or null for All. */
    public String getSelectedBand()
    {
        return selectedBand;
    }

    /** #215: 0 = no fill cap; the ≤2h chip caps the server pick at 120 estimated minutes. */
    public int getMaxFillMin()
    {
        return fastFillsOnly ? 120 : 0;
    }

    /** Selected chip = gold on panel; unselected = dim outline (mirrors the Prices category row). */
    private void syncChipStyles()
    {
        for (int i = 0; i < bandChips.length; i++)
        {
            if (bandChips[i] == null)
            {
                continue;
            }
            boolean sel = (BAND_VALUES[i] == null && selectedBand == null)
                || (BAND_VALUES[i] != null && BAND_VALUES[i].equals(selectedBand));
            applyChipStyle(bandChips[i], sel);
        }
        if (fastChip != null)
        {
            applyChipStyle(fastChip, fastFillsOnly);
        }
    }

    private static void applyChipStyle(JButton chip, boolean selected)
    {
        chip.setOpaque(true);
        if (selected)
        {
            chip.setBackground(GOLD);
            chip.setForeground(GfoPalette.PANEL);
            chip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GOLD),
                BorderFactory.createEmptyBorder(2, 7, 2, 7)));
        }
        else
        {
            chip.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            chip.setForeground(Color.LIGHT_GRAY);
            chip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DIM.darker()),
                BorderFactory.createEmptyBorder(2, 7, 2, 7)));
        }
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
                       + "<span style='color:#9A9A9A;'>for</span> <b style='color:white;'>" + formatGp(s.getPrice()) + "</b>";
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
        fill.setFont(fill.getFont().deriveFont(12f));
        fill.setFocusPainted(false);
        fill.setToolTipText("Auto-fill this price & quantity into the GE offer — open the offer to apply, you press Confirm");
        fill.addActionListener(e -> listener.onFillOffer(s.getItemId(), s.getPrice(), s.getQuantity()));
        buttons.add(fill);

        JButton skip = new JButton("Skip");
        skip.setFont(skip.getFont().deriveFont(12f));
        skip.setFocusPainted(false);
        skip.addActionListener(e -> listener.onSkip(s.getItemId()));
        buttons.add(skip);

        JButton block = new JButton("Block");
        block.setFont(block.getFont().deriveFont(12f));
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
            listRows = null;
            showMessage("No basket right now — your slots are full or capital is low. "
                + "Collect a finished offer and check back.");
            return;
        }
        listRows = basket;
        listIsNextMoves = false;
        renderRows();
    }

    /**
     * Render the stored list (basket or next-moves) honoring the one expanded row.
     * #215 items 5+6: compact rows by default, click a row for the in-place detail
     * card with per-item actions; chip refetches re-enter through show* and keep
     * the expansion when the item is still present.
     */
    private void renderRows()
    {
        List<Suggestion> rows = listRows;
        if (rows == null)
        {
            return;
        }
        content.removeAll();

        JLabel header = new JLabel((listIsNextMoves ? "Next moves (" : "Portfolio basket (") + rows.size() + ")");
        header.setForeground(GOLD);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(wrapLeft(header));
        content.add(Box.createVerticalStrut(2));

        JLabel sub = new JLabel(listIsNextMoves
            ? "Confidence-weighted, reasoned picks — sized to your coins"
            : "Coins spread across your free slots");
        sub.setForeground(DIM);
        sub.setFont(sub.getFont().deriveFont(12f));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(wrapLeft(sub));
        JLabel hint = new JLabel("Click a pick for detail & actions");
        hint.setForeground(DIM);
        hint.setFont(hint.getFont().deriveFont(10f));
        content.add(wrapLeft(hint));
        content.add(Box.createVerticalStrut(6));

        long totalSpend = 0;
        long totalProfit = 0;
        for (Suggestion s : rows)
        {
            if (s == null)
            {
                continue;
            }
            totalSpend += s.getPrice() * (long) s.getQuantity();
            totalProfit += s.getExpectedProfit();
            content.add(s.getItemId() == expandedItemId ? expandedRow(s) : compactRow(s));
            content.add(Box.createVerticalStrut(4));
        }

        content.add(Box.createVerticalStrut(2));
        JPanel sep = new JPanel();
        sep.setBackground(GfoPalette.BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(sep);
        content.add(Box.createVerticalStrut(4));

        content.add(meta("Total cost", formatGp(totalSpend)));
        content.add(metaColored("Est. profit", profitText(totalProfit), profitColor(totalProfit)));

        content.revalidate();
        content.repaint();
    }

    /** #215 item 5: expand/collapse one row's in-place detail (accordion — one open at a time). */
    void toggleDetail(int itemId)
    {
        expandedItemId = (expandedItemId == itemId) ? -1 : itemId;
        renderRows();
    }

    /**
     * First-run teaser (Advisor OFF): turn the old dead-end "enable it in config" message
     * into the value the tab exists for. Two states, chosen by the PLUGIN (this stays a
     * pure view): {@code flips == null} → the STATIC pitch (fresh install — the
     * enableServerFunctionality master switch is off, so NOTHING may be fetched; the
     * button flips that disclosed switch); non-null → the server's PUBLIC top flips
     * (fetched with no account and no game state) and the button enables the Advisor.
     */
    public void showFirstRun(List<Suggestion> flips, boolean serverEnabled, Runnable onEnable)
    {
        content.removeAll();

        JLabel header = new JLabel(serverEnabled ? "Top public flips right now" : "Your next flip, suggested");
        header.setForeground(GOLD);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
        content.add(wrapLeft(header));
        content.add(Box.createVerticalStrut(2));

        if (serverEnabled && flips != null && !flips.isEmpty())
        {
            JLabel sub = new JLabel("For a 10M stack · no account");
            sub.setForeground(DIM);
            sub.setFont(sub.getFont().deriveFont(12f));
            content.add(wrapLeft(sub));
            content.add(Box.createVerticalStrut(6));

            for (Suggestion s : flips)
            {
                if (s == null)
                {
                    continue;
                }
                JLabel name = new JLabel(s.getItemName());
                name.setForeground(Color.WHITE);
                name.setFont(name.getFont().deriveFont(Font.BOLD, 12f));
                content.add(wrapLeft(name));
                String line = !s.getReasons().isEmpty()
                    ? s.getReasons().get(0)
                    : ("Buy at " + formatGp(s.getPrice()));
                // HTML so long price lines WRAP inside the 240px sidebar instead of clipping
                // (a 31M item's "Buy at 31,409,340 -> Sell at 33,200,000" overflows a plain label).
                JLabel detail = new JLabel("<html><body style='width:185px'>" + line
                    + "  ·  " + profitText(s.getMarginPer()) + "/ea</body></html>");
                detail.setForeground(DIM);
                detail.setFont(detail.getFont().deriveFont(12f));
                content.add(wrapLeft(detail));
                content.add(Box.createVerticalStrut(4));
            }

            content.add(Box.createVerticalStrut(2));
            JPanel sep = new JPanel();
            sep.setBackground(GfoPalette.BORDER);
            sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            sep.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(sep);
            content.add(Box.createVerticalStrut(6));
        }

        String pitch = serverEnabled
            ? "<html><body style='width:180px'>Enable the Advisor for picks sized to <b>your</b> coins and free GE"
                + " slots. It sends your GE offers + approximate coin total to grandflipout.com —"
                + " nothing is ever placed for you.</body></html>"
            : "<html><body style='width:180px'>The Advisor suggests your next flip — item, buy/sell price,"
                + " quantity — sized to your coins and free GE slots. Turn on grandflipout.com"
                + " features to see today's top public flips (no account needed).</body></html>";
        JLabel pitchLabel = new JLabel(pitch);
        pitchLabel.setForeground(DIM);
        pitchLabel.setFont(pitchLabel.getFont().deriveFont(12f));
        content.add(wrapLeft(pitchLabel));
        content.add(Box.createVerticalStrut(6));

        JButton enable = new JButton(serverEnabled ? "Enable Advisor" : "Enable grandflipout.com features");
        enable.setFont(enable.getFont().deriveFont(Font.BOLD, 12f));
        enable.setFocusPainted(false);
        enable.addActionListener(e ->
        {
            if (serverEnabled)
            {
                // Advisor enable: the egress disclosure is the pitch text directly above,
                // and the WARNED master switch was already consented when it was turned on.
                onEnable.run();
                return;
            }
            // Fresh-install path flips the WARNED master switch — present the SAME
            // disclosure the RuneLite config panel and the Guide tab show, so consent
            // is equivalent whichever surface the player uses (GuidePanel lockstep rule;
            // programmatic setConfiguration bypasses ConfigPanel's warning dialog).
            int choice = javax.swing.JOptionPane.showConfirmDialog(this, GuidePanel.SERVER_DISCLOSURE,
                "Enable grandflipout.com features", javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);
            if (choice == javax.swing.JOptionPane.OK_OPTION)
            {
                onEnable.run();
            }
        });
        content.add(wrapLeft(enable));

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
            listRows = null;
            showMessage("No confident next move right now — capital is low, slots are full, or "
                + "the reasoner is holding back on weak signals. Check back after your next GE action.");
            return;
        }
        listRows = moves;
        listIsNextMoves = true;
        renderRows();
    }

    /**
     * The shared first two lines of a list row: name + action tag on top;
     * qty @ price + colored profit/loss on the second line.
     */
    private JPanel baseRow(Suggestion s)
    {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.setBorder(new EmptyBorder(4, 6, 4, 6));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JLabel name = new JLabel(s.getItemName());
        name.setForeground(Color.WHITE);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 13f));
        JLabel tag = new JLabel(s.getAction(), SwingConstants.RIGHT);
        tag.setForeground(Color.WHITE);
        tag.setOpaque(true);
        tag.setBackground(actionColor(s.getAction()));
        tag.setBorder(new EmptyBorder(1, 6, 1, 6));
        tag.setFont(tag.getFont().deriveFont(Font.BOLD, 11f));
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
        detail.setFont(detail.getFont().deriveFont(12f));
        JPanel detailWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        detailWrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
        detailWrap.add(detail);
        row.add(detailWrap);

        return row;
    }

    /**
     * #215 item 6: the default list row is the two base lines only — the
     * limit/volume/fill detail moved behind the click, so a full 8-slot basket
     * fits on screen. The tooltip carries the hover summary; the hand cursor
     * signals clickability.
     */
    private JPanel compactRow(Suggestion s)
    {
        JPanel row = baseRow(s);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        StringBuilder tip = new StringBuilder();
        if (s.getGeLimit() > 0)
        {
            tip.append(String.format("Limit %,d", s.getGeLimit()));
        }
        if (s.getVolume() > 0)
        {
            if (tip.length() > 0)
            {
                tip.append(" • ");
            }
            tip.append("vol ").append(formatVolume(s.getVolume()));
        }
        if (s.getEstFillMin() > 0)
        {
            if (tip.length() > 0)
            {
                tip.append(" • ");
            }
            tip.append(fillTime(s.getEstFillMin())).append(" fill");
        }
        row.setToolTipText(tip.length() > 0
            ? tip + " — click for actions"
            : "Click for detail & actions");
        attachToggle(row, s.getItemId());
        return row;
    }

    /**
     * #215 item 5: the in-place detail card — the single-suggestion treatment
     * (fill estimate, limit/volume, the per-cycle ceiling, honesty badges, reasons)
     * plus per-item Fill/Skip/Block, rendered inside the list like the last-slot
     * view. Clicking the row again collapses it; buttons keep their own clicks.
     */
    private JPanel expandedRow(Suggestion s)
    {
        JPanel row = baseRow(s);

        if (s.getEstFillMin() > 0)
        {
            row.add(rowMeta("Est. fill time", fillTime(s.getEstFillMin())));
        }
        if (s.getGeLimit() > 0)
        {
            row.add(rowMeta("Buy limit", String.format("%,d / 4h", s.getGeLimit())));
            row.add(rowMeta("At full limit", profitText(s.getProfitPerLimit())));
        }
        if (s.getVolume() > 0)
        {
            row.add(rowMeta("Volume (1h)", formatVolume(s.getVolume())));
        }
        if (s.getBandLabel() != null && !s.getBandLabel().isEmpty())
        {
            row.add(rowMeta("Style", s.getBandLabel()));
        }

        // Honesty badges — same server-graded conditions as the single card,
        // compacted to one wrapped line each (display-only, never re-derived).
        String tier = s.getPriceTier();
        if (tier != null && !"EXECUTABLE".equalsIgnoreCase(tier))
        {
            boolean noBook = "NO_ESTIMATE".equalsIgnoreCase(tier) || "NO_DATA".equalsIgnoreCase(tier);
            row.add(rowLine(noBook
                ? "• No live book — no defensible live price"
                : "• Stale book — prices are context, not quotes", GOLD));
        }
        String quality = s.getMarginQuality();
        if (quality != null && !"executable".equalsIgnoreCase(quality))
        {
            row.add(rowLine("no_estimate".equalsIgnoreCase(quality)
                ? "• Margin unquotable — no opposite-side price"
                : "• Margin is an estimate — one price leg is stale", GOLD));
        }
        for (String reason : s.getReasons())
        {
            row.add(rowLine("• " + reason, DIM));
        }

        row.add(Box.createVerticalStrut(4));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton fill = new JButton("Fill offer");
        fill.setFont(fill.getFont().deriveFont(11f));
        fill.setFocusPainted(false);
        fill.setToolTipText("Auto-fill this price & quantity into the GE offer — open the offer to apply, you press Confirm");
        fill.addActionListener(e -> listener.onFillOffer(s.getItemId(), s.getPrice(), s.getQuantity()));
        buttons.add(fill);
        JButton skip = new JButton("Skip");
        skip.setFont(skip.getFont().deriveFont(11f));
        skip.setFocusPainted(false);
        skip.setToolTipText("Not this one — refetch without it");
        skip.addActionListener(e -> listener.onSkip(s.getItemId()));
        buttons.add(skip);
        JButton block = new JButton("Block");
        block.setFont(block.getFont().deriveFont(11f));
        block.setFocusPainted(false);
        block.setToolTipText("Never suggest this item again");
        block.addActionListener(e -> listener.onBlock(s.getItemId()));
        buttons.add(block);
        row.add(buttons);

        // Pin the max height to the built content so BoxLayout can't stretch the
        // card into leftover viewport space (compact rows pin to 48 the same way).
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        attachToggle(row, s.getItemId());
        return row;
    }

    /** Hand cursor + click-to-toggle on a list row (buttons keep their own clicks). */
    private void attachToggle(JPanel row, int itemId)
    {
        row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        row.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                toggleDetail(itemId);
            }
        });
    }

    /** {@link #meta} variant on the DARK_GRAY row-card background. */
    private static JPanel rowMeta(String label, String value)
    {
        JPanel r = new JPanel(new BorderLayout());
        r.setBackground(ColorScheme.DARK_GRAY_COLOR);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        JLabel l = new JLabel(label);
        l.setForeground(DIM);
        l.setFont(l.getFont().deriveFont(11f));
        JLabel v = new JLabel(value, SwingConstants.RIGHT);
        v.setForeground(Color.WHITE);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 11f));
        r.add(l, BorderLayout.WEST);
        r.add(v, BorderLayout.EAST);
        return r;
    }

    /** Wrapped dim/caution line on the row card (HTML so it wraps at 242px). */
    private static JPanel rowLine(String text, Color color)
    {
        JLabel l = new JLabel("<html><div style='width:190px'>" + text + "</div></html>");
        l.setForeground(color);
        l.setFont(l.getFont().deriveFont(11f));
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setBackground(ColorScheme.DARK_GRAY_COLOR);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(l);
        return p;
    }

    /** "~35 min" under 90 minutes, "~2.1 h" above — the acquire-time estimate. */
    private static String fillTime(int minutes)
    {
        return minutes >= 90 ? String.format("~%.1f h", minutes / 60.0) : "~" + minutes + " min";
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
            header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
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
                name.setFont(name.getFont().deriveFont(12f));
                JLabel chg = new JLabel(formatPct(e.getPercentChange()), SwingConstants.RIGHT);
                chg.setForeground(e.getPercentChange() < 0 ? RED : DIM);
                chg.setFont(chg.getFont().deriveFont(Font.BOLD, 12f));
                top.add(name, BorderLayout.WEST);
                top.add(chg, BorderLayout.EAST);
                row.add(top);

                boolean enterable = (e.getSellTarget() != null && e.getNetMargin() != null && e.getNetMargin() > 0 &&
                        (e.getRecoveryProb() == null || e.getRecoveryProb() >= 0.05));
                if (enterable) {
                    JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                    bottom.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                    
                    // Compact figures — the full "Buy 20,500 gp • Sell 22,400 gp • Profit
                    // +1,600 gp/ea" line demanded ~310px inside the 242px sidebar, so the
                    // row overflowed/clipped ("the advisor stretches the client", 2026-07-16).
                    String text = "<html><span style='color:#9A9A9A; font-size:9px'>Buy </span><span style='color:white; font-size:9px'>"
                            + shortGp(e.getBuyPrice())
                            + "</span><span style='color:#9A9A9A; font-size:9px'> → sell </span><span style='color:white; font-size:9px'>"
                            + shortGp(e.getSellTarget())
                            + "</span><span style='color:#00D26A; font-size:9px'> · +"
                            + shortGp(e.getNetMargin()) + "/ea</span></html>";
                    
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
            note.setFont(note.getFont().deriveFont(10f));
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
            return GfoPalette.ACCENT_2;
        }
        if ("ABORT".equalsIgnoreCase(action))
        {
            return RED.darker();
        }
        return GfoPalette.TEXT_DIM;
    }

    private JPanel meta(String label, String value)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        JLabel l = new JLabel(label);
        l.setForeground(DIM);
        l.setFont(l.getFont().deriveFont(12f));
        JLabel v = new JLabel(value, SwingConstants.RIGHT);
        v.setForeground(Color.WHITE);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 12f));
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
        l.setFont(l.getFont().deriveFont(12f));
        JLabel v = new JLabel(value, SwingConstants.RIGHT);
        v.setForeground(valueColor);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 12f));
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

    /**
     * The advisor lives inside the 242px-capped sidebar panel (RuneLite's
     * PluginPanel.getPreferredSize hard-caps the outer panel). Demanding more —
     * 310px measured with a live suggestion — made the tab lay out wider than the
     * visible area, so rows clipped/overlapped: the "advisor stretches the client"
     * reports (2026-07-16). Clamp the demand to what the sidebar can actually give;
     * inner rows then wrap/ellipsize instead of painting past the edge.
     */
    @Override
    public java.awt.Dimension getPreferredSize()
    {
        java.awt.Dimension d = super.getPreferredSize();
        return new java.awt.Dimension(
            Math.min(d.width, net.runelite.client.ui.PluginPanel.PANEL_WIDTH), d.height);
    }

    @Override
    public java.awt.Dimension getMinimumSize()
    {
        java.awt.Dimension d = super.getMinimumSize();
        return new java.awt.Dimension(
            Math.min(d.width, net.runelite.client.ui.PluginPanel.PANEL_WIDTH), d.height);
    }

    private static String formatGp(long gp)
    {
        return String.format("%,d gp", gp);
    }

    /** Compact gp for tight rows (the 242px sidebar): 999 · 20.5k · 1.2M · 2.1B. */
    private static String shortGp(long gp)
    {
        long abs = Math.abs(gp);
        if (abs >= 1_000_000_000L) return trimZero(gp / 1_000_000_000.0) + "B";
        if (abs >= 1_000_000L) return trimZero(gp / 1_000_000.0) + "M";
        if (abs >= 1_000L) return trimZero(gp / 1_000.0) + "k";
        return String.valueOf(gp);
    }

    private static String trimZero(double v)
    {
        String s = String.format("%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /**
     * The scroll body: Scrollable so the viewport PREFERENCE is fixed regardless of how
     * many rows the card + dump feed hold — a plain JPanel view makes JScrollPane (and
     * therefore this whole tab, and therefore the CLIENT WINDOW) prefer the full content
     * height. Tracks viewport width (no horizontal scroll at 242px), never the height.
     */
    private static final class ScrollBody extends JPanel implements javax.swing.Scrollable
    {
        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            // Width is moot (getScrollableTracksViewportWidth pins it to the viewport);
            // the FIXED height is the whole point — content depth must never leak into
            // the tab's preferred size.
            return new Dimension(0, 420);
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
        {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
        {
            return 64;
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }
}
