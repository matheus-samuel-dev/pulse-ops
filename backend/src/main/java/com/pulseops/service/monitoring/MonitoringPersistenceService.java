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
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.pulseops.service.EventRecorder events;


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
        Objects.requireNonNull(probeTarget, "probe target é obrigatório");
        Objects.requireNonNull(probe, "probe result é obrigatório");

        UUID systemId = Objects.requireNonNull(probeTarget.getId(), "probe target id é obrigatório");
        MonitoredSystem persistedSystem = systemRepository.findByIdForUpdate(systemId)
                .orElseThrow(() -> new ResourceNotFoundException("Monitored system", systemId));
        if (!persistedSystem.isActive() || persistedSystem.isMaintenance()) {
            throw new BusinessRuleException("Sistemas inativos não podem ser monitorados");
        }

        if (!Objects.equals(persistedSystem.getBaseUrl(),probeTarget.getBaseUrl()) || !Objects.equals(persistedSystem.getHealthEndpoint(),probeTarget.getHealthEndpoint()) || persistedSystem.getExpectedStatusCode()!=probeTarget.getExpectedStatusCode() || persistedSystem.getTimeoutMs()!=probeTarget.getTimeoutMs() || persistedSystem.getLatencyThresholdMs()!=probeTarget.getLatencyThresholdMs() || persistedSystem.getEnvironment()!=probeTarget.getEnvironment()) throw new BusinessRuleException("A configuração mudou durante a verificação. O resultado antigo foi descartado.");
        List<HealthCheck> previousChecks = healthCheckRepository
                .findTop10ByMonitoredSystemIdOrderByCheckedAtDesc(systemId);
        MonitoringDecision decision = statusEvaluator.evaluate(probeTarget, probe, previousChecks);

        HealthCheck persistedCheck = healthCheckRepository.save(toHealthCheck(persistedSystem, probe, decision));
        com.pulseops.domain.system.SystemStatus previousStatus = persistedSystem.getStatus();
        persistedSystem.setStatus(decision.status());
        persistedSystem.setStatusReason(decision.reason());
        if (previousStatus != decision.status()) persistedSystem.setStatusChangedAt(persistedCheck.getCheckedAt());
        if (!decision.successful()) persistedSystem.setLastFailureAt(persistedCheck.getCheckedAt());
        systemRepository.save(persistedSystem);
        if (events != null) {
            events.recordResource(persistedSystem, "HEALTH_CHECK", decision.successful() ? "SUCCESS" : "WARNING", "Verificação executada", probe.httpStatus()==null ? decision.reason() : "HTTP "+probe.httpStatus()+" · "+probe.responseTimeMs()+" ms · "+decision.reason(), "Monitoramento PulseOps",persistedCheck.getId(),decision.successful() ? "SUCCESS" : "FAILED");
            if (previousStatus != decision.status()) {
                String type = decision.status() == com.pulseops.domain.system.SystemStatus.DOWN ? "SYSTEM_DOWN" : decision.status() == com.pulseops.domain.system.SystemStatus.OPERATIONAL ? previousStatus == com.pulseops.domain.system.SystemStatus.UNKNOWN ? "SYSTEM_OPERATIONAL" : "SYSTEM_RECOVERED" : decision.status()==com.pulseops.domain.system.SystemStatus.CONFIGURATION_REQUIRED ? "SYSTEM_CONFIGURATION_REQUIRED" : "SYSTEM_DEGRADED";
                events.record(persistedSystem, type, type.equals("SYSTEM_DOWN") ? "CRITICAL" : type.equals("SYSTEM_RECOVERED") ? "SUCCESS" : "WARNING", "Estado do sistema alterado", decision.reason(), "Monitoramento PulseOps");
            }
        }
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
        check.setFailureType(probe.failureType()!=com.pulseops.dto.monitoring.ProbeFailureType.NONE ? probe.failureType().name() : decision.successful() ? "NONE" : "HTTP_STATUS");
        check.setErrorMessage(decision.successful() ? null : errorMessage(probe, decision));
        return check;
    }

    private String errorMessage(HealthProbeResult probe, MonitoringDecision decision) { return decision.reason(); }
}
