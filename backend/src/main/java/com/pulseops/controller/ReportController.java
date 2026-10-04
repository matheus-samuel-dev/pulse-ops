package com.pulseops.controller;

import com.pulseops.domain.system.Environment;
import com.pulseops.dto.report.OperationalReportResponse;
import com.pulseops.service.report.OperationalReportService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/reports")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reports", description = "Relatórios operacionais consolidados")
public class ReportController {

    private final OperationalReportService reportService;

    public ReportController(OperationalReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/operational")
    public OperationalReportResponse operational(
            @RequestParam(defaultValue = "7d") String period,
            @RequestParam(required = false) Environment environment,
            @RequestParam(required=false) java.util.UUID systemId,
            @RequestParam(defaultValue="0") @jakarta.validation.constraints.Min(0) int page
    ) {
        return reportService.generate(period, environment,systemId,page);
    }
}
