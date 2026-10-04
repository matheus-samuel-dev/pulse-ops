package com.pulseops.controller;

import com.pulseops.dto.quality.CreateTestReportRequest;
import com.pulseops.dto.quality.QualityOverviewResponse;
import com.pulseops.dto.quality.QualityReportResponse;
import com.pulseops.dto.quality.QualitySummary;
import com.pulseops.service.quality.QualityService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quality")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Quality", description = "Relatórios e classificação de qualidade")
public class QualityController {

    private final QualityService qualityService;

    public QualityController(QualityService qualityService) {
        this.qualityService = qualityService;
    }

    @GetMapping("/systems/{systemId}/latest")
    public QualitySummary latest(@PathVariable UUID systemId) {
        return qualityService.getLatest(systemId);
    }

    @GetMapping("/overview")
    public QualityOverviewResponse overview(@RequestParam(defaultValue="30d") String period,@RequestParam(required=false) com.pulseops.domain.system.Environment environment,@RequestParam(required=false) UUID systemId) {
        return qualityService.getOverview(period,environment,systemId);
    }

    @GetMapping("/history")
    public List<QualityReportResponse> history(
            @RequestParam(required = false) UUID systemId,
            @RequestParam(defaultValue = "30d") String period
    ) {
        return qualityService.getHistory(systemId, period);
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public QualitySummary create(@Valid @RequestBody CreateTestReportRequest request) {
        return qualityService.createReport(request.systemId(), request.toCommand());
    }
}
