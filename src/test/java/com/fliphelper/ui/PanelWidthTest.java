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
}
