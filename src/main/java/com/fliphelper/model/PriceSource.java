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
