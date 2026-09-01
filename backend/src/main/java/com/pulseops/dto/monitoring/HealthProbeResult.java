package com.pulseops.dto.monitoring;

import com.pulseops.exception.BusinessRuleException;

public record HealthProbeResult(
        Integer httpStatus,
        long responseTimeMs,
        ProbeFailureType failureType,
        String errorMessage
) {

    public HealthProbeResult {
        if (responseTimeMs < 0) {
            throw new BusinessRuleException("Response time cannot be negative");
        }
        failureType = failureType == null ? ProbeFailureType.UNEXPECTED : failureType;
        if (failureType == ProbeFailureType.NONE && httpStatus == null) {
            throw new BusinessRuleException("A successful probe transport must contain an HTTP status");
        }
    }

    public static HealthProbeResult response(int httpStatus, long responseTimeMs) {
        return new HealthProbeResult(httpStatus, responseTimeMs, ProbeFailureType.NONE, null);
    }

    public static HealthProbeResult failure(
            ProbeFailureType failureType,
            long responseTimeMs,
            String errorMessage
    ) {
        if (failureType == null || failureType == ProbeFailureType.NONE) {
            throw new BusinessRuleException("A transport failure requires a failure type");
        }
        return new HealthProbeResult(null, responseTimeMs, failureType, errorMessage);
    }

    public boolean reachedServer() {
        return httpStatus != null;
    }
}
