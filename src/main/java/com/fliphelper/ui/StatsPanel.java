package com.fliphelper.ui;

import com.fliphelper.api.PriceService;
import com.fliphelper.model.PriceAggregate;
import com.fliphelper.model.TradeRecord;
import com.fliphelper.tracker.AccountDataManager;
import com.fliphelper.tracker.FlipTracker;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.QuantityFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;


public class StatsPanel extends JPanel
{
    // Colors matching the rest of the UI
    private static final Color BG_DARK = new Color(0x0F, 0x0F, 0x17);
    private static final Color BG_CARD = new Color(0x1A, 0x1A, 0x2E);
    private static final Color BG_CARD_ALT = new Color(0x16, 0x16, 0x25);
    private static final Color GOLD = new Color(0xFF, 0xB8, 0x00);
    private static final Color GREEN = new Color(0x00, 0xD2, 0x6A);
    private static final Color RED = new Color(0xFF, 0x47, 0x57);
    private static final Color BLUE = new Color(0x3B, 0x82, 0xF6);
    private static final Color DIM = new Color(0x60, 0x60, 0x80);
    private static final Color SEPARATOR = new Color(0x2A, 0x2A, 0x45);

    private final FlipTracker flipTracker;
    private final AccountDataManager accountDataManager;
    private final PriceService priceService;

    // UI
    private JPanel summaryPanel;
    private JPanel itemsContainer;
    private JComboBox<String> timeFilter;
    private JComboBox<String> sortBy;
    private JLabel totalProfitValue;
    private JLabel flipCountValue;
    private JLabel winRateValue;
    private JLabel gpHrValue;
    private JLabel bestFlipValue;
    private JLabel avgMarginValue;

    public StatsPanel(FlipTracker flipTracker, AccountDataManager accountDataManager,
                     PriceService priceService)
    {
        this.flipTracker = flipTracker;
        this.accountDataManager = accountDataManager;
        this.priceService = priceService;

        setLayout(new BorderLayout());
        setBackground(BG_DARK);

        add(buildControlBar(), BorderLayout.NORTH);
        add(buildMainContent(), BorderLayout.CENTER);
    }

    // [CONTROL BAR]

