package com.pulseops.dto.dashboard;

public record ErrorBreakdownResponse(
        long serverErrors,
        long clientErrors,
        long timeouts,
        long others,
        long total,
        String period
) {
}
