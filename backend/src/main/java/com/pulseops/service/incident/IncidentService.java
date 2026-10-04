package com.pulseops.service.incident;

import com.pulseops.domain.incident.Incident;
import com.pulseops.domain.incident.IncidentSeverity;
import com.pulseops.domain.incident.IncidentStatus;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.incident.CreateIncidentCommand;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.exception.InvalidStateTransitionException;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.IncidentRepository;
import com.pulseops.repository.MonitoredSystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class IncidentService {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.pulseops.service.EventRecorder events;


    private static final int MAX_TITLE_LENGTH = 180;
    private static final int MAX_DESCRIPTION_LENGTH = 4_000;

    private final IncidentRepository incidentRepository;
    private final MonitoredSystemRepository systemRepository;
    private final Clock clock;

    public IncidentService(
            IncidentRepository incidentRepository,
            MonitoredSystemRepository systemRepository,
            Clock clock
    ) {
        this.incidentRepository = Objects.requireNonNull(incidentRepository);
        this.systemRepository = Objects.requireNonNull(systemRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public Incident create(UUID systemId, CreateIncidentCommand command) {
        Objects.requireNonNull(command, "command é obrigatório");
        MonitoredSystem system = findSystem(systemId);
        return createIncident(
                system,
                command.title(),
                command.description(),
                command.severity(),
                command.startedAt(),
                false
        );
    }

    @Transactional
    public Incident createAutomatic(
            MonitoredSystem system,
            String title,
            String description,
            IncidentSeverity severity,
            OffsetDateTime startedAt
    ) {
        Objects.requireNonNull(system, "system é obrigatório");
        return createIncident(system, title, description, severity, startedAt, true);
    }

    @Transactional
    public Incident markInvestigating(UUID incidentId) {
        Incident incident = findIncident(incidentId);
        if (incident.getStatus() != IncidentStatus.OPEN) {
            throw new InvalidStateTransitionException(
                    "incident", incident.getStatus(), IncidentStatus.INVESTIGATING);
        }
        incident.setStatus(IncidentStatus.INVESTIGATING);
        incident.setInvestigatingAt(OffsetDateTime.now(clock));
        record(incident, "INCIDENT_INVESTIGATING", "Investigação iniciada", "WARNING");
        return incidentRepository.save(incident);
    }

    @Transactional
    public Incident resolve(UUID incidentId) {
        return resolve(findIncident(incidentId), OffsetDateTime.now(clock));
    }

    @Transactional
    public Incident resolve(Incident incident, OffsetDateTime resolvedAt) {
        Objects.requireNonNull(incident, "incident é obrigatório");
        Objects.requireNonNull(resolvedAt, "resolvedAt é obrigatório");
        if (incident.getStatus() == IncidentStatus.RESOLVED) {
            throw new InvalidStateTransitionException(
                    "incident", IncidentStatus.RESOLVED, IncidentStatus.RESOLVED);
        }
        if (incident.getStartedAt() != null && resolvedAt.isBefore(incident.getStartedAt())) {
            throw new BusinessRuleException("O incidente não pode ser resolvido antes do início");
        }
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(resolvedAt);
        record(incident, "INCIDENT_RESOLVED", "Incidente resolvido", "SUCCESS");
        return incidentRepository.save(incident);
    }

    @Transactional(readOnly = true)
    public List<Incident> findBySystem(UUID systemId) {
        findSystem(systemId);
        return incidentRepository.findByMonitoredSystemIdOrderByStartedAtDesc(systemId);
    }

    @Transactional
    public Incident update(UUID id, com.pulseops.dto.incident.UpdateIncidentRequest request) {
        Incident incident = findIncident(id);
        incident.setTitle(requiredText(request.title(), "Título", MAX_TITLE_LENGTH));
        incident.setDescription(optionalText(request.description(), "Descrição", MAX_DESCRIPTION_LENGTH));
        incident.setSeverity(request.severity());
        record(incident, "INCIDENT_UPDATED", "Contexto do incidente atualizado", "INFO");
        return incidentRepository.save(incident);
    }

    private void record(Incident incident, String type, String title, String severity) {
        if (events != null) events.recordResource(incident.getMonitoredSystem(), type, severity, title, incident.getTitle(), incident.isAutomatic() ? "Monitoramento PulseOps" : "Conta PulseOps",incident.getId(),incident.getStatus().name());
    }

    private Incident createIncident(
            MonitoredSystem system,
            String title,
            String description,
            IncidentSeverity severity,
            OffsetDateTime startedAt,
            boolean automatic
    ) {
        String normalizedTitle = requiredText(title, "Título do incidente", MAX_TITLE_LENGTH);
        String normalizedDescription = optionalText(
                description, "Descrição do incidente", MAX_DESCRIPTION_LENGTH);
        if (severity == null) {
            throw new BusinessRuleException("A severidade do incidente é obrigatória");
        }

        Incident incident = new Incident();
        incident.setMonitoredSystem(system);
        incident.setTitle(normalizedTitle);
        incident.setDescription(normalizedDescription);
        incident.setSeverity(severity);
        incident.setStatus(IncidentStatus.OPEN);
        if (startedAt != null && startedAt.isAfter(OffsetDateTime.now(clock))) throw new BusinessRuleException("O início do incidente não pode estar no futuro");
        incident.setStartedAt(startedAt == null ? OffsetDateTime.now(clock) : startedAt);
        incident.setAutomatic(automatic);
        Incident saved = incidentRepository.save(incident);
        record(saved, "INCIDENT_OPENED", "Incidente aberto", severity == IncidentSeverity.CRITICAL ? "CRITICAL" : "WARNING");
        return saved;
    }

    private MonitoredSystem findSystem(UUID systemId) {
        return systemRepository.findById(systemId)
                .orElseThrow(() -> new ResourceNotFoundException("Monitored system", systemId));
    }

    private Incident findIncident(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));
    }

    private String requiredText(String text, String field, int maximumLength) {
        if (text == null || text.isBlank()) {
            throw new BusinessRuleException(field + " é obrigatório");
        }
        String normalized = text.trim();
        if (normalized.length() > maximumLength) {
            throw new BusinessRuleException(field + " excede " + maximumLength + " caracteres");
        }
        return normalized;
    }

    private String optionalText(String text, String field, int maximumLength) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.trim();
        if (normalized.length() > maximumLength) {
            throw new BusinessRuleException(field + " excede " + maximumLength + " caracteres");
        }
        return normalized;
    }
}
