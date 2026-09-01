package com.pulseops.service.monitoring;

import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.monitoring.HealthProbeResult;
import com.pulseops.dto.monitoring.MonitoringDecision;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.HealthCheckRepository;
import com.pulseops.repository.MonitoredSystemRepository;
import com.pulseops.service.incident.IncidentAutomationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Persists the outcome of one already completed external probe.
 *
 * <p>The pessimistic lock serializes state transitions for the same system while the transaction
 * keeps the health check, system status and automatic incident decision atomic. No network call is
 * made from this transaction.</p>
 */
@Service
public class MonitoringPersistenceService {

    private final MonitoredSystemRepository systemRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final MonitoringStatusEvaluator statusEvaluator;
    private final IncidentAutomationService incidentAutomationService;
    private final Clock clock;

    public MonitoringPersistenceService(
            MonitoredSystemRepository systemRepository,
            HealthCheckRepository healthCheckRepository,
            MonitoringStatusEvaluator statusEvaluator,
            IncidentAutomationService incidentAutomationService,
            Clock clock
    ) {
        this.systemRepository = Objects.requireNonNull(systemRepository);
        this.healthCheckRepository = Objects.requireNonNull(healthCheckRepository);
        this.statusEvaluator = Objects.requireNonNull(statusEvaluator);
        this.incidentAutomationService = Objects.requireNonNull(incidentAutomationService);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public HealthCheck record(MonitoredSystem probeTarget, HealthProbeResult probe) {
        Objects.requireNonNull(probeTarget, "probe target is required");
        Objects.requireNonNull(probe, "probe result is required");

        UUID systemId = Objects.requireNonNull(probeTarget.getId(), "probe target id is required");
        MonitoredSystem persistedSystem = systemRepository.findByIdForUpdate(systemId)
                .orElseThrow(() -> new ResourceNotFoundException("Monitored system", systemId));
        if (!persistedSystem.isActive()) {
            throw new BusinessRuleException("Inactive systems cannot be monitored");
        }

        List<HealthCheck> previousChecks = healthCheckRepository
                .findTop10ByMonitoredSystemIdOrderByCheckedAtDesc(systemId);
        MonitoringDecision decision = statusEvaluator.evaluate(probeTarget, probe, previousChecks);

        HealthCheck persistedCheck = healthCheckRepository.save(toHealthCheck(persistedSystem, probe, decision));
        persistedSystem.setStatus(decision.status());
        systemRepository.save(persistedSystem);
        incidentAutomationService.evaluate(persistedSystem, persistedCheck, decision, previousChecks);
        return persistedCheck;
    }

    private HealthCheck toHealthCheck(
            MonitoredSystem system,
            HealthProbeResult probe,
            MonitoringDecision decision
    ) {
        HealthCheck check = new HealthCheck();
        check.setMonitoredSystem(system);
        check.setCheckedAt(OffsetDateTime.now(clock));
        check.setHttpStatus(probe.httpStatus());
        check.setResponseTimeMs(probe.responseTimeMs());
        check.setSuccess(decision.successful());
        check.setErrorMessage(decision.successful() ? null : errorMessage(probe, decision));
        return check;
    }

    private String errorMessage(HealthProbeResult probe, MonitoringDecision decision) {
        if (probe.errorMessage() == null || probe.errorMessage().isBlank()) {
            return decision.reason();
        }
        return "%s: %s".formatted(decision.reason(), probe.errorMessage());
    }
}
