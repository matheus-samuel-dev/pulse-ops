package com.pulseops.dto.incident;
import com.pulseops.domain.incident.IncidentSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record UpdateIncidentRequest(@NotBlank @Size(min = 4, max = 180) String title,
        @Size(max = 4000) String description, @NotNull IncidentSeverity severity) { }
