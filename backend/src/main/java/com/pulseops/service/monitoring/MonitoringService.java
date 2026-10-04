package com.pulseops.service.monitoring;

import com.pulseops.client.HealthCheckClient;
import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.monitoring.HealthProbeResult;
import com.pulseops.dto.monitoring.ProbeFailureType;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.MonitoredSystemRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class MonitoringService {

    private final MonitoredSystemRepository systemRepository;
    private final HealthCheckClient healthCheckClient;
    private final MonitoringPersistenceService persistenceService;

    public MonitoringService(
            MonitoredSystemRepository systemRepository,
            HealthCheckClient healthCheckClient,
            MonitoringPersistenceService persistenceService
    ) {
        this.systemRepository = Objects.requireNonNull(systemRepository);
        this.healthCheckClient = Objects.requireNonNull(healthCheckClient);
        this.persistenceService = Objects.requireNonNull(persistenceService);
    }

    public HealthCheck checkSystem(UUID systemId) {
        MonitoredSystem probeTarget = systemRepository.findById(systemId)
                .orElseThrow(() -> new ResourceNotFoundException("Monitored system", systemId));
        if (!probeTarget.isActive() || probeTarget.isMaintenance()) {
            throw new BusinessRuleException("Sistemas inativos não podem ser monitorados");
        }

        HealthProbeResult probe = safelyProbe(probeTarget);
        return persistenceService.record(probeTarget, probe);
    }

    private HealthProbeResult safelyProbe(MonitoredSystem system) {
        try {
            HealthProbeResult result = healthCheckClient.probe(system);
            if (result == null) {
                return HealthProbeResult.failure(
                        ProbeFailureType.UNEXPECTED, 0, "Health check client returned no result");
            }
            return result;
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            return HealthProbeResult.failure(ProbeFailureType.UNEXPECTED, 0, message);
        }
    }

}
