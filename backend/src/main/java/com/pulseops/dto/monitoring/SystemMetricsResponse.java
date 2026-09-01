package com.pulseops.dto.monitoring;

public record SystemMetricsResponse(
        String period,
        AvailabilityMetrics availability,
        LatencyMetrics latency,
        SlaMetrics sla
) {
}
