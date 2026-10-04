package com.pulseops.dto.monitoring;

import com.pulseops.dto.common.TimeRange;

import java.math.BigDecimal;

public record AvailabilityMetrics(
        TimeRange period,
        long totalChecks,
        long successfulChecks,
        long failedChecks,
        BigDecimal availabilityPercentage,
        long eligibleChecks,
        long excludedChecks
) {
    public AvailabilityMetrics(TimeRange period,long total,long successful,long failed,BigDecimal percentage){this(period,total,successful,failed,percentage,total,0);}
}
