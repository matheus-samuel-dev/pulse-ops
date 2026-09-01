package com.pulseops.dto.incident;

import com.pulseops.domain.incident.IncidentSeverity;

import java.time.OffsetDateTime;

public record CreateIncidentCommand(
        String title,
        String description,
        IncidentSeverity severity,
        OffsetDateTime startedAt
) {
}
