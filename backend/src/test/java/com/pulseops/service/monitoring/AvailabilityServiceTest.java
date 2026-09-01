package com.pulseops.service.monitoring;

import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.common.TimeRange;
import com.pulseops.dto.monitoring.AvailabilityMetrics;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AvailabilityService")
class AvailabilityServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T15:00:00Z");
    private static final OffsetDateTime END = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final TimeRange PERIOD = new TimeRange(END.minusHours(1), END);

    @Mock
    private MonitoredSystemRepository systemRepository;
    @Mock
    private HealthCheckRepository healthCheckRepository;

    private AvailabilityService availabilityService;

    @BeforeEach
    void setUp() {
        availabilityService = new AvailabilityService(
                systemRepository,
                healthCheckRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldCalculateOneHundredPercentAvailability() {
        List<HealthCheck> checks = List.of(
                check(true, PERIOD.start()),
                check(true, PERIOD.start().plusMinutes(30)),
                check(true, PERIOD.end())
        );

        AvailabilityMetrics metrics = availabilityService.calculateFromChecks(checks, PERIOD);

        assertThat(metrics.totalChecks()).isEqualTo(3);
        assertThat(metrics.successfulChecks()).isEqualTo(3);
        assertThat(metrics.failedChecks()).isZero();
        assertThat(metrics.availabilityPercentage()).isEqualByComparingTo("100.000");
    }

    @Test
    void shouldRoundPartialAvailabilityToThreeDecimalPlaces() {
        AvailabilityMetrics metrics = availabilityService.calculateFromChecks(
                List.of(
                        check(true, PERIOD.start().plusMinutes(10)),
                        check(true, PERIOD.start().plusMinutes(20)),
                        check(false, PERIOD.start().plusMinutes(30))
                ),
                PERIOD
        );

        assertThat(metrics.totalChecks()).isEqualTo(3);
        assertThat(metrics.successfulChecks()).isEqualTo(2);
        assertThat(metrics.failedChecks()).isEqualTo(1);
        assertThat(metrics.availabilityPercentage()).isEqualByComparingTo("66.667");
    }

    @Test
    void shouldReturnZeroWhenThereAreNoChecks() {
        AvailabilityMetrics metrics = availabilityService.calculateFromChecks(List.of(), PERIOD);

        assertThat(metrics.totalChecks()).isZero();
        assertThat(metrics.successfulChecks()).isZero();
        assertThat(metrics.failedChecks()).isZero();
        assertThat(metrics.availabilityPercentage()).isEqualTo(new BigDecimal("0.000"));
    }

    @Test
    void shouldIncludeBoundariesAndIgnoreNullOrOutOfPeriodChecks() {
        HealthCheck withoutTimestamp = check(true, null);
        AvailabilityMetrics metrics = availabilityService.calculateFromChecks(
                java.util.Arrays.asList(
                        check(true, PERIOD.start()),
                        check(false, PERIOD.end()),
                        check(true, PERIOD.start().minusNanos(1)),
                        check(true, PERIOD.end().plusNanos(1)),
                        withoutTimestamp,
                        null
                ),
                PERIOD
        );

        assertThat(metrics.totalChecks()).isEqualTo(2);
        assertThat(metrics.successfulChecks()).isEqualTo(1);
        assertThat(metrics.failedChecks()).isEqualTo(1);
        assertThat(metrics.availabilityPercentage()).isEqualByComparingTo("50.000");
    }

    @Test
    void shouldLoadSystemAndChecksForRequestedPeriod() {
        UUID systemId = UUID.randomUUID();
        MonitoredSystem system = new MonitoredSystem();
        system.setId(systemId);
        List<HealthCheck> checks = List.of(check(true, PERIOD.start().plusMinutes(5)));
        when(systemRepository.findById(systemId)).thenReturn(Optional.of(system));
        when(healthCheckRepository.findByMonitoredSystemIdAndCheckedAtBetweenOrderByCheckedAtAsc(
                systemId, PERIOD.start(), PERIOD.end())).thenReturn(checks);

        AvailabilityMetrics metrics = availabilityService.calculate(systemId, PERIOD);

        assertThat(metrics.availabilityPercentage()).isEqualByComparingTo("100.000");
        verify(healthCheckRepository)
                .findByMonitoredSystemIdAndCheckedAtBetweenOrderByCheckedAtAsc(
                        systemId, PERIOD.start(), PERIOD.end());
    }

    @Test
    void shouldFailWithoutQueryingChecksWhenSystemDoesNotExist() {
        UUID systemId = UUID.randomUUID();
        when(systemRepository.findById(systemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> availabilityService.calculate(systemId, PERIOD))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(systemId.toString());

        verify(healthCheckRepository, never())
                .findByMonitoredSystemIdAndCheckedAtBetweenOrderByCheckedAtAsc(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldBuildExactLast24HoursPeriodFromInjectedClock() {
        UUID systemId = UUID.randomUUID();
        MonitoredSystem system = new MonitoredSystem();
        system.setId(systemId);
        OffsetDateTime start = END.minusHours(24);
        when(systemRepository.findById(systemId)).thenReturn(Optional.of(system));
        when(healthCheckRepository.findByMonitoredSystemIdAndCheckedAtBetweenOrderByCheckedAtAsc(
                systemId, start, END)).thenReturn(List.of());

        AvailabilityMetrics metrics = availabilityService.last24Hours(systemId);

        assertThat(metrics.period().start()).isEqualTo(start);
        assertThat(metrics.period().end()).isEqualTo(END);
        verify(healthCheckRepository)
                .findByMonitoredSystemIdAndCheckedAtBetweenOrderByCheckedAtAsc(systemId, start, END);
    }

    private HealthCheck check(boolean success, OffsetDateTime checkedAt) {
        HealthCheck check = new HealthCheck();
        check.setSuccess(success);
        check.setCheckedAt(checkedAt);
        return check;
    }
}