    private JPanel buildControlBar()
    {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBackground(BG_CARD);
        bar.setBorder(new EmptyBorder(8, 10, 8, 10));

        // Title
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JLabel title = new JLabel("Trading Stats");
        title.setForeground(GOLD);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        titleRow.add(title, BorderLayout.WEST);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setFont(refreshBtn.getFont().deriveFont(10f));
        refreshBtn.addActionListener(e -> refresh());
        titleRow.add(refreshBtn, BorderLayout.EAST);
        bar.add(titleRow);
        bar.add(Box.createVerticalStrut(6));

        // Filters row
        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filterRow.setOpaque(false);

        filterRow.add(makeLabel("Time:"));
        timeFilter = new JComboBox<>(new String[]{"All Time", "Today", "This Week", "Last 24h"});
        timeFilter.setFont(timeFilter.getFont().deriveFont(10f));
        timeFilter.addActionListener(e -> refresh());
        filterRow.add(timeFilter);

        filterRow.add(makeLabel("Sort:"));
        sortBy = new JComboBox<>(new String[]{"Profit", "Volume", "ROI", "Recent", "Flips"});
        sortBy.setFont(sortBy.getFont().deriveFont(10f));
        sortBy.addActionListener(e -> refresh());
        filterRow.add(sortBy);

        bar.add(filterRow);
        bar.add(Box.createVerticalStrut(6));

        // Summary cards row 1: Profit | GP/hr | Flips
        JPanel summary1 = new JPanel(new GridLayout(1, 3, 4, 0));
        summary1.setOpaque(false);

        totalProfitValue = new JLabel("0 gp");
        totalProfitValue.setForeground(GREEN);
        totalProfitValue.setFont(totalProfitValue.getFont().deriveFont(Font.BOLD, 13f));
        summary1.add(wrapStat("TOTAL PROFIT", totalProfitValue));

        gpHrValue = new JLabel("0/hr");
        gpHrValue.setForeground(GOLD);
        gpHrValue.setFont(gpHrValue.getFont().deriveFont(Font.BOLD, 13f));
        summary1.add(wrapStat("GP/HOUR", gpHrValue));

        flipCountValue = new JLabel("0");
        flipCountValue.setForeground(BLUE);
        flipCountValue.setFont(flipCountValue.getFont().deriveFont(Font.BOLD, 13f));
        summary1.add(wrapStat("FLIPS", flipCountValue));

        bar.add(summary1);
        bar.add(Box.createVerticalStrut(4));

        // Summary cards row 2: Win Rate | Avg Margin | Best Flip
        JPanel summary2 = new JPanel(new GridLayout(1, 3, 4, 0));
        summary2.setOpaque(false);

        winRateValue = new JLabel("—");
        winRateValue.setForeground(GREEN);
        winRateValue.setFont(winRateValue.getFont().deriveFont(Font.BOLD, 12f));
        summary2.add(wrapStat("WIN RATE", winRateValue));

        avgMarginValue = new JLabel("—");
        avgMarginValue.setForeground(Color.WHITE);
        avgMarginValue.setFont(avgMarginValue.getFont().deriveFont(Font.BOLD, 12f));
        summary2.add(wrapStat("AVG MARGIN", avgMarginValue));

        bestFlipValue = new JLabel("—");
        bestFlipValue.setForeground(GOLD);
        bestFlipValue.setFont(bestFlipValue.getFont().deriveFont(Font.BOLD, 12f));
        summary2.add(wrapStat("BEST FLIP", bestFlipValue));

        bar.add(summary2);
        bar.add(Box.createVerticalStrut(4));

        // Row 3: Profit projections based on current GP/hr
        JPanel projRow = new JPanel(new GridLayout(1, 3, 4, 0));
        projRow.setOpaque(false);
        projRow.setBackground(new Color(0x0A, 0x1A, 0x0A)); // Subtle green tint

        JLabel proj1hLabel = new JLabel("—");
        proj1hLabel.setName("proj1h");
        proj1hLabel.setForeground(GREEN);
        proj1hLabel.setFont(proj1hLabel.getFont().deriveFont(Font.BOLD, 11f));
        projRow.add(wrapStat("PROJ 1HR", proj1hLabel));

        JLabel proj4hLabel = new JLabel("—");
        proj4hLabel.setName("proj4h");
        proj4hLabel.setForeground(GREEN);
        proj4hLabel.setFont(proj4hLabel.getFont().deriveFont(Font.BOLD, 11f));
        projRow.add(wrapStat("PROJ 4HR", proj4hLabel));

        JLabel projDayLabel = new JLabel("—");
        projDayLabel.setName("projDay");
        projDayLabel.setForeground(GOLD);
        projDayLabel.setFont(projDayLabel.getFont().deriveFont(Font.BOLD, 11f));
        projRow.add(wrapStat("PROJ/DAY", projDayLabel));

        bar.add(projRow);

        // Separator
        bar.add(Box.createVerticalStrut(6));
        JSeparator sep = new JSeparator();
        sep.setForeground(SEPARATOR);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        bar.add(sep);

        return bar;
    }

    // --- MAIN CONTENT ---

    private JScrollPane buildMainContent()
    {
        itemsContainer = new JPanel();
        itemsContainer.setLayout(new BoxLayout(itemsContainer, BoxLayout.Y_AXIS));
        itemsContainer.setBackground(BG_DARK);

        JScrollPane scroll = new JScrollPane(itemsContainer);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBorder(null);
        return scroll;
    }

    // -- REFRESH / DATA

    public void refresh()
    {
        SwingUtilities.invokeLater(() -> {
            List<com.fliphelper.model.FlipItem> flips = getFilteredFlips();
            updateSummary(flips);
            updateItemCards(flips);
        });
    }

