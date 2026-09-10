package com.pulseops.dto.report;

import com.pulseops.domain.system.Environment;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OperationalEventResponse(
        UUID id,
        OperationalEventType type,
        OffsetDateTime occurredAt,
        UUID systemId,
        String systemName,
        Environment environment,
        String title,
        String description,
        String status,
        OperationalEventImpact impact,
        String source
) {
}
