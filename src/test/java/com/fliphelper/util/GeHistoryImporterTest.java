/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the GE-history-tab import: price-text parsing (the form that varies
 * by tax/quantity) and the dedup logic that stops re-opening History from
 * double-counting.
 */
public class GeHistoryImporterTest
{
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // ==================== PRICE PARSING ====================

    @Test
    public void parsesSingleUntaxedItemPrice()
    {
        // A single low-value item with no tax: "Bought 1 for >100 coin."
        assertEquals(100L, GeHistoryImporter.parsePrice("Bought 1 for <col=ffffff>100 coin</col>", 1));
    }

    @Test
    public void parsesMultiItemEachPrice()
    {
        // Multiple untaxed items show the per-item price after "each":
        // ">= 1,234 each"
        assertEquals(1234L, GeHistoryImporter.parsePrice("Sold 5 for <col=ffffff>>= 1,234 each</col>", 5));
    }

    @Test
    public void parsesTaxedTotalAndDividesByQuantity()
    {
        // Taxed offer shows the PRE-tax TOTAL: "(<original total> - <tax> coins)".
        // 20,000 total over 4 items -> 5,000 per item (pre-tax).
        long perItem = GeHistoryImporter.parsePrice(
            "Sold 4 for <col=ffffff>(20,000 - 400 coins)</col>", 4);
        assertEquals(5000L, perItem);
    }

    @Test
    public void unparseablePriceReturnsZero()
    {
        assertEquals(0L, GeHistoryImporter.parsePrice("", 1));
        assertEquals(0L, GeHistoryImporter.parsePrice(null, 1));
        assertEquals(0L, GeHistoryImporter.parsePrice("no digits here each", 3));
    }

    @Test
    public void detectsBuyVsSellFromStateText()
    {
        assertTrue(GeHistoryImporter.isBought("Bought 10"));
        assertFalse(GeHistoryImporter.isBought("Sold 10"));
        assertFalse(GeHistoryImporter.isBought(null));
    }

    // ==================== DEDUP ====================

    @Test
    public void dedupeKeyDistinguishesSideAndPrice()
    {
        GeHistoryImporter.ParsedOffer buy =
            new GeHistoryImporter.ParsedOffer(4151, 5, 1_000_000L, true);
        GeHistoryImporter.ParsedOffer sellSamePrice =
            new GeHistoryImporter.ParsedOffer(4151, 5, 1_000_000L, false);
        GeHistoryImporter.ParsedOffer buyDifferentPrice =
            new GeHistoryImporter.ParsedOffer(4151, 5, 999_999L, true);

        assertFalse("buy and sell of same item/qty/price must differ",
            GeHistoryImporter.dedupeKey(buy).equals(GeHistoryImporter.dedupeKey(sellSamePrice)));
        assertFalse("different prices must differ",
            GeHistoryImporter.dedupeKey(buy).equals(GeHistoryImporter.dedupeKey(buyDifferentPrice)));
    }

    @Test
    public void identicalOfferProducesIdenticalKey()
    {
        GeHistoryImporter.ParsedOffer a =
            new GeHistoryImporter.ParsedOffer(4151, 5, 1_000_000L, true);
        GeHistoryImporter.ParsedOffer b =
            new GeHistoryImporter.ParsedOffer(4151, 5, 1_000_000L, true);
        assertEquals(GeHistoryImporter.dedupeKey(a), GeHistoryImporter.dedupeKey(b));
    }

    @Test
    public void seenSetSuppressesReimportOfSameRows()
    {
        // Simulate two opens of the History tab showing the same rows: only the
        // first open should count as new.
        Set<String> seen = new HashSet<>();
        GeHistoryImporter.ParsedOffer offer =
            new GeHistoryImporter.ParsedOffer(4151, 5, 1_000_000L, true);

        assertTrue("first sighting is new", seen.add(GeHistoryImporter.dedupeKey(offer)));
        assertFalse("re-open of same row is a duplicate", seen.add(GeHistoryImporter.dedupeKey(offer)));
    }

    @Test
    public void dedupeStorePersistsKeysAcrossReload()
    {
        File dir = tmp.getRoot();
        GeHistoryImporter.GeHistoryDedupeStore store = new GeHistoryImporter.GeHistoryDedupeStore(dir);

        GeHistoryImporter.ParsedOffer offer =
            new GeHistoryImporter.ParsedOffer(4151, 5, 1_000_000L, true);
        String key = GeHistoryImporter.dedupeKey(offer);
        store.getKeys().add(key);
        store.persist();

        // A fresh store over the same dir (e.g. after a client restart) must
        // already know the key, so the offer will not be re-imported.
        GeHistoryImporter.GeHistoryDedupeStore reloaded = new GeHistoryImporter.GeHistoryDedupeStore(dir);
        assertTrue("persisted dedupe key survives reload", reloaded.getKeys().contains(key));
        assertFalse("reloaded store treats the key as already seen", reloaded.getKeys().add(key));
    }
}
