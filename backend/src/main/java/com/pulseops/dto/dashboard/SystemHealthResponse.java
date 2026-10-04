package com.pulseops.dto.dashboard;

import com.pulseops.domain.system.Environment;
import com.pulseops.domain.system.SystemStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SystemHealthResponse(
        UUID id,
        String name,
        Environment environment,
        SystemStatus status,
        BigDecimal uptime,
        Long latencyMs,
        OffsetDateTime lastCheckedAt,
        List<Long> sparkline,
        String statusReason,
        OffsetDateTime statusChangedAt,
        boolean active,
        long totalChecks
) {
    public SystemHealthResponse(UUID id, String name, Environment environment, SystemStatus status, BigDecimal uptime,
            Long latencyMs, OffsetDateTime lastCheckedAt, List<Long> sparkline) {
        this(id,name,environment,status,uptime,latencyMs,lastCheckedAt,sparkline,null,null,true,0);
    }
}
