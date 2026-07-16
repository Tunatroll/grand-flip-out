/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.ui;

import com.fliphelper.model.DumpFeedEntry;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The Advisor-tab height contract (user report 2026-07-16: "plugin seems to not
 * scroll so it just forces the client to expand to fit the plugin tab size"):
 * the tab's preferred height must be INVARIANT to how much content the card and
 * dump feed hold — depth belongs to the scroll viewport, never to the client
 * window. Sibling of PanelWidthTest (the width leg of the same client-stretch
 * class, fixed the same morning).
 */
public class AdvisorScrollTest
{
    private static final AdvisorPanel.Listener NOOP = new AdvisorPanel.Listener()
    {
        @Override public void onSkip(int itemId) { }
        @Override public void onBlock(int itemId) { }
        @Override public void onPauseToggled(boolean paused) { }
        @Override public void onFillOffer(int itemId, long price, int quantity) { }
    };

    private static DumpFeedEntry entry(int i)
    {
        return new DumpFeedEntry(4151 + i, "Test item with a reasonably long name " + i,
            true, 1_000_000 + i, 1_100_000L + i, 78_000L, 0.82, -6.5, "high");
    }

    @Test
    public void preferredHeightDoesNotScaleWithContent() throws Exception
    {
        final int[] heights = new int[2];
        SwingUtilities.invokeAndWait(() ->
        {
            AdvisorPanel panel = new AdvisorPanel(NOOP);

            List<DumpFeedEntry> few = new ArrayList<>();
            few.add(entry(0));
            panel.showDumpFeed(few);
            heights[0] = panel.getPreferredSize().height;

            List<DumpFeedEntry> many = new ArrayList<>();
            for (int i = 0; i < 40; i++)
            {
                many.add(entry(i));
            }
            panel.showDumpFeed(many);
            heights[1] = panel.getPreferredSize().height;
        });

        assertTrue("panel must report a sane bounded height, got " + heights[1],
            heights[1] < 900);
        assertEquals("preferred height must not scale with dump-feed rows (client-stretch class)",
            heights[0], heights[1]);
    }
}
