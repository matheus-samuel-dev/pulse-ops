package com.pulseops.dto.dashboard;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record LatencyPointResponse(
        OffsetDateTime timestamp,
        BigDecimal averageMs,
        Long p95Ms,
        long samples
) {
}
