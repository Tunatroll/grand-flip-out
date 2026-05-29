/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The player's starred (watched) item IDs, kept on a "favourites" list. Persisted as a
 * plain comma-separated file in the plugin data dir so it survives between sessions —
 * insertion order is preserved so the most-recently-starred items don't jump around.
 *
 * <p>If no data dir is supplied (e.g. a unit-test panel) it just lives in memory.
 */
@Slf4j
public class WatchlistStore
{
    private static final String FILE_NAME = "watchlist.txt";

    private final Path file;
    private final Set<Integer> ids = new LinkedHashSet<>();

    public WatchlistStore(java.io.File dataDir)
    {
        this.file = dataDir != null ? dataDir.toPath().resolve(FILE_NAME) : null;
        load();
    }

    public boolean contains(int itemId)
    {
        return ids.contains(itemId);
    }

    public boolean isEmpty()
    {
        return ids.isEmpty();
    }

    /** Star/unstar an item. Returns true if it is now watched. */
    public boolean toggle(int itemId)
    {
        boolean nowWatched;
        if (ids.contains(itemId))
        {
            ids.remove(itemId);
            nowWatched = false;
        }
        else
        {
            ids.add(itemId);
            nowWatched = true;
        }
        save();
        return nowWatched;
    }

    /** Watched item IDs in the order they were starred. */
    public List<Integer> getAll()
    {
        return new ArrayList<>(ids);
    }

    private void load()
    {
        if (file == null || !Files.exists(file))
        {
            return;
        }
        try
        {
            String raw = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            for (String part : raw.split(","))
            {
                String trimmed = part.trim();
                if (!trimmed.isEmpty())
                {
                    try
                    {
                        ids.add(Integer.parseInt(trimmed));
                    }
                    catch (NumberFormatException ignored)
                    {
                        // skip a corrupt entry rather than losing the whole list
                    }
                }
            }
        }
        catch (IOException e)
        {
            log.debug("Could not read watchlist: {}", e.getMessage());
        }
    }

    private void save()
    {
        if (file == null)
        {
            return;
        }
        try
        {
            StringBuilder sb = new StringBuilder();
            for (Integer id : ids)
            {
                if (sb.length() > 0)
                {
                    sb.append(',');
                }
                sb.append(id);
            }
            Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException e)
        {
            log.debug("Could not save watchlist: {}", e.getMessage());
        }
    }
}
