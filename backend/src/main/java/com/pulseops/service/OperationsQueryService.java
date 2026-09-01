package com.pulseops.service;

import com.pulseops.domain.deployment.Deployment;
import com.pulseops.domain.incident.Incident;
import com.pulseops.domain.incident.IncidentSeverity;
import com.pulseops.domain.incident.IncidentStatus;
import com.pulseops.dto.deployment.DeploymentResponse;
import com.pulseops.dto.incident.IncidentResponse;
import com.pulseops.dto.monitoring.HealthCheckResponse;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.DeploymentRepository;
import com.pulseops.repository.HealthCheckRepository;
import com.pulseops.repository.IncidentRepository;
import com.pulseops.repository.MonitoredSystemRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsQueryService {

    private final IncidentRepository incidentRepository;
    private final DeploymentRepository deploymentRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final MonitoredSystemRepository systemRepository;

    public OperationsQueryService(
            IncidentRepository incidentRepository,
            DeploymentRepository deploymentRepository,
            HealthCheckRepository healthCheckRepository,
            MonitoredSystemRepository systemRepository
    ) {
        this.incidentRepository = incidentRepository;
        this.deploymentRepository = deploymentRepository;
        this.healthCheckRepository = healthCheckRepository;
        this.systemRepository = systemRepository;
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> incidents(UUID systemId, IncidentStatus status, IncidentSeverity severity) {
        List<Incident> incidents = systemId == null
                ? incidentRepository.findAll()
                : incidentRepository.findByMonitoredSystemIdOrderByStartedAtDesc(systemId);
        if (systemId != null) {
            ensureSystemExists(systemId);
        }
        return incidents.stream()
                .filter(item -> status == null || item.getStatus() == status)
                .filter(item -> severity == null || item.getSeverity() == severity)
                .sorted(Comparator.comparing(Incident::getStartedAt).reversed())
                .map(IncidentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeploymentResponse> deployments(UUID systemId) {
        List<Deployment> deployments = systemId == null
                ? deploymentRepository.findAll()
                : deploymentRepository.findByMonitoredSystemIdOrderByDeployedAtDesc(systemId);
        if (systemId != null) {
            ensureSystemExists(systemId);
        }
        return deployments.stream()
                .sorted(Comparator.comparing(Deployment::getDeployedAt).reversed())
                .map(DeploymentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<HealthCheckResponse> healthChecks(UUID systemId, int page, int size) {
        ensureSystemExists(systemId);
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "checkedAt"));
        return healthCheckRepository.findByMonitoredSystemIdOrderByCheckedAtDesc(systemId, pageable)
                .map(HealthCheckResponse::from);
    }

    private void ensureSystemExists(UUID systemId) {
        if (!systemRepository.existsById(systemId)) {
            throw new ResourceNotFoundException("Monitored system", systemId);
        }
    }
}
