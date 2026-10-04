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
        Objects.requireNonNull(system, "system é obrigatório");
        Objects.requireNonNull(probe, "probe é obrigatório");
        previousChecks = previousChecks == null ? List.of() : previousChecks;

        if (probe.failureType() != ProbeFailureType.NONE) {
            return new MonitoringDecision(
                    probe.failureType()==ProbeFailureType.SECURITY_POLICY ? SystemStatus.CONFIGURATION_REQUIRED : 1+countConsecutiveFailures(previousChecks)>=failuresForDown ? SystemStatus.DOWN : SystemStatus.DEGRADED,
                    false,
                    transportFailureReason(probe.failureType())
            );
        }

        boolean expectedStatus = probe.httpStatus() == system.getExpectedStatusCode();
        if (!expectedStatus) {
            long consecutiveFailures = 1 + countConsecutiveFailures(previousChecks);
            if (consecutiveFailures >= failuresForDown) {
                return new MonitoringDecision(
                        SystemStatus.DOWN,
                        false,
                        "O endpoint retornou HTTP %d; esperado HTTP %d".formatted(
                                probe.httpStatus(), system.getExpectedStatusCode())
                );
            }
            return new MonitoringDecision(
                    SystemStatus.DEGRADED,
                    false,
                    "O endpoint retornou HTTP %d; esperado HTTP %d".formatted(
                            probe.httpStatus(), system.getExpectedStatusCode())
            );
        }

        if (probe.responseTimeMs() > system.getLatencyThresholdMs()) {
            return new MonitoringDecision(
                    SystemStatus.DEGRADED,
                    true,
                    "Resposta de %d ms acima do limite de %d ms".formatted(
                            probe.responseTimeMs(), system.getLatencyThresholdMs())
            );
        }

        long recentFailures = previousChecks.stream()
                .limit(Math.max(0, recentWindowSize - 1L))
                .filter(check -> !check.isSuccess())
                .count();
        boolean recovered = !previousChecks.isEmpty() && previousChecks.getFirst().isSuccess()
                && previousChecks.getFirst().getHttpStatus() != null
                && previousChecks.getFirst().getHttpStatus() == system.getExpectedStatusCode()
                && previousChecks.getFirst().getResponseTimeMs() <= system.getLatencyThresholdMs();
        if (!recovered && recentFailures >= recentFailuresForDegraded) {
            return new MonitoringDecision(
                    SystemStatus.DEGRADED,
                    true,
                    "%d falhas recentes indicam instabilidade".formatted(recentFailures)
            );
        }

        return new MonitoringDecision(SystemStatus.OPERATIONAL, true, "Verificação bem-sucedida");
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
            case TIMEOUT -> "O sistema não respondeu dentro do tempo limite";
            case DNS -> "Não foi possível resolver o endereço do sistema";
            case CONNECTION_REFUSED -> "Não foi possível conectar ao sistema monitorado";
            case NETWORK -> "Falha de comunicação com o sistema monitorado";
            case SECURITY_POLICY -> "Destino bloqueado pela política de segurança";
            case UNEXPECTED -> "Não foi possível concluir a verificação";
            case NONE -> "O sistema respondeu à verificação";
        };
    }
}
