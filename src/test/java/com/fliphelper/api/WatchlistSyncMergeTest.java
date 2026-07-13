/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pins the #190 watchlist push-back union to the server contract
 * (server account-watchlist.js: PUT REPLACES the whole list — "merge is a
 * client concern"). The invariant that must never regress: server items ride
 * VERBATIM in the merged PUT, because dropping or rewriting them would erase
 * the alert rules the user configured on the website.
 */
public class WatchlistSyncMergeTest
{
    private static final Gson GSON = new Gson();

    private static JsonArray arr(String json)
    {
        return GSON.fromJson(json, JsonArray.class);
    }

    private static JsonElement el(String json)
    {
        return GSON.fromJson(json, JsonElement.class);
    }

    @Test
    public void idsExtractedAndMalformedEntriesSkipped()
    {
        JsonArray server = arr("[{\"itemId\":4151,\"rules\":{\"minRealizable\":50000}},"
            + "\"junk\", {\"noItemId\":1}, {\"itemId\":\"abc\"}, {\"itemId\":2}]");
        Set<Integer> ids = IntelligenceClient.watchlistIds(server);
        assertEquals(2, ids.size());
        assertTrue(ids.contains(4151));
        assertTrue(ids.contains(2));
    }

    @Test
    public void nothingToPushReturnsNull()
    {
        JsonArray server = arr("[{\"itemId\":4151}]");
        // every local star already known server-side
        assertNull(IntelligenceClient.mergeWatchlistForPushBack(server, Collections.singletonList(4151)));
        // no local stars at all
        assertNull(IntelligenceClient.mergeWatchlistForPushBack(server, Collections.emptyList()));
        // null entries never count as extras
        assertNull(IntelligenceClient.mergeWatchlistForPushBack(server, Collections.singletonList(null)));
    }

    @Test
    public void serverItemsRideVerbatimAndExtrasJoinBare()
    {
        JsonObject serverEntry = el(
            "{\"itemId\":4151,\"rules\":{\"minRealizable\":50000,\"minDropPct\":5},\"unknownField\":\"x\"}")
            .getAsJsonObject();
        JsonArray server = new JsonArray();
        server.add(serverEntry);

        JsonArray merged = IntelligenceClient.mergeWatchlistForPushBack(server, Arrays.asList(4151, 2));
        assertEquals(2, merged.size());
        // the server entry is byte-identical — rules AND unknown fields survive
        assertEquals(serverEntry, merged.get(0));
        // the local extra is a bare id
        assertEquals(el("{\"itemId\":2}"), merged.get(1));
    }

    @Test
    public void duplicateLocalStarsAppendOnce()
    {
        JsonArray server = arr("[{\"itemId\":4151}]");
        JsonArray merged = IntelligenceClient.mergeWatchlistForPushBack(server, Arrays.asList(2, 2, 4151));
        assertEquals(2, merged.size());
        assertEquals(el("{\"itemId\":2}"), merged.get(1));
    }

    @Test
    public void inputArrayIsNeverMutated()
    {
        JsonArray server = arr("[{\"itemId\":4151}]");
        IntelligenceClient.mergeWatchlistForPushBack(server, Arrays.asList(2, 3));
        assertEquals(1, server.size());
    }

    @Test
    public void malformedServerEntriesArePreservedNotDropped()
    {
        // The PUT replaces the whole server list: the client filtering ANYTHING out
        // would delete it server-side. Server-side validation is the junk arbiter.
        JsonArray server = arr("[\"junk\", {\"itemId\":4151}]");
        JsonArray merged = IntelligenceClient.mergeWatchlistForPushBack(server, Collections.singletonList(2));
        assertEquals(3, merged.size());
        assertEquals(el("\"junk\""), merged.get(0));
        assertEquals(el("{\"itemId\":4151}"), merged.get(1));
        assertEquals(el("{\"itemId\":2}"), merged.get(2));
    }

    @Test
    public void nonNumericItemIdNeverAbortsTheSync()
    {
        // getAsInt() on {"itemId":"abc"} throws — the extractor must skip it, not
        // blow up the whole pull+push cycle.
        JsonArray server = arr("[{\"itemId\":\"abc\"}, {\"itemId\":7}]");
        Set<Integer> ids = IntelligenceClient.watchlistIds(server);
        assertEquals(Collections.singleton(7), ids);
    }
}
