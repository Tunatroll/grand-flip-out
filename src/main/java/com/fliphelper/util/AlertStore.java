/*
 * Copyright (c) 2026, Tunatroll
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-item price alert targets. Each watched item can carry a target <em>buy</em> price
 * (fire when the insta-buy/low drops to or below it — a good entry) and/or a target
 * <em>sell</em> price (fire when the insta-sell/high rises to or above it — a good exit).
 *
 * <p>Persisted per-character as a plain text file in the plugin data dir, one record per
 * line ({@code itemId,buyTarget,sellTarget}), mirroring {@link WatchlistStore}. A target of
 * {@code 0} means "not set" for that side. If no data dir is supplied (e.g. a unit test) it
 * lives purely in memory.
 *
 * <p>This class is the de-dupe authority: {@link #shouldFireBuy} / {@link #shouldFireSell}
 * latch once a target is crossed and only re-arm after the price moves back to the wrong
 * side of the target, so a single crossing produces a single notification rather than one
 * per refresh. All target/dedupe logic is deterministic and free of RuneLite types so it is
 * directly unit-testable.
 */
@Slf4j
public class AlertStore
{
    private static final String FILE_NAME = "alerts.txt";

    /** Mutable per-item alert target. A side is "unset" when its target is &lt;= 0. */
    public static final class Target
    {
        private long buyTarget;
        private long sellTarget;
        // De-dupe latches: true once the corresponding alert has fired and not yet re-armed.
        private boolean buyFired;
        private boolean sellFired;

        public long getBuyTarget()
        {
            return buyTarget;
        }

        public long getSellTarget()
        {
            return sellTarget;
        }

        public boolean hasBuyTarget()
        {
            return buyTarget > 0;
        }

        public boolean hasSellTarget()
        {
            return sellTarget > 0;
        }

        public boolean isEmpty()
        {
            return buyTarget <= 0 && sellTarget <= 0;
        }
    }

    private final Path file;
    private final Map<Integer, Target> targets = new LinkedHashMap<>();

    public AlertStore(java.io.File dataDir)
    {
        this.file = dataDir != null ? dataDir.toPath().resolve(FILE_NAME) : null;
        load();
    }

    public boolean isEmpty()
    {
        return targets.isEmpty();
    }

    /** Item IDs that carry at least one target, in insertion order. */
    public List<Integer> getItemIds()
    {
        return new ArrayList<>(targets.keySet());
    }

    /** The target for an item, or {@code null} if none is set. */
    public Target get(int itemId)
    {
        return targets.get(itemId);
    }

    public long getBuyTarget(int itemId)
    {
        Target t = targets.get(itemId);
        return t != null ? t.buyTarget : 0;
    }

    public long getSellTarget(int itemId)
    {
        Target t = targets.get(itemId);
        return t != null ? t.sellTarget : 0;
    }

    /**
     * Set (or clear) an item's targets. A value &lt;= 0 clears that side; clearing both sides
     * removes the item entirely. Changing a target re-arms its de-dupe latch so the next
     * crossing fires. Persists immediately.
     */
    public void setTargets(int itemId, long buyTarget, long sellTarget)
    {
        long buy = Math.max(0, buyTarget);
        long sell = Math.max(0, sellTarget);
        if (buy <= 0 && sell <= 0)
        {
            targets.remove(itemId);
            save();
            return;
        }
        Target t = targets.computeIfAbsent(itemId, k -> new Target());
        if (t.buyTarget != buy)
        {
            t.buyTarget = buy;
            t.buyFired = false;
        }
        if (t.sellTarget != sell)
        {
            t.sellTarget = sell;
            t.sellFired = false;
        }
        save();
    }

    /** Remove an item's targets entirely. */
    public void clear(int itemId)
    {
        if (targets.remove(itemId) != null)
        {
            save();
        }
    }

    /**
     * Whether a BUY alert should fire for {@code itemId} at the given current low (insta-buy)
     * price. Returns true exactly once per crossing: the first time the price is at or below
     * the target while the latch is armed. Re-arms automatically once the price climbs back
     * above the target. A non-persisting check (the latch is in-memory dedupe state).
     */
    public boolean shouldFireBuy(int itemId, long currentLow)
    {
        Target t = targets.get(itemId);
        if (t == null || t.buyTarget <= 0 || currentLow <= 0)
        {
            return false;
        }
        if (currentLow <= t.buyTarget)
        {
            if (t.buyFired)
            {
                return false;
            }
            t.buyFired = true;
            return true;
        }
        // Price moved back above the target — re-arm for the next dip.
        t.buyFired = false;
        return false;
    }

    /**
     * Whether a SELL alert should fire for {@code itemId} at the given current high
     * (insta-sell) price. Fires once when the price reaches or exceeds the target; re-arms
     * once the price falls back below it.
     */
    public boolean shouldFireSell(int itemId, long currentHigh)
    {
        Target t = targets.get(itemId);
        if (t == null || t.sellTarget <= 0 || currentHigh <= 0)
        {
            return false;
        }
        if (currentHigh >= t.sellTarget)
        {
            if (t.sellFired)
            {
                return false;
            }
            t.sellFired = true;
            return true;
        }
        t.sellFired = false;
        return false;
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
            for (String line : raw.split("\n"))
            {
                String trimmed = line.trim();
                if (trimmed.isEmpty())
                {
                    continue;
                }
                String[] parts = trimmed.split(",");
                if (parts.length < 3)
                {
                    continue;
                }
                try
                {
                    int id = Integer.parseInt(parts[0].trim());
                    long buy = Long.parseLong(parts[1].trim());
                    long sell = Long.parseLong(parts[2].trim());
                    if (buy > 0 || sell > 0)
                    {
                        Target t = new Target();
                        t.buyTarget = Math.max(0, buy);
                        t.sellTarget = Math.max(0, sell);
                        targets.put(id, t);
                    }
                }
                catch (NumberFormatException ignored)
                {
                    // skip a corrupt entry rather than losing the whole list
                }
            }
        }
        catch (IOException e)
        {
            log.debug("Could not read alerts: {}", e.getMessage());
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
            for (Map.Entry<Integer, Target> e : targets.entrySet())
            {
                sb.append(e.getKey()).append(',')
                    .append(e.getValue().buyTarget).append(',')
                    .append(e.getValue().sellTarget).append('\n');
            }
            Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException e)
        {
            log.debug("Could not save alerts: {}", e.getMessage());
        }
    }
}
