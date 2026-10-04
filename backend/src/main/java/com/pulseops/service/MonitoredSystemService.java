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
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.pulseops.service.EventRecorder events;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.pulseops.repository.HealthCheckRepository checks;


    @org.springframework.beans.factory.annotation.Autowired(required=false) private org.springframework.jdbc.core.JdbcTemplate evidenceDatabase;
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
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonitoredSystem findEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Monitored system", id));
    }

    @Transactional(readOnly = true)
    public MonitoredSystemResponse findById(UUID id) {
        return response(findEntity(id));
    }

    @Transactional
    public MonitoredSystemResponse create(MonitoredSystemRequest request) {
        ensureNameAvailable(request.name(), null);
        MonitoredSystem system = new MonitoredSystem();
        system.setOwnerId(com.pulseops.security.AccountScope.userId());
        apply(system, request);
        system.setStatus(request.maintenance() ? SystemStatus.MAINTENANCE : SystemStatus.UNKNOWN);
        MonitoredSystem saved = repository.save(system);
        if (events != null) events.record(saved, "SYSTEM_CREATED", "INFO", "Sistema cadastrado", "Monitoramento configurado; aguardando primeira verificação.", "Conta PulseOps");
        return MonitoredSystemResponse.from(saved);
    }

    @Transactional
    public MonitoredSystemResponse update(UUID id, MonitoredSystemRequest request) {
        MonitoredSystem system = findEntity(id);
        ensureNameAvailable(request.name(), id);
        if(system.getEnvironment()!=request.environment() && evidenceDatabase!=null && Boolean.TRUE.equals(evidenceDatabase.queryForObject("select exists(select 1 from health_checks where monitored_system_id=? union all select 1 from incidents where monitored_system_id=? union all select 1 from deployments where monitored_system_id=? union all select 1 from test_reports where monitored_system_id=?)",Boolean.class,id,id,id,id))) throw new com.pulseops.exception.BusinessRuleException("O ambiente possui histórico. Cadastre um sistema separado para acompanhar o novo ambiente.");
        boolean targetChanged = !system.getBaseUrl().equals(request.baseUrl()) || !system.getHealthEndpoint().equals(request.healthEndpoint()) || system.getExpectedStatusCode() != request.expectedStatusCode();
        boolean maintenanceChanged=system.isMaintenance()!=request.maintenance();
        boolean wasActive=system.isActive();
        apply(system, request);
        if (targetChanged || maintenanceChanged || wasActive!=request.active()) {
            system.setStatus(SystemStatus.UNKNOWN);
            system.setStatusReason("Configuração alterada; execute uma nova verificação.");
            system.setStatusChangedAt(null);
        }
        if(system.isMaintenance()) { system.setStatus(SystemStatus.MAINTENANCE);system.setStatusReason("Sistema em manutenção; verificações automáticas pausadas."); }
        MonitoredSystem saved = repository.save(system);
        if (events != null) events.record(saved, "SYSTEM_UPDATED", "INFO", "Configuração alterada", system.isActive() ? "Monitoramento ativo." : "Monitoramento pausado.", "Conta PulseOps");
        return MonitoredSystemResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        MonitoredSystem system = findEntity(id);
        if (events != null) events.record(system, "SYSTEM_DELETED", "INFO", "Sistema excluído", "Cadastro e histórico de verificações removidos.", "Conta PulseOps");
        repository.delete(system);
    }

    private MonitoredSystemResponse response(MonitoredSystem system) {
        var view=MonitoredSystemResponse.from(system);
        return checks==null ? view : view.withLastCheck(checks.findFirstByMonitoredSystemIdOrderByCheckedAtDesc(system.getId()).map(com.pulseops.dto.monitoring.HealthCheckResponse::from).orElse(null));
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
        system.setMonitoringIntervalSeconds(request.monitoringIntervalSeconds()==null ? 60 : request.monitoringIntervalSeconds());
        system.setMaintenance(request.maintenance());
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
