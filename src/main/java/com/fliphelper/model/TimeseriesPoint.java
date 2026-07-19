/*
 * Copyright (c) 2026, Tunatroll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.model;

import lombok.Builder;
import lombok.Data;

/**
 * A single point from the OSRS Wiki /timeseries endpoint.
 * Prices are in gp; a value of 0 means "no data" for that point/side.
 */
@Data
@Builder
public class TimeseriesPoint
{
    /** Unix epoch seconds for the start of this timestep bucket. */
    private final long timestamp;
    private final long avgHighPrice;
    private final long avgLowPrice;
    private final long highPriceVolume;
    private final long lowPriceVolume;
}
