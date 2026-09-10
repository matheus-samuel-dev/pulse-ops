package com.pulseops.service;

import com.pulseops.domain.deployment.Deployment;
import com.pulseops.domain.incident.Incident;
import com.pulseops.domain.incident.IncidentStatus;
import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.quality.TestReport;
import com.pulseops.domain.system.Environment;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.domain.system.SystemStatus;
import com.pulseops.dto.dashboard.DashboardSummaryResponse;
import com.pulseops.dto.dashboard.ErrorBreakdownResponse;
import com.pulseops.dto.dashboard.LatencyPointResponse;
import com.pulseops.dto.dashboard.SystemHealthResponse;
import com.pulseops.repository.DeploymentRepository;
import com.pulseops.repository.HealthCheckRepository;
import com.pulseops.repository.IncidentRepository;
import com.pulseops.repository.MonitoredSystemRepository;
import com.pulseops.repository.TestReportRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService")
class DashboardServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T15:00:00Z");
    private static final OffsetDateTime NOW_AT_UTC = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Mock
    private MonitoredSystemRepository systemRepository;
    @Mock
    private HealthCheckRepository healthCheckRepository;
    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private DeploymentRepository deploymentRepository;
    @Mock
    private TestReportRepository testReportRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                systemRepository,
                healthCheckRepository,
                incidentRepository,
                deploymentRepository,
                testReportRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldAggregateCurrentAndPreviousPeriodIntoCriticalSummary() {
        MonitoredSystem alpha = system("Alpha", SystemStatus.OPERATIONAL);
        MonitoredSystem beta = system("Beta", SystemStatus.DEGRADED);
        MonitoredSystem gamma = system("Gamma", SystemStatus.DOWN);
        List<HealthCheck> currentChecks = List.of(
                check(alpha, NOW_AT_UTC.minusHours(3), 200, 100, true, null),
                check(alpha, NOW_AT_UTC.minusHours(2), 200, 120, true, null),
                check(beta, NOW_AT_UTC.minusHours(3), 200, 700, true, null),
                check(beta, NOW_AT_UTC.minusHours(2), 500, 800, false, "HTTP 500")
        );
        List<HealthCheck> previousChecks = List.of(
                check(alpha, NOW_AT_UTC.minusHours(30), 500, 150, false, "HTTP 500"));
        when(systemRepository.findAll()).thenReturn(List.of(alpha, beta, gamma));
        when(healthCheckRepository.findAllByCheckedAtBetweenOrderByCheckedAtAsc(any(), any()))
                .thenReturn(currentChecks, previousChecks);
        when(incidentRepository.findAll()).thenReturn(List.of(
                incident(alpha, NOW_AT_UTC.minusHours(2), IncidentStatus.OPEN),
                incident(alpha, NOW_AT_UTC.minusHours(6), IncidentStatus.INVESTIGATING),
                incident(alpha, NOW_AT_UTC.minusHours(30), IncidentStatus.RESOLVED)
        ));
        when(deploymentRepository.findAll()).thenReturn(List.of(
                deployment(alpha, NOW_AT_UTC.minusHours(1)),
                deployment(alpha, NOW_AT_UTC.minusHours(8)),
                deployment(alpha, NOW_AT_UTC.minusHours(31))
        ));
        when(testReportRepository.findAll()).thenReturn(List.of(
                report(alpha, NOW_AT_UTC.minusHours(2), "90.00"),
                report(alpha, NOW_AT_UTC.minusHours(30), "70.00"),
                report(beta, NOW_AT_UTC.minusHours(26), "60.00"),
                report(beta, NOW_AT_UTC.minusHours(1), "80.00")
        ));

        DashboardSummaryResponse result = dashboardService.summary("24h");

        assertThat(result.monitoredSystems()).isEqualTo(3);
        assertThat(result.averageAvailability()).isEqualByComparingTo("75.00");
        assertThat(result.availabilityChange()).isEqualByComparingTo("75.00");
        assertThat(result.openIncidents()).isEqualTo(2);
        assertThat(result.incidentChange()).isEqualTo(1);
        assertThat(result.averageCoverage()).isEqualByComparingTo("85.00");
        assertThat(result.coverageChange()).isEqualByComparingTo("20.00");
        assertThat(result.deployments()).isEqualTo(2);
        assertThat(result.operationalSystems()).isEqualTo(1);
        assertThat(result.degradedSystems()).isEqualTo(1);
        assertThat(result.downSystems()).isEqualTo(1);
        assertThat(result.overallHealth()).isEqualTo("CRITICAL");
        assertThat(result.period()).isEqualTo("24h");

        ArgumentCaptor<OffsetDateTime> startCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> endCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(healthCheckRepository, times(2))
                .findAllByCheckedAtBetweenOrderByCheckedAtAsc(startCaptor.capture(), endCaptor.capture());
        assertThat(startCaptor.getAllValues()).containsExactly(
                NOW_AT_UTC.minusHours(24), NOW_AT_UTC.minusHours(48));
        assertThat(endCaptor.getAllValues()).containsExactly(
                NOW_AT_UTC, NOW_AT_UTC.minusHours(24));
    }

    @Test
    void shouldReturnHealthyZeroedSummaryForNullPeriodAndNoData() {
        when(systemRepository.findAll()).thenReturn(List.of());
        when(healthCheckRepository.findAllByCheckedAtBetweenOrderByCheckedAtAsc(any(), any()))
                .thenReturn(List.of());
        when(incidentRepository.findAll()).thenReturn(List.of());
        when(deploymentRepository.findAll()).thenReturn(List.of());
        when(testReportRepository.findAll()).thenReturn(List.of());

        DashboardSummaryResponse result = dashboardService.summary(null);

        assertThat(result.period()).isEqualTo("24h");
        assertThat(result.monitoredSystems()).isZero();
        assertThat(result.averageAvailability()).isEqualByComparingTo("0.00");
        assertThat(result.averageCoverage()).isEqualByComparingTo("0.00");
        assertThat(result.deployments()).isZero();
        assertThat(result.openIncidents()).isZero();
        assertThat(result.overallHealth()).isEqualTo("HEALTHY");
    }

    @Test
    void shouldReportDegradedHealthWhenOnlyDegradedSystemsExist() {
        MonitoredSystem degraded = system("Degraded API", SystemStatus.DEGRADED);
        when(systemRepository.findAll()).thenReturn(List.of(degraded));
        when(healthCheckRepository.findAllByCheckedAtBetweenOrderByCheckedAtAsc(any(), any()))
                .thenReturn(List.of());
        when(incidentRepository.findAll()).thenReturn(List.of());
        when(deploymentRepository.findAll()).thenReturn(List.of());
        when(testReportRepository.findAll()).thenReturn(List.of());

        DashboardSummaryResponse result = dashboardService.summary("7d");

        assertThat(result.period()).isEqualTo("7d");
        assertThat(result.degradedSystems()).isOne();
        assertThat(result.downSystems()).isZero();
        assertThat(result.overallHealth()).isEqualTo("DEGRADED");
    }

    @Test
    void shouldBucketHourlyLatencyAndCalculateAverageAndNearestRankP95() {
        MonitoredSystem system = system("Latency API", SystemStatus.OPERATIONAL);
        OffsetDateTime firstHour = NOW_AT_UTC.minusHours(5).truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime secondHour = NOW_AT_UTC.minusHours(4).truncatedTo(ChronoUnit.HOURS);
        when(healthCheckRepository.findAllByCheckedAtBetweenOrderByCheckedAtAsc(any(), any()))
                .thenReturn(List.of(
                        check(system, firstHour.plusMinutes(1), 200, 10, true, null),
                        check(system, firstHour.plusMinutes(2), 200, 20, true, null),
                        check(system, firstHour.plusMinutes(3), 200, 30, true, null),
                        check(system, firstHour.plusMinutes(4), 200, 40, true, null),
                        check(system, firstHour.plusMinutes(5), 200, 100, true, null),
                        check(system, firstHour.plusMinutes(6), null, 999, false, "DNS"),
                        check(system, secondHour.plusMinutes(1), 200, 25, true, null)
                ));

        List<LatencyPointResponse> result = dashboardService.latency("24H");

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().timestamp()).isEqualTo(firstHour);
        assertThat(result.getFirst().averageMs()).isEqualByComparingTo("40.00");
        assertThat(result.getFirst().p95Ms()).isEqualTo(100);
        assertThat(result.getFirst().samples()).isEqualTo(5);
        assertThat(result.getLast().timestamp()).isEqualTo(secondHour);
        assertThat(result.getLast().averageMs()).isEqualByComparingTo("25.00");
    }

    @Test
    void shouldUseDailyBucketsForSevenAndThirtyDayPeriods() {
        MonitoredSystem system = system("Daily API", SystemStatus.OPERATIONAL);
        OffsetDateTime firstDay = NOW_AT_UTC.minusDays(2).truncatedTo(ChronoUnit.DAYS);
        when(healthCheckRepository.findAllByCheckedAtBetweenOrderByCheckedAtAsc(any(), any()))
                .thenReturn(List.of(
                        check(system, firstDay.plusHours(2), 200, 100, true, null),
                        check(system, firstDay.plusHours(9), 200, 300, true, null)
                ));

        List<LatencyPointResponse> result = dashboardService.latency("7d");

        assertThat(result).singleElement().satisfies(point -> {
            assertThat(point.timestamp()).isEqualTo(firstDay);
            assertThat(point.averageMs()).isEqualByComparingTo("200.00");
            assertThat(point.samples()).isEqualTo(2);
        });
    }

    @Test
    void shouldClassifyEveryFailedHealthCheckAndIgnoreSuccessfulChecks() {
        MonitoredSystem system = system("Errors API", SystemStatus.DEGRADED);
        when(healthCheckRepository.findAllByCheckedAtBetweenOrderByCheckedAtAsc(any(), any()))
                .thenReturn(List.of(
                        check(system, NOW_AT_UTC.minusMinutes(6), 503, 100, false, "HTTP 503"),
                        check(system, NOW_AT_UTC.minusMinutes(5), 404, 100, false, "HTTP 404"),
                        check(system, NOW_AT_UTC.minusMinutes(4), null, 100, false, "Read TIMEOUT"),
                        check(system, NOW_AT_UTC.minusSeconds(210), null, 100, false,
                                "Tempo limite excedido durante o health check."),
                        check(system, NOW_AT_UTC.minusMinutes(3), null, 100, false, "DNS failure"),
                        check(system, NOW_AT_UTC.minusMinutes(2), 302, 100, false, "Unexpected redirect"),
                        check(system, NOW_AT_UTC.minusSeconds(90), null, 100, false, null),
                        check(system, NOW_AT_UTC.minusMinutes(1), 500, 100, true, null)
                ));

        ErrorBreakdownResponse result = dashboardService.errors("30d");

        assertThat(result.serverErrors()).isOne();
        assertThat(result.clientErrors()).isOne();
        assertThat(result.timeouts()).isEqualTo(2);
        assertThat(result.others()).isEqualTo(3);
        assertThat(result.total()).isEqualTo(7);
        assertThat(result.period()).isEqualTo("30d");
    }

    @Test
    void shouldBuildSortedSystemHealthWithUptimeLatestCheckAndSparkline() {
        MonitoredSystem zeta = system("Zeta", SystemStatus.UNKNOWN);
        MonitoredSystem alpha = system("alpha", SystemStatus.OPERATIONAL);
        HealthCheck oldest = check(alpha, NOW_AT_UTC.minusHours(3), 200, 100, true, null);
        HealthCheck middle = check(alpha, NOW_AT_UTC.minusHours(2), 500, 300, false, "HTTP 500");
        HealthCheck latest = check(alpha, NOW_AT_UTC.minusHours(1), null, 200, true, null);
        when(healthCheckRepository.findAllByCheckedAtBetweenOrderByCheckedAtAsc(any(), any()))
                .thenReturn(List.of(latest, oldest, middle));
        when(systemRepository.findAll()).thenReturn(List.of(zeta, alpha));

        List<SystemHealthResponse> result = dashboardService.health("30d");

        assertThat(result).extracting(SystemHealthResponse::name).containsExactly("alpha", "Zeta");
        SystemHealthResponse alphaHealth = result.getFirst();
        assertThat(alphaHealth.uptime()).isEqualByComparingTo("66.67");
        assertThat(alphaHealth.latencyMs()).isEqualTo(200);
        assertThat(alphaHealth.lastCheckedAt()).isEqualTo(latest.getCheckedAt());
        assertThat(alphaHealth.sparkline()).containsExactly(100L, 300L);
        SystemHealthResponse zetaHealth = result.getLast();
        assertThat(zetaHealth.uptime()).isEqualByComparingTo("0.00");
        assertThat(zetaHealth.latencyMs()).isNull();
        assertThat(zetaHealth.lastCheckedAt()).isNull();
        assertThat(zetaHealth.sparkline()).isEmpty();
    }

    private MonitoredSystem system(String name, SystemStatus status) {
        MonitoredSystem system = new MonitoredSystem();
        system.setId(UUID.randomUUID());
        system.setName(name);
        system.setEnvironment(Environment.PRODUCTION);
        system.setStatus(status);
        system.setActive(true);
        return system;
    }

    private HealthCheck check(
            MonitoredSystem system,
            OffsetDateTime checkedAt,
            Integer status,
            long latency,
            boolean success,
            String error
    ) {
        HealthCheck check = new HealthCheck();
        check.setId(UUID.randomUUID());
        check.setMonitoredSystem(system);
        check.setCheckedAt(checkedAt);
        check.setHttpStatus(status);
        check.setResponseTimeMs(latency);
        check.setSuccess(success);
        check.setErrorMessage(error);
        return check;
    }

    private TestReport report(MonitoredSystem system, OffsetDateTime generatedAt, String coverage) {
        TestReport report = new TestReport();
        report.setId(UUID.randomUUID());
        report.setMonitoredSystem(system);
        report.setGeneratedAt(generatedAt);
        report.setLineCoverage(new BigDecimal(coverage));
        report.setBranchCoverage(new BigDecimal(coverage));
        return report;
    }

    private Incident incident(
            MonitoredSystem system,
            OffsetDateTime startedAt,
            IncidentStatus status
    ) {
        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());
        incident.setMonitoredSystem(system);
        incident.setStartedAt(startedAt);
        incident.setStatus(status);
        return incident;
    }

    private Deployment deployment(MonitoredSystem system, OffsetDateTime deployedAt) {
        Deployment deployment = new Deployment();
        deployment.setId(UUID.randomUUID());
        deployment.setMonitoredSystem(system);
        deployment.setDeployedAt(deployedAt);
        return deployment;
    }
}
