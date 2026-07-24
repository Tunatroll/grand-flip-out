/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.ui;

import com.fliphelper.api.EntitlementService;
import com.fliphelper.api.EntitlementService.Entitlement;
import com.fliphelper.ui.GrandFlipOutPanel.AccountStatus;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Recognition contract: the plugin must make the account tier VISIBLE — a user
 * should see at a glance whether they are signed out, on Free, or on Pro. Before
 * this, isUnlocked() was true for BOTH free and pro accounts, so the footer said
 * "Account" for either and a payer had no confirmation their Pro was recognized.
 *
 * accountStatus() is the pure/static SSOT for the status badge; tier strings mirror
 * the server contract (routes/account.js: anonymous / free / pro), matched
 * case-insensitively so a server casing change can't mislabel a payer.
 */
public class AccountStatusTest
{
    @Test
    public void nullUnresolvedEntitlementIsGuest()
    {
        assertEquals("panel constructs before the entitlement resolves",
            AccountStatus.GUEST, GrandFlipOutPanel.accountStatus(null));
    }

    @Test
    public void anonymousLockedIsGuest()
    {
        assertEquals(AccountStatus.GUEST, GrandFlipOutPanel.accountStatus(EntitlementService.LOCKED));
    }

    @Test
    public void authenticatedFreeIsFree()
    {
        assertEquals(AccountStatus.FREE, GrandFlipOutPanel.accountStatus(new Entitlement(true, "free", true)));
    }

    @Test
    public void proIsProAcrossCasing()
    {
        assertEquals(AccountStatus.PRO, GrandFlipOutPanel.accountStatus(new Entitlement(true, "pro", true)));
        assertEquals(AccountStatus.PRO, GrandFlipOutPanel.accountStatus(new Entitlement(true, "PRO", true)));
        assertEquals(AccountStatus.PRO, GrandFlipOutPanel.accountStatus(new Entitlement(true, "Pro", true)));
    }

    @Test
    public void unknownAuthenticatedTierFallsBackToFreeNotPro()
    {
        // A tier the plugin doesn't recognize must never be shown as Pro (a false
        // "you're Pro" badge is worse than under-claiming).
        assertEquals(AccountStatus.FREE, GrandFlipOutPanel.accountStatus(new Entitlement(true, "plus", true)));
    }

    @Test
    public void badgeTextIsDistinctAndNonEmptyPerState()
    {
        String guest = GrandFlipOutPanel.statusBadgeText(AccountStatus.GUEST);
        String free = GrandFlipOutPanel.statusBadgeText(AccountStatus.FREE);
        String pro = GrandFlipOutPanel.statusBadgeText(AccountStatus.PRO);
        // each is a real, distinct label — the whole point is the user can tell them apart
        assertEquals(true, guest != null && !guest.trim().isEmpty());
        assertEquals(true, free != null && !free.trim().isEmpty());
        assertEquals(true, pro != null && !pro.trim().isEmpty());
        assertEquals(false, guest.equals(free));
        assertEquals(false, free.equals(pro));
        assertEquals(false, guest.equals(pro));
        // the Pro badge must actually say "Pro" so recognition is unmistakable
        assertEquals(true, pro.toLowerCase().contains("pro"));
    }

    @Test
    public void proTeaserGateStaysConsistentWithAccountStatus()
    {
        // showProTeaser must be exactly "not Pro" — the two helpers can never disagree,
        // or a payer could see the badge say Pro while the upsell still shows.
        for (Entitlement e : new Entitlement[]{
            null, EntitlementService.LOCKED,
            new Entitlement(true, "free", true), new Entitlement(true, "pro", true)})
        {
            boolean teaser = GrandFlipOutPanel.showProTeaser(e);
            boolean notPro = GrandFlipOutPanel.accountStatus(e) != AccountStatus.PRO;
            assertEquals("teaser gate must equal (status != PRO)", notPro, teaser);
        }
    }
}
