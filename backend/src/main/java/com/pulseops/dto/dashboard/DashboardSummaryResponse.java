package com.pulseops.dto.dashboard;

import java.math.BigDecimal;

public record DashboardSummaryResponse(
        int monitoredSystems,
        BigDecimal averageAvailability,
        BigDecimal availabilityChange,
        long openIncidents,
        long incidentChange,
        BigDecimal averageCoverage,
        BigDecimal coverageChange,
        long deployments,
        int operationalSystems,
        int degradedSystems,
        int downSystems,
        String overallHealth,
        String period
) {
}
