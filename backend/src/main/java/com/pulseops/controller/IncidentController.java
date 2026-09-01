package com.pulseops.controller;

import com.pulseops.domain.incident.IncidentSeverity;
import com.pulseops.domain.incident.IncidentStatus;
import com.pulseops.dto.incident.CreateIncidentRequest;
import com.pulseops.dto.incident.IncidentResponse;
import com.pulseops.service.OperationsQueryService;
import com.pulseops.service.incident.IncidentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Incidents", description = "Gestão do ciclo de vida de incidentes")
public class IncidentController {

    private final IncidentService incidentService;
    private final OperationsQueryService queryService;

    public IncidentController(IncidentService incidentService, OperationsQueryService queryService) {
        this.incidentService = incidentService;
        this.queryService = queryService;
    }

    @GetMapping
    public List<IncidentResponse> findAll(
            @RequestParam(required = false) UUID systemId,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentSeverity severity
    ) {
        return queryService.incidents(systemId, status, severity);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public IncidentResponse create(@Valid @RequestBody CreateIncidentRequest request) {
        return IncidentResponse.from(incidentService.create(request.systemId(), request.toCommand()));
    }

    @PatchMapping("/{id}/investigating")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public IncidentResponse markInvestigating(@PathVariable UUID id) {
        return IncidentResponse.from(incidentService.markInvestigating(id));
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public IncidentResponse resolve(@PathVariable UUID id) {
        return IncidentResponse.from(incidentService.resolve(id));
    }
}
