package com.pulseops.service.monitoring;

import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.common.TimeRange;
import com.pulseops.dto.monitoring.AvailabilityMetrics;
import com.pulseops.dto.monitoring.SlaMetrics;
import com.pulseops.dto.monitoring.SlaStatus;
import com.pulseops.exception.ResourceNotFoundException;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SlaService")
class SlaServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T15:00:00Z");
    private static final OffsetDateTime END = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final TimeRange PERIOD = new TimeRange(END.minusDays(7), END);

    @Mock
    private MonitoredSystemRepository systemRepository;
    @Mock
    private AvailabilityService availabilityService;

    private SlaService slaService;

    @BeforeEach
    void setUp() {
        slaService = new SlaService(
                systemRepository,
                availabilityService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldMarkSlaAsMetWhenAvailabilityEqualsTarget() {
        SlaMetrics metrics = slaService.evaluate(
                new BigDecimal("99.900"), availability(10_000, "99.900"));

        assertThat(metrics.currentAvailability()).isEqualByComparingTo("99.900");
        assertThat(metrics.targetAvailability()).isEqualByComparingTo("99.900");
        assertThat(metrics.targetMet()).isTrue();
        assertThat(metrics.differencePercentagePoints()).isEqualByComparingTo("0.000");
        assertThat(metrics.status()).isEqualTo(SlaStatus.MET);
        assertThat(metrics.evaluatedChecks()).isEqualTo(10_000);
    }

    @Test
    void shouldMarkSlaAtRiskAtExactToleranceBoundary() {
        SlaMetrics metrics = slaService.evaluate(
                new BigDecimal("99.900"), availability(1_000, "99.800"));

        assertThat(metrics.targetMet()).isFalse();
        assertThat(metrics.differencePercentagePoints()).isEqualByComparingTo("-0.100");
        assertThat(metrics.status()).isEqualTo(SlaStatus.AT_RISK);
    }

    @Test
    void shouldMarkSlaBreachedBeyondTolerance() {
        SlaMetrics metrics = slaService.evaluate(
                new BigDecimal("99.900"), availability(1_000, "99.799"));

        assertThat(metrics.targetMet()).isFalse();
        assertThat(metrics.differencePercentagePoints()).isEqualByComparingTo("-0.101");
        assertThat(metrics.status()).isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    void shouldReturnNoDataInsteadOfClaimingTargetCompliance() {
        SlaMetrics metrics = slaService.evaluate(
                new BigDecimal("0.000"), availability(0, "0.000"));

        assertThat(metrics.targetMet()).isFalse();
        assertThat(metrics.status()).isEqualTo(SlaStatus.NO_DATA);
        assertThat(metrics.evaluatedChecks()).isZero();
    }

    @Test
    void shouldRejectSlaTargetOutsidePercentageRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> slaService.evaluate(
                        new BigDecimal("-0.001"), availability(1, "100.000")))
                .withMessageContaining("between 0 and 100");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> slaService.evaluate(
                        new BigDecimal("100.001"), availability(1, "100.000")))
                .withMessageContaining("between 0 and 100");
    }

    @Test
    void shouldCalculateSlaUsingSystemTargetAndAvailabilityService() {
        UUID systemId = UUID.randomUUID();
        MonitoredSystem system = new MonitoredSystem();
        system.setId(systemId);
        system.setTargetAvailability(new BigDecimal("99.950"));
        AvailabilityMetrics availability = availability(50, "100.000");
        when(systemRepository.findById(systemId)).thenReturn(Optional.of(system));
        when(availabilityService.calculateForSystem(system, PERIOD)).thenReturn(availability);

        SlaMetrics metrics = slaService.calculate(systemId, PERIOD);

        assertThat(metrics.targetAvailability()).isEqualByComparingTo("99.950");
        assertThat(metrics.currentAvailability()).isEqualByComparingTo("100.000");
        assertThat(metrics.differencePercentagePoints()).isEqualByComparingTo("0.050");
        assertThat(metrics.status()).isEqualTo(SlaStatus.MET);
    }

    @Test
    void shouldFailBeforeCalculatingAvailabilityForUnknownSystem() {
        UUID systemId = UUID.randomUUID();
        when(systemRepository.findById(systemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> slaService.calculate(systemId, PERIOD))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(systemId.toString());

        verifyNoInteractions(availabilityService);
    }

    private AvailabilityMetrics availability(long totalChecks, String percentage) {
        long successful = totalChecks;
        return new AvailabilityMetrics(
                PERIOD,
                totalChecks,
                successful,
                totalChecks - successful,
                new BigDecimal(percentage)
        );
    }
}
