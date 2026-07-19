/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.model;

import java.util.Collections;
import java.util.List;

/**
 * A combination/recipe relationship between an assembled output item and the set of
 * input components it is made from (e.g. a Barrows armour set vs its 4 pieces, or a
 * godsword vs blade + hilt).
 *
 * <p>This is a pure data holder: it knows only item IDs and a human note. Pricing,
 * tax and profit are computed elsewhere (see {@code ui.RecipePanel}) using live
 * {@link PriceAggregate} data so the model stays free of API/UI dependencies.
 */
public final class Recipe
{
    private final String name;
    private final int outputItemId;
    private final List<Integer> inputItemIds;
    private final String note;

    public Recipe(String name, int outputItemId, List<Integer> inputItemIds, String note)
    {
        this.name = name;
        this.outputItemId = outputItemId;
        this.inputItemIds = Collections.unmodifiableList(inputItemIds);
        this.note = note;
    }

    /** Display name of the recipe, e.g. "Dharok's armour set". */
    public String getName()
    {
        return name;
    }

    /** Item ID of the assembled / combined item. */
    public int getOutputItemId()
    {
        return outputItemId;
    }

    /** Item IDs of the component items that combine into the output (one of each). */
    public List<Integer> getInputItemIds()
    {
        return inputItemIds;
    }

    /** Short human note about the recipe (mechanic, caveats). */
    public String getNote()
    {
        return note;
    }
}
