package com.pulseops.dto.report;

import com.pulseops.domain.system.Environment;
import java.time.OffsetDateTime;
import java.util.List;

public record OperationalReportResponse(
        String period,
        Environment environment,
        OffsetDateTime windowStart,
        OffsetDateTime windowEnd,
        OperationalReportKpis kpis,
        List<OperationalEventResponse> feed
) {
    public OperationalReportResponse {
        feed = List.copyOf(feed);
    }
}
