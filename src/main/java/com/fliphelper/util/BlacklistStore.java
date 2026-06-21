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
 * Item IDs the player never wants the advisor to suggest. Persisted as a plain
 * comma-separated file in the plugin data dir, mirroring {@link WatchlistStore}.
 * In memory only when no data dir is supplied (e.g. a unit-test panel).
 */
@Slf4j
public class BlacklistStore
{
    private static final String FILE_NAME = "advisor-blacklist.txt";

    private final Path file;
    private final Set<Integer> ids = new LinkedHashSet<>();

    public BlacklistStore(java.io.File dataDir)
    {
        this.file = dataDir != null ? dataDir.toPath().resolve(FILE_NAME) : null;
        load();
    }

    public boolean contains(int itemId)
    {
        return ids.contains(itemId);
    }

    /** Add an item to the blacklist. Returns true if it was newly added. */
    public boolean add(int itemId)
    {
        boolean added = ids.add(itemId);
        if (added)
        {
            save();
        }
        return added;
    }

    public void remove(int itemId)
    {
        if (ids.remove(itemId))
        {
            save();
        }
    }

    /** Blacklisted item IDs in insertion order. */
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
            log.debug("Could not read advisor blacklist: {}", e.getMessage());
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
            log.debug("Could not save advisor blacklist: {}", e.getMessage());
        }
    }
}
