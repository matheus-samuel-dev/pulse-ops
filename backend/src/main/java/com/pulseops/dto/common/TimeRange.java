package com.pulseops.dto.common;

import com.pulseops.exception.BusinessRuleException;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

public record TimeRange(OffsetDateTime start, OffsetDateTime end) {

    public TimeRange {
        Objects.requireNonNull(start, "start is required");
        Objects.requireNonNull(end, "end is required");
        if (!start.isBefore(end)) {
            throw new BusinessRuleException("Time range start must be before end");
        }
    }

    public static TimeRange endingNow(Duration duration, Clock clock) {
        Objects.requireNonNull(duration, "duration is required");
        Objects.requireNonNull(clock, "clock is required");
        if (duration.isZero() || duration.isNegative()) {
            throw new BusinessRuleException("Duration must be positive");
        }
        OffsetDateTime end = OffsetDateTime.now(clock);
        return new TimeRange(end.minus(duration), end);
    }

    public static TimeRange last24Hours(Clock clock) {
        return endingNow(Duration.ofHours(24), clock);
    }

    public static TimeRange last7Days(Clock clock) {
        return endingNow(Duration.ofDays(7), clock);
    }

    public static TimeRange last30Days(Clock clock) {
        return endingNow(Duration.ofDays(30), clock);
    }
}
