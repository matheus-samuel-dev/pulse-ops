package com.pulseops.dto.integration;

import java.time.OffsetDateTime;
import java.util.List;

public record IntegrationOverviewResponse(
        List<IntegrationResponse> integrations, Summary summary, boolean readOnly,
        OffsetDateTime generatedAt, String reportingTimezone
) {
    public record Summary(long connected, long operational, long withIncidents, long eventsToday) { }
}
