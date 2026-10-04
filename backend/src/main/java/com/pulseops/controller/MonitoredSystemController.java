package com.pulseops.controller;

import com.pulseops.dto.monitoring.HealthCheckResponse;
import com.pulseops.dto.system.MonitoredSystemRequest;
import com.pulseops.dto.system.MonitoredSystemResponse;
import com.pulseops.service.MonitoredSystemService;
import com.pulseops.service.monitoring.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/systems")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Systems", description = "Catálogo e monitoramento de sistemas")
public class MonitoredSystemController {

    private final MonitoredSystemService systemService;
    private final MonitoringService monitoringService;

    public MonitoredSystemController(MonitoredSystemService systemService, MonitoringService monitoringService) {
        this.systemService = systemService;
        this.monitoringService = monitoringService;
    }

    @GetMapping
    public List<MonitoredSystemResponse> findAll() {
        return systemService.findAll();
    }

    @GetMapping("/{id}")
    public MonitoredSystemResponse findById(@PathVariable UUID id) {
        return systemService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public MonitoredSystemResponse create(@Valid @RequestBody MonitoredSystemRequest request) {
        return systemService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public MonitoredSystemResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody MonitoredSystemRequest request
    ) {
        return systemService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public void delete(@PathVariable UUID id) {
        systemService.delete(id);
    }

    @PostMapping("/{id}/checks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    @Operation(summary = "Executa um health check sob demanda")
    public HealthCheckResponse checkNow(@PathVariable UUID id) {
        return HealthCheckResponse.from(monitoringService.checkSystem(id));
    }
}
