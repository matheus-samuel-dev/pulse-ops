package com.pulseops.dto.monitoring;

import com.pulseops.dto.common.TimeRange;

import java.math.BigDecimal;

public record AvailabilityMetrics(
        TimeRange period,
        long totalChecks,
        long successfulChecks,
        long failedChecks,
        BigDecimal availabilityPercentage
) {
}
