package com.pulseops.dto.monitoring;

import java.util.List;
import java.util.UUID;

public record MonitoringBatchResult(
        int attempted,
        int completed,
        List<MonitoringFailure> failures
) {
    public MonitoringBatchResult {
        failures = List.copyOf(failures);
    }

    public record MonitoringFailure(UUID systemId, String systemName, String reason) {
    }
}
