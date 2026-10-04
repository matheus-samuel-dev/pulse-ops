package com.pulseops.service.incident;

import com.pulseops.domain.incident.Incident;
import com.pulseops.domain.incident.IncidentSeverity;
import com.pulseops.domain.incident.IncidentStatus;
import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.domain.system.SystemStatus;
import com.pulseops.dto.monitoring.MonitoringDecision;
import com.pulseops.repository.IncidentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

@Service
public class IncidentAutomationService {

    public static final String AUTOMATIC_INCIDENT_TITLE = "Indisponibilidade detectada automaticamente";
    private static final Collection<IncidentStatus> ACTIVE_STATUSES =
            EnumSet.of(IncidentStatus.OPEN, IncidentStatus.INVESTIGATING);

    private final IncidentRepository incidentRepository;
    private final IncidentService incidentService;
    private final Clock clock;
    private final int failuresToOpen;
    private final int successesToResolve;
    private final int failuresForCriticalSeverity;

    public IncidentAutomationService(
            IncidentRepository incidentRepository,
            IncidentService incidentService,
            Clock clock,
            @Value("${pulseops.monitoring.incident.failures-to-open:3}") int failuresToOpen,
            @Value("${pulseops.monitoring.incident.successes-to-resolve:2}") int successesToResolve,
            @Value("${pulseops.monitoring.incident.failures-for-critical:5}") int failuresForCriticalSeverity
    ) {
        if (failuresToOpen < 1 || successesToResolve < 1) {
            throw new IllegalArgumentException("Incident automation thresholds must be positive");
        }
        if (failuresForCriticalSeverity < failuresToOpen) {
            throw new IllegalArgumentException("Critical threshold cannot precede incident creation");
        }
        this.incidentRepository = Objects.requireNonNull(incidentRepository);
        this.incidentService = Objects.requireNonNull(incidentService);
        this.clock = Objects.requireNonNull(clock);
        this.failuresToOpen = failuresToOpen;
        this.successesToResolve = successesToResolve;
        this.failuresForCriticalSeverity = failuresForCriticalSeverity;
    }

    /** Previous checks must be ordered from newest to oldest and exclude currentCheck. */
    @Transactional
    public void evaluate(
            MonitoredSystem system,
            HealthCheck currentCheck,
            MonitoringDecision decision,
            List<HealthCheck> previousChecks
    ) {
        Objects.requireNonNull(system, "system é obrigatório");
        Objects.requireNonNull(currentCheck, "currentCheck é obrigatório");
        Objects.requireNonNull(decision, "decision é obrigatório");
        previousChecks = previousChecks == null ? List.of() : previousChecks;

        if ("SECURITY_POLICY".equals(currentCheck.getFailureType())) return;
        List<Incident> activeAutomaticIncidents = activeAutomaticIncidents(system);
        if (!currentCheck.isSuccess()) {
            long consecutiveFailures = 1 + countConsecutiveFailures(previousChecks);
            if (consecutiveFailures >= failuresToOpen && activeAutomaticIncidents.isEmpty()) {
                IncidentSeverity severity = consecutiveFailures >= failuresForCriticalSeverity
                        ? IncidentSeverity.CRITICAL
                        : IncidentSeverity.HIGH;
                incidentService.createAutomatic(
                        system,
                        AUTOMATIC_INCIDENT_TITLE,
                        automaticDescription(system, decision, consecutiveFailures),
                        severity,
                        previousChecks.stream().takeWhile(check -> !check.isSuccess()).map(HealthCheck::getCheckedAt).filter(Objects::nonNull).min(OffsetDateTime::compareTo).orElse(currentCheck.getCheckedAt() == null ? OffsetDateTime.now(clock) : currentCheck.getCheckedAt())
                );
            } else if (consecutiveFailures >= failuresForCriticalSeverity) {
                activeAutomaticIncidents.stream()
                        .filter(incident -> incident.getSeverity() != IncidentSeverity.CRITICAL)
                        .forEach(incident -> {
                            incident.setSeverity(IncidentSeverity.CRITICAL);
                            incidentRepository.save(incident);
                        });
            }
            return;
        }

        if (decision.status() == SystemStatus.OPERATIONAL
                && isFullyHealthy(currentCheck, system)
                && !activeAutomaticIncidents.isEmpty()) {
            long consecutiveHealthyChecks = 1 + countConsecutiveHealthy(previousChecks, system);
            if (consecutiveHealthyChecks >= successesToResolve) {
                OffsetDateTime resolvedAt = currentCheck.getCheckedAt() == null
                        ? OffsetDateTime.now(clock)
                        : currentCheck.getCheckedAt();
                activeAutomaticIncidents.forEach(incident -> incidentService.resolve(incident, resolvedAt));
            }
        }
    }

    private List<Incident> activeAutomaticIncidents(MonitoredSystem system) {
        return incidentRepository
                .findByMonitoredSystemIdAndStatusInOrderByStartedAtDesc(system.getId(), ACTIVE_STATUSES)
                .stream()
                .filter(Incident::isAutomatic)
                .toList();
    }

    private long countConsecutiveFailures(List<HealthCheck> checks) {
        long count = 0;
        for (HealthCheck check : checks) {
            if (check.isSuccess()) {
                break;
            }
            count++;
        }
        return count;
    }

    private long countConsecutiveHealthy(List<HealthCheck> checks, MonitoredSystem system) {
        long count = 0;
        for (HealthCheck check : checks) {
            if (!isFullyHealthy(check, system)) {
                break;
            }
            count++;
        }
        return count;
    }

    private boolean isFullyHealthy(HealthCheck check, MonitoredSystem system) {
        return check.isSuccess()
                && check.getHttpStatus() != null
                && check.getHttpStatus() == system.getExpectedStatusCode()
                && check.getResponseTimeMs() <= system.getLatencyThresholdMs();
    }

    private String automaticDescription(
            MonitoredSystem system,
            MonitoringDecision decision,
            long consecutiveFailures
    ) {
        return "O PulseOps detectou %d verificações consecutivas com falha em %s. Resultado mais recente: %s"
                .formatted(consecutiveFailures, system.getName(), decision.reason());
    }
}
