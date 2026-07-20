/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * GE chat copy contracts. Two defect classes, both user-visible in the shipped Hub build:
 *
 * 1. DOUBLE UNIT — {@code formatGp} already returns "1,234 gp", but every arm/inject call
 *    site appended its own " gp", so the client printed "1,234 gp gp x5". The same class was
 *    fixed once for the dump-feed rows (hub 1662a6d) and these sites survived it; a unit that
 *    carries its own suffix has to be pinned or the next caller re-adds one.
 *
 * 2. PHANTOM QUANTITY — a fill armed from a WATCHLIST/price row has no suggested quantity, so
 *    the "x%,d" copy rendered "x0". Advising "x0" of anything is nonsense, and the honest-numbers
 *    rule says a number we do not have is omitted, never printed as zero.
 */
public class GeChatCopyTest
{
    @Test
    public void formatGpCarriesExactlyOneUnit()
    {
        String gp = GrandFlipOutPlugin.formatGp(1234);
        assertEquals("1,234 gp", gp);
        assertFalse("formatGp must not double its own unit", gp.contains("gp gp"));
    }

    @Test
    public void armedWithQuantityNeverDoublesTheUnit()
    {
        String msg = GrandFlipOutPlugin.armChatMessage(GrandFlipOutPlugin.formatGp(1234), 5, true);
        assertFalse("armed copy printed a double unit: " + msg, msg.contains("gp gp"));
        assertTrue("armed copy must quote the price: " + msg, msg.contains("1,234 gp"));
        assertTrue("armed copy must quote the quantity: " + msg, msg.contains("5"));
    }

    @Test
    public void armedWithoutQuantityOmitsItRatherThanPrintingZero()
    {
        String msg = GrandFlipOutPlugin.armChatMessage(GrandFlipOutPlugin.formatGp(1234), 0, true);
        assertFalse("a quantity we do not have must not print as x0: " + msg, msg.contains("x0"));
        assertFalse(msg.contains("gp gp"));
        assertTrue("must still quote the price: " + msg, msg.contains("1,234 gp"));
    }

    @Test
    public void injectionDisabledCopyFollowsTheSameTwoRules()
    {
        String withQty = GrandFlipOutPlugin.armChatMessage(GrandFlipOutPlugin.formatGp(2_000_000), 3, false);
        assertFalse(withQty.contains("gp gp"));
        assertTrue("disabled copy must point at the setting: " + withQty, withQty.contains("settings"));

        String noQty = GrandFlipOutPlugin.armChatMessage(GrandFlipOutPlugin.formatGp(2_000_000), 0, false);
        assertFalse("a quantity we do not have must not print as x0: " + noQty, noQty.contains("x0"));
        assertTrue(noQty.contains("settings"));
    }

    @Test
    public void filledConfirmationNeverDoublesTheUnit()
    {
        String msg = GrandFlipOutPlugin.filledChatMessage(GrandFlipOutPlugin.formatGp(7_654_321));
        assertFalse("fill confirmation printed a double unit: " + msg, msg.contains("gp gp"));
        assertTrue(msg.contains("7,654,321 gp"));
        assertTrue("must still tell the player they confirm: " + msg, msg.contains("Confirm"));
    }
}
