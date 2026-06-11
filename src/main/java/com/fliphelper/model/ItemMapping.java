/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.model;

import lombok.Data;

/**
 * Maps item IDs to metadata from the OSRS Wiki mapping endpoint.
 */
@Data
public class ItemMapping
{
    private int id;
    private String name;
    private String examine;
    private boolean members;
    private int lowalch;
    private int highalch;
    private int limit;
    private int value;
    private String icon;
}
