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
