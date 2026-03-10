package com.fliphelper.model;

public enum FlipState
{
    BUYING("Buying"),
    BOUGHT("Bought"),
    SELLING("Selling"),
    COMPLETE("Complete"),
    CANCELLED("Cancelled");

    private final String displayName;

    FlipState(String displayName)
    {
        this.displayName = displayName;
    }

    public String getDisplayName()
    {
        return displayName;
    }
}
