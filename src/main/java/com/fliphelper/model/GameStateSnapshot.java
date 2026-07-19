/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal snapshot of what the advisor needs to suggest a next action: coins
 * available, free GE slots, and the currently-active offers. Deliberately small —
 * no full inventory and no bank contents leave the client.
 */
@Value
public class GameStateSnapshot
{
    private static final int COINS = 995;

    long gold;
    int freeSlots;
    List<ActiveOffer> activeOffers;

    @Value
    public static class ActiveOffer
    {
        int slot;
        int itemId;
        boolean buy;
        long price;
        int qtyFilled;
        int qtyTotal;
    }

    /** Build a snapshot from the live client. Must be called on the client thread. */
    public static GameStateSnapshot capture(Client client)
    {
        long gold = 0;
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory != null)
        {
            for (Item item : inventory.getItems())
            {
                if (item != null && item.getId() == COINS)
                {
                    gold += item.getQuantity();
                }
            }
        }

        int freeSlots = 0;
        List<ActiveOffer> active = new ArrayList<>();
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        if (offers != null)
        {
            for (int slot = 0; slot < offers.length; slot++)
            {
                GrandExchangeOffer offer = offers[slot];
                if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
                {
                    freeSlots++;
                    continue;
                }
                GrandExchangeOfferState state = offer.getState();
                boolean buy = state == GrandExchangeOfferState.BUYING
                    || state == GrandExchangeOfferState.BOUGHT
                    || state == GrandExchangeOfferState.CANCELLED_BUY;
                active.add(new ActiveOffer(slot, offer.getItemId(), buy, offer.getPrice(),
                    offer.getQuantitySold(), offer.getTotalQuantity()));
            }
        }

        return new GameStateSnapshot(gold, freeSlots, active);
    }

    /** Serialize this snapshot to the JSON request body for POST /api/intelligence/suggest. */
    public String toRequestJson(List<Integer> excludeIds, boolean f2pOnly)
    {
        return toRequestJson(excludeIds, f2pOnly, null, 0);
    }

    /**
     * #215 advisor filters: {@code band} ∈ {throughput, patient_whale} narrows the
     * server-side pick to that flip-bands lane (null = all); {@code maxFillMin} > 0
     * caps the estimated fill time. Band values come from the panel's fixed chip
     * vocabulary — never free text — so the hand-built JSON stays escape-safe.
     */
    public String toRequestJson(List<Integer> excludeIds, boolean f2pOnly, String band, int maxFillMin)
    {
        return toRequestJson(excludeIds, f2pOnly, band, maxFillMin, null);
    }

    /**
     * #215 item 4: {@code slotPlan} (nullable) rides the body additively — an
     * absent plan produces a byte-identical body to the 4-arg overload, so old
     * servers and unmixed clients see no contract change. Lane values come from
     * config ints + the fixed band vocabulary — escape-safe by construction.
     */
    public String toRequestJson(List<Integer> excludeIds, boolean f2pOnly, String band, int maxFillMin,
                                List<SlotLane> slotPlan)
    {
        // v1.1: structured JsonObject tree instead of hand-concatenated strings — same
        // insertion order as the old builder, so the serialized body stays byte-identical
        // (SnapshotBandJsonTest / SlotPlanJsonTest pin it). JsonElement.toString() is
        // gson's compact writer; no Gson/GsonBuilder construction ever appears.
        JsonObject body = new JsonObject();
        body.addProperty("gold", gold);
        body.addProperty("freeSlots", freeSlots);
        body.addProperty("f2pOnly", f2pOnly);
        if (band != null && !band.isEmpty())
        {
            body.addProperty("band", band);
        }
        if (maxFillMin > 0)
        {
            body.addProperty("maxFillMin", maxFillMin);
        }
        if (slotPlan != null && !slotPlan.isEmpty())
        {
            JsonArray lanes = new JsonArray();
            for (SlotLane lane : slotPlan)
            {
                JsonObject l = new JsonObject();
                l.addProperty("slots", lane.getSlots());
                if (lane.getBand() != null)
                {
                    l.addProperty("band", lane.getBand());
                }
                if (lane.getMaxFillMin() > 0)
                {
                    l.addProperty("maxFillMin", lane.getMaxFillMin());
                }
                lanes.add(l);
            }
            body.add("slotPlan", lanes);
        }
        JsonArray offers = new JsonArray();
        for (ActiveOffer o : activeOffers)
        {
            JsonObject j = new JsonObject();
            j.addProperty("slot", o.getSlot());
            j.addProperty("itemId", o.getItemId());
            j.addProperty("buy", o.isBuy());
            j.addProperty("price", o.getPrice());
            j.addProperty("qtyFilled", o.getQtyFilled());
            j.addProperty("qtyTotal", o.getQtyTotal());
            offers.add(j);
        }
        body.add("activeOffers", offers);
        JsonArray ex = new JsonArray();
        if (excludeIds != null)
        {
            for (Integer id : excludeIds)
            {
                ex.add(id);
            }
        }
        body.add("excludeIds", ex);
        return body.toString();
    }
}
