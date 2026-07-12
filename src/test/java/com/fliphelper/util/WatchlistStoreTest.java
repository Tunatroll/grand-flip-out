/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * #190 watchlist sync, pull leg: mergeFrom must be an ADD-ONLY union — the server
 * copy never deletes a local star, a no-change merge reports false (so callers
 * don't refresh for nothing), and null/empty input is a no-op.
 */
public class WatchlistStoreTest
{
    @Test
    public void mergeAddsServerIdsAndKeepsLocalStars()
    {
        WatchlistStore store = new WatchlistStore(null); // in-memory
        store.toggle(4151); // local-only star the server doesn't know about
        assertTrue(store.mergeFrom(Arrays.asList(2577, 21948)));
        assertTrue(store.contains(4151));
        assertTrue(store.contains(2577));
        assertTrue(store.contains(21948));
        assertEquals(3, store.getAll().size());
    }

    @Test
    public void mergeIsAddOnlyAndIdempotent()
    {
        WatchlistStore store = new WatchlistStore(null);
        assertTrue(store.mergeFrom(Arrays.asList(2577)));
        assertFalse("re-merging the same ids must report no change",
            store.mergeFrom(Arrays.asList(2577)));
        assertEquals(1, store.getAll().size());
    }

    @Test
    public void nullAndEmptyMergesAreNoOps()
    {
        WatchlistStore store = new WatchlistStore(null);
        store.toggle(4151);
        assertFalse(store.mergeFrom(null));
        assertFalse(store.mergeFrom(Collections.emptyList()));
        assertFalse(store.mergeFrom(Collections.singletonList(null)));
        assertTrue(store.contains(4151));
    }
}
