package com.pulseops.dto.report;

import java.math.BigDecimal;

public record OperationalReportKpis(
        long monitoredSystems,
        long operationalSystems,
        long degradedSystems,
        long downSystems,
        long unknownSystems,
        long totalHealthChecks,
        long successfulHealthChecks,
        long failedHealthChecks,
        BigDecimal availability,
        long activeIncidents,
        long incidentsOpened,
        long deployments,
        long successfulDeployments,
        BigDecimal deploymentSuccessRate,
        long totalTests,
        long passedTests,
        long failedTests,
        long skippedTests,
        BigDecimal testPassRate,
        BigDecimal averageLineCoverage,
        BigDecimal averageBranchCoverage
) {
}
