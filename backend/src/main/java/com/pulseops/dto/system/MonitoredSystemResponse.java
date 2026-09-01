package com.pulseops.dto.system;

import com.pulseops.domain.system.Environment;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.domain.system.SystemStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MonitoredSystemResponse(
        UUID id,
        String name,
        String description,
        String baseUrl,
        String healthEndpoint,
        Environment environment,
        SystemStatus status,
        boolean active,
        int expectedStatusCode,
        int timeoutMs,
        long latencyThresholdMs,
        BigDecimal targetAvailability,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static MonitoredSystemResponse from(MonitoredSystem system) {
        return new MonitoredSystemResponse(
                system.getId(), system.getName(), system.getDescription(), system.getBaseUrl(),
                system.getHealthEndpoint(), system.getEnvironment(), system.getStatus(), system.isActive(),
                system.getExpectedStatusCode(), system.getTimeoutMs(), system.getLatencyThresholdMs(),
                system.getTargetAvailability(), system.getCreatedAt(), system.getUpdatedAt());
    }
}
