package com.pulseops.service.integration;

import com.pulseops.config.IntegrationProperties;
import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.quality.TestReport;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.integration.IntegrationResponse;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.HealthCheckRepository;
import com.pulseops.repository.MonitoredSystemRepository;
import com.pulseops.repository.TestReportRepository;
import com.pulseops.security.DemoModeProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntegrationServiceTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-10T14:00:00Z"), ZoneOffset.UTC);
    static final OffsetDateTime NOW = OffsetDateTime.now(CLOCK);
    @Mock MonitoredSystemRepository systems;
    @Mock HealthCheckRepository checks;
    @Mock TestReportRepository reports;
    IntegrationService service;

    static IntegrationProperties properties(Map<String, IntegrationProperties.Binding> bindings) {
        return new IntegrationProperties(Duration.ofMinutes(5), Duration.ofMinutes(1), ZoneId.of("America/Sao_Paulo"), bindings);
    }

    @BeforeEach void setup() { service = new IntegrationService(systems, checks, reports, properties(Map.of()), new DemoModeProperties(false), CLOCK); }

    @Test void unconfiguredCatalogHasNoFabricatedMetricsAndDoesNotProbe() {
        var result = service.overview();
        assertThat(result.integrations()).hasSize(6).allSatisfy(item -> {
            assertThat(item.configured()).isFalse();
            assertThat(item.status()).isEqualTo("UNKNOWN");
            assertThat(item.healthPercent()).isNull();
            assertThat(item.lastCheckedAt()).isNull();
        });
        assertThat(result.summary().connected()).isZero();
        assertThat(result.summary().eventsToday()).isZero();
        verifyNoInteractions(checks, reports);
    }

    @Test void derivesHealthAndReceiptTimeFromPersistedDataAndMasksSensitiveConfiguration() {
        MonitoredSystem system = system();
        when(systems.findByNameIgnoreCase("AI Web Auditor")).thenReturn(Optional.of(system));
        HealthCheck check = check(system, true, NOW.minusSeconds(20));
        when(checks.findFirstByMonitoredSystemIdOrderByCheckedAtDesc(system.getId())).thenReturn(Optional.of(check));
        when(checks.countByMonitoredSystemIdAndCheckedAtBetween(system.getId(), NOW.minusDays(1), NOW)).thenReturn(10L);
        when(checks.countByMonitoredSystemIdAndSuccessTrueAndCheckedAtBetween(system.getId(), NOW.minusDays(1), NOW)).thenReturn(9L);
        TestReport report = new TestReport();
        report.setGeneratedAt(NOW.minusDays(1));
        ReflectionTestUtils.setField(report, "createdAt", NOW.minusMinutes(2));
        when(reports.findFirstByMonitoredSystemIdOrderByCreatedAtDesc(system.getId())).thenReturn(Optional.of(report));
        var result = service.detail("ai-web-auditor");
        assertThat(result.healthPercent()).isEqualByComparingTo("90.0");
        assertThat(result.errorsLast24h()).isEqualTo(1);
        assertThat(result.lastSuccessfulSyncAt()).isEqualTo(NOW.minusMinutes(2));
        assertThat(result.lastReportAt()).isEqualTo(NOW.minusDays(1));
        assertThat(result.status()).isEqualTo("ONLINE");
        assertThat(result.baseUrl()).doesNotContain("internal", "secret");
        assertThat(result.healthEndpoint()).doesNotContain("secret");
    }

    @Test void summariesUseLocalDayAndCountAllEventsIndependentlyOfTheFeedLimit() {
        MonitoredSystem system = system();
        when(systems.findByNameIgnoreCase("AI Web Auditor")).thenReturn(Optional.of(system));
        OffsetDateTime start = OffsetDateTime.parse("2026-09-10T00:00:00-03:00");
        when(checks.countByMonitoredSystemIdInAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(List.of(system.getId()), start, NOW)).thenReturn(1450L);
        when(reports.countByMonitoredSystemIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(List.of(system.getId()), start, NOW)).thenReturn(20L);
        var result = service.overview();
        assertThat(result.summary().connected()).isEqualTo(1);
        assertThat(result.summary().eventsToday()).isEqualTo(1470);
        assertThat(result.summary().operational()).isZero();
        assertThat(result.summary().withIncidents()).isZero();
        assertThat(result.reportingTimezone()).isEqualTo("America/Sao_Paulo");
    }

    @Test void usesExplicitIdAndNeverFallsBackWhenTheBoundSystemIsMissing() {
        UUID id = UUID.randomUUID();
        service = new IntegrationService(systems, checks, reports, properties(Map.of("ai-web-auditor", new IntegrationProperties.Binding(id, "https://auditor.example.com/app"))), new DemoModeProperties(true), CLOCK);
        IntegrationResponse result = service.detail("ai-web-auditor");
        assertThat(result.configured()).isFalse();
        assertThat(result.publicUrl()).isEqualTo("https://auditor.example.com");
        verify(systems).findById(id);
        verify(systems, never()).findByNameIgnoreCase(anyString());
        assertThat(service.overview().readOnly()).isTrue();
    }

    @Test void findsConfiguredIdsWithoutDuplicates() {
        var system = system();
        when(systems.findByNameIgnoreCase("AI Web Auditor")).thenReturn(Optional.of(system));
        assertThat(service.systemIds()).containsExactly(system.getId());
    }

    @Test void returnsUnknownForStaleDataAndNoHealthForFewerThanFiveChecks() {
        var system = system();
        when(systems.findByNameIgnoreCase("AI Web Auditor")).thenReturn(Optional.of(system));
        when(checks.findFirstByMonitoredSystemIdOrderByCheckedAtDesc(system.getId())).thenReturn(Optional.of(check(system, true, NOW.minusMinutes(5))));
        when(checks.countByMonitoredSystemIdAndCheckedAtBetween(any(), any(), any())).thenReturn(4L);
        var result = service.detail("ai-web-auditor");
        assertThat(result.status()).isEqualTo("UNKNOWN");
        assertThat(result.statusReason()).contains("desatualizada");
        assertThat(result.healthPercent()).isNull();
        assertThat(result.lastSuccessfulSyncAt()).isNull();
    }

    @Test void unknownCatalogEntryIsNotFound() { assertThatThrownBy(() -> service.detail("arbitrary")).isInstanceOf(ResourceNotFoundException.class); }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {"javascript:alert(1)", "https://user:password@example.com", "https://example.com?key=secret", "https://example.com#secret", "http://", "http://example.com:0", "http://example.com:99999", "not a url"})
    void rejectsUnsafePublicLinks(String value) { assertThat(IntegrationService.publicOrigin(value)).isNull(); }

    @Test void validatesCooldownAndFreshnessConfiguration() {
        assertThatThrownBy(() -> new IntegrationProperties(Duration.ZERO, Duration.ofMinutes(1), ZoneOffset.UTC, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntegrationProperties(Duration.ofMinutes(1), Duration.ofSeconds(29), ZoneOffset.UTC, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(properties(null).systems()).isEmpty();
    }

    static MonitoredSystem system() {
        var system = new MonitoredSystem(); system.setId(UUID.randomUUID()); system.setName("AI Web Auditor");
        system.setBaseUrl("https://internal.example.com"); system.setHealthEndpoint("/health?key=secret");
        system.setActive(true); system.setLatencyThresholdMs(500); return system;
    }
    static HealthCheck check(MonitoredSystem system, boolean success, OffsetDateTime time) {
        var check = new HealthCheck(); check.setId(UUID.randomUUID()); check.setMonitoredSystem(system); check.setCheckedAt(time);
        check.setSuccess(success); check.setHttpStatus(success ? 200 : 503); check.setResponseTimeMs(120); return check;
    }
}
