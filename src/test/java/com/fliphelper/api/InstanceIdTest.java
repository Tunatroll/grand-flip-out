/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * #242: the per-launch instance token must be STABLE within a launch (so the server
 * sees one consistent holder while the plugin runs) and non-empty. A fresh launch =
 * a fresh token is by design (new JVM = new static), and can't be unit-asserted here.
 */
public class InstanceIdTest
{
    @Test
    public void tokenIsStableAcrossCallsWithinALaunch()
    {
        String a = InstanceId.get();
        String b = InstanceId.get();
        assertNotNull("token must exist", a);
        assertEquals("the same launch always reports the same token (a stable lease holder)", a, b);
        assertTrue("non-trivial length", a.length() >= 16);
    }

    @Test
    public void headerNameIsTheServerContract()
    {
        assertEquals("X-GFO-Instance", InstanceId.HEADER);
    }
}
