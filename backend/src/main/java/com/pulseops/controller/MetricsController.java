package com.pulseops.controller;

import com.pulseops.dto.monitoring.AvailabilityMetrics;
import com.pulseops.dto.monitoring.HealthCheckResponse;
import com.pulseops.dto.monitoring.LatencyMetrics;
import com.pulseops.dto.monitoring.SlaMetrics;
import com.pulseops.dto.monitoring.SystemMetricsResponse;
import com.pulseops.service.OperationsQueryService;
import com.pulseops.service.monitoring.AvailabilityService;
import com.pulseops.service.monitoring.LatencyMetricsService;
import com.pulseops.service.monitoring.SlaService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/systems/{systemId}")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Metrics", description = "Disponibilidade, latência, SLA e histórico")
public class MetricsController {

    private final AvailabilityService availabilityService;
    private final LatencyMetricsService latencyService;
    private final SlaService slaService;
    private final OperationsQueryService queryService;

    public MetricsController(
            AvailabilityService availabilityService,
            LatencyMetricsService latencyService,
            SlaService slaService,
            OperationsQueryService queryService
    ) {
        this.availabilityService = availabilityService;
        this.latencyService = latencyService;
        this.slaService = slaService;
        this.queryService = queryService;
    }

    @GetMapping("/metrics")
    public SystemMetricsResponse metrics(
            @PathVariable UUID systemId,
            @RequestParam(defaultValue = "24h") String period
    ) {
        AvailabilityMetrics availability;
        LatencyMetrics latency;
        SlaMetrics sla;
        switch (period.toLowerCase()) {
            case "7d" -> {
                availability = availabilityService.last7Days(systemId);
                latency = latencyService.last7Days(systemId);
                sla = slaService.last7Days(systemId);
            }
            case "30d" -> {
                availability = availabilityService.last30Days(systemId);
                latency = latencyService.last30Days(systemId);
                sla = slaService.last30Days(systemId);
            }
            default -> {
                period = "24h";
                availability = availabilityService.last24Hours(systemId);
                latency = latencyService.last24Hours(systemId);
                sla = slaService.last24Hours(systemId);
            }
        }
        return new SystemMetricsResponse(period, availability, latency, sla);
    }

    @GetMapping("/checks")
    public Page<HealthCheckResponse> checks(
            @PathVariable UUID systemId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return queryService.healthChecks(systemId, page, size);
    }
}
