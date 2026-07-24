/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.api;

import java.util.UUID;

/**
 * The plugin's EPHEMERAL per-launch instance token for the single-live-instance
 * lease (#242). Generated ONCE per JVM launch, held in memory only, never
 * persisted and never derived from the RuneScape account/identity — it exists
 * solely so the server can tell two concurrent launches of the same Pro key apart
 * ("one Pro = one live instance"). A fresh launch = a fresh token, by design.
 *
 * Sent as the {@code X-GFO-Instance} header alongside the Bearer key on every
 * authenticated grandflipout.com call; the server enforces nothing unless it is
 * both configured on and the token is present (dark-safe).
 */
public final class InstanceId
{
    private static final String ID = UUID.randomUUID().toString();

    private InstanceId()
    {
    }

    /** The stable per-launch instance token. */
    public static String get()
    {
        return ID;
    }

    /** Header name the server reads the token from. */
    public static final String HEADER = "X-GFO-Instance";
}