    private List<com.fliphelper.model.FlipItem> getFilteredFlips()
    {
        List<com.fliphelper.model.FlipItem> all = flipTracker.getCompletedFlips();
        String filter = (String) timeFilter.getSelectedItem();
        if (filter == null || "All Time".equals(filter))
        {
            return all;
        }

        Instant cutoff;
        switch (filter)
        {
            case "Today":
                cutoff = Instant.now().minus(Duration.ofHours(
                    java.time.LocalTime.now().getHour())).minus(Duration.ofMinutes(
                    java.time.LocalTime.now().getMinute()));
                break;
            case "This Week":
                cutoff = Instant.now().minus(Duration.ofDays(7));
                break;
            case "Last 24h":
                cutoff = Instant.now().minus(Duration.ofHours(24));
                break;
            default:
                return all;
        }

        return all.stream()
            .filter(f -> f.getSellTime() != null && f.getSellTime().isAfter(cutoff))
            .collect(Collectors.toList());
    }

    private void updateSummary(List<com.fliphelper.model.FlipItem> flips)
    {
        long totalProfit = flips.stream().mapToLong(com.fliphelper.model.FlipItem::getProfit).sum();
        int count = flips.size();
        long wins = flips.stream().filter(f -> f.getProfit() >= 0).count();
        double winRate = count > 0 ? (double) wins / count * 100 : 0;

        totalProfitValue.setText(formatGp(totalProfit));
        totalProfitValue.setForeground(totalProfit >= 0 ? GREEN : RED);

        flipCountValue.setText(String.valueOf(count));

        if (count > 0)
        {
            winRateValue.setText(String.format("%.0f%%", winRate));
            winRateValue.setForeground(winRate >= 60 ? GREEN : winRate >= 40 ? GOLD : RED);
        }
        else
        {
            winRateValue.setText("—");
        }

        // GP/hr from session tracker
        double gphr = flipTracker.getGpPerHour();
        gpHrValue.setText(formatGp((long) gphr) + "/hr");

        // Avg margin across all flips
        if (count > 0)
        {
            long avgMargin = flips.stream()
                .filter(f -> f.getBuyPrice() > 0 && f.getSellPrice() > 0)
                .mapToLong(f -> f.getSellPrice() - f.getBuyPrice())
                .average().isPresent() ?
                (long) flips.stream()
                    .filter(f -> f.getBuyPrice() > 0 && f.getSellPrice() > 0)
                    .mapToLong(f -> f.getSellPrice() - f.getBuyPrice())
                    .average().getAsDouble() : 0;
            avgMarginValue.setText(formatGp(avgMargin));
        }

        // Best flip
        long best = flips.stream().mapToLong(com.fliphelper.model.FlipItem::getProfit).max().orElse(0);
        bestFlipValue.setText(formatGp(best));

        // Profit projections
        if (gphr > 0)
        {
            updateProjectionLabel("proj1h", formatGp((long) gphr));
            updateProjectionLabel("proj4h", formatGp((long) (gphr * 4)));
            updateProjectionLabel("projDay", formatGp((long) (gphr * 24)));
        }
        else
        {
            updateProjectionLabel("proj1h", "—");
            updateProjectionLabel("proj4h", "—");
            updateProjectionLabel("projDay", "—");
        }
    }

