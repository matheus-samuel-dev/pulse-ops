package com.pulseops.dto.quality;

import com.pulseops.domain.quality.QualityClassification;
import java.math.BigDecimal;
import java.util.List;

/**
 * Portfolio-wide quality snapshot calculated exclusively from the latest
 * report of each active monitored system.
 */
public record QualityOverviewResponse(
        int monitoredSystems,
        int systemsWithReports,
        int systemsWithoutReports,
        long totalTests,
        long passedTests,
        long failedTests,
        long skippedTests,
        BigDecimal passRate,
        BigDecimal averageLineCoverage,
        BigDecimal averageBranchCoverage,
        BigDecimal averageCoverageScore,
        QualityClassification classification,
        List<QualityReportResponse> systems
) {
    public QualityOverviewResponse {
        systems = List.copyOf(systems);
    }
}
