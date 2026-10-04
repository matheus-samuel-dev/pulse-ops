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
        List<OperationalEventResponse> feed,
        long totalEvents,
        int eventPages,
        int eventPage
) {
    public OperationalReportResponse(String period,Environment environment,OffsetDateTime windowStart,OffsetDateTime windowEnd,OperationalReportKpis kpis,List<OperationalEventResponse> feed){this(period,environment,windowStart,windowEnd,kpis,feed,feed.size(),feed.isEmpty()?0:1,0);}
    public OperationalReportResponse {
        feed = List.copyOf(feed);
    }
}
