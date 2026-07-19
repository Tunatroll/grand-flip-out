/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.model;

public enum PriceSource
{
    WIKI("OSRS Wiki"),
    RUNELITE("RuneLite"),
    OFFICIAL_GE("Official GE"),
    AGGREGATE("Aggregate");

    private final String displayName;

    PriceSource(String displayName)
    {
        this.displayName = displayName;
    }

    public String getDisplayName()
    {
        return displayName;
    }
}
