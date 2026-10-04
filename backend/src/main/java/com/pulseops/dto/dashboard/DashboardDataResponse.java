package com.pulseops.dto.dashboard;
import java.time.OffsetDateTime;
import java.util.List;
public record DashboardDataResponse(DashboardSummaryResponse summary,List<LatencyPointResponse> latency,
        ErrorBreakdownResponse errors,List<SystemHealthResponse> health,OffsetDateTime generatedAt) { }
