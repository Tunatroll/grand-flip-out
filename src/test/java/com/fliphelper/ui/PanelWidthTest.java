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
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.time.Instant;

import static org.junit.Assert.assertTrue;

/**
 * The sidebar-width contract ("client stretches without consent" investigation,
 * 2026-07-16): RuneLite's PluginPanel.getPreferredSize() hard-caps panel width at
 * 242px (225 wrapped) — bytecode-verified — so the panel cannot widen the sidebar
 * even with absurdly wide content. This test pins that contract against BOTH a
 * RuneLite behavior change and a local override: if it ever fails, opening or
 * updating the panel would physically widen the client window mid-session.
 */
public class PanelWidthTest
{
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

    @Test
    public void longContentNeverWidensThePanelPastTheSidebar() throws Exception
    {
        GrandFlipOutConfig config = mockConfig();
        Gson gson = new Gson();
        File dataDir = Files.createTempDirectory("gfo-widthtest").toFile();
        PriceService priceService = new PriceService(new OkHttpClient(), config, gson);
        FlipTracker flipTracker = new FlipTracker(config, priceService, dataDir, gson, Runnable::run);

        // An active flip whose name is absurdly wide — the stretch trigger class.
        flipTracker.recordTransaction(TradeRecord.builder()
            .itemId(999)
            .itemName("Extremely long test item name that would balloon a BoxLayout preferred width far past the sidebar")
            .quantity(100)
            .price(1_000_000L)
            .bought(true)
            .timestamp(Instant.now())
            .geSlot(0)
            .build());

        final int[] width = new int[1];
        SwingUtilities.invokeAndWait(() -> {
            GrandFlipOutPanel panel = new GrandFlipOutPanel(config, priceService, flipTracker);
            panel.updateFlipsTab();
            width[0] = panel.getPreferredSize().width;
        });

        int cap = PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH;
        assertTrue(
            "panel preferred width " + width[0] + " exceeds the sidebar cap " + cap
                + " — opening/updating the panel would physically widen the client window",
            width[0] <= cap);
    }

    @Test
    public void advisorContentNeverDemandsMoreThanTheSidebar() throws Exception
    {
        // The live report ("the advisor stretches the client", 2026-07-16): a rendered
        // suggestion + dump feed demanded 310px inside the 242px sidebar, so the tab
        // laid out past the visible edge (clipped/overlapping rows). AdvisorPanel now
        // clamps its preferred/minimum width to PluginPanel.PANEL_WIDTH.
        final int[] width = new int[2];
        SwingUtilities.invokeAndWait(() -> {
            AdvisorPanel ap = new AdvisorPanel(new AdvisorPanel.Listener()
            {
                public void onSkip(int id) { }
                public void onBlock(int id) { }
                public void onPauseToggled(boolean p) { }
                public void onFillOffer(int id, long price, int quantity) { }
                public void onFiltersChanged() { }
            });
            ap.showSuggestion(com.fliphelper.model.Suggestion.builder()
                .action("BUY").itemId(1163).itemName("Extremely long advisor item name for width purposes")
                .price(20_500_000L).quantity(70).expectedProfit(5_600_000L).confidence(0.78)
                .reasons(java.util.Arrays.asList(
                    "Margin clears the 2% tax", "Strong 1h volume", "Fresh price data"))
                .targetSlot(-1)
                .build());
            ap.showDumpFeed(java.util.Arrays.asList(
                new com.fliphelper.model.DumpFeedEntry(1163, "Rune full helm ornamented extra long",
                    false, 20_500_000L, 22_400_000L, 1_600_000L, 0.8, -9.1, "f2p"),
                new com.fliphelper.model.DumpFeedEntry(1127, "Rune platebody", false,
                    37_500_000L, 39_900_000L, 1_600_000L, 0.7, -6.4, "f2p")));
            width[0] = ap.getPreferredSize().width;
            width[1] = ap.getMinimumSize().width;
        });
        assertTrue("advisor preferred width " + width[0] + " exceeds PANEL_WIDTH "
            + PluginPanel.PANEL_WIDTH + " — the tab would lay out past the sidebar edge",
            width[0] <= PluginPanel.PANEL_WIDTH);
        assertTrue("advisor minimum width " + width[1] + " exceeds PANEL_WIDTH",
            width[1] <= PluginPanel.PANEL_WIDTH);
    }
}