    private void updateItemCards(List<com.fliphelper.model.FlipItem> flips)
    {
        itemsContainer.removeAll();

        if (flips.isEmpty())
        {
            JLabel empty = new JLabel("No flips recorded yet. Start trading!");
            empty.setForeground(DIM);
            empty.setBorder(new EmptyBorder(20, 12, 20, 12));
            itemsContainer.add(empty);
            itemsContainer.revalidate();
            itemsContainer.repaint();
            return;
        }

        // Aggregate per item
        Map<Integer, ItemAgg> aggMap = new LinkedHashMap<>();
        for (com.fliphelper.model.FlipItem flip : flips)
        {
            if (!flip.isComplete()) continue;
            aggMap.computeIfAbsent(flip.getItemId(),
                k -> new ItemAgg(flip.getItemId(), flip.getItemName())).addFlip(flip);
        }

        // Sort
        List<ItemAgg> sorted = new ArrayList<>(aggMap.values());
        String sortKey = (String) sortBy.getSelectedItem();
        switch (sortKey != null ? sortKey : "Profit")
        {
            case "Volume":
                sorted.sort(Comparator.comparingInt(a -> -a.totalQty));
                break;
            case "ROI":
                sorted.sort(Comparator.comparingDouble(a -> -a.getAvgRoi()));
                break;
            case "Recent":
                sorted.sort(Comparator.comparing(a -> a.lastFlipTime, Comparator.reverseOrder()));
                break;
            case "Flips":
                sorted.sort(Comparator.comparingInt(a -> -a.flipCount));
                break;
            default: // Profit
                sorted.sort(Comparator.comparingLong(a -> -a.totalProfit));
                break;
        }

        // Build cards
        int rank = 0;
        for (ItemAgg agg : sorted)
        {
            rank++;
            itemsContainer.add(buildItemStatsCard(agg, rank));
            if (rank < sorted.size())
            {
                JSeparator sep = new JSeparator();
                sep.setForeground(SEPARATOR);
                sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                itemsContainer.add(sep);
            }
        }

        itemsContainer.revalidate();
        itemsContainer.repaint();
    }

    // PER-ITEM STATS CARD

