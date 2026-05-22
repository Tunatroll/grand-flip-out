package com.fliphelper.model;

import lombok.Builder;
import lombok.Value;

/**
 * Read-only opportunity from GFO server (living-nn proxy).
 */
@Value
@Builder
public class ServerOpportunity
{
    int itemId;
    String name;
    String signalType;
    long buyPrice;
    long sellPrice;
    double gpPerHour;
    String grade;
}
