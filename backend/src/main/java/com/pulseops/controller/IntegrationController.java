package com.pulseops.controller;

import com.pulseops.dto.integration.IntegrationCheckResponse;
import com.pulseops.dto.integration.IntegrationOverviewResponse;
import com.pulseops.dto.integration.IntegrationResponse;
import com.pulseops.dto.report.OperationalEventResponse;
import com.pulseops.service.integration.IntegrationActivityService;
import com.pulseops.service.integration.IntegrationCheckService;
import com.pulseops.service.integration.IntegrationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Integrations", description = "Hub de sistemas, disponibilidade e relatórios recebidos")
@PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER', 'VIEWER')")
public class IntegrationController {
    private final IntegrationService integrations;
    private final IntegrationActivityService activity;
    private final IntegrationCheckService checks;

    public IntegrationController(IntegrationService integrations, IntegrationActivityService activity, IntegrationCheckService checks) {
        this.integrations = integrations;
        this.activity = activity;
        this.checks = checks;
    }

    @GetMapping
    public IntegrationOverviewResponse overview() { return integrations.overview(); }

    @GetMapping("/events")
    public List<OperationalEventResponse> events() { return activity.events(null); }

    @GetMapping("/{id}")
    public IntegrationResponse detail(@PathVariable String id) { return integrations.detail(id); }

    @GetMapping("/{id}/events")
    public List<OperationalEventResponse> events(@PathVariable String id) { return activity.events(id); }

    @PostMapping("/{id}/health-check")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public IntegrationCheckResponse check(@PathVariable String id) { return checks.check(id); }
}