    private JPanel buildItemStatsCard(ItemAgg agg, int rank)
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(rank % 2 == 0 ? BG_CARD_ALT : BG_CARD);
        card.setBorder(new EmptyBorder(8, 10, 8, 10));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 145));

        // Row 1: #rank + Item Name + Total Profit (colored)
        JPanel row1 = new JPanel(new BorderLayout());
        row1.setOpaque(false);

        JPanel nameGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        nameGroup.setOpaque(false);
        JLabel rankLabel = new JLabel("#" + rank + " ");
        rankLabel.setForeground(DIM);
        rankLabel.setFont(rankLabel.getFont().deriveFont(Font.BOLD, 10f));
        nameGroup.add(rankLabel);
        JLabel nameLabel = new JLabel(agg.itemName);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        nameGroup.add(nameLabel);
        row1.add(nameGroup, BorderLayout.WEST);

        // Profit + ROI badge
        JPanel profitGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        profitGroup.setOpaque(false);
        String profitStr = (agg.totalProfit >= 0 ? "+" : "") + formatGp(agg.totalProfit);
        JLabel profitLabel = new JLabel(profitStr);
        profitLabel.setForeground(agg.totalProfit >= 0 ? GREEN : RED);
        profitLabel.setFont(profitLabel.getFont().deriveFont(Font.BOLD, 12f));
        profitGroup.add(profitLabel);

        JLabel roiBadge = new JLabel(String.format(" %.1f%% ", agg.getAvgRoi()));
        roiBadge.setForeground(Color.WHITE);
        roiBadge.setOpaque(true);
        roiBadge.setBackground(agg.getAvgRoi() >= 0
            ? new Color(0x00, 0x6A, 0x35) : new Color(0x6A, 0x00, 0x00));
        roiBadge.setFont(roiBadge.getFont().deriveFont(Font.BOLD, 9f));
        profitGroup.add(roiBadge);
        row1.add(profitGroup, BorderLayout.EAST);
        card.add(row1);
        card.add(Box.createVerticalStrut(4));

        // Row 2: Avg Buy | Avg Sell | Avg Margin | Avg Margin %
        JPanel row2 = new JPanel(new GridLayout(1, 4, 4, 0));
        row2.setOpaque(false);
        row2.add(statCell("AVG BUY", formatGp(agg.avgBuyPrice), GREEN));
        row2.add(statCell("AVG SELL", formatGp(agg.avgSellPrice), RED));
        row2.add(statCell("AVG MARGIN", formatGp(agg.getAvgMargin()), GOLD));
        row2.add(statCell("MARGIN %", String.format("%.1f%%", agg.getAvgMarginPercent()), GOLD));
        card.add(row2);
        card.add(Box.createVerticalStrut(3));

        // Row 3: Volume | Flips | Avg Profit/Flip | Avg Duration
        JPanel row3 = new JPanel(new GridLayout(1, 4, 4, 0));
        row3.setOpaque(false);
        row3.add(statCell("VOLUME", QuantityFormatter.formatNumber(agg.totalQty) + "x", Color.LIGHT_GRAY));
        row3.add(statCell("FLIPS", String.valueOf(agg.flipCount), BLUE));
        row3.add(statCell("AVG/FLIP", formatGp(agg.getAvgProfitPerFlip()), Color.WHITE));
        row3.add(statCell("AVG TIME", formatDuration(agg.getAvgDuration()), Color.LIGHT_GRAY));
        card.add(row3);
        card.add(Box.createVerticalStrut(3));

        // Row 4: Current price vs your avg buy (are you buying cheap?)
        // + Win rate for this specific item + Tax paid
        JPanel row4 = new JPanel(new GridLayout(1, 3, 4, 0));
        row4.setOpaque(false);

        // Current market price comparison
        PriceAggregate currentPrice = priceService != null ? priceService.getPrice(agg.itemId) : null;
        if (currentPrice != null && currentPrice.getBestLowPrice() > 0)
        {
            long currentBuy = currentPrice.getBestLowPrice();
            long diff = currentBuy - agg.avgBuyPrice;
            String diffStr = (diff >= 0 ? "+" : "") + formatGp(diff);
            Color diffColor = diff <= 0 ? GREEN : RED; // Lower = better for buying
            row4.add(statCell("NOW vs AVG BUY", diffStr, diffColor));
        }
        else
        {
            row4.add(statCell("NOW vs AVG BUY", "—", DIM));
        }

        double itemWinRate = agg.flipCount > 0 ? (double) agg.wins / agg.flipCount * 100 : 0;
        row4.add(statCell("WIN RATE", String.format("%.0f%%", itemWinRate),
            itemWinRate >= 70 ? GREEN : itemWinRate >= 50 ? GOLD : RED));
        row4.add(statCell("TAX PAID", formatGp(agg.totalTax), new Color(0xFF, 0x8C, 0x00)));
        card.add(row4);
        card.add(Box.createVerticalStrut(3));

        // Row 5: Profit Trend + Best Single Flip + Projected Daily
        JPanel row5 = new JPanel(new GridLayout(1, 3, 4, 0));
        row5.setOpaque(false);

        // Trend: is this item getting more or less profitable over time?
        String trendText = "—";
        Color trendColor = DIM;
        if (agg.flipCount >= 3)
        {
            double trend = agg.getProfitTrend();
            if (trend > 5)
            {
                trendText = "\u2191 Improving";
                trendColor = GREEN;
            }
            else if (trend < -5)
            {
                trendText = "\u2193 Declining";
                trendColor = RED;
            }
            else
            {
                trendText = "\u2192 Stable";
                trendColor = GOLD;
            }
        }
        row5.add(statCell("TREND", trendText, trendColor));

        // Best single flip for this item
        row5.add(statCell("BEST FLIP", formatGp(agg.bestSingleFlip), GOLD));

        // Projected daily if you kept flipping this item at current rate
        if (agg.getAvgDuration() > 0 && agg.getAvgProfitPerFlip() > 0)
        {
            long flipsPerDay = 86400 / Math.max(agg.getAvgDuration(), 60);
            long projectedDaily = flipsPerDay * agg.getAvgProfitPerFlip();
            row5.add(statCell("PROJ/DAY", formatGp(projectedDaily), GREEN));
        }
        else
        {
            row5.add(statCell("PROJ/DAY", "—", DIM));
        }
        card.add(row5);

        return card;
    }

    // ~~~ ITEM AGGREGATION MODEL ~~~

    private static class ItemAgg
    {
        final int itemId;
        final String itemName;
        long totalBuySpend = 0;     // sum of (buyPrice * qty) across all flips
        long totalSellRevenue = 0;  // sum of (sellPrice * qty) across all flips
        long totalProfit = 0;
        long totalTax = 0;
        int totalQty = 0;           // total items flipped
        int flipCount = 0;
        int wins = 0;
        long avgBuyPrice = 0;
        long avgSellPrice = 0;
        long totalDurationSeconds = 0;
        long bestSingleFlip = 0;
        Instant lastFlipTime = Instant.EPOCH;
        // Track recent profits for trend analysis
        final List<Long> recentProfits = new ArrayList<>();

        ItemAgg(int itemId, String itemName)
        {
            this.itemId = itemId;
            this.itemName = itemName;
        }

        void addFlip(com.fliphelper.model.FlipItem flip)
        {
            int qty = flip.getQuantity();
            totalBuySpend += flip.getBuyPrice() * qty;
            totalSellRevenue += flip.getSellPrice() * qty;
            totalProfit += flip.getProfit();
            totalTax += flip.getTax();
            totalQty += qty;
            flipCount++;
            totalDurationSeconds += flip.getFlipDurationSeconds();

            if (flip.getProfit() >= 0) wins++;
            if (flip.getProfit() > bestSingleFlip) bestSingleFlip = flip.getProfit();
            recentProfits.add(flip.getProfit());

            if (flip.getSellTime() != null && flip.getSellTime().isAfter(lastFlipTime))
            {
                lastFlipTime = flip.getSellTime();
            }

            // Recalculate averages
            avgBuyPrice = totalQty > 0 ? totalBuySpend / totalQty : 0;
            avgSellPrice = totalQty > 0 ? totalSellRevenue / totalQty : 0;
        }

        long getAvgMargin() { return avgSellPrice - avgBuyPrice; }

        double getAvgMarginPercent()
        {
            return avgBuyPrice > 0 ? (double) getAvgMargin() / avgBuyPrice * 100 : 0;
        }

        long getAvgProfitPerFlip()
        {
            return flipCount > 0 ? totalProfit / flipCount : 0;
        }

        double getAvgRoi()
        {
            return totalBuySpend > 0 ? (double) totalProfit / totalBuySpend * 100 : 0;
        }

        long getAvgDuration()
        {
            return flipCount > 0 ? totalDurationSeconds / flipCount : 0;
        }

        
        double getProfitTrend()
        {
            if (recentProfits.size() < 3) return 0;
            int mid = recentProfits.size() / 2;
            double firstHalf = recentProfits.subList(0, mid).stream()
                .mapToLong(Long::longValue).average().orElse(0);
            double secondHalf = recentProfits.subList(mid, recentProfits.size()).stream()
                .mapToLong(Long::longValue).average().orElse(0);
            if (Math.abs(firstHalf) < 1) return 0;
            return ((secondHalf - firstHalf) / Math.abs(firstHalf)) * 100;
        }
    }

    /* HELPERS */

    private void updateProjectionLabel(String name, String text)
    {
        // Walk the component tree to find the label by name
        JLabel label = findLabelByName(this, name);
        if (label != null) label.setText(text);
    }

    private JLabel findLabelByName(Container container, String name)
    {
        for (Component c : container.getComponents())
        {
            if (c instanceof JLabel && name.equals(c.getName()))
            {
                return (JLabel) c;
            }
            if (c instanceof Container)
            {
                JLabel found = findLabelByName((Container) c, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private JPanel statCell(String title, String value, Color valueColor)
    {
        JPanel cell = new JPanel(new BorderLayout(0, 1));
        cell.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setForeground(DIM);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 7f));
        cell.add(t, BorderLayout.NORTH);
        JLabel v = new JLabel(value);
        v.setForeground(valueColor);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 10f));
        cell.add(v, BorderLayout.CENTER);
        return cell;
    }

    private JPanel wrapStat(String title, JLabel valueLabel)
    {
        JPanel card = new JPanel(new BorderLayout(0, 2));
        card.setBackground(new Color(0x1A, 0x1A, 0x2E));
        card.setBorder(new EmptyBorder(4, 6, 4, 6));
        JLabel t = new JLabel(title);
        t.setForeground(DIM);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 8f));
        card.add(t, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JLabel makeLabel(String text)
    {
        JLabel l = new JLabel(text);
        l.setForeground(DIM);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 10f));
        return l;
    }

    private String formatGp(long amount)
    {
        return String.format("%,d", amount);
    }

    private String formatDuration(long seconds)
    {
        if (seconds <= 0) return "N/A";
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }
}
