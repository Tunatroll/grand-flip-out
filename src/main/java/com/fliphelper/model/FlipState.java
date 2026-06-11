/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

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
