package com.pulseops.dto.monitoring;

import com.pulseops.domain.monitoring.HealthCheck;
import java.time.OffsetDateTime;
import java.util.UUID;

public record HealthCheckResponse(
        UUID id,
        UUID systemId,
        OffsetDateTime checkedAt,
        Integer httpStatus,
        long responseTimeMs,
        boolean success,
        String errorMessage,
        String failureType
) {
    public static HealthCheckResponse from(HealthCheck check) {
        return new HealthCheckResponse(
                check.getId(), check.getMonitoredSystem().getId(), check.getCheckedAt(), check.getHttpStatus(),
                check.getResponseTimeMs(), check.isSuccess(), check.getErrorMessage(),check.getFailureType());
    }
}
