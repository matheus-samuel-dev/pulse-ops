package com.pulseops.service.monitoring;

import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.dto.common.TimeRange;
import com.pulseops.dto.monitoring.LatencyMetrics;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.HealthCheckRepository;
import com.pulseops.repository.MonitoredSystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LatencyMetricsService")
class LatencyMetricsServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T15:00:00Z");
    private static final OffsetDateTime END = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final TimeRange PERIOD = new TimeRange(END.minusHours(1), END);

    @Mock
    private MonitoredSystemRepository systemRepository;
    @Mock
    private HealthCheckRepository healthCheckRepository;

    private LatencyMetricsService latencyService;

    @BeforeEach
    void setUp() {
        latencyService = new LatencyMetricsService(
                systemRepository,
                healthCheckRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldCalculateAverageMinimumMaximumAndNearestRankP95() {
        List<HealthCheck> checks = new ArrayList<>();
        for (int sample = 1; sample <= 20; sample++) {
            checks.add(check(sample * 10L, 200, PERIOD.start().plusMinutes(sample)));
        }

        LatencyMetrics metrics = latencyService.calculateFromChecks(checks, PERIOD);

        assertThat(metrics.sampleCount()).isEqualTo(20);
        assertThat(metrics.averageMs()).isEqualByComparingTo("105.00");
        assertThat(metrics.minimumMs()).isEqualTo(10);
        assertThat(metrics.maximumMs()).isEqualTo(200);
        assertThat(metrics.p95Ms()).isEqualTo(190);
    }

    @Test
    void shouldRoundAverageHalfUp() {
        LatencyMetrics metrics = latencyService.calculateFromChecks(
                List.of(
                        check(1, 200, PERIOD.start().plusMinutes(1)),
                        check(2, 200, PERIOD.start().plusMinutes(2)),
                        check(2, 200, PERIOD.start().plusMinutes(3))
                ),
                PERIOD
        );

        assertThat(metrics.averageMs()).isEqualByComparingTo("1.67");
        assertThat(metrics.p95Ms()).isEqualTo(2);
    }

    @Test
    void shouldReturnEmptyMetricsWhenNoRemoteServerWasReached() {
        HealthCheck transportFailure = check(5_000, null, PERIOD.start().plusMinutes(1));

        LatencyMetrics metrics = latencyService.calculateFromChecks(
                List.of(transportFailure), PERIOD);

        assertThat(metrics.sampleCount()).isZero();
        assertThat(metrics.averageMs()).isNull();
        assertThat(metrics.minimumMs()).isNull();
        assertThat(metrics.maximumMs()).isNull();
        assertThat(metrics.p95Ms()).isNull();
    }

    @Test
    void shouldIncludeUnexpectedHttpResponsesButIgnoreOutOfPeriodAndNullEntries() {
        HealthCheck unexpectedStatus = check(450, 503, PERIOD.start());
        unexpectedStatus.setSuccess(false);
        HealthCheck noTimestamp = check(100, 200, null);

        LatencyMetrics metrics = latencyService.calculateFromChecks(
                java.util.Arrays.asList(
                        unexpectedStatus,
                        check(100, 200, PERIOD.end()),
                        check(9_999, 200, PERIOD.start().minusNanos(1)),
                        check(9_999, 200, PERIOD.end().plusNanos(1)),
                        noTimestamp,
                        null
                ),
                PERIOD
        );

        assertThat(metrics.sampleCount()).isEqualTo(2);
        assertThat(metrics.averageMs()).isEqualByComparingTo("275.00");
        assertThat(metrics.minimumMs()).isEqualTo(100);
        assertThat(metrics.maximumMs()).isEqualTo(450);
    }

    @Test
    void shouldQueryRepositoryForExistingSystem() {
        UUID systemId = UUID.randomUUID();
        List<HealthCheck> checks = List.of(check(321, 200, PERIOD.start().plusMinutes(1)));
        when(systemRepository.existsById(systemId)).thenReturn(true);
        when(healthCheckRepository.findByMonitoredSystemIdAndCheckedAtBetweenOrderByCheckedAtAsc(
                systemId, PERIOD.start(), PERIOD.end())).thenReturn(checks);

        LatencyMetrics metrics = latencyService.calculate(systemId, PERIOD);

        assertThat(metrics.sampleCount()).isEqualTo(1);
        assertThat(metrics.averageMs()).isEqualByComparingTo("321.00");
        verify(healthCheckRepository)
                .findByMonitoredSystemIdAndCheckedAtBetweenOrderByCheckedAtAsc(
                        systemId, PERIOD.start(), PERIOD.end());
    }

    @Test
    void shouldFailFastWhenSystemDoesNotExist() {
        UUID systemId = UUID.randomUUID();
        when(systemRepository.existsById(systemId)).thenReturn(false);

        assertThatThrownBy(() -> latencyService.calculate(systemId, PERIOD))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(systemId.toString());

        verify(healthCheckRepository, never())
                .findByMonitoredSystemIdAndCheckedAtBetweenOrderByCheckedAtAsc(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    private HealthCheck check(long responseTimeMs, Integer httpStatus, OffsetDateTime checkedAt) {
        HealthCheck check = new HealthCheck();
        check.setResponseTimeMs(responseTimeMs);
        check.setHttpStatus(httpStatus);
        check.setCheckedAt(checkedAt);
        check.setSuccess(httpStatus != null && httpStatus == 200);
        return check;
    }
}
