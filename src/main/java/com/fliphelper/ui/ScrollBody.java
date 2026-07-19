/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.ui;

import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;

/**
 * Scroll-pane body for the sidebar lists: tracks the viewport WIDTH so cards
 * lay out inside the 242px sidebar (a plain JPanel view is laid out at its own
 * preferred width — wide rows rendered past the panel edge, invisible),
 * and pins a fixed viewport-height PREFERENCE so content depth never leaks
 * into the tab's preferred size (the client-stretch class, 2026-07-16).
 */
class ScrollBody extends JPanel implements Scrollable
{
    private final int preferredViewportHeight;

    ScrollBody(int preferredViewportHeight)
    {
        this.preferredViewportHeight = preferredViewportHeight;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize()
    {
        // Width is moot (getScrollableTracksViewportWidth pins it to the viewport);
        // the FIXED height is the point — content depth must never leak upward.
        return new Dimension(0, preferredViewportHeight);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
    {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
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
