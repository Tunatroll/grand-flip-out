/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.ui;

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
import static org.junit.Assert.assertTrue;

/**
 * #215 items 5+6 (power-user batch): basket/next-moves rows are COMPACT by
 * default (no action buttons, no detail metas) and expand IN PLACE on toggle to
 * the full detail card with per-item Fill/Skip/Block — one row open at a time
 * (accordion), toggling the open row collapses it, and the actions dispatch the
 * row's own itemId. Sibling of AdvisorScrollTest (which pins that content depth
 * never leaks into the tab's preferred size — expanded cards ride the same
 * viewport contract).
 */
public class AdvisorBasketDetailTest
{
    /** Records the last action per callback so dispatch targets are assertable. */
    private static final class RecordingListener implements AdvisorPanel.Listener
    {
        int skipped = -1;
        int blocked = -1;
        int filled = -1;

        @Override public void onSkip(int itemId)
        {
            skipped = itemId;
        }

        @Override public void onBlock(int itemId)
        {
            blocked = itemId;
        }

        @Override public void onPauseToggled(boolean paused)
        {
        }

        @Override public void onFillOffer(int itemId, long price, int quantity)
        {
            filled = itemId;
        }

        @Override public void onFiltersChanged()
        {
        }
    }

    private static Suggestion sugg(int id, String name)
    {
        return Suggestion.builder()
            .action("BUY")
            .itemId(id)
            .itemName(name)
            .price(1_800_000L)
            .quantity(5)
            .marginPer(12_000L)
            .expectedProfit(60_000L)
            .geLimit(70)
            .profitPerLimit(840_000L)
            .volume(900L)
            .band("throughput")
            .bandLabel("Volume play")
            .estFillMin(35)
            .reasons(Arrays.asList("Fills both sides"))
            .build();
    }

    private static List<Suggestion> basket()
    {
        return Arrays.asList(sugg(4151, "Abyssal whip"), sugg(11832, "Bandos chestplate"), sugg(2434, "Prayer potion(4)"));
    }

    private static int countButtons(Container c, String text)
    {
        int n = 0;
        for (Component comp : c.getComponents())
        {
            if (comp instanceof JButton && text.equals(((JButton) comp).getText()))
            {
                n++;
            }
            if (comp instanceof Container)
            {
                n += countButtons((Container) comp, text);
            }
        }
        return n;
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
    public void rowsAreCompactByDefault() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            AdvisorPanel panel = new AdvisorPanel(new RecordingListener());
            panel.showBasket(basket());
            assertEquals("compact rows carry no action buttons", 0, countButtons(panel, "Fill offer"));
            assertEquals(0, countButtons(panel, "Skip"));
            assertFalse("detail metas live behind the click", hasLabelContaining(panel, "Est. fill time"));
        });
    }

    @Test
    public void toggleExpandsToDetailWithActions() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            AdvisorPanel panel = new AdvisorPanel(new RecordingListener());
            panel.showBasket(basket());
            panel.toggleDetail(4151);
            assertEquals("exactly one expanded card", 1, countButtons(panel, "Fill offer"));
            assertEquals(1, countButtons(panel, "Skip"));
            assertEquals(1, countButtons(panel, "Block"));
            assertTrue("fill estimate rendered", hasLabelContaining(panel, "~35 min"));
            assertTrue("band style rendered", hasLabelContaining(panel, "Volume play"));
        });
    }

    @Test
    public void accordionKeepsOneRowOpen() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            RecordingListener listener = new RecordingListener();
            AdvisorPanel panel = new AdvisorPanel(listener);
            panel.showBasket(basket());
            panel.toggleDetail(4151);
            panel.toggleDetail(11832);
            assertEquals("expanding a second row collapses the first", 1, countButtons(panel, "Skip"));
            findButton(panel, "Skip").doClick();
            assertEquals("actions target the row that is open", 11832, listener.skipped);
        });
    }

    @Test
    public void togglingTheOpenRowCollapsesIt() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            AdvisorPanel panel = new AdvisorPanel(new RecordingListener());
            panel.showBasket(basket());
            panel.toggleDetail(4151);
            panel.toggleDetail(4151);
            assertEquals(0, countButtons(panel, "Fill offer"));
        });
    }

    @Test
    public void actionsDispatchTheRowItemId() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            RecordingListener listener = new RecordingListener();
            AdvisorPanel panel = new AdvisorPanel(listener);
            panel.showBasket(basket());
            panel.toggleDetail(4151);
            findButton(panel, "Fill offer").doClick();
            findButton(panel, "Block").doClick();
            assertEquals(4151, listener.filled);
            assertEquals(4151, listener.blocked);
        });
    }

    @Test
    public void nextMovesUsesTheSameMechanics() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            AdvisorPanel panel = new AdvisorPanel(new RecordingListener());
            panel.showNextMoves(basket());
            assertEquals(0, countButtons(panel, "Fill offer"));
            panel.toggleDetail(2434);
            assertEquals(1, countButtons(panel, "Fill offer"));
            assertTrue(hasLabelContaining(panel, "Next moves (3)"));
        });
    }

    @Test
    public void expansionSurvivesARefetchOfTheSameList() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            AdvisorPanel panel = new AdvisorPanel(new RecordingListener());
            panel.showBasket(basket());
            panel.toggleDetail(4151);
            // A chip refetch re-enters showBasket with a fresh list — the open
            // row stays open when the item is still present.
            panel.showBasket(basket());
            assertEquals(1, countButtons(panel, "Fill offer"));
        });
    }
}
