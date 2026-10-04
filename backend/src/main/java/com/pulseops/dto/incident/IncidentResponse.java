package com.pulseops.dto.incident;

import com.pulseops.domain.incident.Incident;
import com.pulseops.domain.incident.IncidentSeverity;
import com.pulseops.domain.incident.IncidentStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        UUID systemId,
        String systemName,
        String title,
        String description,
        IncidentSeverity severity,
        IncidentStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime resolvedAt,
        boolean automatic,
        OffsetDateTime createdAt,
        OffsetDateTime investigatingAt
) {
    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(), incident.getMonitoredSystem().getId(), incident.getMonitoredSystem().getName(),
                incident.getTitle(), incident.getDescription(), incident.getSeverity(), incident.getStatus(),
                incident.getStartedAt(), incident.getResolvedAt(), incident.isAutomatic(), incident.getCreatedAt(), incident.getInvestigatingAt());
    }
}
