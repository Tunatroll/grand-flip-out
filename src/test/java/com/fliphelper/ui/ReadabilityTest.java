/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.ui;

import com.fliphelper.GrandFlipOutConfig;
import com.fliphelper.api.PriceService;
import com.fliphelper.model.TradeRecord;
import com.fliphelper.tracker.FlipTracker;
import com.google.gson.Gson;
import net.runelite.client.ui.PluginPanel;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Test;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * #190 churn-feedback leg 1 ("the size of the items in plugin. have to squint
 * to see anything"): the sidebar's typography must clear a legibility floor,
 * and the opt-in Large text mode must scale row text without breaching the
 * 242px sidebar cap that PanelWidthTest pins.
 */
public class ReadabilityTest
{
    private static final float FLOOR = 10f;

    @After
    public void resetScale()
    {
        UiText.setBump(0f);
    }

    private static GrandFlipOutConfig mockConfig()
    {
        return (GrandFlipOutConfig) Proxy.newProxyInstance(
            GrandFlipOutConfig.class.getClassLoader(),
            new Class<?>[]{GrandFlipOutConfig.class},
            (proxy, method, mArgs) -> {
                if (method.isDefault())
                {
                    return java.lang.invoke.MethodHandles
                        .privateLookupIn(GrandFlipOutConfig.class, java.lang.invoke.MethodHandles.lookup())
                        .unreflectSpecial(method, GrandFlipOutConfig.class)
                        .bindTo(proxy)
                        .invokeWithArguments(mArgs == null ? new Object[0] : mArgs);
                }
                Class<?> rt = method.getReturnType();
                if (rt == boolean.class) return false;
                if (rt == int.class) return 0;
                if (rt == long.class) return 0L;
                if (rt == double.class) return 0.0;
                if (rt == String.class) return "";
                if ("toString".equals(method.getName())) return "MockConfig";
                if ("hashCode".equals(method.getName())) return 0;
                if ("equals".equals(method.getName())) return proxy == (mArgs != null ? mArgs[0] : null);
                return null;
            });
    }

    private static GrandFlipOutPanel builtPanel() throws Exception
    {
        GrandFlipOutConfig config = mockConfig();
        Gson gson = new Gson();
        File dataDir = Files.createTempDirectory("gfo-readability").toFile();
        PriceService priceService = new PriceService(new OkHttpClient(), config, gson);
        FlipTracker flipTracker = new FlipTracker(config, priceService, dataDir, gson, Runnable::run);
        flipTracker.recordTransaction(TradeRecord.builder()
            .itemId(4151)
            .itemName("Abyssal whip")
            .quantity(5)
            .price(1_500_000L)
            .bought(true)
            .timestamp(Instant.now())
            .geSlot(0)
            .build());

        GrandFlipOutPanel panel = new GrandFlipOutPanel(config, priceService, flipTracker);
        panel.updateFlipsTab();
        panel.updateHistoryTab(true);
        return panel;
    }

    private static void collectText(Container root, List<JComponent> out)
    {
        for (Component c : root.getComponents())
        {
            if (c instanceof JLabel || c instanceof AbstractButton)
            {
                out.add((JComponent) c);
            }
            if (c instanceof Container)
            {
                collectText((Container) c, out);
            }
        }
    }

    private static String describe(JComponent c)
    {
        String text = c instanceof JLabel ? ((JLabel) c).getText()
            : ((AbstractButton) c).getText();
        return c.getClass().getSimpleName() + " \"" + text + "\" @ " + c.getFont().getSize2D() + "px";
    }

    /**
     * Every label and button the sidebar renders must be at least 10px — the 8f
     * meta captions (Qty/Buy/Slot) and 9f badge/stat titles were below any
     * reasonable legibility line and are exactly what the churned user squinted at.
     */
    @Test
    public void everySidebarLabelClearsTheLegibilityFloor() throws Exception
    {
        final List<String> offenders = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            try
            {
                GrandFlipOutPanel panel = builtPanel();
                List<JComponent> texts = new ArrayList<>();
                collectText(panel, texts);
                assertTrue("panel rendered no text at all — walk is broken", texts.size() > 20);
                for (JComponent c : texts)
                {
                    if (c.getFont().getSize2D() < FLOOR)
                    {
                        offenders.add(describe(c));
                    }
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        });
        assertTrue("labels below the " + FLOOR + "px legibility floor: " + offenders, offenders.isEmpty());
    }

