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

    @org.springframework.beans.factory.annotation.Autowired private java.time.Clock clock;
    @org.springframework.beans.factory.annotation.Autowired private com.pulseops.service.DashboardService dashboard;
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

    @org.springframework.transaction.annotation.Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    @GetMapping("/metrics")
    public SystemMetricsResponse metrics(
            @PathVariable UUID systemId,
            @RequestParam(defaultValue = "24h") String period
    ) {
        period=com.pulseops.service.OperationalReadModel.normalize(period);
        var end=java.time.OffsetDateTime.now(clock);
        var range=new com.pulseops.dto.common.TimeRange(end.minusDays(period.equals("7d")?7:period.equals("30d")?30:1),end);
        AvailabilityMetrics availability=availabilityService.calculate(systemId,range);
        LatencyMetrics latency=latencyService.calculate(systemId,range);
        SlaMetrics sla=slaService.calculate(systemId,range);
        return new SystemMetricsResponse(period, availability, latency, sla);
    }

    @GetMapping("/latency")
    public java.util.List<com.pulseops.dto.dashboard.LatencyPointResponse> latency(@PathVariable UUID systemId,@RequestParam(defaultValue="24h") String period) { return dashboard.latency(period,null,systemId); }
    @GetMapping("/checks")
    public Page<HealthCheckResponse> checks(
            @PathVariable UUID systemId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required=false) String period
    ) {
        return period==null?queryService.healthChecks(systemId,page,size):queryService.healthChecks(systemId,page,size,period);
    }
}
