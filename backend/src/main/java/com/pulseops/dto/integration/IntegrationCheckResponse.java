package com.pulseops.dto.integration;

import java.time.OffsetDateTime;

public record IntegrationCheckResponse(
        String integrationId, String status, long responseTimeMs, OffsetDateTime checkedAt,
        String message, boolean cached, OffsetDateTime nextCheckAt
) { }
