package com.pulseops.controller;

import com.pulseops.dto.dashboard.DashboardSummaryResponse;
import com.pulseops.dto.dashboard.ErrorBreakdownResponse;
import com.pulseops.dto.dashboard.LatencyPointResponse;
import com.pulseops.dto.dashboard.SystemHealthResponse;
import com.pulseops.service.DashboardService;
import com.pulseops.domain.system.Environment;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Dashboard", description = "Agregados otimizados para a experiência do dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public com.pulseops.dto.dashboard.DashboardDataResponse snapshot(@RequestParam(defaultValue="24h") String period,@RequestParam(required=false) Environment environment) {
        return dashboardService.snapshot(period,environment);
    }
    @GetMapping("/summary")
    public DashboardSummaryResponse summary(
            @RequestParam(defaultValue = "24h") String period,
            @RequestParam(required = false) Environment environment
    ) {
        return dashboardService.summary(period, environment);
    }

    @GetMapping("/latency")
    public List<LatencyPointResponse> latency(
            @RequestParam(defaultValue = "24h") String period,
            @RequestParam(required = false) Environment environment
    ) {
        return dashboardService.latency(period, environment);
    }

    @GetMapping("/errors")
    public ErrorBreakdownResponse errors(
            @RequestParam(defaultValue = "24h") String period,
            @RequestParam(required = false) Environment environment
    ) {
        return dashboardService.errors(period, environment);
    }

    @GetMapping("/health")
    public List<SystemHealthResponse> health(
            @RequestParam(defaultValue = "24h") String period,
            @RequestParam(required = false) Environment environment
    ) {
        return dashboardService.health(period, environment);
    }
}
