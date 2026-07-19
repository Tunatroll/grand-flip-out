/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.util;

import com.fliphelper.model.TradeRecord;
import com.fliphelper.tracker.FlipTracker;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.widgets.Widget;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Back-fills trades the player made on MOBILE or before the plugin loaded by
 * reading the in-game Grand Exchange "History" interface.
 *
 * <p>The GE History tab (interface group 383, gameval
 * {@code InterfaceID.GeHistory.LIST} = 25100291, i.e. group 383 child 3)
 * populates a list of completed offers as dynamic child widgets. Following
 * Flipping Utilities' {@code GeHistoryTabExtractor}, each offer occupies a group
 * of 6 consecutive dynamic children:</p>
 * <ul>
 *   <li>index 2 — state text ("Bought ..." / "Sold ...")</li>
 *   <li>index 4 — item model ({@code getItemId()} + {@code getItemQuantity()})</li>
 *   <li>index 5 — price text ("... coins" / "... each" / "(orig - tax)")</li>
 * </ul>
 *
 * <p>The history tab shows no timestamp, and re-opening it re-renders the same
 * rows, so a naive feed would double-count. Imported trades are therefore
 * deduplicated on the composite key {@code itemId:quantity:price:side}, with the
 * seen keys persisted to {@code ge_history_imported.txt} in the per-character
 * data dir so re-opening History (or restarting the client) never re-imports the
 * same offer.</p>
 *
 * <p>Widget reads MUST run on the client thread; {@link #importVisibleHistory}
 * is intended to be called from {@code clientThread.invokeLater(...)} or from
 * within an event handler that already runs on the client thread.</p>
 */
@Slf4j
public final class GeHistoryImporter
{
    /** Group id of the GE history interface (loads in both right-click and GE-button paths). */
    public static final int GE_HISTORY_GROUP_ID = 383;

    /** Packed gameval id of the history list widget (group 383, child 3 = InterfaceID.GeHistory.LIST). */
    public static final int GE_HISTORY_LIST_WIDGET_ID = (383 << 16) | 3;

    /** A completed offer occupies a group of this many consecutive dynamic children. */
    private static final int WIDGETS_PER_OFFER = 6;
    private static final int STATE_IDX = 2;
    private static final int ITEM_IDX = 4;
    private static final int PRICE_IDX = 5;

    // Mirrors Flipping Utilities' price-text patterns. The history text encodes the
    // PRE-tax price; we keep the pre-tax per-item price so downstream tax handling
    // (FlipItem/GeTax) is not applied twice.
    private static final Pattern MULTI_ITEM_PATTERN = Pattern.compile(">= (.*) each");
    private static final Pattern SINGLE_ITEM_PATTERN = Pattern.compile(">(.*) coin");
    private static final Pattern ORIGINAL_PRICE_PATTERN = Pattern.compile("\\((.*) -");

    private final Client client;
    private final FlipTracker flipTracker;
    private final GeHistoryDedupeStore dedupeStore;

    /** Guards against concurrent imports racing on the dedupe set. */
    private final Object importLock = new Object();

    public GeHistoryImporter(Client client, FlipTracker flipTracker, GeHistoryDedupeStore dedupeStore)
    {
        this.client = client;
        this.flipTracker = flipTracker;
        this.dedupeStore = dedupeStore;
    }

    /**
     * Read the currently-visible GE history rows and feed the new ones into the
     * flip tracker. No-op if the history widget is not loaded. MUST be called on
     * the client thread.
     *
     * @return the number of newly-imported (non-duplicate) trades
     */
    public int importVisibleHistory()
    {
        Widget list = client.getWidget(GE_HISTORY_LIST_WIDGET_ID);
        if (list == null)
        {
            return 0;
        }

        Widget[] children = list.getDynamicChildren();
        if (children == null || children.length < WIDGETS_PER_OFFER)
        {
            return 0;
        }

        List<ParsedOffer> offers = parseOffers(children);
        if (offers.isEmpty())
        {
            return 0;
        }

        long accountId = client.getAccountHash();
        Player local = client.getLocalPlayer();
        String accountName = local != null ? local.getName() : null;
        Instant now = Instant.now();

        int imported = 0;
        synchronized (importLock)
        {
            Set<String> seen = dedupeStore.getKeys();
            List<TradeRecord> newTrades = new ArrayList<>();
            for (ParsedOffer offer : offers)
            {
                String key = dedupeKey(offer);
                if (!seen.add(key))
                {
                    continue;
                }
                newTrades.add(TradeRecord.builder()
                    .itemId(offer.getItemId())
                    .itemName(resolveItemName(offer.getItemId()))
                    .quantity(offer.getQuantity())
                    .price(offer.getPrice())
                    .bought(offer.isBought())
                    .timestamp(now)
                    .geSlot(-1)
                    .accountId(accountId)
                    .accountName(accountName)
                    .build());
                imported++;
            }

            if (imported > 0)
            {
                dedupeStore.persist();
                // Feed buys before sells so FlipTracker's FIFO pairing can match a
                // sell against a buy seen in the same history snapshot.
                for (TradeRecord trade : newTrades)
                {
                    if (trade.isBought())
                    {
                        flipTracker.recordTransaction(trade);
                    }
                }
                for (TradeRecord trade : newTrades)
                {
                    if (!trade.isBought())
                    {
                        flipTracker.recordTransaction(trade);
                    }
                }
                log.info("Imported {} new GE history trade(s) ({} total rows visible)",
                    imported, offers.size());
            }
        }
        return imported;
    }

    /**
     * Parse a flat array of GE-history dynamic child widgets into offers,
     * skipping any row whose item / price cannot be read. Visible for testing
     * the row-grouping logic.
     */
    static List<ParsedOffer> parseOffers(Widget[] children)
    {
        List<ParsedOffer> offers = new ArrayList<>();
        int groups = children.length / WIDGETS_PER_OFFER;
        for (int g = 0; g < groups; g++)
        {
            int base = g * WIDGETS_PER_OFFER;
            Widget stateWidget = children[base + STATE_IDX];
            Widget itemWidget = children[base + ITEM_IDX];
            Widget priceWidget = children[base + PRICE_IDX];
            if (stateWidget == null || itemWidget == null || priceWidget == null)
            {
                continue;
            }

            int itemId = itemWidget.getItemId();
            int quantity = itemWidget.getItemQuantity();
            if (itemId <= 0 || quantity <= 0)
            {
                continue;
            }

            boolean bought = isBought(stateWidget.getText());
            long price = parsePrice(priceWidget.getText(), quantity);
            if (price <= 0)
            {
                continue;
            }

            offers.add(new ParsedOffer(itemId, quantity, price, bought));
        }
        return offers;
    }

    static boolean isBought(String stateText)
    {
        return stateText != null && stateText.startsWith("Bought");
    }

    /**
     * Extract the PRE-tax per-item price from a GE-history price-text widget.
     * Handles the three observed forms (single untaxed, multi untaxed "each",
     * and taxed "(original - tax)"). Returns 0 when the text cannot be parsed.
     */
    static long parsePrice(String text, int quantity)
    {
        if (text == null || text.isEmpty())
        {
            return 0;
        }

        Matcher matcher;
        boolean totalPrice = false;
        if (text.contains(")</col>"))
        {
            // Taxed offer: "(<original total> - <tax> coins)". The original is a
            // TOTAL across the offer, so divide by quantity for the per-item price.
            matcher = ORIGINAL_PRICE_PATTERN.matcher(text);
            totalPrice = true;
        }
        else if (text.contains("each"))
        {
            matcher = MULTI_ITEM_PATTERN.matcher(text);
        }
        else
        {
            matcher = SINGLE_ITEM_PATTERN.matcher(text);
        }

        if (!matcher.find())
        {
            return 0;
        }

        StringBuilder digits = new StringBuilder();
        for (char c : matcher.group(1).toCharArray())
        {
            if (Character.isDigit(c))
            {
                digits.append(c);
            }
        }
        if (digits.length() == 0)
        {
            return 0;
        }

        long price;
        try
        {
            price = Long.parseLong(digits.toString());
        }
        catch (NumberFormatException e)
        {
            return 0;
        }

        if (totalPrice && quantity > 0)
        {
            return price / quantity;
        }
        return price;
    }

    static String dedupeKey(ParsedOffer offer)
    {
        return offer.getItemId() + ":" + offer.getQuantity() + ":"
            + offer.getPrice() + ":" + (offer.isBought() ? "b" : "s");
    }

    private String resolveItemName(int itemId)
    {
        try
        {
            net.runelite.api.ItemComposition def = client.getItemDefinition(itemId);
            if (def != null && def.getName() != null)
            {
                return def.getName();
            }
        }
        catch (Exception e)
        {
            log.debug("Could not resolve item name for {}: {}", itemId, e.getMessage());
        }
        return "Item #" + itemId;
    }

    /** A single completed offer read from the GE history tab. */
    @Value
    static class ParsedOffer
    {
        int itemId;
        int quantity;
        long price;
        boolean bought;
    }

    /**
     * Convenience builder for production wiring: a {@link GeHistoryImporter}
     * backed by a {@link GeHistoryDedupeStore} persisted under {@code dataDir}.
     * The executor keeps the store's file write off the client thread —
     * {@link #importVisibleHistory} runs there (widget reads).
     */
    public static GeHistoryImporter create(Client client, FlipTracker flipTracker, java.io.File dataDir,
        java.util.concurrent.Executor ioExecutor)
    {
        return new GeHistoryImporter(client, flipTracker, new GeHistoryDedupeStore(dataDir, ioExecutor));
    }

    /**
     * Persistent set of dedupe keys for already-imported GE-history offers.
     * One key per line in {@code ge_history_imported.txt}. Thread-safe.
     */
    public static final class GeHistoryDedupeStore
    {
        private static final String FILE_NAME = "ge_history_imported.txt";
        private final java.io.File file;
        private final java.util.concurrent.Executor ioExecutor;
        private final Set<String> keys = ConcurrentHashMap.newKeySet();

        public GeHistoryDedupeStore(java.io.File dataDir, java.util.concurrent.Executor ioExecutor)
        {
            this.file = dataDir != null ? new java.io.File(dataDir, FILE_NAME) : null;
            this.ioExecutor = ioExecutor;
            load();
        }

        Set<String> getKeys()
        {
            return keys;
        }

        private void load()
        {
            if (file == null || !file.exists())
            {
                return;
            }
            try (java.io.BufferedReader reader =
                     new java.io.BufferedReader(new java.io.FileReader(file)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty())
                    {
                        keys.add(trimmed);
                    }
                }
            }
            catch (java.io.IOException e)
            {
                log.debug("Could not load GE-history dedupe store: {}", e.getMessage());
            }
        }

        void persist()
        {
            if (file == null)
            {
                return;
            }
            // Rewrite on the executor so the import path (client thread) never blocks
            // on disk. The snapshot is taken inside the serialized write: keys only
            // grow, so the last write always holds everything regardless of order.
            ioExecutor.execute(this::writeNow);
        }

        private synchronized void writeNow()
        {
            try
            {
                if (file.getParentFile() != null)
                {
                    file.getParentFile().mkdirs();
                }
                try (java.io.Writer writer =
                         new java.io.BufferedWriter(new java.io.FileWriter(file)))
                {
                    for (String key : keys)
                    {
                        writer.write(key);
                        writer.write('\n');
                    }
                }
            }
            catch (java.io.IOException e)
            {
                log.debug("Could not persist GE-history dedupe store: {}", e.getMessage());
            }
        }
    }
}
