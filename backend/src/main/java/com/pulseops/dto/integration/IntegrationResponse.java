package com.pulseops.dto.integration;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record IntegrationResponse(
        String id, String name, String description, String type, boolean primary,
        UUID systemId, boolean configured, boolean enabled, String status, String statusReason,
        String publicUrl, String baseUrl, String healthEndpoint,
        OffsetDateTime lastCheckedAt, OffsetDateTime lastSuccessfulSyncAt,
        OffsetDateTime lastReportAt, Long responseTimeMs, BigDecimal healthPercent,
        long checksLast24h, long errorsLast24h, OffsetDateTime lastFailureAt,
        OffsetDateTime nextCheckAt
) { }
