package com.pulseops.dto.monitoring;

import com.pulseops.dto.common.TimeRange;

import java.math.BigDecimal;

public record SlaMetrics(
        TimeRange period,
        BigDecimal currentAvailability,
        BigDecimal targetAvailability,
        boolean targetMet,
        BigDecimal differencePercentagePoints,
        SlaStatus status,
        long evaluatedChecks
) {
}
