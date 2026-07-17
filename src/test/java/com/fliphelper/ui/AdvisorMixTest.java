/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.ui;

import com.fliphelper.model.SlotLane;
import com.fliphelper.model.Suggestion;
import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * #215 item 4 plugin leg: the Mix chip + lane-grouped basket render. Mix is a
 * mode exclusive with the band/fast chips (selecting either side clears the
 * other); laneTagsFor zips the requested plan over the returned rows — headers
 * at lane starts, "Best overall" for unplanned remainder, and an explicit
 * fallback flag when a banded lane got a different-band substitute (the server
 * falls through rather than idling a slot — the render must stay honest).
 */
public class AdvisorMixTest
{
    private static final AdvisorPanel.Listener NOOP = new AdvisorPanel.Listener()
    {
        @Override public void onSkip(int itemId) { }
        @Override public void onBlock(int itemId) { }
        @Override public void onPauseToggled(boolean paused) { }
        @Override public void onFillOffer(int itemId, long price, int quantity) { }
        @Override public void onFiltersChanged() { }
    };

    private static Suggestion sugg(int id, String band)
    {
        return Suggestion.builder()
            .action("BUY").itemId(id).itemName("Item " + id)
            .price(1000L).quantity(10).marginPer(100L).expectedProfit(1000L)
            .band(band).bandLabel(band).estFillMin(5)
            .reasons(Arrays.asList("why")).build();
    }

    private static JButton findButton(Container c, String text)
    {
        for (Component comp : c.getComponents())
        {
            if (comp instanceof JButton && text.equals(((JButton) comp).getText()))
            {
                return (JButton) comp;
            }
            if (comp instanceof Container)
            {
                JButton b = findButton((Container) comp, text);
                if (b != null)
                {
                    return b;
                }
            }
        }
        return null;
    }

    private static boolean hasLabelContaining(Container c, String needle)
    {
        for (Component comp : c.getComponents())
        {
            if (comp instanceof JLabel)
            {
                String t = ((JLabel) comp).getText();
                if (t != null && t.contains(needle))
                {
                    return true;
                }
            }
            if (comp instanceof Container && hasLabelContaining((Container) comp, needle))
            {
                return true;
            }
        }
        return false;
    }

    @Test
    public void mixChipTogglesModeAndClearsOtherFilters() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            AdvisorPanel panel = new AdvisorPanel(NOOP);
            JButton mix = findButton(panel, "Mix");
            assertTrue("Mix chip exists in the chip row", mix != null);
            assertFalse(panel.isMixMode());

            // Select a band + fast fill, then Mix — mix clears both.
            findButton(panel, "Whales").doClick();
            findButton(panel, "≤2h").doClick();
            mix.doClick();
            assertTrue(panel.isMixMode());
            assertNull("mix clears the band filter", panel.getSelectedBand());
            assertEquals("mix clears the fast-fill cap", 0, panel.getMaxFillMin());

            // Selecting a band chip exits mix mode.
            findButton(panel, "Volume").doClick();
            assertFalse(panel.isMixMode());
            assertEquals("throughput", panel.getSelectedBand());
        });
    }

    @Test
    public void laneTagsHeadersAtLaneStartsAndRemainder()
    {
        List<SlotLane> plan = SlotLane.mixPlan(1, 0, 2, 8);
        List<Suggestion> rows = Arrays.asList(
            sugg(1, "throughput"), sugg(2, "patient_whale"), sugg(3, "patient_whale"), sugg(4, "standard"));
        List<AdvisorPanel.LaneTag> tags = AdvisorPanel.laneTagsFor(plan, rows);
        assertEquals(4, tags.size());
        assertEquals("High volume", tags.get(0).header);
        assertEquals("High ticket", tags.get(1).header);
        assertNull("same lane continues without a header", tags.get(2).header);
        assertEquals("unplanned remainder row is Best overall", "Best overall", tags.get(3).header);
        for (AdvisorPanel.LaneTag t : tags.subList(0, 3))
        {
            assertFalse("matching bands are not fallbacks", t.fallback);
        }
    }

    @Test
    public void laneTagsFlagBandFallbacks()
    {
        List<SlotLane> plan = SlotLane.mixPlan(0, 0, 2, 8);
        List<Suggestion> rows = Arrays.asList(sugg(1, "patient_whale"), sugg(2, "standard"));
        List<AdvisorPanel.LaneTag> tags = AdvisorPanel.laneTagsFor(plan, rows);
        assertFalse(tags.get(0).fallback);
        assertTrue("different-band substitute is flagged", tags.get(1).fallback);
        assertNull("fallback row stays inside its lane group", tags.get(1).header);
    }

    @Test
    public void showBasketRendersLaneHeadersAndFallbackMarker() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            AdvisorPanel panel = new AdvisorPanel(NOOP);
            List<SlotLane> plan = SlotLane.mixPlan(1, 0, 1, 8);
            List<Suggestion> rows = Arrays.asList(sugg(1, "throughput"), sugg(2, "standard"));
            panel.showBasket(rows, AdvisorPanel.laneTagsFor(plan, rows));
            assertTrue("volume lane header rendered", hasLabelContaining(panel, "High volume"));
            assertTrue("whale lane header rendered", hasLabelContaining(panel, "High ticket"));
            assertTrue("fallback substitute is marked", hasLabelContaining(panel, "best-overall fallback"));
        });
    }

    @Test
    public void plainShowBasketStaysUngrouped() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            AdvisorPanel panel = new AdvisorPanel(NOOP);
            panel.showBasket(Arrays.asList(sugg(1, "throughput"), sugg(2, "standard")));
            assertFalse("no lane headers without a plan", hasLabelContaining(panel, "High volume"));
            assertFalse(hasLabelContaining(panel, "Best overall"));
        });
    }
}
