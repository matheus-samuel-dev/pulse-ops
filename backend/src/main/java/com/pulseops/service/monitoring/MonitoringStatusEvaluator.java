package com.pulseops.service.monitoring;

import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.domain.system.SystemStatus;
import com.pulseops.dto.monitoring.HealthProbeResult;
import com.pulseops.dto.monitoring.MonitoringDecision;
import com.pulseops.dto.monitoring.ProbeFailureType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class MonitoringStatusEvaluator {

    private final int failuresForDown;
    private final int recentWindowSize;
    private final int recentFailuresForDegraded;

    public MonitoringStatusEvaluator(
            @Value("${pulseops.monitoring.failures-for-down:3}") int failuresForDown,
            @Value("${pulseops.monitoring.recent-window-size:5}") int recentWindowSize,
            @Value("${pulseops.monitoring.recent-failures-for-degraded:2}") int recentFailuresForDegraded
    ) {
        if (failuresForDown < 1) {
            throw new IllegalArgumentException("failuresForDown must be positive");
        }
        if (recentWindowSize < 1) {
            throw new IllegalArgumentException("recentWindowSize must be positive");
        }
        if (recentFailuresForDegraded < 1 || recentFailuresForDegraded > recentWindowSize) {
            throw new IllegalArgumentException("recentFailuresForDegraded must fit inside the recent window");
        }
        this.failuresForDown = failuresForDown;
        this.recentWindowSize = recentWindowSize;
        this.recentFailuresForDegraded = recentFailuresForDegraded;
    }

    /**
     * Evaluates the latest probe. Previous checks must be ordered from newest to oldest and must not
     * include the current probe.
     */
    public MonitoringDecision evaluate(
            MonitoredSystem system,
            HealthProbeResult probe,
            List<HealthCheck> previousChecks
    ) {
        Objects.requireNonNull(system, "system is required");
        Objects.requireNonNull(probe, "probe is required");
        previousChecks = previousChecks == null ? List.of() : previousChecks;

        if (probe.failureType() != ProbeFailureType.NONE) {
            return new MonitoringDecision(
                    SystemStatus.DOWN,
                    false,
                    transportFailureReason(probe.failureType())
            );
        }

        boolean expectedStatus = probe.httpStatus() == system.getExpectedStatusCode();
        if (!expectedStatus) {
            long consecutiveFailures = 1 + countConsecutiveFailures(previousChecks);
            boolean criticalHttpStatus = probe.httpStatus() >= 500;
            if (criticalHttpStatus || consecutiveFailures >= failuresForDown) {
                return new MonitoringDecision(
                        SystemStatus.DOWN,
                        false,
                        "Unexpected HTTP status %d (expected %d)".formatted(
                                probe.httpStatus(), system.getExpectedStatusCode())
                );
            }
            return new MonitoringDecision(
                    SystemStatus.DEGRADED,
                    false,
                    "Unexpected HTTP status %d (expected %d)".formatted(
                            probe.httpStatus(), system.getExpectedStatusCode())
            );
        }

        if (probe.responseTimeMs() > system.getLatencyThresholdMs()) {
            return new MonitoringDecision(
                    SystemStatus.DEGRADED,
                    true,
                    "Latency %d ms exceeded the %d ms threshold".formatted(
                            probe.responseTimeMs(), system.getLatencyThresholdMs())
            );
        }

        long recentFailures = previousChecks.stream()
                .limit(Math.max(0, recentWindowSize - 1L))
                .filter(check -> !check.isSuccess())
                .count();
        if (recentFailures >= recentFailuresForDegraded) {
            return new MonitoringDecision(
                    SystemStatus.DEGRADED,
                    true,
                    "%d recent failures indicate instability".formatted(recentFailures)
            );
        }

        return new MonitoringDecision(SystemStatus.OPERATIONAL, true, "Health check succeeded");
    }

    private long countConsecutiveFailures(List<HealthCheck> checks) {
        long failures = 0;
        for (HealthCheck check : checks) {
            if (check.isSuccess()) {
                break;
            }
            failures++;
        }
        return failures;
    }

    private String transportFailureReason(ProbeFailureType failureType) {
        return switch (failureType) {
            case TIMEOUT -> "Health check timed out";
            case DNS -> "Health check failed due to DNS resolution";
            case CONNECTION_REFUSED -> "Health check connection was refused";
            case NETWORK -> "Health check failed due to a network error";
            case SECURITY_POLICY -> "Health check target was blocked by the outbound security policy";
            case UNEXPECTED -> "Health check failed unexpectedly";
            case NONE -> "Health check reached the remote system";
        };
    }
}
