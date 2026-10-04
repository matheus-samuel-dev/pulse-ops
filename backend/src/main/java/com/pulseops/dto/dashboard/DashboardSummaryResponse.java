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
        String period,
        int configurationRequiredSystems,
        int problemSystems
) {
    public DashboardSummaryResponse(int systems,BigDecimal availability,BigDecimal availabilityChange,long incidents,long incidentChange,BigDecimal coverage,BigDecimal coverageChange,long deployments,int operational,int degraded,int down,String health,String period){this(systems,availability,availabilityChange,incidents,incidentChange,coverage,coverageChange,deployments,operational,degraded,down,health,period,0,degraded+down);}
}
