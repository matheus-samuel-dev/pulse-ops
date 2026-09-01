package com.pulseops.dto.incident;

import com.pulseops.domain.incident.IncidentSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateIncidentRequest(
        @NotNull UUID systemId,
        @NotBlank @Size(max = 180) String title,
        @Size(max = 4000) String description,
        @NotNull IncidentSeverity severity,
        OffsetDateTime startedAt
) {
    public CreateIncidentCommand toCommand() {
        return new CreateIncidentCommand(title, description, severity, startedAt);
    }
}
