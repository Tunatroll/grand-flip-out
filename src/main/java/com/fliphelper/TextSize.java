/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper;

/**
 * Sidebar text-size setting: Standard keeps today's sizes (with the
 * 10px legibility floor); Large adds +2px to every panel label.
 */
public enum TextSize
{
    STANDARD("Standard", 0f),
    LARGE("Large", 2f);

    private final String label;
    private final float bump;

    TextSize(String label, float bump)
    {
        this.label = label;
        this.bump = bump;
    }

    public float bump()
    {
        return bump;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
