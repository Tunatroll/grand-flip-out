/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.ui;

import java.awt.Font;

/**
 * The one place sidebar font sizes are decided ("have to squint" feedback):
 * every size is floored at 10px (the 8f/9f micro-captions were below
 * legibility) and offset by the user's Text size setting (+2 for Large).
 * The plugin sets the bump from config at startup and on config change;
 * panels route their deriveFont calls through here.
 */
public final class UiText
{
    /** No text the sidebar renders may be smaller than this. */
    static final float FLOOR = 10f;

    private static volatile float bump = 0f;

    private UiText()
    {
    }

    public static void setBump(float value)
    {
        bump = value;
    }

    /** Current bump — for the few inline-HTML px sizes that can't deriveFont. */
    static float bump()
    {
        return bump;
    }

    /** Mirror of {@code base.deriveFont(size)} — style preserved. */
    static Font font(Font base, float size)
    {
        return base.deriveFont(Math.max(FLOOR, size + bump));
    }

    /** Mirror of {@code base.deriveFont(style, size)}. */
    static Font font(Font base, int style, float size)
    {
        return base.deriveFont(style, Math.max(FLOOR, size + bump));
    }

    /**
     * Eyebrow captions and chip badges: fixed at the 10px floor in every text
     * mode — they are glanceable markers, and scaling them steals row width
     * from the values that actually carry the information.
     */
    static Font caption(Font base, int style)
    {
        return base.deriveFont(style, FLOOR);
    }
}