    /**
     * Large mode (+2px) must actually grow row text AND keep every width
     * contract: the panel stays inside the sidebar cap (the PanelWidthTest
     * invariant re-checked under the bigger fonts).
     */
    @Test
    public void largeModeGrowsTextWithoutBreachingTheSidebarCap() throws Exception
    {
        UiText.setBump(2f);
        final float[] nameSize = new float[1];
        final int[] width = new int[1];
        SwingUtilities.invokeAndWait(() -> {
            try
            {
                GrandFlipOutPanel panel = builtPanel();
                List<JComponent> texts = new ArrayList<>();
                collectText(panel, texts);
                for (JComponent c : texts)
                {
                    if (c instanceof JLabel && "Abyssal whip".equals(((JLabel) c).getText()))
                    {
                        nameSize[0] = Math.max(nameSize[0], c.getFont().getSize2D());
                    }
                }
                width[0] = panel.getPreferredSize().width;
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        });
        assertTrue("flip-card item name should render at >= 15px under Large (13f + 2), saw "
            + nameSize[0], nameSize[0] >= 15f);
        int cap = PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH;
        assertTrue("large-mode panel preferred width " + width[0] + " exceeds the sidebar cap " + cap,
            width[0] <= cap);
    }

    /**
     * Advisor compact rows must never clip their own content when fonts grow:
     * the max-height pin has to be content-derived, not a hardcoded 48.
     */
    @Test
    public void advisorCompactRowsNeverClipUnderLargeMode() throws Exception
    {
        UiText.setBump(2f);
        final List<String> clipped = new ArrayList<>();
        final int[] width = new int[1];
        SwingUtilities.invokeAndWait(() -> {
            AdvisorPanel ap = new AdvisorPanel(new AdvisorPanel.Listener()
            {
                public void onSkip(int id) { }
                public void onBlock(int id) { }
                public void onPauseToggled(boolean p) { }
                public void onFillOffer(int id, long price, int quantity) { }
                public void onFiltersChanged() { }
            });
            ap.showBasket(java.util.Arrays.asList(
                com.fliphelper.model.Suggestion.builder()
                    .action("BUY").itemId(4151).itemName("Abyssal whip")
                    .price(1_500_000L).quantity(5).expectedProfit(120_000L).confidence(0.8)
                    .reasons(java.util.Arrays.asList("Margin clears the 2% tax"))
                    .targetSlot(-1).geLimit(70).volume(4000).estFillMin(12)
                    .build(),
                com.fliphelper.model.Suggestion.builder()
                    .action("BUY").itemId(1163).itemName("Rune full helm")
                    .price(20_500L).quantity(70).expectedProfit(56_000L).confidence(0.7)
                    .reasons(java.util.Arrays.asList("Strong 1h volume"))
                    .targetSlot(-1).geLimit(125).volume(9000).estFillMin(8)
                    .build()));
            width[0] = ap.getPreferredSize().width;
            List<JComponent> rows = new ArrayList<>();
            collectRows(ap, rows);
            for (JComponent row : rows)
            {
                int pref = row.getPreferredSize().height;
                int max = row.getMaximumSize().height;
                if (max < pref)
                {
                    clipped.add(row.getClass().getSimpleName() + " pref=" + pref + " max=" + max);
                }
            }
        });
        assertTrue("advisor rows whose max-height pin clips their own content under large text: "
            + clipped, clipped.isEmpty());
        assertTrue("large-mode advisor width " + width[0] + " exceeds PANEL_WIDTH",
            width[0] <= PluginPanel.PANEL_WIDTH);
    }

    private static void collectRows(Container root, List<JComponent> out)
    {
        for (Component c : root.getComponents())
        {
            if (c instanceof JComponent)
            {
                JComponent jc = (JComponent) c;
                // text rows given an explicit max-height pin — 1-2px separator
                // rules carry pins by design and hold no text, so require a
                // label/button descendant before treating the pin as a clip risk
                if (jc.getMaximumSize() != null && jc.getMaximumSize().width == Integer.MAX_VALUE
                    && jc.getMaximumSize().height < Integer.MAX_VALUE
                    && containsText(jc))
                {
                    out.add(jc);
                }
            }
            if (c instanceof Container)
            {
                collectRows((Container) c, out);
            }
        }
    }

    private static boolean containsText(Container root)
    {
        for (Component c : root.getComponents())
        {
            if (c instanceof JLabel || c instanceof AbstractButton)
            {
                return true;
            }
            if (c instanceof Container && containsText((Container) c))
            {
                return true;
            }
        }
        return false;
    }
}
