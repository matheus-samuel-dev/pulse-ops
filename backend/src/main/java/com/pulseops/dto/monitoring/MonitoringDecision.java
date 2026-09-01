package com.pulseops.dto.monitoring;

import com.pulseops.domain.system.SystemStatus;

import java.util.Objects;

public record MonitoringDecision(
        SystemStatus status,
        boolean successful,
        String reason
) {
    public MonitoringDecision {
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(reason, "reason is required");
    }
}
