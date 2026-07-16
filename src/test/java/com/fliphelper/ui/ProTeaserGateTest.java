/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.ui;

import com.fliphelper.api.EntitlementService;
import com.fliphelper.api.EntitlementService.Entitlement;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * #62 gate contract: the PRO teaser shows for anonymous users (including the
 * null/unresolved entitlement the panel constructs with) and free accounts, and
 * NEVER for a resolved pro account — a payer seeing an upsell for something they
 * already bought reads as a billing bug. Tier strings mirror the server contract
 * (routes/account.js: anonymous / free / pro); matched case-insensitively so a
 * server casing change cannot flash the upsell at a payer.
 */
public class ProTeaserGateTest
{
    @Test
    public void nullUnresolvedEntitlementSeesTeaser()
    {
        assertTrue("panel constructs before the entitlement resolves — must default to teaser",
            GrandFlipOutPanel.showProTeaser(null));
    }

    @Test
    public void anonymousSeesTeaser()
    {
        assertTrue(GrandFlipOutPanel.showProTeaser(EntitlementService.LOCKED));
    }

    @Test
    public void freeAccountSeesTeaser()
    {
        assertTrue(GrandFlipOutPanel.showProTeaser(new Entitlement(true, "free", true)));
    }

    @Test
    public void proAccountNeverSeesTeaser()
    {
        assertFalse(GrandFlipOutPanel.showProTeaser(new Entitlement(true, "pro", true)));
    }

    @Test
    public void proCasingChangeStillHidesTeaser()
    {
        assertFalse(GrandFlipOutPanel.showProTeaser(new Entitlement(true, "PRO", true)));
        assertFalse(GrandFlipOutPanel.showProTeaser(new Entitlement(true, "Pro", true)));
    }

    @Test
    public void unknownFutureTierDefaultsToTeaser()
    {
        // A tier the plugin has never heard of must fail OPEN to the teaser (an upsell
        // to a non-payer is safe; hiding Pro from a would-be payer costs revenue only).
        assertTrue(GrandFlipOutPanel.showProTeaser(new Entitlement(true, "plus", true)));
    }
}
