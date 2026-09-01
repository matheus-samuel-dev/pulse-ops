package com.pulseops.service;

import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.domain.system.SystemStatus;
import com.pulseops.dto.system.MonitoredSystemRequest;
import com.pulseops.dto.system.MonitoredSystemResponse;
import com.pulseops.exception.ConflictException;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.MonitoredSystemRepository;
import com.pulseops.security.outbound.MonitoredUrlPolicy;
import com.pulseops.security.outbound.ValidatedMonitoredUrl;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoredSystemService {

    private final MonitoredSystemRepository repository;
    private final MonitoredUrlPolicy monitoredUrlPolicy;

    public MonitoredSystemService(
            MonitoredSystemRepository repository,
            MonitoredUrlPolicy monitoredUrlPolicy
    ) {
        this.repository = repository;
        this.monitoredUrlPolicy = monitoredUrlPolicy;
    }

    @Transactional(readOnly = true)
    public List<MonitoredSystemResponse> findAll() {
        return repository.findAll().stream()
                .sorted(java.util.Comparator.comparing(MonitoredSystem::getName, String.CASE_INSENSITIVE_ORDER))
                .map(MonitoredSystemResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonitoredSystem findEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Monitored system", id));
    }

    @Transactional(readOnly = true)
    public MonitoredSystemResponse findById(UUID id) {
        return MonitoredSystemResponse.from(findEntity(id));
    }

    @Transactional
    public MonitoredSystemResponse create(MonitoredSystemRequest request) {
        ensureNameAvailable(request.name(), null);
        MonitoredSystem system = new MonitoredSystem();
        apply(system, request);
        system.setStatus(SystemStatus.UNKNOWN);
        return MonitoredSystemResponse.from(repository.save(system));
    }

    @Transactional
    public MonitoredSystemResponse update(UUID id, MonitoredSystemRequest request) {
        MonitoredSystem system = findEntity(id);
        ensureNameAvailable(request.name(), id);
        apply(system, request);
        return MonitoredSystemResponse.from(repository.save(system));
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(findEntity(id));
    }

    private void apply(MonitoredSystem system, MonitoredSystemRequest request) {
        ValidatedMonitoredUrl monitoredUrl = monitoredUrlPolicy.validate(
                request.baseUrl(), request.healthEndpoint());
        system.setName(request.name().trim());
        system.setDescription(blankToNull(request.description()));
        system.setBaseUrl(monitoredUrl.baseUrl());
        system.setHealthEndpoint(monitoredUrl.healthEndpoint());
        system.setEnvironment(request.environment());
        system.setActive(request.active());
        system.setExpectedStatusCode(request.expectedStatusCode());
        system.setTimeoutMs(request.timeoutMs());
        system.setLatencyThresholdMs(request.latencyThresholdMs());
        system.setTargetAvailability(request.targetAvailability());
    }

    private void ensureNameAvailable(String name, UUID currentId) {
        repository.findByNameIgnoreCase(name.trim()).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new ConflictException("Já existe um sistema monitorado com este nome");
            }
        });
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
