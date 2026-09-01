package com.pulseops.dto.monitoring;

import com.pulseops.dto.common.TimeRange;

import java.math.BigDecimal;

public record LatencyMetrics(
        TimeRange period,
        long sampleCount,
        BigDecimal averageMs,
        Long minimumMs,
        Long maximumMs,
        Long p95Ms
) {
}
